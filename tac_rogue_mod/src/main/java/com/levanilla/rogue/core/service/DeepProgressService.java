package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CurrencyManager;
import com.levanilla.rogue.core.PlayerRunData;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.StaminaManager;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.networking.PopupNotificationMessage;
import com.levanilla.rogue.networking.SyncDataMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 101層以降の継続要素。通常ランの外側に置く小さな進行レイヤー。
 */
public final class DeepProgressService {
    public static final int DEEP_START_FLOOR = 101;
    private static final int BAND_CLEAR_REWARD = 3;
    private static final int DEEP_BOSS_FIRST_REWARD = 8;
    private static final int DEEP_CACHE_COST = 10;
    private static final int REFORGE_BASE_COST = 8;
    private static final int REFORGE_STEP_COST = 4;
    private static final int LOCK_COST = 20;
    private static final int EXTRACT_COST = 12;
    private static final int INFUSE_COST = 24;
    private static final int[] PRESTIGE_UNLOCK_COSTS = {15, 30, 50, 75, 110};
    public static final String ACTION_CACHE = "cache";
    public static final String ACTION_REFORGE = "reforge";
    public static final String ACTION_LOCK = "lock";
    public static final String ACTION_EXTRACT = "extract";
    public static final String ACTION_INFUSE = "infuse";
    public static final String ACTION_DISMANTLE = "dismantle";
    public static final String ACTION_PRESTIGE = "prestige";
    public static final String ACTION_PRESTIGE_UNLOCK = "prestige_unlock";

    public static final String MODIFIER_KEY = "RogueDeepModifier";
    public static final String LOCKED_KEY = "RogueDeepLocked";
    public static final String REFORGE_COUNT_KEY = "RogueDeepReforgeCount";
    public static final String TOKEN_KEY = "RogueDeepModifierToken";

    public enum DeepModifier {
        BREACH,
        SUSTAIN,
        OVERLOAD,
        SCAVENGE,
        FOCUS;

        public String langKey() {
            return "deep_modifier.tac_rogue." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum DeepTaskType {
        BAND_CLEAR(5, 6),
        BOSS_KILL(1, 10),
        LOW_HEALTH_CLEAR(1, 9),
        SPEEDRUN(2, 8),
        WEAPON_MASTERY(30, 8),
        NO_DAMAGE(1, 12);

        final int baseTarget;
        final int reward;

        DeepTaskType(int baseTarget, int reward) {
            this.baseTarget = baseTarget;
            this.reward = reward;
        }

        public String langKey() {
            return "deep_task.tac_rogue." + name().toLowerCase(Locale.ROOT);
        }
    }

    private DeepProgressService() {}

    public static boolean isDeepFloor(int floor) {
        return floor >= DEEP_START_FLOOR;
    }

    public static boolean isUnlocked(PlayerRunData data) {
        return data != null && data.getHighestEverFloor() >= 100;
    }

    public static int deepBand(int floor) {
        return Math.max(0, (Math.max(1, floor) - 1) / 5);
    }

    public static void ensureDeepTask(ServerPlayer player, int floor) {
        if (player == null || !isDeepFloor(floor)) return;
        PlayerRunData data = RunManager.getData(player);
        int band = deepBand(floor);
        if (band <= 0 || data.hasCompletedDeepTaskBand(band)) return;
        if (data.getCurrentDeepBand() == band && !data.getCurrentDeepTaskType().isBlank()) return;

        DeepTaskType task = taskForBand(player, band);
        data.setCurrentDeepBand(band);
        data.setCurrentDeepTaskType(task.name());
        data.setDeepTaskProgress(0);
        data.setDeepTaskTarget(task.baseTarget + Math.max(0, (floor - DEEP_START_FLOOR) / 35));
    }

    public static void advanceDeepTask(ServerPlayer player, DeepTaskType type, int amount) {
        if (player == null || amount <= 0) return;
        PlayerRunData data = RunManager.getData(player);
        if (!isDeepFloor(data.getCurrentFloor()) && data.getCurrentDeepBand() == 0) return;
        ensureDeepTask(player, Math.max(data.getCurrentFloor(), DEEP_START_FLOOR));
        if (!type.name().equals(data.getCurrentDeepTaskType())) return;
        if (data.hasCompletedDeepTaskBand(data.getCurrentDeepBand())) return;

        int next = Math.min(data.getDeepTaskTarget(), data.getDeepTaskProgress() + amount);
        data.setDeepTaskProgress(next);
        if (next >= data.getDeepTaskTarget()) {
            DeepTaskType task = parseTask(data.getCurrentDeepTaskType());
            int reward = task.reward + Math.min(4, Math.max(0, data.getCurrentDeepBand() - 20) / 6);
            data.addDeepCore(reward);
            data.markDeepTaskBandCompleted(data.getCurrentDeepBand());
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.REWARD,
                Component.translatable("popup.tac_rogue.deep.title"),
                Component.translatable("message.tac_rogue.deep_task_complete", reward),
                140
            );
        }
        RunManager.syncPlayer(player);
    }

    public static void awardBandClear(ServerPlayer player, int floor) {
        if (player == null || !isDeepFloor(floor) || floor % 5 != 0) return;
        PlayerRunData data = RunManager.getData(player);
        int band = deepBand(floor);
        if (data.hasCompletedDeepBand(band)) return;
        data.markDeepBandCompleted(band);
        data.addDeepCore(BAND_CLEAR_REWARD);
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.REWARD,
            Component.translatable("popup.tac_rogue.deep.title"),
            Component.translatable("message.tac_rogue.deep_band_clear", BAND_CLEAR_REWARD),
            120
        );
    }

    public static void awardDeepBossFirstKill(ServerPlayer player, int floor) {
        if (player == null || !isDeepFloor(floor)) return;
        PlayerRunData data = RunManager.getData(player);
        data.addDeepCore(DEEP_BOSS_FIRST_REWARD);
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.REWARD,
            Component.translatable("popup.tac_rogue.deep.title"),
            Component.translatable("message.tac_rogue.deep_boss_first", DEEP_BOSS_FIRST_REWARD),
            130
        );
        advanceDeepTask(player, DeepTaskType.BOSS_KILL, 1);
    }

    public static void maybeApplyDeepModifier(ItemStack stack, int floor, RandomSource random, float chance) {
        if (stack == null || stack.isEmpty() || random == null || !isDeepFloor(floor)) return;
        if (!isGun(stack) || hasModifier(stack)) return;
        if (random.nextFloat() > chance) return;
        applyModifier(stack, randomModifier(random));
    }

    public static void applyModifier(ItemStack stack, DeepModifier modifier) {
        if (stack == null || stack.isEmpty() || modifier == null) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(MODIFIER_KEY, modifier.name());
        applyDeepLore(stack, modifier);
    }

    public static boolean hasModifier(ItemStack stack) {
        return getModifier(stack) != null;
    }

    public static DeepModifier getModifier(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return null;
        String raw = stack.getTag().getString(MODIFIER_KEY);
        if (raw == null || raw.isBlank()) return null;
        try {
            return DeepModifier.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static float damageMultiplier(ServerPlayer attacker, LivingEntity target, ItemStack gun, boolean headshot) {
        DeepModifier modifier = getModifier(gun);
        if (modifier == null) return 1.0f;
        return switch (modifier) {
            case BREACH -> isBossOrArmored(target) ? 1.18f : 1.0f;
            case FOCUS -> headshot ? 1.12f : 1.0f;
            default -> 1.0f;
        };
    }

    public static float fireRateMultiplier(ItemStack gun) {
        return getModifier(gun) == DeepModifier.OVERLOAD ? 1.12f : 1.0f;
    }

    public static void onGunKill(ServerPlayer player, ItemStack gun, boolean headshot) {
        DeepModifier modifier = getModifier(gun);
        if (modifier == null) return;
        switch (modifier) {
            case SUSTAIN -> {
                player.heal(Math.max(0.4f, player.getMaxHealth() * 0.025f));
                StaminaManager.setStamina(player, Math.min(StaminaManager.getMaxStamina(player), StaminaManager.getStamina(player) + 5.0f));
            }
            case SCAVENGE -> {
                if (player.getRandom().nextFloat() < 0.18f) {
                    gun.getOrCreateTag().putInt("GunCurrentAmmoCount", gun.getOrCreateTag().getInt("GunCurrentAmmoCount") + 1);
                }
            }
            case FOCUS -> {
                if (headshot) CurrencyManager.addGoldNoQuest(player, 15);
            }
            default -> {}
        }
    }

    public static boolean handleDeepAction(ServerPlayer player, String action) {
        return switch (action) {
            case "deep_cache" -> buyDeepCache(player);
            case "deep_reforge" -> reforgeHeldWeapon(player);
            case "deep_lock" -> lockHeldWeapon(player);
            case "deep_extract" -> extractHeldModifier(player);
            case "deep_infuse" -> infuseHeldWeapon(player);
            case "deep_dismantle" -> dismantleHeldWeapon(player);
            case "prestige" -> prestige(player);
            case "prestige_provision" -> buyPrestigeUnlock(player, "provision");
            case "prestige_prepared" -> buyPrestigeUnlock(player, "prepared");
            case "prestige_selection" -> buyPrestigeUnlock(player, "selection");
            case "prestige_supply_line" -> buyPrestigeUnlock(player, "supply_line");
            case "prestige_black_market" -> buyPrestigeUnlock(player, "black_market");
            default -> false;
        };
    }

    public static boolean handleDeepOperation(ServerPlayer player, String action, int gunSlot, int tokenSlot, String upgradeKey) {
        if (player == null || action == null) return false;
        return switch (action) {
            case ACTION_CACHE -> buyDeepCache(player);
            case ACTION_REFORGE -> reforgeWeapon(player, gunSlot);
            case ACTION_LOCK -> lockWeapon(player, gunSlot);
            case ACTION_EXTRACT -> extractModifier(player, gunSlot);
            case ACTION_INFUSE -> infuseWeapon(player, gunSlot, tokenSlot);
            case ACTION_DISMANTLE -> dismantleWeapon(player, gunSlot);
            case ACTION_PRESTIGE -> prestige(player);
            case ACTION_PRESTIGE_UNLOCK -> buyPrestigeUnlock(player, upgradeKey);
            default -> false;
        };
    }

    public static int deepCacheCost() {
        return DEEP_CACHE_COST;
    }

    public static int lockCost() {
        return LOCK_COST;
    }

    public static int extractCost() {
        return EXTRACT_COST;
    }

    public static int infuseCost() {
        return INFUSE_COST;
    }

    public static int getReforgeCost(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return REFORGE_BASE_COST;
        int count = gun.getOrCreateTag().getInt(REFORGE_COUNT_KEY);
        return REFORGE_BASE_COST + count * REFORGE_STEP_COST;
    }

    public static int getDismantleReward(ItemStack gun) {
        if (!isGun(gun)) return 0;
        return switch (WeaponRarity.getRarity(gun)) {
            case EPIC -> 10;
            case LEGENDARY -> 18;
            default -> 0;
        };
    }

    public static int getPrestigeUnlockCost(int currentLevel) {
        if (currentLevel < 0 || currentLevel >= PRESTIGE_UNLOCK_COSTS.length) return -1;
        return PRESTIGE_UNLOCK_COSTS[currentLevel];
    }

    public static boolean isDeepGun(ItemStack stack) {
        return isGun(stack);
    }

    public static boolean buyDeepCache(ServerPlayer player) {
        PlayerRunData data = RunManager.getData(player);
        if (!isUnlocked(data)) return warn(player, "message.tac_rogue.deep_locked");
        if (!data.consumeDeepCore(DEEP_CACHE_COST)) return warn(player, "message.tac_rogue.deep_core_short", DEEP_CACHE_COST);
        ItemStack reward = rollDeepCache(player, Math.max(data.getCurrentFloor(), data.getHighestEverFloor()));
        if (reward.isEmpty()) {
            data.addDeepCore(DEEP_CACHE_COST);
            return warn(player, "message.tac_rogue.deep_cache_failed");
        }
        ShopPlacementService.placeRewardItem(player, reward, "deep_cache");
        sendDeepCacheResult(player, reward);
        reward(player, "message.tac_rogue.deep_cache_bought", DEEP_CACHE_COST);
        RunManager.syncPlayer(player);
        return true;
    }

    public static boolean reforgeHeldWeapon(ServerPlayer player) {
        return reforgeWeapon(player, player.getInventory().selected);
    }

    public static boolean reforgeWeapon(ServerPlayer player, int gunSlot) {
        ItemStack gun = getInventorySlot(player, gunSlot);
        if (!isGun(gun)) return warn(player, "message.tac_rogue.deep_need_gun");
        if (gun.getOrCreateTag().getBoolean(LOCKED_KEY)) return warn(player, "message.tac_rogue.deep_locked_modifier");
        int count = gun.getOrCreateTag().getInt(REFORGE_COUNT_KEY);
        int cost = getReforgeCost(gun);
        PlayerRunData data = RunManager.getData(player);
        if (!data.consumeDeepCore(cost)) return warn(player, "message.tac_rogue.deep_core_short", cost);
        DeepModifier next = randomModifier(player.getRandom());
        gun.getOrCreateTag().putInt(REFORGE_COUNT_KEY, count + 1);
        applyModifier(gun, next);
        reward(player, "message.tac_rogue.deep_reforged", cost);
        RunManager.syncPlayer(player);
        return true;
    }

    public static boolean lockHeldWeapon(ServerPlayer player) {
        return lockWeapon(player, player.getInventory().selected);
    }

    public static boolean lockWeapon(ServerPlayer player, int gunSlot) {
        ItemStack gun = getInventorySlot(player, gunSlot);
        if (!isGun(gun) || !hasModifier(gun)) return warn(player, "message.tac_rogue.deep_need_modifier");
        PlayerRunData data = RunManager.getData(player);
        if (!data.consumeDeepCore(LOCK_COST)) return warn(player, "message.tac_rogue.deep_core_short", LOCK_COST);
        gun.getOrCreateTag().putBoolean(LOCKED_KEY, true);
        reward(player, "message.tac_rogue.deep_locked_done", LOCK_COST);
        RunManager.syncPlayer(player);
        return true;
    }

    public static boolean extractHeldModifier(ServerPlayer player) {
        return extractModifier(player, player.getInventory().selected);
    }

    public static boolean extractModifier(ServerPlayer player, int gunSlot) {
        ItemStack gun = getInventorySlot(player, gunSlot);
        DeepModifier modifier = getModifier(gun);
        if (!isGun(gun) || modifier == null) return warn(player, "message.tac_rogue.deep_need_modifier");
        PlayerRunData data = RunManager.getData(player);
        if (!data.consumeDeepCore(EXTRACT_COST)) return warn(player, "message.tac_rogue.deep_core_short", EXTRACT_COST);
        gun.shrink(1);
        ShopPlacementService.placeRewardItem(player, createModifierToken(modifier), "deep_modifier_token");
        reward(player, "message.tac_rogue.deep_extracted", EXTRACT_COST);
        RunManager.syncPlayer(player);
        return true;
    }

    public static boolean infuseHeldWeapon(ServerPlayer player) {
        return warn(player, "message.tac_rogue.deep_use_gui");
    }

    public static boolean infuseWeapon(ServerPlayer player, int gunSlot, int tokenSlot) {
        if (gunSlot == tokenSlot) return warn(player, "message.tac_rogue.deep_same_slot");
        ItemStack gun = getInventorySlot(player, gunSlot);
        ItemStack token = getInventorySlot(player, tokenSlot);
        if (!isGun(gun) || WeaponRarity.getRarity(gun).stars < WeaponRarity.Rarity.RARE.stars) {
            return warn(player, "message.tac_rogue.deep_infuse_need_rare");
        }
        DeepModifier modifier = getTokenModifier(token);
        if (modifier == null) return warn(player, "message.tac_rogue.deep_infuse_need_token");
        PlayerRunData data = RunManager.getData(player);
        if (!data.consumeDeepCore(INFUSE_COST)) return warn(player, "message.tac_rogue.deep_core_short", INFUSE_COST);
        token.shrink(1);
        applyModifier(gun, modifier);
        reward(player, "message.tac_rogue.deep_infused", INFUSE_COST);
        RunManager.syncPlayer(player);
        return true;
    }

    public static boolean dismantleHeldWeapon(ServerPlayer player) {
        return dismantleWeapon(player, player.getInventory().selected);
    }

    public static boolean dismantleWeapon(ServerPlayer player, int gunSlot) {
        ItemStack gun = getInventorySlot(player, gunSlot);
        if (!isGun(gun)) return warn(player, "message.tac_rogue.deep_need_gun");
        int reward = getDismantleReward(gun);
        if (reward <= 0) return warn(player, "message.tac_rogue.deep_dismantle_need");
        gun.shrink(1);
        PlayerRunData data = RunManager.getData(player);
        data.addDeepCore(reward);
        reward(player, "message.tac_rogue.deep_dismantled", reward);
        RunManager.syncPlayer(player);
        return true;
    }

    public static boolean prestige(ServerPlayer player) {
        PlayerRunData data = RunManager.getData(player);
        if (data.getMaxReachedFloor() < 100) {
            return warn(player, "message.tac_rogue.prestige_locked");
        }
        CurrencyManager.setGold(player, 0);
        data.addPrestigeLevel(1);
        data.setCurrentFloor(1);
        data.setMaxReachedFloor(1);
        data.setRunActive(false);
        data.setFloorCleared(false);
        data.clearCurrentDeepTask();
        data.clearRunRewardClaims();
        player.removeTag("rogue:gear_selected");
        for (String tag : new ArrayList<>(player.getTags())) {
            if (tag.startsWith("perk:")) player.removeTag(tag);
        }
        RunManager.savePerkTags(player);
        grantFirstPrestigeCache(player, data);
        reward(player, "message.tac_rogue.prestige_done", data.getPrestigeLevel());
        RunManager.syncPlayer(player);
        return true;
    }

    private static void grantFirstPrestigeCache(ServerPlayer player, PlayerRunData data) {
        if (data.isFirstPrestigeCacheClaimed()) return;
        data.setFirstPrestigeCacheClaimed(true);
        data.addDeepCore(15);

        ItemStack cacheReward = createFirstPrestigeEquipment(player, data);
        if (!cacheReward.isEmpty()) {
            ShopPlacementService.placeRewardItem(player, cacheReward, cacheReward.getHoverName().getString());
        }
        ShopPlacementService.placeRewardItem(player, RogueItemFactory.createRecoveryItem("rogue:medkit"), "rogue:medkit");
        ShopPlacementService.placeRewardItem(player, RogueItemFactory.createRecoveryItem("rogue:armor_plate"), "rogue:armor_plate");
        reward(player, "message.tac_rogue.first_prestige_cache", 15);
    }

    private static ItemStack createFirstPrestigeEquipment(ServerPlayer player, PlayerRunData data) {
        RandomSource random = player.getRandom();
        int floor = Math.max(DEEP_START_FLOOR, data.getHighestEverFloor());
        List<ShopCatalog.ShopItem> weapons = RewardSelectionService.weaponCandidates(
            floor,
            9000 + floor * 220,
            List.of(ShopCatalog.Category.RIFLE, ShopCatalog.Category.SMG, ShopCatalog.Category.SHOTGUN,
                ShopCatalog.Category.SNIPER, ShopCatalog.Category.LMG));
        if (!weapons.isEmpty()) {
            ShopCatalog.ShopItem item = weapons.get(random.nextInt(weapons.size()));
            ItemStack stack = RogueItemFactory.createRewardWeaponStack(player, item.id, floor, random);
            if (WeaponRarity.getRarity(stack).ordinal() < WeaponRarity.Rarity.RARE.ordinal()) {
                stack = RogueItemFactory.createGunStack(item.id, WeaponRarity.Rarity.RARE);
            }
            maybeApplyDeepModifier(stack, floor, random, 0.30f);
            return stack;
        }
        List<ShopCatalog.ShopItem> attachments = ShopCatalog.getBuiltinAttachments();
        if (!attachments.isEmpty()) {
            return RogueItemFactory.createAttachmentStack(attachments.get(random.nextInt(attachments.size())).id);
        }
        return ItemStack.EMPTY;
    }

    public static boolean buyPrestigeUnlock(ServerPlayer player, String key) {
        if (!isPrestigeKey(key)) return warn(player, "message.tac_rogue.deep_invalid_operation");
        PlayerRunData data = RunManager.getData(player);
        if (data.getPrestigeLevel() <= 0 && data.getHighestEverFloor() < 100) {
            return warn(player, "message.tac_rogue.prestige_locked");
        }
        int current = data.getPrestigeUnlockLevel(key);
        if (current >= PRESTIGE_UNLOCK_COSTS.length) return warn(player, "message.tac_rogue.prestige_unlock_max");
        if (current >= data.getPrestigeLevel()) return warn(player, "message.tac_rogue.prestige_unlock_needs_prestige", data.getPrestigeLevel());
        int cost = PRESTIGE_UNLOCK_COSTS[current];
        if (!data.consumeDeepCore(cost)) return warn(player, "message.tac_rogue.deep_core_short", cost);
        data.setPrestigeUnlockLevel(key, current + 1);
        reward(player, "message.tac_rogue.prestige_unlock_bought", displayPrestigeKey(key), current + 1, cost);
        RunManager.syncPlayer(player);
        return true;
    }

    public static void sync(ServerPlayer player) {
        PlayerRunData data = RunManager.getData(player);
        ensureDeepTask(player, Math.max(data.getCurrentFloor(), data.getMaxReachedFloor()));
        String payload = "deep:" + data.getDeepCore()
            + ":" + data.getPrestigeLevel()
            + ":" + data.getHighestEverFloor()
            + ":" + data.getCurrentDeepBand()
            + ":" + data.getCurrentDeepTaskType()
            + ":" + data.getDeepTaskProgress()
            + ":" + data.getDeepTaskTarget()
            + ":" + data.getProvisionLevel()
            + ":" + data.getPreparedLevel()
            + ":" + data.getSelectionLevel()
            + ":" + data.getSupplyLineLevel()
            + ":" + data.getBlackMarketLevel();
        TacRogueNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncDataMessage(payload));
    }

    public static int getPrestigeUnlockLevel(ServerPlayer player, String key) {
        return RunManager.getData(player).getPrestigeUnlockLevel(key);
    }

    public static int getSelectionBonus(ServerPlayer player) {
        return Math.min(2, getPrestigeUnlockLevel(player, "selection"));
    }

    public static int getSupplyLineLevel(ServerPlayer player) {
        return getPrestigeUnlockLevel(player, "supply_line");
    }

    public static int getBlackMarketLevel(ServerPlayer player) {
        return getPrestigeUnlockLevel(player, "black_market");
    }

    public static void applyStarterPrestigeBonuses(ServerPlayer player) {
        int provision = getPrestigeUnlockLevel(player, "provision");
        if (provision > 0) {
            CurrencyManager.addGoldNoQuest(player, 250 * provision);
        }
        int prepared = getPrestigeUnlockLevel(player, "prepared");
        for (int i = 0; i < prepared; i++) {
            ShopPlacementService.placePurchasedItem(player, RogueItemFactory.createRecoveryItem("rogue:armor_plate"), "rogue:armor_plate", 0);
            ShopPlacementService.placePurchasedItem(player, RogueItemFactory.createRecoveryItem("rogue:bandage"), "rogue:bandage", 0);
        }
        if (prepared >= 3) {
            ShopPlacementService.placePurchasedItem(player, RogueItemFactory.createRecoveryItem("rogue:medkit"), "rogue:medkit", 0);
        }
    }

    private static ItemStack rollDeepCache(ServerPlayer player, int floor) {
        RandomSource random = player.getRandom();
        int blackMarket = getBlackMarketLevel(player);
        float roll = random.nextFloat();
        if (roll < 0.48f + blackMarket * 0.03f) {
            List<ShopCatalog.ShopItem> weapons = RewardSelectionService.weaponCandidates(
                Math.max(DEEP_START_FLOOR, floor),
                7000 + floor * 260 + blackMarket * 1200,
                List.of(ShopCatalog.Category.RIFLE, ShopCatalog.Category.SMG, ShopCatalog.Category.SHOTGUN,
                    ShopCatalog.Category.SNIPER, ShopCatalog.Category.LMG, ShopCatalog.Category.EXPLOSIVE));
            if (!weapons.isEmpty()) {
                ShopCatalog.ShopItem item = weapons.get(random.nextInt(weapons.size()));
                ItemStack stack = RogueItemFactory.createRewardWeaponStack(player, item.id, Math.max(DEEP_START_FLOOR, floor), random);
                maybeApplyDeepModifier(stack, floor, random, 0.55f + blackMarket * 0.05f);
                return stack;
            }
        }
        List<ShopCatalog.ShopItem> attachments = ShopCatalog.getBuiltinAttachments();
        if (roll < 0.75f && !attachments.isEmpty()) {
            return RogueItemFactory.createAttachmentStack(attachments.get(random.nextInt(attachments.size())).id);
        }
        return random.nextBoolean()
            ? RogueItemFactory.createRecoveryItem("rogue:medkit")
            : RogueItemFactory.createRecoveryItem("rogue:armor_plate");
    }

    private static void sendDeepCacheResult(ServerPlayer player, ItemStack reward) {
        String name = reward.getHoverName().getString();
        String category = classifyDeepCacheReward(reward);
        DeepModifier modifier = getModifier(reward);
        String rarity = isGun(reward) ? WeaponRarity.getRarity(reward).name() : "";
        String meta = modifier == null
            ? rarity
            : (rarity.isBlank() ? modifier.name() : rarity + " / " + modifier.name());
        String payload = "deep_cache_result:" + encode(name) + ":" + encode(category) + ":" + encode(meta);
        TacRogueNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncDataMessage(payload));
    }

    private static String classifyDeepCacheReward(ItemStack reward) {
        if (isGun(reward)) return "gui.tac_rogue.deep.cache.result.weapon";
        if (reward.hasTag() && reward.getTag().getBoolean("rogue_item")) return "gui.tac_rogue.deep.cache.result.supply";
        net.minecraft.resources.ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(reward.getItem());
        String itemId = key == null ? "" : key.toString();
        if (itemId.contains("attachment")) return "gui.tac_rogue.deep.cache.result.attachment";
        return "gui.tac_rogue.deep.cache.result.supply";
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static DeepTaskType taskForBand(ServerPlayer player, int band) {
        DeepTaskType[] values = DeepTaskType.values();
        int index = Math.floorMod(player.getUUID().hashCode() ^ (band * 31), values.length);
        return values[index];
    }

    private static DeepTaskType parseTask(String raw) {
        try {
            return DeepTaskType.valueOf(raw);
        } catch (Exception ignored) {
            return DeepTaskType.BAND_CLEAR;
        }
    }

    private static DeepModifier randomModifier(RandomSource random) {
        DeepModifier[] values = DeepModifier.values();
        return values[random.nextInt(values.length)];
    }

    private static ItemStack getInventorySlot(ServerPlayer player, int slot) {
        if (player == null || slot < 0 || slot >= player.getInventory().items.size()) return ItemStack.EMPTY;
        return player.getInventory().items.get(slot);
    }

    private static boolean isGun(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTag() && stack.getTag().contains("GunId");
    }

    private static boolean isBossOrArmored(LivingEntity target) {
        return target != null
            && (target.getTags().contains("rogue:boss") || target.getArmorValue() > 0);
    }

    private static ItemStack createModifierToken(DeepModifier modifier) {
        ItemStack token = new ItemStack(Items.ECHO_SHARD);
        token.setHoverName(Component.translatable("item.tac_rogue.deep_modifier_token", Component.translatable(modifier.langKey()))
            .withStyle(ChatFormatting.AQUA));
        token.getOrCreateTag().putString(TOKEN_KEY, modifier.name());
        token.getOrCreateTag().putBoolean("rogue_item", true);
        return token;
    }

    public static DeepModifier getTokenModifier(ItemStack token) {
        if (token == null || token.isEmpty() || !token.hasTag()) return null;
        String raw = token.getTag().getString(TOKEN_KEY);
        if (raw.isBlank()) return null;
        try {
            return DeepModifier.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void applyDeepLore(ItemStack stack, DeepModifier modifier) {
        String baseName = stack.getHoverName().getString()
            .replaceAll("§.", "")
            .replaceAll("\\[[A-Z]+\\]\\s*", "")
            .trim();
        stack.setHoverName(Component.empty()
            .append(Component.literal("[" + modifier.name() + "] ").withStyle(ChatFormatting.AQUA))
            .append(Component.literal(baseName).withStyle(WeaponRarity.getRarity(stack).format)));
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = display.getList("Lore", 8);
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable(modifier.langKey()).withStyle(ChatFormatting.AQUA))));
        display.put("Lore", lore);
    }

    private static boolean warn(ServerPlayer player, String key, Object... args) {
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
            Component.translatable("popup.tac_rogue.deep.title"),
            Component.translatable(key, args),
            90);
        return true;
    }

    private static void reward(ServerPlayer player, String key, Object... args) {
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.REWARD,
            Component.translatable("popup.tac_rogue.deep.title"),
            Component.translatable(key, args),
            110);
    }

    private static Component displayPrestigeKey(String key) {
        return Component.translatable("prestige.tac_rogue." + key);
    }

    private static boolean isPrestigeKey(String key) {
        return "provision".equals(key)
            || "prepared".equals(key)
            || "selection".equals(key)
            || "supply_line".equals(key)
            || "black_market".equals(key);
    }
}
