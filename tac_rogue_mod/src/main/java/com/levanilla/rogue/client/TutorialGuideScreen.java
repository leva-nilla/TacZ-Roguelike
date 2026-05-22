package com.levanilla.rogue.client;

import com.levanilla.rogue.client.hud.TutorialGuideManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class TutorialGuideScreen extends Screen {
    private final Screen parent;
    private final List<TutorialGuideManager.GuideEntry> entries = TutorialGuideManager.guideEntries();
    private int selectedIndex = 0;

    public TutorialGuideScreen(Screen parent) {
        super(Component.translatable("gui.tac_rogue.guide.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int panelW = Math.min(520, Math.max(280, this.width - 24));
        int panelX = this.width / 2 - panelW / 2;
        int panelY = Math.max(12, this.height / 2 - 132);
        int tabX = panelX + 14;
        int tabY = panelY + 42;
        int tabW = Math.min(142, Math.max(104, panelW / 3));
        int tabH = 18;
        int rowStep = 20;

        for (int i = 0; i < entries.size(); i++) {
            final int index = i;
            this.addRenderableWidget(Button.builder(Component.translatable(entries.get(i).titleKey()), b -> {
                selectedIndex = index;
                this.init();
            }).bounds(tabX, tabY + i * rowStep, tabW, tabH).build());
        }

        int bottomY = panelY + 226;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.guide.restart_hud"), b -> {
            TutorialGuideManager.restartGuideForCurrentWorld();
            this.onClose();
        }).bounds(panelX + 14, bottomY, 154, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.common.back"), b -> this.onClose())
            .bounds(panelX + panelW - 96, bottomY, 82, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelW = Math.min(520, Math.max(280, this.width - 24));
        int panelH = 258;
        int panelX = this.width / 2 - panelW / 2;
        int panelY = Math.max(12, this.height / 2 - 132);
        int tabW = Math.min(142, Math.max(104, panelW / 3));
        int contentX = panelX + tabW + 26;
        int contentW = panelW - tabW - 42;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE607111F);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF55DDAA);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xAA55DDAA);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.guide.title"),
            this.width / 2, panelY + 12, 0xFFAAFFDD);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.guide.subtitle"),
            this.width / 2, panelY + 25, 0xFF9BA8B8);

        int selectedY = panelY + 42 + selectedIndex * 20;
        graphics.fill(panelX + 12, selectedY - 1, panelX + 16 + tabW, selectedY + 19, 0x3355DDAA);
        graphics.fill(panelX + 12, selectedY - 1, panelX + 14, selectedY + 19, 0xFF55DDAA);

        TutorialGuideManager.GuideEntry entry = entries.get(Math.max(0, Math.min(selectedIndex, entries.size() - 1)));
        graphics.drawString(this.font, Component.translatable(entry.titleKey()), contentX, panelY + 50, 0xFFFFD166, false);
        int y = panelY + 68;
        for (var line : this.font.split(Component.translatable(entry.bodyKey()), contentW)) {
            graphics.drawString(this.font, line, contentX, y, 0xFFE8FFF7, false);
            y += 10;
        }
        y += 10;
        for (var line : this.font.split(Component.translatable("gui.tac_rogue.guide.restart_hint"), contentW)) {
            graphics.drawString(this.font, line, contentX, y, 0xFF93A5B1, false);
            y += 10;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
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
