package com.levanilla.rogue.client;

import com.levanilla.rogue.TacRogue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

public class TacRogueTitleScreen extends Screen {
    private static final int BUTTON_COUNT = 6;
    private static boolean languagePromptQueued = false;

    public TacRogueTitleScreen() {
        super(Component.literal("LR-TAC ROGUELIKE"));
    }

    @Override
    protected void init() {
        super.init();
        ClientStartupAssist.ensureDefaultShaderConfig();
        Layout layout = layout(this.width, this.height);
        int x = layout.buttonX;
        int y = layout.buttonY;
        int buttonW = layout.buttonW;
        int buttonH = layout.buttonH;
        int gap = layout.gap;

        addRenderableWidget(Button.builder(Component.translatable("menu.singleplayer"),
            b -> this.minecraft.setScreen(new RogueWorldSelectScreen(this))).bounds(x, y, buttonW, buttonH).build());
        y += buttonH + gap;
        addRenderableWidget(Button.builder(Component.translatable("menu.multiplayer"),
            b -> this.minecraft.setScreen(new JoinMultiplayerScreen(this))).bounds(x, y, buttonW, buttonH).build());
        y += buttonH + gap;
        addRenderableWidget(Button.builder(Component.translatable("fml.menu.mods"),
            b -> this.minecraft.setScreen(new net.minecraftforge.client.gui.ModListScreen(this))).bounds(x, y, buttonW, buttonH).build());
        y += buttonH + gap;
        addRenderableWidget(Button.builder(Component.translatable("options.language"),
            b -> openLanguageScreen()).bounds(x, y, buttonW, buttonH).build());
        y += buttonH + gap;
        addRenderableWidget(Button.builder(Component.translatable("menu.options"),
            b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))).bounds(x, y, buttonW, buttonH).build());
        y += buttonH + gap;
        addRenderableWidget(Button.builder(Component.translatable("menu.quit"),
            b -> this.minecraft.stop()).bounds(x, y, buttonW, buttonH).build());

        if (!languagePromptQueued && !ClientPreferenceManager.hasSelectedLanguage()) {
            languagePromptQueued = true;
            Minecraft.getInstance().tell(() -> {
                if (Minecraft.getInstance().screen == this && !ClientPreferenceManager.hasSelectedLanguage()) {
                    Minecraft.getInstance().setScreen(new FirstLaunchLanguageScreen(this));
                }
            });
        }
    }

    private void openLanguageScreen() {
        ClientPreferenceManager.markLanguageSelected();
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.setScreen(new LanguageSelectScreen(this, mc.options, mc.getLanguageManager()));
        } catch (Throwable ignored) {
            mc.setScreen(new FirstLaunchLanguageScreen(this));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = layout(this.width, this.height);
        renderTacticalBackground(graphics, this.width, this.height);
        drawLeftCommandArea(graphics, this.width, this.height, layout);
        drawMenuPlate(graphics, layout);
        drawTitleBlock(graphics, this.font, layout);
        drawRecentRunPanel(graphics, this.font, layout);
        drawStartupAssist(graphics, this.font, layout);
        drawCorner(graphics, layout.margin, 18, true);
        drawCorner(graphics, this.width - layout.margin - 30, 18, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTacticalButtons(graphics, this.font);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawTacticalButtons(GuiGraphics graphics, Font font) {
        this.renderables.forEach(renderable -> {
            if (renderable instanceof Button button && button.visible) {
                drawTacticalButton(graphics, font, button);
            }
        });
    }

    private static void renderTacticalBackground(GuiGraphics graphics, int w, int h) {
        int horizon = Math.max(120, h * 2 / 5);
        int centerX = w / 2;
        int vanishingY = horizon + 18;
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

    private static void drawLeftCommandArea(GuiGraphics graphics, int w, int h, Layout layout) {
        int panelRight = layout.leftW;
        graphics.fill(0, 0, panelRight, h, 0xC804080A);
        graphics.fill(panelRight, 0, panelRight + 2, h, 0xAA54E7C4);
        graphics.fill(0, 0, panelRight, 2, 0x6654E7C4);
        graphics.fill(0, h - 32, panelRight, h, 0xB0020507);
        for (int y = 56; y < h - 42; y += 44) {
            graphics.fill(layout.margin, y, layout.margin + 30, y + 2, 0x5554E7C4);
        }
    }

    private static void drawMenuPlate(GuiGraphics graphics, Layout layout) {
        graphics.fill(layout.menuX - 8, layout.menuY - 8, layout.menuX + layout.menuW + 8, layout.menuY + layout.menuH + 8, 0x9E020608);
        graphics.fill(layout.menuX - 5, layout.menuY - 5, layout.menuX + layout.menuW + 5, layout.menuY + layout.menuH + 5, 0x990A1416);
        graphics.fill(layout.menuX - 5, layout.menuY - 5, layout.menuX + layout.menuW + 5, layout.menuY - 3, 0xAA54E7C4);
        graphics.fill(layout.menuX - 5, layout.menuY + layout.menuH + 3, layout.menuX + layout.menuW + 5, layout.menuY + layout.menuH + 5, 0x6654E7C4);
        graphics.fill(layout.menuX + 12, layout.menuY - 1, Math.min(layout.menuX + layout.menuW - 12, layout.menuX + 72), layout.menuY + 1, 0xCC54E7C4);
    }

    private static void drawTitleBlock(GuiGraphics graphics, Font font, Layout layout) {
        if (!layout.showTitle) return;
        int x = layout.titleX;
        int y = layout.titleY;
        int panelW = layout.titleW;
        int panelH = layout.titleH;
        graphics.fill(x - 10, y - 10, x + panelW + 10, y + panelH + 10, 0xB805090B);
        graphics.fill(x - 10, y - 10, x + panelW + 10, y - 8, 0xAA54E7C4);
        graphics.fill(x + Math.max(0, panelW - 110), y + panelH + 8, x + panelW + 10, y + panelH + 10, 0x99E6C76A);
        graphics.drawString(font, Component.literal(fitLabel(font, TacRogue.DISPLAY_NAME, panelW)), x, y, 0xFFE8FFF8, false);
        graphics.drawString(font, Component.literal(fitLabel(font, Component.translatable("gui.tac_rogue.title.status").getString(), panelW)), x, y + 18, 0xFF74DDBE, false);
        if (!layout.compactTitle) {
            graphics.drawString(font, Component.literal(fitLabel(font, Component.translatable("gui.tac_rogue.title.protocol").getString(), panelW)), x, y + 35, 0xFF8A98A0, false);
            graphics.drawString(font, Component.literal(fitLabel(font, TacRogue.displayNameWithVersion(), panelW)), x, y + 52, 0xFF708087, false);
        }
    }

    private static void drawRecentRunPanel(GuiGraphics graphics, Font font, Layout layout) {
        if (!layout.showRecent) return;
        int panelW = layout.recentW;
        int panelH = layout.recentH;
        int x = layout.recentX;
        int y = layout.recentY;
        TitleRunSummary.Snapshot summary = TitleRunSummary.snapshot();
        graphics.fill(x - 10, y - 10, x + panelW + 10, y + panelH + 10, 0xC805090B);
        graphics.fill(x - 10, y - 10, x - 8, y + panelH + 10, 0xAA54E7C4);
        graphics.fill(x - 10, y - 10, x + panelW + 10, y - 8, 0xAA54E7C4);
        graphics.fill(x + panelW - 50, y + panelH + 8, x + panelW + 10, y + panelH + 10, 0x88E6C76A);
        graphics.drawString(font, Component.translatable("gui.tac_rogue.title.recent_run"), x, y, 0xFFE8FFF8, false);
        int lineY = y + 20;
        drawDataLine(graphics, font, x, lineY, "WORLD", summary.world(), panelW);
        drawDataLine(graphics, font, x, lineY + 16, "FLOOR", summary.floor() + " / MAX " + summary.maxFloor(), panelW);
        drawDataLine(graphics, font, x, lineY + 32, "PERKS", Integer.toString(summary.perkCount()), panelW);
        if (!layout.compactRecent) {
            drawDataLine(graphics, font, x, lineY + 48, "GOLD", Integer.toString(summary.gold()), panelW);
            drawDataLine(graphics, font, x, lineY + 64, "THEME", summary.theme(), panelW);
            drawDataLine(graphics, font, x, lineY + 80, "STATUS", summary.active() ? "DEPLOYED" : "STANDBY", panelW);
        }
    }

    private static void drawStartupAssist(GuiGraphics graphics, Font font, Layout layout) {
        int x = layout.margin;
        int panelW = Math.max(120, layout.leftW - layout.margin * 2);
        if (panelW < 140 || layout.h < 260) return;
        int y = layout.h - 68;
        graphics.fill(x - 4, y - 5, x + panelW + 4, y + 35, 0x9A05090B);
        graphics.fill(x - 4, y - 5, x + panelW + 4, y - 3, 0x8854E7C4);
        graphics.drawString(font, Component.literal(fitLabel(font, "SHADER " + ClientStartupAssist.shaderStatusText(), panelW)), x, y, 0xFF74DDBE, false);
        graphics.drawString(font, Component.literal(fitLabel(font, ClientStartupAssist.memoryAdvice(), panelW)), x, y + 16, 0xFFB8C0CC, false);
    }

    private static void drawDataLine(GuiGraphics graphics, Font font, int x, int y, String label, String value, int panelW) {
        graphics.drawString(font, Component.literal(label), x, y, 0xFF74DDBE, false);
        String fitted = fitLabel(font, value, Math.max(40, panelW - 76));
        graphics.drawString(font, Component.literal(fitted), x + 74, y, 0xFFE4FFF8, false);
    }

    private static Layout layout(int w, int h) {
        int margin = clamp(w / 36, 8, 34);
        int leftW = clamp(w * 30 / 100, 160, 320);
        int rightX = leftW + margin;
        int rightW = Math.max(0, w - rightX - margin);

        int gap = clamp(h / 72, 3, 8);
        int pad = clamp(h / 32, 8, 18);
        int availableH = Math.max(96, h - margin * 2 - 12);
        int buttonH = clamp((availableH - pad * 2 - (BUTTON_COUNT - 1) * gap) / BUTTON_COUNT, 16, 24);
        int menuH = pad * 2 + BUTTON_COUNT * buttonH + (BUTTON_COUNT - 1) * gap;
        int menuW = clamp(leftW - margin * 2 - 14, 132, 260);
        int menuX = Math.max(margin, (leftW - menuW) / 2);
        int desiredMenuY = h < 320 ? h / 5 : h < 520 ? h / 4 : h / 3 - menuH / 3;
        int menuY = clamp(desiredMenuY, margin + 8, Math.max(margin + 8, h - menuH - margin - 8));
        int buttonX = menuX + pad;
        int buttonY = menuY + pad;
        int buttonW = Math.max(96, menuW - pad * 2);

        boolean showTitle = rightW >= 170 && h >= 220;
        boolean compactTitle = rightW < 340 || h < 380;
        int titleW = clamp(rightW * 62 / 100, 160, compactTitle ? 280 : 430);
        titleW = Math.min(titleW, Math.max(0, rightW - 10));
        int titleH = compactTitle ? 36 : 72;
        int titleX = w - margin - titleW;
        int titleY = clamp(margin + 18, 20, Math.max(20, h - titleH - margin));

        boolean compactRecent = rightW < 340 || h < 460;
        int recentH = compactRecent ? 68 : 132;
        int recentW = clamp(rightW * 54 / 100, 172, compactRecent ? 240 : 286);
        recentW = Math.min(recentW, Math.max(0, rightW - 12));
        int recentX = w - margin - recentW - 4;
        int recentY = h - recentH - margin - 28;
        int titleBottom = showTitle ? titleY + titleH + 20 : margin;
        boolean showRecent = rightW >= 190 && h >= 300 && recentY >= titleBottom + 16;

        return new Layout(
            w, h, margin, leftW,
            menuX, menuY, menuW, menuH,
            buttonX, buttonY, buttonW, buttonH, gap,
            showTitle, compactTitle, titleX, titleY, titleW, titleH,
            showRecent, compactRecent, recentX, recentY, recentW, recentH
        );
    }

    private static void drawTacticalButton(GuiGraphics graphics, Font font, Button button) {
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
        graphics.fill(x + 5, y + 4, x + 22, y + 6, border);
        graphics.fill(x + w - 22, y + h - 6, x + w - 5, y + h - 4, border);
        if (hot) {
            graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x2454E7C4);
        }
        String label = fitLabel(font, button.getMessage().getString(), w - 18);
        graphics.drawString(font, Component.literal(label), x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, text, false);
    }

    private static String fitLabel(Font font, String label, int maxWidth) {
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

    private static void drawCorner(GuiGraphics graphics, int x, int y, boolean left) {
        int c = 0xCC57E5C4;
        int hLen = 30;
        int vLen = 22;
        if (left) {
            graphics.fill(x, y, x + hLen, y + 2, c);
            graphics.fill(x, y, x + 2, y + vLen, c);
        } else {
            graphics.fill(x, y, x + hLen, y + 2, c);
            graphics.fill(x + hLen - 2, y, x + hLen, y + vLen, c);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Layout {
        final int w;
        final int h;
        final int margin;
        final int leftW;
        final int menuX;
        final int menuY;
        final int menuW;
        final int menuH;
        final int buttonX;
        final int buttonY;
        final int buttonW;
        final int buttonH;
        final int gap;
        final boolean showTitle;
        final boolean compactTitle;
        final int titleX;
        final int titleY;
        final int titleW;
        final int titleH;
        final boolean showRecent;
        final boolean compactRecent;
        final int recentX;
        final int recentY;
        final int recentW;
        final int recentH;

        Layout(int w, int h, int margin, int leftW,
               int menuX, int menuY, int menuW, int menuH,
               int buttonX, int buttonY, int buttonW, int buttonH, int gap,
               boolean showTitle, boolean compactTitle, int titleX, int titleY, int titleW, int titleH,
               boolean showRecent, boolean compactRecent, int recentX, int recentY, int recentW, int recentH) {
            this.w = w;
            this.h = h;
            this.margin = margin;
            this.leftW = leftW;
            this.menuX = menuX;
            this.menuY = menuY;
            this.menuW = menuW;
            this.menuH = menuH;
            this.buttonX = buttonX;
            this.buttonY = buttonY;
            this.buttonW = buttonW;
            this.buttonH = buttonH;
            this.gap = gap;
            this.showTitle = showTitle;
            this.compactTitle = compactTitle;
            this.titleX = titleX;
            this.titleY = titleY;
            this.titleW = titleW;
            this.titleH = titleH;
            this.showRecent = showRecent;
            this.compactRecent = compactRecent;
            this.recentX = recentX;
            this.recentY = recentY;
            this.recentW = recentW;
            this.recentH = recentH;
        }
    }
}
