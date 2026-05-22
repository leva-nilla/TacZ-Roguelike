package com.levanilla.taczstartuphelper.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.levanilla.taczstartuphelper.StartupTimer;
import com.levanilla.taczstartuphelper.TaczStartupHelper;
import com.tacz.guns.util.ResourceScanner;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(value = ResourceScanner.class, remap = false)
public class MixinResourceScanner {
    @Inject(method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/google/gson/Gson;)Ljava/util/Map;", at = @At("HEAD"))
    private static void taczStartupHelper$scanStart(ResourceManager manager, FileToIdConverter converter, Gson gson, CallbackInfoReturnable<Map<ResourceLocation, JsonElement>> cir) {
        taczStartupHelper$start.set(StartupTimer.start());
    }

    @Inject(method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/google/gson/Gson;)Ljava/util/Map;", at = @At("RETURN"))
    private static void taczStartupHelper$scanEnd(ResourceManager manager, FileToIdConverter converter, Gson gson, CallbackInfoReturnable<Map<ResourceLocation, JsonElement>> cir) {
        long start = taczStartupHelper$start.get() != null ? taczStartupHelper$start.get() : StartupTimer.start();
        Map<ResourceLocation, JsonElement> result = cir.getReturnValue();
        StartupTimer.log(TaczStartupHelper.LOGGER, "ResourceScanner.scanDirectory", start, result != null ? result.size() + " jsons" : "null");
        taczStartupHelper$start.remove();
    }

    @Inject(method = "scanDirectoryAll", at = @At("HEAD"))
    private static void taczStartupHelper$scanAllStart(ResourceManager manager, FileToIdConverter converter, Gson gson, CallbackInfoReturnable<Map<ResourceLocation, List<JsonElement>>> cir) {
        taczStartupHelper$startAll.set(StartupTimer.start());
    }

    @Inject(method = "scanDirectoryAll", at = @At("RETURN"))
    private static void taczStartupHelper$scanAllEnd(ResourceManager manager, FileToIdConverter converter, Gson gson, CallbackInfoReturnable<Map<ResourceLocation, List<JsonElement>>> cir) {
        long start = taczStartupHelper$startAll.get() != null ? taczStartupHelper$startAll.get() : StartupTimer.start();
        Map<ResourceLocation, List<JsonElement>> result = cir.getReturnValue();
        int total = 0;
        if (result != null) {
            for (List<JsonElement> values : result.values()) {
                total += values.size();
            }
        }
        StartupTimer.log(TaczStartupHelper.LOGGER, "ResourceScanner.scanDirectoryAll", start, result != null ? result.size() + " keys / " + total + " jsons" : "null");
        taczStartupHelper$startAll.remove();
    }

    @Unique
    private static final ThreadLocal<Long> taczStartupHelper$start = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Long> taczStartupHelper$startAll = new ThreadLocal<>();
}
