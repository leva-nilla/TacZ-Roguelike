package com.levanilla.rogue.client;

import com.levanilla.rogue.client.hud.NotificationManager;
import com.levanilla.rogue.networking.DebugAiOverlayRequestMessage;
import com.levanilla.rogue.networking.DebugAiOverlayStateMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class DebugAiOverlayManager {
    private static final int REQUEST_INTERVAL_TICKS = 10;
    private static final int MAX_ROWS = 12;
    private static final double REQUEST_RADIUS = 128.0D;

    private static boolean enabled = false;
    private static int nextRequestTick = 0;
    private static List<DebugAiOverlayStateMessage.Entry> entries = List.of();

    private DebugAiOverlayManager() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    public static void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (!enabled) {
            entries = List.of();
        } else {
            nextRequestTick = 0;
        }
        NotificationManager.add(enabled ? "AI Debug Overlay ON" : "AI Debug Overlay OFF",
            enabled ? 0xFF66E8FF : 0xFF8A8F99);
    }

    public static void tick(Minecraft mc) {
        if (!enabled || mc == null || mc.player == null || mc.level == null) return;
        if (!ClientInputEventDelegate.isRogueContext(mc)) {
            entries = List.of();
            return;
        }
        int tick = mc.player.tickCount;
        if (tick < nextRequestTick) return;
        nextRequestTick = tick + REQUEST_INTERVAL_TICKS;
        TacRogueNetworking.CHANNEL.sendToServer(new DebugAiOverlayRequestMessage(REQUEST_RADIUS, MAX_ROWS));
    }

    public static void update(List<DebugAiOverlayStateMessage.Entry> newEntries) {
        entries = newEntries == null ? List.of() : List.copyOf(newEntries);
    }

    public static void render(GuiGraphics graphics, Minecraft mc, int width, int height) {
        if (!enabled || mc == null || mc.options == null || mc.options.hideGui || mc.player == null) return;
        int x = 8;
        int y = 38;
        int rowH = 10;
        List<String> lines = new ArrayList<>();
        lines.add("AI DEBUG  target / alert / memory");
        if (entries.isEmpty()) {
            lines.add("no mobs nearby");
        } else {
            for (DebugAiOverlayStateMessage.Entry entry : entries) {
                lines.add("#" + entry.entityId() + " " + entry.entityType() + " "
                    + fmt(entry.distance()) + "m "
                    + entry.alertLevel() + " [" + emptyDash(entry.reason()) + "]");
                lines.add("  tgt=" + emptyDash(entry.targetInfo())
                    + " los=" + (entry.hasLosToPlayer() ? "Y" : "N")
                    + " active=" + (entry.hasActiveTarget() ? "Y" : "N")
                    + " left=" + entry.alertTicksLeft());
                lines.add("  mem=" + emptyDash(entry.memoryPos())
                    + " seen=" + entry.lastSeenAgeTicks()
                    + " decoy=" + entry.decoyTicksLeft());
            }
        }

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.font.width(line));
        }
        int panelW = Math.min(width - 16, maxWidth + 12);
        int panelH = lines.size() * rowH + 10;
        graphics.fill(x, y, x + panelW, y + panelH, 0xD8061018);
        graphics.fill(x, y, x + 2, y + panelH, 0xFF66E8FF);
        graphics.renderOutline(x, y, panelW, panelH, 0xAA66E8FF);

        int textY = y + 5;
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFBFFFF3 : lineColor(lines.get(i));
            graphics.drawString(mc.font, Component.literal(lines.get(i)), x + 6, textY, color, false);
            textY += rowH;
        }
    }

    private static int lineColor(String line) {
        if (line.contains("ENGAGED")) return 0xFFFF6D5A;
        if (line.contains("WARNED")) return 0xFFFFD45C;
        if (line.contains("INVESTIGATE")) return 0xFF7FD8FF;
        if (line.startsWith("  ")) return 0xFF9BA8B8;
        return 0xFFE6EDF2;
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
