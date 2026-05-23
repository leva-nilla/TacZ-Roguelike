package com.levanilla.rogue.mixin;

import com.levanilla.rogue.client.TacticalScreenStyle;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen extends Screen {
    @Shadow @Final private StoringChunkProgressListener progressListener;

    protected MixinLevelLoadingScreen() {
        super(GameNarrator.NO_TITLE);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tacRogue$renderTacticalLevelLoading(GuiGraphics graphics, int mouseX, int mouseY,
                                                     float partialTick, CallbackInfo ci) {
        int progress = Mth.clamp(this.progressListener.getProgress(), 0, 100);
        TacticalScreenStyle.renderBackground(graphics, this.width, this.height);

        int panelW = clamp(this.width * 44 / 100, 300, 620);
        int panelH = this.height < 360 ? 136 : 168;
        int x = this.width / 2 - panelW / 2;
        int y = this.height / 2 - panelH / 2;
        TacticalScreenStyle.drawPanel(graphics, x, y, x + panelW, y + panelH);
        TacticalScreenStyle.drawFrame(graphics, x, y, x + panelW, y + panelH);

        String title = TacticalScreenStyle.fitLabel(this.font,
            Component.translatable("gui.tac_rogue.loading.level.title").getString(), panelW - 36);
        graphics.drawString(this.font, Component.literal(title), x + 18, y + 18, 0xFFE8FFF8, false);
        String subtitle = TacticalScreenStyle.fitLabel(this.font,
            Component.translatable("gui.tac_rogue.loading.level.subtitle").getString(), panelW - 36);
        graphics.drawString(this.font, Component.literal(subtitle), x + 18, y + 38, 0xFF74DDBE, false);
        graphics.drawString(this.font, Component.literal(progress + "%"), x + panelW - 18 - this.font.width(progress + "%"),
            y + 18, 0xFFE6C76A, false);

        int gridCenterX = this.width / 2;
        int gridCenterY = y + (this.height < 360 ? 72 : 92);
        int gridPad = this.height < 360 ? 30 : 38;
        graphics.fill(gridCenterX - gridPad - 8, gridCenterY - gridPad - 8,
            gridCenterX + gridPad + 8, gridCenterY + gridPad + 8, 0xA0020506);
        graphics.fill(gridCenterX - gridPad - 8, gridCenterY - gridPad - 8,
            gridCenterX + gridPad + 8, gridCenterY - gridPad - 6, 0x9954E7C4);
        graphics.fill(gridCenterX - gridPad - 8, gridCenterY + gridPad + 6,
            gridCenterX + gridPad + 8, gridCenterY + gridPad + 8, 0x66E6C76A);
        LevelLoadingScreen.renderChunks(graphics, this.progressListener, gridCenterX, gridCenterY, 2, 1);
        graphics.fill(gridCenterX - 16, gridCenterY, gridCenterX + 16, gridCenterY + 1, 0x7754E7C4);
        graphics.fill(gridCenterX, gridCenterY - 16, gridCenterX + 1, gridCenterY + 16, 0x7754E7C4);

        int barX = x + 18;
        int barY = y + panelH - 32;
        int barW = panelW - 36;
        graphics.fill(barX, barY, barX + barW, barY + 7, 0xFF020506);
        graphics.fill(barX + 1, barY + 1, barX + barW - 1, barY + 6, 0xFF10191B);
        graphics.fill(barX + 1, barY + 1, barX + 1 + (barW - 2) * progress / 100, barY + 6, 0xCC54E7C4);
        String footer = TacticalScreenStyle.fitLabel(this.font,
            Component.translatable("gui.tac_rogue.loading.level.footer").getString(), barW);
        graphics.drawString(this.font, Component.literal(footer), barX, barY + 13, 0xFF8A98A0, false);
        ci.cancel();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
