package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CurrencyManager;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.StashSavedData;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.registry.LrTacticalRegistry;
import com.levanilla.rogue.networking.PopupNotificationMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ShopPlacementService {

    private ShopPlacementService() {}

    public static void placePurchasedItem(ServerPlayer player, ItemStack stack, String itemId, int price) {
        boolean isGun = stack.hasTag() && stack.getTag().contains("GunId");
        boolean isMelee = (stack.hasTag() && stack.getTag().contains("MeleeWeaponId"))
            || LrTacticalRegistry.isMeleeWeapon(stack);
        boolean isAmmo = stack.hasTag() && stack.getTag().contains("AmmoId");

        if (isGun) {
            placeGun(player, stack, itemId, price);
        } else if (isMelee) {
            placeMelee(player, stack, itemId, price);
        } else if (isAmmo) {
            placeAmmo(player, stack, itemId, price);
        } else {
            placeGeneralItem(player, stack, itemId, price);
        }
    }

    public static boolean sendToStash(ServerPlayer player, ItemStack stack) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
        int maxSlots = stash.unlockedLines * 9;
        ItemStack remaining = stack.copy();

        for (int i = 0; i < maxSlots; i++) {
            ItemStack existing = stash.getItem(i);
            if (existing.isEmpty() || !RogueStackingService.canMerge(existing, remaining)) continue;
            int space = existing.getMaxStackSize() - existing.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, remaining.getCount());
            existing.grow(move);
            remaining.shrink(move);
            stash.setItem(i, existing);
            if (remaining.isEmpty()) {
                data.setDirty();
                return true;
            }
        }

        for (int i = 0; i < maxSlots; i++) {
            if (stash.getItem(i).isEmpty()) {
                stash.setItem(i, remaining.copy());
                data.setDirty();
                return true;
            }
        }
        return false;
    }

    public static void placeRewardItem(ServerPlayer player, ItemStack stack, String itemId) {
        boolean isGun = stack.hasTag() && stack.getTag().contains("GunId");
        boolean isMelee = (stack.hasTag() && stack.getTag().contains("MeleeWeaponId"))
            || LrTacticalRegistry.isMeleeWeapon(stack);

        if (isGun) {
            boolean slot0Full = !player.getInventory().items.get(GameConstants.SLOT_GUN_START).isEmpty();
            boolean slot1Full = !player.getInventory().items.get(GameConstants.SLOT_GUN_END).isEmpty();
            if (!slot0Full || !slot1Full) {
                player.getInventory().setItem(slot0Full ? GameConstants.SLOT_GUN_END : GameConstants.SLOT_GUN_START, stack);
                return;
            }
        } else if (isMelee && player.getInventory().items.get(GameConstants.SLOT_MELEE).isEmpty()) {
            player.getInventory().setItem(GameConstants.SLOT_MELEE, stack);
            return;
        } else if (isMelee && placeInCombatItemSlots(player, stack)) {
            return;
        } else if (!isMelee && placeInUnlockedItemSlots(player, stack)) {
            return;
        }

        if (sendToStash(player, stack)) {
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.REWARD,
                Component.translatable("popup.tac_rogue.quest_weapon.title"),
                Component.translatable("popup.tac_rogue.reward_stash.body", itemId.toUpperCase())
            );
        } else {
            player.drop(stack, false);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.REWARD,
                Component.translatable("popup.tac_rogue.quest_weapon.title"),
                Component.translatable("popup.tac_rogue.reward_drop.body", itemId.toUpperCase())
            );
        }
    }

    private static void placeGun(ServerPlayer player, ItemStack stack, String itemId, int price) {
        boolean slot0Full = !player.getInventory().items.get(GameConstants.SLOT_GUN_START).isEmpty();
        boolean slot1Full = !player.getInventory().items.get(GameConstants.SLOT_GUN_END).isEmpty();
        if (slot0Full && slot1Full) {
            if (sendToStash(player, stack)) {
                notify(player, PopupNotificationMessage.PopupType.REWARD, "STASH", "Gun slots full. Sent to stash: " + itemId.toUpperCase());
            } else {
                refundPurchase(player, price, itemId);
            }
        } else {
            player.getInventory().setItem(slot0Full ? GameConstants.SLOT_GUN_END : GameConstants.SLOT_GUN_START, stack);
            notifyPurchased(player, itemId);
        }
    }

    private static void placeMelee(ServerPlayer player, ItemStack stack, String itemId, int price) {
        if (!player.getInventory().items.get(GameConstants.SLOT_MELEE).isEmpty()) {
            if (placeInCombatItemSlots(player, stack)) {
                notifyPurchased(player, itemId);
                return;
            }
            if (sendToStash(player, stack)) {
                notify(player, PopupNotificationMessage.PopupType.REWARD, "STASH", "Melee slot full. Sent to stash: " + itemId.toUpperCase());
            } else {
                refundPurchase(player, price, itemId);
            }
        } else {
            player.getInventory().setItem(GameConstants.SLOT_MELEE, stack);
            notifyPurchased(player, itemId);
        }
    }

    private static void placeAmmo(ServerPlayer player, ItemStack stack, String itemId, int price) {
        String ammoId = stack.getTag().getString("AmmoId");
        int targetSlotStart = findAmmoSlotForAmmo(player, ammoId);
        ItemStack remaining = stack.copy();

        if (targetSlotStart >= 0) {
            mergeAndPlaceAmmoRange(player, remaining, targetSlotStart, targetSlotStart + 1);
        }

        if (!remaining.isEmpty()) {
            mergeAndPlaceAmmoRange(player, remaining, GameConstants.SLOT_AMMO_GUN1_START, GameConstants.SLOT_AMMO_GUN2_END);
        }

        if (!remaining.isEmpty()) {
            placeInUnlockedItemSlots(player, remaining);
        }

        if (!remaining.isEmpty()) {
            if (sendToStash(player, remaining)) {
                notify(player, PopupNotificationMessage.PopupType.REWARD, "STASH", "Ammo overflow. Sent to stash.");
            } else {
                refundPurchase(player, price, itemId);
            }
        } else {
            notifyPurchased(player, itemId);
        }
    }

    private static void mergeAndPlaceAmmoRange(ServerPlayer player, ItemStack moving, int startSlot, int endSlot) {
        if (moving.isEmpty() || !moving.hasTag() || !moving.getTag().contains("AmmoId")) return;
        int baseMax = TacZRegistryHelper.getAmmoStackSize(moving.getTag().getString("AmmoId"));
        int limit = com.levanilla.rogue.core.RunManager.getAmmoReserveStackLimit(player, baseMax);

        for (int slot = startSlot; slot <= endSlot && !moving.isEmpty(); slot++) {
            ItemStack existing = player.getInventory().items.get(slot);
            if (existing.isEmpty() || !RogueStackingService.canMerge(existing, moving)) continue;
            int space = limit - existing.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, moving.getCount());
            existing.grow(move);
            moving.shrink(move);
        }

        for (int slot = startSlot; slot <= endSlot && !moving.isEmpty(); slot++) {
            if (!player.getInventory().items.get(slot).isEmpty()) continue;
            int move = Math.min(limit, moving.getCount());
            ItemStack placed = moving.copy();
            placed.setCount(move);
            player.getInventory().setItem(slot, placed);
            moving.shrink(move);
        }
    }

    private static void placeGeneralItem(ServerPlayer player, ItemStack stack, String itemId, int price) {
        if (placeInUnlockedItemSlots(player, stack)) {
            notifyPurchased(player, itemId);
            return;
        }

        if (sendToStash(player, stack)) {
            notify(player, PopupNotificationMessage.PopupType.REWARD, "STASH", "Item slots full. Sent to stash.");
        } else {
            refundPurchase(player, price, itemId);
        }
    }

    private static boolean placeInUnlockedItemSlots(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (!canFitInUnlockedItemSlots(player, stack)) return false;

        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        int maxAllowedIndex = Math.min(GameConstants.SLOT_AMMO_GUN2_END + (invLevel * 2), 35);
        ItemStack remaining = stack.copy();

        for (int slot = GameConstants.SLOT_ITEM_START; slot <= maxAllowedIndex; slot++) {
            if (slot >= GameConstants.SLOT_AMMO_GUN1_START && slot <= GameConstants.SLOT_AMMO_GUN2_END) continue;
            ItemStack existing = player.getInventory().items.get(slot);
            if (existing.isEmpty() || !RogueStackingService.canMerge(existing, remaining)) continue;
            int space = existing.getMaxStackSize() - existing.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, remaining.getCount());
            existing.grow(move);
            remaining.shrink(move);
            if (remaining.isEmpty()) {
                player.getInventory().setChanged();
                return true;
            }
        }

        for (int slot = GameConstants.SLOT_ITEM_START; slot <= maxAllowedIndex; slot++) {
            if (slot >= GameConstants.SLOT_AMMO_GUN1_START && slot <= GameConstants.SLOT_AMMO_GUN2_END) continue;
            if (player.getInventory().items.get(slot).isEmpty()) {
                player.getInventory().setItem(slot, remaining);
                player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    private static boolean canFitInUnlockedItemSlots(ServerPlayer player, ItemStack stack) {
        int remaining = stack.getCount();
        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        int maxAllowedIndex = Math.min(GameConstants.SLOT_AMMO_GUN2_END + (invLevel * 2), 35);

        for (int slot = GameConstants.SLOT_ITEM_START; slot <= maxAllowedIndex; slot++) {
            if (slot >= GameConstants.SLOT_AMMO_GUN1_START && slot <= GameConstants.SLOT_AMMO_GUN2_END) continue;
            ItemStack existing = player.getInventory().items.get(slot);
            if (existing.isEmpty()) {
                remaining -= stack.getMaxStackSize();
            } else if (RogueStackingService.canMerge(existing, stack)) {
                remaining -= Math.max(0, existing.getMaxStackSize() - existing.getCount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static boolean placeInCombatItemSlots(ServerPlayer player, ItemStack stack) {
        for (int slot = GameConstants.SLOT_ITEM_START; slot <= GameConstants.SLOT_ITEM_END; slot++) {
            if (player.getInventory().items.get(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack.copy());
                return true;
            }
        }
        return false;
    }

    private static void refundPurchase(ServerPlayer player, int price, String itemId) {
        CurrencyManager.addGoldNoQuest(player, price);
        notify(player, PopupNotificationMessage.PopupType.WARNING, "REFUNDED",
            "All slots and stash are full. Refunded $" + price + " for " + itemId.toUpperCase());
    }

    private static int findAmmoSlotForAmmo(ServerPlayer player, String ammoId) {
        ItemStack gun0 = player.getInventory().items.get(GameConstants.SLOT_GUN_START);
        if (!gun0.isEmpty() && gun0.hasTag() && gun0.getTag().contains("GunId")) {
            String gunAmmo = TacZRegistryHelper.getAmmoForGun(gun0.getTag().getString("GunId"));
            if (gunAmmo.equals(ammoId)) return GameConstants.SLOT_AMMO_GUN1_START;
        }

        ItemStack gun1 = player.getInventory().items.get(GameConstants.SLOT_GUN_END);
        if (!gun1.isEmpty() && gun1.hasTag() && gun1.getTag().contains("GunId")) {
            String gunAmmo = TacZRegistryHelper.getAmmoForGun(gun1.getTag().getString("GunId"));
            if (gunAmmo.equals(ammoId)) return GameConstants.SLOT_AMMO_GUN2_START;
        }
        return -1;
    }

    private static void notifyPurchased(ServerPlayer player, String itemId) {
        notify(player, PopupNotificationMessage.PopupType.REWARD, "PURCHASED", itemId.toUpperCase());
    }

    private static void notify(ServerPlayer player, PopupNotificationMessage.PopupType type, String title, String body) {
        PopupNotificationMessage.send(player, type, Component.literal(title), Component.literal(body), 120);
    }
}
