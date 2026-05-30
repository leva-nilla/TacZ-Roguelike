package com.levanilla.rogue.core.service;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class RogueStackingService {
    private RogueStackingService() {}

    public static boolean canMerge(ItemStack existing, ItemStack incoming) {
        if (existing == null || incoming == null || existing.isEmpty() || incoming.isEmpty()) return false;
        if (ItemStack.isSameItemSameTags(existing, incoming)) return true;
        if (!ItemStack.isSameItem(existing, incoming)) return false;

        CompoundTag left = existing.getTag();
        CompoundTag right = incoming.getTag();
        if (left == null || right == null) return false;

        if (sameString(left, right, "AmmoId")) return true;
        if (sameString(left, right, RogueUtilityItemService.UTILITY_ID_KEY)) return true;

        boolean leftConsumable = left.contains(RogueItemFactory.CONSUMABLE_MAX_STACK_KEY);
        boolean rightConsumable = right.contains(RogueItemFactory.CONSUMABLE_MAX_STACK_KEY);
        if (leftConsumable && rightConsumable) {
            return sameInt(left, right, "CustomModelData")
                && sameInt(left, right, RogueItemFactory.CONSUMABLE_MAX_STACK_KEY);
        }

        return false;
    }

    private static boolean sameString(CompoundTag left, CompoundTag right, String key) {
        return left.contains(key) && right.contains(key) && left.getString(key).equals(right.getString(key));
    }

    private static boolean sameInt(CompoundTag left, CompoundTag right, String key) {
        return left.contains(key) && right.contains(key) && left.getInt(key) == right.getInt(key);
    }
}
