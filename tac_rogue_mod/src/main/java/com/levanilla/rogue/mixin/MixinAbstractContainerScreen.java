package com.levanilla.rogue.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen {

    @Unique private static Field tacRogue$leftPosField;
    @Unique private static Field tacRogue$topPosField;
    @Unique private static Field tacRogue$imageWidthField;
    @Unique private static Field tacRogue$imageHeightField;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // Full screen dark overlay
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x44000000);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V", shift = At.Shift.AFTER))
    private void onRenderAfterBg(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // Custom tactical border
        int color = 0xAA00AAFF;
        int imageWidth = tacRogue$imageWidth((AbstractContainerScreen<?>) (Object) this, 176);
        int imageHeight = tacRogue$imageHeight((AbstractContainerScreen<?>) (Object) this, 166);
        int leftPos = tacRogue$leftPos((AbstractContainerScreen<?>) (Object) this, (graphics.guiWidth() - imageWidth) / 2);
        int topPos = tacRogue$topPos((AbstractContainerScreen<?>) (Object) this, (graphics.guiHeight() - imageHeight) / 2);

        graphics.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos, color); // Top
        graphics.fill(leftPos - 2, topPos + imageHeight, leftPos + imageWidth + 2, topPos + imageHeight + 2, color); // Bottom
        graphics.fill(leftPos - 2, topPos, leftPos, topPos + imageHeight, color); // Left
        graphics.fill(leftPos + imageWidth, topPos, leftPos + imageWidth + 2, topPos + imageHeight, color); // Right
    }

    @Unique
    private static int tacRogue$leftPos(AbstractContainerScreen<?> screen, int fallback) {
        if (tacRogue$leftPosField == null) tacRogue$leftPosField = tacRogue$findField("leftPos", "f_97735_");
        return tacRogue$readInt(screen, tacRogue$leftPosField, fallback);
    }

    @Unique
    private static int tacRogue$topPos(AbstractContainerScreen<?> screen, int fallback) {
        if (tacRogue$topPosField == null) tacRogue$topPosField = tacRogue$findField("topPos", "f_97736_");
        return tacRogue$readInt(screen, tacRogue$topPosField, fallback);
    }

    @Unique
    private static int tacRogue$imageWidth(AbstractContainerScreen<?> screen, int fallback) {
        if (tacRogue$imageWidthField == null) tacRogue$imageWidthField = tacRogue$findField("imageWidth", "f_97726_");
        return tacRogue$readInt(screen, tacRogue$imageWidthField, fallback);
    }

    @Unique
    private static int tacRogue$imageHeight(AbstractContainerScreen<?> screen, int fallback) {
        if (tacRogue$imageHeightField == null) tacRogue$imageHeightField = tacRogue$findField("imageHeight", "f_97727_");
        return tacRogue$readInt(screen, tacRogue$imageHeightField, fallback);
    }

    @Unique
    private static Field tacRogue$findField(String mojmapName, String srgName) {
        for (String name : new String[] { mojmapName, srgName }) {
            Class<?> type = AbstractContainerScreen.class;
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
        }
        return null;
    }

    @Unique
    private static int tacRogue$readInt(AbstractContainerScreen<?> screen, Field field, int fallback) {
        if (field == null) return fallback;
        try {
            return field.getInt(screen);
        } catch (IllegalAccessException ignored) {
            return fallback;
        }
    }
}
