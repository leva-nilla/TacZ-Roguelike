package com.levanilla.rogue.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 武器レアリティシステム — ★1〜★5
 *
 * ドロップ/ショップで入手する武器にレアリティを付与し、
 * ダメージ・リロード速度・マガジンサイズにボーナスを適用する。
 *
 * レアリティはNBTタグ "RogueRarity" (int 1-5) で保持。
 */
public class WeaponRarity {

    public enum Rarity {
        COMMON(1, "Common", net.minecraft.ChatFormatting.GRAY, 1.0f, 1.0f, 1.0f, 0xFFAAAAAA),
        UNCOMMON(2, "Uncommon", net.minecraft.ChatFormatting.GREEN, 1.10f, 0.95f, 1.05f, 0xFF55FF55),
        RARE(3, "Rare", net.minecraft.ChatFormatting.BLUE, 1.20f, 0.90f, 1.10f, 0xFF5555FF),
        EPIC(4, "Epic", net.minecraft.ChatFormatting.DARK_PURPLE, 1.35f, 0.85f, 1.20f, 0xFFAA00AA),
        LEGENDARY(5, "Legendary", net.minecraft.ChatFormatting.GOLD, 1.50f, 0.80f, 1.30f, 0xFFFFAA00);

        public final int stars;
        public final String name;
        public final net.minecraft.ChatFormatting format;
        public final float damageMult;     // ダメージ倍率
        public final float reloadMult;     // リロード時間倍率 (低い = 速い)
        public final float magSizeMult;    // マガジンサイズ倍率
        public final int color;

        Rarity(int stars, String name, net.minecraft.ChatFormatting format, float dmg, float reload, float mag, int color) {
            this.stars = stars;
            this.name = name;
            this.format = format;
            this.damageMult = dmg;
            this.reloadMult = reload;
            this.magSizeMult = mag;
            this.color = color;
        }

        public String getStarsDisplay() {
            return "★".repeat(stars) + "☆".repeat(5 - stars);
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

    /** ItemStack にレアリティを付与 */
    public static void applyRarity(ItemStack gun, Rarity rarity) {
        CompoundTag tag = gun.getOrCreateTag();
        tag.putInt("RogueRarity", rarity.stars);
        tag.putFloat("RogueDamageMult", rarity.damageMult);
        tag.putFloat("RogueReloadMult", rarity.reloadMult);
        tag.putFloat("RogueMagMult", rarity.magSizeMult);

        // 表示名にレアリティを付与
        String baseName = gun.getHoverName().getString()
            .replaceAll("§.", "")
            .replaceAll("★+☆*\\s*", "")
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
        return mult > 0 ? mult : 1.0f;
    }

    /** リロード速度倍率を取得 */
    public static float getReloadMult(ItemStack gun) {
        if (!gun.hasTag()) return 1.0f;
        float mult = gun.getTag().getFloat("RogueReloadMult");
        return mult > 0 ? mult : 1.0f;
    }

    /** マガジンサイズ倍率を取得 */
    public static float getMagSizeMult(ItemStack gun) {
        if (!gun.hasTag()) return 1.0f;
        float mult = gun.getTag().getFloat("RogueMagMult");
        return mult > 0 ? mult : 1.0f;
    }
}
