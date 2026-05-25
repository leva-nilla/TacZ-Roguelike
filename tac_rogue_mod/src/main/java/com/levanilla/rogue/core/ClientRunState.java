package com.levanilla.rogue.core;

public final class ClientRunState {

    private static int floor = 0;
    private static int maxReachedFloor = 0;
    private static boolean runActive = false;
    private static boolean floorCleared = false;
    private static int stashLines = 2;
    private static String themeName = "RUINS - OVERGROWN";
    private static int gold = 0;
    private static int ammoCapacityLevel = 0;
    private static int inventoryLevel = 0;
    private static int meleeLevel = 0;
    private static int randomPerkBuys = 0;
    private static int flashlightLevel = 0;
    private static int deepCore = 0;
    private static int prestigeLevel = 0;
    private static int highestEverFloor = 0;
    private static int currentDeepBand = 0;
    private static String currentDeepTaskType = "";
    private static int deepTaskProgress = 0;
    private static int deepTaskTarget = 0;
    private static int prestigeProvision = 0;
    private static int prestigePrepared = 0;
    private static int prestigeSelection = 0;
    private static int prestigeSupplyLine = 0;
    private static int prestigeBlackMarket = 0;
    private static float healthRatioOverride = Float.NaN;
    private static long healthRatioOverrideUntilMs = 0L;
    private static GenerationStatus generationStatus = null;
    private static long generationStatusUntilMs = 0L;
    private static BossBarState bossBarState = null;
    private static long bossBarStateUntilMs = 0L;
    private static ObjectiveState objectiveState = null;
    private static long objectiveUntilMs = 0L;
    private static EnemyDirectionState enemyDirectionState = null;
    private static long enemyDirectionUntilMs = 0L;
    private static long medicalBuffUntilMs = 0L;
    private static int stealthTakedownTargetId = -1;
    private static long stealthTakedownUntilMs = 0L;
    private static final java.util.Set<String> perkTags = new java.util.LinkedHashSet<>();

    private ClientRunState() {}

    public static void setStashLines(int lines) {
        stashLines = lines;
    }

    public static int getStashLines() {
        return stashLines;
    }

    public static void setGold(int value) {
        gold = value;
    }

    public static int getGold() {
        return gold;
    }

    public static int getAmmoCapacityLevel() {
        return ammoCapacityLevel;
    }

    public static int getFlashlightLevel() {
        return flashlightLevel;
    }

    public static int getInventoryLevel() {
        return inventoryLevel;
    }

    public static int getMeleeLevel() {
        return meleeLevel;
    }

    public static int getRandomPerkBuys() {
        return randomPerkBuys;
    }

    public static void setDeepState(int core, int prestige, int highestFloor, int band, String taskType,
                                    int taskProgress, int taskTarget, int provision, int prepared,
                                    int selection, int supplyLine, int blackMarket) {
        deepCore = Math.max(0, core);
        prestigeLevel = Math.max(0, prestige);
        highestEverFloor = Math.max(0, highestFloor);
        currentDeepBand = Math.max(0, band);
        currentDeepTaskType = taskType == null ? "" : taskType;
        deepTaskProgress = Math.max(0, taskProgress);
        deepTaskTarget = Math.max(0, taskTarget);
        prestigeProvision = Math.max(0, provision);
        prestigePrepared = Math.max(0, prepared);
        prestigeSelection = Math.max(0, selection);
        prestigeSupplyLine = Math.max(0, supplyLine);
        prestigeBlackMarket = Math.max(0, blackMarket);
    }

    public static int getDeepCore() { return deepCore; }
    public static int getPrestigeLevel() { return prestigeLevel; }
    public static int getHighestEverFloor() { return Math.max(highestEverFloor, maxReachedFloor); }
    public static int getCurrentDeepBand() { return currentDeepBand; }
    public static String getCurrentDeepTaskType() { return currentDeepTaskType; }
    public static int getDeepTaskProgress() { return deepTaskProgress; }
    public static int getDeepTaskTarget() { return deepTaskTarget; }
    public static int getPrestigeProvision() { return prestigeProvision; }
    public static int getPrestigePrepared() { return prestigePrepared; }
    public static int getPrestigeSelection() { return prestigeSelection; }
    public static int getPrestigeSupplyLine() { return prestigeSupplyLine; }
    public static int getPrestigeBlackMarket() { return prestigeBlackMarket; }

    public static void setPerkTags(java.util.Collection<String> tags) {
        perkTags.clear();
        if (tags == null) return;
        for (String tag : tags) {
            if (tag != null && tag.startsWith("perk:")) {
                perkTags.add(tag);
            }
        }
    }

    public static java.util.List<String> getPerkTags() {
        return java.util.List.copyOf(perkTags);
    }

    public static void setHealthOverride(float health, float maxHealth, long durationMs) {
        healthRatioOverride = maxHealth > 0.0f
            ? Math.max(0.0f, Math.min(1.0f, health / maxHealth))
            : Float.NaN;
        healthRatioOverrideUntilMs = System.currentTimeMillis() + Math.max(0L, durationMs);
    }

    public static float getHealthRatioOverride() {
        if (Float.isNaN(healthRatioOverride)) return Float.NaN;
        if (System.currentTimeMillis() > healthRatioOverrideUntilMs) {
            healthRatioOverride = Float.NaN;
            healthRatioOverrideUntilMs = 0L;
            return Float.NaN;
        }
        return healthRatioOverride;
    }

    public static void setGenerationStatus(int floor, int remaining, int total, float progress, long durationMs) {
        int safeTotal = Math.max(1, total);
        int safeRemaining = Math.max(0, Math.min(remaining, safeTotal));
        float safeProgress = Math.max(0.0f, Math.min(1.0f, progress));
        generationStatus = new GenerationStatus(floor, safeRemaining, safeTotal, safeProgress);
        generationStatusUntilMs = System.currentTimeMillis() + Math.max(0L, durationMs);
    }

    public static void clearGenerationStatus() {
        generationStatus = null;
        generationStatusUntilMs = 0L;
    }

    public static GenerationStatus getGenerationStatus() {
        if (generationStatus == null) return null;
        if (System.currentTimeMillis() > generationStatusUntilMs) {
            clearGenerationStatus();
            return null;
        }
        return generationStatus;
    }

    public static void setBossBarState(int floor, float health, float maxHealth, String name, long durationMs) {
        if (maxHealth <= 0.0F || health <= 0.0F) {
            clearBossBarState();
            return;
        }
        float safeHealth = Math.max(0.0F, Math.min(health, maxHealth));
        bossBarState = new BossBarState(floor, safeHealth, maxHealth, name == null || name.isBlank() ? "[BOSS]" : name);
        bossBarStateUntilMs = System.currentTimeMillis() + Math.max(0L, durationMs);
    }

    public static void clearBossBarState() {
        bossBarState = null;
        bossBarStateUntilMs = 0L;
    }

    public static BossBarState getBossBarState() {
        if (bossBarState == null) return null;
        if (System.currentTimeMillis() > bossBarStateUntilMs) {
            clearBossBarState();
            return null;
        }
        return bossBarState;
    }

    public static void setObjectiveState(String type, String title, String statusKey, int progress, int target,
                                         boolean complete, int targetX, int targetY, int targetZ,
                                         double dx, double dz, long durationMs) {
        objectiveState = new ObjectiveState(
            type == null ? "" : type,
            title == null || title.isBlank() ? "Objective" : title,
            statusKey == null ? "" : statusKey,
            Math.max(0, progress),
            Math.max(1, target),
            complete,
            targetX,
            targetY,
            targetZ,
            dx,
            dz);
        objectiveUntilMs = System.currentTimeMillis() + Math.max(0L, durationMs);
    }

    public static void clearObjectiveState() {
        objectiveState = null;
        objectiveUntilMs = 0L;
    }

    public static ObjectiveState getObjectiveState() {
        if (objectiveState == null) return null;
        if (System.currentTimeMillis() > objectiveUntilMs) {
            clearObjectiveState();
            return null;
        }
        return objectiveState;
    }

    public static void setEnemyDirection(double dx, double dz, int count, long durationMs) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || count <= 0 || dx * dx + dz * dz < 0.001D) {
            clearEnemyDirection();
            return;
        }
        enemyDirectionState = new EnemyDirectionState(dx, dz, count);
        enemyDirectionUntilMs = System.currentTimeMillis() + Math.max(0L, durationMs);
    }

    public static void clearEnemyDirection() {
        enemyDirectionState = null;
        enemyDirectionUntilMs = 0L;
    }

    public static EnemyDirectionState getEnemyDirectionState() {
        if (enemyDirectionState == null) return null;
        if (System.currentTimeMillis() > enemyDirectionUntilMs) {
            clearEnemyDirection();
            return null;
        }
        return enemyDirectionState;
    }

    public static void setMedicalBuffRemainingTicks(int ticks) {
        if (ticks <= 0) {
            medicalBuffUntilMs = 0L;
        } else {
            medicalBuffUntilMs = System.currentTimeMillis() + ticks * 50L;
        }
    }

    public static int getMedicalBuffRemainingSeconds() {
        if (medicalBuffUntilMs <= 0L) return 0;
        long remaining = medicalBuffUntilMs - System.currentTimeMillis();
        if (remaining <= 0L) {
            medicalBuffUntilMs = 0L;
            return 0;
        }
        return (int)Math.ceil(remaining / 1000.0D);
    }

    public static void setStealthTakedownTarget(int entityId, long durationMs) {
        if (entityId < 0 || durationMs <= 0L) {
            clearStealthTakedownTarget();
            return;
        }
        stealthTakedownTargetId = entityId;
        stealthTakedownUntilMs = System.currentTimeMillis() + durationMs;
    }

    public static void clearStealthTakedownTarget() {
        stealthTakedownTargetId = -1;
        stealthTakedownUntilMs = 0L;
    }

    public static int getStealthTakedownTargetId() {
        if (stealthTakedownTargetId < 0) return -1;
        if (System.currentTimeMillis() > stealthTakedownUntilMs) {
            clearStealthTakedownTarget();
            return -1;
        }
        return stealthTakedownTargetId;
    }

    public static int getFloor() {
        return floor;
    }

    public static int getMaxReachedFloor() {
        return maxReachedFloor;
    }

    public static boolean isRunActive() {
        return runActive;
    }

    public static boolean isFloorCleared() {
        return floorCleared;
    }

    public static String getThemeName() {
        return themeName;
    }

    public static void setRunData(int newFloor, String newThemeName, boolean active, int maxFloor) {
        floor = newFloor;
        themeName = newThemeName;
        runActive = active;
        maxReachedFloor = maxFloor;
        if (!active) {
            clearBossBarState();
            clearObjectiveState();
        }
    }

    public static void setRunData(int newFloor, String newThemeName, boolean active, boolean cleared, int maxFloor, int ammoCapacity) {
        setRunData(newFloor, newThemeName, active, maxFloor);
        floorCleared = cleared;
        ammoCapacityLevel = ammoCapacity;
    }

    public static void setFloorCleared(boolean value) {
        floorCleared = value;
    }

    public static void setAmmoCapacityLevel(int value) {
        ammoCapacityLevel = value;
    }

    public static void setFlashlightLevel(int value) {
        flashlightLevel = value;
    }

    public static void setMetaData(int invLevel, int meleeUpgradeLevel, int perkBuys, int lightLevel) {
        inventoryLevel = invLevel;
        meleeLevel = meleeUpgradeLevel;
        randomPerkBuys = perkBuys;
        flashlightLevel = lightLevel;
    }

    public record GenerationStatus(int floor, int remaining, int total, float progress) {}

    public record BossBarState(int floor, float health, float maxHealth, String name) {
        public float ratio() {
            return maxHealth > 0.0F ? Math.max(0.0F, Math.min(1.0F, health / maxHealth)) : 0.0F;
        }
    }

    public record ObjectiveState(String type, String title, String statusKey, int progress, int target,
                                 boolean complete, int targetX, int targetY, int targetZ,
                                 double dx, double dz) {
        public static final int NO_TARGET = Integer.MIN_VALUE;

        public float ratio() {
            return Math.max(0.0F, Math.min(1.0F, progress / (float)Math.max(1, target)));
        }

        public boolean hasDirection() {
            return Double.isFinite(dx) && Double.isFinite(dz) && dx * dx + dz * dz > 0.001D;
        }

        public boolean hasTarget() {
            return targetX != NO_TARGET && targetY != NO_TARGET && targetZ != NO_TARGET;
        }
    }

    public record EnemyDirectionState(double dx, double dz, int count) {}
}
