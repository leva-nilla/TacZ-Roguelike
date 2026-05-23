package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.GameConstants;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class MixinInventoryMenu extends AbstractContainerMenu {
    protected MixinInventoryMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void tacRogue$quickMoveCombatItemsToBackpack(Player player, int index,
                                                         CallbackInfoReturnable<ItemStack> cir) {
        if (!(player.getInventory() instanceof Inventory)) return;
        if (!isCombatItemMenuSlot(index)) return;
        if (index < 0 || index >= this.slots.size()) return;

        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int before = stack.getCount();

        if (isAmmo(stack)) {
            this.moveItemStackTo(stack, GameConstants.SLOT_AMMO_START, GameConstants.SLOT_AMMO_END + 1, false);
        }
        if (!stack.isEmpty()) {
            this.moveItemStackTo(stack, GameConstants.SLOT_AMMO_END + 1, 36, false);
        }

        if (stack.getCount() == before) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        cir.setReturnValue(original);
    }

    private static boolean isCombatItemMenuSlot(int menuIndex) {
        return (menuIndex >= 39 && menuIndex <= 44) || (menuIndex >= 9 && menuIndex <= 11);
    }

    private static boolean isAmmo(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().contains("AmmoId");
    }
}
