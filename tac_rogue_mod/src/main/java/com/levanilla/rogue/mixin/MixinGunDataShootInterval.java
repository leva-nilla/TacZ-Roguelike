package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.WeaponRarity;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = com.tacz.guns.resource.pojo.data.gun.GunData.class, remap = false)
public abstract class MixinGunDataShootInterval {
    @Inject(method = "getShootInterval", at = @At("RETURN"), cancellable = true, remap = false)
    private void tacRogue$applyFireRateScaling(LivingEntity shooter, FireMode fireMode, ItemStack gunStack,
                                               CallbackInfoReturnable<Long> cir) {
        if (shooter == null
            || (shooter.level().dimension() != CommonEventHandler.ROGUE_DIM
                && shooter.level().dimension() != CommonEventHandler.LOBBY_DIM)) return;
        if (gunStack == null || gunStack.isEmpty()) return;

        long originalIntervalMs = cir.getReturnValue();
        long adjustedIntervalMs = WeaponRarity.getFireRateAdjustedIntervalMs(originalIntervalMs, gunStack, shooter);
        if (adjustedIntervalMs != originalIntervalMs) {
            cir.setReturnValue(adjustedIntervalMs);
        }
    }
}
