package com.levanilla.rogue.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class MixinInventoryScreen {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // Inventory specific rendering can be added here
    }
}
