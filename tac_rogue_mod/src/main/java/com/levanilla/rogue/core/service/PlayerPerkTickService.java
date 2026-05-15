package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.StaminaManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public final class PlayerPerkTickService {

    private PlayerPerkTickService() {}
    private static final java.util.UUID PERK_VITALITY_UUID = java.util.UUID.fromString("a1b2c3d4-1111-4444-8888-aabbccddeef0");
    private static final java.util.UUID PERK_ARMOR_UUID    = java.util.UUID.fromString("a1b2c3d4-2222-4444-8888-aabbccddeef1");
    private static final java.util.UUID PERK_VELOCITY_UUID = java.util.UUID.fromString("a1b2c3d4-3333-4444-8888-aabbccddeef2");

    private static final java.util.Map<java.util.UUID, Float> autoloaderCarry = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, CachedPerkSnapshot> perkSnapshots = new java.util.HashMap<>();
    private static final int PERK_SNAPSHOT_RESCAN_TICKS = 20;

    public static final class PerkSnapshot {
        private static final PerkSnapshot EMPTY = new PerkSnapshot(
            new float[PerkDefinition.Category.values().length],
            new int[PerkDefinition.Modifier.values().length],
            0);

        private final float[] categoryEffects;
        private final int[] modifierCounts;
        private final int perkCount;

        private PerkSnapshot(float[] categoryEffects, int[] modifierCounts, int perkCount) {
            this.categoryEffects = categoryEffects;
            this.modifierCounts = modifierCounts;
            this.perkCount = perkCount;
        }

        public float effect(PerkDefinition.Category category) {
            return categoryEffects[category.ordinal()];
        }

        public int modifierCount(PerkDefinition.Modifier modifier) {
            return modifierCounts[modifier.ordinal()];
        }

        public int perkCount() {
            return perkCount;
        }
    }

    private record CachedPerkSnapshot(
        PerkSnapshot snapshot,
        int perkSerial,
        int tagCount,
        int lastScanTick
    ) {}

    public static PerkSnapshot getSnapshot(ServerPlayer player) {
        java.util.UUID uuid = player.getUUID();
        int perkSerial = getPerkSerial(player);
        int tagCount = player.getTags().size();
        int tick = player.tickCount;
        CachedPerkSnapshot cached = perkSnapshots.get(uuid);

        if (cached != null
                && cached.perkSerial == perkSerial
                && cached.tagCount == tagCount
                && tick >= cached.lastScanTick
                && tick - cached.lastScanTick < PERK_SNAPSHOT_RESCAN_TICKS) {
            return cached.snapshot;
        }

        PerkSnapshot snapshot = buildSnapshot(player);
        perkSnapshots.put(uuid, new CachedPerkSnapshot(snapshot, perkSerial, player.getTags().size(), tick));
        return snapshot;
    }

    public static void invalidateSnapshot(ServerPlayer player) {
        if (player != null) {
            perkSnapshots.remove(player.getUUID());
        }
    }

    private static int getPerkSerial(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        int serial = data.getInt("TacRoguePerkSerial");
        serial = 31 * serial + data.getInt("TacRogueDebugPerkSerial");
        serial = 31 * serial + data.getInt("TacRogueRandomPerkSerial");
        return serial;
    }

    private static PerkSnapshot buildSnapshot(ServerPlayer player) {
        float[] categoryEffects = new float[PerkDefinition.Category.values().length];
        int[] modifierCounts = new int[PerkDefinition.Modifier.values().length];
        int perkCount = 0;

        for (String tag : player.getTags()) {
            if (!tag.startsWith("perk:")) continue;
            PerkDefinition perk = PerkDefinition.fromTag(tag);
            categoryEffects[perk.category.ordinal()] += perk.calculateEffect();
            modifierCounts[perk.modifier.ordinal()]++;
            perkCount++;
        }

        return perkCount == 0 ? PerkSnapshot.EMPTY : new PerkSnapshot(categoryEffects, modifierCounts, perkCount);
    }

    /**
     * パーク効果をプレイヤー属性に反映する。
     * 毎秒（20tick）ごとに呼び出される。
     */
    public static void applyPerkStats(ServerPlayer player) {
        applyPerkStats(player, getSnapshot(player));
    }

    public static void applyPerkStats(ServerPlayer player, PerkSnapshot perks) {
        float vitalityEffect = perks.effect(PerkDefinition.Category.VITALITY);
        float armorEffect = perks.effect(PerkDefinition.Category.ARMOR);
        float velocityEffect = perks.effect(PerkDefinition.Category.VELOCITY);
        float staminaEffect = perks.effect(PerkDefinition.Category.STAMINA);
        int cursedCount = perks.modifierCount(PerkDefinition.Modifier.CURSED);
        int overclockedCount = perks.modifierCount(PerkDefinition.Modifier.OVERCLOCKED);
        int fracturedCount = perks.modifierCount(PerkDefinition.Modifier.FRACTURED);
        int primalCount = perks.modifierCount(PerkDefinition.Modifier.PRIMAL);

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
        int titanicCount = perks.modifierCount(PerkDefinition.Modifier.TITANIC);
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
            float baseMax = StaminaManager.getBaseMaxStamina(player);
            float bonus = baseMax * (staminaEffect / 100.0f);
            float newMax = baseMax + bonus;
            // OVERCLOCKED: スタミナ最大値を線形に減少（25%/個、最大50%）
            float overclockedPenalty = Math.min(0.5f, overclockedCount * 0.25f);
            newMax *= (1.0f - overclockedPenalty);
            StaminaManager.setMaxStamina(player, Math.max(20.0f, newMax)); // 最低20
        }

        detectReloadAndApplyQuickFix(player, perks);
        applyAutoloader(player, perks);
    }

    /**
     * AUTOLOADER: 専用スロットの銃へ時間経過で弾を装填する。
     */
    private static void applyAutoloader(ServerPlayer player, PerkSnapshot perks) {
        float autoloaderEffect = perks.effect(PerkDefinition.Category.AUTOLOADER);
        if (autoloaderEffect <= 0) {
            autoloaderCarry.remove(player.getUUID());
            return;
        }

        float roundsPerSecond = PerkDefinition.getAutoloaderRoundsPerSecond(autoloaderEffect);
        float carry = autoloaderCarry.getOrDefault(player.getUUID(), 0.0f) + roundsPerSecond;
        int bulletsToLoad = Math.max(1, (int) carry);
        autoloaderCarry.put(player.getUUID(), carry - bulletsToLoad);
        int remainingBudget = bulletsToLoad;

        for (int slot = 0; slot <= 1 && remainingBudget > 0; slot++) {
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
                for (int i = 0; i < remainingBudget; i++) {
                    if (current + loaded >= maxCap) break;
                    if (consumeAmmoFromInventory(player, ammoId)) {
                        loaded++;
                    } else {
                        break; // 弾切れ
                    }
                }
                if (loaded > 0) {
                    tag.putInt("GunCurrentAmmoCount", current + loaded);
                    remainingBudget -= loaded;
                } else {
                    break;
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

    // AMMO_EFFICIENCY: TacZEventHandler.onGunFire() に移行済み
    /** 前回のチック時の銃0,1の弾数（リロード完了検出用） */
    private static final java.util.Map<java.util.UUID, int[]> lastGunAmmo = new java.util.HashMap<>();

    /**
     * リロード完了を検出し、QUICK_FIX (回復) を適用する。
     */
    private static void detectReloadAndApplyQuickFix(ServerPlayer player, PerkSnapshot perks) {
        float quickFixPercent = perks.effect(PerkDefinition.Category.QUICK_FIX);

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
            boolean vanillaReloadJustCompleted = previousAmmo >= 0
                && previousAmmo < baseMag
                && current >= baseMag
                && current < effectiveBase;
            if (vanillaReloadJustCompleted) {
                String ammoId = com.levanilla.rogue.core.registry.TacZGunRegistry.getAmmoForGun(gunIdStr);
                int loaded = 0;
                int needed = effectiveBase - current;
                for (int i = 0; i < needed; i++) {
                    if (!consumeAmmoFromInventory(player, ammoId)) break;
                    loaded++;
                }
                if (loaded > 0) {
                    current += loaded;
                    tag.putInt("GunCurrentAmmoCount", current);
                }
            }
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
        float rarityMagMult = tag.getFloat("RogueMagMult");
        if (rarityMagMult <= 0) rarityMagMult = 1.0f;
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
                            return Math.max(1, Math.round(extMagAmounts[idx] * rarityMagMult));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return Math.max(1, Math.round(vanillaBaseMag * rarityMagMult));
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
    /**
     * 以前の applyPerkEffect() が setBaseValue() で直接変更したベース属性値を
     * デフォルト値にリセットする。これにより TransientModifier が正しいベース値に
     * 対して計算される。
     */
    private static void resetBaseAttributes(ServerPlayer player) {
        resetAttribute(player, Attributes.MAX_HEALTH, 20.0);
        resetAttribute(player, Attributes.MOVEMENT_SPEED, 0.1);
        resetAttribute(player, Attributes.ARMOR, 0.0);
        resetAttribute(player, Attributes.ATTACK_DAMAGE, 1.0);
    }

    private static void resetAttribute(ServerPlayer player,
            net.minecraft.world.entity.ai.attributes.Attribute attribute, double defaultValue) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst != null && Math.abs(inst.getBaseValue() - defaultValue) > 0.001) {
            inst.setBaseValue(defaultValue);
        }
    }

    private static void applyPerkModifier(ServerPlayer player,
            net.minecraft.world.entity.ai.attributes.Attribute attribute,
            java.util.UUID uuid, String name, double amount,
            AttributeModifier.Operation operation) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(uuid);
        if (Math.abs(amount) > 0.001) {
            inst.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
        }
    }
    // ===== BUG-6: メモリリーク防止 =====

    /** サーバー停止時にスタティックマップをクリア */
    public static void clearMemory() {
        autoloaderCarry.clear();
        perkSnapshots.clear();
        lastGunAmmo.clear();
    }


}
