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
    /** 同じフロアの再生成回数。生成器側の脱既視感用。 */
    private int floorAttemptIndex = 0;
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
    /** 現ランでフロアクリアパークを受領済みのフロア */
    private final Set<Integer> claimedPerkRewardFloors = new HashSet<>();
    /** 101層以降の専用通貨。 */
    private int deepCore = 0;
    /** Prestige実行回数。 */
    private int prestigeLevel = 0;
    /** Prestige後も保持する過去最高到達階層。 */
    private int highestEverFloor = 0;
    /** Deep Coreの5層帯クリア報酬を受け取った帯。 */
    private final Set<Integer> completedDeepBands = new HashSet<>();
    /** 深層任務を完了済みの帯。 */
    private final Set<Integer> completedDeepTaskBands = new HashSet<>();
    /** 現在の深層任務帯。 */
    private int currentDeepBand = 0;
    /** 現在の深層任務タイプ。 */
    private String currentDeepTaskType = "";
    /** 現在の深層任務進捗。 */
    private int deepTaskProgress = 0;
    /** 現在の深層任務目標値。 */
    private int deepTaskTarget = 0;
    private int provisionLevel = 0;
    private int preparedLevel = 0;
    private int selectionLevel = 0;
    private int supplyLineLevel = 0;
    private int blackMarketLevel = 0;
    private boolean firstPrestigeCacheClaimed = false;

    int getCurrentFloor() { return currentFloor; }
    void setCurrentFloor(int f) {
        currentFloor = f;
        updateHighestEverFloor(f);
    }

    int getMaxReachedFloor() { return maxReachedFloor; }
    void setMaxReachedFloor(int f) {
        maxReachedFloor = f;
        updateHighestEverFloor(f);
    }

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

    int getFloorAttemptIndex() { return floorAttemptIndex; }
    void setFloorAttemptIndex(int value) { floorAttemptIndex = Math.max(0, value); }

    void rerollFloorSeedSalt(long entropy) {
        floorSeedSalt = System.nanoTime()
            ^ Long.rotateLeft(entropy, 17)
            ^ ((long) currentFloor * 0x9E3779B97F4A7C15L);
        if (floorSeedSalt == 0) floorSeedSalt = 0xD1B54A32D192ED03L;
    }

    void startFloorAttempt(long entropy) {
        floorAttemptIndex = 1;
        rerollFloorSeedSalt(entropy ^ 0xA11CE5EEDL);
    }

    void nextFloorAttempt(long entropy) {
        floorAttemptIndex = Math.max(1, floorAttemptIndex + 1);
        rerollFloorSeedSalt(entropy ^ ((long) floorAttemptIndex * 0xC2B2AE3D27D4EB4FL));
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

    boolean hasClaimedPerkReward(int floor) {
        return claimedPerkRewardFloors.contains(floor);
    }

    void markPerkRewardClaimed(int floor) {
        if (floor > 0) claimedPerkRewardFloors.add(floor);
    }

    void clearRunRewardClaims() {
        claimedBossRewardFloors.clear();
        claimedPerkRewardFloors.clear();
    }

    int getDeepCore() { return deepCore; }
    void setDeepCore(int value) { deepCore = Math.max(0, value); }
    void addDeepCore(int amount) { deepCore = Math.max(0, deepCore + Math.max(0, amount)); }
    boolean consumeDeepCore(int amount) {
        int cost = Math.max(0, amount);
        if (deepCore < cost) return false;
        deepCore -= cost;
        return true;
    }

    int getPrestigeLevel() { return prestigeLevel; }
    void setPrestigeLevel(int value) { prestigeLevel = Math.max(0, value); }
    void addPrestigeLevel(int amount) { prestigeLevel = Math.max(0, prestigeLevel + Math.max(0, amount)); }

    int getHighestEverFloor() { return Math.max(highestEverFloor, Math.max(currentFloor, maxReachedFloor)); }
    void updateHighestEverFloor(int floor) {
        if (floor > highestEverFloor) highestEverFloor = floor;
    }

    boolean hasCompletedDeepBand(int band) { return completedDeepBands.contains(band); }
    void markDeepBandCompleted(int band) { if (band > 0) completedDeepBands.add(band); }
    boolean hasCompletedDeepTaskBand(int band) { return completedDeepTaskBands.contains(band); }
    void markDeepTaskBandCompleted(int band) { if (band > 0) completedDeepTaskBands.add(band); }

    int getCurrentDeepBand() { return currentDeepBand; }
    void setCurrentDeepBand(int value) { currentDeepBand = Math.max(0, value); }
    String getCurrentDeepTaskType() { return currentDeepTaskType == null ? "" : currentDeepTaskType; }
    void setCurrentDeepTaskType(String value) { currentDeepTaskType = value == null ? "" : value; }
    int getDeepTaskProgress() { return deepTaskProgress; }
    void setDeepTaskProgress(int value) { deepTaskProgress = Math.max(0, value); }
    int getDeepTaskTarget() { return deepTaskTarget; }
    void setDeepTaskTarget(int value) { deepTaskTarget = Math.max(0, value); }
    void clearCurrentDeepTask() {
        currentDeepBand = 0;
        currentDeepTaskType = "";
        deepTaskProgress = 0;
        deepTaskTarget = 0;
    }

    int getProvisionLevel() { return provisionLevel; }
    int getPreparedLevel() { return preparedLevel; }
    int getSelectionLevel() { return selectionLevel; }
    int getSupplyLineLevel() { return supplyLineLevel; }
    int getBlackMarketLevel() { return blackMarketLevel; }
    boolean isFirstPrestigeCacheClaimed() { return firstPrestigeCacheClaimed; }
    void setFirstPrestigeCacheClaimed(boolean value) { firstPrestigeCacheClaimed = value; }
    int getPrestigeUnlockLevel(String key) {
        return switch (key == null ? "" : key) {
            case "provision" -> provisionLevel;
            case "prepared" -> preparedLevel;
            case "selection" -> selectionLevel;
            case "supply_line" -> supplyLineLevel;
            case "black_market" -> blackMarketLevel;
            default -> 0;
        };
    }
    void setPrestigeUnlockLevel(String key, int level) {
        int safe = Math.max(0, Math.min(5, level));
        switch (key == null ? "" : key) {
            case "provision" -> provisionLevel = safe;
            case "prepared" -> preparedLevel = safe;
            case "selection" -> selectionLevel = safe;
            case "supply_line" -> supplyLineLevel = safe;
            case "black_market" -> blackMarketLevel = safe;
            default -> {}
        }
    }

    void startRun() {
        runActive = true;
        currentFloor = 1;
        if (currentFloor > maxReachedFloor) maxReachedFloor = currentFloor;
        updateHighestEverFloor(currentFloor);
        floorCleared = false;
        runSeed = System.nanoTime();
        startFloorAttempt(runSeed);
        claimedPerkRewardFloors.clear();
        floorStartTick = 0; // 呼び出し元でサーバーtickを設定
    }

    void advanceFloor() {
        currentFloor++;
        if (currentFloor > maxReachedFloor) maxReachedFloor = currentFloor;
        updateHighestEverFloor(currentFloor);
        floorCleared = false;
        runActive = true;
        startFloorAttempt(runSeed);
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
        tag.putInt("FloorAttemptIndex", floorAttemptIndex);
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
        tag.put("ClaimedPerkRewardFloors", intSetToTag(claimedPerkRewardFloors));
        tag.putInt("DeepCore", deepCore);
        tag.putInt("PrestigeLevel", prestigeLevel);
        tag.putInt("HighestEverFloor", getHighestEverFloor());
        tag.put("CompletedDeepBands", intSetToTag(completedDeepBands));
        tag.put("CompletedDeepTaskBands", intSetToTag(completedDeepTaskBands));
        tag.putInt("CurrentDeepBand", currentDeepBand);
        tag.putString("CurrentDeepTaskType", getCurrentDeepTaskType());
        tag.putInt("DeepTaskProgress", deepTaskProgress);
        tag.putInt("DeepTaskTarget", deepTaskTarget);
        tag.putInt("PrestigeProvision", provisionLevel);
        tag.putInt("PrestigePrepared", preparedLevel);
        tag.putInt("PrestigeSelection", selectionLevel);
        tag.putInt("PrestigeSupplyLine", supplyLineLevel);
        tag.putInt("PrestigeBlackMarket", blackMarketLevel);
        tag.putBoolean("FirstPrestigeCacheClaimed", firstPrestigeCacheClaimed);
    }

    void loadFrom(CompoundTag tag) {
        if (tag.contains("CurrentFloor")) currentFloor = tag.getInt("CurrentFloor");
        if (tag.contains("MaxReachedFloor")) maxReachedFloor = tag.getInt("MaxReachedFloor");
        if (tag.contains("RunActive")) runActive = tag.getBoolean("RunActive");
        if (tag.contains("AmmoCapacityLevel")) ammoCapacityLevel = tag.getInt("AmmoCapacityLevel");
        if (tag.contains("FloorCleared")) floorCleared = tag.getBoolean("FloorCleared");
        if (tag.contains("RunSeed")) runSeed = tag.getLong("RunSeed");
        if (tag.contains("FloorSeedSalt")) floorSeedSalt = tag.getLong("FloorSeedSalt");
        if (tag.contains("FloorAttemptIndex")) {
            floorAttemptIndex = Math.max(0, tag.getInt("FloorAttemptIndex"));
        } else if (currentFloor > 0 && floorSeedSalt != 0) {
            floorAttemptIndex = 1;
        }
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
        claimedPerkRewardFloors.clear();
        if (tag.contains("ClaimedPerkRewardFloors")) {
            readIntSet(tag.getList("ClaimedPerkRewardFloors", 3), claimedPerkRewardFloors);
        }
        if (tag.contains("DeepCore")) deepCore = Math.max(0, tag.getInt("DeepCore"));
        if (tag.contains("PrestigeLevel")) prestigeLevel = Math.max(0, tag.getInt("PrestigeLevel"));
        if (tag.contains("HighestEverFloor")) highestEverFloor = Math.max(0, tag.getInt("HighestEverFloor"));
        highestEverFloor = Math.max(highestEverFloor, Math.max(currentFloor, maxReachedFloor));
        completedDeepBands.clear();
        if (tag.contains("CompletedDeepBands")) readIntSet(tag.getList("CompletedDeepBands", 3), completedDeepBands);
        completedDeepTaskBands.clear();
        if (tag.contains("CompletedDeepTaskBands")) readIntSet(tag.getList("CompletedDeepTaskBands", 3), completedDeepTaskBands);
        if (tag.contains("CurrentDeepBand")) currentDeepBand = Math.max(0, tag.getInt("CurrentDeepBand"));
        if (tag.contains("CurrentDeepTaskType")) currentDeepTaskType = tag.getString("CurrentDeepTaskType");
        if (tag.contains("DeepTaskProgress")) deepTaskProgress = Math.max(0, tag.getInt("DeepTaskProgress"));
        if (tag.contains("DeepTaskTarget")) deepTaskTarget = Math.max(0, tag.getInt("DeepTaskTarget"));
        if (tag.contains("PrestigeProvision")) provisionLevel = Math.max(0, Math.min(5, tag.getInt("PrestigeProvision")));
        if (tag.contains("PrestigePrepared")) preparedLevel = Math.max(0, Math.min(5, tag.getInt("PrestigePrepared")));
        if (tag.contains("PrestigeSelection")) selectionLevel = Math.max(0, Math.min(5, tag.getInt("PrestigeSelection")));
        if (tag.contains("PrestigeSupplyLine")) supplyLineLevel = Math.max(0, Math.min(5, tag.getInt("PrestigeSupplyLine")));
        if (tag.contains("PrestigeBlackMarket")) blackMarketLevel = Math.max(0, Math.min(5, tag.getInt("PrestigeBlackMarket")));
        if (tag.contains("FirstPrestigeCacheClaimed")) firstPrestigeCacheClaimed = tag.getBoolean("FirstPrestigeCacheClaimed");
    }

    void reset() {
        currentFloor = 0;
        // maxReachedFloor はリセットしない(全ロス時以外)
        runActive = false;
        floorCleared = false;
        runSeed = 0;
        floorSeedSalt = 0;
        floorAttemptIndex = 0;
        themeName = "RUINS - OVERGROWN";
        theme = RunManager.Theme.MANSION;
        dungeonOrigin = new BlockPos(0, GameConstants.DUNGEON_BASE_Y, 0);
        floorStartTick = 0;
        // ボス初回報酬履歴は進行データなので通常リセットでは保持する
        claimedPerkRewardFloors.clear();
    }

    private static ListTag intSetToTag(Set<Integer> set) {
        ListTag list = new ListTag();
        for (Integer value : set) {
            if (value != null) list.add(IntTag.valueOf(value));
        }
        return list;
    }

    private static void readIntSet(ListTag list, Set<Integer> target) {
        for (int i = 0; i < list.size(); i++) {
            target.add(list.getInt(i));
        }
    }
}
