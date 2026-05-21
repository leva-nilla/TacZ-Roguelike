package com.levanilla.rogue.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;

import java.util.HashSet;
import java.util.Set;

/**
 * ランの深度進行と永続進行に関する状態。
 */
final class DeepProgressData {

    /** 現在の階層 (1-based, 0=ラン未開始) */
    private int currentFloor = 0;
    /** 最高到達フロア (過去クリアしたフロアの最高値) */
    private int maxReachedFloor = 0;
    /** ランが進行中か */
    private boolean runActive = false;
    /** 弾薬容量レベル */
    private int ammoCapacityLevel = 0;
    /** 現フロアがクリア済みか */
    private boolean floorCleared = false;
    /** ランごとのシード値 */
    private long runSeed = 0;
    /** 同じフロアを再生成するたびに変える生成ソルト */
    private long floorSeedSalt = 0;
    /** テーマ表示名 */
    private String themeName = "RUINS - OVERGROWN";
    /** レガシーテーマ enum */
    private RunManager.Theme theme = RunManager.Theme.MANSION;
    /** このプレイヤーのダンジョン基準座標（マルチプレイヤー座標分離用） */
    private BlockPos dungeonOrigin = new BlockPos(0, GameConstants.DUNGEON_BASE_Y, 0);
    /** フロア開始時のサーバーtick（クリア判定猶予用） */
    private long floorStartTick = 0;
    /** 初回ボス報酬を受領済みのフロア */
    private final Set<Integer> claimedBossRewardFloors = new HashSet<>();

    int getCurrentFloor() { return currentFloor; }
    void setCurrentFloor(int f) { currentFloor = f; }

    int getMaxReachedFloor() { return maxReachedFloor; }
    void setMaxReachedFloor(int f) { maxReachedFloor = f; }

    boolean isRunActive() { return runActive; }
    void setRunActive(boolean v) { runActive = v; }

    int getAmmoCapacityLevel() { return ammoCapacityLevel; }
    void setAmmoCapacityLevel(int ammoCapacityLevel) { this.ammoCapacityLevel = ammoCapacityLevel; }

    boolean isFloorCleared() { return floorCleared; }
    void setFloorCleared(boolean v) { floorCleared = v; }

    long getRunSeed() { return runSeed; }
    void setRunSeed(long s) { runSeed = s; }

    long getFloorSeedSalt() { return floorSeedSalt; }
    void setFloorSeedSalt(long salt) { floorSeedSalt = salt; }

    void rerollFloorSeedSalt(long entropy) {
        floorSeedSalt = System.nanoTime()
            ^ Long.rotateLeft(entropy, 17)
            ^ ((long) currentFloor * 0x9E3779B97F4A7C15L);
        if (floorSeedSalt == 0) floorSeedSalt = 0xD1B54A32D192ED03L;
    }

    String getThemeName() { return themeName; }
    void setThemeName(String n) { themeName = n; }

    RunManager.Theme getTheme() { return theme; }
    void setTheme(RunManager.Theme t) { theme = t; }

    BlockPos getDungeonOrigin() { return dungeonOrigin; }
    void setDungeonOrigin(BlockPos pos) { dungeonOrigin = pos; }

    long getFloorStartTick() { return floorStartTick; }
    void setFloorStartTick(long tick) { floorStartTick = tick; }

    boolean hasClaimedBossReward(int floor) {
        return claimedBossRewardFloors.contains(floor);
    }

    void markBossRewardClaimed(int floor) {
        claimedBossRewardFloors.add(floor);
    }

    void startRun() {
        runActive = true;
        currentFloor = 1;
        if (currentFloor > maxReachedFloor) maxReachedFloor = currentFloor;
        floorCleared = false;
        runSeed = System.nanoTime();
        rerollFloorSeedSalt(runSeed);
        floorStartTick = 0; // 呼び出し元でサーバーtickを設定
    }

    void advanceFloor() {
        currentFloor++;
        if (currentFloor > maxReachedFloor) maxReachedFloor = currentFloor;
        floorCleared = false;
        runActive = true;
        rerollFloorSeedSalt(runSeed);
        floorStartTick = 0; // 呼び出し元でサーバーtickを設定
    }

    void saveTo(CompoundTag tag) {
        tag.putInt("CurrentFloor", currentFloor);
        tag.putInt("MaxReachedFloor", maxReachedFloor);
        tag.putBoolean("RunActive", runActive);
        tag.putInt("AmmoCapacityLevel", ammoCapacityLevel);
        tag.putBoolean("FloorCleared", floorCleared);
        tag.putLong("RunSeed", runSeed);
        tag.putLong("FloorSeedSalt", floorSeedSalt);
        tag.putString("ThemeName", themeName);
        tag.putString("ThemeEnum", theme.name());
        if (dungeonOrigin != null) {
            tag.putInt("OriginX", dungeonOrigin.getX());
            tag.putInt("OriginY", dungeonOrigin.getY());
            tag.putInt("OriginZ", dungeonOrigin.getZ());
        }
        tag.putLong("FloorStartTick", floorStartTick);
        ListTag bossRewards = new ListTag();
        for (Integer floor : claimedBossRewardFloors) {
            bossRewards.add(IntTag.valueOf(floor));
        }
        tag.put("ClaimedBossRewardFloors", bossRewards);
    }

    void loadFrom(CompoundTag tag) {
        if (tag.contains("CurrentFloor")) currentFloor = tag.getInt("CurrentFloor");
        if (tag.contains("MaxReachedFloor")) maxReachedFloor = tag.getInt("MaxReachedFloor");
        if (tag.contains("RunActive")) runActive = tag.getBoolean("RunActive");
        if (tag.contains("AmmoCapacityLevel")) ammoCapacityLevel = tag.getInt("AmmoCapacityLevel");
        if (tag.contains("FloorCleared")) floorCleared = tag.getBoolean("FloorCleared");
        if (tag.contains("RunSeed")) runSeed = tag.getLong("RunSeed");
        if (tag.contains("FloorSeedSalt")) floorSeedSalt = tag.getLong("FloorSeedSalt");
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
        claimedBossRewardFloors.clear();
        if (tag.contains("ClaimedBossRewardFloors")) {
            ListTag bossRewards = tag.getList("ClaimedBossRewardFloors", 3);
            for (int i = 0; i < bossRewards.size(); i++) {
                claimedBossRewardFloors.add(bossRewards.getInt(i));
            }
        }
    }

    void reset() {
        currentFloor = 0;
        // maxReachedFloor はリセットしない(全ロス時以外)
        runActive = false;
        floorCleared = false;
        runSeed = 0;
        floorSeedSalt = 0;
        themeName = "RUINS - OVERGROWN";
        theme = RunManager.Theme.MANSION;
        dungeonOrigin = new BlockPos(0, GameConstants.DUNGEON_BASE_Y, 0);
        floorStartTick = 0;
        // ボス初回報酬履歴は進行データなので通常リセットでは保持する
    }
}
