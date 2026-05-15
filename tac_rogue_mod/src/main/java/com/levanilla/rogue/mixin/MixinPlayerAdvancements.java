package com.levanilla.rogue.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public class MixinPlayerAdvancements {
    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void tac_rogue$disableAdvancementAward(Advancement advancement, String criterionKey, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
