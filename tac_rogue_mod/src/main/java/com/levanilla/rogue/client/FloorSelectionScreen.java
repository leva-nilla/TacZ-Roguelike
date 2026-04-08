package com.levanilla.rogue.client;

import com.levanilla.rogue.networking.RogueActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.world.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FloorSelectionScreen extends Screen {

    private final int maxFloor;
    private final long worldSeed;
    private FloorSelectionList floorList;
    
    // UI layout constants
    private final int imageWidth = 220;
    private final int imageHeight = 166;
    private int leftPos;
    private int topPos;

    public FloorSelectionScreen(int maxFloor, long worldSeed) {
        super(Component.literal("Floor Selection"));
        this.maxFloor = maxFloor;
        this.worldSeed = worldSeed;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        int listX = this.leftPos - 100;
        int listY = this.topPos - 20;
        int listW = this.imageWidth + 200;
        int listH = this.imageHeight + 40;

        // Initialize the custom selection list for scrollable functionality
        this.floorList = new FloorSelectionList(this.minecraft, listW, listH, listY, listY + listH, 25);
        this.floorList.setLeftPos(listX);
        this.floorList.setRenderBackground(false);
        this.floorList.setRenderTopAndBottom(false);

        // Add entries for all floors up to maxReachedFloor
        int actualMax = Math.max(0, maxFloor);
        for (int i = 1; i <= actualMax; i++) {
            this.floorList.addFloorEntry(i);
        }

        this.addRenderableWidget(this.floorList);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render background dark overlay manually to avoid dirt background
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int x = this.leftPos;
        int y = this.topPos;

        // --- Cyberpunk-style background like RogueInventoryScreen ---
        graphics.fill(x - 110, y - 40, x + imageWidth + 110, y + imageHeight + 40, 0xAA001122);
        graphics.renderOutline(x - 110, y - 40, imageWidth + 220, imageHeight + 80, 0xAA00AAFF);
        for (int i = -100; i < imageWidth + 100; i += 20) graphics.fill(x + i, y - 35, x + i + 1, y + imageHeight + 35, 0x2200AAFF);
        for (int i = -30; i < imageHeight + 30; i += 20) graphics.fill(x - 105, y + i, x + imageWidth + 105, y + i + 1, 0x2200AAFF);

        // Title text
        graphics.drawCenteredString(this.font, "§b[ REPLAY SECURED FLOORS ]", this.width / 2, this.topPos - 30, 0xFFFFFF);

        if (maxFloor <= 0) {
            graphics.drawCenteredString(this.font, "§cNo secured floors available.", this.width / 2, this.topPos + 30, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "Clear a floor to unlock replay.", this.width / 2, this.topPos + 45, 0xAAAAAA);
        }

        // Render the scrollable list on top
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Inner class for the scrollable list ---
    class FloorSelectionList extends ObjectSelectionList<FloorSelectionList.FloorEntry> {

        public FloorSelectionList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
            super(minecraft, width, height, y0, y1, itemHeight);
        }

        public void addFloorEntry(int floor) {
            this.addEntry(new FloorEntry(floor));
        }

        @Override
        public int getRowWidth() {
            return this.width - 20; // Allow room for scrollbar
        }

        @Override
        public int getScrollbarPosition() {
            return this.x0 + this.width - 5;
        }

        // --- Individual item in the scrollable list ---
        class FloorEntry extends ObjectSelectionList.Entry<FloorEntry> {
            private final int floor;
            private final String themeName;

            public FloorEntry(int floor) {
                this.floor = floor;
                ThemeManager.ThemeInstance theme = ThemeManager.getThemeForFloor(floor, FloorSelectionScreen.this.worldSeed);
                this.themeName = theme.displayName;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
                // Background for each row
                int bgColor = isHovered ? 0x880055AA : 0x55002244;
                graphics.fill(left, top, left + width, top + height - 2, bgColor);

                // Floor text
                String floorText = "§eFloor " + floor;
                graphics.drawString(FloorSelectionScreen.this.font, floorText, left + 10, top + 5, 0xFFFFFF, true);

                // Theme / Info text
                int isBoss = ThemeManager.isBossFloor(floor) ? 0xFF5555 : 0xAAAAAA;
                String infoText = (ThemeManager.isBossFloor(floor) ? "§c[BOSS] " : "") + themeName;
                graphics.drawString(FloorSelectionScreen.this.font, infoText, left + 60, top + 5, isBoss, true);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) { // Left click
                    // Send packet to server
                    TacRogueNetworking.CHANNEL.sendToServer(new RogueActionMessage(RogueActionMessage.ActionType.GOTO_FLOOR, String.valueOf(this.floor)));
                    // Close the UI immediately
                    Minecraft.getInstance().setScreen(null);
                    return true;
                }
                return false;
            }
            
            @Override
            public Component getNarration() {
                return Component.literal("Floor " + floor);
            }
        }
    }
}
