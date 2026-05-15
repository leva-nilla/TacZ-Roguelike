package com.levanilla.rogue.client;

import com.levanilla.rogue.client.hud.NotificationManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PopupSettingsScreen extends Screen {
    private final Screen parent;

    public PopupSettingsScreen(Screen parent) {
        super(Component.translatable("gui.tac_rogue.popup_settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int panelW = 292;
        int x = this.width / 2 - panelW / 2;
        int y = this.height / 2 - 96;
        int by = y + 38;

        addSlider(x + 16, by, panelW - 32, t("gui.tac_rogue.settings.scale"), 0.70, 1.20,
            NotificationManager.getPopupScale(),
            v -> Math.round(v * 100.0) + "%",
            v -> NotificationManager.setPopupScale((float)v));
        by += 24;

        addSlider(x + 16, by, panelW - 32, t("gui.tac_rogue.popup_settings.slide_speed"), 4.0, 14.0,
            NotificationManager.getPopupSpeedTicks(),
            v -> Math.round(v) + " tick",
            v -> NotificationManager.setPopupSpeedTicks((int)Math.round(v)));
        by += 24;

        addSlider(x + 16, by, panelW - 32, t("gui.tac_rogue.popup_settings.duration"), 0.60, 1.50,
            NotificationManager.getDurationMultiplier(),
            v -> Math.round(v * 100.0) + "%",
            v -> NotificationManager.setDurationMultiplier((float)v));
        by += 24;

        addSlider(x + 16, by, panelW - 32, t("gui.tac_rogue.popup_settings.max_visible"), 1.0, 5.0,
            NotificationManager.getMaxVisible(),
            v -> String.valueOf(Math.round(v)),
            v -> NotificationManager.setMaxVisible((int)Math.round(v)));
        by += 28;

        this.addRenderableWidget(Button.builder(
            positionLabel(),
            b -> {
                NotificationManager.cyclePopupPosition();
                b.setMessage(positionLabel());
            })
            .bounds(x + 16, by, panelW - 32, 20).build());
        by += 28;

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.tac_rogue.popup_settings.preview"),
            b -> NotificationManager.addPreviewPopup())
            .bounds(x + 16, by, 126, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.tac_rogue.common.back"),
            b -> this.onClose())
            .bounds(x + panelW - 142, by, 126, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelW = 292;
        int panelH = 222;
        int x = this.width / 2 - panelW / 2;
        int y = this.height / 2 - 108;
        graphics.fill(x, y, x + panelW, y + panelH, 0xE607111F);
        graphics.fill(x, y, x + panelW, y + 2, 0xFF55DDAA);
        graphics.renderOutline(x, y, panelW, panelH, 0xAA55DDAA);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.popup_settings.title"), this.width / 2, y + 12, 0xFFAAFFDD);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.popup_settings.subtitle"), this.width / 2, y + 25, 0xFF9BA8B8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component positionLabel() {
        return Component.translatable("gui.tac_rogue.settings.position",
            Component.translatable("gui.tac_rogue.popup_settings.position." + NotificationManager.getPopupPosition().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private static String t(String key) {
        return Component.translatable(key).getString();
    }

    private void addSlider(int x, int y, int width, String label, double min, double max, double current,
                           SliderFormatter formatter, java.util.function.DoubleConsumer setter) {
        double normalized = (current - min) / (max - min);
        this.addRenderableWidget(new PopupSlider(x, y, width, 20, label, min, max, normalized, formatter, setter));
    }

    private interface SliderFormatter {
        String format(double value);
    }

    private static class PopupSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final SliderFormatter formatter;
        private final java.util.function.DoubleConsumer setter;

        PopupSlider(int x, int y, int width, int height, String label, double min, double max, double value,
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
