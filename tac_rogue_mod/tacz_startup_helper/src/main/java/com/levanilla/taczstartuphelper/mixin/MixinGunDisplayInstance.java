package com.levanilla.taczstartuphelper.mixin;

import com.levanilla.taczstartuphelper.StartupTimer;
import com.levanilla.taczstartuphelper.TaczStartupHelper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunDisplayInstance.class, remap = false)
public class MixinGunDisplayInstance {
    @Unique
    private static final ThreadLocal<Long> taczStartupHelper$createStart = new ThreadLocal<>();

    @Inject(method = "create", at = @At("HEAD"))
    private static void taczStartupHelper$createStart(GunDisplay display, CallbackInfoReturnable<GunDisplayInstance> cir) {
        taczStartupHelper$createStart.set(StartupTimer.start());
    }

    @Inject(method = "create", at = @At("RETURN"))
    private static void taczStartupHelper$createEnd(GunDisplay display, CallbackInfoReturnable<GunDisplayInstance> cir) {
        long start = taczStartupHelper$createStart.get() != null ? taczStartupHelper$createStart.get() : StartupTimer.start();
        StartupTimer.log(TaczStartupHelper.LOGGER, "GunDisplayInstance.create", start, display.getModelLocation());
        taczStartupHelper$createStart.remove();
    }
}
