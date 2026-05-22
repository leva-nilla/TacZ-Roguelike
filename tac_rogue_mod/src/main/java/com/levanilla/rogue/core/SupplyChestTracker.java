package com.levanilla.rogue.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 同一ラン内で取得済みの補給チェスト履歴。
 * retry floorでのチェスト厳選を防ぐ。
 */
final class SupplyChestTracker {

    private final Set<String> claimedSupplyChestKeys = new HashSet<>();
    private final Map<Integer, Integer> supplyChestMaxClaimsByFloor = new HashMap<>();

    boolean hasClaimed(String key) {
        return key != null && claimedSupplyChestKeys.contains(key);
    }

    void markClaimed(String key) {
        if (key != null && !key.isBlank()) claimedSupplyChestKeys.add(key);
    }

    int updateMaxClaims(int floor, int generatedCount) {
        int safeFloor = Math.max(1, floor);
        int safeCount = Math.max(1, generatedCount);
        int merged = Math.max(supplyChestMaxClaimsByFloor.getOrDefault(safeFloor, 0), safeCount);
        supplyChestMaxClaimsByFloor.put(safeFloor, merged);
        return merged;
    }

    int getMaxClaims(int floor) {
        return supplyChestMaxClaimsByFloor.getOrDefault(Math.max(1, floor), 0);
    }

    void setMaxClaims(int floor, int generatedCount) {
        int safeFloor = Math.max(1, floor);
        int safeCount = Math.max(0, generatedCount);
        if (safeCount <= 0) {
            supplyChestMaxClaimsByFloor.remove(safeFloor);
        } else {
            supplyChestMaxClaimsByFloor.put(safeFloor, safeCount);
        }
    }

    int getClaimedCount(int floor) {
        String prefix = Math.max(1, floor) + ":";
        int count = 0;
        for (String key : claimedSupplyChestKeys) {
            if (key != null && key.startsWith(prefix)) count++;
        }
        return count;
    }

    int claimNext(int floor, int maxClaims) {
        int safeFloor = Math.max(1, floor);
        int safeMax = Math.max(1, maxClaims);
        for (int i = 0; i < safeMax; i++) {
            String key = safeFloor + ":" + i;
            if (!claimedSupplyChestKeys.contains(key)) {
                claimedSupplyChestKeys.add(key);
                return i;
            }
        }
        return -1;
    }

    void clear() {
        claimedSupplyChestKeys.clear();
        supplyChestMaxClaimsByFloor.clear();
    }

    void saveTo(CompoundTag tag) {
        ListTag supplyChests = new ListTag();
        for (String key : claimedSupplyChestKeys) {
            supplyChests.add(StringTag.valueOf(key));
        }
        tag.put("ClaimedSupplyChestKeys", supplyChests);
        CompoundTag supplyChestMaxClaims = new CompoundTag();
        for (Map.Entry<Integer, Integer> entry : supplyChestMaxClaimsByFloor.entrySet()) {
            supplyChestMaxClaims.putInt(Integer.toString(entry.getKey()), entry.getValue());
        }
        tag.put("SupplyChestMaxClaimsByFloor", supplyChestMaxClaims);
    }

    void loadFrom(CompoundTag tag) {
        claimedSupplyChestKeys.clear();
        if (tag.contains("ClaimedSupplyChestKeys")) {
            ListTag supplyChests = tag.getList("ClaimedSupplyChestKeys", 8);
            for (int i = 0; i < supplyChests.size(); i++) {
                claimedSupplyChestKeys.add(supplyChests.getString(i));
            }
        }
        supplyChestMaxClaimsByFloor.clear();
        if (tag.contains("SupplyChestMaxClaimsByFloor")) {
            CompoundTag supplyChestMaxClaims = tag.getCompound("SupplyChestMaxClaimsByFloor");
            for (String key : supplyChestMaxClaims.getAllKeys()) {
                try {
                    supplyChestMaxClaimsByFloor.put(Integer.parseInt(key), Math.max(1, supplyChestMaxClaims.getInt(key)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }
}
