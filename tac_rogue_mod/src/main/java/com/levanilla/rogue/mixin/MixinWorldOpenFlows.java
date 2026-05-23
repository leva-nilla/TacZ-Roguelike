package com.levanilla.rogue.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
public abstract class MixinWorldOpenFlows {
    @Inject(method = "confirmWorldCreation", at = @At("HEAD"), cancellable = true)
    private static void tacRogue$skipExperimentalWorldWarning(Minecraft minecraft, CreateWorldScreen screen,
                                                              Lifecycle lifecycle, Runnable createWorld,
                                                              boolean skipWarning, CallbackInfo ci) {
        createWorld.run();
        ci.cancel();
    }
}
