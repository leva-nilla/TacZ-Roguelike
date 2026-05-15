package com.levanilla.rogue.mixin;

import com.levanilla.rogue.client.TacticalScreenStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MixinScreenDirtBackground {
    @Inject(method = "renderDirtBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private void tacRogue$renderDirtBackground(GuiGraphics graphics, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!TacticalScreenStyle.isWorldFlowScreen(screen)) return;
        TacticalScreenStyle.renderBackground(graphics, screen.width, screen.height);
        ci.cancel();
    }
}
