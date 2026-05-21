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
        GHOSTLY("Ghostly", 0.6, 1.2, 1.3, 0xFFCCCCCC),     // v0.5: HP 0.5→0.6, DMG 1.3→1.2, SPD 1.2→1.3
        ELITE("Elite", 1.5, 1.5, 1.1, 0xFFFFD700),
        ANCIENT("Ancient", 2.5, 0.7, 0.8, 0xFFAA8855),      // v0.5: HP 3.0→2.5, DMG 0.5→0.7, SPD 0.7→0.8
        BERSERK("Berserk", 0.5, 2.5, 1.3, 0xFFFF3333),      // v0.5: HP 0.3→0.5, DMG 3.0→2.5, SPD 1.5→1.3
        NORMAL("Normal", 1.0, 1.0, 1.0, 0xFFFFFFFF);

        public final String name;
        public final double hpMult;
        public final double dmgMult;
        public final double spdMult;
        public final int color;

        Prefix(String name, double hp, double dmg, double spd, int color) {
            this.name = name; this.hpMult = hp; this.dmgMult = dmg; this.spdMult = spd; this.color = color;
        }
    }

    private static final Prefix[] SPECIAL_PREFIXES = java.util.Arrays.stream(Prefix.values())
        .filter(p -> p != Prefix.NORMAL)
        .toArray(Prefix[]::new);

    /** ボス名（バイオーム群ごと） */
    private static final String[] BOSS_NAMES = {
        "RUINED OVERLORD", "BIO HAZARD ALPHA", "DEEP CORE GUARDIAN",
        "COMMANDER IRON", "NETHER LORD", "LEVIATHAN",
        "URBAN PREDATOR", "ELDER SENTINEL", "VOID HARBINGER"
    };

    /**
     * 通常の敵にプレフィックス + 階層スケーリングを適用する。
     * v1.1: 二次多項式スケーリング — 後半の歯ごたえを維持。
     *   HP  = base × (1 + 0.10*f + 0.001*f²) × diffHp × prefix
     *   DMG = base × (1 + 0.05*f + 0.0005*f²) × diffDmg × prefix
     */
    public static void applyScaling(LivingEntity entity, int floor) {
        if (entity == null) return;

        // プレフィックス出現率: 高階層ほどほぼ全てが特殊個体に (F86で95%到達)
        double specialChance = Math.min(0.95, 0.10 + (floor - 1) * 0.01);
        Prefix prefix;
        if (RANDOM.nextDouble() < specialChance) {
            prefix = SPECIAL_PREFIXES[RANDOM.nextInt(SPECIAL_PREFIXES.length)];
        } else {
            prefix = Prefix.NORMAL;
        }

        float diffHp = DifficultyManager.getHpScale(floor);
        float diffDmg = DifficultyManager.getDmgScale(floor);

        // 二次多項式: 序盤はリニアに近く、後半で加速（パーク累積に対抗）
        // v0.5: HP二次項 0.001→0.0005 に緩和（F50以降のHP膨張を抑制）
        double f = floor - 1;
        double floorHpMult = (1.0 + f * 0.10 + f * f * 0.0005) * diffHp;
        double floorDmgMult = (1.0 + f * 0.05 + f * f * 0.0005) * diffDmg * getEarlyDamageMult(floor);

        // プレフィックス固有の追加効果
        if (prefix != Prefix.NORMAL) {
            applyPrefixEffects(entity, prefix);
        }

        // 名前の設定
        if (prefix != Prefix.NORMAL) {
            entity.setCustomName(Component.literal("§7[" + prefix.name + "] §f" + entity.getType().getDescription().getString()));
        } else {
            entity.setCustomName(Component.literal("§f" + entity.getType().getDescription().getString()));
        }
        entity.setCustomNameVisible(false);

        // HP スケーリング
        setScaledAttribute(entity, Attributes.MAX_HEALTH, prefix.hpMult * floorHpMult);

        // 攻撃力スケーリング
        setScaledAttribute(entity, Attributes.ATTACK_DAMAGE, prefix.dmgMult * floorDmgMult);

        // 速度スケーリング
        setScaledAttribute(entity, Attributes.MOVEMENT_SPEED, prefix.spdMult);

        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            entity.setHealth((float) maxHealth.getValue());
        }
    }

    /**
     * ボス専用のスケーリングを適用する。
     * v1.1: 二次多項式 — 高階層ボスの圧倒的存在感。
     *   HP  = base × (3.6 + 0.22*f + 0.0045*f²)
     *   DMG = base × (3 + 0.15*f + 0.002*f²)
     */
    public static void applyBossScaling(LivingEntity entity, int floor, int biomeIndex) {
        if (entity == null) return;

        double f = floor - 1;
        double bossHpMult = 3.6 + f * 0.22 + f * f * 0.0045;
        double bossDmgMult = 3.0 + f * 0.15 + f * f * 0.002;

        String bossName = BOSS_NAMES[biomeIndex % BOSS_NAMES.length];
        entity.setCustomName(Component.literal("§c§l[BOSS] §e" + bossName));
        entity.setCustomNameVisible(false);

        // HP: 初回ボスは軽く、100層以降では二次式で伸ばす
        setScaledAttribute(entity, Attributes.MAX_HEALTH, bossHpMult);
        // 攻撃力: 基礎値 × 3
        setScaledAttribute(entity, Attributes.ATTACK_DAMAGE, bossDmgMult);
        // 速度: やや遅め (地上/空中) - ウィザー等の超速初期値対策として上限キャップを導入
        setScaledAttribute(entity, Attributes.MOVEMENT_SPEED, GameConstants.BOSS_SPEED_MULT);
        AttributeInstance followRange = entity.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(Math.max(followRange.getBaseValue(), 256.0D));
        }
        if (entity.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            double currentSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
            entity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Math.max(0.16D, Math.min(currentSpeed, 0.22D)));
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

        // LEVIATHAN (リヴァイアサン) 個別強化
        if ("LEVIATHAN".equals(bossName)) {
            // 水棲等ベースで弱い場合はHPをさらに1.5倍、地上移動速度を少し補強する
            setScaledAttribute(entity, Attributes.MAX_HEALTH, 1.5);
            if (entity.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                entity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.24); // 他のボスより少し速めに補強
            }
            // 攻撃力も1.2倍
            setScaledAttribute(entity, Attributes.ATTACK_DAMAGE, 1.2);
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
            armor.setBaseValue(Math.min(30.0D, 7.0D + floor * 0.30D));
        }
    }

    /** プレフィックス固有の追加効果 */
    private static void applyPrefixEffects(LivingEntity entity, Prefix prefix) {
        // TODO: 効果キャッシュが必要になったら、prefix効果の副作用とMobEffect寿命を先に設計する。
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

    private static double getEarlyDamageMult(int floor) {
        return switch (Math.max(1, floor)) {
            case 1 -> 0.65D;
            case 2 -> 0.75D;
            case 3 -> 0.85D;
            case 4 -> 0.95D;
            default -> 1.0D;
        };
    }

    /** 属性値を倍率で設定するヘルパー */
    private static void setScaledAttribute(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attr, double multiplier) {
        AttributeInstance instance = entity.getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }
}
