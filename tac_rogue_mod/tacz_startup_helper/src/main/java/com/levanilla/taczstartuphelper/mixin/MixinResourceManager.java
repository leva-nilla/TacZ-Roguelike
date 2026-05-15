package com.levanilla.taczstartuphelper.mixin;

import com.levanilla.taczstartuphelper.TaczStartupHelper;
import com.tacz.guns.api.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Paths;

@Mixin(value = ResourceManager.class, remap = false)
public class MixinResourceManager {
    @Inject(method = "registerExportResource", at = @At("HEAD"), cancellable = true)
    private static void taczStartupHelper$synchronizedRegisterExportResource(Class<?> modMainClass, String extraFolderPath, CallbackInfo ci) {
        synchronized (ResourceManager.EXTRA_ENTRIES) {
            ResourceManager.EXTRA_ENTRIES.add(new ResourceManager.ExtraEntry(
                modMainClass,
                extraFolderPath,
                Paths.get(extraFolderPath).getFileName().toString()
            ));
        }
        TaczStartupHelper.LOGGER.debug("{} registered export resource {} from {}", TaczStartupHelper.PREFIX, extraFolderPath, modMainClass.getName());
        ci.cancel();
    }
}
