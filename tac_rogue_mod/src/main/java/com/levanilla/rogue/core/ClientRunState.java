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
    private static int flashlightLevel = 0;

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

    public static void setFloorCleared(boolean value) {
        floorCleared = value;
    }

    public static void setAmmoCapacityLevel(int value) {
        ammoCapacityLevel = value;
    }

    public static void setFlashlightLevel(int value) {
        flashlightLevel = value;
    }
}
