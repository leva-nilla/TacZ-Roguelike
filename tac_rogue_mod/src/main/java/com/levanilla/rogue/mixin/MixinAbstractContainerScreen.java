package com.levanilla.rogue.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // Full screen dark overlay
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x44000000);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V", shift = At.Shift.AFTER))
    private void onRenderAfterBg(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // Custom tactical border
        int color = 0xAA00AAFF;
        graphics.fill(this.leftPos - 2, this.topPos - 2, this.leftPos + this.imageWidth + 2, this.topPos, color); // Top
        graphics.fill(this.leftPos - 2, this.topPos + this.imageHeight, this.leftPos + this.imageWidth + 2, this.topPos + this.imageHeight + 2, color); // Bottom
        graphics.fill(this.leftPos - 2, this.topPos, this.leftPos, this.topPos + this.imageHeight, color); // Left
        graphics.fill(this.leftPos + this.imageWidth, this.topPos, this.leftPos + this.imageWidth + 2, this.topPos + this.imageHeight, color); // Right
    }
}
