package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
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
        boolean isAmmo = isAmmo(stack);

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
            sendToStashOrDrop(player, stack, event);
            return;
        }

        if (placeInItemSlots(player, stack)) {
            event.getItem().discard();
            return;
        }

        sendToStashOrDrop(player, stack, event);
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
        int reserveLimit = ammoReserveLimit(player, stack);
        for (int slot = GameConstants.SLOT_AMMO_GUN1_START; slot <= GameConstants.SLOT_AMMO_GUN2_END; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (!existing.isEmpty() && RogueStackingService.canMerge(existing, stack)) {
                int space = reserveLimit - existing.getCount();
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
                int transfer = Math.min(reserveLimit, stack.getCount());
                ItemStack placed = stack.copy();
                placed.setCount(transfer);
                player.getInventory().setItem(slot, placed);
                stack.shrink(transfer);
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean placeInItemSlots(ServerPlayer player, ItemStack stack) {
        boolean ammo = isAmmo(stack);
        int limit = ammo ? ammoGeneralLimit(player, stack) : stack.getMaxStackSize();
        for (int slot = GameConstants.SLOT_ITEM_START; slot <= GameConstants.SLOT_ITEM_END; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (!existing.isEmpty() && RogueStackingService.canMerge(existing, stack)) {
                int space = limit - existing.getCount();
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
                int transfer = Math.min(limit, stack.getCount());
                ItemStack placed = stack.copy();
                placed.setCount(transfer);
                player.getInventory().setItem(slot, placed);
                stack.shrink(transfer);
                if (stack.isEmpty()) {
                    return true;
                }
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
            if (!existing.isEmpty() && RogueStackingService.canMerge(existing, stack)) {
                int space = limit - existing.getCount();
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
        for (int slot = GameConstants.SLOT_AMMO_GUN2_END + 1; slot <= maxAllowed; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            boolean isLocked = existing.is(Items.BARRIER) && existing.hasTag()
                && existing.getOrCreateTag().getBoolean("rogue_item_locked");
            if (isLocked) {
                continue;
            }
            if (existing.isEmpty()) {
                int transfer = Math.min(limit, stack.getCount());
                ItemStack placed = stack.copy();
                placed.setCount(transfer);
                player.getInventory().setItem(slot, placed);
                stack.shrink(transfer);
                if (stack.isEmpty()) {
                    return true;
                }
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
        if (stack.isEmpty()) {
            event.getItem().discard();
            return;
        }

        if (ShopPlacementService.sendToStash(player, stack)) {
            event.getItem().discard();
            player.sendSystemMessage(Component.literal("[STASH] " + stack.getHoverName().getString() + " recovered."));
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.REWARD,
                Component.literal("STASH"),
                Component.translatable("popup.tac_rogue.reward_stash.body", stack.getHoverName())
            );
            return;
        }

        delayPickup(event);
        player.displayClientMessage(Component.literal("Stash is full."), true);
    }

    private static int ammoReserveLimit(ServerPlayer player, ItemStack stack) {
        return RunManager.getAmmoReserveStackLimit(player, ammoBaseLimit(stack));
    }

    private static int ammoGeneralLimit(ServerPlayer player, ItemStack stack) {
        return RunManager.getAmmoStackLimit(player, ammoBaseLimit(stack));
    }

    private static int ammoBaseLimit(ItemStack stack) {
        if (!isAmmo(stack)) return Math.max(1, stack.getMaxStackSize());
        return Math.max(1, TacZRegistryHelper.getAmmoStackSize(stack.getTag().getString("AmmoId")));
    }

    private static void delayPickup(EntityItemPickupEvent event) {
        event.getItem().setPickUpDelay(40);
    }

    private static boolean isAmmo(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().contains("AmmoId");
    }
}
