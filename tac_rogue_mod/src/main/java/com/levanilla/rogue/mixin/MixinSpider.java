package com.levanilla.rogue.mixin;

import net.minecraft.world.entity.monster.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Spider.class)
public abstract class MixinSpider {
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void tacRogue$disableDungeonSpiderClimb(CallbackInfoReturnable<Boolean> cir) {
        Spider spider = (Spider) (Object) this;
        if (spider.getTags().contains("tac_rogue_spawned")
                && spider.getPersistentData().getBoolean("TacRogueNoSpiderClimb")) {
            cir.setReturnValue(false);
        }
    }
}
