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
        return filterAvailable(items, floor, 0);
    }

    public static List<ShopCatalog.ShopItem> filterAvailable(List<ShopCatalog.ShopItem> items, int floor, int blackMarketLevel) {
        return items.stream()
            .filter(item -> isAvailable(item, floor, blackMarketLevel))
            .toList();
    }

    public static boolean isAvailable(ShopCatalog.ShopItem item, int floor) {
        return isAvailable(item, floor, 0);
    }

    public static boolean isAvailable(ShopCatalog.ShopItem item, int floor, int blackMarketLevel) {
        if (item == null) {
            return false;
        }
        if (com.levanilla.rogue.core.service.RogueUtilityItemService.isConsumableUtilityId(item.id)
                && floor < com.levanilla.rogue.core.service.RogueUtilityItemService.getConsumableShopUnlockFloor(item.id)) {
            return false;
        }
        if (isAlwaysAvailable(item)) {
            return true;
        }
        if (!isUnlockedByFloor(item.category, floor)) {
            return false;
        }

        boolean preferred = isPreferredForPhase(item.category, RogueActManager.getPhase(floor));
        int threshold = availabilityThreshold(item, floor, preferred, Math.max(0, blackMarketLevel));
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
        return true;
    }

    private static int availabilityThreshold(ShopCatalog.ShopItem item, int floor, boolean preferred, int blackMarketLevel) {
        int threshold = (preferred ? 85 : 35) + blackMarketLevel * 4;
        if (!isGunCategory(item.category)) {
            return Math.min(96, threshold + blackMarketLevel * 2);
        }

        int safeFloor = Math.max(1, floor);
        int stableCap = stableWeaponPriceCap(safeFloor, blackMarketLevel);
        int luxuryCap = luxuryWeaponPriceCap(safeFloor, blackMarketLevel);
        if (item.price <= stableCap) {
            return Math.min(97, threshold + 15 + blackMarketLevel * 2);
        }
        if (item.price >= luxuryCap) {
            return Math.min(45, (preferred ? 18 : 8) + blackMarketLevel * 5);
        }
        return Math.min(92, threshold);
    }

    private static int stableWeaponPriceCap(int floor, int blackMarketLevel) {
        return 1200 + Math.max(1, floor) * 130 + Math.max(0, blackMarketLevel) * 650;
    }

    private static int luxuryWeaponPriceCap(int floor, int blackMarketLevel) {
        return 4500 + Math.max(1, floor) * 260 + Math.max(0, blackMarketLevel) * 1400;
    }

    private static boolean isGunCategory(ShopCatalog.Category category) {
        return category == ShopCatalog.Category.PISTOL
            || category == ShopCatalog.Category.RIFLE
            || category == ShopCatalog.Category.SMG
            || category == ShopCatalog.Category.SHOTGUN
            || category == ShopCatalog.Category.SNIPER
            || category == ShopCatalog.Category.LMG
            || category == ShopCatalog.Category.EXPLOSIVE;
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
