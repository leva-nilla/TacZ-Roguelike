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
    private static float healthRatioOverride = Float.NaN;
    private static long healthRatioOverrideUntilMs = 0L;

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
}
