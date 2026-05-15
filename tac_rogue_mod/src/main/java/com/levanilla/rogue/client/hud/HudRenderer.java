package com.levanilla.rogue.client.hud;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.StaminaManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * カスタムHUD描画 — HP/スタミナ/ゴールド/階層表示。
 */
public final class HudRenderer {

    private HudRenderer() {}

    private static int lastFloor = 0;
    private static boolean lastFloorCleared = false;
    private static int victoryTicks = 0;
    private static float displayedHealthRatio = 1.0f;
    private static boolean displayedHealthInitialized = false;

    public static void tickVictory() {
        if (victoryTicks > 0) victoryTicks--;
        int currentFloor = RunManager.getCurrentFloor();
        boolean cleared = RunManager.isFloorCleared();
        if (cleared && !lastFloorCleared && currentFloor > 0) {
            victoryTicks = 100;
        }
        lastFloor = currentFloor;
        lastFloorCleared = cleared;
    }

    public static void render(GuiGraphics graphics, Minecraft mc, Player player, int screenWidth, int screenHeight) {
        boolean inLobby = mc.level != null && mc.level.dimension().location().getPath().contains("lobby");
        if (!RunManager.isRunActive() && !inLobby) {
            lastFloor = 0;
            lastFloorCleared = false;
            displayedHealthInitialized = false;
            return;
        }

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float absorptionAmount = player.getAbsorptionAmount();
        float totalHealth = health + absorptionAmount;
        float targetHealthRatio = maxHealth > 0.0f ? Math.max(0.0f, totalHealth / maxHealth) : 0.0f;
        float syncedHealthOverride = com.levanilla.rogue.core.ClientRunState.getHealthRatioOverride();
        if (!Float.isNaN(syncedHealthOverride)) {
            targetHealthRatio = syncedHealthOverride;
        }
        if (!displayedHealthInitialized) {
            displayedHealthRatio = targetHealthRatio;
            displayedHealthInitialized = true;
        } else {
            displayedHealthRatio = smooth(displayedHealthRatio, targetHealthRatio, 0.18f);
        }
        float healthRatio = displayedHealthRatio;

        int currentFloor = RunManager.getCurrentFloor();
        String theme = RunManager.getCurrentThemeName();
        boolean isBoss = currentFloor > 0 && com.levanilla.rogue.world.ThemeManager.isBossFloor(currentFloor);

        // --- フロアクリア演出 ---
        if (victoryTicks > 0) {
            float alpha = Math.min(1.0f, victoryTicks / 20.0f);
            int color = ((int)(alpha * 255) << 24) | 0x00FF88;
            graphics.drawCenteredString(mc.font, "FLOOR CLEARED", screenWidth / 2, screenHeight / 4, color);
            graphics.drawCenteredString(mc.font, "PROCEED TO NEXT LEVEL", screenWidth / 2, screenHeight / 4 + 12, color & 0xAAFFFFFF);
        }

        // --- ボス戦の体力バー ---
        if (isBoss) BossBarRenderer.render(graphics, mc, screenWidth);

        // スタミナバー — クライアント同期キャッシュから取得
        StaminaManager.updateClientDisplay();
        float stamina = StaminaManager.getClientDisplayedStamina();
        float maxStamina = StaminaManager.getClientDisplayedMaxStamina();
        float staminaRatio = maxStamina > 0 ? Math.max(0.0f, Math.min(1.0f, stamina / maxStamina)) : 0.0f;
        boolean staminaExhausted = StaminaManager.isClientExhausted();

        int gold = RunManager.getClientGold();
        StatusData data = new StatusData(currentFloor, theme, isBoss, healthRatio, targetHealthRatio, staminaRatio, staminaExhausted, gold);
        HudSettings.Bounds bounds = HudSettings.getBounds(screenWidth, screenHeight);
        drawStatusPanel(graphics, mc, bounds.x, bounds.y, HudSettings.getScale(),
            HudSettings.getStyle(), HudSettings.getOpacity(), data);
    }

    public static void renderPreview(GuiGraphics graphics, Minecraft mc, int x, int y,
                                     float scale, HudSettings.HudStyle style, float opacity) {
        StatusData data = new StatusData(7, "URBAN RAID", false, 0.78f, 0.78f, 0.62f, false, 1240);
        drawStatusPanel(graphics, mc, x, y, scale, style, opacity, data);
    }

    public static HudSettings.Bounds getHudBounds(int screenWidth, int screenHeight) {
        return HudSettings.getBounds(screenWidth, screenHeight);
    }

    private static void drawStatusPanel(GuiGraphics graphics, Minecraft mc, int x, int y, float scale,
                                        HudSettings.HudStyle style, float opacity, StatusData data) {
        int panelW = HudSettings.baseWidth(style);
        int panelH = HudSettings.baseHeight(style);
        int bg = argb(opacity * 0.88f, 0x07111F);
        int bgSoft = argb(opacity * 0.55f, 0x0C1822);
        int accent = data.isBoss ? 0xFFFF5555 : 0xFF55DDAA;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        switch (style) {
            case COMPACT -> renderCompact(graphics, mc, panelW, panelH, bg, bgSoft, accent, data);
            case MINIMAL -> renderMinimal(graphics, mc, panelW, panelH, bgSoft, accent, data);
            case TACTICAL -> renderTactical(graphics, mc, panelW, panelH, bg, bgSoft, accent, data);
        }

        graphics.pose().popPose();
    }

    private static void renderTactical(GuiGraphics graphics, Minecraft mc, int panelW, int panelH,
                                       int bg, int bgSoft, int accent, StatusData data) {
        graphics.fill(0, 0, panelW, panelH, bg);
        graphics.fill(0, 0, 2, panelH, accent);
        graphics.fill(2, 0, panelW, 1, argb(0.65f, accent & 0xFFFFFF));
        graphics.renderOutline(0, 0, panelW, panelH, argb(0.40f, accent & 0xFFFFFF));

        String floorText = "FLOOR " + String.format("%02d", data.floor);
        if (data.isBoss) floorText += " [EXTREME]";
        graphics.drawString(mc.font, trim(mc, floorText, panelW - 16), 8, 6, data.isBoss ? 0xFFFF7777 : 0xFFFFFFFF, false);
        graphics.drawString(mc.font, trim(mc, data.theme, panelW - 16), 8, 17, 0xFF8C99A6, false);

        renderBar(graphics, 8, 32, panelW - 16, 9, data.healthRatio, healthColor(data.healthTextRatio), "HP " + percent(data.healthTextRatio));
        renderBar(graphics, 8, 46, panelW - 16, 4, data.staminaRatio, staminaColor(data), "");
        graphics.drawString(mc.font, "$" + data.gold, 8, 55, 0xFFFFD166, false);
    }

    private static void renderCompact(GuiGraphics graphics, Minecraft mc, int panelW, int panelH,
                                      int bg, int bgSoft, int accent, StatusData data) {
        graphics.fill(0, 0, panelW, panelH, bg);
        graphics.fill(0, 0, panelW, 2, accent);
        graphics.renderOutline(0, 0, panelW, panelH, argb(0.35f, accent & 0xFFFFFF));

        String top = "F" + String.format("%02d", data.floor) + "  $" + data.gold;
        graphics.drawString(mc.font, trim(mc, top, panelW - 10), 5, 6, data.isBoss ? 0xFFFF7777 : 0xFFFFF2C6, false);
        graphics.drawString(mc.font, trim(mc, data.theme, panelW - 10), 5, 16, 0xFF8896A2, false);
        renderBar(graphics, 5, 29, panelW - 10, 7, data.healthRatio, healthColor(data.healthTextRatio), percent(data.healthTextRatio));
        renderBar(graphics, 5, 40, panelW - 10, 3, data.staminaRatio, staminaColor(data), "");
    }

    private static void renderMinimal(GuiGraphics graphics, Minecraft mc, int panelW, int panelH,
                                      int bg, int accent, StatusData data) {
        graphics.fill(0, 0, panelW, panelH, bg);
        graphics.fill(0, 0, 2, panelH, accent);
        String top = "F" + String.format("%02d", data.floor) + "  " + data.theme;
        graphics.drawString(mc.font, trim(mc, top, panelW - 10), 6, 5, 0xFFE9F0F4, false);
        renderBar(graphics, 6, 18, panelW - 12, 6, data.healthRatio, healthColor(data.healthTextRatio), percent(data.healthTextRatio));
        renderBar(graphics, 6, 29, panelW - 12, 3, data.staminaRatio, staminaColor(data), "");
        graphics.drawString(mc.font, "$" + data.gold, 6, 34, 0xFFFFD166, false);
    }

    private static void renderBar(GuiGraphics graphics, int x, int y, int width, int height,
                                  float ratio, int fillColor, String label) {
        float clamped = Math.max(0.0f, Math.min(1.0f, ratio));
        graphics.fill(x, y, x + width, y + height, 0x44FFFFFF);
        int fill = Math.round(width * clamped);
        if (fill > 0) {
            graphics.fill(x, y, x + fill, y + height, fillColor);
        }
        if (!label.isEmpty()) {
            graphics.drawCenteredString(Minecraft.getInstance().font, label, x + width / 2, y - 1, 0xFFFFFFFF);
        }
    }

    private static int healthColor(float ratio) {
        if (ratio > 1.0f) return 0xFFFFD700;
        if (ratio > 0.50f) return 0xFF00FF88;
        if (ratio > 0.25f) return 0xFFFFFF00;
        return 0xFFFF4444;
    }

    private static int staminaColor(StatusData data) {
        return data.staminaExhausted ? 0xFFFFB347 : 0xFF55DDAA;
    }

    private static String percent(float ratio) {
        return Math.round(ratio * 100.0f) + "%";
    }

    private static String trim(Minecraft mc, String value, int width) {
        if (mc.font.width(value) <= width) return value;
        return mc.font.plainSubstrByWidth(value, Math.max(0, width - mc.font.width("..."))) + "...";
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static float smooth(float current, float target, float alpha) {
        if (Math.abs(target - current) < 0.002f) return target;
        return current + (target - current) * alpha;
    }

    private static final class StatusData {
        final int floor;
        final String theme;
        final boolean isBoss;
        final float healthRatio;
        final float healthTextRatio;
        final float staminaRatio;
        final boolean staminaExhausted;
        final int gold;

        StatusData(int floor, String theme, boolean isBoss, float healthRatio, float healthTextRatio,
                   float staminaRatio, boolean staminaExhausted, int gold) {
            this.floor = floor;
            this.theme = theme == null ? "" : theme;
            this.isBoss = isBoss;
            this.healthRatio = healthRatio;
            this.healthTextRatio = healthTextRatio;
            this.staminaRatio = staminaRatio;
            this.staminaExhausted = staminaExhausted;
            this.gold = gold;
        }
    }
}
