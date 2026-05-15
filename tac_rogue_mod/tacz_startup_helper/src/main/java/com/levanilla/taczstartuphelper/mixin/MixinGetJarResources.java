package com.levanilla.taczstartuphelper.mixin;

import com.levanilla.taczstartuphelper.ExportCopyCache;
import com.levanilla.taczstartuphelper.StartupTimer;
import com.levanilla.taczstartuphelper.TaczStartupHelper;
import com.tacz.guns.util.GetJarResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(value = GetJarResources.class, remap = false)
public class MixinGetJarResources {
    @Unique
    private static final ThreadLocal<Long> taczStartupHelper$copyStart = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Boolean> taczStartupHelper$copySkipped = ThreadLocal.withInitial(() -> false);

    @Inject(method = "copyModDirectory(Ljava/lang/Class;Ljava/lang/String;Ljava/nio/file/Path;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private static void taczStartupHelper$skipUnchangedExport(Class<?> resourceClass, String srcPath, Path root, String path, CallbackInfo ci) {
        taczStartupHelper$copyStart.set(StartupTimer.start());
        taczStartupHelper$copySkipped.set(false);
        if (ExportCopyCache.canSkip(resourceClass, srcPath, root, path)) {
            taczStartupHelper$copySkipped.set(true);
            long start = taczStartupHelper$copyStart.get() != null ? taczStartupHelper$copyStart.get() : StartupTimer.start();
            StartupTimer.log(TaczStartupHelper.LOGGER, "TacZ export copy skip", start, srcPath + " -> " + root.resolve(path));
            taczStartupHelper$copyStart.remove();
            taczStartupHelper$copySkipped.remove();
            ci.cancel();
        }
    }

    @Inject(method = "copyModDirectory(Ljava/lang/Class;Ljava/lang/String;Ljava/nio/file/Path;Ljava/lang/String;)V", at = @At("RETURN"))
    private static void taczStartupHelper$markExportCopy(Class<?> resourceClass, String srcPath, Path root, String path, CallbackInfo ci) {
        long start = taczStartupHelper$copyStart.get() != null ? taczStartupHelper$copyStart.get() : StartupTimer.start();
        boolean skipped = Boolean.TRUE.equals(taczStartupHelper$copySkipped.get());
        if (!skipped) {
            if (ExportCopyCache.isExportComplete(root, path)) {
                ExportCopyCache.markCopied(resourceClass, srcPath, path);
            } else {
                TaczStartupHelper.LOGGER.warn("{} not marking incomplete TacZ export copy: {} -> {}",
                    TaczStartupHelper.PREFIX, srcPath, root.resolve(path));
            }
        }
        StartupTimer.log(TaczStartupHelper.LOGGER, "TacZ export copy " + (skipped ? "skip" : "copy"), start, srcPath + " -> " + root.resolve(path));
        taczStartupHelper$copyStart.remove();
        taczStartupHelper$copySkipped.remove();
    }
}
