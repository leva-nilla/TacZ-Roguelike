package com.levanilla.rogue.client;

import com.levanilla.rogue.mixin.SlotAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

public final class RogueContainerLayout {
    private RogueContainerLayout() {}

    public static int containerSlotCount(ChestMenu menu) {
        return Math.max(0, menu.getRowCount() * 9);
    }

    public static int menuSlotForPlayerInventory(ChestMenu menu, int playerInvSlot) {
        int firstPlayerSlot = containerSlotCount(menu);
        if (playerInvSlot >= 0 && playerInvSlot <= 8) {
            return firstPlayerSlot + 27 + playerInvSlot;
        }
        if (playerInvSlot >= 9 && playerInvSlot <= 35) {
            return firstPlayerSlot + (playerInvSlot - 9);
        }
        return -1;
    }

    public static void placeSlot(AbstractContainerMenu menu, int menuIndex, int x, int y) {
        if (menuIndex < 0 || menuIndex >= menu.slots.size()) return;
        Slot slot = menu.slots.get(menuIndex);
        SlotAccessor accessor = (SlotAccessor) slot;
        accessor.tacRogue$setX(x);
        accessor.tacRogue$setY(y);
    }

    public static void hideSlot(AbstractContainerMenu menu, int menuIndex) {
        placeSlot(menu, menuIndex, -1000, -1000);
    }

    public static void applyRoguePlayerSlots(ChestMenu menu, int yBase) {
        placeSlot(menu, menuSlotForPlayerInventory(menu, 0), 14, yBase + 12);
        placeSlot(menu, menuSlotForPlayerInventory(menu, 1), 36, yBase + 12);
        placeSlot(menu, menuSlotForPlayerInventory(menu, 2), 64, yBase + 12);

        int[] itemInvSlots = {3, 4, 5, 6, 7, 8, 9, 10, 11};
        for (int i = 0; i < itemInvSlots.length; i++) {
            placeSlot(menu, menuSlotForPlayerInventory(menu, itemInvSlots[i]),
                100 + (i % 3) * 20, yBase + 2 + (i / 3) * 20);
        }

        for (int i = 0; i < 4; i++) {
            placeSlot(menu, menuSlotForPlayerInventory(menu, 12 + i),
                174 + (i % 2) * 20, yBase + 2 + (i / 2) * 20);
        }

        for (int invSlot = 16; invSlot <= 35; invSlot++) {
            int offset = invSlot - 16;
            placeSlot(menu, menuSlotForPlayerInventory(menu, invSlot),
                14 + (offset % 10) * 20, yBase + 82 + (offset / 10) * 20);
        }
    }

    public static void drawRoguePlayerBackgrounds(GuiGraphics graphics, Font font, int left, int top, int yBase) {
        drawSection(graphics, left + 8, top + yBase, 80, 44, 0x33100000, 0x88AA5533,
            "GUN", font, 0xAAFF7755);
        drawSection(graphics, left + 94, top + yBase - 6, 70, 70, 0x33102010, 0x8844AA55,
            "ITEM", font, 0xAA55DD77);
        drawSection(graphics, left + 168, top + yBase - 6, 50, 50, 0x33303010, 0x88AAAA44,
            "AMMO", font, 0xAAAAA800);
        drawSection(graphics, left + 8, top + yBase + 76, 212, 56, 0x33202028, 0x88556688,
            "PACK", font, 0xAAAAAAAA);

        drawSlot(graphics, left + 14, top + yBase + 12, 0x55FF4422, "G1", font);
        drawSlot(graphics, left + 36, top + yBase + 12, 0x55FF4422, "G2", font);
        drawSlot(graphics, left + 64, top + yBase + 12, 0x552244FF, "M", font);

        int[] itemLabels = {3, 4, 5, 6, 7, 8, 1, 2, 3};
        for (int i = 0; i < 9; i++) {
            int sx = left + 100 + (i % 3) * 20;
            int sy = top + yBase + 2 + (i / 3) * 20;
            String label = i >= 6 ? "E" + itemLabels[i] : null;
            drawSlot(graphics, sx, sy, i >= 6 ? 0x6633AA66 : 0x5522FF22, label, font);
        }

        for (int i = 0; i < 4; i++) {
            int sx = left + 174 + (i % 2) * 20;
            int sy = top + yBase + 2 + (i / 2) * 20;
            String label = i == 0 ? "A1" : (i == 2 ? "A2" : null);
            drawSlot(graphics, sx, sy, i >= 2 ? 0x5500AAFF : 0x55FFFF00, label, font);
        }

        for (int i = 0; i < 20; i++) {
            int sx = left + 14 + (i % 10) * 20;
            int sy = top + yBase + 82 + (i / 10) * 20;
            drawSlot(graphics, sx, sy, 0x33FFFFFF, null, font);
        }
    }

    public static void drawSlot(GuiGraphics graphics, int x, int y, int color, String label, Font font) {
        graphics.fill(x - 1, y - 1, x + 19, y + 19, 0xAA000000);
        graphics.fill(x, y, x + 18, y + 18, color);
        graphics.renderOutline(x, y, 18, 18, (color & 0x00FFFFFF) | 0xAA000000);
        if (label != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 1, y + 1, 300);
            graphics.pose().scale(0.5f, 0.5f, 1.0f);
            graphics.drawString(font, label, 0, 0, 0xCCFFFFFF, false);
            graphics.pose().popPose();
        }
    }

    private static void drawSection(GuiGraphics graphics, int x, int y, int w, int h, int fill, int outline,
                                    String label, Font font, int labelColor) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.renderOutline(x, y, w, h, outline);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.pose().scale(0.45f, 0.45f, 1.0f);
        float inv = 1.0f / 0.45f;
        graphics.drawString(font, label, (int)((x + 4) * inv), (int)((y + 4) * inv), labelColor, false);
        graphics.pose().popPose();
    }
}
