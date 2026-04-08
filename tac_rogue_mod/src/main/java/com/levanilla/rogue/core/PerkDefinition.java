package com.levanilla.rogue.core;

/**
 * パークの「設計図」を保持するデータクラス。
 * カテゴリ(25) × 修飾子(11) × レベル(1-10) = 2,750 種類のプロシージャル生成パーク。
 */
public class PerkDefinition {

    /** パークの効果カテゴリ (25 種) */
    public enum Category {
        VITALITY("Vitality", "perk.tac_rogue.cat.vitality", 0xFF00FF88),
        REGENERATION("Regeneration", "perk.tac_rogue.cat.regeneration", 0xFF00FF00),
        ARMOR("Armor", "perk.tac_rogue.cat.armor", 0xFF8888FF),
        VELOCITY("Velocity", "perk.tac_rogue.cat.velocity", 0xFF00FFFF),
        STAMINA("Stamina", "perk.tac_rogue.cat.stamina", 0xFF00DDFF),
        DAMAGE("Damage", "perk.tac_rogue.cat.damage", 0xFFFF4444),
        GUN_PROFICIENCY("Gun Proficiency", "perk.tac_rogue.cat.gun_proficiency", 0xFFFFAA00),
        RELOAD_SPEED("Reload Speed", "perk.tac_rogue.cat.reload_speed", 0xFFFFCC00),
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

    // ===== フィールド =====
    public final Category category;
    public final Modifier modifier;
    public final int level;

    public PerkDefinition(Category category, Modifier modifier, int level) {
        this.category = category;
        this.modifier = modifier;
        this.level = Math.max(1, Math.min(10, level));
    }

    /** パークの基本効果値を算出 (カテゴリの基礎値 × レベル × 修飾子) */
    public float calculateEffect() {
        return level * 10.0f * modifier.multiplier;
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
        if (category == Category.REGENERATION || category == Category.VAMPIRE || category == Category.BLOODLUST || category == Category.QUICK_FIX) {
            valueStr = String.format("%.1f", effect / 10.0f);
        } else {
            valueStr = String.valueOf((int) effect);
        }
        return net.minecraft.network.chat.Component.translatable(category.descriptionKey, valueStr);
    }

    /** 文字列として説明を返す（ログ等のフォールバック用） */
    public String getDescription() {
        return getDescriptionComponent().getString();
    }

    /** レア度に応じた色コード */
    public int getRarityColor() {
        if (modifier.tier == 5) return modifier.color;
        if (modifier.tier == 4) return modifier.color;
        if (modifier == Modifier.BLESSED || modifier == Modifier.RADIANT) return modifier.color;
        if (modifier == Modifier.REINFORCED) return modifier.color;
        return modifier.color;
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
