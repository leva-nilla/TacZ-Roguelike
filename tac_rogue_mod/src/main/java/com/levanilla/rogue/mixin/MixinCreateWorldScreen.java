package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.DifficultyManager;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen extends net.minecraft.client.gui.screens.Screen {

    protected MixinCreateWorldScreen(Component title) {
        super(title);
    }

    @Shadow protected abstract void onCreate();

    @Inject(method = "init()V", at = @At("TAIL"))
    private void onInitTail(CallbackInfo ci) {
        this.clearWidgets();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 50;
        int btnWidth = 140;
        int btnX = centerX - 160;

        // Title
        // (rendered in render override below)

        // Difficulty buttons
        DifficultyManager.Difficulty[] diffs = DifficultyManager.Difficulty.values();
        for (int i = 0; i < diffs.length; i++) {
            DifficultyManager.Difficulty diff = diffs[i];
            boolean selected = diff == DifficultyManager.getDifficulty();
            String prefix = selected ? "\u00A7l\u00BB " : "  ";
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.literal(prefix + diff.displayName),
                (button) -> {
                    DifficultyManager.setDifficulty(diff.ordinal());
                    this.rebuildWidgets();
                }
            ).bounds(btnX, startY + i * 28, btnWidth, 20).build());
        }

        // Start button
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
            Component.translatable("gui.tac_rogue.difficulty.start"),
            (button) -> this.onCreate()
        ).bounds(centerX - 100, startY + diffs.length * 28 + 30, 200, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 50;

        // Title
        graphics.drawCenteredString(this.font, "\u00A7b\u00A7l[ ROGUELIKE MODE ]", centerX, startY - 35, 0xFF00FFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.difficulty.subtitle").getString(), centerX, startY - 22, 0xFF888888);

        // Difficulty descriptions (Right side)
        DifficultyManager.Difficulty current = DifficultyManager.getDifficulty();
        int descY = startY + 5;
        int textX = centerX + 10;
        String resolved = Component.translatable(current.description).getString();
        String[] descLines = resolved.split("\n");
        for (int i = 0; i < descLines.length; i++) {
            graphics.drawString(this.font, descLines[i], textX, descY + i * 14, 0xFF888888, false);
        }

        // Selected indicator
        graphics.drawString(this.font, "\u00A7e\u25B6", centerX - 172, startY + current.ordinal() * 28 + 6, 0xFFFFFF00, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
