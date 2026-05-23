package com.levanilla.rogue.core;

import net.minecraft.core.BlockPos;

/**
 * プレイヤー個別のローグライクラン進行データ。
 * RunManager が UUID → PlayerRunData の Map で管理する。
 *
 * <p>外部 API と NBT 形式を維持するための facade。実データは責務別に分離する。</p>
 */
public class PlayerRunData {

    private final DeepProgressData progressData = new DeepProgressData();
    private final SupplyChestTracker supplyChestTracker = new SupplyChestTracker();

    // ===== アクセサ =====

    public int getCurrentFloor() { return progressData.getCurrentFloor(); }
    public void setCurrentFloor(int f) { progressData.setCurrentFloor(f); }

    public int getMaxReachedFloor() { return progressData.getMaxReachedFloor(); }
    public void setMaxReachedFloor(int f) { progressData.setMaxReachedFloor(f); }

    public boolean isRunActive() { return progressData.isRunActive(); }
    public void setRunActive(boolean v) { progressData.setRunActive(v); }

    public int getAmmoCapacityLevel() { return progressData.getAmmoCapacityLevel(); }
    public void setAmmoCapacityLevel(int ammoCapacityLevel) { progressData.setAmmoCapacityLevel(ammoCapacityLevel); }

    public boolean isFloorCleared() { return progressData.isFloorCleared(); }
    public void setFloorCleared(boolean v) { progressData.setFloorCleared(v); }

    public long getRunSeed() { return progressData.getRunSeed(); }
    public void setRunSeed(long s) { progressData.setRunSeed(s); }
    public long getFloorSeedSalt() { return progressData.getFloorSeedSalt(); }
    public void setFloorSeedSalt(long salt) { progressData.setFloorSeedSalt(salt); }
    public void rerollFloorSeedSalt(long entropy) { progressData.rerollFloorSeedSalt(entropy); }
    public int getFloorAttemptIndex() { return progressData.getFloorAttemptIndex(); }
    public void setFloorAttemptIndex(int value) { progressData.setFloorAttemptIndex(value); }
    public void startFloorAttempt(long entropy) { progressData.startFloorAttempt(entropy); }
    public void nextFloorAttempt(long entropy) { progressData.nextFloorAttempt(entropy); }

    public String getThemeName() { return progressData.getThemeName(); }
    public void setThemeName(String n) { progressData.setThemeName(n); }

    public RunManager.Theme getTheme() { return progressData.getTheme(); }
    public void setTheme(RunManager.Theme t) { progressData.setTheme(t); }

    public BlockPos getDungeonOrigin() { return progressData.getDungeonOrigin(); }
    public void setDungeonOrigin(BlockPos pos) { progressData.setDungeonOrigin(pos); }

    public long getFloorStartTick() { return progressData.getFloorStartTick(); }
    public void setFloorStartTick(long tick) { progressData.setFloorStartTick(tick); }

    public boolean hasClaimedBossReward(int floor) {
        return progressData.hasClaimedBossReward(floor);
    }

    public void markBossRewardClaimed(int floor) {
        progressData.markBossRewardClaimed(floor);
    }

    public boolean hasClaimedSupplyChest(String key) {
        return supplyChestTracker.hasClaimed(key);
    }

    public void markSupplyChestClaimed(String key) {
        supplyChestTracker.markClaimed(key);
    }

    public int updateSupplyChestMaxClaims(int floor, int generatedCount) {
        return supplyChestTracker.updateMaxClaims(floor, generatedCount);
    }

    public int getSupplyChestMaxClaims(int floor) {
        return supplyChestTracker.getMaxClaims(floor);
    }

    public void setSupplyChestMaxClaims(int floor, int generatedCount) {
        supplyChestTracker.setMaxClaims(floor, generatedCount);
    }

    public int getClaimedSupplyChestCount(int floor) {
        return supplyChestTracker.getClaimedCount(floor);
    }

    public int claimNextSupplyChest(int floor, int maxClaims) {
        return supplyChestTracker.claimNext(floor, maxClaims);
    }

    public void clearRunRewardClaims() {
        progressData.clearRunRewardClaims();
        supplyChestTracker.clear();
    }

    public int getDeepCore() { return progressData.getDeepCore(); }
    public void setDeepCore(int value) { progressData.setDeepCore(value); }
    public void addDeepCore(int amount) { progressData.addDeepCore(amount); }
    public boolean consumeDeepCore(int amount) { return progressData.consumeDeepCore(amount); }

    public int getPrestigeLevel() { return progressData.getPrestigeLevel(); }
    public void setPrestigeLevel(int value) { progressData.setPrestigeLevel(value); }
    public void addPrestigeLevel(int amount) { progressData.addPrestigeLevel(amount); }

    public int getHighestEverFloor() { return progressData.getHighestEverFloor(); }
    public void updateHighestEverFloor(int floor) { progressData.updateHighestEverFloor(floor); }

    public boolean hasCompletedDeepBand(int band) { return progressData.hasCompletedDeepBand(band); }
    public void markDeepBandCompleted(int band) { progressData.markDeepBandCompleted(band); }
    public boolean hasCompletedDeepTaskBand(int band) { return progressData.hasCompletedDeepTaskBand(band); }
    public void markDeepTaskBandCompleted(int band) { progressData.markDeepTaskBandCompleted(band); }

    public int getCurrentDeepBand() { return progressData.getCurrentDeepBand(); }
    public void setCurrentDeepBand(int value) { progressData.setCurrentDeepBand(value); }
    public String getCurrentDeepTaskType() { return progressData.getCurrentDeepTaskType(); }
    public void setCurrentDeepTaskType(String value) { progressData.setCurrentDeepTaskType(value); }
    public int getDeepTaskProgress() { return progressData.getDeepTaskProgress(); }
    public void setDeepTaskProgress(int value) { progressData.setDeepTaskProgress(value); }
    public int getDeepTaskTarget() { return progressData.getDeepTaskTarget(); }
    public void setDeepTaskTarget(int value) { progressData.setDeepTaskTarget(value); }
    public void clearCurrentDeepTask() { progressData.clearCurrentDeepTask(); }

    public int getProvisionLevel() { return progressData.getProvisionLevel(); }
    public int getPreparedLevel() { return progressData.getPreparedLevel(); }
    public int getSelectionLevel() { return progressData.getSelectionLevel(); }
    public int getSupplyLineLevel() { return progressData.getSupplyLineLevel(); }
    public int getBlackMarketLevel() { return progressData.getBlackMarketLevel(); }
    public boolean isFirstPrestigeCacheClaimed() { return progressData.isFirstPrestigeCacheClaimed(); }
    public void setFirstPrestigeCacheClaimed(boolean value) { progressData.setFirstPrestigeCacheClaimed(value); }
    public int getPrestigeUnlockLevel(String key) { return progressData.getPrestigeUnlockLevel(key); }
    public void setPrestigeUnlockLevel(String key, int level) { progressData.setPrestigeUnlockLevel(key, level); }

    // ===== ライフサイクル =====

    /** 新規ランを開始する */
    public void startRun() {
        progressData.startRun();
        supplyChestTracker.clear();
    }

    /** 次の階層へ進む */
    public void advanceFloor() {
        progressData.advanceFloor();
    }

    // ===== NBT セーブ・ロード =====

    public net.minecraft.nbt.CompoundTag saveToNbt() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        progressData.saveTo(tag);
        supplyChestTracker.saveTo(tag);
        return tag;
    }

    public void loadFromNbt(net.minecraft.nbt.CompoundTag tag) {
        progressData.loadFrom(tag);
        supplyChestTracker.loadFrom(tag);
    }

    /** ラン状態をリセット */
    public void reset() {
        progressData.reset();
        supplyChestTracker.clear();
    }
}
