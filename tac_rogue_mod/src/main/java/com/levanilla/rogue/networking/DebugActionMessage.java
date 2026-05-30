package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.CurrencyManager;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PlayerRunData;
import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.ModEntities;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.core.service.RogueItemFactory;
import com.levanilla.rogue.core.service.ShopPlacementService;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import com.levanilla.rogue.core.service.PerkStorageService;
import com.levanilla.rogue.world.TacRogueBossEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

public class DebugActionMessage {
    public enum ActionType {
        GIVE_GUN,
        GIVE_ITEM,
        ADD_GOLD,
        SYNC,
        ADVANCE_QUEST,
        GIVE_PERK,
        REMOVE_PERK,
        CLEAR_PERKS,
        GIVE_AMMO,
        SET_FLOOR_CLEARED,
        SET_FLOOR,
        SET_MAX_FLOOR,
        SET_FLASHLIGHT_LEVEL,
        RELOAD_INFO,
        SPAWN_BOSS,
        BOSS_INFO,
        MP_STATE,
        MP_PARTY_SIZE,
        MP_START_NOW,
        MP_COMPLETE,
        MP_VIRTUAL_JOIN,
        MP_LEAVE,
        KILL_DUNGEON_ENEMIES,
        ADD_DEEP_CORE,
        SET_PRESTIGE,
        GIVE_DEEP_GUN,
        COMPLETE_DEEP_TASK,
        RUN_DEBUG_COMMAND
    }

    private static final Set<String> ALLOWED_DEBUG_COMMAND_PREFIXES = Set.of(
        "debug state",
        "debug weapon",
        "debug reloadinfo",
        "debug fire_rate_probe",
        "debug alert_probe",
        "debug support_team",
        "debug perf_start",
        "debug perf_stop",
        "debug smoke quick",
        "debug smoke monster",
        "debug smoke objective",
        "debug smoke encounter",
        "debug smoke perk_storage",
        "debug smoke ai_matrix",
        "debug smoke ai_matrix_view",
        "debug smoke generation",
        "debug smoke generation_view",
        "debug smoke boss_generation",
        "debug smoke boss_generation_view"
    );

    private final ActionType action;
    private final String data;

    public DebugActionMessage(ActionType action, String data) {
        this.action = action;
        this.data = data == null ? "" : data;
    }

    public static void encode(DebugActionMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.data);
    }

    public static DebugActionMessage decode(FriendlyByteBuf buf) {
        return new DebugActionMessage(buf.readEnum(ActionType.class), buf.readUtf());
    }

    public static void handle(DebugActionMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!isAllowed(player)) {
                player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Admin permission required."));
                return;
            }

            try {
                switch (msg.action) {
                    case GIVE_GUN -> handleGiveGun(player, msg.data);
                    case GIVE_ITEM -> handleGiveItem(player, msg.data);
                    case ADD_GOLD -> {
                        int amount = Integer.parseInt(msg.data);
                        CurrencyManager.addGoldNoQuest(player, amount);
                        RunManager.syncPlayer(player);
                        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Gold +" + amount));
                    }
                    case SYNC -> {
                        RunManager.syncPlayer(player);
                        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Synced rogue data."));
                    }
                    case ADVANCE_QUEST -> handleAdvanceQuest(player, msg.data);
                    case GIVE_PERK -> handleGivePerk(player, msg.data);
                    case REMOVE_PERK -> handleRemovePerk(player, msg.data);
                    case CLEAR_PERKS -> handleClearPerks(player);
                    case GIVE_AMMO -> handleGiveAmmo(player, msg.data);
                    case SET_FLOOR_CLEARED -> handleSetFloorCleared(player, msg.data);
                    case SET_FLOOR -> handleSetFloor(player, msg.data);
                    case SET_MAX_FLOOR -> handleSetMaxFloor(player, msg.data);
                    case SET_FLASHLIGHT_LEVEL -> handleSetFlashlightLevel(player, msg.data);
                    case RELOAD_INFO -> handleReloadInfo(player);
                    case SPAWN_BOSS -> handleSpawnBoss(player, msg.data);
                    case BOSS_INFO -> handleBossInfo(player);
                    case MP_STATE -> FloorInstanceManager.debugDescribe(player);
                    case MP_PARTY_SIZE -> FloorInstanceManager.debugSetPartySize(player, Integer.parseInt(msg.data));
                    case MP_START_NOW -> {
                        if (!FloorInstanceManager.debugForceStart(player)) {
                            player.sendSystemMessage(Component.literal("\u00A7e[MP-DEBUG] No waiting instance to start."));
                        }
                    }
                    case MP_COMPLETE -> {
                        if (!FloorInstanceManager.debugForceComplete(player)) {
                            player.sendSystemMessage(Component.literal("\u00A7e[MP-DEBUG] No active instance to complete."));
                        }
                    }
                    case MP_VIRTUAL_JOIN -> {
                        if (!FloorInstanceManager.debugApplyVirtualJoin(player, Integer.parseInt(msg.data))) {
                            player.sendSystemMessage(Component.literal("\u00A7e[MP-DEBUG] No active instance for virtual join."));
                        }
                    }
                    case MP_LEAVE -> FloorInstanceManager.debugLeave(player);
                    case KILL_DUNGEON_ENEMIES -> FloorInstanceManager.debugKillDungeonEnemies(player);
                    case ADD_DEEP_CORE -> handleAddDeepCore(player, msg.data);
                    case SET_PRESTIGE -> handleSetPrestige(player, msg.data);
                    case GIVE_DEEP_GUN -> handleGiveDeepGun(player);
                    case COMPLETE_DEEP_TASK -> handleCompleteDeepTask(player);
                    case RUN_DEBUG_COMMAND -> handleRunDebugCommand(player, msg.data);
                }
            } catch (Exception ex) {
                player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Action failed: " + ex.getMessage()));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean isAllowed(ServerPlayer player) {
        return player.getGameProfile().getName().equalsIgnoreCase("levanilla_");
    }

    private static void handleRunDebugCommand(ServerPlayer player, String rawCommand) {
        String command = rawCommand == null ? "" : rawCommand.trim();
        if (command.startsWith("/")) command = command.substring(1).trim();
        if (command.startsWith("rogue_admin ")) command = command.substring("rogue_admin ".length()).trim();
        if (!command.startsWith("debug ")) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Rejected non-debug command: " + command));
            return;
        }
        if (!isAllowedDebugCommand(command)) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Rejected debug command: " + command));
            return;
        }
        player.server.getCommands().performPrefixedCommand(
            player.createCommandSourceStack(),
            "rogue_admin " + command);
    }

    private static boolean isAllowedDebugCommand(String command) {
        for (String prefix : ALLOWED_DEBUG_COMMAND_PREFIXES) {
            if (command.equals(prefix) || command.startsWith(prefix + " ")) {
                return true;
            }
        }
        return false;
    }

    private static void handleGiveGun(ServerPlayer player, String data) {
        String[] parts = data.split("\\|", 2);
        if (parts.length < 2) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Missing gun id or rarity."));
            return;
        }

        String fullId = parts[0].contains(":") ? parts[0] : "tacz:" + parts[0];
        WeaponRarity.Rarity rarity = WeaponRarity.Rarity.valueOf(parts[1].toUpperCase(Locale.ROOT));
        ItemStack stack = RogueItemFactory.createGunStack(fullId, rarity);
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Failed to create gun: " + fullId));
            return;
        }

        ShopPlacementService.placeRewardItem(player, stack, fullId);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Gave " + fullId + " " + rarity.name()));
    }

    private static void handleGiveItem(ServerPlayer player, String data) {
        String itemId = data == null ? "" : data.trim();
        if (itemId.isBlank()) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Missing item id."));
            return;
        }
        ItemStack stack = RogueItemFactory.createItemStack(player, itemId);
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Failed to create item: " + itemId));
            return;
        }
        ShopPlacementService.placeRewardItem(player, stack, itemId);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Gave item " + itemId + " x" + stack.getCount()));
    }

    private static void handleAddDeepCore(ServerPlayer player, String data) {
        int amount = Integer.parseInt(data == null || data.isBlank() ? "25" : data);
        PlayerRunData run = RunManager.getData(player);
        run.addDeepCore(amount);
        RunManager.syncPlayer(player);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Deep Core +" + amount + " total=" + run.getDeepCore()));
    }

    private static void handleSetPrestige(ServerPlayer player, String data) {
        int level = Math.max(0, Integer.parseInt(data == null || data.isBlank() ? "1" : data));
        PlayerRunData run = RunManager.getData(player);
        run.setPrestigeLevel(level);
        run.updateHighestEverFloor(100);
        RunManager.syncPlayer(player);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Prestige level=" + level));
    }

    private static void handleGiveDeepGun(ServerPlayer player) {
        java.util.List<ShopCatalog.ShopItem> candidates = TacZRegistryHelper.getItemsByCategory(ShopCatalog.Category.RIFLE);
        if (candidates.isEmpty()) candidates = TacZRegistryHelper.getAllShopItems();
        ShopCatalog.ShopItem item = candidates.stream()
            .filter(i -> i.category == ShopCatalog.Category.RIFLE || i.category == ShopCatalog.Category.SMG || i.category == ShopCatalog.Category.LMG)
            .findFirst()
            .orElse(candidates.get(0));
        ItemStack stack = RogueItemFactory.createGunStack(item.id, WeaponRarity.Rarity.RARE);
        com.levanilla.rogue.core.service.DeepProgressService.applyModifier(
            stack,
            com.levanilla.rogue.core.service.DeepProgressService.DeepModifier.BREACH);
        ShopPlacementService.placeRewardItem(player, stack, item.id);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Gave Deep Modifier gun " + item.id));
    }

    private static void handleCompleteDeepTask(ServerPlayer player) {
        PlayerRunData run = RunManager.getData(player);
        if (run.getCurrentDeepTaskType().isBlank()) {
            com.levanilla.rogue.core.service.DeepProgressService.ensureDeepTask(player, Math.max(101, run.getCurrentFloor()));
        }
        com.levanilla.rogue.core.service.DeepProgressService.DeepTaskType type;
        try {
            type = com.levanilla.rogue.core.service.DeepProgressService.DeepTaskType.valueOf(run.getCurrentDeepTaskType());
        } catch (Exception ignored) {
            type = com.levanilla.rogue.core.service.DeepProgressService.DeepTaskType.BAND_CLEAR;
        }
        int remain = Math.max(1, run.getDeepTaskTarget() - run.getDeepTaskProgress());
        com.levanilla.rogue.core.service.DeepProgressService.advanceDeepTask(player, type, remain);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Completed current deep task."));
    }

    private static void handleAdvanceQuest(ServerPlayer player, String data) {
        String[] parts = data.split("\\|", 2);
        if (parts.length < 2) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Missing quest type or amount."));
            return;
        }

        QuestManager.QuestType type = QuestManager.QuestType.valueOf(parts[0].toUpperCase(Locale.ROOT));
        int amount = Integer.parseInt(parts[1]);
        QuestManager.advanceQuest(player, type, amount);
        RunManager.syncPlayer(player);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Advanced " + type.name() + " +" + amount));
    }

    private static void handleGivePerk(ServerPlayer player, String data) {
        String[] parts = data.split("\\|", 3);
        if (parts.length < 3) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Missing perk category, modifier, or level."));
            return;
        }

        PerkDefinition.Category category = PerkDefinition.Category.valueOf(parts[0].toUpperCase(Locale.ROOT));
        PerkDefinition.Modifier modifier = PerkDefinition.Modifier.valueOf(parts[1].toUpperCase(Locale.ROOT));
        int level = Integer.parseInt(parts[2]);
        PerkDefinition perk = new PerkDefinition(category, modifier, level);
        int serial = player.getPersistentData().getInt("TacRogueDebugPerkSerial") + 1;
        player.getPersistentData().putInt("TacRogueDebugPerkSerial", serial);
        PerkStorageService.addPerk(player, perk.toTag() + ":#" + serial);
        RunManager.syncPlayer(player);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Gave perk " + perk.getDisplayName()));
    }

    private static void handleRemovePerk(ServerPlayer player, String data) {
        String[] parts = data.split("\\|", 3);
        if (parts.length < 3) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Missing perk category, modifier, or level."));
            return;
        }

        PerkDefinition.Category category = PerkDefinition.Category.valueOf(parts[0].toUpperCase(Locale.ROOT));
        PerkDefinition.Modifier modifier = PerkDefinition.Modifier.valueOf(parts[1].toUpperCase(Locale.ROOT));
        int level = Integer.parseInt(parts[2]);

        for (String tag : PerkStorageService.getPerkTags(player)) {
            PerkDefinition perk = PerkDefinition.fromTag(tag);
            if (perk.category == category && perk.modifier == modifier && perk.level == level) {
                PerkStorageService.removePerk(player, tag);
                RunManager.syncPlayer(player);
                player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Removed perk " + perk.getDisplayName()));
                return;
            }
        }

        player.sendSystemMessage(Component.literal("\u00A7e[DEBUG] Matching perk was not found."));
    }

    private static void handleClearPerks(ServerPlayer player) {
        int removed = PerkStorageService.getPerkCount(player);
        PerkStorageService.clearPerks(player);
        RunManager.syncPlayer(player);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Removed " + removed + " perk(s)."));
    }

    private static void handleGiveAmmo(ServerPlayer player, String data) {
        String[] parts = data.split("\\|", 2);
        String ammoId = resolveAmmoId(player, parts.length == 0 ? "" : parts[0]);
        int amount = parts.length >= 2 ? Integer.parseInt(parts[1]) : 128;
        if (ammoId.isBlank() || amount <= 0) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Missing ammo id or amount."));
            return;
        }

        int remaining = amount;
        int stacks = 0;
        while (remaining > 0) {
            ItemStack stack = RogueItemFactory.createAmmoStack(player, ammoId);
            if (stack.isEmpty()) {
                player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Failed to create ammo: " + ammoId));
                return;
            }
            int stackCount = Math.max(1, Math.min(remaining, stack.getCount()));
            stack.setCount(stackCount);
            ShopPlacementService.placePurchasedItem(player, stack, ammoId, 0);
            remaining -= stackCount;
            stacks++;
        }
        RunManager.syncPlayer(player);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Gave ammo " + ammoId + " x" + amount + " (" + stacks + " stack(s))"));
    }

    private static void handleSetFloorCleared(ServerPlayer player, String data) {
        PlayerRunData run = RunManager.getData(player);
        boolean cleared = Boolean.parseBoolean(data);
        run.setFloorCleared(cleared);
        if (run.getCurrentFloor() > 0) run.setRunActive(true);
        RunManager.syncPlayer(player);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Floor cleared=" + cleared));
    }

    private static void handleSetFloor(ServerPlayer player, String data) {
        int floor = Math.max(1, Integer.parseInt(data));
        PlayerRunData run = RunManager.getData(player);
        run.setCurrentFloor(floor);
        run.setMaxReachedFloor(Math.max(run.getMaxReachedFloor(), floor));
        run.setRunActive(true);
        run.setFloorCleared(false);
        run.setFloorStartTick(player.server.getTickCount());
        RunManager.syncPlayer(player);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Current floor=" + floor));
    }

    private static void handleSetMaxFloor(ServerPlayer player, String data) {
        int floor = Math.max(0, Integer.parseInt(data));
        PlayerRunData run = RunManager.getData(player);
        run.setMaxReachedFloor(floor);
        if (run.getCurrentFloor() > floor) run.setCurrentFloor(floor);
        RunManager.syncPlayer(player);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Max floor=" + floor));
    }

    private static void handleSetFlashlightLevel(ServerPlayer player, String data) {
        int level = Math.max(0, Math.min(GameConstants.FLASHLIGHT_MAX_LEVEL, Integer.parseInt(data)));
        player.getPersistentData().putInt("TacRogueFlashlightLevel", level);
        RunManager.syncPlayer(player);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Flashlight level=" + level));
    }

    private static void handleReloadInfo(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains("GunId")) {
            player.sendSystemMessage(Component.literal("\u00A7e[DEBUG] Hold a TacZ gun in main hand."));
            return;
        }
        WeaponRarity.Rarity rarity = WeaponRarity.getRarity(stack);
        float autoloaderEffect = PerkDefinition.sumCategoryEffect(player, PerkDefinition.Category.AUTOLOADER);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Reload rarity="
            + rarity.name()
            + " rarityMult=" + String.format(Locale.ROOT, "%.2f", WeaponRarity.getReloadMult(stack))
            + " effectiveMult=" + String.format(Locale.ROOT, "%.2f", WeaponRarity.getEffectiveReloadMult(stack, player))
            + " fireRateMult=" + String.format(Locale.ROOT, "%.2f", WeaponRarity.getFireRateMult(stack))
            + " fireRateEffective=" + String.format(Locale.ROOT, "%.2f", WeaponRarity.getEffectiveFireRateMult(stack, player))
            + " autoloader=" + String.format(Locale.ROOT, "%.1f/s",
                autoloaderEffect > 0.0f ? PerkDefinition.getAutoloaderRoundsPerSecond(autoloaderEffect) : 0.0f)));
        try {
            var state = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player).getSynReloadState();
            player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] TacZ reloadState="
                + state.getStateType()
                + " countDown=" + state.getCountDown()));
        } catch (Throwable ex) {
            player.sendSystemMessage(Component.literal("\u00A7e[DEBUG] TacZ reload state unavailable: " + ex.getClass().getSimpleName()));
        }
    }

    private static void handleSpawnBoss(ServerPlayer player, String data) {
        TacRogueBossEntity.BossRole role = TacRogueBossEntity.BossRole.valueOf(
            (data == null || data.isBlank() ? "BREACHER" : data.trim()).toUpperCase(Locale.ROOT));
        TacRogueBossEntity boss = ModEntities.TAC_ROGUE_BOSS.get().create(player.level());
        if (boss == null) {
            player.sendSystemMessage(Component.literal("\u00A7c[DEBUG] Failed to create boss."));
            return;
        }
        PlayerRunData run = RunManager.getData(player);
        int floor = Math.max(1, run.getCurrentFloor());
        int biome = switch (role) {
            case COMMANDER -> 1;
            case VOID_WARDEN -> 2;
            case PYRO -> 4;
            case LEVIATHAN -> 5;
            default -> 0;
        };
        net.minecraft.world.phys.Vec3 look = player.getLookAngle().normalize();
        net.minecraft.world.phys.Vec3 pos = player.position().add(look.scale(6.0D));
        boss.setPos(pos.x, player.getY(), pos.z);
        boss.configureForFloor(floor, biome, role);
        boss.setTarget(player);
        player.level().addFreshEntity(boss);
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Spawned boss " + role.name()));
    }

    private static void handleBossInfo(ServerPlayer player) {
        java.util.List<TacRogueBossEntity> bosses = player.level().getEntitiesOfClass(
            TacRogueBossEntity.class,
            player.getBoundingBox().inflate(96.0D),
            TacRogueBossEntity::isAlive);
        if (bosses.isEmpty()) {
            player.sendSystemMessage(Component.literal("\u00A7e[DEBUG] No active tac_rogue boss nearby."));
            return;
        }
        TacRogueBossEntity boss = bosses.get(0);
        net.minecraft.nbt.CompoundTag tag = boss.getPersistentData();
        player.sendSystemMessage(Component.literal("\u00A7a[DEBUG] Boss "
            + boss.getRole().name()
            + " HP=" + String.format(Locale.ROOT, "%.1f/%.1f", boss.getHealth(), boss.getMaxHealth())
            + " shockwave=" + tag.getInt(TacRogueBossEntity.SHOCKWAVE_COUNT_KEY)
            + " summon=" + tag.getInt(TacRogueBossEntity.SUMMON_COUNT_KEY)
            + " pressure=" + tag.getInt(TacRogueBossEntity.PRESSURE_COUNT_KEY)));
    }

    private static String resolveAmmoId(ServerPlayer player, String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.isEmpty() || id.equalsIgnoreCase("current")) {
            ItemStack stack = player.getMainHandItem();
            if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("GunId")) {
                String ammo = TacZRegistryHelper.getAmmoForGun(stack.getTag().getString("GunId"));
                if (ammo != null && !ammo.isBlank()) return ammo;
            }
            java.util.List<String> allAmmo = TacZRegistryHelper.getAllAmmoIds();
            return allAmmo.isEmpty() ? "" : allAmmo.get(0);
        }
        return id.contains(":") ? id : "tacz:" + id;
    }
}
