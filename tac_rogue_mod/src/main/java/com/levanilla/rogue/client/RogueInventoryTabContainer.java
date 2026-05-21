package com.levanilla.rogue.client;

import com.levanilla.rogue.core.RunManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class RogueInventoryTabContainer {
    private RogueInventoryTabContainer() {}

    static void renderChrome(RogueInventoryScreen screen, GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 110, y - 40, x + screen.rogueImageWidth() + 110, y + screen.rogueImageHeight() + 40, 0xAA001122);
        graphics.renderOutline(x - 110, y - 40, screen.rogueImageWidth() + 220, screen.rogueImageHeight() + 80, 0xAA00AAFF);
        for (int i = -100; i < screen.rogueImageWidth() + 100; i += 20) {
            graphics.fill(x + i, y - 35, x + i + 1, y + screen.rogueImageHeight() + 35, 0x2200AAFF);
        }
        for (int i = -30; i < screen.rogueImageHeight() + 30; i += 20) {
            graphics.fill(x - 105, y + i, x + screen.rogueImageWidth() + 105, y + i + 1, 0x2200AAFF);
        }
    }

    static void renderTitle(RogueInventoryScreen screen, GuiGraphics graphics, int x, int y) {
        Component statusText = RunManager.isRunActive() ?
            Component.translatable("gui.tac_rogue.inventory.operator_status", RunManager.getCurrentFloor()) :
            Component.translatable("gui.tac_rogue.inventory.base_command");
        graphics.drawString(screen.rogueFont(), statusText, x - 100, y - 55, 0xFF00FFFF, false);
    }

    static void renderTabHighlight(RogueInventoryScreen screen, GuiGraphics graphics) {
        int idx = screen.activeTab().ordinal();
        int[] widths = {65, 50, 50, 50, 50, 50};
        int[] xs = {screen.tabX() - 6, screen.tabX() + 61, screen.tabX() + 113, screen.tabX() + 165, screen.tabX() + 217, screen.tabX() + 269};
        int w = idx < widths.length ? widths[idx] : 50;
        int hx = idx < xs.length ? xs[idx] : screen.tabX();
        graphics.renderOutline(hx, screen.tabY() - 1, w, 17, 0xFFFFFF00);
    }
}
