package com.levanilla.rogue.core;

import net.minecraft.core.BlockPos;
import java.util.HashSet;
import java.util.Set;

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
    private BlockPos dungeonOrigin;
    /** フロア開始時のサーバーtick（クリア判定猶予用） */
    private long floorStartTick = 0;
    /** 初回ボス報酬を受領済みのフロア */
    private final Set<Integer> claimedBossRewardFloors = new HashSet<>();
    /** 同一ラン内で取得済みの補給チェスト。retry floorでのチェスト厳選を防ぐ。 */
    private final Set<String> claimedSupplyChestKeys = new HashSet<>();

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

    public int getAmmoCapacityLevel() { return ammoCapacityLevel; }
    public void setAmmoCapacityLevel(int ammoCapacityLevel) { this.ammoCapacityLevel = ammoCapacityLevel; }

    public boolean isFloorCleared() { return floorCleared; }
    public void setFloorCleared(boolean v) { floorCleared = v; }

    public long getRunSeed() { return runSeed; }
    public void setRunSeed(long s) { runSeed = s; }
    public long getFloorSeedSalt() { return floorSeedSalt; }
    public void setFloorSeedSalt(long salt) { floorSeedSalt = salt; }
    public void rerollFloorSeedSalt(long entropy) {
        floorSeedSalt = System.nanoTime()
            ^ Long.rotateLeft(entropy, 17)
            ^ ((long) currentFloor * 0x9E3779B97F4A7C15L);
        if (floorSeedSalt == 0) floorSeedSalt = 0xD1B54A32D192ED03L;
    }

    public String getThemeName() { return themeName; }
    public void setThemeName(String n) { themeName = n; }

    public RunManager.Theme getTheme() { return theme; }
    public void setTheme(RunManager.Theme t) { theme = t; }

    public BlockPos getDungeonOrigin() { return dungeonOrigin; }
    public void setDungeonOrigin(BlockPos pos) { dungeonOrigin = pos; }

    public long getFloorStartTick() { return floorStartTick; }
    public void setFloorStartTick(long tick) { floorStartTick = tick; }

    public boolean hasClaimedBossReward(int floor) {
        return claimedBossRewardFloors.contains(floor);
    }

    public void markBossRewardClaimed(int floor) {
        claimedBossRewardFloors.add(floor);
    }

    public boolean hasClaimedSupplyChest(String key) {
        return key != null && claimedSupplyChestKeys.contains(key);
    }

    public void markSupplyChestClaimed(String key) {
        if (key != null && !key.isBlank()) claimedSupplyChestKeys.add(key);
    }

    // ===== ライフサイクル =====

    /** 新規ランを開始する */
    public void startRun() {
        runActive = true;
        currentFloor = 1;
        if (currentFloor > maxReachedFloor) maxReachedFloor = currentFloor;
        floorCleared = false;
        runSeed = System.nanoTime();
        rerollFloorSeedSalt(runSeed);
        floorStartTick = 0; // 呼び出し元でサーバーtickを設定
        claimedSupplyChestKeys.clear();
    }

    /** 次の階層へ進む */
    public void advanceFloor() {
        currentFloor++;
        if (currentFloor > maxReachedFloor) maxReachedFloor = currentFloor;
        floorCleared = false;
        runActive = true;
        rerollFloorSeedSalt(runSeed);
        floorStartTick = 0; // 呼び出し元でサーバーtickを設定
    }

    // ===== NBT セーブ・ロード =====

    public net.minecraft.nbt.CompoundTag saveToNbt() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
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
        net.minecraft.nbt.ListTag bossRewards = new net.minecraft.nbt.ListTag();
        for (Integer floor : claimedBossRewardFloors) {
            bossRewards.add(net.minecraft.nbt.IntTag.valueOf(floor));
        }
        tag.put("ClaimedBossRewardFloors", bossRewards);
        net.minecraft.nbt.ListTag supplyChests = new net.minecraft.nbt.ListTag();
        for (String key : claimedSupplyChestKeys) {
            supplyChests.add(net.minecraft.nbt.StringTag.valueOf(key));
        }
        tag.put("ClaimedSupplyChestKeys", supplyChests);
        return tag;
    }

    public void loadFromNbt(net.minecraft.nbt.CompoundTag tag) {
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
            net.minecraft.nbt.ListTag bossRewards = tag.getList("ClaimedBossRewardFloors", 3);
            for (int i = 0; i < bossRewards.size(); i++) {
                claimedBossRewardFloors.add(bossRewards.getInt(i));
            }
        }
        claimedSupplyChestKeys.clear();
        if (tag.contains("ClaimedSupplyChestKeys")) {
            net.minecraft.nbt.ListTag supplyChests = tag.getList("ClaimedSupplyChestKeys", 8);
            for (int i = 0; i < supplyChests.size(); i++) {
                claimedSupplyChestKeys.add(supplyChests.getString(i));
            }
        }
    }

    /** ラン状態をリセット */
    public void reset() {
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
        claimedSupplyChestKeys.clear();
        // ボス初回報酬履歴は進行データなので通常リセットでは保持する
    }
}
