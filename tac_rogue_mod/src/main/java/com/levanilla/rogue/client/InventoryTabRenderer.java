package com.levanilla.rogue.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class InventoryTabRenderer {
    private InventoryTabRenderer() {}

    static void renderOperatorPanel(RogueInventoryScreen screen, GuiGraphics graphics,
                                    net.minecraft.client.player.LocalPlayer player, int mouseX, int mouseY, int x, int y) {
        int panelX = x - 95;
        int panelY = y + 5;
        int panelW = 80;
        int panelH = 130;
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0x88001133);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xAA00CCFF);
        graphics.drawString(screen.rogueFont(), "\u00A7b\u2605 OPERATOR", panelX + 4, panelY + 3, 0xFF00AAFF, false);

        if (player != null) {
            int entityX = panelX + panelW / 2;
            int entityY = panelY + panelH - 10;
            float scale = 38.0f;
            float lookX = entityX - mouseX;
            float lookY = (entityY - 60) - mouseY;
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics, entityX, entityY, (int) scale, lookX, lookY, player);
        }
    }

    static void renderBackground(RogueInventoryScreen screen, GuiGraphics g) {
        int bx = screen.leftPos();
        int by = screen.topPos();

        g.fill(bx, by, bx + screen.rogueImageWidth(), by + screen.rogueImageHeight(), 0x88001122);
        g.fill(bx + 8, by + 18, bx + 88, by + 62, 0x33100000);
        g.renderOutline(bx + 8, by + 18, 80, 44, 0x66AA5533);
        g.fill(bx + 94, by + 18, bx + 164, by + 88, 0x33102010);
        g.renderOutline(bx + 94, by + 18, 70, 70, 0x6644AA55);
        g.fill(bx + 168, by + 18, bx + 218, by + 68, 0x33303010);
        g.renderOutline(bx + 168, by + 18, 50, 50, 0x66AAAA44);
        g.fill(bx + 8, by + 104, bx + 220, by + 160, 0x33202028);
        g.renderOutline(bx + 8, by + 104, 212, 56, 0x66556688);

        for (int i = 0; i < screen.rogueMenu().slots.size(); i++) {
            if (i <= 8 || i == 45) continue;
            net.minecraft.world.inventory.Slot slot = screen.rogueMenu().slots.get(i);
            int sx = bx + slot.x - 1;
            int sy = by + slot.y - 1;
            int bgColor;
            String label = null;

            if (i >= 36 && i <= 37) {
                bgColor = 0x55FF4422;
                if (i == 36) label = "\u00A7cG1";
                else label = "\u00A7cG2";
            } else if (i == 38) {
                bgColor = 0x552244FF;
                label = "\u00A79M";
            } else if ((i >= 39 && i <= 44) || (i >= 9 && i <= 11)) {
                bgColor = i <= 11 ? 0x6633AA66 : 0x5522FF22;
                if (i >= 9 && i <= 11) label = "\u00A7aE" + (i - 8);
            } else if (i >= 12 && i <= 15) {
                boolean isGun2 = (i >= 14);
                bgColor = isGun2 ? 0x5500AAFF : 0x55FFFF00;
                if (i == 12) label = "\u00A7eA1";
                else if (i == 14) label = "\u00A7bA2";
            } else if (i >= 16 && i <= 35) {
                net.minecraft.world.item.ItemStack stack = slot.getItem();
                boolean isLocked = stack.is(net.minecraft.world.item.Items.BARRIER)
                    && stack.hasTag() && stack.getOrCreateTag().getBoolean("rogue_item_locked");
                bgColor = isLocked ? 0x44330000 : 0x33FFFFFF;
            } else {
                bgColor = 0x22FFFFFF;
            }

            g.fill(sx, sy, sx + 18, sy + 18, bgColor);
            g.renderOutline(sx, sy, 18, 18, (bgColor & 0x00FFFFFF) | 0x66000000);

            if (label != null) {
                g.pose().pushPose();
                g.pose().translate(sx + 1, sy + 1, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                g.drawString(screen.rogueFont(), label, 0, 0, 0x88FFFFFF, false);
                g.pose().popPose();
            }
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.pose().scale(0.6f, 0.6f, 1.0f);
        float invScale = 1.0f / 0.6f;
        g.drawString(screen.rogueFont(), Component.translatable("gui.tac_rogue.inventory.section.gun_melee"), (int)((bx + 12) * invScale), (int)((by + 22) * invScale), 0xAAFF7755, false);
        g.drawString(screen.rogueFont(), Component.translatable("gui.tac_rogue.inventory.section.items"), (int)((bx + 98) * invScale), (int)((by + 10) * invScale), 0xAA55DD77, false);
        g.drawString(screen.rogueFont(), Component.translatable("gui.tac_rogue.inventory.section.ammo"), (int)((bx + 172) * invScale), (int)((by + 10) * invScale), 0xAAAAA800, false);
        g.drawString(screen.rogueFont(), Component.translatable("gui.tac_rogue.inventory.section.backpack"), (int)((bx + 12) * invScale), (int)((by + 98) * invScale), 0xAAAAAAAA, false);
        g.pose().popPose();
    }
}
