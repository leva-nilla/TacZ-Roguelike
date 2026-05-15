package com.levanilla.rogue.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

public class RogueSupplyChestScreen extends AbstractContainerScreen<ChestMenu> {

    public RogueSupplyChestScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        int rows = Math.max(1, menu.getRowCount());
        this.imageWidth = 380;
        this.imageHeight = Math.max(220, 198 + rows * 20);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        layoutSlots();
    }

    private void layoutSlots() {
        int rows = this.menu.getRowCount();
        int containerSlots = RogueContainerLayout.containerSlotCount(this.menu);
        for (int i = 0; i < containerSlots; i++) {
            int row = i / 9;
            int col = i % 9;
            RogueContainerLayout.placeSlot(this.menu, i, 18 + col * 20, 34 + row * 20);
        }
        RogueContainerLayout.applyRoguePlayerSlots(this.menu, 58 + rows * 20);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        layoutSlots();
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;
        int rows = this.menu.getRowCount();

        graphics.fill(x - 5, y - 5, x + w + 5, y + h + 5, 0xF005090C);
        graphics.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF17202A);
        graphics.fill(x, y, x + w, y + h, 0xE80B1015);
        graphics.fill(x, y, x + w, y + 2, 0xFFE0B04B);
        graphics.fill(x, y + h - 2, x + w, y + h, 0x996B7C88);
        graphics.fill(x, y, x + 2, y + h, 0xFFE0B04B);

        String heading = isSupplyCache() ? "SUPPLY CACHE" : "FIELD CONTAINER";
        String sub = isSupplyCache() ? "PERSONAL LOOT INSTANCE" : "ROGUE INVENTORY LINK";
        graphics.drawString(this.font, heading, x + 12, y + 8, 0xFFFFD17A, false);
        graphics.drawString(this.font, sub, x + 12, y + 19, 0xFF7F8A92, false);

        int chestPanelX = x + 12;
        int chestPanelY = y + 30;
        int chestPanelW = 190;
        int chestPanelH = Math.max(30, rows * 20 + 8);
        graphics.fill(chestPanelX, chestPanelY, chestPanelX + chestPanelW, chestPanelY + chestPanelH, 0x661A1F24);
        graphics.renderOutline(chestPanelX, chestPanelY, chestPanelW, chestPanelH, 0xAA4A5B66);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + 18 + col * 20;
                int sy = y + 34 + row * 20;
                RogueContainerLayout.drawSlot(graphics, sx, sy, 0xFF10171D, null, this.font);
            }
        }

        int playerY = 58 + rows * 20;
        graphics.fill(x + 12, y + playerY - 8, x + w - 12, y + playerY - 7, 0xAAE0B04B);
        RogueContainerLayout.drawRoguePlayerBackgrounds(graphics, this.font, x, y, playerY);
        graphics.drawCenteredString(this.font, "TRANSFER RULES: ROGUE LOADOUT ACTIVE", x + w / 2, y + h - 11, 0xFF56626B);
    }

    private boolean isSupplyCache() {
        String title = this.title.getString().toUpperCase(java.util.Locale.ROOT);
        return title.contains("SUPPLY") || title.contains("CACHE") || title.contains("ROGUE");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
