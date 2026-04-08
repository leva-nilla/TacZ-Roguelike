package com.levanilla.rogue.core;

/**
 * 難易度システム。スケーリング・ゴールド・ドロップ率に影響する。
 */
public class DifficultyManager {

    public enum Difficulty {
        EASY("EASY", "difficulty.tac_rogue.easy.desc",
             0.7f, 0.8f, 1.5f, 1.3f, 0.0f, 0xFF55FF55),
        NORMAL("NORMAL", "difficulty.tac_rogue.normal.desc",
               1.0f, 1.0f, 1.0f, 1.0f, 0.05f, 0xFFFFFFFF),
        HARD("HARD", "difficulty.tac_rogue.hard.desc",
             1.5f, 1.2f, 0.8f, 0.8f, 0.15f, 0xFFFF5555),
        EXTREME("EXTREME", "difficulty.tac_rogue.extreme.desc",
                2.0f, 2.0f, 0.5f, 0.5f, 0.30f, 0xFFFF0000);

        public final String displayName;
        public final String description;
        public final float hpScale;      // 敵 HP 倍率
        public final float dmgScale;     // 敵攻撃力倍率
        public final float goldScale;    // ゴールド報酬倍率
        public final float dropScale;    // アイテムドロップ率倍率
        public final float deathPenalty; // デスペナルティ(所持金の割合)
        public final int color;

        Difficulty(String name, String desc, float hp, float dmg, float gold, float drop, float deathPen, int color) {
            this.displayName = name;
            this.description = desc;
            this.hpScale = hp;
            this.dmgScale = dmg;
            this.goldScale = gold;
            this.dropScale = drop;
            this.deathPenalty = deathPen;
            this.color = color;
        }
    }

    private static Difficulty currentDifficulty = Difficulty.NORMAL;

    public static Difficulty getDifficulty() { return currentDifficulty; }
    public static void setDifficulty(Difficulty d) { currentDifficulty = d; }
    public static void setDifficulty(int ordinal) {
        if (ordinal >= 0 && ordinal < Difficulty.values().length) {
            currentDifficulty = Difficulty.values()[ordinal];
        }
    }

    /** 敵 HP のスケール値（階層 + 難易度） */
    public static float getHpScale(int floor) {
        return currentDifficulty.hpScale;
    }

    /** 敵攻撃力のスケール値 */
    public static float getDmgScale(int floor) {
        return currentDifficulty.dmgScale;
    }

    /** ゴールド報酬倍率 */
    public static float getGoldMultiplier() {
        return currentDifficulty.goldScale;
    }

    /** ドロップ率倍率 */
    public static float getDropMultiplier() {
        return currentDifficulty.dropScale;
    }

    /** デスペナルティ率 (0.0 ~ 1.0) */
    public static float getDeathPenaltyRate() {
        return currentDifficulty.deathPenalty;
    }
}
