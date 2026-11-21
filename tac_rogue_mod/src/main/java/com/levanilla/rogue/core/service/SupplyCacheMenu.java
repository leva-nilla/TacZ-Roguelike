package com.levanilla.rogue.core.service;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Server-side, take-only variant of the existing 3-row chest menu. */
final class SupplyCacheMenu extends ChestMenu {
    static final int SIZE = 27;
    private final Predicate<Player> canUse;

    SupplyCacheMenu(int id, Inventory playerInventory, Container loot, Predicate<Player> canUse) {
        super(MenuType.GENERIC_9x3, id, playerInventory, loot, 3);
        this.canUse = canUse;
    }

    @Override
    protected Slot addSlot(Slot slot) {
        if (slot.container instanceof Inventory) return super.addSlot(slot);
        return super.addSlot(new Slot(slot.container, slot.getContainerSlot(), slot.x, slot.y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // ChestMenu's merge path can bypass mayPlace for an existing stack.
        return index >= 0 && index < SIZE ? super.quickMoveStack(player, index) : ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return canUse.test(player);
    }
}
