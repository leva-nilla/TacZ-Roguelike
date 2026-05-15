package com.levanilla.rogue.client;

import com.levanilla.rogue.client.hud.HudRenderer;
import com.levanilla.rogue.client.hud.HudSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HudSettingsScreen extends Screen {
    private final Screen parent;

    public HudSettingsScreen(Screen parent) {
        super(Component.translatable("gui.tac_rogue.hud_settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int panelW = 316;
        int x = this.width / 2 - panelW / 2;
        int y = this.height / 2 - 124;
        int by = y + 42;

        addSlider(x + 16, by, panelW - 32, t("gui.tac_rogue.settings.scale"), 0.70, 1.20,
            HudSettings.getScale(),
            v -> Math.round(v * 100.0) + "%",
            v -> HudSettings.setScale((float) v));
        by += 24;

        addSlider(x + 16, by, panelW - 32, t("gui.tac_rogue.settings.opacity"), 0.35, 1.00,
            HudSettings.getOpacity(),
            v -> Math.round(v * 100.0) + "%",
            v -> HudSettings.setOpacity((float) v));
        by += 28;

        this.addRenderableWidget(Button.builder(styleLabel(), b -> {
            HudSettings.cycleStyle();
            b.setMessage(styleLabel());
        }).bounds(x + 16, by, 138, 20).build());

        this.addRenderableWidget(Button.builder(positionLabel(), b -> {
            HudSettings.cyclePosition();
            b.setMessage(positionLabel());
        }).bounds(x + panelW - 154, by, 138, 20).build());
        by += 28;

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.tac_rogue.common.back"),
            b -> this.onClose())
            .bounds(x + panelW / 2 - 68, by + 106, 136, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelW = 316;
        int panelH = 258;
        int x = this.width / 2 - panelW / 2;
        int y = this.height / 2 - 132;
        graphics.fill(x, y, x + panelW, y + panelH, 0xE607111F);
        graphics.fill(x, y, x + panelW, y + 2, 0xFF55DDAA);
        graphics.renderOutline(x, y, panelW, panelH, 0xAA55DDAA);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.hud_settings.title"), this.width / 2, y + 12, 0xFFAAFFDD);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.hud_settings.subtitle"), this.width / 2, y + 25, 0xFF9BA8B8);

        int previewW = Math.round(HudSettings.baseWidth(HudSettings.getStyle()) * HudSettings.getScale());
        int previewX = this.width / 2 - previewW / 2;
        int previewY = y + 150;
        graphics.fill(x + 16, previewY - 9, x + panelW - 16, previewY + 68, 0x88040A10);
        graphics.renderOutline(x + 16, previewY - 9, panelW - 32, 77, 0x554A5B66);
        HudRenderer.renderPreview(graphics, this.minecraft, previewX, previewY,
            HudSettings.getScale(), HudSettings.getStyle(), HudSettings.getOpacity());

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component styleLabel() {
        return Component.translatable("gui.tac_rogue.hud_settings.style",
            Component.translatable("gui.tac_rogue.hud_settings.style." + HudSettings.getStyle().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private Component positionLabel() {
        return Component.translatable("gui.tac_rogue.settings.position",
            Component.translatable("gui.tac_rogue.hud_settings.position." + HudSettings.getPosition().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private static String t(String key) {
        return Component.translatable(key).getString();
    }

    private void addSlider(int x, int y, int width, String label, double min, double max, double current,
                           SliderFormatter formatter, java.util.function.DoubleConsumer setter) {
        double normalized = (current - min) / (max - min);
        this.addRenderableWidget(new HudSlider(x, y, width, 20, label, min, max, normalized, formatter, setter));
    }

    private interface SliderFormatter {
        String format(double value);
    }

    private static class HudSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final SliderFormatter formatter;
        private final java.util.function.DoubleConsumer setter;

        HudSlider(int x, int y, int width, int height, String label, double min, double max, double value,
                  SliderFormatter formatter, java.util.function.DoubleConsumer setter) {
            super(x, y, width, height, Component.empty(), Math.max(0.0, Math.min(1.0, value)));
            this.label = label;
            this.min = min;
            this.max = max;
            this.formatter = formatter;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double actual = min + (max - min) * this.value;
            this.setMessage(Component.literal(label + ": " + formatter.format(actual)));
        }

        @Override
        protected void applyValue() {
            double actual = min + (max - min) * this.value;
            setter.accept(actual);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
