package com.levanilla.rogue.core;

/**
 * 難易度システム。スケーリング・ゴールド・ドロップ率に影響する。
 * SavedData によりワールド単位で永続化される。
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
                2.0f, 1.5f, 0.7f, 0.7f, 0.20f, 0xFFFF0000);  // v0.5: DMG 2.0→1.5, Gold 0.5→0.7, Drop 0.5→0.7, DeathPen 0.30→0.20

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

    /**
     * 難易度を設定し、SavedData に永続化する。
     * サーバー側からの難易度変更は必ずこのメソッドを使用すること。
     */
    public static void setDifficultyAndSave(net.minecraft.server.level.ServerLevel level, Difficulty d) {
        currentDifficulty = d;
        DifficultySavedData savedData = DifficultySavedData.get(level);
        savedData.setDifficulty(d);
    }

    public static void setDifficultyAndSave(net.minecraft.server.level.ServerLevel level, int ordinal) {
        if (ordinal >= 0 && ordinal < Difficulty.values().length) {
            setDifficultyAndSave(level, Difficulty.values()[ordinal]);
        }
    }

    /**
     * SavedData からワールドに保存された難易度を復元する。
     * サーバー起動時に1度だけ呼び出すこと。
     *
     * 新規ワールドの場合: SavedData はまだ存在しない（デフォルト NORMAL）。
     * MixinCreateWorldScreen で既にクライアント側で難易度が選択されている場合、
     * メモリ上の値を SavedData に保存する（シングルプレイでは JVM が共有されるため）。
     */
    public static void loadFromSavedData(net.minecraft.server.level.ServerLevel level) {
        DifficultySavedData savedData = DifficultySavedData.get(level);
        Difficulty saved = savedData.getDifficulty();
        if (currentDifficulty != Difficulty.NORMAL && saved == Difficulty.NORMAL) {
            // 新規ワールド: CreateWorldScreen で選択済みの難易度をSavedDataに永続化
            savedData.setDifficulty(currentDifficulty);
        } else {
            // 既存ワールド: SavedData から復元
            currentDifficulty = saved;
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

