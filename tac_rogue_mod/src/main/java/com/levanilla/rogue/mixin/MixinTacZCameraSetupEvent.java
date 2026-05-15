package com.levanilla.rogue.mixin;

import com.levanilla.rogue.client.compat.LeaWindsRecoilController;
import com.levanilla.rogue.client.compat.RecoilDebugLogger;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.tacz.guns.client.event.CameraSetupEvent.class, remap = false)
public abstract class MixinTacZCameraSetupEvent {
    @Inject(method = "applyCameraRecoil", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tacRogue$letLeaWindsOwnThirdPersonRecoil(ViewportEvent.ComputeCameraAngles event, CallbackInfo ci) {
        boolean ownedByLeaWinds = LeaWindsRecoilController.shouldOwnTacZRecoil();
        RecoilDebugLogger.logTacZCamera("TACZ_CAMERA_HEAD", event, ownedByLeaWinds);
        if (LeaWindsRecoilController.consumeTacZPlayerRecoilIfOwned()) {
            RecoilDebugLogger.logTacZCamera("TACZ_CAMERA_CANCEL_FOR_LEAWINDS", event, true);
            ci.cancel();
        }
    }

    @Inject(method = "applyCameraRecoil", at = @At("RETURN"), remap = false)
    private static void tacRogue$logTacZRecoilReturn(ViewportEvent.ComputeCameraAngles event, CallbackInfo ci) {
        RecoilDebugLogger.logTacZCamera("TACZ_CAMERA_RETURN", event, false);
    }
}
