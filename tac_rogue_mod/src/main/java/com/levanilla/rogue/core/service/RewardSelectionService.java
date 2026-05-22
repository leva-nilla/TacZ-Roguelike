package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.ShopStockManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.registry.ShopCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared reward filtering so quest, boss, and chest weapons follow the same floor progression. */
public final class RewardSelectionService {
    private static final int MIN_REWARD_POOL_SIZE = 4;
    private static final Set<String> BANNED_REWARD_WEAPON_IDS = Set.of(
        "daffas_arsenal:samula3",
        "daffas_arsenal:southeastmomssecretweapon",
        "daffas_arsenal:southeastmomssecretweapon2",
        "daffas_arsenal:southeastmomssecretweapon3"
    );

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

        if (preferred.size() >= MIN_REWARD_POOL_SIZE) return preferred;
        List<ShopCatalog.ShopItem> expanded = expandSmallPreferredPool(
            safeFloor, maxPrice, preferredCategories, preferred, available);
        if (!expanded.isEmpty()) return expanded;
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

    private static List<ShopCatalog.ShopItem> expandSmallPreferredPool(
            int floor,
            int maxPrice,
            List<ShopCatalog.Category> preferredCategories,
            List<ShopCatalog.ShopItem> preferred,
            List<ShopCatalog.ShopItem> available) {
        Map<String, ShopCatalog.ShopItem> merged = new LinkedHashMap<>();
        addAllUnique(merged, preferred);
        if (merged.size() >= MIN_REWARD_POOL_SIZE || maxPrice <= 0) {
            return new ArrayList<>(merged.values());
        }

        int relaxedCap = maxPrice + Math.max(900, floor * 120);
        for (ShopCatalog.ShopItem item : TacZRegistryHelper.getAllShopItems()) {
            if (!isRewardWeapon(item)) continue;
            if (!ShopStockManager.isUnlockedByFloor(item.category, floor)) continue;
            if (preferredCategories != null && !preferredCategories.contains(item.category)) continue;
            if (item.price <= relaxedCap) {
                merged.putIfAbsent(item.id, item);
            }
        }
        if (merged.size() >= MIN_REWARD_POOL_SIZE) {
            return new ArrayList<>(merged.values());
        }

        addAllUnique(merged, available);
        if (merged.size() >= MIN_REWARD_POOL_SIZE) {
            return new ArrayList<>(merged.values());
        }

        int broadCap = maxPrice + Math.max(1400, floor * 180);
        for (ShopCatalog.ShopItem item : TacZRegistryHelper.getAllShopItems()) {
            if (!isRewardWeapon(item)) continue;
            if (!ShopStockManager.isUnlockedByFloor(item.category, floor)) continue;
            if (item.price <= broadCap) {
                merged.putIfAbsent(item.id, item);
            }
        }
        return merged.size() > preferred.size() ? new ArrayList<>(merged.values()) : List.of();
    }

    private static void addAllUnique(Map<String, ShopCatalog.ShopItem> target, List<ShopCatalog.ShopItem> items) {
        for (ShopCatalog.ShopItem item : items) {
            if (item != null) {
                target.putIfAbsent(item.id, item);
            }
        }
    }

    public static List<ShopCatalog.ShopItem> chestWeaponCandidates(int floor) {
        int safeFloor = Math.max(1, floor);
        int maxPrice = 900 + safeFloor * 150 + Math.max(0, safeFloor - 50) * 70;
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
            && item.category != ShopCatalog.Category.TACTICAL
            && !BANNED_REWARD_WEAPON_IDS.contains(item.id);
    }
}
