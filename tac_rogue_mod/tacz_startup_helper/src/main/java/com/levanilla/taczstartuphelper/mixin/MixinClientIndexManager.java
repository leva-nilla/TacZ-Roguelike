package com.levanilla.taczstartuphelper.mixin;

import com.levanilla.taczstartuphelper.ClientPrewarmManager;
import com.levanilla.taczstartuphelper.StartupTimer;
import com.levanilla.taczstartuphelper.TaczStartupHelper;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = ClientIndexManager.class, remap = false)
public class MixinClientIndexManager {
    @Unique
    private static long taczStartupHelper$reloadStart;
    @Unique
    private static long taczStartupHelper$gunDisplayStart;
    @Unique
    private static long taczStartupHelper$gunIndexStart;
    @Unique
    private static long taczStartupHelper$ammoIndexStart;
    @Unique
    private static long taczStartupHelper$attachmentIndexStart;
    @Unique
    private static long taczStartupHelper$blockIndexStart;

    @Inject(method = "reload", at = @At("HEAD"))
    private static void taczStartupHelper$reloadStart(CallbackInfo ci) {
        taczStartupHelper$reloadStart = StartupTimer.start();
    }

    @Inject(method = "reload", at = @At("RETURN"))
    private static void taczStartupHelper$reloadEnd(CallbackInfo ci) {
        List<ResourceLocation> prioritySounds = new ArrayList<>();
        for (GunDisplayInstance display : ClientIndexManager.GUN_DISPLAY.values()) {
            taczStartupHelper$addSound(prioritySounds, display, SoundManager.SHOOT_SOUND);
            taczStartupHelper$addSound(prioritySounds, display, SoundManager.SHOOT_3P_SOUND);
            taczStartupHelper$addSound(prioritySounds, display, SoundManager.SILENCE_SOUND);
            taczStartupHelper$addSound(prioritySounds, display, SoundManager.SILENCE_3P_SOUND);
            taczStartupHelper$addSound(prioritySounds, display, SoundManager.RELOAD_EMPTY_SOUND);
            taczStartupHelper$addSound(prioritySounds, display, SoundManager.RELOAD_TACTICAL_SOUND);
            taczStartupHelper$addSound(prioritySounds, display, SoundManager.DRAW_SOUND);
            taczStartupHelper$addSound(prioritySounds, display, SoundManager.PUT_AWAY_SOUND);
            taczStartupHelper$addSound(prioritySounds, display, SoundManager.DRY_FIRE_SOUND);
        }
        ClientPrewarmManager.prioritizeSoundIds(prioritySounds);
        StartupTimer.log(TaczStartupHelper.LOGGER, "ClientIndexManager.reload", taczStartupHelper$reloadStart,
            ClientIndexManager.GUN_INDEX.size() + " guns, " + ClientIndexManager.ATTACHMENT_INDEX.size() + " attachments, "
                + prioritySounds.size() + " priority sounds");
    }

    @Unique
    private static void taczStartupHelper$addSound(List<ResourceLocation> sounds, GunDisplayInstance display, String name) {
        ResourceLocation sound = display.getSounds(name);
        if (sound != null) {
            sounds.add(sound);
        }
    }

    @Inject(method = "loadGunDisplay", at = @At("HEAD"))
    private static void taczStartupHelper$gunDisplayStart(CallbackInfo ci) {
        taczStartupHelper$gunDisplayStart = StartupTimer.start();
    }

    @Inject(method = "loadGunDisplay", at = @At("RETURN"))
    private static void taczStartupHelper$gunDisplayEnd(CallbackInfo ci) {
        StartupTimer.log(TaczStartupHelper.LOGGER, "ClientIndexManager.loadGunDisplay", taczStartupHelper$gunDisplayStart,
            ClientIndexManager.GUN_DISPLAY.size() + " displays");
    }

    @Inject(method = "loadGunIndex", at = @At("HEAD"))
    private static void taczStartupHelper$gunIndexStart(CallbackInfo ci) {
        taczStartupHelper$gunIndexStart = StartupTimer.start();
    }

    @Inject(method = "loadGunIndex", at = @At("RETURN"))
    private static void taczStartupHelper$gunIndexEnd(CallbackInfo ci) {
        StartupTimer.log(TaczStartupHelper.LOGGER, "ClientIndexManager.loadGunIndex", taczStartupHelper$gunIndexStart,
            ClientIndexManager.GUN_INDEX.size() + " guns");
    }

    @Inject(method = "loadAmmoIndex", at = @At("HEAD"))
    private static void taczStartupHelper$ammoIndexStart(CallbackInfo ci) {
        taczStartupHelper$ammoIndexStart = StartupTimer.start();
    }

    @Inject(method = "loadAmmoIndex", at = @At("RETURN"))
    private static void taczStartupHelper$ammoIndexEnd(CallbackInfo ci) {
        StartupTimer.log(TaczStartupHelper.LOGGER, "ClientIndexManager.loadAmmoIndex", taczStartupHelper$ammoIndexStart,
            ClientIndexManager.AMMO_INDEX.size() + " ammo");
    }

    @Inject(method = "loadAttachmentIndex", at = @At("HEAD"))
    private static void taczStartupHelper$attachmentIndexStart(CallbackInfo ci) {
        taczStartupHelper$attachmentIndexStart = StartupTimer.start();
    }

    @Inject(method = "loadAttachmentIndex", at = @At("RETURN"))
    private static void taczStartupHelper$attachmentIndexEnd(CallbackInfo ci) {
        StartupTimer.log(TaczStartupHelper.LOGGER, "ClientIndexManager.loadAttachmentIndex", taczStartupHelper$attachmentIndexStart,
            ClientIndexManager.ATTACHMENT_INDEX.size() + " attachments");
    }

    @Inject(method = "loadBlockIndex", at = @At("HEAD"))
    private static void taczStartupHelper$blockIndexStart(CallbackInfo ci) {
        taczStartupHelper$blockIndexStart = StartupTimer.start();
    }

    @Inject(method = "loadBlockIndex", at = @At("RETURN"))
    private static void taczStartupHelper$blockIndexEnd(CallbackInfo ci) {
        StartupTimer.log(TaczStartupHelper.LOGGER, "ClientIndexManager.loadBlockIndex", taczStartupHelper$blockIndexStart,
            ClientIndexManager.BLOCK_INDEX.size() + " blocks");
    }
}
