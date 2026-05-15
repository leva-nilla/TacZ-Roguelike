package com.levanilla.rogue.mixin;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class MixinInventory {
    @Inject(method = "getSelectionSize", at = @At("HEAD"), cancellable = true)
    private static void tacRogue$getSelectionSize(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(12);
    }

    @Inject(method = "isHotbarSlot", at = @At("HEAD"), cancellable = true)
    private static void tacRogue$isHotbarSlot(int slot, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(slot >= 0 && slot < 12);
    }

    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void onGetMaxStackSize(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(999);
    }
}
