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
        for (int i = 0; i < maxSlots; i++) {
            if (stash.getItem(i).isEmpty()) {
                stash.setItem(i, stack.copy());
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
            if (placeInCombatItemSlots(player, stack)) {
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
            if (placeInCombatItemSlots(player, stack)) {
                player.sendSystemMessage(Component.translatable("message.tac_rogue.shop_purchased", itemId.toUpperCase()));
                return;
            }
            if (sendToStash(player, stack)) {
                player.sendSystemMessage(Component.literal(
                    "\u00A7e[SHOP] Gun slots full -> Sent to stash: " + itemId.toUpperCase()));
            } else {
                refundPurchase(player, price, itemId);
            }
        } else {
            player.getInventory().setItem(slot0Full ? GameConstants.SLOT_GUN_END : GameConstants.SLOT_GUN_START, stack);
            player.sendSystemMessage(Component.translatable("message.tac_rogue.shop_purchased", itemId.toUpperCase()));
        }
    }

    private static void placeMelee(ServerPlayer player, ItemStack stack, String itemId, int price) {
        if (!player.getInventory().items.get(GameConstants.SLOT_MELEE).isEmpty()) {
            if (placeInCombatItemSlots(player, stack)) {
                player.sendSystemMessage(Component.translatable("message.tac_rogue.shop_purchased", itemId.toUpperCase()));
                return;
            }
            if (sendToStash(player, stack)) {
                player.sendSystemMessage(Component.literal(
                    "\u00A7e[SHOP] Melee slot full -> Sent to stash: " + itemId.toUpperCase()));
            } else {
                refundPurchase(player, price, itemId);
            }
        } else {
            player.getInventory().setItem(GameConstants.SLOT_MELEE, stack);
            player.sendSystemMessage(Component.translatable("message.tac_rogue.shop_purchased", itemId.toUpperCase()));
        }
    }

    private static void placeAmmo(ServerPlayer player, ItemStack stack, String itemId, int price) {
        String ammoId = stack.getTag().getString("AmmoId");
        int targetSlotStart = findAmmoSlotForAmmo(player, ammoId);
        boolean placed = false;

        if (targetSlotStart >= 0) {
            for (int slot = targetSlotStart; slot <= targetSlotStart + 1; slot++) {
                if (player.getInventory().items.get(slot).isEmpty()) {
                    player.getInventory().setItem(slot, stack);
                    placed = true;
                    break;
                }
            }
        }

        if (!placed) {
            for (int slot = GameConstants.SLOT_AMMO_GUN1_START; slot <= GameConstants.SLOT_AMMO_GUN2_END; slot++) {
                if (player.getInventory().items.get(slot).isEmpty()) {
                    player.getInventory().setItem(slot, stack);
                    placed = true;
                    break;
                }
            }
        }

        if (!placed) {
            placed = placeInUnlockedItemSlots(player, stack);
        }

        if (!placed) {
            if (sendToStash(player, stack)) {
                player.sendSystemMessage(Component.literal("\u00A7e[SHOP] Ammo overflow -> Sent to stash"));
            } else {
                refundPurchase(player, price, itemId);
            }
        } else {
            player.sendSystemMessage(Component.translatable("message.tac_rogue.shop_purchased", itemId.toUpperCase()));
        }
    }

    private static void placeGeneralItem(ServerPlayer player, ItemStack stack, String itemId, int price) {
        if (placeInUnlockedItemSlots(player, stack)) {
            player.sendSystemMessage(Component.translatable("message.tac_rogue.shop_purchased", itemId.toUpperCase()));
            return;
        }

        if (sendToStash(player, stack)) {
            player.sendSystemMessage(Component.literal("\u00A7e[SHOP] Item slots full -> Sent to stash"));
        } else {
            refundPurchase(player, price, itemId);
        }
    }

    private static boolean placeInUnlockedItemSlots(ServerPlayer player, ItemStack stack) {
        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        int maxAllowedIndex = Math.min(GameConstants.SLOT_AMMO_GUN2_END + (invLevel * 2), 35);

        for (int slot = GameConstants.SLOT_ITEM_START; slot <= maxAllowedIndex; slot++) {
            if (slot >= GameConstants.SLOT_AMMO_GUN1_START && slot <= GameConstants.SLOT_AMMO_GUN2_END) continue;
            if (player.getInventory().items.get(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack);
                return true;
            }
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
        player.sendSystemMessage(Component.literal(
            "\u00A7c[SHOP] All slots & stash full! Refunded \u00A76$" + price + "\u00A7c for " + itemId.toUpperCase()));
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
}
