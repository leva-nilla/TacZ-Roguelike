package com.levanilla.rogue.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TacticalScreenStyle {
    private TacticalScreenStyle() {}

    public static boolean isWorldFlowScreen(Screen screen) {
        return screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
            || screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
            || screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
            || screen instanceof net.minecraft.client.gui.screens.GenericDirtMessageScreen
            || screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen
            || screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
            || screen instanceof net.minecraft.client.gui.screens.ProgressScreen;
    }

    public static void renderBackground(GuiGraphics graphics, int w, int h) {
        int horizon = Math.max(110, h * 2 / 5);
        int centerX = w / 2;
        int vanishingY = horizon + 16;
        graphics.fill(0, 0, w, h, 0xFF05070A);
        graphics.fill(0, 0, w, horizon, 0xFF071016);
        graphics.fill(0, horizon, w, h, 0xFF0A0D0F);
        graphics.fill(0, horizon - 1, w, horizon + 1, 0xAA54E7C4);
        for (int x = -w; x <= w * 2; x += 44) {
            drawLine(graphics, x, h - 1, centerX + (x - centerX) / 5, vanishingY, 0x2254E7C4);
        }
        for (int y = horizon + 28; y < h; y += 34) {
            int alpha = clamp(120 - (y - horizon) / 5, 24, 110);
            graphics.fill(0, y, w, y + 1, (alpha << 24) | 0x0054E7C4);
        }
        for (int y = 0; y < h; y += 4) {
            graphics.fill(0, y, w, y + 1, 0x10000000);
        }
    }

    public static void drawHeader(GuiGraphics graphics, Font font, int w, int h, Component title, Component subtitle) {
        int margin = clamp(w / 36, 8, 34);
        int panelW = clamp(w * 44 / 100, 220, 520);
        int x = w - margin - panelW;
        int y = margin + 18;
        int panelH = subtitle == null ? 38 : 58;
        graphics.fill(x - 10, y - 10, x + panelW + 10, y + panelH + 10, 0xB805090B);
        graphics.fill(x - 10, y - 10, x + panelW + 10, y - 8, 0xAA54E7C4);
        graphics.fill(x + Math.max(0, panelW - 120), y + panelH + 8, x + panelW + 10, y + panelH + 10, 0x99E6C76A);
        String titleText = fitLabel(font, title.getString(), panelW);
        graphics.drawString(font, Component.literal(titleText), x, y, 0xFFE8FFF8, false);
        if (subtitle != null) {
            String subText = fitLabel(font, subtitle.getString(), panelW);
            graphics.drawString(font, Component.literal(subText), x, y + 20, 0xFF74DDBE, false);
        }
    }

    public static void drawPanel(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.fill(x1, y1, x2, y2, 0xB805090B);
        graphics.fill(x1, y1, x2, y1 + 2, 0xAA54E7C4);
        graphics.fill(x1, y2 - 2, x2, y2, 0x6654E7C4);
        graphics.fill(x1, y1, x1 + 2, y2, 0x8854E7C4);
    }

    public static void drawCenteredStatus(GuiGraphics graphics, Font font, int w, int h, Component title, Component body, int progress) {
        int panelW = clamp(w * 42 / 100, 260, 560);
        int panelH = progress >= 0 ? 112 : 86;
        int x = w / 2 - panelW / 2;
        int y = h / 2 - panelH / 2;
        drawPanel(graphics, x, y, x + panelW, y + panelH);
        drawFrame(graphics, x, y, x + panelW, y + panelH);

        String titleText = fitLabel(font, title.getString(), panelW - 36);
        graphics.drawString(font, Component.literal(titleText), x + 18, y + 18, 0xFFE8FFF8, false);
        if (body != null) {
            String bodyText = fitLabel(font, body.getString(), panelW - 36);
            graphics.drawString(font, Component.literal(bodyText), x + 18, y + 40, 0xFF74DDBE, false);
        }
        if (progress >= 0) {
            int barX = x + 18;
            int barY = y + panelH - 34;
            int barW = panelW - 36;
            int clamped = clamp(progress, 0, 100);
            graphics.fill(barX, barY, barX + barW, barY + 7, 0xFF020506);
            graphics.fill(barX + 1, barY + 1, barX + barW - 1, barY + 6, 0xFF10191B);
            graphics.fill(barX + 1, barY + 1, barX + 1 + (barW - 2) * clamped / 100, barY + 6, 0xCC54E7C4);
            graphics.drawString(font, Component.literal(clamped + "%"), barX + barW - font.width(clamped + "%"), barY + 12, 0xFF8A98A0, false);
        }
    }

    public static void drawFrame(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.fill(x1, y1, x2, y1 + 2, 0xCC54E7C4);
        graphics.fill(x1, y2 - 2, x2, y2, 0x8854E7C4);
        graphics.fill(x1, y1, x1 + 2, y2, 0x9954E7C4);
        graphics.fill(x2 - 2, y1, x2, y2, 0x4454E7C4);
        graphics.fill(x1 + 12, y1 + 14, Math.min(x2 - 12, x1 + 92), y1 + 16, 0xCC54E7C4);
        graphics.fill(Math.max(x1 + 12, x2 - 92), y2 - 16, x2 - 12, y2 - 14, 0x99E6C76A);
    }

    public static void drawWorldSelectOverlay(GuiGraphics graphics, Font font, int w, int h) {
        int margin = clamp(w / 36, 8, 34);
        int center = w / 2;
        int gap = clamp(w / 32, 18, 32);
        int listX = margin + clamp(w / 72, 4, 14);
        int listW = Math.max(170, center - gap - listX);
        int rightX = center + gap;
        int rightW = Math.max(0, w - rightX - margin - clamp(w / 72, 4, 14));
        int topY = clamp(h / 10, 26, 68);
        int bottomH = clamp(h / 6, 58, 112);
        int listY1 = topY + 42;
        int listY2 = Math.max(listY1 + 90, h - bottomH - 18);

        graphics.fill(0, 0, w, Math.min(h, topY + 32), 0x66000000);
        graphics.fill(0, Math.max(0, h - bottomH - 8), w, h, 0x96020507);
        drawFrame(graphics, listX - 12, listY1 - 10, listX + listW + 12, listY2 + 10);
        graphics.drawString(font, Component.translatable("gui.tac_rogue.world_select.archive"), listX, listY1 - 24, 0xFF74DDBE, false);
        if (rightW >= 150) {
            int headerH = h < 420 ? 42 : 58;
            int y = topY + 42;
            drawPanel(graphics, rightX, y, rightX + rightW, y + headerH);
            graphics.drawString(font, Component.translatable("gui.tac_rogue.world_select.header"), rightX + 12, y + 12, 0xFFE8FFF8, false);
            if (headerH > 44) {
                String subtitle = fitLabel(font, Component.translatable("gui.tac_rogue.world_select.subtitle").getString(), rightW - 24);
                graphics.drawString(font, Component.literal(subtitle), rightX + 12, y + 32, 0xFF74DDBE, false);
            }
        }
        graphics.drawString(font, Component.translatable("gui.tac_rogue.world_select.footer"), margin, h - 18, 0xFF708087, false);
    }

    public static void drawButton(GuiGraphics graphics, Font font, Button button) {
        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();
        boolean hot = button.active && button.isHoveredOrFocused();
        int border = button.active ? (hot ? 0xFF8FFFF0 : 0xDD54E7C4) : 0x88717A7D;
        int fill = button.active ? (hot ? 0xFF102C2E : 0xFF061114) : 0xFF17191B;
        int text = button.active ? (hot ? 0xFFFFFFFF : 0xFFE4FFF8) : 0xFF8B9398;
        graphics.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xE0010304);
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF020506);
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
        graphics.fill(x + 5, y + 4, x + Math.min(w - 5, 24), y + 6, border);
        graphics.fill(x + Math.max(5, w - 24), y + h - 6, x + w - 5, y + h - 4, border);
        if (hot) {
            graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x2454E7C4);
        }
        String label = fitLabel(font, button.getMessage().getString(), w - 18);
        graphics.drawString(font, Component.literal(label), x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, text, false);
    }

    public static String fitLabel(Font font, String label, int maxWidth) {
        if (font.width(label) <= maxWidth) return label;
        String suffix = "...";
        int bodyWidth = Math.max(0, maxWidth - font.width(suffix));
        return font.plainSubstrByWidth(label, bodyWidth) + suffix;
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = -Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        int x = x1;
        int y = y1;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
