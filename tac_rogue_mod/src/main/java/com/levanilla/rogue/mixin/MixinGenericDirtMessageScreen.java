package com.levanilla.rogue.mixin;

import com.levanilla.rogue.client.TacticalScreenStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericDirtMessageScreen.class)
public abstract class MixinGenericDirtMessageScreen extends Screen {
    protected MixinGenericDirtMessageScreen(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tacRogue$renderTacticalLoading(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                CallbackInfo ci) {
        TacticalScreenStyle.renderBackground(graphics, this.width, this.height);
        TacticalScreenStyle.drawCenteredStatus(graphics, this.font, this.width, this.height, this.title,
            Component.translatable("progress.working"), -1);
        super.render(graphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }
}
