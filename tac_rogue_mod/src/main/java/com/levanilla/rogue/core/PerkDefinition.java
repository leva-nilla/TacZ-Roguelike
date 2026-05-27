package com.levanilla.rogue.core;

/**
 * パークの「設計図」を保持するデータクラス。
 * カテゴリ × 修飾子 × レベルでプロシージャル生成されるパーク。
 */
public class PerkDefinition {

    /** パークの効果カテゴリ */
    public enum Category {
        VITALITY("Vitality", "perk.tac_rogue.cat.vitality", 0xFF00FF88),
        REGENERATION("Regeneration", "perk.tac_rogue.cat.regeneration", 0xFF00FF00),
        ARMOR("Armor", "perk.tac_rogue.cat.armor", 0xFF8888FF),
        VELOCITY("Velocity", "perk.tac_rogue.cat.velocity", 0xFF00FFFF),
        STAMINA("Stamina", "perk.tac_rogue.cat.stamina", 0xFF00DDFF),
        DAMAGE("Damage", "perk.tac_rogue.cat.damage", 0xFFFF4444),
        GUN_PROFICIENCY("Gun Proficiency", "perk.tac_rogue.cat.gun_proficiency", 0xFFFFAA00),
        FIRE_RATE("Fire Rate", "perk.tac_rogue.cat.fire_rate", 0xFFFF7744),
        MELEE_SPEED("Melee Speed", "perk.tac_rogue.cat.melee_speed", 0xFFFF5555),
        RELOAD_SPEED("Reload Speed", "perk.tac_rogue.cat.reload_speed", 0xFFFFCC00),
        MAG_SIZE("Mag Size", "perk.tac_rogue.cat.mag_size", 0xFFAAFFCC),
        AUTOLOADER("Autoloader", "perk.tac_rogue.cat.autoloader", 0xFF66EEFF),
        AMMO_EFFICIENCY("Ammo Saver", "perk.tac_rogue.cat.ammo_efficiency", 0xFFDDDD00),
        SCAVENGER("Scavenger", "perk.tac_rogue.cat.scavenger", 0xFF88FF88),
        GOLD_RUSH("Gold Rush", "perk.tac_rogue.cat.gold_rush", 0xFFFFD700),
        VAMPIRE("Blood Harvest", "perk.tac_rogue.cat.vampire", 0xFFCC0000),
        EXPLOSIVE("Explosive Rounds", "perk.tac_rogue.cat.explosive", 0xFFFF6600),
        RESISTANCE("Resistance", "perk.tac_rogue.cat.resistance", 0xFFAA88FF),
        FORTUNE("Fortune", "perk.tac_rogue.cat.fortune", 0xFFFFFF00),
        HANDLING("Handling", "perk.tac_rogue.cat.handling", 0xFFBBBBFF),
        SHARPSHOOTER("Sharpshooter", "perk.tac_rogue.cat.sharpshooter", 0xFF88CCFF),
        EXECUTIONER("Executioner", "perk.tac_rogue.cat.executioner", 0xFF8B0000),
        DODGE("Dodge", "perk.tac_rogue.cat.dodge", 0xFFFFFFFF),
        ADRENALINE("Adrenaline", "perk.tac_rogue.cat.adrenaline", 0xFFFF00FF),
        BLOODLUST("Bloodlust", "perk.tac_rogue.cat.bloodlust", 0xFFBB0000),
        MEDIC("Medic", "perk.tac_rogue.cat.medic", 0xFF00FFCC),
        HEAD_HUNTER("Head Hunter", "perk.tac_rogue.cat.head_hunter", 0xFFFF8844),
        STEALTH_EXTEND("Stealth Extend", "perk.tac_rogue.cat.stealth_extend", 0xFF444444),
        QUICK_FIX("Quick Fix", "perk.tac_rogue.cat.quick_fix", 0xFF88FF00);

        public final String displayName;
        public final String descriptionKey;
        public final int color;

        Category(String displayName, String descKey, int color) {
            this.displayName = displayName;
            this.descriptionKey = descKey;
            this.color = color;
        }
    }

    /** 修飾子 (11 種) — パークの品質とトレードオフを決定 */
    public enum Modifier {
        NONE("", 1.0f, "", 0xFFAAAAAA, 1),
        RADIANT("Radiant", 1.2f, "perk.tac_rogue.mod.radiant", 0xFFFFFFCC, 2),
        BLESSED("Blessed", 1.3f, "perk.tac_rogue.mod.blessed", 0xFFFFD700, 2),
        PRIMAL("Primal", 1.4f, "perk.tac_rogue.mod.primal", 0xFF33AA33, 2),
        REINFORCED("Reinforced", 1.5f, "perk.tac_rogue.mod.reinforced", 0xFF5588FF, 3),
        FRACTURED("Fractured", 1.6f, "perk.tac_rogue.mod.fractured", 0xFF888888, 3),
        CURSED("Cursed", 1.8f, "perk.tac_rogue.mod.cursed", 0xFF9933CC, 4),
        OVERCLOCKED("Overclocked", 2.0f, "perk.tac_rogue.mod.overclocked", 0xFFFF8800, 4),
        VOLATILE("Volatile", 2.0f, "perk.tac_rogue.mod.volatile", 0xFFFF3300, 4),
        CORRUPTED("Corrupted", 2.5f, "perk.tac_rogue.mod.corrupted", 0xFFAA0000, 5),
        TITANIC("Titanic", 3.0f, "perk.tac_rogue.mod.titanic", 0xFF00AAFF, 5);

        public final String prefix;
        public final float multiplier;
        public final String tradeoff;
        public final int color;
        public final int tier;

        Modifier(String prefix, float mult, String tradeoff, int color, int tier) {
            this.prefix = prefix;
            this.multiplier = mult;
            this.tradeoff = tradeoff;
            this.color = color;
            this.tier = tier;
        }

        public boolean isRare() {
            return this.tier >= 3;
        }
    }

    public enum CursedPenaltyTarget {
        VITALITY("perk.tac_rogue.cursed.vitality"),
        ARMOR("perk.tac_rogue.cursed.armor"),
        VELOCITY("perk.tac_rogue.cursed.velocity"),
        STAMINA("perk.tac_rogue.cursed.stamina");

        public final String descriptionKey;

        CursedPenaltyTarget(String descriptionKey) {
            this.descriptionKey = descriptionKey;
        }
    }

    // ===== フィールド =====
    public final Category category;
    public final Modifier modifier;
    public final int level;

    public PerkDefinition(Category category, Modifier modifier, int level) {
        this.category = category;
        this.modifier = modifier;
        this.level = Math.max(1, Math.min(10, level));
    }

    /** パークの基本効果値を算出 (Lvソフトキャップ × カテゴリ係数 × 修飾子) */
    public float calculateEffect() {
        return effectiveLevel(level)
            * GameConstants.PERK_EFFECT_PER_LEVEL
            * categoryScale(category)
            * modifier.multiplier;
    }

    private static float effectiveLevel(int level) {
        int cappedLevel = Math.max(1, Math.min(10, level));
        if (cappedLevel <= GameConstants.PERK_LEVEL_SOFTCAP_START) {
            return cappedLevel;
        }
        return GameConstants.PERK_LEVEL_SOFTCAP_START
            + (cappedLevel - GameConstants.PERK_LEVEL_SOFTCAP_START)
            * GameConstants.PERK_LEVEL_POST_SOFTCAP_SCALE;
    }

    private static float categoryScale(Category category) {
        return switch (category) {
            case DAMAGE, FIRE_RATE, MELEE_SPEED, RELOAD_SPEED, MAG_SIZE, GOLD_RUSH, VELOCITY, STAMINA, DODGE ->
                GameConstants.PERK_STRONG_CATEGORY_SCALE;
            case AUTOLOADER, REGENERATION, VAMPIRE, BLOODLUST, QUICK_FIX ->
                GameConstants.PERK_RECOVERY_CATEGORY_SCALE;
            default -> 1.0f;
        };
    }

    public static float softcapTotalEffect(Category category, float rawTotal) {
        if (rawTotal <= 0.0f) return rawTotal;
        return switch (category) {
            case DAMAGE, FIRE_RATE, MELEE_SPEED, RELOAD_SPEED, MAG_SIZE, GOLD_RUSH, DODGE, AUTOLOADER,
                    REGENERATION, VAMPIRE, BLOODLUST, QUICK_FIX ->
                segmentedSoftcap(rawTotal, 45.0f, 90.0f, 150.0f, 0.70f, 0.55f, 0.38f);
            case VITALITY, ARMOR, VELOCITY, STAMINA ->
                segmentedSoftcap(rawTotal, 60.0f, 120.0f, 200.0f, 0.80f, 0.65f, 0.48f);
            case SCAVENGER, RESISTANCE, STEALTH_EXTEND, MEDIC, AMMO_EFFICIENCY, EXPLOSIVE, FORTUNE,
                    HANDLING, SHARPSHOOTER, EXECUTIONER, ADRENALINE, GUN_PROFICIENCY, HEAD_HUNTER ->
                segmentedSoftcap(rawTotal, 75.0f, 150.0f, 250.0f, 0.90f, 0.78f, 0.60f);
        };
    }

    private static float segmentedSoftcap(float value, float first, float second, float third,
                                          float secondScale, float thirdScale, float finalScale) {
        if (value <= first) return value;
        float result = first;
        float secondAmount = Math.min(value, second) - first;
        if (secondAmount > 0.0f) {
            result += secondAmount * secondScale;
        }
        float thirdAmount = Math.min(value, third) - second;
        if (thirdAmount > 0.0f) {
            result += thirdAmount * thirdScale;
        }
        float finalAmount = value - third;
        if (finalAmount > 0.0f) {
            result += finalAmount * finalScale;
        }
        return result;
    }

    /** 表示名を動的生成 */
    public String getDisplayName() {
        String prefix = modifier.prefix.isEmpty() ? "" : modifier.prefix + " ";
        return prefix + category.displayName + " Lv." + level;
    }

    /** クライアント描画用: Componentとして説明文を返す */
    public net.minecraft.network.chat.Component getDescriptionComponent() {
        float effect = calculateEffect();
        String valueStr;
        if (category == Category.REGENERATION) {
            valueStr = String.format(java.util.Locale.ROOT, "%.1f", getRegenerationHealPerSecond(effect));
        } else if (category == Category.VAMPIRE || category == Category.BLOODLUST || category == Category.QUICK_FIX) {
            valueStr = String.format(java.util.Locale.ROOT, "%.1f", getRecoveryHealAmount(effect));
        } else if (category == Category.AUTOLOADER) {
            valueStr = String.format(java.util.Locale.ROOT, "%.1f", getAutoloaderRoundsPerSecond(effect));
        } else if (category == Category.DODGE) {
            valueStr = String.format(java.util.Locale.ROOT, "%.1f", getDodgeChancePercent(effect));
        } else {
            valueStr = String.valueOf((int) effect);
        }
        return net.minecraft.network.chat.Component.translatable(category.descriptionKey, valueStr);
    }

    public static float getAutoloaderRoundsPerSecond(float effect) {
        return Math.max(GameConstants.AUTOLOADER_MIN_ROUNDS_PER_SECOND,
            effect / GameConstants.AUTOLOADER_EFFECT_DIVISOR);
    }

    public static float getDodgeChancePercent(float effect) {
        return effect * GameConstants.DODGE_EFFECT_SCALE;
    }

    public static float getAmmoSaveChancePercent(float effect) {
        return effect * GameConstants.AMMO_SAVE_EFFECT_SCALE;
    }

    public static float getCriticalChance(float effect) {
        return Math.min(GameConstants.CRITICAL_CHANCE_MAX, Math.max(0.0f, effect / 100.0f));
    }

    public static float getCriticalDamageMultiplier(float effect) {
        float rawChance = Math.max(0.0f, effect / 100.0f);
        float overflow = Math.max(0.0f, rawChance - GameConstants.CRITICAL_CHANCE_MAX);
        float bonus = Math.min(GameConstants.CRITICAL_OVERFLOW_DAMAGE_MAX,
            overflow * GameConstants.CRITICAL_OVERFLOW_DAMAGE_SCALE);
        return GameConstants.CRITICAL_DAMAGE_MULT + bonus;
    }

    public static float getRegenerationHealPerSecond(float effect) {
        return effect / GameConstants.REGEN_EFFECT_HEAL_DIVISOR;
    }

    public static float getRegenerationCapUnlockRatio(float effect) {
        return effect * GameConstants.REGEN_CAP_UNLOCK_PER_EFFECT;
    }

    public static float getRecoveryHealAmount(float effect) {
        return effect / GameConstants.PERK_RECOVERY_HEAL_DIVISOR;
    }

    public net.minecraft.network.chat.Component getModifierDescriptionComponent() {
        if (modifier.tradeoff == null || modifier.tradeoff.isEmpty()) {
            return net.minecraft.network.chat.Component.translatable("perk.tac_rogue.mod.none");
        }
        return net.minecraft.network.chat.Component.translatable(modifier.tradeoff);
    }

    public static CursedPenaltyTarget resolveCursedPenaltyTarget(java.util.UUID playerId, String perkTag) {
        int seed = 31 * playerId.hashCode() + (perkTag == null ? 0 : perkTag.hashCode());
        CursedPenaltyTarget[] values = CursedPenaltyTarget.values();
        return values[Math.floorMod(seed, values.length)];
    }

    public static net.minecraft.network.chat.Component getCursedPenaltyDescription(java.util.UUID playerId, String perkTag) {
        CursedPenaltyTarget target = resolveCursedPenaltyTarget(playerId, perkTag);
        return net.minecraft.network.chat.Component.translatable(target.descriptionKey);
    }

    /** 文字列として説明を返す（ログ等のフォールバック用） */
    public String getDescription() {
        return getDescriptionComponent().getString();
    }

    /** レア度に応じた色コード */
    public int getRarityColor() {
        // MAINT-2: 全パスが modifier.color を返していたので簡略化
        return modifier.color;
    }

    /**
     * PERF-2: パーク効果合算の共通ユーティリティ。
     * CombatEventHandler と TacZEventHandler の両方で使用される。
     * @param player 対象プレイヤー
     * @param perkPrefix パークタグのプレフィックス（例: "perk:DAMAGE"）
     * @return 全該当パークの効果値合計
     */
    public static float sumEffect(net.minecraft.server.level.ServerPlayer player, String perkPrefix) {
        return com.levanilla.rogue.core.service.PerkStorageService.sumEffect(player, perkPrefix);
    }

    public static float sumCategoryEffect(net.minecraft.world.entity.LivingEntity entity, Category category) {
        return com.levanilla.rogue.core.service.PerkStorageService.sumCategoryEffect(entity, category);
    }

    /** シリアライズ用のタグ文字列 (プレイヤーの Tag として保存) */
    public String toTag() {
        return "perk:" + category.name() + ":" + modifier.name() + ":" + level;
    }

    /** タグ文字列からの復元 */
    public static PerkDefinition fromTag(String tag) {
        String[] parts = tag.replace("perk:", "").split(":");
        if (parts.length >= 3) {
            try {
                Category cat = Category.valueOf(parts[0]);
                Modifier mod = Modifier.valueOf(parts[1]);
                int lvl = Integer.parseInt(parts[2]);
                return new PerkDefinition(cat, mod, lvl);
            } catch (Exception e) { /* fall through */ }
        }
        return new PerkDefinition(Category.VITALITY, Modifier.NONE, 1);
    }
}
