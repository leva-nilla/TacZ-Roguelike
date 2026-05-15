package com.levanilla.rogue.core;

import com.levanilla.rogue.core.registry.ShopCatalog;

import java.util.List;

/**
 * Deterministic act-aware shop stock filtering.
 *
 * The client and server both use this logic so displayed stock and purchase
 * validation stay aligned without adding a new shop-stock sync packet.
 */
public final class ShopStockManager {

    private ShopStockManager() {
    }

    public static List<ShopCatalog.ShopItem> filterAvailable(List<ShopCatalog.ShopItem> items, int floor) {
        return items.stream()
            .filter(item -> isAvailable(item, floor))
            .toList();
    }

    public static boolean isAvailable(ShopCatalog.ShopItem item, int floor) {
        if (item == null) {
            return false;
        }
        if (isAlwaysAvailable(item)) {
            return true;
        }
        if (!isUnlockedByFloor(item.category, floor)) {
            return false;
        }

        boolean preferred = isPreferredForPhase(item.category, RogueActManager.getPhase(floor));
        int threshold = preferred ? 85 : 35;
        int score = Math.floorMod(item.id.hashCode() ^ (floor * 1103515245), 100);
        return score < threshold;
    }

    public static int getShopFloor(int currentFloor, boolean floorCleared) {
        if (currentFloor <= 0) {
            return 1;
        }
        return floorCleared ? currentFloor + 1 : currentFloor;
    }

    private static boolean isAlwaysAvailable(ShopCatalog.ShopItem item) {
        return item.category == ShopCatalog.Category.SPECIAL
            || item.category == ShopCatalog.Category.AMMO;
    }

    public static boolean isUnlockedByFloor(ShopCatalog.Category category, int floor) {
        return switch (category) {
            case PISTOL, SMG, MELEE, TACTICAL, ATTACHMENT, AMMO, SPECIAL -> true;
            case SHOTGUN -> floor >= 4;
            case RIFLE -> floor >= 8;
            case SNIPER -> floor >= 10;
            case LMG -> floor >= 15;
            case EXPLOSIVE -> floor >= 20;
        };
    }

    private static boolean isPreferredForPhase(ShopCatalog.Category category, RogueActManager.FloorPhase phase) {
        return switch (phase) {
            case SCOUT -> category == ShopCatalog.Category.PISTOL
                || category == ShopCatalog.Category.SMG
                || category == ShopCatalog.Category.SNIPER
                || category == ShopCatalog.Category.MELEE
                || category == ShopCatalog.Category.TACTICAL;
            case SUPPLY -> category == ShopCatalog.Category.ATTACHMENT
                || category == ShopCatalog.Category.AMMO
                || category == ShopCatalog.Category.SPECIAL
                || category == ShopCatalog.Category.TACTICAL;
            case ELITE -> category == ShopCatalog.Category.RIFLE
                || category == ShopCatalog.Category.SHOTGUN
                || category == ShopCatalog.Category.LMG
                || category == ShopCatalog.Category.EXPLOSIVE;
            case DANGER -> category == ShopCatalog.Category.ATTACHMENT
                || category == ShopCatalog.Category.SPECIAL
                || category == ShopCatalog.Category.SHOTGUN
                || category == ShopCatalog.Category.MELEE;
            case BOSS -> category == ShopCatalog.Category.RIFLE
                || category == ShopCatalog.Category.SNIPER
                || category == ShopCatalog.Category.LMG
                || category == ShopCatalog.Category.EXPLOSIVE
                || category == ShopCatalog.Category.ATTACHMENT;
        };
    }
}
