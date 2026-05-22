package com.levanilla.rogue.client.hud;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.StaminaManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * カスタムHUD描画 — HP/スタミナ/ゴールド/階層表示。
 */
public final class HudRenderer {

    private HudRenderer() {}

    private static final java.util.List<GoldGainToast> goldGainToasts = new java.util.ArrayList<>();
    private static int lastFloor = 0;
    private static boolean lastFloorCleared = false;
    private static int victoryTicks = 0;
    private static float displayedHealthRatio = 1.0f;
    private static boolean displayedHealthInitialized = false;

    public static void tickVictory() {
        if (victoryTicks > 0) victoryTicks--;
        goldGainToasts.removeIf(GoldGainToast::isExpired);
        int currentFloor = RunManager.getCurrentFloor();
        boolean cleared = RunManager.isFloorCleared();
        if (cleared && !lastFloorCleared && currentFloor > 0) {
            victoryTicks = 100;
        }
        lastFloor = currentFloor;
        lastFloorCleared = cleared;
    }

    public static void render(GuiGraphics graphics, Minecraft mc, Player player, int screenWidth, int screenHeight) {
        boolean inTacRogueDim = mc.level != null && mc.level.dimension().location().getNamespace().equals("tac_rogue");
        boolean inLobby = mc.level != null && mc.level.dimension().location().getPath().contains("lobby");
        if (!RunManager.isRunActive() && !inLobby && !inTacRogueDim) {
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
        int armor = Math.max(0, Math.round((float) player.getAttributeValue(Attributes.ARMOR)));
        int deepCore = com.levanilla.rogue.core.ClientRunState.getDeepCore();
        StatusData data = new StatusData(currentFloor, theme, isBoss, healthRatio, targetHealthRatio,
            staminaRatio, staminaExhausted, gold, armor, deepCore);
        HudSettings.Bounds bounds = HudSettings.getBounds(screenWidth, screenHeight);
        drawStatusPanel(graphics, mc, bounds.x, bounds.y, HudSettings.getScale(),
            HudSettings.getStyle(), HudSettings.getOpacity(), data);
        renderGoldGainToasts(graphics, mc, bounds, HudSettings.getScale(), HudSettings.getStyle(), data);
        renderStaminaWarning(graphics, mc, screenWidth, screenHeight, data);
        renderEnemyDirection(graphics, mc, player, screenWidth, screenHeight);
    }

    public static void addGoldGain(int amount) {
        if (amount <= 0) return;
        goldGainToasts.add(new GoldGainToast(amount));
        while (goldGainToasts.size() > 5) {
            goldGainToasts.remove(0);
        }
    }

    public static void renderPreview(GuiGraphics graphics, Minecraft mc, int x, int y,
                                     float scale, HudSettings.HudStyle style, float opacity) {
        StatusData data = new StatusData(7, "URBAN RAID", false, 0.78f, 0.78f, 0.62f, false, 1240, 14, 0);
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
        String economy = "$" + data.gold + "  AR " + data.armor + (data.deepCore > 0 ? "  DC " + data.deepCore : "");
        graphics.drawString(mc.font, trim(mc, economy, panelW - 16), 8, 55, 0xFFFFD166, false);
    }

    private static void renderCompact(GuiGraphics graphics, Minecraft mc, int panelW, int panelH,
                                      int bg, int bgSoft, int accent, StatusData data) {
        graphics.fill(0, 0, panelW, panelH, bg);
        graphics.fill(0, 0, panelW, 2, accent);
        graphics.renderOutline(0, 0, panelW, panelH, argb(0.35f, accent & 0xFFFFFF));

        String top = "F" + String.format("%02d", data.floor) + "  $" + data.gold + "  A" + data.armor
            + (data.deepCore > 0 ? " D" + data.deepCore : "");
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
        String economy = "$" + data.gold + "  AR " + data.armor + (data.deepCore > 0 ? "  DC " + data.deepCore : "");
        graphics.drawString(mc.font, trim(mc, economy, panelW - 12), 6, 34, 0xFFFFD166, false);
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

    private static void renderGoldGainToasts(GuiGraphics graphics, Minecraft mc, HudSettings.Bounds bounds,
                                             float scale, HudSettings.HudStyle style, StatusData data) {
        GoldAnchor anchor = goldAnchor(mc, bounds, scale, style, data);
        long now = System.currentTimeMillis();
        for (int i = 0; i < goldGainToasts.size(); i++) {
            GoldGainToast toast = goldGainToasts.get(i);
            float progress = toast.progress(now);
            if (progress >= 1.0F) continue;
            int alpha = Math.max(0, Math.min(255, Math.round((1.0F - progress) * 255.0F)));
            int rise = Math.round(progress * 12.0F);
            String text = "+$" + toast.amount;
            int x = anchor.x;
            int y = Math.max(6, anchor.y - rise - i * 10);
            graphics.drawString(mc.font, text, x + 1, y + 1, (alpha << 24), false);
            graphics.drawString(mc.font, text, x, y, (alpha << 24) | 0xFFD166, false);
        }
    }

    private static GoldAnchor goldAnchor(Minecraft mc, HudSettings.Bounds bounds, float scale,
                                         HudSettings.HudStyle style, StatusData data) {
        int localX;
        int localY;
        switch (style) {
            case COMPACT -> {
                String beforeGold = "F" + String.format("%02d", data.floor) + "  ";
                localX = 5 + mc.font.width(beforeGold);
                localY = 6;
            }
            case MINIMAL -> {
                localX = 6;
                localY = 34;
            }
            case TACTICAL -> {
                localX = 8;
                localY = 55;
            }
            default -> {
                localX = 8;
                localY = 55;
            }
        }
        int x = bounds.x + Math.round(localX * scale);
        int y = Math.max(6, bounds.y + Math.round((localY - 11) * scale));
        return new GoldAnchor(x, y);
    }

    private static void renderStaminaWarning(GuiGraphics graphics, Minecraft mc, int screenWidth, int screenHeight, StatusData data) {
        if (!RunManager.isRunActive()) return;
        if (!data.staminaExhausted && data.staminaRatio > 0.18F) return;
        int cx = screenWidth / 2 + 13;
        int cy = screenHeight / 2 + 12;
        int color = data.staminaExhausted ? 0xFFFF8A30 : 0xFFFFD166;
        graphics.fill(cx - 3, cy, cx, cy + 3, 0xAA000000);
        graphics.fill(cx, cy - 3, cx + 3, cy, 0xAA000000);
        graphics.fill(cx, cy + 3, cx + 3, cy + 6, 0xAA000000);
        graphics.fill(cx + 3, cy, cx + 6, cy + 3, 0xAA000000);
        graphics.fill(cx - 2, cy, cx + 1, cy + 3, color);
        graphics.fill(cx + 1, cy - 3, cx + 4, cy, color);
        graphics.fill(cx + 1, cy + 3, cx + 4, cy + 6, color);
        graphics.fill(cx + 4, cy, cx + 7, cy + 3, color);
        graphics.drawString(mc.font, "!", cx + 1, cy - 3, 0xFF201000, false);
    }

    private static void renderEnemyDirection(GuiGraphics graphics, Minecraft mc, Player player, int screenWidth, int screenHeight) {
        if (!RunManager.isRunActive() || RunManager.isFloorCleared()) return;
        com.levanilla.rogue.core.ClientRunState.EnemyDirectionState state =
            com.levanilla.rogue.core.ClientRunState.getEnemyDirectionState();
        if (state == null) return;

        DirectionIndicator indicator = directionIndicator(player, state.dx(), state.dz());
        HotbarRenderer.Bounds hotbar = HotbarRenderer.bounds(screenWidth, screenHeight);
        int panelW = state.count() > 1 ? 48 : 40;
        int panelH = 30;
        int x = hotbar.right() + 8;
        if (x + panelW > screenWidth - 4) {
            x = Math.max(4, hotbar.x() - panelW - 8);
        }
        int y = hotbar.y() + Math.max(0, (hotbar.height() - panelH) / 2);
        if (x < 4 || x + panelW > screenWidth - 4) {
            x = Math.max(4, Math.min(screenWidth - panelW - 4, hotbar.right() - panelW));
            y = Math.max(4, hotbar.y() - panelH - 4);
        }
        int color = state.count() <= 1 ? 0xFFFFD166 : 0xFFFF7A4A;

        graphics.fill(x, y, x + panelW, y + panelH, 0x88000000);
        graphics.renderOutline(x, y, panelW, panelH, 0x66333333);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 5, y + 2, 0);
        graphics.pose().scale(1.45F, 1.45F, 1.0F);
        graphics.drawString(mc.font, indicator.arrow(), 0, 0, color, true);
        graphics.pose().popPose();
        graphics.drawString(mc.font, indicator.label(), x + 22, y + 5, color, false);
        if (state.count() > 1) {
            graphics.drawString(mc.font, String.valueOf(Math.min(99, state.count())), x + panelW - 14, y + 17, color, true);
        }
    }

    private static DirectionIndicator directionIndicator(Player player, double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.0001D) {
            return new DirectionIndicator("▲", "前");
        }
        double dirX = dx / length;
        double dirZ = dz / length;
        double yawRad = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        double forward = dirX * forwardX + dirZ * forwardZ;
        double right = dirX * rightX + dirZ * rightZ;
        if (Math.abs(forward) >= Math.abs(right)) {
            return forward >= 0.0D
                ? new DirectionIndicator("▲", "前")
                : new DirectionIndicator("▼", "後");
        }
        return right >= 0.0D
            ? new DirectionIndicator("▶", "右")
            : new DirectionIndicator("◀", "左");
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
        final int armor;
        final int deepCore;

        StatusData(int floor, String theme, boolean isBoss, float healthRatio, float healthTextRatio,
                   float staminaRatio, boolean staminaExhausted, int gold, int armor, int deepCore) {
            this.floor = floor;
            this.theme = theme == null ? "" : theme;
            this.isBoss = isBoss;
            this.healthRatio = healthRatio;
            this.healthTextRatio = healthTextRatio;
            this.staminaRatio = staminaRatio;
            this.staminaExhausted = staminaExhausted;
            this.gold = gold;
            this.armor = armor;
            this.deepCore = deepCore;
        }
    }

    private record GoldAnchor(int x, int y) {}

    private record DirectionIndicator(String arrow, String label) {}

    private static final class GoldGainToast {
        private static final long LIFETIME_MS = 1500L;
        final int amount;
        final long createdMs;

        GoldGainToast(int amount) {
            this.amount = amount;
            this.createdMs = System.currentTimeMillis();
        }

        boolean isExpired() {
            return progress(System.currentTimeMillis()) >= 1.0F;
        }

        float progress(long now) {
            return Math.max(0.0F, Math.min(1.0F, (now - this.createdMs) / (float) LIFETIME_MS));
        }
    }
}
