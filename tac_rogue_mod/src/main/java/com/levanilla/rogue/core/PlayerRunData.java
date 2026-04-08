package com.levanilla.rogue.core;

import net.minecraft.core.BlockPos;

/**
 * プレイヤー個別のローグライクラン進行データ。
 * RunManager が UUID → PlayerRunData の Map で管理する。
 */
public class PlayerRunData {

    /** 現在の階層 (1-based, 0=ラン未開始) */
    private int currentFloor = 0;
    /** 最高到達フロア (過去クリアしたフロアの最高値) */
    private int maxReachedFloor = 0;
    /** ランが進行中か */
    private boolean runActive = false;
    /** 現フロアがクリア済みか */
    private boolean floorCleared = false;
    /** ランごとのシード値 */
    private long runSeed = 0;
    /** テーマ表示名 */
    private String themeName = "RUINS - OVERGROWN";
    /** レガシーテーマ enum */
    private RunManager.Theme theme = RunManager.Theme.MANSION;
    /** このプレイヤーのダンジョン基準座標（マルチプレイヤー座標分離用） */
    private BlockPos dungeonOrigin;
    /** フロア開始時のサーバーtick（クリア判定猶予用） */
    private long floorStartTick = 0;

    public PlayerRunData() {
        this.dungeonOrigin = new BlockPos(0, GameConstants.DUNGEON_BASE_Y, 0);
    }

    // ===== アクセサ =====

    public int getCurrentFloor() { return currentFloor; }
    public void setCurrentFloor(int f) { currentFloor = f; }

    public int getMaxReachedFloor() { return maxReachedFloor; }
    public void setMaxReachedFloor(int f) { maxReachedFloor = f; }

    public boolean isRunActive() { return runActive; }
    public void setRunActive(boolean v) { runActive = v; }

    public boolean isFloorCleared() { return floorCleared; }
    public void setFloorCleared(boolean v) { floorCleared = v; }

    public long getRunSeed() { return runSeed; }
    public void setRunSeed(long s) { runSeed = s; }

    public String getThemeName() { return themeName; }
    public void setThemeName(String n) { themeName = n; }

    public RunManager.Theme getTheme() { return theme; }
    public void setTheme(RunManager.Theme t) { theme = t; }

    public BlockPos getDungeonOrigin() { return dungeonOrigin; }
    public void setDungeonOrigin(BlockPos pos) { dungeonOrigin = pos; }

    public long getFloorStartTick() { return floorStartTick; }
    public void setFloorStartTick(long tick) { floorStartTick = tick; }

    // ===== ライフサイクル =====

    /** 新規ランを開始する */
    public void startRun() {
        runActive = true;
        currentFloor = 1;
        if (currentFloor > maxReachedFloor) maxReachedFloor = currentFloor;
        floorCleared = false;
        runSeed = System.nanoTime();
        floorStartTick = 0; // 呼び出し元でサーバーtickを設定
    }

    /** 次の階層へ進む */
    public void advanceFloor() {
        currentFloor++;
        if (currentFloor > maxReachedFloor) maxReachedFloor = currentFloor;
        floorCleared = false;
        runActive = true;
        floorStartTick = 0; // 呼び出し元でサーバーtickを設定
    }

    // ===== NBT セーブ・ロード =====

    public net.minecraft.nbt.CompoundTag saveToNbt() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("CurrentFloor", currentFloor);
        tag.putInt("MaxReachedFloor", maxReachedFloor);
        tag.putBoolean("RunActive", runActive);
        tag.putBoolean("FloorCleared", floorCleared);
        tag.putLong("RunSeed", runSeed);
        tag.putString("ThemeName", themeName);
        tag.putString("ThemeEnum", theme.name());
        if (dungeonOrigin != null) {
            tag.putInt("OriginX", dungeonOrigin.getX());
            tag.putInt("OriginY", dungeonOrigin.getY());
            tag.putInt("OriginZ", dungeonOrigin.getZ());
        }
        tag.putLong("FloorStartTick", floorStartTick);
        return tag;
    }

    public void loadFromNbt(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("CurrentFloor")) currentFloor = tag.getInt("CurrentFloor");
        if (tag.contains("MaxReachedFloor")) maxReachedFloor = tag.getInt("MaxReachedFloor");
        if (tag.contains("RunActive")) runActive = tag.getBoolean("RunActive");
        if (tag.contains("FloorCleared")) floorCleared = tag.getBoolean("FloorCleared");
        if (tag.contains("RunSeed")) runSeed = tag.getLong("RunSeed");
        if (tag.contains("ThemeName")) themeName = tag.getString("ThemeName");
        if (tag.contains("ThemeEnum")) {
            try {
                theme = RunManager.Theme.valueOf(tag.getString("ThemeEnum"));
            } catch (IllegalArgumentException e) {
                theme = RunManager.Theme.MANSION;
            }
        }
        if (tag.contains("OriginX") && tag.contains("OriginY") && tag.contains("OriginZ")) {
            dungeonOrigin = new BlockPos(tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ"));
        }
        if (tag.contains("FloorStartTick")) floorStartTick = tag.getLong("FloorStartTick");
    }

    /** ラン状態をリセット */
    public void reset() {
        currentFloor = 0;
        // maxReachedFloor はリセットしない(全ロス時以外)
        runActive = false;
        floorCleared = false;
        runSeed = 0;
        themeName = "RUINS - OVERGROWN";
        theme = RunManager.Theme.MANSION;
        dungeonOrigin = new BlockPos(0, GameConstants.DUNGEON_BASE_Y, 0);
        floorStartTick = 0;
    }
}
