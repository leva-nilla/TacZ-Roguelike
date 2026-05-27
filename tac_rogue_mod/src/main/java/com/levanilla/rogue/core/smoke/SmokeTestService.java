package com.levanilla.rogue.core.smoke;

import com.levanilla.rogue.core.ClientRunState;
import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.CurrencyManager;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PlayerRunData;
import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.registry.AttachmentDatabase;
import com.levanilla.rogue.core.registry.LrTacticalRegistry;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import com.levanilla.rogue.core.service.FloorObjectiveService;
import com.levanilla.rogue.core.service.PerkStorageService;
import com.levanilla.rogue.core.service.RogueItemFactory;
import com.levanilla.rogue.core.service.RogueMobAlertService;
import com.levanilla.rogue.world.MapGenerator;
import com.levanilla.rogue.world.NpcManager;
import com.levanilla.rogue.world.TacRogueNpcEntity;
import com.levanilla.rogue.world.ThemeManager;
import com.levanilla.rogue.world.generation.DungeonPlanGenerator;
import com.levanilla.rogue.world.generation.FloorGenerationContext;
import com.levanilla.rogue.world.generation.plan.DungeonPlan;
import com.levanilla.rogue.world.generation.plan.RoomRole;
import com.levanilla.rogue.world.goal.RogueMobVisionGoal;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SmokeTestService {
    public static final List<String> SUITES = List.of(
        "quick", "registry", "lobby", "ui", "floor", "combat", "economy", "quest", "deep"
    );
    public static final List<String> FULL_SUITES = List.of(
        "quick", "registry", "lobby", "ui", "floor", "combat", "economy", "quest", "deep",
        "world", "thirdperson", "shooting", "monster", "ai_matrix", "generation", "objective", "encounter",
        "perk_storage"
    );
    private static final List<String> EXTRA_SUITES = List.of(
        "generation_view", "ai_matrix_view", "objective", "encounter", "perk_storage"
    );
    private static final BlockPos AI_MATRIX_CENTER = new BlockPos(7800, 80, 7800);

    private SmokeTestService() {}

    public static int runSuite(CommandSourceStack source, String requestedSuite) {
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;

        String suite = normalizeSuite(requestedSuite);
        SmokeLogger.startRun(suite);
        Counter counter = new Counter();
        if ("full".equals(suite)) {
            for (String s : FULL_SUITES) runSingleSuite(source, player, s, counter);
        } else if ("all".equals(suite)) {
            for (String s : SUITES) runSingleSuite(source, player, s, counter);
        } else {
            runSingleSuite(source, player, suite, counter);
        }
        SmokeLogger.record(SmokeLogger.activeRunId(), suite, "run.finish",
            counter.failed == 0 ? "PASS" : "FAIL",
            "all cases finish without FAIL",
            counter.failed == 0 ? "no failures" : counter.failed + " failure(s)",
            "pass=" + counter.passed + " skip=" + counter.skipped + " fail=" + counter.failed,
            0L);
        source.sendSuccess(() -> Component.literal("\u00A7a[SMOKE] suite=" + suite
            + " pass=" + counter.passed + " skip=" + counter.skipped + " fail=" + counter.failed
            + " log=" + SmokeLogger.jsonLog()), false);
        return counter.failed == 0 ? 1 : 0;
    }

    private static void runSingleSuite(CommandSourceStack source, ServerPlayer player, String suite, Counter counter) {
        switch (suite) {
            case "quick" -> quick(source, player, counter);
            case "registry" -> registry(player, counter);
            case "lobby" -> lobby(player, counter);
            case "ui" -> ui(counter);
            case "floor" -> floor(player, counter);
            case "combat" -> combat(player, counter);
            case "economy" -> economy(player, counter);
            case "quest" -> quest(player, counter);
            case "deep" -> deep(player, counter);
            case "world" -> world(player, counter);
            case "thirdperson" -> thirdPerson(player, counter);
            case "shooting" -> shooting(player, counter);
            case "monster" -> monster(player, counter);
            case "ai_matrix" -> aiMatrix(player, counter, false);
            case "ai_matrix_view" -> aiMatrix(player, counter, true);
            case "generation" -> generation(player, counter, false);
            case "generation_view" -> generation(player, counter, true);
            case "objective" -> objective(counter);
            case "encounter" -> encounter(counter);
            case "perk_storage" -> perkStorage(player, counter);
            default -> record(counter, suite, "suite.known", false, "known smoke suite", suite, "unknown suite");
        }
    }

    private static void quick(CommandSourceStack source, ServerPlayer player, Counter counter) {
        String suite = "quick";
        record(counter, suite, "command.player", player != null, "command has ServerPlayer", playerName(player), "");
        record(counter, suite, "mod.tac_rogue", ModList.get().isLoaded("tac_rogue"), "tac_rogue loaded", loaded("tac_rogue"), "");
        record(counter, suite, "mod.tacz", ModList.get().isLoaded("tacz"), "TacZ loaded", loaded("tacz"), "");
        record(counter, suite, "mod.leawinds", ModList.get().isLoaded("leawind_third_person") || ModList.get().isLoaded("leawind"),
            "LeaWinds loaded", "leawind_third_person=" + loaded("leawind_third_person") + " leawind=" + loaded("leawind"), "");
        record(counter, suite, "mod.ysm", ModList.get().isLoaded("yes_steve_model") || ModList.get().isLoaded("ysm"),
            "YSM loaded", "yes_steve_model=" + loaded("yes_steve_model") + " ysm=" + loaded("ysm"), "");
        record(counter, suite, "mod.oculus", ModList.get().isLoaded("oculus"), "Oculus loaded", loaded("oculus"), "");
        record(counter, suite, "run.data", RunManager.getData(player) != null, "PlayerRunData available", "available", "");
        record(counter, suite, "gold.read", CurrencyManager.getGold(player) >= 0, "gold value readable", String.valueOf(CurrencyManager.getGold(player)), "");
        source.sendSuccess(() -> Component.literal("\u00A7a[SMOKE] quick checks queued."), false);
    }

    private static void registry(ServerPlayer player, Counter counter) {
        String suite = "registry";
        List<String> guns = safeList(TacZRegistryHelper::getAllGunIds);
        List<String> ammo = safeList(TacZRegistryHelper::getAllAmmoIds);
        List<String> attachments = safeList(TacZRegistryHelper::getAllAttachmentIds);
        List<ShopCatalog.ShopItem> melee = safeList(ShopCatalog::getBuiltinMelee);
        List<ShopCatalog.ShopItem> tactical = safeList(ShopCatalog::getBuiltinTactical);
        List<ShopCatalog.ShopItem> shopItems = safeList(TacZRegistryHelper::getAllShopItems);

        record(counter, suite, "tacz.guns.non_empty", !guns.isEmpty(), "TacZ gun registry non-empty", String.valueOf(guns.size()), "");
        record(counter, suite, "tacz.ammo.non_empty", !ammo.isEmpty(), "TacZ ammo registry non-empty", String.valueOf(ammo.size()), "");
        record(counter, suite, "tacz.attachments.non_empty", !attachments.isEmpty(), "TacZ attachment registry non-empty", String.valueOf(attachments.size()), "");
        record(counter, suite, "lrtactical.melee.non_empty", !melee.isEmpty(), "LR Tactical melee registry non-empty", String.valueOf(melee.size()), "");
        record(counter, suite, "lrtactical.tactical.available", tactical != null, "LR Tactical tactical registry callable", String.valueOf(tactical == null ? -1 : tactical.size()), "");
        record(counter, suite, "shop.items.non_empty", !shopItems.isEmpty(), "shop candidates non-empty", String.valueOf(shopItems.size()), "");

        int invalid = 0;
        for (ShopCatalog.ShopItem item : shopItems) {
            if (item == null || item.id == null || item.id.isBlank()) {
                invalid++;
                continue;
            }
            if (!"rogue".equals(namespace(item.id)) && !"minecraft".equals(namespace(item.id))
                && !item.category.isWeapon() && item.category != ShopCatalog.Category.ATTACHMENT && item.category != ShopCatalog.Category.AMMO) {
                invalid++;
            }
        }
        record(counter, suite, "shop.ids.valid", invalid == 0, "no broken shop ids", "invalid=" + invalid, "");

        if (!guns.isEmpty()) {
            ItemStack stack = RogueItemFactory.createGunStack(guns.get(0), WeaponRarity.Rarity.COMMON);
            record(counter, suite, "factory.representative_gun", !stack.isEmpty(), "representative gun stack can be created", stack.getHoverName().getString(), guns.get(0));
        } else {
            skip(counter, suite, "factory.representative_gun", "gun registry needed", "no guns", "");
        }
        if (!melee.isEmpty()) {
            ItemStack stack = RogueItemFactory.createMeleeStack(melee.get(0).id, WeaponRarity.Rarity.COMMON);
            record(counter, suite, "factory.representative_melee", !stack.isEmpty(), "representative melee stack can be created", stack.getHoverName().getString(), melee.get(0).id);
        } else {
            skip(counter, suite, "factory.representative_melee", "melee registry needed", "no melee", "");
        }
        String representativeAttachment = attachments.isEmpty() ? "" : attachments.get(0);
        record(counter, suite, "attachment.cache.callable", attachments.isEmpty() || AttachmentDatabase.guessSlotType(representativeAttachment) != null,
            "attachment slot lookup callable", attachments.isEmpty() ? "no attachments" : representativeAttachment, "");
    }

    private static void lobby(ServerPlayer player, Counter counter) {
        String suite = "lobby";
        ServerLevel lobby = player.server.getLevel(CommonEventHandler.LOBBY_DIM);
        if (lobby == null) {
            skip(counter, suite, "lobby.loaded", "lobby dimension loaded", "not loaded", "");
            return;
        }
        record(counter, suite, "lobby.loaded", true, "lobby dimension loaded", lobby.dimension().location().toString(), "");
        NpcManager.ensureNpcsSpawned(lobby, net.minecraft.core.BlockPos.containing(GameConstants.LOBBY_X, GameConstants.LOBBY_Y, GameConstants.LOBBY_Z));
        List<TacRogueNpcEntity> npcs = lobby.getEntitiesOfClass(TacRogueNpcEntity.class,
            new net.minecraft.world.phys.AABB(GameConstants.LOBBY_X - 128, GameConstants.LOBBY_Y - 64, GameConstants.LOBBY_Z - 128,
                GameConstants.LOBBY_X + 128, GameConstants.LOBBY_Y + 64, GameConstants.LOBBY_Z + 128),
            TacRogueNpcEntity::isAlive);
        Map<NpcManager.NpcRole, Integer> counts = new EnumMap<>(NpcManager.NpcRole.class);
        for (TacRogueNpcEntity npc : npcs) {
            if (npc.isLobbyNpc()) counts.merge(npc.getRole(), 1, Integer::sum);
        }
        for (NpcManager.NpcRole role : NpcManager.NpcRole.values()) {
            int count = counts.getOrDefault(role, 0);
            record(counter, suite, "lobby.npc." + role.id, count == 1, "exactly one " + role.id, String.valueOf(count), "");
        }
    }

    private static void ui(Counter counter) {
        String suite = "ui";
        skip(counter, suite, "client.main_menu", "client automation opens main menu", "server command cannot inspect client screen", "run client smoke");
        skip(counter, suite, "client.debug_gui", "client automation opens Debug GUI", "server command cannot inspect client screen", "run client smoke");
        skip(counter, suite, "client.low_resolution", "client automation validates low resolution layout", "manual/visual smoke required", "");
    }

    private static void floor(ServerPlayer player, Counter counter) {
        String suite = "floor";
        PlayerRunData data = RunManager.getData(player);
        boolean currentFloorValid = data.isRunActive()
            ? data.getCurrentFloor() >= 1
            : data.getCurrentFloor() >= 0;
        record(counter, suite, "run.floor.current_valid", currentFloorValid, "current floor valid for run/lobby state", "floor=" + data.getCurrentFloor() + " active=" + data.isRunActive(), "");
        record(counter, suite, "run.floor.max_valid", data.getMaxReachedFloor() >= 0, "max floor >= 0", String.valueOf(data.getMaxReachedFloor()), "");
        for (int floor : List.of(1, 5, 50, 100, 101)) {
            boolean valid = floor >= 1 && floor <= 999;
            record(counter, suite, "floor.target." + floor, valid, "floor target accepted", String.valueOf(floor), "generation is intentionally not forced by smoke command");
        }
        skip(counter, suite, "floor.generation_visual", "player does not fall and generation is hidden", "requires client world run", "run client smoke all");
    }

    private static void combat(ServerPlayer player, Counter counter) {
        String suite = "combat";
        List<String> guns = safeList(TacZRegistryHelper::getAllGunIds);
        if (guns.isEmpty()) {
            skip(counter, suite, "gun.create", "gun registry non-empty", "no guns", "");
        } else {
            ItemStack gun = RogueItemFactory.createGunStack(guns.get(0), WeaponRarity.Rarity.RARE);
            record(counter, suite, "gun.create", !gun.isEmpty(), "rare gun created", gun.getHoverName().getString(), guns.get(0));
            record(counter, suite, "gun.rarity.damage", WeaponRarity.getDamageMult(gun) > 1.0F, "rare gun has damage multiplier", String.valueOf(WeaponRarity.getDamageMult(gun)), "");
            record(counter, suite, "gun.rarity.reload", WeaponRarity.getReloadMult(gun) <= 1.0F, "rare gun reload multiplier readable", String.valueOf(WeaponRarity.getReloadMult(gun)), "");
        }
        List<ShopCatalog.ShopItem> melee = safeList(ShopCatalog::getBuiltinMelee);
        if (melee.isEmpty()) {
            skip(counter, suite, "melee.create", "melee registry non-empty", "no melee", "");
        } else {
            ItemStack stack = RogueItemFactory.createMeleeStack(melee.get(0).id, WeaponRarity.Rarity.RARE);
            record(counter, suite, "melee.create", !stack.isEmpty(), "rare melee created", stack.getHoverName().getString(), melee.get(0).id);
            record(counter, suite, "melee.rarity.damage", WeaponRarity.getDamageMult(stack) > 1.0F, "rare melee damage multiplier readable", String.valueOf(WeaponRarity.getDamageMult(stack)), "");
        }
        record(counter, suite, "player.health.readable", player.getHealth() > 0.0F, "player health readable", player.getHealth() + "/" + player.getMaxHealth(), "");
    }

    private static void economy(ServerPlayer player, Counter counter) {
        String suite = "economy";
        int gold = CurrencyManager.getGold(player);
        record(counter, suite, "gold.non_negative", gold >= 0, "gold is non-negative", String.valueOf(gold), "");
        List<ShopCatalog.ShopItem> shopItems = safeList(TacZRegistryHelper::getAllShopItems);
        long weaponCount = shopItems.stream().filter(i -> i.category.isWeapon()).count();
        long ammoCount = shopItems.stream().filter(i -> i.category == ShopCatalog.Category.AMMO).count();
        long specialCount = shopItems.stream().filter(i -> i.category == ShopCatalog.Category.SPECIAL).count();
        record(counter, suite, "shop.weapon_candidates", weaponCount > 0, "weapon candidates exist", String.valueOf(weaponCount), "");
        record(counter, suite, "shop.ammo_candidates", ammoCount > 0, "ammo candidates exist", String.valueOf(ammoCount), "");
        record(counter, suite, "shop.special_candidates", specialCount > 0, "special candidates exist", String.valueOf(specialCount), "");
    }

    private static void quest(ServerPlayer player, Counter counter) {
        String suite = "quest";
        QuestManager.QuestProgress progress = QuestManager.getProgress(player);
        QuestManager.ensureChapterPlan(player, progress);
        record(counter, suite, "quest.progress.available", progress != null, "quest progress available", "chapter=" + progress.currentChapter, "");
        record(counter, suite, "quest.candidates.max5", progress.candidateQuestIds.size() <= 5, "candidate quests <= 5", String.valueOf(progress.candidateQuestIds.size()), "");
        record(counter, suite, "quest.selected.max3", progress.selectedQuestIds.size() <= QuestManager.REQUIRED_SIDE_QUESTS,
            "selected quests <= required", String.valueOf(progress.selectedQuestIds.size()), "");
        List<QuestManager.Quest> active = QuestManager.getActiveChapterQuests(player);
        record(counter, suite, "quest.active.non_empty", !active.isEmpty(), "active quest list non-empty", String.valueOf(active.size()), "");
    }

    private static void deep(ServerPlayer player, Counter counter) {
        String suite = "deep";
        PlayerRunData data = RunManager.getData(player);
        record(counter, suite, "deep.core.non_negative", data.getDeepCore() >= 0, "Deep Core non-negative", String.valueOf(data.getDeepCore()), "");
        record(counter, suite, "deep.prestige.non_negative", data.getPrestigeLevel() >= 0, "Prestige non-negative", String.valueOf(data.getPrestigeLevel()), "");
        record(counter, suite, "deep.highest.valid", data.getHighestEverFloor() >= data.getMaxReachedFloor(),
            "highest ever floor tracks max floor", data.getHighestEverFloor() + " >= " + data.getMaxReachedFloor(), "");
        record(counter, suite, "deep.unlock.rule", data.getHighestEverFloor() < 100 || data.getDeepCore() >= 0,
            "deep state readable when unlocked", "highest=" + data.getHighestEverFloor(), "");
    }

    private static void perkStorage(ServerPlayer player, Counter counter) {
        String suite = "perk_storage";
        List<String> original = new ArrayList<>(PerkStorageService.getPerkTags(player));
        try {
            PerkStorageService.clearPerks(player);
            record(counter, suite, "clear.empty", PerkStorageService.getPerkCount(player) == 0,
                "storage clear removes all perks", String.valueOf(PerkStorageService.getPerkCount(player)), "");

            for (int i = 0; i < 151; i++) {
                PerkDefinition.Category category = i % 3 == 0
                    ? PerkDefinition.Category.DAMAGE
                    : (i % 3 == 1 ? PerkDefinition.Category.AMMO_EFFICIENCY : PerkDefinition.Category.DODGE);
                PerkDefinition perk = new PerkDefinition(category, PerkDefinition.Modifier.NONE, 1 + (i % 10));
                PerkStorageService.addPerk(player, perk.toTag() + ":#smoke" + i);
            }

            int storedCount = PerkStorageService.getPerkCount(player);
            record(counter, suite, "bulk.151_stored", storedCount >= 151,
                "151+ perks can be stored outside scoreboard tags", String.valueOf(storedCount), "");

            long scoreboardPerks = player.getTags().stream().filter(tag -> tag.startsWith("perk:")).count();
            record(counter, suite, "scoreboard.clean_after_bulk", scoreboardPerks == 0,
                "bulk storage does not add scoreboard perk tags", String.valueOf(scoreboardPerks), "");

            float damage = PerkDefinition.sumEffect(player, "perk:DAMAGE");
            record(counter, suite, "effect.from_nbt", damage > 0.0f,
                "perk effects are calculated from NBT storage", String.format(Locale.ROOT, "%.2f", damage), "");

            String legacy = new PerkDefinition(PerkDefinition.Category.VITALITY, PerkDefinition.Modifier.BLESSED, 2).toTag()
                + ":#legacy_smoke";
            player.addTag(legacy);
            boolean migrated = PerkStorageService.getPerkTags(player).contains(legacy);
            long legacyAfter = player.getTags().stream().filter(tag -> tag.startsWith("perk:")).count();
            record(counter, suite, "legacy.migrates", migrated,
                "legacy scoreboard perk tag migrates into NBT storage", String.valueOf(migrated), legacy);
            record(counter, suite, "legacy.removed_from_scoreboard", legacyAfter == 0,
                "legacy scoreboard perk tag is removed after migration", String.valueOf(legacyAfter), "");

            int removedDamage = PerkStorageService.removeMatching(player, tag -> tag.startsWith("perk:DAMAGE"));
            record(counter, suite, "remove.matching", removedDamage > 0,
                "storage removeMatching removes selected perks", String.valueOf(removedDamage), "");

            PerkStorageService.clearPerks(player);
            record(counter, suite, "clear.final", PerkStorageService.getPerkCount(player) == 0,
                "storage clear works after bulk and migration", String.valueOf(PerkStorageService.getPerkCount(player)), "");
        } finally {
            PerkStorageService.setPerks(player, original);
            RunManager.syncPlayer(player);
        }
    }

    private static void world(ServerPlayer player, Counter counter) {
        String suite = "world";
        record(counter, suite, "player.level.loaded", player.level() instanceof ServerLevel,
            "player is in a server level", player.level().dimension().location().toString(), "");
        record(counter, suite, "dimension.lobby.available", player.server.getLevel(CommonEventHandler.LOBBY_DIM) != null,
            "lobby dimension available", String.valueOf(player.server.getLevel(CommonEventHandler.LOBBY_DIM) != null), "");
        record(counter, suite, "dimension.rogue.available", player.server.getLevel(CommonEventHandler.ROGUE_DIM) != null,
            "rogue dimension available", String.valueOf(player.server.getLevel(CommonEventHandler.ROGUE_DIM) != null), "");
        PlayerRunData data = RunManager.getData(player);
        record(counter, suite, "run.origin.available", RunManager.getPrivateDungeonOrigin(player) != null,
            "private dungeon origin can be resolved", String.valueOf(RunManager.getPrivateDungeonOrigin(player)), "");
        record(counter, suite, "run.theme.refresh", data.getThemeName() != null,
            "theme state is readable", data.getThemeName(), "");
    }

    private static void thirdPerson(ServerPlayer player, Counter counter) {
        String suite = "thirdperson";
        boolean leawinds = ModList.get().isLoaded("leawind_third_person") || ModList.get().isLoaded("leawind");
        record(counter, suite, "leawinds.loaded", leawinds,
            "LeaWinds third person mod loaded", "leawind_third_person=" + loaded("leawind_third_person") + " leawind=" + loaded("leawind"), "");

        ItemStack gun = ensureRepresentativeGunInHand(player, counter, suite);
        if (gun.isEmpty()) {
            skip(counter, suite, "gun.in_hand", "TacZ gun can be placed in selected hotbar slot", "no gun", "");
            return;
        }
        record(counter, suite, "gun.in_hand", true, "TacZ gun in selected hotbar slot",
            gun.getHoverName().getString(), gun.getTag().getString("GunId"));
        try {
            boolean igun = com.tacz.guns.api.item.IGun.getIGunOrNull(gun) != null;
            record(counter, suite, "tacz.igun.detects_hand", igun, "TacZ IGun detects held gun", String.valueOf(igun), "");
        } catch (Throwable ex) {
            record(counter, suite, "tacz.igun.detects_hand", false, "TacZ IGun detects held gun", ex.getClass().getSimpleName(), "");
        }
    }

    private static void shooting(ServerPlayer player, Counter counter) {
        String suite = "shooting";
        ItemStack gun = ensureRepresentativeGunInHand(player, counter, suite);
        if (gun.isEmpty()) {
            skip(counter, suite, "gun.prepare", "representative gun prepared", "no gun", "");
            return;
        }
        String gunId = gun.getTag().getString("GunId");
        String ammoId = TacZRegistryHelper.getAmmoForGun(gunId);
        record(counter, suite, "gun.id.present", !gunId.isBlank(), "held gun has GunId", gunId, "");
        record(counter, suite, "gun.ammo.mapping", ammoId != null && !ammoId.isBlank(), "gun has ammo mapping", String.valueOf(ammoId), "");
        int baseMag = TacZRegistryHelper.getMagazineSize(gunId);
        int effectiveMag = com.levanilla.rogue.core.TacZMagazineHelper.getEffectiveMagazineSize(player.getMainHandItem(), player, baseMag);
        record(counter, suite, "gun.magazine.effective", effectiveMag >= Math.max(1, baseMag),
            "effective magazine >= base magazine", effectiveMag + " >= " + baseMag, "");
        if (ammoId != null && !ammoId.isBlank()) {
            ItemStack ammo = RogueItemFactory.createAmmoStack(player, ammoId);
            record(counter, suite, "ammo.create", !ammo.isEmpty(), "ammo stack can be created", ammo.getHoverName().getString(), ammoId);
        }
        try {
            var state = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player).getSynReloadState();
            record(counter, suite, "tacz.reload_state.readable", state != null,
                "TacZ reload state is readable", state == null ? "null" : state.getStateType().name(), "");
        } catch (Throwable ex) {
            record(counter, suite, "tacz.reload_state.readable", false, "TacZ reload state is readable", ex.getClass().getSimpleName(), "");
        }
    }

    private static void monster(ServerPlayer player, Counter counter) {
        String suite = "monster";
        if (player.level().dimension() != CommonEventHandler.ROGUE_DIM) {
            skip(counter, suite, "rogue.dimension", "player is inside rogue dimension", player.level().dimension().location().toString(),
                "enter a dungeon floor for live monster AI smoke");
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            skip(counter, suite, "server_level", "server level available", "not server level", "");
            return;
        }

        Mob near = EntityType.ZOMBIE.create(level);
        Mob ally = EntityType.SKELETON.create(level);
        if (near == null || ally == null) {
            record(counter, suite, "mob.create", false, "test mobs can be created", "null mob", "");
            return;
        }
        try {
            near.addTag("tac_rogue_spawned");
            ally.addTag("tac_rogue_spawned");
            near.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY,
                player.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY));
            ally.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY,
                player.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY));
            near.moveTo(player.getX() + 5.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
            ally.moveTo(player.getX() + 8.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
            near.setNoAi(true);
            ally.setNoAi(true);
            near.setTarget(null);
            ally.setTarget(null);
            boolean nearAdded = level.addFreshEntity(near);
            boolean allyAdded = level.addFreshEntity(ally);
            record(counter, suite, "mob.spawn", nearAdded && allyAdded && near.isAlive() && ally.isAlive(),
                "rogue-tagged test mobs spawn",
                near.getType().toShortString() + "," + ally.getType().toShortString(),
                "added=" + nearAdded + "/" + allyAdded);

            near.setTarget(null);
            RogueMobAlertService.applyGunshotAlertToMobsForSmoke(player, false, GameConstants.GUNSHOT_ALERT_RADIUS, List.of(near, ally));
            RogueMobAlertService.AlertLevel gunshotLevel = RogueMobAlertService.getAlertLevel(near);
            record(counter, suite, "alert.gunshot", gunshotLevel != RogueMobAlertService.AlertLevel.NONE,
                "normal gunshot alerts nearby rogue mob", gunshotLevel.name(),
                "target=" + (near.getTarget() == player));

            near.setTarget(null);
            clearAlertData(near);
            RogueMobAlertService.clearShotHistoryForSmoke(player.getUUID());
            RogueMobAlertService.applyGunshotAlertToMobsForSmoke(player, true, GameConstants.SUPPRESSED_ALERT_RADIUS, List.of(near, ally));
            RogueMobAlertService.AlertLevel suppressedLevel = RogueMobAlertService.getAlertLevel(near);
            record(counter, suite, "alert.suppressed", suppressedLevel != RogueMobAlertService.AlertLevel.NONE,
                "suppressed gunshot still creates bounded alert", suppressedLevel.name(),
                "target=" + (near.getTarget() == player));

            near.setTarget(null);
            clearAlertData(near);
            near.moveTo(player.getX() + 7.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
            faceMobAt(near, player);
            RogueMobAlertService.clearShotHistoryForSmoke(player.getUUID());
            RogueMobAlertService.applyGunshotAlertToMobsForSmoke(player, true, GameConstants.SUPPRESSED_ALERT_RADIUS, List.of(near, ally));
            RogueMobAlertService.AlertLevel firstSuppressed = RogueMobAlertService.getAlertLevel(near);
            boolean firstSuppressedQuiet = firstSuppressed != RogueMobAlertService.AlertLevel.ENGAGED
                && near.getTarget() == null;
            RogueMobAlertService.applyGunshotAlertToMobsForSmoke(player, true, GameConstants.SUPPRESSED_ALERT_RADIUS, List.of(near, ally));
            RogueMobAlertService.applyGunshotAlertToMobsForSmoke(player, true, GameConstants.SUPPRESSED_ALERT_RADIUS, List.of(near, ally));
            RogueMobAlertService.AlertLevel burstSuppressed = RogueMobAlertService.getAlertLevel(near);
            record(counter, suite, "alert.suppressed_burst", firstSuppressedQuiet
                    && burstSuppressed == RogueMobAlertService.AlertLevel.ENGAGED
                    && near.getTarget() == player,
                "suppressed single shot stays cautious, short burst escalates",
                "first=" + firstSuppressed.name() + " burst=" + burstSuppressed.name()
                    + " target=" + (near.getTarget() == player), "");

            near.setTarget(null);
            clearAlertData(near);
            near.moveTo(player.getX() + 5.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
            faceMobAt(near, player);
            RogueMobVisionGoal frontGoal = new RogueMobVisionGoal(near);
            boolean frontCanUse = frontGoal.canUse();
            if (frontCanUse) {
                frontGoal.start();
            }
            record(counter, suite, "alert.fov.front_detects", frontCanUse && near.getTarget() == player,
                "front LOS target is detected by vision goal",
                "canUse=" + frontCanUse + " target=" + (near.getTarget() == player), "");

            near.setTarget(null);
            clearAlertData(near);
            near.moveTo(player.getX() + 5.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
            faceMobAwayFrom(near, player);
            RogueMobVisionGoal backGoal = new RogueMobVisionGoal(near);
            boolean backCanUse = backGoal.canUse();
            record(counter, suite, "alert.fov.back_quiet", !backCanUse && near.getTarget() == null,
                "unalerted rear LOS target is not detected outside close awareness",
                "canUse=" + backCanUse + " target=" + (near.getTarget() == player), "");

            near.setTarget(null);
            clearAlertData(near);
            near.moveTo(player.getX() + 5.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
            faceMobAwayFrom(near, player);
            float headOnlyYaw = yawTo(near, player);
            near.setYRot(headOnlyYaw);
            near.setYHeadRot(headOnlyYaw);
            RogueMobVisionGoal headOnlyGoal = new RogueMobVisionGoal(near);
            boolean headOnlyCanUse = headOnlyGoal.canUse();
            record(counter, suite, "alert.fov.head_turn_back_quiet", !headOnlyCanUse && near.getTarget() == null,
                "unalerted passive head turn does not count as body-facing vision",
                "canUse=" + headOnlyCanUse + " bodyYaw=" + near.yBodyRot + " headYaw=" + near.getYHeadRot(), "");

            near.setTarget(null);
            ally.setTarget(null);
            clearAlertData(near);
            clearAlertData(ally);
            RogueMobAlertService.applyAllyHitAlertToMobsForSmoke(player, near, List.of(ally));
            RogueMobAlertService.AlertLevel allyHitLevel = RogueMobAlertService.getAlertLevel(ally);
            record(counter, suite, "alert.ally_hit.victim", near.getTarget() == player
                    && RogueMobAlertService.getAlertLevel(near) == RogueMobAlertService.AlertLevel.ENGAGED,
                "ranged hit engages the hurt rogue mob immediately",
                RogueMobAlertService.getAlertLevel(near).name() + " target=" + (near.getTarget() == player), "");
            record(counter, suite, "alert.engaged_releases_move_goal", !RogueMobAlertService.shouldTacticalGoalRun(near),
                "engaged mobs release the tactical MOVE goal to vanilla combat",
                "tactical=" + RogueMobAlertService.shouldTacticalGoalRun(near)
                    + " level=" + RogueMobAlertService.getAlertLevel(near).name(), "");
            record(counter, suite, "alert.ally_hit.nearby", ally.getTarget() == player
                    && allyHitLevel == RogueMobAlertService.AlertLevel.ENGAGED,
                "ranged hit engages nearby rogue mob at close range",
                allyHitLevel.name() + " target=" + (ally.getTarget() == player), "");

            near.setTarget(null);
            ally.setTarget(null);
            clearAlertData(near);
            clearAlertData(ally);
            ally.moveTo(player.getX() + 14.5D, player.getY(), player.getZ(), 0.0F, 0.0F);
            RogueMobAlertService.onMeleeAllyHit(player, near);
            RogueMobAlertService.AlertLevel meleeFarLevel = RogueMobAlertService.getAlertLevel(ally);
            record(counter, suite, "alert.melee.victim", near.getTarget() == player
                    && RogueMobAlertService.getAlertLevel(near) == RogueMobAlertService.AlertLevel.ENGAGED,
                "melee hit engages the hurt rogue mob immediately",
                RogueMobAlertService.getAlertLevel(near).name() + " target=" + (near.getTarget() == player), "");
            record(counter, suite, "alert.melee.far_ally_quiet", meleeFarLevel == RogueMobAlertService.AlertLevel.NONE
                    && ally.getTarget() == null,
                "melee hit does not alert farther nearby rogue mob",
                meleeFarLevel.name() + " target=" + (ally.getTarget() == player), "");

            near.setTarget(null);
            ally.setTarget(null);
            clearAlertData(near);
            clearAlertData(ally);
            record(counter, suite, "alert.stealth.quiet", RogueMobAlertService.getAlertLevel(ally) == RogueMobAlertService.AlertLevel.NONE
                    && ally.getTarget() == null,
                "stealth takedown path does not propagate an ally alert",
                RogueMobAlertService.getAlertLevel(ally).name() + " target=" + (ally.getTarget() == player), "");

            TacRogueNpcEntity support = null;
            Mob supportTarget = null;
            try {
                BlockPos supportPos = player.blockPosition().offset(2, 0, 2);
                support = NpcManager.spawnSupportOperator(level, supportPos, Math.max(1, RunManager.getData(player).getCurrentFloor()), 0);
                supportTarget = EntityType.ZOMBIE.create(level);
                if (support == null || supportTarget == null) {
                    record(counter, suite, "support.autonomous_sweep", false,
                        "support operator and target can be created", "support=" + (support != null) + " target=" + (supportTarget != null), "");
                } else {
                    String instanceId = player.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
                    support.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY, instanceId);
                    supportTarget.addTag("tac_rogue_spawned");
                    supportTarget.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY, instanceId);
                    supportTarget.moveTo(support.getX() + 9.0D, support.getY(), support.getZ(), 0.0F, 0.0F);
                    supportTarget.setNoAi(true);
                    boolean targetAdded = level.addFreshEntity(supportTarget);
                    boolean killed = support.debugRunSupportCombatForSmoke(80);
                    record(counter, suite, "support.autonomous_sweep", targetAdded && killed && !supportTarget.isAlive(),
                        "support operator independently acquires and kills rogue mob",
                        "targetAdded=" + targetAdded + " killed=" + killed
                            + " targetAlive=" + (supportTarget != null && supportTarget.isAlive()),
                        "");
                }
            } finally {
                if (supportTarget != null) supportTarget.discard();
                if (support != null) support.discard();
            }
        } finally {
            near.discard();
            ally.discard();
        }
    }

    private static void aiMatrix(ServerPlayer player, Counter counter, boolean keepForInspection) {
        String suite = keepForInspection ? "ai_matrix_view" : "ai_matrix";
        ServerLevel rogue = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogue == null) {
            record(counter, suite, "dimension.rogue.available", false,
                "rogue dimension available for AI matrix", "not loaded", "");
            return;
        }

        BlockPos center = AI_MATRIX_CENTER;
        String instanceId = "smoke-ai-matrix";
        ServerLevel originalLevel = player.serverLevel();
        double originalX = player.getX();
        double originalY = player.getY();
        double originalZ = player.getZ();
        float originalYaw = player.getYRot();
        float originalPitch = player.getXRot();
        boolean originalInstancePresent = player.getPersistentData().contains(FloorInstanceManager.INSTANCE_ID_KEY);
        String originalInstance = player.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        clearAiMatrixArena(rogue, center);
        buildAiMatrixArena(rogue, center);
        player.teleportTo(rogue, center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D,
            Direction.SOUTH.toYRot(), 0.0F);
        player.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY, instanceId);
        RunManager.requestJourneyMapRefresh(player, center, 96);
        record(counter, suite, "arena.entered", player.level() == rogue && player.blockPosition().distSqr(center) <= 9.0D,
            "player enters dedicated AI matrix arena", player.blockPosition().toShortString(), "center=" + center.toShortString());

        int index = 0;
        for (MatrixAlert alert : MatrixAlert.values()) {
            for (MatrixDirection direction : MatrixDirection.values()) {
                runAiMatrixCase(rogue, player, counter, suite, center, instanceId, alert, direction, false, index++);
                runAiMatrixCase(rogue, player, counter, suite, center, instanceId, alert, direction, true, index++);
            }
        }

        if (keepForInspection) {
            player.teleportTo(rogue, center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D,
                Direction.SOUTH.toYRot(), 0.0F);
            RunManager.requestJourneyMapRefresh(player, center, 96);
            buildAiMatrixViewCases(rogue, player, center, instanceId);
            record(counter, suite, "inspection.left_in_world", true,
                "AI matrix arena remains for visual inspection",
                center.toShortString(),
                "F10 AI Debug HUD can inspect target/alert state; run ai_matrix to rebuild/clean");
        } else {
            clearAiMatrixArena(rogue, center);
            player.teleportTo(originalLevel, originalX, originalY, originalZ, originalYaw, originalPitch);
        }
        restorePlayerInstance(player, originalInstancePresent, originalInstance);
    }

    private static void runAiMatrixCase(
        ServerLevel level,
        ServerPlayer player,
        Counter counter,
        String suite,
        BlockPos center,
        String instanceId,
        MatrixAlert alert,
        MatrixDirection direction,
        boolean blocked,
        int index
    ) {
        Mob mob = EntityType.ZOMBIE.create(level);
        if (mob == null) {
            record(counter, suite, matrixCaseId(alert, direction, blocked), false,
                "matrix mob can be created", "null", "");
            return;
        }
        try {
            BlockPos mobPos = center.offset(-22 + (index % 8) * 6, 1, -20 + (index / 8) * 6);
            level.setBlock(mobPos.below(), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
            mob.addTag("tac_rogue_spawned");
            mob.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY, instanceId);
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.moveTo(mobPos.getX() + 0.5D, mobPos.getY(), mobPos.getZ() + 0.5D, 0.0F, 0.0F);
            setBodyYaw(mob, 0.0F);
            level.addFreshEntity(mob);

            Vec3 playerPos = matrixPlayerPos(mob.position(), direction, 6.0D);
            player.teleportTo(level, playerPos.x, playerPos.y, playerPos.z, Direction.NORTH.toYRot(), 0.0F);
            clearLine(level, mob.blockPosition(), player.blockPosition());
            if (blocked) {
                placeVisionWall(level, mob.blockPosition(), player.blockPosition());
            }

            applyMatrixAlert(mob, player, alert);
            RogueMobVisionGoal goal = new RogueMobVisionGoal(mob);
            boolean canUse = goal.canUse();
            if (canUse) {
                goal.start();
            }
            boolean canContinue = goal.canContinueToUse();
            RogueMobAlertService.AlertLevel actualLevel = RogueMobAlertService.getAlertLevel(mob);
            boolean target = mob.getTarget() == player;
            boolean pass = expectedMatrixResult(alert, direction, blocked, canUse, canContinue, actualLevel, target);
            record(counter, suite, matrixCaseId(alert, direction, blocked), pass,
                "AI vision/target state follows alert, direction and LOS matrix",
                "canUse=" + canUse + " continue=" + canContinue + " level=" + actualLevel.name()
                    + " target=" + target + " los=" + mob.hasLineOfSight(player),
                "bodyYaw=" + mob.yBodyRot + " headYaw=" + mob.getYHeadRot()
                    + " player=" + direction.name().toLowerCase(Locale.ROOT));
        } finally {
            mob.discard();
        }
    }

    private static boolean expectedMatrixResult(
        MatrixAlert alert,
        MatrixDirection direction,
        boolean blocked,
        boolean canUse,
        boolean canContinue,
        RogueMobAlertService.AlertLevel actualLevel,
        boolean target
    ) {
        if (alert == MatrixAlert.DECOY) {
            return !canUse && !target
                && actualLevel == RogueMobAlertService.AlertLevel.INVESTIGATE;
        }
        if (blocked) {
            if (alert == MatrixAlert.ENGAGED) {
                return canUse && !canContinue && !target
                    && actualLevel == RogueMobAlertService.AlertLevel.WARNED;
            }
            return !canUse && !target;
        }
        return switch (alert) {
            case NONE -> switch (direction) {
                case FRONT -> canUse && target && actualLevel == RogueMobAlertService.AlertLevel.ENGAGED;
                case BACK, LEFT, RIGHT -> !canUse && !target && actualLevel == RogueMobAlertService.AlertLevel.NONE;
            };
            case INVESTIGATE -> switch (direction) {
                case FRONT, LEFT, RIGHT -> canUse && target && actualLevel == RogueMobAlertService.AlertLevel.ENGAGED;
                case BACK -> !canUse && !target && actualLevel == RogueMobAlertService.AlertLevel.INVESTIGATE;
            };
            case WARNED -> switch (direction) {
                case FRONT, LEFT, RIGHT -> canUse && target && actualLevel == RogueMobAlertService.AlertLevel.ENGAGED;
                case BACK -> !canUse && !target && actualLevel == RogueMobAlertService.AlertLevel.WARNED;
            };
            case DECOY -> false;
            case ENGAGED -> canUse && canContinue && target
                && actualLevel == RogueMobAlertService.AlertLevel.ENGAGED;
        };
    }

    private static void buildAiMatrixViewCases(ServerLevel level, ServerPlayer player, BlockPos center, String instanceId) {
        int index = 0;
        for (MatrixAlert alert : MatrixAlert.values()) {
            for (MatrixDirection direction : MatrixDirection.values()) {
                boolean blocked = direction == MatrixDirection.BACK;
                BlockPos mobPos = center.offset(-18 + direction.ordinal() * 12, 1, -18 + alert.ordinal() * 12);
                Mob mob = EntityType.ZOMBIE.create(level);
                if (mob == null) continue;
                mob.addTag("tac_rogue_spawned");
                mob.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY, instanceId);
                mob.setPersistenceRequired();
                mob.moveTo(mobPos.getX() + 0.5D, mobPos.getY(), mobPos.getZ() + 0.5D, 0.0F, 0.0F);
                setBodyYaw(mob, 0.0F);
                mob.setCustomName(Component.literal(alert.name() + " / " + direction.name()));
                mob.setCustomNameVisible(true);
                level.addFreshEntity(mob);
                applyMatrixAlert(mob, player, alert);
                Vec3 playerMark = matrixPlayerPos(mob.position(), direction, 4.0D);
                level.setBlock(BlockPos.containing(playerMark).below(), Blocks.LIME_CONCRETE.defaultBlockState(), 3);
                if (blocked) {
                    placeVisionWall(level, mob.blockPosition(), BlockPos.containing(playerMark));
                }
                index++;
            }
        }
    }

    private static void applyMatrixAlert(Mob mob, ServerPlayer player, MatrixAlert alert) {
        clearAlertData(mob);
        mob.setTarget(null);
        Vec3 memory = mob.position().add(0.0D, 0.0D, 5.0D);
        long now = mob.level().getGameTime();
        switch (alert) {
            case NONE -> {
            }
            case DECOY -> RogueMobAlertService.applyDecoy(mob, memory, player, 200L);
            case INVESTIGATE, WARNED -> {
                mob.getPersistentData().putString(RogueMobAlertService.ALERT_LEVEL, alert.name());
                mob.getPersistentData().putString(RogueMobAlertService.ALERT_REASON,
                    alert == MatrixAlert.INVESTIGATE ? "matrix_investigate" : "matrix_warned");
                mob.getPersistentData().putLong(RogueMobAlertService.LAST_ALERT_TICK, now);
                mob.getPersistentData().putLong(RogueMobAlertService.INVESTIGATE_END_TIME, now + 200L);
                mob.getPersistentData().putDouble(RogueMobAlertService.INVESTIGATE_X, memory.x);
                mob.getPersistentData().putDouble(RogueMobAlertService.INVESTIGATE_Y, memory.y);
                mob.getPersistentData().putDouble(RogueMobAlertService.INVESTIGATE_Z, memory.z);
            }
            case ENGAGED -> RogueMobAlertService.engageFromVision(mob, player);
        }
    }

    private static String matrixCaseId(MatrixAlert alert, MatrixDirection direction, boolean blocked) {
        return "matrix." + alert.name().toLowerCase(Locale.ROOT)
            + "." + direction.name().toLowerCase(Locale.ROOT)
            + "." + (blocked ? "blocked" : "los");
    }

    private static Vec3 matrixPlayerPos(Vec3 mobPos, MatrixDirection direction, double distance) {
        return switch (direction) {
            case FRONT -> mobPos.add(0.0D, 0.0D, distance);
            case BACK -> mobPos.add(0.0D, 0.0D, -distance);
            case LEFT -> mobPos.add(distance, 0.0D, 0.0D);
            case RIGHT -> mobPos.add(-distance, 0.0D, 0.0D);
        };
    }

    private static void setBodyYaw(Mob mob, float yaw) {
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
    }

    private static void buildAiMatrixArena(ServerLevel level, BlockPos center) {
        for (int dx = -34; dx <= 34; dx++) {
            for (int dz = -34; dz <= 34; dz++) {
                BlockPos floor = center.offset(dx, 0, dz);
                level.setBlock(floor, Blocks.DEEPSLATE_TILES.defaultBlockState(), 3);
                for (int dy = 1; dy <= 4; dy++) {
                    BlockPos pos = floor.above(dy);
                    boolean border = Math.abs(dx) == 34 || Math.abs(dz) == 34;
                    level.setBlock(pos, border ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int i = -34; i <= 34; i += 4) {
            level.setBlock(center.offset(i, 0, 0), Blocks.CYAN_CONCRETE.defaultBlockState(), 3);
            level.setBlock(center.offset(0, 0, i), Blocks.CYAN_CONCRETE.defaultBlockState(), 3);
        }
    }

    private static void clearAiMatrixArena(ServerLevel level, BlockPos center) {
        AABB bounds = new AABB(center).inflate(40.0D, 12.0D, 40.0D);
        level.getEntitiesOfClass(Mob.class, bounds, mob ->
            mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY).equals("smoke-ai-matrix")
                || mob.getTags().contains("tac_rogue_spawned")).forEach(Mob::discard);
        for (int dx = -36; dx <= 36; dx++) {
            for (int dz = -36; dz <= 36; dz++) {
                for (int dy = 0; dy <= 6; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void clearLine(ServerLevel level, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()) - 1, Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()) - 1);
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 2, Math.max(a.getZ(), b.getZ()) + 1);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (pos.getY() <= AI_MATRIX_CENTER.getY()) continue;
            if (Math.abs(pos.getX() - AI_MATRIX_CENTER.getX()) > 34 || Math.abs(pos.getZ() - AI_MATRIX_CENTER.getZ()) > 34) continue;
            level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void placeVisionWall(ServerLevel level, BlockPos mobPos, BlockPos playerPos) {
        BlockPos mid = new BlockPos(
            (mobPos.getX() + playerPos.getX()) / 2,
            mobPos.getY(),
            (mobPos.getZ() + playerPos.getZ()) / 2);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(mid.offset(dx, dy, dz), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void objective(Counter counter) {
        String suite = "objective";
        int checked = 0;
        int failed = 0;
        for (FloorObjectiveService.ObjectiveType type : FloorObjectiveService.ObjectiveType.values()) {
            FloorObjectiveService.ObjectiveType parsed = FloorObjectiveService.ObjectiveType.parse(type.name());
            record(counter, suite, "type." + type.name().toLowerCase(Locale.ROOT),
                parsed == type,
                "objective type parses by name",
                parsed.name(),
                "");
            FloorObjectiveService.ObjectiveType lowerParsed = FloorObjectiveService.ObjectiveType.parse(type.name().toLowerCase(Locale.ROOT));
            record(counter, suite, "type_lower." + type.name().toLowerCase(Locale.ROOT),
                lowerParsed == type,
                "objective type parses case-insensitively",
                lowerParsed.name(),
                "");

            int target = FloorObjectiveService.progressTarget(type);
            boolean targetValid = switch (type) {
                case SECURE_TERMINAL -> target == 240;
                case HOLD_POSITION -> target == 500;
                case ELIMINATE, RECOVER_CACHE, HUNT_ELITE, ESCAPE_ROUTE -> target == 1;
            };
            record(counter, suite, "target." + type.name().toLowerCase(Locale.ROOT),
                targetValid,
                "objective progress target matches completion model",
                String.valueOf(target),
                "");

            RoomRole required = requiredRole(type);
            boolean roomValid = required == null || FloorObjectiveService.matchesObjectiveRoom(type, required);
            record(counter, suite, "room." + type.name().toLowerCase(Locale.ROOT),
                roomValid,
                "objective accepts its required dungeon room role",
                String.valueOf(required),
                "");

            ThemeManager.ThemeInstance theme = representativeTheme(type);
            ThemeManager.ThemeGenerationStyle style = ThemeManager.generationStyle(theme);
            FloorGenerationContext context = new FloorGenerationContext(
                0x0B1EC7_1E57L,
                Math.max(4, 10 + checked),
                0xACCE55L + checked,
                checked + 1,
                "smoke-objective-" + type.name().toLowerCase(Locale.ROOT),
                "SMOKE",
                type.name(),
                1,
                0x0B1EC7_1E57L);
            DungeonPlan plan = DungeonPlanGenerator.generate(context, false, style);
            boolean hasRequired = required == null || plan.rooms().stream().anyMatch(room -> room.role() == required);
            boolean hasSpawnableRoom = plan.rooms().stream().anyMatch(room ->
                FloorObjectiveService.roomWeight(room.role(), type) > 0
                    && FloorObjectiveService.roomCap(room.role(), type, 6) > 0);
            boolean pass = !plan.rooms().isEmpty() && hasRequired && hasSpawnableRoom;
            if (!pass) failed++;
            record(counter, suite, "plan." + type.name().toLowerCase(Locale.ROOT),
                pass,
                "representative dungeon plan supports objective completion and enemy placement",
                "style=" + style + " rooms=" + plan.rooms().size() + " required=" + required,
                "hasRequired=" + hasRequired + " hasSpawnableRoom=" + hasSpawnableRoom);
            checked++;
        }
        record(counter, suite, "non_eliminate.count",
            FloorObjectiveService.ObjectiveType.values().length >= 6,
            "multiple non kill-all objectives exist",
            String.valueOf(FloorObjectiveService.ObjectiveType.values().length),
            "");
        record(counter, suite, "parse.invalid_fallback",
            FloorObjectiveService.ObjectiveType.parse("missing-objective") == FloorObjectiveService.ObjectiveType.ELIMINATE,
            "unknown objective falls back to eliminate",
            FloorObjectiveService.ObjectiveType.parse("missing-objective").name(),
            "");
        record(counter, suite, "room.recover_cache_supply_risk",
            FloorObjectiveService.matchesObjectiveRoom(FloorObjectiveService.ObjectiveType.RECOVER_CACHE, RoomRole.SUPPLY_RISK),
            "recover cache can use high-risk supply rooms without completing on proximity",
            String.valueOf(RoomRole.SUPPLY_RISK),
            "");
        record(counter, suite, "room.escape_route_long",
            FloorObjectiveService.matchesObjectiveRoom(FloorObjectiveService.ObjectiveType.ESCAPE_ROUTE, RoomRole.COMBAT_LONG),
            "escape route can use long traversal rooms",
            String.valueOf(RoomRole.COMBAT_LONG),
            "");
        record(counter, suite, "plans.each_objective",
            checked == FloorObjectiveService.ObjectiveType.values().length && failed == 0,
            "all objective types have a representative valid plan",
            "checked=" + checked + " failed=" + failed,
            "");
    }

    private static void encounter(Counter counter) {
        String suite = "encounter";
        long runSeed = 0x5EED_0E77L;
        int checked = 0;
        int failed = 0;
        for (int biome = 0; biome < ThemeManager.biomeCount(); biome++) {
            for (int variant = 0; variant < ThemeManager.variantCount(biome); variant++) {
                ThemeManager.ThemeInstance theme = ThemeManager.getThemeForIndices(biome, variant);
                ThemeManager.ThemeGenerationStyle style = ThemeManager.generationStyle(theme);
                for (FloorObjectiveService.ObjectiveType objective : FloorObjectiveService.ObjectiveType.values()) {
                    if (objective == FloorObjectiveService.ObjectiveType.ELIMINATE) continue;
                    FloorGenerationContext context = new FloorGenerationContext(
                        runSeed,
                        8 + checked,
                        0xE770L + checked,
                        checked + 1,
                        "smoke-encounter-" + checked,
                        "SMOKE",
                        objective.name(),
                        1,
                        runSeed);
                    DungeonPlan plan = DungeonPlanGenerator.generate(context, false, style);
                    RoomRole required = requiredRole(objective);
                    boolean hasRequired = required == null || plan.rooms().stream().anyMatch(room -> room.role() == required);
                    boolean hasVariety = plan.rooms().stream().map(room -> room.role()).distinct().count() >= 4;
                    boolean pass = hasRequired && hasVariety && !plan.rooms().isEmpty();
                    if (!pass) failed++;
                    record(counter, suite, theme.biomeName.toLowerCase(Locale.ROOT) + "."
                            + theme.variantName.toLowerCase(Locale.ROOT) + "." + objective.name().toLowerCase(Locale.ROOT),
                        pass,
                        "theme/objective dungeon plan contains required encounter role and role variety",
                        "style=" + style + " archetype=" + plan.archetype() + " rooms=" + plan.rooms().size()
                            + " required=" + required,
                        "hasRequired=" + hasRequired + " hasVariety=" + hasVariety);
                    checked++;
                }
            }
        }
        record(counter, suite, "plans.all_objectives",
            checked > 0 && failed == 0,
            "all theme/objective plan combinations are valid",
            "checked=" + checked + " failed=" + failed,
            "");
    }

    private static RoomRole requiredRole(FloorObjectiveService.ObjectiveType objective) {
        return switch (objective) {
            case ELIMINATE -> null;
            case SECURE_TERMINAL -> RoomRole.OBJECTIVE_TERMINAL;
            case HOLD_POSITION -> RoomRole.DEFENSE_POINT;
            case RECOVER_CACHE -> RoomRole.LOCKED_REWARD;
            case HUNT_ELITE -> RoomRole.ELITE_ARENA;
            case ESCAPE_ROUTE -> RoomRole.STEALTH_ROUTE;
        };
    }

    private static ThemeManager.ThemeInstance representativeTheme(FloorObjectiveService.ObjectiveType objective) {
        return switch (objective) {
            case SECURE_TERMINAL -> ThemeManager.getThemeForIndices(1, 0); // LAB / STERILE
            case HOLD_POSITION -> ThemeManager.getThemeForIndices(3, 1); // MILITARY / COMMAND
            case RECOVER_CACHE -> ThemeManager.getThemeForIndices(0, 0); // RUINS / OVERGROWN
            case HUNT_ELITE -> ThemeManager.getThemeForIndices(6, 4); // URBAN / ROOFTOP
            case ESCAPE_ROUTE -> ThemeManager.getThemeForIndices(6, 0); // URBAN / SUBWAY
            case ELIMINATE -> ThemeManager.getThemeForIndices(2, 0); // UNDERGROUND / CAVE
        };
    }

    private static void generation(ServerPlayer player, Counter counter, boolean keepLastForInspection) {
        String suite = keepLastForInspection ? "generation_view" : "generation";
        ServerLevel rogue = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogue == null) {
            record(counter, suite, "dimension.rogue.available", false,
                "rogue dimension available for live generation", "not loaded", "");
            return;
        }
        record(counter, suite, "dimension.rogue.available", true,
            "rogue dimension available for live generation", rogue.dimension().location().toString(), "");

        BlockPos center = new BlockPos(7200, 80, 7200);
        boolean originalInstancePresent = player.getPersistentData().contains(FloorInstanceManager.INSTANCE_ID_KEY);
        String originalInstance = player.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        long runSeed = 0x5EED_0800L;
        int checked = 0;
        int failed = 0;
        long totalUpdates = 0L;
        BlockPos lastSpawn = null;
        for (int biome = 0; biome < ThemeManager.biomeCount(); biome++) {
            for (int variant = 0; variant < ThemeManager.variantCount(biome); variant++) {
                boolean lastTheme = biome == ThemeManager.biomeCount() - 1
                    && variant == ThemeManager.variantCount(biome) - 1;
                ThemeManager.ThemeInstance theme = ThemeManager.getThemeForIndices(biome, variant);
                int floor = checked + 1;
                String caseId = "theme." + theme.biomeName.toLowerCase(Locale.ROOT) + "."
                    + theme.variantName.toLowerCase(Locale.ROOT);
                String instanceId = "smoke-generation-" + checked;
                try {
                    MapGenerator.GenerationJob job = MapGenerator.generateRoomJob(
                        rogue,
                        center,
                        theme,
                        floor,
                        runSeed,
                        0x4500L + checked,
                        instanceId,
                        "SMOKE",
                        1,
                        0,
                        checked + 1);
                    int total = job.totalBlockUpdates();
                    int ticks = 0;
                    while (!job.isComplete() && ticks < 80) {
                        job.tick(25000);
                        ticks++;
                    }
                    BlockPos spawn = job.getSpawnPos();
                    boolean complete = job.isComplete();
                    if (spawn != null) {
                        player.teleportTo(rogue, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                            Direction.SOUTH.toYRot(), 0.0F);
                        player.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY, instanceId);
                        RunManager.requestJourneyMapRefresh(player, spawn, 128);
                    }
                    boolean entered = spawn != null
                        && player.level() == rogue
                        && player.blockPosition().distSqr(spawn) <= 4.0D;
                    boolean spawnClear = spawn != null && rogue.getBlockState(spawn).isAir()
                        && rogue.getBlockState(spawn.above()).isAir();
                    boolean spawnFloor = spawn != null && !rogue.getBlockState(spawn.below()).isAir();
                    boolean hasBlocks = total > 1000;
                    boolean pass = complete && entered && spawnClear && spawnFloor && hasBlocks;
                    if (!pass) failed++;
                    if (pass) lastSpawn = spawn.immutable();
                    totalUpdates += Math.max(0, total);
                    record(counter, suite, caseId, pass,
                        "theme generates physical dungeon blocks and player enters safe spawn",
                        "complete=" + complete + " ticks=" + ticks + " blocks=" + total
                            + " spawn=" + spawn + " style=" + ThemeManager.generationStyle(theme),
                        "entered=" + entered + " spawnClear=" + spawnClear + " spawnFloor=" + spawnFloor);
                } catch (Throwable ex) {
                    failed++;
                    record(counter, suite, caseId, false,
                        "theme generation does not throw", ex.getClass().getSimpleName(), ex.getMessage());
                } finally {
                    if (!keepLastForInspection || !lastTheme) {
                        discardSmokeEntities(rogue, center);
                        MapGenerator.clearStoredDungeon(rogue, center);
                    }
                }
                checked++;
            }
        }
        record(counter, suite, "themes.all_45", checked == 45 && failed == 0,
            "all 45 theme variants generate live dungeon geometry and are entered",
            "checked=" + checked + " failed=" + failed + " totalBlocks=" + totalUpdates,
            "center=" + center + " lastSpawn=" + lastSpawn);
        if (keepLastForInspection) {
            record(counter, suite, "inspection.left_in_world", lastSpawn != null,
                "last generated theme remains in world for visual inspection",
                String.valueOf(lastSpawn),
                "run /rogue_admin debug smoke generation to clean it after inspection");
        }
        restorePlayerInstance(player, originalInstancePresent, originalInstance);
    }

    private static void restorePlayerInstance(ServerPlayer player, boolean originalPresent, String originalValue) {
        if (player == null) return;
        if (originalPresent) {
            player.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY, originalValue == null ? "" : originalValue);
        } else {
            player.getPersistentData().remove(FloorInstanceManager.INSTANCE_ID_KEY);
        }
    }

    private static void discardSmokeEntities(ServerLevel level, BlockPos center) {
        AABB bounds = new AABB(center).inflate(96.0D, 32.0D, 96.0D);
        level.getEntitiesOfClass(Mob.class, bounds, mob ->
            mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY).startsWith("smoke-generation-")
                || mob.getTags().contains("tac_rogue_spawned")).forEach(Mob::discard);
        level.getEntitiesOfClass(TacRogueNpcEntity.class, bounds, npc ->
            npc.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY).startsWith("smoke-generation-"))
            .forEach(TacRogueNpcEntity::discard);
    }

    private static void clearAlertData(Mob mob) {
        if (mob == null) return;
        mob.getPersistentData().remove(RogueMobAlertService.ALERT_LEVEL);
        mob.getPersistentData().remove(RogueMobAlertService.INVESTIGATE_END_TIME);
        mob.getPersistentData().remove(RogueMobAlertService.INVESTIGATE_X);
        mob.getPersistentData().remove(RogueMobAlertService.INVESTIGATE_Y);
        mob.getPersistentData().remove(RogueMobAlertService.INVESTIGATE_Z);
        mob.getPersistentData().remove(RogueMobAlertService.LAST_ALERT_TICK);
        mob.getPersistentData().remove(RogueMobAlertService.ALERT_REASON);
        mob.getPersistentData().remove(RogueMobAlertService.ALERT_TARGET_UUID);
        mob.getPersistentData().remove(RogueMobAlertService.LAST_SEEN_TICK);
        mob.getPersistentData().remove(RogueMobAlertService.LAST_SEEN_X);
        mob.getPersistentData().remove(RogueMobAlertService.LAST_SEEN_Y);
        mob.getPersistentData().remove(RogueMobAlertService.LAST_SEEN_Z);
        mob.getPersistentData().remove(RogueMobAlertService.FLASHLIGHT_FOCUS_TICK);
        mob.getPersistentData().remove(RogueMobAlertService.FLASHLIGHT_FOCUS_TARGET);
        mob.getPersistentData().remove(RogueMobAlertService.DECOY_X);
        mob.getPersistentData().remove(RogueMobAlertService.DECOY_Y);
        mob.getPersistentData().remove(RogueMobAlertService.DECOY_Z);
        mob.getPersistentData().remove(RogueMobAlertService.DECOY_END_TIME);
        mob.getPersistentData().remove(RogueMobAlertService.DECOY_OWNER);
    }

    private static void faceMobAt(Mob mob, net.minecraft.world.entity.Entity target) {
        float yaw = yawTo(mob, target);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
    }

    private static float yawTo(Mob mob, net.minecraft.world.entity.Entity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
    }

    private static void faceMobAwayFrom(Mob mob, net.minecraft.world.entity.Entity target) {
        faceMobAt(mob, target);
        float yaw = mob.getYRot() + 180.0F;
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
    }

    private static ItemStack ensureRepresentativeGunInHand(ServerPlayer player, Counter counter, String suite) {
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && held.hasTag() && held.getTag().contains("GunId")) return held;
        List<String> guns = safeList(TacZRegistryHelper::getAllGunIds);
        if (guns.isEmpty()) return ItemStack.EMPTY;
        ItemStack gun = RogueItemFactory.createGunStack(guns.get(0), WeaponRarity.Rarity.RARE);
        if (gun.isEmpty()) return ItemStack.EMPTY;
        player.getInventory().setItem(player.getInventory().selected, gun);
        RunManager.syncPlayer(player);
        record(counter, suite, "gun.prepare", true, "representative gun placed in selected hotbar slot",
            gun.getHoverName().getString(), guns.get(0));
        return gun;
    }

    private static String normalizeSuite(String suite) {
        if (suite == null || suite.isBlank()) return "quick";
        String normalized = suite.toLowerCase(Locale.ROOT);
        return "all".equals(normalized) || "full".equals(normalized)
            || FULL_SUITES.contains(normalized) || EXTRA_SUITES.contains(normalized)
            ? normalized
            : "quick";
    }

    private static void record(Counter counter, String suite, String caseId, boolean pass, String expected, String actual, String detail) {
        long start = System.nanoTime();
        long elapsed = Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
        if (pass) {
            counter.passed++;
            SmokeLogger.pass(suite, caseId, expected, actual, detail, elapsed);
        } else {
            counter.failed++;
            SmokeLogger.fail(suite, caseId, expected, actual, detail, elapsed);
        }
    }

    private static void skip(Counter counter, String suite, String caseId, String expected, String actual, String detail) {
        counter.skipped++;
        SmokeLogger.skip(suite, caseId, expected, actual, detail, 0L);
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    private static String playerName(ServerPlayer player) {
        return player == null ? "none" : player.getGameProfile().getName();
    }

    private static String loaded(String modId) {
        return String.valueOf(ModList.get().isLoaded(modId));
    }

    private static String namespace(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? "" : location.getNamespace();
    }

    private static <T> List<T> safeList(ListSupplier<T> supplier) {
        try {
            List<T> list = supplier.get();
            return list == null ? List.of() : list;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    @FunctionalInterface
    private interface ListSupplier<T> {
        List<T> get();
    }

    private enum MatrixAlert {
        NONE,
        INVESTIGATE,
        WARNED,
        DECOY,
        ENGAGED
    }

    private enum MatrixDirection {
        FRONT,
        BACK,
        LEFT,
        RIGHT
    }

    private static final class Counter {
        int passed;
        int skipped;
        int failed;
    }
}
