package com.levanilla.rogue.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FirstLaunchLanguageScreen extends Screen {
    private final Screen parent;

    public FirstLaunchLanguageScreen(Screen parent) {
        super(Component.translatable("gui.tac_rogue.language.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int buttonW = Math.min(220, this.width - 40);
        int x = this.width / 2 - buttonW / 2;
        int y = Math.max(92, this.height / 2 - 32);

        addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.language.japanese"), b -> chooseLanguage("ja_jp"))
            .bounds(x, y, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.language.english"), b -> chooseLanguage("en_us"))
            .bounds(x, y + 26, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.language.other"), b -> openVanillaLanguageScreen())
            .bounds(x, y + 52, buttonW, 20).build());
    }

    private void chooseLanguage(String code) {
        ClientPreferenceManager.applyLanguage(code);
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void openVanillaLanguageScreen() {
        ClientPreferenceManager.markLanguageSelected();
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.setScreen(new LanguageSelectScreen(this.parent, mc.options, mc.getLanguageManager()));
        } catch (Throwable ignored) {
            mc.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelW = Math.min(360, this.width - 28);
        int panelH = 142;
        int x = this.width / 2 - panelW / 2;
        int y = Math.max(36, this.height / 2 - 78);

        graphics.fill(0, 0, this.width, this.height, 0xA8000000);
        graphics.fill(x, y, x + panelW, y + panelH, 0xE6071118);
        graphics.renderOutline(x, y, panelW, panelH, 0xAA55DDAA);
        graphics.fill(x, y, x + panelW, y + 2, 0xFF55DDAA);
        graphics.fill(x, y + panelH - 2, x + panelW, y + panelH, 0x667A5C1C);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, y + 16, 0xFFAAFFDD);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.language.subtitle"),
            this.width / 2, y + 31, 0xFF8C99A6);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.language.other_hint"),
            this.width / 2, y + panelH - 16, 0xFF6F7B84);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
