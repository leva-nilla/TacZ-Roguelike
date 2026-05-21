package com.levanilla.rogue.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.HashSet;
import java.util.Set;

/**
 * 同一ラン内で取得済みの補給チェスト履歴。
 * retry floorでのチェスト厳選を防ぐ。
 */
final class SupplyChestTracker {

    private final Set<String> claimedSupplyChestKeys = new HashSet<>();

    boolean hasClaimed(String key) {
        return key != null && claimedSupplyChestKeys.contains(key);
    }

    void markClaimed(String key) {
        if (key != null && !key.isBlank()) claimedSupplyChestKeys.add(key);
    }

    void clear() {
        claimedSupplyChestKeys.clear();
    }

    void saveTo(CompoundTag tag) {
        ListTag supplyChests = new ListTag();
        for (String key : claimedSupplyChestKeys) {
            supplyChests.add(StringTag.valueOf(key));
        }
        tag.put("ClaimedSupplyChestKeys", supplyChests);
    }

    void loadFrom(CompoundTag tag) {
        claimedSupplyChestKeys.clear();
        if (tag.contains("ClaimedSupplyChestKeys")) {
            ListTag supplyChests = tag.getList("ClaimedSupplyChestKeys", 8);
            for (int i = 0; i < supplyChests.size(); i++) {
                claimedSupplyChestKeys.add(supplyChests.getString(i));
            }
        }
    }
}
