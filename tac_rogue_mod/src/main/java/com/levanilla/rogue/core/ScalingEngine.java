package com.levanilla.rogue.core;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.network.chat.Component;

/**
 * 敵のスケーリングエンジン。
 * 10 種のプレフィックスで敵のバリエーションを生成し、
 * 階層に応じた HP / 攻撃力 / 速度のスケーリングを適用する。
 * ボス専用のスケーリングも提供。
 */
public class ScalingEngine {

    private static final java.util.Random RANDOM = new java.util.Random();

    /** プレフィックス定義 */
    public enum Prefix {
        SWIFT("Swift", 0.8, 1.0, 1.4, 0xFF00FFFF),
        TANKY("Tanky", 2.0, 1.0, 0.8, 0xFF888888),
        LETHAL("Lethal", 0.7, 2.0, 1.0, 0xFFFF0000),
        ARMORED("Armored", 1.2, 1.0, 0.9, 0xFF6666FF),
        FIERY("Fiery", 1.0, 1.2, 1.0, 0xFFFF8800),
        TOXIC("Toxic", 1.0, 1.0, 1.0, 0xFF00FF00),
        GHOSTLY("Ghostly", 0.5, 1.3, 1.2, 0xFFCCCCCC),
        ELITE("Elite", 1.5, 1.5, 1.1, 0xFFFFD700),
        ANCIENT("Ancient", 3.0, 0.5, 0.7, 0xFFAA8855),
        BERSERK("Berserk", 0.3, 3.0, 1.5, 0xFFFF3333);

        public final String name;
        public final double hpMult;
        public final double dmgMult;
        public final double spdMult;
        public final int color;

        Prefix(String name, double hp, double dmg, double spd, int color) {
            this.name = name; this.hpMult = hp; this.dmgMult = dmg; this.spdMult = spd; this.color = color;
        }
    }

    /** ボス名（バイオーム群ごと） */
    private static final String[] BOSS_NAMES = {
        "RUINED OVERLORD", "BIO HAZARD ALPHA", "DEEP CORE GUARDIAN",
        "COMMANDER IRON", "NETHER LORD", "LEVIATHAN",
        "URBAN PREDATOR", "ELDER SENTINEL", "VOID HARBINGER"
    };

    /**
     * 通常の敵にプレフィックス + 階層スケーリングを適用する。
     */
    public static void applyScaling(LivingEntity entity, int floor) {
        if (entity == null) return;

        Prefix prefix = Prefix.values()[RANDOM.nextInt(Prefix.values().length)];
        float diffHp = DifficultyManager.getHpScale(floor);
        float diffDmg = DifficultyManager.getDmgScale(floor);
        double floorHpMult = (1.0 + (floor - 1) * GameConstants.FLOOR_HP_SCALE_PER_LEVEL) * diffHp;
        double floorDmgMult = (1.0 + (floor - 1) * GameConstants.FLOOR_DMG_SCALE_PER_LEVEL) * diffDmg;

        // プレフィックス固有の追加効果
        applyPrefixEffects(entity, prefix);

        // 名前の設定
        entity.setCustomName(Component.literal("§7[" + prefix.name + "] §f" + entity.getType().getDescription().getString()));
        entity.setCustomNameVisible(false);

        // HP スケーリング
        setScaledAttribute(entity, Attributes.MAX_HEALTH, prefix.hpMult * floorHpMult);

        // 攻撃力スケーリング
        setScaledAttribute(entity, Attributes.ATTACK_DAMAGE, prefix.dmgMult * floorDmgMult);

        // 速度スケーリング
        setScaledAttribute(entity, Attributes.MOVEMENT_SPEED, prefix.spdMult);

        // 体力を最大値にリセット
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            entity.setHealth((float) maxHealth.getValue());
        }

        // 10 階以上で発光
        if (floor > GameConstants.GLOW_FLOOR_THRESHOLD) entity.setGlowingTag(true);
    }

    /**
     * ボス専用のスケーリングを適用する。
     */
    public static void applyBossScaling(LivingEntity entity, int floor, int biomeIndex) {
        if (entity == null) return;

        double bossHpMult = GameConstants.BOSS_HP_BASE + (floor - 1) * GameConstants.BOSS_HP_PER_FLOOR;
        double bossDmgMult = GameConstants.BOSS_DMG_BASE + (floor - 1) * GameConstants.BOSS_DMG_PER_FLOOR;

        String bossName = BOSS_NAMES[biomeIndex % BOSS_NAMES.length];
        entity.setCustomName(Component.literal("§c§l[BOSS] §e" + bossName));
        entity.setCustomNameVisible(false);

        // HP: 基礎値 × 5 + 階層ボーナス
        setScaledAttribute(entity, Attributes.MAX_HEALTH, bossHpMult);
        // 攻撃力: 基礎値 × 3
        setScaledAttribute(entity, Attributes.ATTACK_DAMAGE, bossDmgMult);
        // 速度: やや遅め (地上/空中) - ウィザー等の超速初期値対策として上限キャップを導入
        setScaledAttribute(entity, Attributes.MOVEMENT_SPEED, GameConstants.BOSS_SPEED_MULT);
        if (entity.getAttribute(Attributes.MOVEMENT_SPEED) != null && entity.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() > 0.18) {
            entity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.18);
        }
        if (entity.getAttributes().hasAttribute(Attributes.FLYING_SPEED)) {
            setScaledAttribute(entity, Attributes.FLYING_SPEED, GameConstants.BOSS_SPEED_MULT);
            if (entity.getAttribute(Attributes.FLYING_SPEED) != null && entity.getAttribute(Attributes.FLYING_SPEED).getBaseValue() > 0.18) {
                entity.getAttribute(Attributes.FLYING_SPEED).setBaseValue(0.18);
            }
        }

        // ウィザーはさらに遅くする
        if (entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither) {
            if (wither.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                wither.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.10);
            }
            if (wither.getAttribute(Attributes.FLYING_SPEED) != null) {
                wither.getAttribute(Attributes.FLYING_SPEED).setBaseValue(0.08); // バニラ(0.0)に対して若干の余裕を
            }
        }

        // 体力リセット
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            entity.setHealth((float) maxHealth.getValue());
        }

        // ボスは常に発光
        entity.setGlowingTag(true);

        // 防御力
        AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(GameConstants.BOSS_ARMOR_BASE + floor * GameConstants.BOSS_ARMOR_PER_FLOOR);
        }
    }

    /** プレフィックス固有の追加効果 */
    private static void applyPrefixEffects(LivingEntity entity, Prefix prefix) {
        switch (prefix) {
            case ARMORED -> {
                AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
                if (armor != null) armor.setBaseValue(10.0);
            }
            case FIERY -> entity.setSecondsOnFire(1000);
            case TOXIC -> entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.POISON, 100000, 0, false, false));
            case GHOSTLY -> entity.setInvisible(true);
            default -> { /* no special effect */ }
        }
    }

    /** 属性値を倍率で設定するヘルパー */
    private static void setScaledAttribute(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attr, double multiplier) {
        AttributeInstance instance = entity.getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }
}
