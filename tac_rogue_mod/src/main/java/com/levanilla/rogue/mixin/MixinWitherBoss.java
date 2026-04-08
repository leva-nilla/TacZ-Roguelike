package com.levanilla.rogue.mixin;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WitherBoss.class)
public class MixinWitherBoss {

    /**
     * WitherBossの aiStep() メソッドに介入し、
     * バニラコードが強制付与した突進ベクトル(deltaMovement)を、
     * 実際に座標が更新される直前（RETURNの直前、すなわち super.aiStep() 呼び出しの直前または直後）で減衰させます。
     */
    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Monster;aiStep()V", shift = At.Shift.BEFORE))
    private void dampenDashMomentumBeforeSuper(CallbackInfo ci) {
        WitherBoss wither = (WitherBoss) (Object) this;
        Vec3 vel = wither.getDeltaMovement();
        // バニラAIの硬直ハードコードされた加速を物理レイヤーでさらに強力に(0.3倍)に抑え込む
        wither.setDeltaMovement(vel.multiply(0.3D, 0.5D, 0.3D));
    }
}
