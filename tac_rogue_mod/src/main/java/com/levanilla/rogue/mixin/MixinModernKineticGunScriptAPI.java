package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.CommonEventHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = com.tacz.guns.item.ModernKineticGunScriptAPI.class, remap = false)
public abstract class MixinModernKineticGunScriptAPI {
    @Shadow private LivingEntity shooter;

    @Inject(method = "reduceAmmoOnce", at = @At("HEAD"), cancellable = true, remap = false)
    private void tacRogue$skipLobbyAmmoReduction(CallbackInfoReturnable<Boolean> cir) {
        if (shooter != null && shooter.level().dimension() == CommonEventHandler.LOBBY_DIM) {
            cir.setReturnValue(true);
        }
    }
}
