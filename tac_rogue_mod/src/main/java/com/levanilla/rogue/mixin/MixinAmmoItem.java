package com.levanilla.rogue.mixin;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.item.AmmoItem;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AmmoItem.class)
public abstract class MixinAmmoItem {
    @Inject(method = {"getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", "m_5866_(Lnet/minecraft/world/item/ItemStack;)I"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetMaxStackSize(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack.getItem() instanceof IAmmo iAmmo) {
            int baseSize = TimelessAPI.getCommonAmmoIndex(iAmmo.getAmmoId(stack))
                    .map(CommonAmmoIndex::getStackSize).orElse(1);
            int capLevel = com.levanilla.rogue.core.RunManager.getGlobalAmmoCapacityLevel();
            int finalLimit = Math.round(baseSize * (1.5f + (capLevel * 0.5f)));
            cir.setReturnValue(finalLimit);
        }
    }
}
