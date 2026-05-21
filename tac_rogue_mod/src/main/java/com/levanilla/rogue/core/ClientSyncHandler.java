package com.levanilla.rogue.core;

import com.levanilla.rogue.client.ClientEventHandler;
import com.levanilla.rogue.client.PerkManager;
import com.levanilla.rogue.client.RogueInventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.List;

public final class ClientSyncHandler {

    private ClientSyncHandler() {}

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
            if (data.startsWith("meta:")) {
                handleMeta(data);
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
            if (data.equals("open_quest_tab")) {
                openQuestTab();
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
        applyPerks(data.substring(6));
    }

    public static void applyPerks(String perkTags) {
        String[] tags = perkTags.split(",");
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            net.minecraft.client.player.LocalPlayer clientPlayer = net.minecraft.client.Minecraft.getInstance().player;
            if (clientPlayer != null) {
                clientPlayer.getTags().removeIf(t -> t.startsWith("perk:"));
                for (String tag : tags) {
                    if (!tag.isEmpty()) clientPlayer.addTag(tag);
                }
                com.levanilla.rogue.client.TitleRunSummary.recordPerks(tags);
            }
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

    private static void handleQuestData(String data) {
        String payload = data.substring(11);
        String[] chapterAndQuests = payload.split("\\|", 2);
        int chapter = Integer.parseInt(chapterAndQuests[0]);
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
        com.levanilla.rogue.client.QuestScreen.syncQuestData(chapter, questList);
    }

    private static void openQuestTab() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            net.minecraft.client.Minecraft.getInstance().setScreen(new com.levanilla.rogue.client.QuestScreen()));
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
