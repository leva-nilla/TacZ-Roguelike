package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.core.PerkDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;



import static com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM;
import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;

/**
 * プレイヤーの毎チック処理：スタミナ、ステルス、インベントリ制限、スロット制約。
 */
@Mod.EventBusSubscriber(modid = "tac_rogue")
public class PlayerTickHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;

        // スタミナの自然回復/消費処理
        float adrenalineBonus = getAdrenalineBonus(player);
        StaminaManager.tick(player, 1.0f + (adrenalineBonus / 100.0f));

        // === 潜伏効果: スニーク/伏せ中はモブの検知範囲を低下 ===
        if (player.level().dimension() == ROGUE_DIM && player.tickCount % GameConstants.STEALTH_CHECK_INTERVAL == 0) {
            applyStealth(player);
        }

        // ゲームモードをアドベンチャーに固定
        if (!player.isCreative() && !player.isSpectator()) {
            if (player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.ADVENTURE) {
                player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            }
        }

        // 空腹状態でもダッシュを許可
        if (player.getFoodData().getFoodLevel() > GameConstants.SPRINT_MIN_FOOD_LEVEL
                && player.getFoodData().getFoodLevel() <= GameConstants.SPRINT_MAX_CHECK_FOOD) {
            if (player.isSprinting()) {
                player.getFoodData().setFoodLevel(GameConstants.SPRINT_RESTORE_FOOD);
            }
        }

        // バニラ自然回復を無効化（カスタム回復システムで置き換え）
        if (player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION)) {
            player.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(false, player.server);
        }

        // ===== カスタム体力回復（ダメージ後5秒間は停止）=====
        if (player.tickCount % 20 == 0) {
            applyCustomHealthRegen(player);
        }

        // 奈落への落下防止
        if (player.getY() < GameConstants.VOID_Y_THRESHOLD) {
            RunManager.returnToLobby(player);
            player.setHealth(player.getMaxHealth());
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.void_return"));
        }

        // ロビーにいてまだ装備を選んでいない場合、定期的に初期装備選択画面を開く
        if (player.level().dimension() == LOBBY_DIM && !player.getTags().contains("rogue:gear_selected")) {
            if (player.tickCount % GameConstants.GEAR_CHECK_INTERVAL == 0) {
                com.levanilla.rogue.networking.TacRogueNetworking.openStarterGear(player);
            }
        }

        // ===== インベントリ制限 + スロット制約 =====
        if (player.tickCount % GameConstants.INV_CHECK_INTERVAL == 0) {
            enforceSlotRestrictions(player);
            enforceAmmoSlotRestrictions(player);
            enforceInventoryLimits(player);
            enforceArmorAndOffhand(player);
            enforceAmmoStackSizes(player);
        }

        // ===== パーク効果の適用 =====
        if (player.tickCount % 20 == 0) {
            applyPerkStats(player);
        }
    }

    // ===== スタミナ / ADRENALINE =====
    
    private static float getAdrenalineBonus(ServerPlayer player) {
        float bonus = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:ADRENALINE")) {
                bonus += PerkDefinition.fromTag(tag).calculateEffect();
            }
        }
        return bonus;
    }

    // ===== ステルス =====

    private static void applyStealth(ServerPlayer player) {
        float stealthExtend = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:STEALTH_EXTEND")) {
                stealthExtend += PerkDefinition.fromTag(tag).calculateEffect();
            }
        }
        boolean isSneaking = player.hasPose(net.minecraft.world.entity.Pose.CROUCHING);
        boolean isCrawling = player.hasPose(net.minecraft.world.entity.Pose.SWIMMING);
        double rangeMul = isCrawling ? GameConstants.STEALTH_CRAWL_RANGE :
                           (isSneaking ? GameConstants.STEALTH_SNEAK_RANGE : 1.0);
        
        if (stealthExtend > 0) {
            rangeMul = Math.max(0.01, rangeMul - (stealthExtend / 100.0));
        }

        AABB stealth = new AABB(player.blockPosition()).inflate(GameConstants.STEALTH_CHECK_RADIUS);
        List<Mob> nearby = player.level().getEntitiesOfClass(Mob.class, stealth, Mob::isAlive);
        for (Mob mob : nearby) {
            AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (followRange != null) {
                followRange.removeModifier(GameConstants.STEALTH_MODIFIER_UUID);
                if (rangeMul < 1.0) {
                    followRange.addTransientModifier(new AttributeModifier(
                        GameConstants.STEALTH_MODIFIER_UUID, "rogue_stealth", rangeMul - 1.0,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            }
        }
    }

    // ===== スロット制約 (スロット0,1=銃のみ / スロット2=近接のみ) =====

    /**
     * 武器スロットに不正なアイテムが入っていた場合、空きスロットへ移動 or ドロップ。
     * - スロット 0, 1: GunId タグを持つアイテムのみ許可
     * - スロット 2: MeleeWeaponId タグを持つアイテムのみ許可
     */
    private static void enforceSlotRestrictions(ServerPlayer player) {
        // スロット 0, 1: 銃のみ
        for (int slot = 0; slot <= 1; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (!isGunItem(stack)) {
                // 銃ではないアイテムを退避
                ejectFromSlot(player, slot);
            }
        }

        // スロット 2: 近接のみ
        {
            ItemStack stack = player.getInventory().getItem(2);
            if (!stack.isEmpty() && !isMeleeItem(stack)) {
                ejectFromSlot(player, 2);
            }
        }
    }

    /** GunId NBT タグを持つアイテム = 銃 */
    private static boolean isGunItem(ItemStack stack) {
        if (!stack.hasTag()) return false;
        return stack.getTag().contains("GunId");
    }

    /** MeleeWeaponId NBT タグを持つアイテム = 近接武器 */
    private static boolean isMeleeItem(ItemStack stack) {
        if (!stack.hasTag()) return false;
        return stack.getTag().contains("MeleeWeaponId");
    }

    /**
     * 指定スロットからアイテムを退避。
     * まずアイテムスロット（3-8）に空きがあればそこへ、なければドロップ。
     * 銃・近接スロットには退避しない（ループ防止）。
     */
    private static void ejectFromSlot(ServerPlayer player, int fromSlot) {
        ItemStack stack = player.getInventory().getItem(fromSlot);
        if (stack.isEmpty()) return;

        // アイテムスロット 3-8 に空きを探す（銃/近接スロットへの退避を防止）
        for (int h = GameConstants.SLOT_ITEM_START; h <= GameConstants.SLOT_ITEM_END; h++) {
            if (player.getInventory().getItem(h).isEmpty()) {
                player.getInventory().setItem(h, stack.copy());
                player.getInventory().setItem(fromSlot, ItemStack.EMPTY);
                return;
            }
        }
        // 空きなし → ドロップ
        player.drop(stack.copy(), true, false);
        player.getInventory().setItem(fromSlot, ItemStack.EMPTY);
    }

    // ===== インベントリ制限 (初期はホットバーのみ) =====

    /**
     * インベントリスロットのロック/アンロックを強制する。
     * ロック境界を超えたスロットにBARRIERが無ければ毎回復元する。
     */
    private static void enforceInventoryLimits(ServerPlayer player) {
        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        // 弾薬スロット(9-12)は常に許可。ロック範囲は13以降から計算
        // 1レベルにつき2スロットをアンロック (最大35まで)
        int maxAllowedIndex = Math.min(GameConstants.SLOT_AMMO_GUN2_END + (invLevel * 2), 35);

        ItemStack lockedSlotItem = new ItemStack(Items.BARRIER);
        lockedSlotItem.setHoverName(net.minecraft.network.chat.Component.literal("\u00A7c[LOCKED]"));
        lockedSlotItem.getOrCreateTag().putBoolean("rogue_item_locked", true);

        for (int i = GameConstants.SLOT_AMMO_GUN2_END + 1; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            boolean isLockedVisual = stack.is(Items.BARRIER) && stack.hasTag()
                && stack.getOrCreateTag().getBoolean("rogue_item_locked");

            if (i > maxAllowedIndex) {
                // ロックされるべきスロット: BARRIERが無ければ復元
                if (!isLockedVisual) {
                    if (!stack.isEmpty()) {
                        boolean returned = false;
                        // アイテムスロット 3-8 に退避（銃/近接スロットへの退避を防止）
                        for (int h = GameConstants.SLOT_ITEM_START; h <= GameConstants.SLOT_ITEM_END; h++) {
                            if (player.getInventory().getItem(h).isEmpty()) {
                                player.getInventory().setItem(h, stack.copy());
                                returned = true;
                                break;
                            }
                        }
                        if (!returned) {
                            player.drop(stack.copy(), true, false);
                        }
                    }
                    player.getInventory().setItem(i, lockedSlotItem.copy());
                }
            } else {
                // アンロックされるべきスロット: BARRIER残置を除去
                if (isLockedVisual) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
                // 拡張スロットに銃・近接武器が入っていたら退避
                if (!stack.isEmpty() && !isLockedVisual) {
                    if (isGunItem(stack) || isMeleeItem(stack)) {
                        ejectFromSlot(player, i);
                    }
                }
            }
        }
    }

    private static void enforceArmorAndOffhand(ServerPlayer player) {
        for (int armorSlot = 36; armorSlot <= 39; armorSlot++) {
            ItemStack armorStack = player.getInventory().getItem(armorSlot);
            if (!armorStack.isEmpty()) {
                player.drop(armorStack.copy(), true, false);
                player.getInventory().setItem(armorSlot, ItemStack.EMPTY);
            }
        }
        ItemStack offhand = player.getInventory().getItem(40);
        if (!offhand.isEmpty()) {
            player.drop(offhand.copy(), true, false);
            player.getInventory().setItem(40, ItemStack.EMPTY);
        }
    }

    /**
     * スロット9-12は弾薬専用。AmmoIdタグを持たないアイテムがあれば退避する。
     */
    private static void enforceAmmoSlotRestrictions(ServerPlayer player) {
        for (int slot = GameConstants.SLOT_AMMO_GUN1_START; slot <= GameConstants.SLOT_AMMO_GUN2_END; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            // LOCKEDアイテムは無視
            if (stack.is(Items.BARRIER) && stack.hasTag() && stack.getOrCreateTag().getBoolean("rogue_item_locked")) continue;
            // AmmoIdタグがなければ退避
            if (!stack.hasTag() || !stack.getTag().contains("AmmoId")) {
                ejectFromSlot(player, slot);
            }
        }
    }

    private static void enforceAmmoStackSizes(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack ammoStack = player.getInventory().getItem(i);
            if (!ammoStack.isEmpty() && ammoStack.hasTag() && ammoStack.getTag().contains("AmmoId")) {
                String ammoId = ammoStack.getTag().getString("AmmoId");
                int maxStack = TacZRegistryHelper.getAmmoStackSize(ammoId);
                if (ammoStack.getCount() > maxStack) {
                    int excess = ammoStack.getCount() - maxStack;
                    ammoStack.setCount(maxStack);
                    ItemStack overflow = ammoStack.copy();
                    overflow.setCount(excess);
                    if (!player.getInventory().add(overflow)) {
                        player.drop(overflow, true, false);
                    }
                }
            }
        }
    }

    // ===== パーク効果のステータス適用 =====

    private static final java.util.UUID PERK_VITALITY_UUID = java.util.UUID.fromString("a1b2c3d4-1111-4444-8888-aabbccddeef0");
    private static final java.util.UUID PERK_ARMOR_UUID    = java.util.UUID.fromString("a1b2c3d4-2222-4444-8888-aabbccddeef1");
    private static final java.util.UUID PERK_VELOCITY_UUID = java.util.UUID.fromString("a1b2c3d4-3333-4444-8888-aabbccddeef2");

    /** 前回のチック時の総弾薬数（AMMO_EFFICIENCY判定用） */
    private static final java.util.Map<java.util.UUID, Integer> lastAmmoCount = new java.util.HashMap<>();

    /**
     * パーク効果をプレイヤー属性に反映する。
     * 毎秒（20tick）ごとに呼び出される。
     */
    private static void applyPerkStats(ServerPlayer player) {
        float vitalityEffect = 0, armorEffect = 0, velocityEffect = 0;
        float staminaEffect = 0, ammoEfficiency = 0;
        int cursedCount = 0, overclockedCount = 0, fracturedCount = 0, primalCount = 0;

        for (String tag : player.getTags()) {
            if (!tag.startsWith("perk:")) continue;
            PerkDefinition perk = PerkDefinition.fromTag(tag);
            float effect = perk.calculateEffect();
            switch (perk.category) {
                case VITALITY        -> vitalityEffect += effect;
                case ARMOR           -> armorEffect += effect;
                case VELOCITY        -> velocityEffect += effect;
                case REGENERATION    -> {} // applyCustomHealthRegen() で処理
                case STAMINA         -> staminaEffect += effect;
                case AMMO_EFFICIENCY -> ammoEfficiency += effect;
                case RELOAD_SPEED    -> {} // applyAutoloader() にて個別に処理（バグ回避のため自動装填化）
                default -> {} // DAMAGE/FORTUNE/RESISTANCE etc は他のハンドラで処理
            }
            // 修飾子トレードオフのカウント
            switch (perk.modifier) {
                case CURSED      -> cursedCount++;
                case OVERCLOCKED -> overclockedCount++;
                case FRACTURED   -> fracturedCount++;
                case PRIMAL      -> primalCount++;
                default -> {}
            }
        }

        // === 修飾子トレードオフの適用 ===
        // CURSED: ランダムで1つのステータスを-10% (各CURSED perkごと)
        if (cursedCount > 0) {
            java.util.Random cursedRng = new java.util.Random(player.getUUID().hashCode());
            for (int c = 0; c < cursedCount; c++) {
                int target = cursedRng.nextInt(4);
                switch (target) {
                    case 0 -> vitalityEffect -= 10.0f; // HP -10%
                    case 1 -> armorEffect -= 5.0f;     // Armor -5
                    case 2 -> velocityEffect -= 10.0f; // Speed -10%
                    case 3 -> staminaEffect -= 10.0f;  // Stamina -10%
                }
            }
        }
        // FRACTURED: 移動速度 -10% per perk
        velocityEffect -= fracturedCount * 10.0f;
        
        // TITANIC
        int titanicCount = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:") && tag.contains(":TITANIC:")) titanicCount++;
        }
        if (titanicCount > 0) {
            velocityEffect -= titanicCount * 30.0f;
            if (player.isSprinting()) player.setSprinting(false);
        }

        // PRIMAL: スタミナ回復速度 -30% → staminaEffect を減少
        staminaEffect -= primalCount * 30.0f;
        // OVERCLOCKED: スタミナ最大値 -50% は下のSTAMINAセクションで処理
        // VOLATILE: 被ダメ +20% はCombatEventHandlerで処理

        // VITALITY → MAX_HEALTH (base 20 + effect%)
        applyPerkModifier(player, Attributes.MAX_HEALTH, PERK_VITALITY_UUID, "perk_vitality",
            vitalityEffect / 100.0, AttributeModifier.Operation.MULTIPLY_BASE);

        // ARMOR → ARMOR (加算)
        applyPerkModifier(player, Attributes.ARMOR, PERK_ARMOR_UUID, "perk_armor",
            armorEffect / 10.0, AttributeModifier.Operation.ADDITION);

        // VELOCITY → MOVEMENT_SPEED (base 0.1 + effect%)
        applyPerkModifier(player, Attributes.MOVEMENT_SPEED, PERK_VELOCITY_UUID, "perk_velocity",
            velocityEffect / 100.0, AttributeModifier.Operation.MULTIPLY_BASE);

        // REGENERATION → パッシブ回復 — applyCustomHealthRegen() に統合済みのため、ここでは効果値のみ使用
        // (applyCustomHealthRegen が CombatEventHandler.getLastDamageTick をチェック)

        // STAMINA → スタミナ最大値を増加
        {
            float baseMax = GameConstants.DEFAULT_MAX_STAMINA;
            float bonus = baseMax * (staminaEffect / 100.0f);
            float newMax = baseMax + bonus;
            // OVERCLOCKED: スタミナ最大値 -50% per perk
            for (int o = 0; o < overclockedCount; o++) {
                newMax *= 0.5f;
            }
            StaminaManager.setMaxStamina(player, Math.max(20.0f, newMax)); // 最低20
        }

        // AMMO_EFFICIENCY → 弾薬が減った場合、確率でロールバック
        if (ammoEfficiency > 0) {
        // ammoEfficiency は TacZEventHandler.onGunFire で処理するように変更しました else {
            // 現在の弾薬数を記録（次回比較用）
            lastAmmoCount.put(player.getUUID(), countTotalAmmo(player));
        }

        detectReloadAndApplyQuickFix(player);
        applyAutoloader(player); // RELOAD_SPEEDによる自動装填
    }

    /**
     * RELOAD_SPEED: アニメーション時間の限界を回避し、さらにマルチプレイ競合バグを消すため、
     * 「所持している銃の弾を時間経過で自動装填する (Autoloader)」システムにリワークしました。
     */
    private static void applyAutoloader(ServerPlayer player) {
        float reloadBonus = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:RELOAD_SPEED")) {
                reloadBonus += PerkDefinition.fromTag(tag).calculateEffect();
            }
        }
        if (reloadBonus <= 0) return;

        // 計算: reloadBonusが20なら、2秒(40tick)に1発？
        // ここは毎秒呼ばれる。1秒間に装填される弾の数は bonus / 10.0f とする。
        // （例：Lv3(=30)なら毎秒3発）
        int bulletsToLoad = Math.max(1, (int)(reloadBonus / 10.0f));

        for (int slot = 0; slot <= 1; slot++) {
            ItemStack gun = player.getInventory().getItem(slot);
            if (gun.isEmpty() || !gun.hasTag()) continue;
            net.minecraft.nbt.CompoundTag tag = gun.getTag();
            if (tag == null || !tag.contains("GunId")) continue;

            String gunIdStr = tag.getString("GunId");
            int current = tag.getInt("GunCurrentAmmoCount");
            int baseMag = getBaseMagFromTacZ(gunIdStr, gun);
            if (baseMag <= 0) continue;
            int maxCap = getEffectiveBaseMag(baseMag, gunIdStr, tag);

            if (current < maxCap) {
                String ammoId = com.levanilla.rogue.core.registry.TacZGunRegistry.getAmmoForGun(gunIdStr);
                // インベントリから弾を探して減らす
                int loaded = 0;
                for (int i = 0; i < bulletsToLoad; i++) {
                    if (current + loaded >= maxCap) break;
                    if (consumeAmmoFromInventory(player, ammoId)) {
                        loaded++;
                    } else {
                        break; // 弾切れ
                    }
                }
                if (loaded > 0) {
                    tag.putInt("GunCurrentAmmoCount", current + loaded);
                }
            }
        }
    }

    private static boolean consumeAmmoFromInventory(ServerPlayer player, String ammoId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("AmmoId")) {
                if (stack.getTag().getString("AmmoId").equals(ammoId)) {
                    stack.shrink(1);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * AMMO_EFFICIENCY: 弾薬が消費された場合、確率でロールバックする。


    /** 前回のチック時の銃0,1の弾数（リロード完了検出用） */
    private static final java.util.Map<java.util.UUID, int[]> lastGunAmmo = new java.util.HashMap<>();

    /**
     * リロード完了を検出し、QUICK_FIX (回復) を適用する。
     */
    private static void detectReloadAndApplyQuickFix(ServerPlayer player) {
        float quickFixPercent = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:QUICK_FIX")) {
                quickFixPercent += PerkDefinition.fromTag(tag).calculateEffect();
            }
        }

        java.util.UUID uuid = player.getUUID();
        int[] prev = lastGunAmmo.computeIfAbsent(uuid, k -> new int[]{-1, -1});

        for (int slot = 0; slot <= 1; slot++) {
            ItemStack gun = player.getInventory().getItem(slot);
            if (gun.isEmpty() || !gun.hasTag()) {
                prev[slot] = -1;
                continue;
            }
            net.minecraft.nbt.CompoundTag tag = gun.getTag();
            if (tag == null || !tag.contains("GunId")) {
                prev[slot] = -1;
                continue;
            }

            String gunIdStr = tag.getString("GunId");
            int baseMag = getBaseMagFromTacZ(gunIdStr, gun);
            if (baseMag <= 0) {
                prev[slot] = -1;
                continue;
            }

            int effectiveBase = getEffectiveBaseMag(baseMag, gunIdStr, tag);
            int current = tag.getInt("GunCurrentAmmoCount");
            int previousAmmo = prev[slot];
            prev[slot] = current;

            // リロード完了検出: 前回の弾数が実効ベース未満 → 今回が実効ベース以上 = リロード完了
            boolean reloadJustCompleted = (previousAmmo >= 0 && previousAmmo < effectiveBase && current >= effectiveBase);

            if (reloadJustCompleted && quickFixPercent > 0) {
                player.heal(quickFixPercent / 10.0f);
                // 連続発動を防ぐため、1度のtickで1回処理したら抜ける
                break;
            }
        }
    }

    /**
     * 拡張マガジンを考慮した実効ベースマガジンサイズを取得。
     */
    private static int getEffectiveBaseMag(int vanillaBaseMag, String gunIdStr, net.minecraft.nbt.CompoundTag tag) {
        try {
            net.minecraft.resources.ResourceLocation gunId = new net.minecraft.resources.ResourceLocation(gunIdStr);
            var optIndex = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId);
            if (optIndex.isPresent()) {
                int[] extMagAmounts = optIndex.get().getGunData().getExtendedMagAmmoAmount();
                if (extMagAmounts != null && extMagAmounts.length > 0 && tag.contains("Attachments")) {
                    net.minecraft.nbt.CompoundTag attachments = tag.getCompound("Attachments");
                    if (attachments.contains("extended_mag")) {
                        net.minecraft.nbt.CompoundTag magTag = attachments.getCompound("extended_mag");
                        String extMagId = magTag.getString("id");
                        int extLevel = getExtendedMagLevel(extMagId);
                        int idx = Math.min(extLevel, extMagAmounts.length) - 1;
                        if (idx >= 0) {
                            return extMagAmounts[idx];
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return vanillaBaseMag;
    }

    private static int getExtendedMagLevel(String extMagId) {
        if (extMagId == null || extMagId.isEmpty()) return 1;
        if (extMagId.endsWith("_3")) return 3;
        if (extMagId.endsWith("_2")) return 2;
        return 1;
    }

    private static int getBaseMagFromTacZ(String gunIdStr, ItemStack gun) {
        try {
            net.minecraft.resources.ResourceLocation gunId = new net.minecraft.resources.ResourceLocation(gunIdStr);
            var optIndex = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId);
            if (optIndex.isPresent()) {
                return optIndex.get().getGunData().getAmmoAmount();
            }
        } catch (Exception ignored) {}
        return com.levanilla.rogue.core.registry.TacZGunRegistry.getMagazineSize(gunIdStr);
    }
    private static int countTotalAmmo(ServerPlayer player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.hasTag() && stack.getTag() != null && stack.getTag().contains("AmmoId")) {
                total += stack.getCount();
            }
        }
        return total;
    }



    private static void applyPerkModifier(ServerPlayer player,
            net.minecraft.world.entity.ai.attributes.Attribute attribute,
            java.util.UUID uuid, String name, double amount,
            AttributeModifier.Operation operation) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(uuid);
        if (amount > 0.001) {
            inst.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
        }
    }

    /**
     * カスタム体力回復システム（毎秒呼び出し）。
     * ダメージを受けてから5秒間（100tick）は全ての回復を停止する。
     * - ベース回復: 最大HPの30%まで緩やかに回復（パーク不要）
     * - パーク回復: REGENERATION パークがあれば最大HPまで回復
     */
    private static void applyCustomHealthRegen(ServerPlayer player) {
        // ダメージ後5秒間のクールダウンチェック
        long lastDmgTick = CombatEventHandler.getLastDamageTick(player.getUUID());
        long currentTick = player.level().getGameTime();
        boolean canRegen = (currentTick - lastDmgTick) >= GameConstants.REGEN_DAMAGE_COOLDOWN_TICKS;
        if (!canRegen) return;

        float currentHp = player.getHealth();
        float maxHp = player.getMaxHealth();
        if (currentHp >= maxHp) return;

        // パーク REGENERATION の効果合算
        float regenEffect = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:REGENERATION")) {
                regenEffect += PerkDefinition.fromTag(tag).calculateEffect();
            }
        }

        if (regenEffect > 0) {
            // パーク回復: 最大HPまで回復可能（効果値に応じた回復速度）
            float healPerSecond = regenEffect / 10.0f;
            player.heal(healPerSecond);
        } else {
            // ベース回復: 最大HPの30%まで緩やかに回復
            float regenCap = maxHp * GameConstants.REGEN_BASE_CAP_RATIO;
            if (currentHp < regenCap) {
                // スタミナが50%以上ある場合のみ回復
                float stamina = StaminaManager.getStamina(player);
                float maxStamina = StaminaManager.getMaxStamina(player);
                if (maxStamina > 0 && (stamina / maxStamina) >= GameConstants.REGEN_STAMINA_THRESHOLD) {
                    player.heal(GameConstants.REGEN_BASE_HEAL);
                }
            }
        }
    }
}
