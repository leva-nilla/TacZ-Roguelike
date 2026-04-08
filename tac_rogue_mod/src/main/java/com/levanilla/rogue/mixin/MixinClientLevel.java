package com.levanilla.rogue.mixin;

import com.levanilla.rogue.client.DynamicLightManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ClientLevel の getRawBrightness をフックし、動的ライトを追加。
 * DynamicLightManager が保持するフラッシュライトのビーム位置に基づき、
 * レンダリング時の光量を差し替える。ブロック配置は一切行わない。
 */
@Mixin(ClientLevel.class)
public class MixinClientLevel {

    @Inject(method = "getRawBrightness", at = @At("RETURN"), cancellable = true)
    private void tac_rogue$addDynamicLight(BlockPos pos, int darkness, CallbackInfoReturnable<Integer> cir) {
        int dynamicLight = DynamicLightManager.getDynamicLightAt(pos);
        if (dynamicLight > 0) {
            int vanilla = cir.getReturnValue();
            // max(バニラ光量, 動的ライト - ダークネス補正)
            int effective = Math.max(0, dynamicLight - darkness);
            if (effective > vanilla) {
                cir.setReturnValue(effective);
            }
        }
    }
}
