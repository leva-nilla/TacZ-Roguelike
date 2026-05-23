package com.levanilla.rogue.mixin;

import com.levanilla.rogue.client.TacticalScreenStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(ProgressScreen.class)
public abstract class MixinProgressScreen extends Screen {
    @Shadow @Nullable private Component header;
    @Shadow @Nullable private Component stage;
    @Shadow private int progress;
    @Shadow private boolean stop;
    @Shadow @Final private boolean clearScreenAfterStop;

    protected MixinProgressScreen(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tacRogue$renderTacticalProgress(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                 CallbackInfo ci) {
        if (this.stop) {
            if (this.clearScreenAfterStop) {
                this.minecraft.setScreen(null);
            }
            ci.cancel();
            return;
        }
        TacticalScreenStyle.renderBackground(graphics, this.width, this.height);
        Component title = this.header == null ? Component.translatable("progress.working") : this.header;
        TacticalScreenStyle.drawCenteredStatus(graphics, this.font, this.width, this.height, title, this.stage,
            this.progress <= 0 ? -1 : this.progress);
        super.render(graphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }
}
