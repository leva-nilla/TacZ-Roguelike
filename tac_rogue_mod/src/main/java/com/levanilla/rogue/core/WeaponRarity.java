package com.levanilla.rogue.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 武器レアリティシステム — ★1〜★5
 *
 * ドロップ/ショップで入手する武器にレアリティを付与し、
 * ダメージ・リロード速度・マガジンサイズ・連射速度にボーナスを適用する。
 *
 * レアリティはNBTタグ "RogueRarity" (int 1-5) で保持。
 */
public class WeaponRarity {

    public enum Rarity {
        COMMON(1, "Common", net.minecraft.ChatFormatting.GRAY, 1.0f, 1.0f, 1.0f, 1.00f, 0xFFAAAAAA),
        UNCOMMON(2, "Uncommon", net.minecraft.ChatFormatting.GREEN, 1.12f, 0.93f, 1.08f, 1.03f, 0xFF55FF55),
        RARE(3, "Rare", net.minecraft.ChatFormatting.BLUE, 1.28f, 0.86f, 1.18f, 1.07f, 0xFF5555FF),
        EPIC(4, "Epic", net.minecraft.ChatFormatting.DARK_PURPLE, 1.48f, 0.78f, 1.32f, 1.11f, 0xFFAA00AA),
        LEGENDARY(5, "Legendary", net.minecraft.ChatFormatting.GOLD, 1.75f, 0.68f, 1.50f, 1.16f, 0xFFFFAA00);

        public final int stars;
        public final String name;
        public final net.minecraft.ChatFormatting format;
        public final float damageMult;     // ダメージ倍率
        public final float reloadMult;     // リロード時間倍率 (低い = 速い)
        public final float magSizeMult;    // マガジンサイズ倍率
        public final float fireRateMult;   // 連射速度倍率
        public final int color;

        Rarity(int stars, String name, net.minecraft.ChatFormatting format,
               float dmg, float reload, float mag, float fireRate, int color) {
            this.stars = stars;
            this.name = name;
            this.format = format;
            this.damageMult = dmg;
            this.reloadMult = reload;
            this.magSizeMult = mag;
            this.fireRateMult = fireRate;
            this.color = color;
        }

        public String getStarsDisplay() {
            return switch (this) {
                case COMMON -> "[C]";
                case UNCOMMON -> "[U]";
                case RARE -> "[R]";
                case EPIC -> "[E]";
                case LEGENDARY -> "[L]";
            };
        }
    }

    /** フロアに応じたレアリティを重み付きランダムで決定 */
    public static Rarity rollRarity(int floor, net.minecraft.util.RandomSource rand) {
        // フロアが高いほどレア出現率が少しずつ上がる
        int commonW = Math.max(10, 100 - floor); // 序盤多く、徐々に減る (最低10)
        int uncommonW = 60 + Math.min(floor, 40); // 徐々に増えて頭打ち
        int rareW = 20 + Math.min(floor, 80); // 中盤以降の主役として増え続ける
        int epicW = Math.max(0, floor - 15); // 16階以降から徐々に出現
        
        // 伝説(Legendary)は上限5%に抑える
        int subTotal = commonW + uncommonW + rareW + epicW;
        
        // 20階から出現し、1階ごとに0.1%ずつ上昇し、最大5%でカンストする
        float legendTargetPercent = Math.min(0.05f, Math.max(0f, floor - 20) / 1000f);
        
        // TargetPercent = L / (subTotal + L)  => L = subTotal * TargetPercent / (1 - TargetPercent)
        int legendaryW = (int)(subTotal * (legendTargetPercent / (1.0f - legendTargetPercent)));
        if (legendTargetPercent > 0 && legendaryW <= 0) legendaryW = 1;

        int total = subTotal + legendaryW;
        int roll = rand.nextInt(total);

        if (roll < commonW) return Rarity.COMMON;
        roll -= commonW;
        if (roll < uncommonW) return Rarity.UNCOMMON;
        roll -= uncommonW;
        if (roll < rareW) return Rarity.RARE;
        roll -= rareW;
        if (roll < epicW) return Rarity.EPIC;
        return Rarity.LEGENDARY;
    }

    public static Rarity rollShopRarity(int floor, String itemId) {
        int safeFloor = Math.max(1, floor);
        int score = Math.floorMod(itemId.hashCode() ^ (safeFloor * 73428767), 1000);
        if (safeFloor >= 20 && score >= 990) return Rarity.EPIC;
        if (safeFloor >= 10 && score >= 930) return Rarity.RARE;
        if (safeFloor >= 5 && score >= 760) return Rarity.UNCOMMON;
        if (safeFloor < 5 && score >= 900) return Rarity.UNCOMMON;
        return Rarity.COMMON;
    }

    public static Rarity atLeast(Rarity rarity, Rarity minimum) {
        return rarity.stars < minimum.stars ? minimum : rarity;
    }

    /** ItemStack にレアリティを付与 */
    public static void applyRarity(ItemStack gun, Rarity rarity) {
        CompoundTag tag = gun.getOrCreateTag();
        tag.putInt("RogueRarity", rarity.stars);
        tag.putFloat("RogueDamageMult", rarity.damageMult);
        tag.putFloat("RogueReloadMult", rarity.reloadMult);
        tag.putFloat("RogueMagMult", rarity.magSizeMult);
        tag.putFloat("RogueFireRateMult", rarity.fireRateMult);

        // 表示名にレアリティを付与
        String baseName = gun.getHoverName().getString()
            .replaceAll("§.", "")
            .replaceAll("\\[[CUREL]\\]\\s*", "")
            .trim();
            
        gun.setHoverName(net.minecraft.network.chat.Component.empty()
            .append(net.minecraft.network.chat.Component.literal(rarity.getStarsDisplay() + " ").withStyle(rarity.format))
            .append(net.minecraft.network.chat.Component.literal(baseName).withStyle(rarity.format)));
    }

    /** ItemStack からレアリティを取得 */
    public static Rarity getRarity(ItemStack gun) {
        if (!gun.hasTag()) return Rarity.COMMON;
        int stars = gun.getTag().getInt("RogueRarity");
        if (stars <= 0) return Rarity.COMMON;
        for (Rarity r : Rarity.values()) {
            if (r.stars == stars) return r;
        }
        return Rarity.COMMON;
    }

    /** ダメージ倍率を取得 */
    public static float getDamageMult(ItemStack gun) {
        if (!gun.hasTag()) return 1.0f;
        float mult = gun.getTag().getFloat("RogueDamageMult");
        return mult > 0 ? Math.max(mult, getRarity(gun).damageMult) : getRarity(gun).damageMult;
    }

    /** リロード速度倍率を取得 */
    public static float getReloadMult(ItemStack gun) {
        if (!gun.hasTag()) return 1.0f;
        float mult = gun.getTag().getFloat("RogueReloadMult");
        return mult > 0 ? Math.min(mult, getRarity(gun).reloadMult) : getRarity(gun).reloadMult;
    }

    /** RELOAD_SPEED パークの実効ボーナスを取得 */
    public static float getReloadSpeedPerkBonusPercent(LivingEntity holder) {
        return PerkDefinition.sumCategoryEffect(holder, PerkDefinition.Category.RELOAD_SPEED);
    }

    /** レアリティと RELOAD_SPEED パークを合算した実効リロード時間倍率を取得 */
    public static float getEffectiveReloadMult(ItemStack gun, LivingEntity holder) {
        float mult = getReloadMult(gun);
        float reloadBonus = getReloadSpeedPerkBonusPercent(holder);
        if (reloadBonus > 0.0f) {
            mult /= (1.0f + reloadBonus / 100.0f);
        }
        return Math.max(GameConstants.MIN_EFFECTIVE_RELOAD_MULT, mult);
    }

    /** マガジンサイズ倍率を取得 */
    public static float getMagSizeMult(ItemStack gun) {
        if (!gun.hasTag()) return 1.0f;
        float mult = gun.getTag().getFloat("RogueMagMult");
        return mult > 0 ? Math.max(mult, getRarity(gun).magSizeMult) : getRarity(gun).magSizeMult;
    }

    /** 連射速度倍率を取得 */
    public static float getFireRateMult(ItemStack gun) {
        if (!gun.hasTag()) return 1.0f;
        // RogueFireRateMult is derived only from RogueRarity. Ignore stale pre-0.9.6 values
        // so existing guns pick up the current Fire Rate balance table.
        return getRarity(gun).fireRateMult;
    }

    /** FIRE_RATE パークのソフトキャップ後ボーナスを取得 */
    public static float getFireRatePerkBonusPercent(LivingEntity holder) {
        float fireRateBonus = holder != null
            ? PerkDefinition.sumCategoryEffect(holder, PerkDefinition.Category.FIRE_RATE)
            : PerkDefinition.sumClientCategoryEffect(PerkDefinition.Category.FIRE_RATE);
        return softcapPercent(
            fireRateBonus,
            GameConstants.FIRE_RATE_SOFTCAP_START,
            GameConstants.FIRE_RATE_POST_SOFTCAP_SCALE,
            GameConstants.FIRE_RATE_HARD_CAP);
    }

    /** MELEE_SPEED パークのソフトキャップ後ボーナスを取得 */
    public static float getMeleeSpeedPerkBonusPercent(LivingEntity holder) {
        float meleeSpeedBonus = getRawMeleeSpeedPerkBonusPercent(holder);
        return softcapPercent(
            meleeSpeedBonus,
            GameConstants.FIRE_RATE_SOFTCAP_START,
            GameConstants.FIRE_RATE_POST_SOFTCAP_SCALE,
            GameConstants.MELEE_SPEED_HARD_CAP);
    }

    public static float getRawMeleeSpeedPerkBonusPercent(LivingEntity holder) {
        return holder != null
            ? PerkDefinition.sumCategoryEffect(holder, PerkDefinition.Category.MELEE_SPEED)
            : PerkDefinition.sumClientCategoryEffect(PerkDefinition.Category.MELEE_SPEED);
    }

    public static float getMeleeSpeedOverflowPercent(LivingEntity holder) {
        float raw = getRawMeleeSpeedPerkBonusPercent(holder);
        float overflowStart = rawPercentForSoftcapOutput(
            GameConstants.MELEE_SPEED_HARD_CAP,
            GameConstants.FIRE_RATE_SOFTCAP_START,
            GameConstants.FIRE_RATE_POST_SOFTCAP_SCALE);
        return Math.max(0.0f, raw - overflowStart);
    }

    public static float getMeleeSpeedOverflowDamageBonusPercent(LivingEntity holder) {
        return getMeleeSpeedOverflowPercent(holder) * GameConstants.MELEE_SPEED_OVERFLOW_DAMAGE_SCALE;
    }

    public static float getMeleeSpeedOverflowCriticalDamageBonusPercent(LivingEntity holder) {
        return getMeleeSpeedOverflowPercent(holder) * GameConstants.MELEE_SPEED_OVERFLOW_CRIT_DAMAGE_SCALE;
    }

    public static float getMeleeSpeedOverflowDamageMultiplier(LivingEntity holder) {
        return 1.0f + getMeleeSpeedOverflowDamageBonusPercent(holder) / 100.0f;
    }

    public static float getMeleeSpeedOverflowCriticalDamageMultiplier(LivingEntity holder) {
        return 1.0f + getMeleeSpeedOverflowCriticalDamageBonusPercent(holder) / 100.0f;
    }

    /** レアリティと FIRE_RATE パークを合算した実効連射速度倍率を取得 */
    public static float getEffectiveFireRateMult(ItemStack gun, LivingEntity holder) {
        float mult = getFireRateMult(gun);
        float perkBonus = getFireRatePerkBonusPercent(holder);
        if (perkBonus > 0.0f) {
            mult *= 1.0f + perkBonus / 100.0f;
        }
        mult *= com.levanilla.rogue.core.service.DeepProgressService.fireRateMultiplier(gun);
        // FIRE_RATE is an upgrade path. Deep progression can stop adding speed, but must not slow the weapon below base.
        return Math.max(1.0f, mult);
    }

    /** レアリティと MELEE_SPEED パークを合算した実効近接攻撃速度倍率を取得 */
    public static float getEffectiveMeleeFireRateMult(ItemStack melee, LivingEntity holder) {
        float mult = getFireRateMult(melee);
        float perkBonus = getMeleeSpeedPerkBonusPercent(holder);
        if (perkBonus > 0.0f) {
            mult *= 1.0f + perkBonus / 100.0f;
        }
        return Math.max(1.0f, mult);
    }

    public static long getFireRateAdjustedIntervalMs(long originalIntervalMs, ItemStack gun, LivingEntity holder) {
        return com.levanilla.rogue.core.service.TacZFireRateService.adjustedShootIntervalMs(originalIntervalMs, gun, holder);
    }

    public static double getFireRateAdjustedIntervalSeconds(double originalIntervalSeconds, ItemStack gun, LivingEntity holder) {
        return com.levanilla.rogue.core.service.TacZFireRateService.adjustedShootIntervalSeconds(originalIntervalSeconds, gun, holder);
    }

    public static int getMeleeFireRateAdjustedTicks(int originalTicks, ItemStack melee, LivingEntity holder) {
        if (originalTicks <= 0) return originalTicks;
        return scaleTicksByMultiplier(originalTicks, getEffectiveMeleeFireRateMult(melee, holder));
    }

    public static int getMeleeSpeedPerkAdjustedTicks(int originalTicks, LivingEntity holder) {
        if (originalTicks <= 0) return originalTicks;
        float perkBonus = getMeleeSpeedPerkBonusPercent(holder);
        if (perkBonus <= 0.0f) return originalTicks;
        return scaleTicksByMultiplier(originalTicks, 1.0f + perkBonus / 100.0f);
    }

    public static int getEffectiveMagazineSize(ItemStack gun, int baseSize) {
        return Math.max(1, Math.round(baseSize * getMagSizeMult(gun)));
    }

    private static float softcapPercent(float rawPercent, float softStart, float postSoftScale, float hardCap) {
        if (rawPercent <= softStart) {
            return Math.max(0.0f, rawPercent);
        }
        float compressed = softStart + (rawPercent - softStart) * postSoftScale;
        return Math.min(compressed, hardCap);
    }

    private static float rawPercentForSoftcapOutput(float outputPercent, float softStart, float postSoftScale) {
        if (outputPercent <= softStart) return outputPercent;
        if (postSoftScale <= 0.0f) return Float.MAX_VALUE;
        return softStart + (outputPercent - softStart) / postSoftScale;
    }

    private static int scaleTicksByMultiplier(int originalTicks, float multiplier) {
        if (multiplier <= 1.005f) return originalTicks;
        return Math.max(1, Math.min(originalTicks, Math.round(originalTicks / multiplier)));
    }
}
