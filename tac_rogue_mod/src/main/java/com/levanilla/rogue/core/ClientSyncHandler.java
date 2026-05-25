package com.levanilla.rogue.core;

import com.levanilla.rogue.client.ClientEventHandler;
import com.levanilla.rogue.client.PerkManager;
import com.levanilla.rogue.client.RogueInventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClientSyncHandler {

    private ClientSyncHandler() {}
    private static final String PERK_CLEAR_TOKEN = "clear";

    public static void onSync(String data) {
        try {
            if (data.startsWith("stamina:")) {
                handleStamina(data);
                return;
            }
            if (data.startsWith("health:")) {
                handleHealth(data);
                return;
            }
            if (data.startsWith("perks:")) {
                handlePerks(data);
                return;
            }
            if (data.startsWith("dmg:")) {
                handleDamageIndicator(data);
                return;
            }
            if (data.startsWith("drop:")) {
                handleDropIndicator(data);
                return;
            }
            if (data.startsWith("gold:")) {
                int gold = Integer.parseInt(data.substring(5));
                applyGold(gold);
                return;
            }
            if (data.startsWith("gold_gain:")) {
                int amount = Integer.parseInt(data.substring(10));
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.levanilla.rogue.client.hud.HudRenderer.addGoldGain(amount));
                return;
            }
            if (data.startsWith("meta:")) {
                handleMeta(data);
                return;
            }
            if (data.startsWith("deep:")) {
                handleDeep(data);
                return;
            }
            if (data.startsWith("deep_cache_result:")) {
                handleDeepCacheResult(data);
                return;
            }
            if (data.startsWith("quest_data:")) {
                handleQuestData(data);
                return;
            }
            if (data.startsWith("coop_wait:")) {
                handleCoopWait(data);
                return;
            }
            if (data.equals("coop_wait_clear")) {
                clearCoopWait();
                return;
            }
            if (data.startsWith("generation:")) {
                handleGeneration(data);
                return;
            }
            if (data.equals("generation_clear")) {
                ClientRunState.clearGenerationStatus();
                return;
            }
            if (data.startsWith("boss_bar:")) {
                handleBossBar(data);
                return;
            }
            if (data.equals("boss_bar_clear")) {
                ClientRunState.clearBossBarState();
                return;
            }
            if (data.startsWith("objective:")) {
                handleObjective(data);
                return;
            }
            if (data.equals("objective_clear")) {
                ClientRunState.clearObjectiveState();
                return;
            }
            if (data.startsWith("enemy_dir:")) {
                handleEnemyDirection(data);
                return;
            }
            if (data.equals("enemy_dir_clear")) {
                ClientRunState.clearEnemyDirection();
                return;
            }
            if (data.startsWith("medical_buff:")) {
                ClientRunState.setMedicalBuffRemainingTicks(Integer.parseInt(data.substring(13)));
                return;
            }
            if (data.startsWith("perf_start:")) {
                handlePerfStart(data);
                return;
            }
            if (data.equals("perf_stop")) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.levanilla.rogue.client.ClientPerformanceProfiler.stop());
                return;
            }
            if (data.equals("open_quest_tab")) {
                openQuestTab();
                return;
            }
            if (data.equals("open_deep_operations")) {
                openDeepOperations();
                return;
            }
            if (data.startsWith("perk_init:")) {
                PerkManager.openInitialPerkScreen();
                return;
            }
            if (data.startsWith("perk_boss:")) {
                PerkManager.openBossPerkScreen();
                return;
            }
            if (data.startsWith("perk_normal:")) {
                PerkManager.openPerkScreen();
                return;
            }
            if (data.startsWith("open_floor_selection:")) {
                handleFloorSelection(data);
                return;
            }

            handleRunData(data);
        } catch (Exception e) {
            // Keep legacy sync behavior: malformed payloads are ignored.
        }
    }

    private static void handleStamina(String data) {
        String[] parts = data.substring(8).split(":");
        if (parts.length >= 2) {
            float stamina = Float.parseFloat(parts[0]);
            float maxStamina = Float.parseFloat(parts[1]);
            boolean exhausted = parts.length >= 3 && ("1".equals(parts[2]) || Boolean.parseBoolean(parts[2]));
            StaminaManager.setClientData(stamina, maxStamina, exhausted);
        }
    }

    private static void handleHealth(String data) {
        String[] parts = data.substring(7).split(":");
        if (parts.length >= 2) {
            float health = Float.parseFloat(parts[0]);
            float maxHealth = Float.parseFloat(parts[1]);
            long durationMs = parts.length >= 3 ? Long.parseLong(parts[2]) : 1500L;
            ClientRunState.setHealthOverride(health, maxHealth, durationMs);
        }
    }

    private static void handlePerks(String data) {
        applyClientPerkSync(data.substring(6));
    }

    public static void applyPerks(String perkTags) {
        applyClientPerkSync(perkTags);
    }

    public static void applyClientPerkSync(String payload) {
        String raw = payload == null ? "" : payload;
        boolean explicitClear = PERK_CLEAR_TOKEN.equals(raw);
        List<String> tags = new ArrayList<>();
        if (!raw.isBlank() && !explicitClear) {
            for (String tag : raw.split(",")) {
                if (!tag.isEmpty() && tag.startsWith("perk:")) {
                    tags.add(tag);
                }
            }
        }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            boolean hasStoredPerks = !ClientRunState.getPerkTags().isEmpty();
            net.minecraft.client.player.LocalPlayer clientPlayer = net.minecraft.client.Minecraft.getInstance().player;
            boolean hasCurrentPerks = clientPlayer != null
                && clientPlayer.getTags().stream().anyMatch(t -> t.startsWith("perk:"));
            if (tags.isEmpty() && !explicitClear && (hasCurrentPerks || hasStoredPerks)) {
                return;
            }
            ClientRunState.setPerkTags(tags);
            if (clientPlayer != null) {
                clientPlayer.getTags().removeIf(t -> t.startsWith("perk:"));
                for (String tag : tags) {
                    clientPlayer.addTag(tag);
                }
            }
            com.levanilla.rogue.client.TitleRunSummary.recordPerks(tags.toArray(String[]::new));
        });
    }

    public static void applyGold(int gold) {
        ClientRunState.setGold(gold);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.levanilla.rogue.client.TitleRunSummary.recordGold(gold));
    }

    private static void handleDamageIndicator(String data) {
        String[] parts = data.substring(4).split(":");
        if (parts.length >= 5) {
            float damage = Float.parseFloat(parts[0]);
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            boolean isCritical = Boolean.parseBoolean(parts[4]);
            boolean isHeadShot = parts.length >= 6 && Boolean.parseBoolean(parts[5]);
            boolean isShotgun = parts.length >= 7 && Boolean.parseBoolean(parts[6]);
            if (isShotgun) {
                ClientEventHandler.addShotgunDamageIndicator(x, y, z, damage, isCritical, isHeadShot);
            } else {
                ClientEventHandler.addDamageIndicator(x, y, z, damage, isCritical, isHeadShot);
            }
        }
    }

    private static void handleDropIndicator(String data) {
        String[] parts = data.substring(5).split(":");
        if (parts.length >= 4) {
            ClientEventHandler.addDropIndicator(
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                parts[0]);
        }
    }

    private static void handleMeta(String data) {
        String[] parts = data.substring(5).split(":");
        if (parts.length >= 3) {
            int flashlightLevel = parts.length >= 4 ? Integer.parseInt(parts[3]) : ClientRunState.getFlashlightLevel();
            applyMeta(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), flashlightLevel);
        }
    }

    public static void applyMeta(int invLevel, int meleeLevel, int randomPerkBuys, int flashlightLevel) {
        ClientRunState.setMetaData(invLevel, meleeLevel, randomPerkBuys, flashlightLevel);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.client.player.LocalPlayer clientPlayer = mc.player;
            if (clientPlayer != null) {
                clientPlayer.getPersistentData().putInt("TacRogue_InvLevel", invLevel);
                clientPlayer.getPersistentData().putInt("TacRogueMeleeLevel", meleeLevel);
                clientPlayer.getPersistentData().putInt("RandomPerkBuys", randomPerkBuys);
            }
            if (mc.screen instanceof RogueInventoryScreen screen) {
                screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }
        });
    }

    private static void handleDeep(String data) {
        String[] parts = data.substring(5).split(":", -1);
        if (parts.length < 7) return;
        int core = Integer.parseInt(parts[0]);
        int prestige = Integer.parseInt(parts[1]);
        int highest = Integer.parseInt(parts[2]);
        int band = Integer.parseInt(parts[3]);
        String taskType = parts[4];
        int progress = Integer.parseInt(parts[5]);
        int target = Integer.parseInt(parts[6]);
        int provision = parts.length > 7 ? Integer.parseInt(parts[7]) : 0;
        int prepared = parts.length > 8 ? Integer.parseInt(parts[8]) : 0;
        int selection = parts.length > 9 ? Integer.parseInt(parts[9]) : 0;
        int supplyLine = parts.length > 10 ? Integer.parseInt(parts[10]) : 0;
        int blackMarket = parts.length > 11 ? Integer.parseInt(parts[11]) : 0;
        ClientRunState.setDeepState(core, prestige, highest, band, taskType, progress, target,
            provision, prepared, selection, supplyLine, blackMarket);
    }

    private static void handleDeepCacheResult(String data) {
        String[] parts = data.substring("deep_cache_result:".length()).split(":", -1);
        if (parts.length < 3) return;
        String name = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String categoryKey = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String meta = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.levanilla.rogue.client.DeepOperationsScreen.setLastCacheResult(name, categoryKey, meta));
    }

    private static void handleQuestData(String data) {
        String payload = data.substring(11);
        String[] chapterAndQuests = payload.split("\\|", 2);
        String[] header = chapterAndQuests[0].split(":", -1);
        int chapter = Integer.parseInt(header[0]);
        boolean selectionLocked = header.length > 1 && Boolean.parseBoolean(header[1]);
        Set<String> selectedIds = header.length > 2 ? parseIdSet(header[2]) : new LinkedHashSet<>();
        Set<String> candidateIds = header.length > 3 ? parseIdSet(header[3]) : new LinkedHashSet<>();
        List<String[]> questList = new ArrayList<>();
        if (chapterAndQuests.length > 1 && !chapterAndQuests[1].isEmpty()) {
            String[] questEntries = chapterAndQuests[1].split(",");
            for (String entry : questEntries) {
                if (entry.isEmpty()) continue;
                String[] fields = entry.split(";");
                if (fields.length >= 6) {
                    questList.add(fields);
                }
            }
        }
        RogueInventoryScreen.syncQuestData(chapter, questList);
        com.levanilla.rogue.client.QuestScreen.syncQuestData(chapter, questList, candidateIds, selectedIds, selectionLocked);
    }

    private static Set<String> parseIdSet(String encoded) {
        Set<String> ids = new LinkedHashSet<>();
        if (encoded == null || encoded.isBlank()) return ids;
        for (String id : encoded.split("~")) {
            if (!id.isBlank()) ids.add(id);
        }
        return ids;
    }

    private static void openQuestTab() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            net.minecraft.client.Minecraft.getInstance().setScreen(new com.levanilla.rogue.client.QuestScreen()));
    }

    private static void openDeepOperations() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            net.minecraft.client.Minecraft.getInstance().setScreen(new com.levanilla.rogue.client.DeepOperationsScreen()));
    }

    private static void handleCoopWait(String data) {
        String[] parts = data.substring(10).split(":");
        if (parts.length >= 2) {
            int floor = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.levanilla.rogue.client.PublicCoopWaitState.update(floor, seconds);
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.tac_rogue.coop_wait", floor, seconds),
                        true);
                }
            });
        }
    }

    private static void clearCoopWait() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.levanilla.rogue.client.PublicCoopWaitState.clear());
    }

    private static void handleGeneration(String data) {
        String[] parts = data.substring(11).split(":");
        if (parts.length < 4) return;
        int floor = Integer.parseInt(parts[0]);
        int remaining = Integer.parseInt(parts[1]);
        int total = Integer.parseInt(parts[2]);
        float progress = Float.parseFloat(parts[3]);
        ClientRunState.setGenerationStatus(floor, remaining, total, progress, 1600L);
    }

    private static void handleBossBar(String data) {
        String[] parts = data.substring(9).split(":", 4);
        if (parts.length < 4) return;
        int floor = Integer.parseInt(parts[0]);
        float health = Float.parseFloat(parts[1]);
        float maxHealth = Float.parseFloat(parts[2]);
        String name = new String(Base64.getUrlDecoder().decode(parts[3]), StandardCharsets.UTF_8);
        ClientRunState.setBossBarState(floor, health, maxHealth, name, 2200L);
    }

    private static void handleEnemyDirection(String data) {
        String[] parts = data.substring(10).split(":");
        if (parts.length < 3) return;
        ClientRunState.setEnemyDirection(
            Double.parseDouble(parts[0]),
            Double.parseDouble(parts[1]),
            Integer.parseInt(parts[2]),
            1600L);
    }

    private static void handleObjective(String data) {
        String[] parts = data.substring(10).split(":", 7);
        if (parts.length < 5) return;
        String type = parts[0];
        int progress = Integer.parseInt(parts[1]);
        int target = Integer.parseInt(parts[2]);
        boolean complete = Boolean.parseBoolean(parts[3]);
        String title = new String(Base64.getUrlDecoder().decode(parts[4]), StandardCharsets.UTF_8);
        String statusKey = parts.length >= 7 && !parts[6].isBlank()
            ? new String(Base64.getUrlDecoder().decode(parts[6]), StandardCharsets.UTF_8)
            : "";
        int targetX = ClientRunState.ObjectiveState.NO_TARGET;
        int targetY = ClientRunState.ObjectiveState.NO_TARGET;
        int targetZ = ClientRunState.ObjectiveState.NO_TARGET;
        double dx = Double.NaN;
        double dz = Double.NaN;
        if (parts.length >= 6 && !parts[5].isBlank()) {
            String posRaw = new String(Base64.getUrlDecoder().decode(parts[5]), StandardCharsets.UTF_8);
            String[] xyz = posRaw.split(",");
            if (xyz.length >= 3) {
                targetX = Integer.parseInt(xyz[0]);
                targetY = Integer.parseInt(xyz[1]);
                targetZ = Integer.parseInt(xyz[2]);
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    dx = targetX + 0.5D - mc.player.getX();
                    dz = targetZ + 0.5D - mc.player.getZ();
                }
            }
        }
        ClientRunState.setObjectiveState(type, title, statusKey, progress, target, complete,
            targetX, targetY, targetZ, dx, dz, 5000L);
    }

    private static void handlePerfStart(String data) {
        String[] parts = data.split(":", 3);
        int seconds = parts.length >= 2 ? Integer.parseInt(parts[1]) : 60;
        String label = parts.length >= 3 ? parts[2] : "manual";
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.levanilla.rogue.client.ClientPerformanceProfiler.start(seconds, label));
    }

    private static void handleFloorSelection(String data) {
        String[] split = data.substring(21).split(":");
        int maxFloor = Integer.parseInt(split[0]);
        long seed = split.length > 1 ? Long.parseLong(split[1]) : 0L;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            ClientEventHandler.openFloorSelectionScreen(maxFloor, seed));
    }

    private static void handleRunData(String data) {
        String[] parts = data.split(":", 6);
        if (parts.length >= 3) {
            int maxFloor = parts.length >= 5 ? Integer.parseInt(parts[4]) : 0;
            int floor = Integer.parseInt(parts[0]);
            boolean active = Boolean.parseBoolean(parts[2]);
            if (parts.length >= 4) {
                int ammoCapacityLevel = parts.length >= 6 ? Integer.parseInt(parts[5]) : ClientRunState.getAmmoCapacityLevel();
                applyRunData(floor, parts[1], active, Boolean.parseBoolean(parts[3]), maxFloor, ammoCapacityLevel);
            } else {
                applyRunData(floor, parts[1], active, maxFloor);
            }
        }
    }

    public static void applyRunData(int floor, String theme, boolean active, int maxFloor) {
        ClientRunState.setRunData(floor, theme, active, maxFloor);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.levanilla.rogue.client.TitleRunSummary.recordRun(floor, theme, active, maxFloor));
    }

    public static void applyRunData(int floor, String theme, boolean active, boolean cleared, int maxFloor, int ammoCapacityLevel) {
        ClientRunState.setRunData(floor, theme, active, cleared, maxFloor, ammoCapacityLevel);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.levanilla.rogue.client.TitleRunSummary.recordRun(floor, theme, active, maxFloor));
    }
}
