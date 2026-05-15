package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.ShopStockManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.registry.ShopCatalog;

import java.util.ArrayList;
import java.util.List;

/** Shared reward filtering so quest, boss, and chest weapons follow the same floor progression. */
public final class RewardSelectionService {
    private RewardSelectionService() {}

    public static List<ShopCatalog.ShopItem> weaponCandidates(
            int floor,
            int maxPrice,
            List<ShopCatalog.Category> preferredCategories) {
        int safeFloor = Math.max(1, floor);
        List<ShopCatalog.ShopItem> preferred = new ArrayList<>();
        List<ShopCatalog.ShopItem> available = new ArrayList<>();
        List<ShopCatalog.ShopItem> fallback = new ArrayList<>();

        for (ShopCatalog.ShopItem item : TacZRegistryHelper.getAllShopItems()) {
            if (!isRewardWeapon(item)) continue;
            if (!ShopStockManager.isUnlockedByFloor(item.category, safeFloor)) continue;

            boolean underCap = maxPrice <= 0 || item.price <= maxPrice;
            if (underCap) {
                available.add(item);
                if (preferredCategories != null && preferredCategories.contains(item.category)) {
                    preferred.add(item);
                }
            }

            if (item.category == ShopCatalog.Category.PISTOL || item.category == ShopCatalog.Category.SMG) {
                fallback.add(item);
            }
        }

        if (!preferred.isEmpty()) return preferred;
        if (!available.isEmpty()) return available;

        int relaxedCap = maxPrice <= 0 ? 0 : maxPrice + 500;
        if (relaxedCap > 0) {
            for (ShopCatalog.ShopItem item : TacZRegistryHelper.getAllShopItems()) {
                if (!isRewardWeapon(item)) continue;
                if (!ShopStockManager.isUnlockedByFloor(item.category, safeFloor)) continue;
                if (item.price <= relaxedCap) fallback.add(item);
            }
        }

        return fallback;
    }

    public static List<ShopCatalog.ShopItem> chestWeaponCandidates(int floor) {
        int maxPrice = 700 + Math.max(1, floor) * 120;
        return weaponCandidates(floor, maxPrice, List.of(
            ShopCatalog.Category.PISTOL,
            ShopCatalog.Category.SMG,
            ShopCatalog.Category.SHOTGUN,
            ShopCatalog.Category.RIFLE
        ));
    }

    private static boolean isRewardWeapon(ShopCatalog.ShopItem item) {
        return item != null
            && item.category.isWeapon()
            && item.category != ShopCatalog.Category.MELEE
            && item.category != ShopCatalog.Category.TACTICAL;
    }
}
