package com.levanilla.rogue.mixin;

import com.github.leawind.thirdperson.api.client.event.ThirdPersonCameraSetupEvent;
import com.github.leawind.thirdperson.core.CameraAgent;
import com.levanilla.rogue.client.compat.LeaWindsRecoilController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.github.leawind.thirdperson.core.CameraAgent.class, remap = false)
public abstract class MixinLeaWindsCameraAgent {
    @Inject(method = "onCameraSetup", at = @At("HEAD"), remap = false)
    private void tacRogue$applyTacZRecoilToLeaWindsRotation(ThirdPersonCameraSetupEvent event, CallbackInfo ci) {
        LeaWindsRecoilController.applyToLeaWindsRotation((CameraAgent) (Object) this);
    }

    @Inject(method = "onCameraSetup", at = @At("RETURN"), remap = false)
    private void tacRogue$applyTacZRecoil(ThirdPersonCameraSetupEvent event, CallbackInfo ci) {
        LeaWindsRecoilController.applyToCamera(event);
    }
}
