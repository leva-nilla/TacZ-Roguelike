package com.levanilla.rogue.client;

import com.levanilla.rogue.client.compat.LeaWindsCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class IntelSettingsScreen extends Screen {
    private final Screen parent;

    public IntelSettingsScreen(Screen parent) {
        super(Component.translatable("gui.tac_rogue.intel_settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int panelW = Math.min(320, Math.max(230, this.width - 24));
        int x = this.width / 2 - panelW / 2;
        int y = Math.max(18, this.height / 2 - 102);
        int by = y + 42;
        int buttonW = panelW - 32;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.npc_menu.hud_settings"),
            b -> this.minecraft.setScreen(new HudSettingsScreen(this)))
            .bounds(x + 16, by, buttonW, 20).build());
        by += 24;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.npc_menu.popup_settings"),
            b -> this.minecraft.setScreen(new PopupSettingsScreen(this)))
            .bounds(x + 16, by, buttonW, 20).build());
        by += 24;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.npc_menu.quick_keys"),
            b -> this.minecraft.setScreen(new QuickKeybindScreen(this)))
            .bounds(x + 16, by, buttonW, 20).build());
        by += 24;

        this.addRenderableWidget(Button.builder(thirdPersonAdsLabel(), b -> {
            LeaWindsCompat.cycleScopeAdsMode();
            b.setMessage(thirdPersonAdsLabel());
        }).bounds(x + 16, by, buttonW, 20).build());
        by += 34;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.common.back"), b -> this.onClose())
            .bounds(x + panelW / 2 - 64, by, 128, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelW = Math.min(320, Math.max(230, this.width - 24));
        int panelH = 198;
        int x = this.width / 2 - panelW / 2;
        int y = Math.max(18, this.height / 2 - 102);
        graphics.fill(x, y, x + panelW, y + panelH, 0xE607111F);
        graphics.fill(x, y, x + panelW, y + 2, 0xFF55DDAA);
        graphics.renderOutline(x, y, panelW, panelH, 0xAA55DDAA);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.intel_settings.title"),
            this.width / 2, y + 12, 0xFFAAFFDD);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.intel_settings.subtitle"),
            this.width / 2, y + 25, 0xFF9BA8B8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component thirdPersonAdsLabel() {
        return Component.translatable("gui.tac_rogue.npc_menu.third_person_ads",
            Component.translatable(LeaWindsCompat.getScopeAdsModeTranslationKey()));
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
