package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.service.RogueStackingService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestMenu.class)
public abstract class MixinChestMenu {
    @Shadow public abstract int getRowCount();

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void tacRogue$quickMoveAmmoToReserve(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        ChestMenu menu = (ChestMenu) (Object) this;
        int containerSlots = getRowCount() * 9;
        if (index < 0 || index >= containerSlots || index >= menu.slots.size()) return;

        Slot sourceSlot = menu.slots.get(index);
        ItemStack source = sourceSlot.getItem();
        if (!isAmmo(source)) return;

        ItemStack original = source.copy();
        ItemStack moving = source.copy();
        moveAmmoToPlayer(player, moving);
        int moved = original.getCount() - moving.getCount();
        if (moved <= 0) return;

        if (moving.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.set(moving);
        }
        sourceSlot.setChanged();
        cir.setReturnValue(original);
    }

    private static void moveAmmoToPlayer(Player player, ItemStack moving) {
        if (!isAmmo(moving)) return;
        int baseMax = TacZRegistryHelper.getAmmoStackSize(moving.getTag().getString("AmmoId"));
        int reserveLimit = player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            ? RunManager.getAmmoReserveStackLimit(serverPlayer, baseMax)
            : clientReserveLimit(baseMax);
        int generalLimit = player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            ? RunManager.getAmmoStackLimit(serverPlayer, baseMax)
            : clientGeneralLimit(baseMax);
        int backpackEnd = unlockedBackpackEnd(player);

        mergeIntoRange(player, moving, GameConstants.SLOT_AMMO_START, GameConstants.SLOT_AMMO_END, reserveLimit);
        placeIntoRange(player, moving, GameConstants.SLOT_AMMO_START, GameConstants.SLOT_AMMO_END, reserveLimit);
        mergeIntoRange(player, moving, GameConstants.SLOT_ITEM_START, GameConstants.SLOT_ITEM_END, generalLimit);
        placeIntoRange(player, moving, GameConstants.SLOT_ITEM_START, GameConstants.SLOT_ITEM_END, generalLimit);
        mergeIntoRange(player, moving, GameConstants.SLOT_AMMO_END + 1, backpackEnd, generalLimit);
        placeIntoRange(player, moving, GameConstants.SLOT_AMMO_END + 1, backpackEnd, generalLimit);
    }

    private static void mergeIntoRange(Player player, ItemStack moving, int start, int end, int limit) {
        for (int slot = start; slot <= end && !moving.isEmpty(); slot++) {
            ItemStack target = player.getInventory().getItem(slot);
            if (target.isEmpty() || !RogueStackingService.canMerge(target, moving)) continue;
            int space = limit - target.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, moving.getCount());
            target.grow(move);
            moving.shrink(move);
        }
    }

    private static void placeIntoRange(Player player, ItemStack moving, int start, int end, int limit) {
        for (int slot = start; slot <= end && !moving.isEmpty(); slot++) {
            ItemStack target = player.getInventory().getItem(slot);
            if (!target.isEmpty()) continue;
            int move = Math.min(limit, moving.getCount());
            ItemStack placed = moving.copy();
            placed.setCount(move);
            player.getInventory().setItem(slot, placed);
            moving.shrink(move);
        }
    }

    private static boolean isAmmo(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().contains("AmmoId");
    }

    private static int clientGeneralLimit(int baseMax) {
        return clientReserveLimit(baseMax);
    }

    private static int clientReserveLimit(int baseMax) {
        int capLevel = RunManager.getGlobalAmmoCapacityLevel();
        return Math.max(1, Math.round(baseMax * (1.5f + capLevel * 0.5f)));
    }

    private static int unlockedBackpackEnd(Player player) {
        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        return Math.min(GameConstants.SLOT_AMMO_END + invLevel * 2, 35);
    }
}
