package com.levanilla.rogue.core.command;

import com.levanilla.rogue.core.CurrencyManager;
import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.DifficultyManager;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PlayerRunData;
import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.ShopStockManager;
import com.levanilla.rogue.core.StaminaManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.core.service.FloorService;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import com.levanilla.rogue.core.service.FloorObjectiveService;
import com.levanilla.rogue.core.service.RogueMobAlertService;
import com.levanilla.rogue.core.service.RogueItemFactory;
import com.levanilla.rogue.core.service.ShopPlacementService;
import com.levanilla.rogue.core.smoke.SmokeGenerationWalkService;
import com.levanilla.rogue.core.smoke.SmokeTestService;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.world.LobbyGenerator;
import com.levanilla.rogue.world.MapGenerator;
import com.levanilla.rogue.world.NpcManager;
import com.levanilla.rogue.world.TacRogueNpcEntity;
import com.levanilla.rogue.world.ThemeManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "tac_rogue")
public class RogueAdminCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("rogue_admin")
                .requires(RogueAdminCommand::isAllowed)
                .then(Commands.literal("addgold")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(-1000000, 1000000))
                        .executes(context -> addGold(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))
                    )
                )
                .then(Commands.literal("debug")
                    .executes(context -> openDebugMenu(context.getSource()))
                    .then(Commands.literal("state")
                        .executes(context -> debugState(context.getSource()))
                    )
                    .then(Commands.literal("weapon")
                        .executes(context -> debugWeapon(context.getSource()))
                    )
                    .then(Commands.literal("reloadinfo")
                        .executes(context -> debugReloadInfo(context.getSource()))
                    )
                    .then(Commands.literal("clear_instance")
                        .executes(context -> debugClearPlayerInstance(context.getSource()))
                    )
                    .then(Commands.literal("alert_probe")
                        .executes(context -> debugAlertProbe(context.getSource(), 24, false))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 80))
                            .executes(context -> debugAlertProbe(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "radius"),
                                false))
                            .then(Commands.argument("suppressed", BoolArgumentType.bool())
                                .executes(context -> debugAlertProbe(
                                    context.getSource(),
                                    IntegerArgumentType.getInteger(context, "radius"),
                                    BoolArgumentType.getBool(context, "suppressed"))))
                        )
                    )
                    .then(Commands.literal("stamina")
                        .executes(context -> debugStamina(context.getSource()))
                    )
                    .then(Commands.literal("stamina_trace")
                        .executes(context -> debugStaminaTrace(context.getSource(), 20))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 120))
                            .executes(context -> debugStaminaTrace(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "seconds")))
                        )
                    )
                    .then(Commands.literal("quests")
                        .executes(context -> debugQuests(context.getSource()))
                    )
                    .then(Commands.literal("support_team")
                        .executes(context -> debugSupportTeam(context.getSource(), 3))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 6))
                            .executes(context -> debugSupportTeam(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "count"))))
                    )
                    .then(Commands.literal("perf_start")
                        .executes(context -> debugPerfStart(context.getSource(), 30, "manual"))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                            .executes(context -> debugPerfStart(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "seconds"),
                                "manual"))
                            .then(Commands.argument("label", StringArgumentType.word())
                                .executes(context -> debugPerfStart(
                                    context.getSource(),
                                    IntegerArgumentType.getInteger(context, "seconds"),
                                    StringArgumentType.getString(context, "label")))))
                    )
                    .then(Commands.literal("perf_stop")
                        .executes(context -> debugPerfStop(context.getSource()))
                    )
                    .then(Commands.literal("theme_list")
                        .executes(context -> debugThemeList(context.getSource(), ""))
                        .then(Commands.argument("biome", StringArgumentType.word())
                            .executes(context -> debugThemeList(
                                context.getSource(),
                                StringArgumentType.getString(context, "biome"))))
                    )
                    .then(Commands.literal("theme")
                        .then(Commands.argument("biome", StringArgumentType.word())
                            .then(Commands.argument("variant", StringArgumentType.word())
                                .executes(context -> debugGenerateTheme(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "biome"),
                                    StringArgumentType.getString(context, "variant")))))
                    )
                    .then(Commands.literal("smoke")
                        .executes(context -> debugSmoke(context.getSource(), "quick"))
                        .then(Commands.literal("quick")
                            .executes(context -> debugSmoke(context.getSource(), "quick")))
                        .then(Commands.literal("floor")
                            .executes(context -> debugSmoke(context.getSource(), "floor")))
                        .then(Commands.literal("combat")
                            .executes(context -> debugSmoke(context.getSource(), "combat")))
                        .then(Commands.literal("economy")
                            .executes(context -> debugSmoke(context.getSource(), "economy")))
                        .then(Commands.literal("quest")
                            .executes(context -> debugSmoke(context.getSource(), "quest")))
                        .then(Commands.literal("deep")
                            .executes(context -> debugSmoke(context.getSource(), "deep")))
                        .then(Commands.literal("ui")
                            .executes(context -> debugSmoke(context.getSource(), "ui")))
                        .then(Commands.literal("registry")
                            .executes(context -> debugSmoke(context.getSource(), "registry")))
                        .then(Commands.literal("world")
                            .executes(context -> debugSmoke(context.getSource(), "world")))
                        .then(Commands.literal("thirdperson")
                            .executes(context -> debugSmoke(context.getSource(), "thirdperson")))
                        .then(Commands.literal("shooting")
                            .executes(context -> debugSmoke(context.getSource(), "shooting")))
                        .then(Commands.literal("monster")
                            .executes(context -> debugSmoke(context.getSource(), "monster")))
                        .then(Commands.literal("ai_matrix")
                            .executes(context -> debugSmoke(context.getSource(), "ai_matrix")))
                        .then(Commands.literal("ai_matrix_view")
                            .executes(context -> debugSmoke(context.getSource(), "ai_matrix_view")))
                        .then(Commands.literal("generation")
                            .executes(context -> debugSmoke(context.getSource(), "generation")))
                        .then(Commands.literal("generation_view")
                            .executes(context -> debugSmoke(context.getSource(), "generation_view")))
                        .then(Commands.literal("objective")
                            .executes(context -> debugSmoke(context.getSource(), "objective")))
                        .then(Commands.literal("encounter")
                            .executes(context -> debugSmoke(context.getSource(), "encounter")))
                        .then(Commands.literal("generation_walk")
                            .executes(context -> debugGenerationWalk(context.getSource(), 8))
                            .then(Commands.argument("seconds", IntegerArgumentType.integer(2, 30))
                                .executes(context -> debugGenerationWalk(
                                    context.getSource(),
                                    IntegerArgumentType.getInteger(context, "seconds")))))
                        .then(Commands.literal("generation_stop")
                            .executes(context -> debugGenerationStop(context.getSource())))
                        .then(Commands.literal("all")
                            .executes(context -> debugSmoke(context.getSource(), "all")))
                        .then(Commands.literal("full")
                            .executes(context -> debugSmoke(context.getSource(), "full")))
                    )
                    .then(Commands.literal("floorcleared")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> debugSetFloorCleared(
                                context.getSource(),
                                BoolArgumentType.getBool(context, "value")))
                        )
                    )
                    .then(Commands.literal("setfloor")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 999))
                            .executes(context -> debugSetFloor(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "floor")))
                        )
                    )
                    .then(Commands.literal("objective")
                        .then(Commands.argument("type", StringArgumentType.word())
                            .executes(context -> debugSetObjectiveOverride(
                                context.getSource(),
                                StringArgumentType.getString(context, "type"))))
                    )
                    .then(Commands.literal("enterfloor")
                        .executes(context -> debugEnterFloor(context.getSource()))
                    )
                    .then(Commands.literal("setmaxfloor")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(0, 999))
                            .executes(context -> debugSetMaxFloor(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "floor")))
                        )
                    )
                    .then(Commands.literal("flashlight")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, GameConstants.FLASHLIGHT_MAX_LEVEL))
                            .executes(context -> debugSetFlashlight(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "level")))
                        )
                    )
                    .then(Commands.literal("shop")
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 999))
                            .executes(context -> debugShop(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "floor")))
                        )
                    )
                    .then(Commands.literal("giveammo")
                        .then(Commands.argument("ammoId", ResourceLocationArgument.id())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000000))
                                .executes(context -> debugGiveAmmo(
                                    context.getSource(),
                                    ResourceLocationArgument.getId(context, "ammoId").toString(),
                                    IntegerArgumentType.getInteger(context, "amount")))
                            )
                        )
                    )
                    .then(Commands.literal("givegun")
                        .then(Commands.argument("itemId", ResourceLocationArgument.id())
                            .then(Commands.argument("rarity", StringArgumentType.word())
                                .executes(context -> debugGiveGun(
                                    context.getSource(),
                                    ResourceLocationArgument.getId(context, "itemId").toString(),
                                    StringArgumentType.getString(context, "rarity")))
                            )
                        )
                    )
                    .then(Commands.literal("advancequest")
                        .then(Commands.argument("type", StringArgumentType.word())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000000))
                                .executes(context -> debugAdvanceQuest(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "type"),
                                    IntegerArgumentType.getInteger(context, "amount")))
                            )
                        )
                    )
                    .then(Commands.literal("removeperk")
                        .then(Commands.argument("category", StringArgumentType.word())
                            .then(Commands.argument("modifier", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                    .executes(context -> debugRemovePerk(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "category"),
                                        StringArgumentType.getString(context, "modifier"),
                                        IntegerArgumentType.getInteger(context, "level")))
                                )
                            )
                        )
                    )
                    .then(Commands.literal("clearperks")
                        .executes(context -> debugClearPerks(context.getSource()))
                    )
                    .then(Commands.literal("sync")
                        .executes(context -> debugSync(context.getSource()))
                    )
                    .then(Commands.literal("mp")
                        .executes(context -> debugMpState(context.getSource()))
                        .then(Commands.literal("state")
                            .executes(context -> debugMpState(context.getSource()))
                        )
                        .then(Commands.literal("party_size")
                            .then(Commands.argument("players", IntegerArgumentType.integer(1, 8))
                                .executes(context -> debugMpPartySize(
                                    context.getSource(),
                                    IntegerArgumentType.getInteger(context, "players"))))
                        )
                        .then(Commands.literal("start_now")
                            .executes(context -> debugMpStartNow(context.getSource()))
                        )
                        .then(Commands.literal("complete")
                            .executes(context -> debugMpComplete(context.getSource()))
                        )
                        .then(Commands.literal("virtual_join")
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 7))
                                .executes(context -> debugMpVirtualJoin(
                                    context.getSource(),
                                    IntegerArgumentType.getInteger(context, "count"))))
                        )
                        .then(Commands.literal("leave")
                            .executes(context -> debugMpLeave(context.getSource()))
                        )
                    )
                    .then(Commands.literal("rebuild_lobby")
                        .executes(context -> debugRebuildLobby(context.getSource()))
                    )
                )
        );
    }

    private static boolean isAllowed(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer sp
            && sp.getGameProfile().getName().equalsIgnoreCase("levanilla_");
    }

    private static int addGold(CommandSourceStack source, int amount) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        CurrencyManager.addGoldNoQuest(player, amount);
        RunManager.syncPlayer(player);
        send(source, "Admin gave you " + amount + " Gold.");
        return 1;
    }

    private static int openDebugMenu(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        TacRogueNetworking.openDebugMenu(player);
        send(source, "Opened debug menu.");
        return 1;
    }

    private static int debugState(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        PlayerRunData data = RunManager.getData(player);
        int shopFloor = ShopStockManager.getShopFloor(data.getCurrentFloor(), data.isFloorCleared());
        DifficultyManager.Difficulty difficulty = DifficultyManager.getDifficulty();

        send(source, "State");
        send(source, "gold=" + CurrencyManager.getGold(player)
            + " floor=" + data.getCurrentFloor()
            + " maxFloor=" + data.getMaxReachedFloor()
            + " shopFloor=" + shopFloor);
        send(source, "active=" + data.isRunActive()
            + " cleared=" + data.isFloorCleared()
            + " ammoCapLv=" + data.getAmmoCapacityLevel()
            + " theme=" + data.getThemeName());
        send(source, "difficulty=" + difficulty.displayName
            + " hp=" + difficulty.hpScale
            + " dmg=" + difficulty.dmgScale
            + " gold=" + difficulty.goldScale
            + " drop=" + difficulty.dropScale);
        send(source, "origin=" + data.getDungeonOrigin()
            + " seed=" + data.getRunSeed()
            + " floorStartTick=" + data.getFloorStartTick());
        send(source, "stamina=" + StaminaManager.debugStatus(player));
        return 1;
    }

    private static int debugStamina(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        send(source, "Stamina / ADS fatigue");
        send(source, StaminaManager.debugStatus(player));
        return 1;
    }

    private static int debugStaminaTrace(CommandSourceStack source, int seconds) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        StaminaManager.startAdsDebugTrace(player, seconds);
        send(source, "ADS stamina trace started for " + seconds + "s.");
        return 1;
    }

    private static int debugWeapon(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        send(source, "Weapons");
        reportWeapon(source, "main", player.getMainHandItem());
        reportWeapon(source, "gun0", player.getInventory().items.get(GameConstants.SLOT_GUN_START));
        reportWeapon(source, "gun1", player.getInventory().items.get(GameConstants.SLOT_GUN_END));
        return 1;
    }

    private static int debugReloadInfo(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        reportWeapon(source, "main", player.getMainHandItem());
        try {
            var state = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player).getSynReloadState();
            send(source, "TacZ reloadState=" + state.getStateType()
                + " countDown=" + state.getCountDown());
        } catch (Throwable ex) {
            send(source, "TacZ reload state unavailable: " + ex.getClass().getSimpleName());
        }
        return 1;
    }

    private static int debugAlertProbe(CommandSourceStack source, int radius, boolean suppressed) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        if (player.level().dimension() != CommonEventHandler.ROGUE_DIM || !(player.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("[DEBUG] alert_probe must be run inside rogue dimension."));
            return 0;
        }

        RogueMobAlertService.clearShotHistoryForSmoke(player.getUUID());
        RogueMobAlertService.onGunshot(player, suppressed, radius);
        Vec3 soundPos = player.position();
        String playerInstance = player.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        List<Mob> allMobs = level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(radius), mob ->
            mob.isAlive()
                && mob.distanceTo(player) <= radius);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(radius), mob ->
            mob.isAlive()
                && mob.distanceTo(player) <= radius
                && mob.getTags().contains("tac_rogue_spawned")
                && sameDebugInstance(playerInstance, mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY)));
        mobs.sort(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
        allMobs.sort(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
        long taggedCount = allMobs.stream().filter(mob -> mob.getTags().contains("tac_rogue_spawned")).count();
        long bossCount = allMobs.stream().filter(mob -> mob.getTags().contains("rogue:boss")).count();
        long sameInstanceCount = allMobs.stream()
            .filter(mob -> sameDebugInstance(playerInstance, mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY)))
            .count();

        send(source, "Alert probe radius=" + radius
            + " suppressed=" + suppressed
            + " soundPos=" + formatVec(soundPos)
            + " playerInstance=" + shortenDebug(playerInstance)
            + " mobs=" + mobs.size()
            + " allMobs=" + allMobs.size()
            + " tagged=" + taggedCount
            + " boss=" + bossCount
            + " sameInstance=" + sameInstanceCount);
        if (mobs.isEmpty() && !allMobs.isEmpty()) {
            int debugLimit = Math.min(8, allMobs.size());
            for (int i = 0; i < debugLimit; i++) {
                Mob mob = allMobs.get(i);
                String mobInstance = mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
                send(source, "near#" + mob.getId()
                    + " " + mob.getType().toShortString()
                    + " dist=" + String.format(Locale.ROOT, "%.1f", mob.distanceTo(player))
                    + " tags=" + compactDebugTags(mob)
                    + " instance=" + shortenDebug(mobInstance)
                    + " same=" + sameDebugInstance(playerInstance, mobInstance));
            }
        }
        int limit = Math.min(8, mobs.size());
        for (int i = 0; i < limit; i++) {
            Mob mob = mobs.get(i);
            RogueMobAlertService.AlertLevel alert = RogueMobAlertService.getAlertLevel(mob);
            Vec3 investigate = RogueMobAlertService.getInvestigatePos(mob);
            CompoundTag data = mob.getPersistentData();
            BlockPos targetBlock = BlockPos.containing(investigate);
            boolean targetStandable = isStandable(level, targetBlock);
            boolean pathDirect = mob.getNavigation().createPath(targetBlock, 0) != null;
            boolean navigationActive = !mob.getNavigation().isDone();
            long now = level.getGameTime();
            long end = data.getLong(RogueMobAlertService.INVESTIGATE_END_TIME);
            long losLostSince = data.getLong(RogueMobAlertService.LOS_LOST_SINCE);
            long flashlightFocusTick = data.getLong(RogueMobAlertService.FLASHLIGHT_FOCUS_TICK);
            send(source, "#" + i
                + " " + mob.getType().toShortString()
                + " dist=" + format((float) mob.distanceTo(player))
                + " alert=" + alert.name()
                + " reason=" + data.getString(RogueMobAlertService.ALERT_REASON)
                + " target=" + (mob.getTarget() == player)
                + " los=" + mob.hasLineOfSight(player)
                + " inv=" + formatVec(investigate)
                + " endIn=" + (end > 0L ? end - now : 0L)
                + " lastSeen=" + formatSavedVec(data,
                    RogueMobAlertService.LAST_SEEN_X,
                    RogueMobAlertService.LAST_SEEN_Y,
                    RogueMobAlertService.LAST_SEEN_Z)
                + " losLostFor=" + (losLostSince > 0L ? now - losLostSince : 0L)
                + " flashFocusFor=" + (flashlightFocusTick > 0L ? now - flashlightFocusTick : 0L)
                + " targetUuid=" + data.getString(RogueMobAlertService.ALERT_TARGET_UUID)
                + " standable=" + targetStandable
                + " path=" + pathDirect
                + " navActive=" + navigationActive);
        }
        if (mobs.size() > limit) {
            send(source, "... +" + (mobs.size() - limit) + " more mobs");
        }
        return mobs.isEmpty() ? 0 : 1;
    }

    private static int debugClearPlayerInstance(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        String old = player.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        player.getPersistentData().remove(FloorInstanceManager.INSTANCE_ID_KEY);
        send(source, "Cleared player instance id: " + shortenDebug(old));
        return 1;
    }

    private static int debugQuests(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        QuestManager.QuestProgress progress = QuestManager.getProgress(player);
        List<QuestManager.Quest> quests = QuestManager.getChapterQuests(progress.currentChapter);
        long completedInChapter = quests.stream()
            .filter(q -> progress.completedQuests.contains(q.id))
            .count();

        send(source, "Quests chapter=" + progress.currentChapter
            + " ng+=" + progress.ngPlusLevel
            + " completed=" + completedInChapter + "/" + quests.size()
            + " totalCompleted=" + progress.completedQuests.size());
        for (QuestManager.Quest quest : quests) {
            int current = progress.questProgress.getOrDefault(quest.id, 0);
            boolean complete = progress.completedQuests.contains(quest.id);
            send(source, quest.id
                + " role=" + quest.role.name()
                + " type=" + quest.type.name()
                + " progress=" + current + "/" + quest.targetAmount
                + " complete=" + complete
                + " gold=" + quest.goldReward
                + " rareWeapon=" + quest.rareWeaponReward);
        }
        return 1;
    }

    private static int debugSupportTeam(CommandSourceStack source, int count) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        if (!(player.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("[DEBUG] support_team requires a server level."));
            return 0;
        }

        PlayerRunData data = RunManager.getData(player);
        int floor = Math.max(1, data.getCurrentFloor());
        String instanceId = player.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        FloorInstanceManager.EntryMode mode =
            FloorInstanceManager.EntryMode.parse(player.getPersistentData().getString(FloorInstanceManager.MODE_KEY));
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() < 0.001D) {
            forward = Vec3.directionFromRotation(0.0F, player.getYRot()).multiply(1.0D, 0.0D, 1.0D);
        }
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        BlockPos base = player.blockPosition();

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double side = i - (count - 1) * 0.5D;
            BlockPos wanted = BlockPos.containing(
                base.getX() + 0.5D + forward.x * 3.0D + right.x * side * 1.5D,
                base.getY(),
                base.getZ() + 0.5D + forward.z * 3.0D + right.z * side * 1.5D);
            BlockPos spawnPos = findSupportDebugSpawn(level, wanted, base.getY());
            TacRogueNpcEntity npc = NpcManager.spawnSupportOperator(level, spawnPos, floor, i);
            if (npc == null) continue;
            if (!instanceId.isBlank()) {
                FloorInstanceManager.stampEntity(npc, instanceId, floor, mode, player.getUUID());
            } else {
                npc.getPersistentData().putString(FloorInstanceManager.OWNER_KEY, player.getUUID().toString());
            }
            npc.setYRot(player.getYRot());
            npc.setYHeadRot(player.getYHeadRot());
            npc.prepareSupportGunOperator();
            spawned++;

            ItemStack gun = npc.getMainHandItem();
            String gunId = gun.hasTag() && gun.getTag().contains("GunId")
                ? gun.getTag().getString("GunId")
                : gun.getHoverName().getString();
            send(source, "support#" + npc.getId()
                + " pos=" + npc.blockPosition()
                + " gun=" + gunId
                + " support=" + npc.isSupportOperator());
        }
        send(source, "Spawned support operators=" + spawned
            + " floor=" + floor
            + " instance=" + shortenDebug(instanceId));
        return spawned > 0 ? 1 : 0;
    }

    private static BlockPos findSupportDebugSpawn(ServerLevel level, BlockPos pos, int playerY) {
        for (int dy = 2; dy >= -5; dy--) {
            BlockPos candidate = new BlockPos(pos.getX(), playerY + dy, pos.getZ());
            if (isSupportDebugSpawnSafe(level, candidate)) return candidate;
        }
        return pos;
    }

    private static boolean isSupportDebugSpawnSafe(ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        return !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static int debugPerfStart(CommandSourceStack source, int seconds, String label) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        int duration = Math.max(5, Math.min(600, seconds));
        String safeLabel = (label == null || label.isBlank()) ? "manual" : label.trim();
        TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("perf_start:" + duration + ":" + safeLabel));
        send(source, "FPS trace start seconds=" + duration + " label=" + safeLabel
            + " output=.minecraft/tac_rogue_perf/perf-*.csv");
        return 1;
    }

    private static int debugPerfStop(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("perf_stop"));
        send(source, "FPS trace stop requested.");
        return 1;
    }

    private static int debugSmoke(CommandSourceStack source, String suite) {
        return SmokeTestService.runSuite(source, suite);
    }

    private static int debugGenerationWalk(CommandSourceStack source, int seconds) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        return SmokeGenerationWalkService.start(player, seconds);
    }

    private static int debugGenerationStop(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        return SmokeGenerationWalkService.stop(player, true);
    }

    private static int debugThemeList(CommandSourceStack source, String biomeName) {
        if (biomeName == null || biomeName.isBlank()) {
            send(source, "Theme biomes=" + ThemeManager.biomeNamesCsv());
            send(source, "Use /rogue_admin debug theme_list <biome> to list variants.");
            return 1;
        }
        String variants = ThemeManager.variantNamesCsv(biomeName);
        if (variants.isBlank()) {
            source.sendFailure(Component.literal("[DEBUG] Unknown biome: " + biomeName
                + " options=" + ThemeManager.biomeNamesCsv()));
            return 0;
        }
        send(source, biomeName.toUpperCase(Locale.ROOT) + " variants=" + variants);
        return 1;
    }

    private static int debugGenerateTheme(CommandSourceStack source, String biomeName, String variantName) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        ServerLevel rogue = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogue == null) {
            source.sendFailure(Component.literal("[DEBUG] Rogue dimension is not loaded."));
            return 0;
        }

        ThemeManager.ThemeInstance theme = ThemeManager.getThemeForNames(biomeName, variantName);
        if (theme == null) {
            source.sendFailure(Component.literal("[DEBUG] Unknown theme: " + biomeName + "/" + variantName
                + ". Use /rogue_admin debug theme_list."));
            return 0;
        }

        BlockPos center = new BlockPos(7350, 80, 7350);
        String instanceId = "debug-theme-" + theme.biomeName.toLowerCase(Locale.ROOT)
            + "-" + theme.variantName.toLowerCase(Locale.ROOT);
        MapGenerator.clearStoredDungeon(rogue, center);
        MapGenerator.GenerationJob job = MapGenerator.generateRoomJob(
            rogue,
            center,
            theme,
            1,
            0xD06E_0800L,
            theme.displayName.hashCode(),
            instanceId,
            "DEBUG",
            1,
            0,
            1);
        int ticks = 0;
        while (!job.isComplete() && ticks < 80) {
            job.tick(25000);
            ticks++;
        }
        BlockPos spawn = job.getSpawnPos();
        if (spawn != null) {
            player.teleportTo(rogue, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                Direction.SOUTH.toYRot(), 0.0F);
            player.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY, instanceId);
            RunManager.requestJourneyMapRefresh(player, spawn, 128);
        }

        boolean complete = job.isComplete();
        boolean entered = spawn != null && player.level() == rogue && player.blockPosition().distSqr(spawn) <= 4.0D;
        boolean spawnClear = spawn != null && rogue.getBlockState(spawn).isAir()
            && rogue.getBlockState(spawn.above()).isAir();
        boolean spawnFloor = spawn != null && !rogue.getBlockState(spawn.below()).isAir();
        boolean ok = complete && entered && spawnClear && spawnFloor && job.totalBlockUpdates() > 1000;
        String result = "Generated theme " + theme.biomeName + "/" + theme.variantName
            + " style=" + ThemeManager.generationStyle(theme)
            + " complete=" + complete
            + " entered=" + entered
            + " blocks=" + job.totalBlockUpdates()
            + " ticks=" + ticks
            + " spawn=" + spawn;
        if (ok) {
            send(source, result);
        } else {
            source.sendFailure(Component.literal("[DEBUG] Theme generation check failed. " + result
                + " spawnClear=" + spawnClear + " spawnFloor=" + spawnFloor));
        }
        return ok ? 1 : 0;
    }

    private static int debugSetFloorCleared(CommandSourceStack source, boolean cleared) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        PlayerRunData data = RunManager.getData(player);
        data.setFloorCleared(cleared);
        if (data.getCurrentFloor() > 0) data.setRunActive(true);
        RunManager.syncPlayer(player);
        send(source, "Floor cleared=" + cleared);
        return debugState(source);
    }

    private static int debugSetFloor(CommandSourceStack source, int floor) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        PlayerRunData data = RunManager.getData(player);
        data.setCurrentFloor(floor);
        data.setMaxReachedFloor(Math.max(data.getMaxReachedFloor(), floor));
        data.setRunActive(true);
        data.setFloorCleared(false);
        data.setFloorStartTick(player.server.getTickCount());
        RunManager.syncPlayer(player);
        send(source, "Current floor=" + floor);
        return debugState(source);
    }

    private static int debugSetObjectiveOverride(CommandSourceStack source, String rawType) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        FloorObjectiveService.ObjectiveType type = parseObjectiveStrict(rawType);
        if (type == null) {
            source.sendFailure(Component.literal("Unknown objective: " + rawType
                + " / valid=" + java.util.Arrays.toString(FloorObjectiveService.ObjectiveType.values())));
            return 0;
        }

        FloorObjectiveService.setDebugObjectiveOverride(player.getUUID(), type);
        send(source, "Next generated floor objective=" + type.name());
        return 1;
    }

    private static FloorObjectiveService.ObjectiveType parseObjectiveStrict(String rawType) {
        if (rawType == null || rawType.isBlank()) return null;
        try {
            return FloorObjectiveService.ObjectiveType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int debugEnterFloor(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        FloorService.handleStartNextFloor(player);
        send(source, "Requested floor entry.");
        return 1;
    }

    private static int debugSetMaxFloor(CommandSourceStack source, int floor) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        PlayerRunData data = RunManager.getData(player);
        data.setMaxReachedFloor(floor);
        if (data.getCurrentFloor() > floor) data.setCurrentFloor(floor);
        RunManager.syncPlayer(player);
        send(source, "Max floor=" + floor);
        return debugState(source);
    }

    private static int debugSetFlashlight(CommandSourceStack source, int level) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        int clamped = Math.max(0, Math.min(GameConstants.FLASHLIGHT_MAX_LEVEL, level));
        player.getPersistentData().putInt("TacRogueFlashlightLevel", clamped);
        RunManager.syncPlayer(player);
        send(source, "Flashlight level=" + clamped);
        return 1;
    }

    private static int debugMpState(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        return FloorInstanceManager.debugDescribe(player);
    }

    private static int debugMpPartySize(CommandSourceStack source, int players) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        FloorInstanceManager.debugSetPartySize(player, players);
        send(source, "Virtual party size set to " + players + ". Use this before entering a floor.");
        return 1;
    }

    private static int debugMpStartNow(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        boolean ok = FloorInstanceManager.debugForceStart(player);
        if (!ok) send(source, "No waiting floor instance for current player.");
        return ok ? 1 : 0;
    }

    private static int debugMpComplete(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        boolean ok = FloorInstanceManager.debugForceComplete(player);
        if (!ok) send(source, "No active floor instance for current player.");
        return ok ? 1 : 0;
    }

    private static int debugMpVirtualJoin(CommandSourceStack source, int count) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        boolean ok = FloorInstanceManager.debugApplyVirtualJoin(player, count);
        if (!ok) send(source, "No active floor instance for current player.");
        return ok ? 1 : 0;
    }

    private static int debugMpLeave(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        return FloorInstanceManager.debugLeave(player) ? 1 : 0;
    }

    private static int debugShop(CommandSourceStack source, int floor) {
        List<ShopCatalog.ShopItem> allItems = TacZRegistryHelper.getAllShopItems();
        List<ShopCatalog.ShopItem> available = ShopStockManager.filterAvailable(allItems, floor);
        Map<ShopCatalog.Category, Integer> counts = new EnumMap<>(ShopCatalog.Category.class);
        for (ShopCatalog.ShopItem item : available) {
            counts.merge(item.category, 1, Integer::sum);
        }

        send(source, "Shop floor=" + floor + " available=" + available.size() + "/" + allItems.size());
        send(source, "categories=" + counts);
        int limit = Math.min(12, available.size());
        for (int i = 0; i < limit; i++) {
            ShopCatalog.ShopItem item = available.get(i);
            String rarity = item.category.isWeapon()
                ? " rarity=" + WeaponRarity.rollShopRarity(floor, item.id).name()
                : "";
            send(source, "#" + i
                + " " + item.id
                + " " + item.category.name()
                + " price=" + item.price
                + rarity);
        }
        return 1;
    }

    private static int debugGiveGun(CommandSourceStack source, String itemId, String rarityName) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        WeaponRarity.Rarity rarity = parseRarity(source, rarityName);
        if (rarity == null) return 0;

        String fullId = itemId.contains(":") ? itemId : "tacz:" + itemId;
        ItemStack stack = RogueItemFactory.createGunStack(fullId, rarity);
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("[DEBUG] Failed to create gun: " + fullId));
            return 0;
        }

        ShopPlacementService.placeRewardItem(player, stack, fullId);
        send(source, "Gave " + fullId + " rarity=" + rarity.name());
        reportWeapon(source, "given", stack);
        return 1;
    }

    private static int debugGiveAmmo(CommandSourceStack source, String itemId, int amount) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        String ammoId = itemId.contains(":") ? itemId : "tacz:" + itemId;
        int remaining = amount;
        int stacks = 0;
        while (remaining > 0) {
            ItemStack stack = RogueItemFactory.createAmmoStack(player, ammoId);
            if (stack.isEmpty()) {
                source.sendFailure(Component.literal("[DEBUG] Failed to create ammo: " + ammoId));
                return 0;
            }
            int stackCount = Math.max(1, Math.min(remaining, stack.getCount()));
            stack.setCount(stackCount);
            ShopPlacementService.placePurchasedItem(player, stack, ammoId, 0);
            remaining -= stackCount;
            stacks++;
        }
        RunManager.syncPlayer(player);
        send(source, "Gave ammo " + ammoId + " x" + amount + " stacks=" + stacks);
        return 1;
    }

    private static int debugAdvanceQuest(CommandSourceStack source, String typeName, int amount) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        QuestManager.QuestType type = parseQuestType(source, typeName);
        if (type == null) return 0;

        QuestManager.advanceQuest(player, type, amount);
        RunManager.syncPlayer(player);
        send(source, "Advanced quest type=" + type.name() + " amount=" + amount);
        return debugQuests(source);
    }

    private static int debugSync(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        RunManager.syncPlayer(player);
        send(source, "Synced rogue data.");
        return 1;
    }

    private static int debugRemovePerk(CommandSourceStack source, String categoryName, String modifierName, int level) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        PerkDefinition.Category category;
        PerkDefinition.Modifier modifier;
        try {
            category = PerkDefinition.Category.valueOf(categoryName.toUpperCase(Locale.ROOT));
            modifier = PerkDefinition.Modifier.valueOf(modifierName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("[DEBUG] Unknown perk category or modifier."));
            return 0;
        }

        for (String tag : new java.util.ArrayList<>(player.getTags())) {
            if (!tag.startsWith("perk:")) continue;
            PerkDefinition perk = PerkDefinition.fromTag(tag);
            if (perk.category == category && perk.modifier == modifier && perk.level == level) {
                player.removeTag(tag);
                RunManager.syncPlayer(player);
                send(source, "Removed perk " + perk.getDisplayName());
                return 1;
            }
        }

        source.sendFailure(Component.literal("[DEBUG] Matching perk was not found."));
        return 0;
    }

    private static int debugClearPerks(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        int removed = 0;
        for (String tag : new java.util.ArrayList<>(player.getTags())) {
            if (tag.startsWith("perk:") && player.removeTag(tag)) removed++;
        }
        RunManager.syncPlayer(player);
        send(source, "Removed " + removed + " perk(s).");
        return 1;
    }

    private static int debugRebuildLobby(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        ServerLevel lobbyLevel = player.server.getLevel(CommonEventHandler.LOBBY_DIM);
        if (lobbyLevel == null) {
            source.sendFailure(Component.literal("[DEBUG] Lobby dimension is not loaded."));
            return 0;
        }

        LobbyGenerator.buildLobby(lobbyLevel, LobbyGenerator.DEFAULT_CENTER);
        player.teleportTo(lobbyLevel, GameConstants.LOBBY_X, GameConstants.LOBBY_Y, GameConstants.LOBBY_Z, 0, 0);
        send(source, "Rebuilt lobby layout.");
        return 1;
    }

    private static void reportWeapon(CommandSourceStack source, String label, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            send(source, label + ": empty");
            return;
        }

        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String itemId = itemKey == null ? stack.getItem().toString() : itemKey.toString();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("GunId")) {
            send(source, label + ": item=" + itemId + " name=" + stack.getHoverName().getString());
            return;
        }

        String gunId = tag.getString("GunId");
        WeaponRarity.Rarity rarity = WeaponRarity.getRarity(stack);
        int baseMag = TacZRegistryHelper.getMagazineSize(gunId);
        int effectiveMag = WeaponRarity.getEffectiveMagazineSize(stack, baseMag);
        int currentAmmo = tag.getInt("GunCurrentAmmoCount");
        String ammoId = TacZRegistryHelper.getAmmoForGun(gunId);
        net.minecraft.world.entity.LivingEntity holder =
            source.getEntity() instanceof net.minecraft.world.entity.LivingEntity living ? living : null;

        send(source, label
            + ": item=" + itemId
            + " gun=" + gunId
            + " rarity=" + rarity.name()
            + " ammo=" + currentAmmo + "/" + effectiveMag
            + " baseMag=" + baseMag
            + " ammoId=" + ammoId);
        send(source, label
            + ": damage=" + format(WeaponRarity.getDamageMult(stack))
            + " reload=" + format(WeaponRarity.getReloadMult(stack))
            + " reloadEffective=" + format(WeaponRarity.getEffectiveReloadMult(stack, holder))
            + " fireRate=" + format(WeaponRarity.getFireRateMult(stack))
            + " fireRateEffective=" + format(WeaponRarity.getEffectiveFireRateMult(stack, holder))
            + " autoloader=" + format(getAutoloaderRoundsPerSecond(source))
            + " mag=" + format(WeaponRarity.getMagSizeMult(stack))
            + " tagRarity=" + tag.getInt("RogueRarity"));
    }

    private static float getAutoloaderRoundsPerSecond(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.world.entity.LivingEntity holder)) return 0.0f;
        float effect = PerkDefinition.sumCategoryEffect(holder, PerkDefinition.Category.AUTOLOADER);
        return effect > 0.0f ? PerkDefinition.getAutoloaderRoundsPerSecond(effect) : 0.0f;
    }

    private static WeaponRarity.Rarity parseRarity(CommandSourceStack source, String value) {
        try {
            return WeaponRarity.Rarity.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("[DEBUG] Unknown rarity: " + value
                + " options=COMMON,UNCOMMON,RARE,EPIC,LEGENDARY"));
            return null;
        }
    }

    private static QuestManager.QuestType parseQuestType(CommandSourceStack source, String value) {
        try {
            return QuestManager.QuestType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("[DEBUG] Unknown quest type: " + value
                + " options=KILL_COUNT,FLOOR_CLEAR,HEADSHOT,STEALTH_KILL,BOSS_KILL,GOLD_EARN,SURVIVE,NO_DAMAGE,SPEEDRUN,WEAPON_MASTERY"));
            return null;
        }
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatVec(Vec3 value) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", value.x, value.y, value.z);
    }

    private static String formatSavedVec(CompoundTag data, String xKey, String yKey, String zKey) {
        if (data == null || !data.contains(xKey) || !data.contains(yKey) || !data.contains(zKey)) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f",
            data.getDouble(xKey),
            data.getDouble(yKey),
            data.getDouble(zKey));
    }

    private static boolean sameDebugInstance(String playerInstance, String mobInstance) {
        return playerInstance == null || playerInstance.isBlank()
            || mobInstance == null || mobInstance.isBlank()
            || playerInstance.equals(mobInstance);
    }

    private static String shortenDebug(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.length() <= 14 ? value : value.substring(0, 14);
    }

    private static String compactDebugTags(Mob mob) {
        if (mob == null || mob.getTags().isEmpty()) return "-";
        return mob.getTags().stream()
            .filter(tag -> tag.startsWith("tac_rogue") || tag.startsWith("rogue:"))
            .limit(4)
            .reduce((left, right) -> left + "," + right)
            .orElse("-");
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos.below()).isAir()
            && level.getBlockState(pos).isAir()
            && level.getBlockState(pos.above()).isAir();
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        source.sendFailure(Component.literal("[DEBUG] Player only."));
        return null;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("\u00A7a[DEBUG] " + message), false);
    }
}
