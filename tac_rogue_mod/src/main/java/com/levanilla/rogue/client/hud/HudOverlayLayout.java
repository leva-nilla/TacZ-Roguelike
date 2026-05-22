package com.levanilla.rogue.client.hud;

import net.minecraft.client.Minecraft;

/**
 * HUD周辺に出る補助表示の配置だけをまとめる。
 * HUD本体、ポップアップ、生成/先読み表示が互いに同じ場所を取り合わないようにする。
 */
public final class HudOverlayLayout {
    private HudOverlayLayout() {}

    public static Bounds loadIndicatorBounds(Minecraft mc, int screenWidth, int screenHeight, int width, int height) {
        HudSettings.Bounds hud = HudRenderer.getHudBounds(screenWidth, screenHeight);
        int popupReserve = NotificationManager.getAboveHudPopupHeight(mc, screenWidth);
        int x = clamp(hud.x, 8, Math.max(8, screenWidth - width - 8));
        int y = hud.y - popupReserve - 8 - height;
        if (y < 8) {
            y = Math.min(Math.max(8, hud.y + hud.height + 6), Math.max(8, screenHeight - height - 8));
        }
        return new Bounds(x, y, width, height);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Bounds(int x, int y, int width, int height) {}
}
