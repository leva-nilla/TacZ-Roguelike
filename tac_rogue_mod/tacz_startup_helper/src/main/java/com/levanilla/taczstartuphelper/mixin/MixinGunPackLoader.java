package com.levanilla.taczstartuphelper.mixin;

import com.levanilla.taczstartuphelper.StartupTimer;
import com.levanilla.taczstartuphelper.TaczStartupHelper;
import com.tacz.guns.resource.GunPackLoader;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunPackLoader.class, remap = false)
public class MixinGunPackLoader {
    @Unique
    private final ThreadLocal<Long> taczStartupHelper$discoverStart = new ThreadLocal<>();

    @Inject(method = "discoverExtensions", at = @At("HEAD"))
    private void taczStartupHelper$profileDiscoverStart(CallbackInfoReturnable<Pack> cir) {
        taczStartupHelper$discoverStart.set(StartupTimer.start());
    }

    @Inject(method = "discoverExtensions", at = @At("RETURN"))
    private void taczStartupHelper$profileDiscoverEnd(CallbackInfoReturnable<Pack> cir) {
        long start = taczStartupHelper$discoverStart.get() != null ? taczStartupHelper$discoverStart.get() : StartupTimer.start();
        StartupTimer.log(TaczStartupHelper.LOGGER, "GunPackLoader.discoverExtensions", start, cir.getReturnValue() != null ? "pack registered" : "no pack");
        taczStartupHelper$discoverStart.remove();
    }
}
