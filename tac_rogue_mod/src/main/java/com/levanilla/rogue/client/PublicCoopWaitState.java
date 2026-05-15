package com.levanilla.rogue.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PublicCoopWaitState {
    private static boolean active;
    private static int floor;
    private static int secondsLeft;
    private static long lastSyncMs;

    private PublicCoopWaitState() {}

    public static void update(int floorValue, int seconds) {
        active = true;
        floor = Math.max(1, floorValue);
        secondsLeft = Math.max(1, seconds);
        lastSyncMs = System.currentTimeMillis();
    }

    public static void clear() {
        active = false;
        secondsLeft = 0;
    }

    public static boolean isActive() {
        return active && getSecondsLeft() > 0;
    }

    public static int getFloor() {
        return floor;
    }

    public static int getSecondsLeft() {
        if (!active) return 0;
        long elapsed = Math.max(0L, (System.currentTimeMillis() - lastSyncMs) / 1000L);
        return Math.max(0, secondsLeft - (int) elapsed);
    }

    public static void render(GuiGraphics graphics, Minecraft mc, int screenWidth, int screenHeight) {
        if (!isActive() || mc == null || mc.font == null) return;
        int seconds = getSecondsLeft();
        Component text = Component.translatable("message.tac_rogue.coop_wait", floor, seconds);
        int textW = mc.font.width(text);
        int w = Math.max(154, textW + 28);
        int x = (screenWidth - w) / 2;
        int y = 18;
        graphics.fill(x, y, x + w, y + 24, 0xCC06101A);
        graphics.fill(x, y, x + w, y + 2, 0xFF55DDAA);
        graphics.renderOutline(x, y, w, 24, 0xAA55DDAA);
        graphics.drawCenteredString(mc.font, text, screenWidth / 2, y + 8, 0xFFE8FFF8);
    }
}
