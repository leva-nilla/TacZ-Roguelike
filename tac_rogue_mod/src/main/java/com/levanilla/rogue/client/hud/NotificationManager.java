package com.levanilla.rogue.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HUD通知システム — チャットの代わりに画面上部にメッセージを表示。
 */
public final class NotificationManager {

    private NotificationManager() {}

    private static final CopyOnWriteArrayList<HudNotification> notifications = new CopyOnWriteArrayList<>();

    private static class HudNotification {
        final String text;
        final int color;
        int ticksAlive;
        final int maxTicks;
        HudNotification(String text, int color, int durationTicks) {
            this.text = text; this.color = color;
            this.ticksAlive = 0; this.maxTicks = durationTicks;
        }
    }

    /** HUD通知を追加 */
    public static void add(String text, int color) {
        notifications.add(new HudNotification(text, color, 80));
        if (notifications.size() > 5) notifications.remove(0);
    }

    /** Tick処理 */
    public static void tick() {
        notifications.removeIf(n -> { n.ticksAlive++; return n.ticksAlive > n.maxTicks; });
    }

    /** 描画 */
    public static void render(GuiGraphics graphics, Minecraft mc, int width, int height) {
        if (notifications.isEmpty()) return;
        int baseY = height - 55;
        int idx = 0;
        for (HudNotification n : notifications) {
            float alpha = 1.0f;
            if (n.ticksAlive > n.maxTicks - 20) {
                alpha = (n.maxTicks - n.ticksAlive) / 20.0f;
            }
            int alphaInt = Math.max(4, (int)(alpha * 255));
            int color = (alphaInt << 24) | (n.color & 0xFFFFFF);
            int bgColor = (Math.max(4, alphaInt / 2) << 24) | 0x000000;
            String text = n.text;
            int textWidth = mc.font.width(text.replaceAll("§.", ""));
            int nx = (width - textWidth) / 2;
            int ny = baseY - idx * 12;
            graphics.fill(nx - 4, ny - 1, nx + textWidth + 4, ny + 10, bgColor);
            graphics.drawString(mc.font, text, nx, ny, color, false);
            idx++;
        }
    }
}
