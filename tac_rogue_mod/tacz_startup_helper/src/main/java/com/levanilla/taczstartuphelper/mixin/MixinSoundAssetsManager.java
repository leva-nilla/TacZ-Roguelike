package com.levanilla.taczstartuphelper.mixin;

import com.google.common.collect.Maps;
import com.levanilla.taczstartuphelper.ClientPrewarmManager;
import com.levanilla.taczstartuphelper.StartupTimer;
import com.levanilla.taczstartuphelper.TaczStartupHelper;
import com.mojang.blaze3d.audio.OggAudioStream;
import com.tacz.guns.client.resource.manager.SoundAssetsManager;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Mixin(value = SoundAssetsManager.class, remap = false)
public class MixinSoundAssetsManager {
    @Shadow
    @Final
    private Map<ResourceLocation, SoundAssetsManager.SoundData> dataMap;

    @Shadow
    @Final
    private FileToIdConverter filetoidconverter;

    @Unique
    private final ThreadLocal<Long> taczStartupHelper$soundStart = new ThreadLocal<>();

    @Unique
    private volatile Map<ResourceLocation, ResourceLocation> taczStartupHelper$preparedSoundIndex = Collections.emptyMap();

    @Unique
    private volatile Map<ResourceLocation, ResourceLocation> taczStartupHelper$activeSoundIndex = Collections.emptyMap();

    @Unique
    private volatile ResourceManager taczStartupHelper$resourceManager;

    @Unique
    private final ConcurrentMap<ResourceLocation, Object> taczStartupHelper$soundLocks = new ConcurrentHashMap<>();

    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true)
    private void taczStartupHelper$profileSoundPrepareStart(ResourceManager manager, ProfilerFiller profiler, CallbackInfoReturnable<Map<ResourceLocation, SoundAssetsManager.SoundData>> cir) {
        long start = StartupTimer.start();
        taczStartupHelper$soundStart.set(start);
        Map<ResourceLocation, ResourceLocation> index = Maps.newHashMap();
        for (Map.Entry<ResourceLocation, Resource> entry : filetoidconverter.listMatchingResources(manager).entrySet()) {
            ResourceLocation file = entry.getKey();
            ResourceLocation id = filetoidconverter.fileToId(file);
            index.put(id, file);
        }
        taczStartupHelper$preparedSoundIndex = index;
        StartupTimer.log(TaczStartupHelper.LOGGER, "SoundAssetsManager.prepare.lazyIndex", start, index.size() + " sounds indexed");
        taczStartupHelper$soundStart.remove();
        cir.setReturnValue(Collections.emptyMap());
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void taczStartupHelper$profileSoundPrepareEnd(ResourceManager manager, ProfilerFiller profiler, CallbackInfoReturnable<Map<ResourceLocation, SoundAssetsManager.SoundData>> cir) {
        long start = taczStartupHelper$soundStart.get() != null ? taczStartupHelper$soundStart.get() : StartupTimer.start();
        Map<ResourceLocation, SoundAssetsManager.SoundData> result = cir.getReturnValue();
        int indexed = taczStartupHelper$preparedSoundIndex != null ? taczStartupHelper$preparedSoundIndex.size() : 0;
        StartupTimer.log(TaczStartupHelper.LOGGER, "SoundAssetsManager.prepare", start,
            result != null ? result.size() + " eager sounds, " + indexed + " lazy sounds" : "null");
        taczStartupHelper$soundStart.remove();
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void taczStartupHelper$activateLazySoundIndex(Map<ResourceLocation, SoundAssetsManager.SoundData> object, ResourceManager manager, ProfilerFiller profiler, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        taczStartupHelper$resourceManager = manager;
        taczStartupHelper$activeSoundIndex = taczStartupHelper$preparedSoundIndex != null ? taczStartupHelper$preparedSoundIndex : Collections.emptyMap();
        taczStartupHelper$soundLocks.clear();
        ClientPrewarmManager.setSoundIds(taczStartupHelper$activeSoundIndex.keySet());
    }

    @Inject(method = "getData", at = @At("HEAD"), cancellable = true)
    private void taczStartupHelper$getLazySoundData(ResourceLocation id, CallbackInfoReturnable<SoundAssetsManager.SoundData> cir) {
        SoundAssetsManager.SoundData eager = dataMap.get(id);
        if (eager != null) {
            cir.setReturnValue(eager);
            return;
        }

        Map<ResourceLocation, ResourceLocation> index = taczStartupHelper$activeSoundIndex;
        ResourceLocation file = index.get(id);
        ResourceManager manager = taczStartupHelper$resourceManager;
        if (file == null || manager == null) {
            return;
        }

        SoundAssetsManager.SoundData loaded = taczStartupHelper$loadSound(id, file, manager);
        cir.setReturnValue(loaded);
    }

    @Unique
    private SoundAssetsManager.SoundData taczStartupHelper$loadSound(ResourceLocation id, ResourceLocation file, ResourceManager manager) {
        Object lock = taczStartupHelper$soundLocks.computeIfAbsent(id, ignored -> new Object());
        synchronized (lock) {
            SoundAssetsManager.SoundData cached = dataMap.get(id);
            if (cached != null) {
                return cached;
            }
            Optional<Resource> resource = manager.getResource(file);
            if (resource.isEmpty()) {
                TaczStartupHelper.LOGGER.warn("{} missing lazy sound resource {}", TaczStartupHelper.PREFIX, file);
                return null;
            }
            long start = StartupTimer.start();
            try (InputStream stream = resource.get().open(); OggAudioStream audioStream = new OggAudioStream(stream)) {
                ByteBuffer byteBuffer = audioStream.readAll();
                SoundAssetsManager.SoundData data = new SoundAssetsManager.SoundData(byteBuffer, audioStream.getFormat());
                dataMap.put(id, data);
                ClientPrewarmManager.markSoundLoaded(id);
                StartupTimer.log(TaczStartupHelper.LOGGER, "SoundAssetsManager.lazyDecode", start, id.toString());
                return data;
            } catch (IOException e) {
                TaczStartupHelper.LOGGER.warn("{} failed to lazy decode sound {} from {}", TaczStartupHelper.PREFIX, id, file, e);
                return null;
            }
        }
    }
}
