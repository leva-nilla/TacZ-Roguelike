package com.levanilla.rogue.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 三人称視点で hitResult が null の場合にクラッシュする問題を修正。
 * TacZ や他のMOD が hitResult を参照するとき NullPointerException が発生するのを防止する。
 */
@Mixin(LocalPlayer.class)
public class MixinLocalPlayerDraw {

    @Inject(method = "tick", at = @At("HEAD"))
    private void preventHitResultNPE(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult == null && mc.player != null) {
            mc.hitResult = new BlockHitResult(
                mc.player.position(),
                Direction.UP,
                mc.player.blockPosition(),
                false
            );
        }
    }
}
