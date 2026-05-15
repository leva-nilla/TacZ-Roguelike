package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.StashSavedData;
import com.levanilla.rogue.networking.PopupNotificationMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

public final class RoguePickupService {
    private RoguePickupService() {
    }

    public static void routePickup(ServerPlayer player, EntityItemPickupEvent event) {
        ItemStack stack = event.getItem().getItem();

        event.setCanceled(true);

        boolean isGun = stack.hasTag() && stack.getTag().contains("GunId");
        boolean isMelee = stack.hasTag() && stack.getTag().contains("MeleeWeaponId");
        boolean isAmmo = stack.hasTag() && stack.getTag().contains("AmmoId");

        if (isGun) {
            if (placeGun(player, stack) || placeInCombatItemSlots(player, stack)) {
                event.getItem().discard();
                return;
            }
            sendToStashOrDrop(player, stack, event);
            return;
        }

        if (isMelee) {
            if (placeMelee(player, stack) || placeInCombatItemSlots(player, stack)) {
                event.getItem().discard();
                return;
            }
            sendToStashOrDrop(player, stack, event);
            return;
        }

        if (isAmmo) {
            if (placeAmmo(player, stack) || placeInItemSlots(player, stack)) {
                event.getItem().discard();
                return;
            }
            delayPickup(event);
            return;
        }

        if (placeInItemSlots(player, stack)) {
            event.getItem().discard();
            return;
        }

        delayPickup(event);
        player.displayClientMessage(Component.literal("\u00a7c\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u304c\u6e80\u676f\u3067\u3059\uff01"), true);
    }

    private static boolean placeGun(ServerPlayer player, ItemStack stack) {
        for (int slot = GameConstants.SLOT_GUN_START; slot <= GameConstants.SLOT_GUN_END; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack.copy());
                return true;
            }
        }
        return false;
    }

    private static boolean placeMelee(ServerPlayer player, ItemStack stack) {
        if (player.getInventory().getItem(GameConstants.SLOT_MELEE).isEmpty()) {
            player.getInventory().setItem(GameConstants.SLOT_MELEE, stack.copy());
            return true;
        }
        return false;
    }

    private static boolean placeAmmo(ServerPlayer player, ItemStack stack) {
        for (int slot = GameConstants.SLOT_AMMO_GUN1_START; slot <= GameConstants.SLOT_AMMO_GUN2_END; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, stack.getCount());
                    existing.grow(transfer);
                    stack.shrink(transfer);
                    if (stack.isEmpty()) {
                        return true;
                    }
                }
            }
        }

        for (int slot = GameConstants.SLOT_AMMO_GUN1_START; slot <= GameConstants.SLOT_AMMO_GUN2_END; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack.copy());
                return true;
            }
        }
        return false;
    }

    private static boolean placeInItemSlots(ServerPlayer player, ItemStack stack) {
        for (int slot = GameConstants.SLOT_ITEM_START; slot <= GameConstants.SLOT_ITEM_END; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, stack.getCount());
                    existing.grow(transfer);
                    stack.shrink(transfer);
                    if (stack.isEmpty()) {
                        return true;
                    }
                }
            }
        }

        for (int slot = GameConstants.SLOT_ITEM_START; slot <= GameConstants.SLOT_ITEM_END; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack.copy());
                return true;
            }
        }

        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        int maxAllowed = Math.min(GameConstants.SLOT_AMMO_GUN2_END + (invLevel * 2), 35);
        for (int slot = GameConstants.SLOT_AMMO_GUN2_END + 1; slot <= maxAllowed; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            boolean isLocked = existing.is(Items.BARRIER) && existing.hasTag()
                && existing.getOrCreateTag().getBoolean("rogue_item_locked");
            if (isLocked) {
                continue;
            }
            if (existing.isEmpty()) {
                player.getInventory().setItem(slot, stack.copy());
                return true;
            }
        }
        return false;
    }

    private static boolean placeInCombatItemSlots(ServerPlayer player, ItemStack stack) {
        for (int slot = GameConstants.SLOT_ITEM_START; slot <= GameConstants.SLOT_ITEM_END; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack.copy());
                return true;
            }
        }
        return false;
    }

    private static void sendToStashOrDrop(ServerPlayer player, ItemStack stack, EntityItemPickupEvent event) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());

        int maxSlots = stash.unlockedLines * 9;
        for (int i = 0; i < maxSlots; i++) {
            if (stash.getItem(i).isEmpty()) {
                stash.setItem(i, stack.copy());
                event.getItem().discard();
                player.sendSystemMessage(Component.literal(
                    "\u00a7b[STASH] \u00a7a" + stack.getHoverName().getString() + " \u00a7f\u3092\u30b9\u30bf\u30c3\u30b7\u30e5\u306b\u56de\u53ce\u3057\u307e\u3057\u305f\uff01"));
                PopupNotificationMessage.send(
                    player,
                    PopupNotificationMessage.PopupType.REWARD,
                    Component.literal("STASH"),
                    Component.translatable("popup.tac_rogue.reward_stash.body", stack.getHoverName())
                );
                data.setDirty();
                return;
            }
        }

        delayPickup(event);
        player.displayClientMessage(Component.literal("\u00a7c\u30b9\u30bf\u30c3\u30b7\u30e5\u304c\u6e80\u676f\u3067\u56de\u53ce\u3067\u304d\u307e\u305b\u3093\uff01"), true);
    }

    private static void delayPickup(EntityItemPickupEvent event) {
        event.getItem().setPickUpDelay(40);
    }
}
