package com.levanilla.rogue.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HUD通知システム — チャットの代わりに画面上部にメッセージを表示。
 */
public final class NotificationManager {

    private NotificationManager() {}

    public enum PopupPosition {
        TOP_RIGHT("現在"),
        MINIMAP_UNDER("ミニマップ下"),
        ABOVE_HUD("HUD上");

        private final String label;

        PopupPosition(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final CopyOnWriteArrayList<HudNotification> notifications = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<PopupNotification> popups = new CopyOnWriteArrayList<>();

    private static final float[] SIZE_VALUES = {0.70f, 0.82f, 1.00f, 1.12f};
    private static final int[] SPEED_VALUES = {5, 7, 11};
    private static final float[] DURATION_VALUES = {0.70f, 0.85f, 1.00f, 1.25f};
    private static final int[] MAX_VISIBLE_VALUES = {2, 3, 4};
    private static final int MAX_POPUP_BODY_LINES = 4;

    private static boolean settingsLoaded = false;
    private static float popupScale = 0.82f;
    private static int popupSpeedTicks = 7;
    private static float durationMultiplier = 0.85f;
    private static int maxVisible = 3;
    private static PopupPosition popupPosition = PopupPosition.TOP_RIGHT;

    private static class HudNotification {
        final String text;
        final int textWidth;
        final int color;
        int ticksAlive;
        final int maxTicks;
        HudNotification(String text, int color, int durationTicks) {
            this.text = text;
            this.textWidth = Minecraft.getInstance().font.width(stripFormatting(text));
            this.color = color;
            this.ticksAlive = 0; this.maxTicks = durationTicks;
        }
    }

    private static class PopupNotification {
        final String type;
        final Component title;
        final Component body;
        final int color;
        int ticksAlive;
        final int maxTicks;
        int cachedTextWidth = -1;
        List<FormattedCharSequence> cachedBodyLines = List.of();

        PopupNotification(String type, Component title, Component body, int color, int durationTicks) {
            this.type = type;
            this.title = title;
            this.body = body;
            this.color = color;
            this.ticksAlive = 0;
            this.maxTicks = durationTicks;
        }

        List<FormattedCharSequence> bodyLines(Minecraft mc, int textWidth) {
            if (cachedTextWidth != textWidth) {
                cachedTextWidth = textWidth;
                cachedBodyLines = mc.font.split(body, textWidth);
            }
            return cachedBodyLines;
        }
    }

    /** HUD通知を追加 */
    public static void add(String text, int color) {
        notifications.add(new HudNotification(text, color, 80));
        if (notifications.size() > 5) notifications.remove(0);
    }

    public static void addPopup(String type, Component title, Component body, int color, int durationTicks) {
        ensureSettingsLoaded();
        int adjustedDuration = Math.max(20, Math.round(durationTicks * durationMultiplier));
        popups.add(new PopupNotification(type, title, body, color, adjustedDuration));
        while (popups.size() > getMaxVisible()) popups.remove(0);
    }

    public static void addPreviewPopup() {
        addPopup("PREVIEW",
            Component.literal("表示テスト"),
            Component.literal("サイズと速度はこの見え方で保存される。邪魔なら小さくしておけばいいよ。"),
            0xFF55DDAA,
            100);
    }

    public static String getSizeLabel() {
        ensureSettingsLoaded();
        return Math.round(popupScale * 100.0f) + "%";
    }

    public static String getSpeedLabel() {
        ensureSettingsLoaded();
        return popupSpeedTicks + " tick";
    }

    public static String getDurationLabel() {
        ensureSettingsLoaded();
        return Math.round(durationMultiplier * 100.0f) + "%";
    }

    public static int getMaxVisible() {
        ensureSettingsLoaded();
        return maxVisible;
    }

    public static PopupPosition getPopupPosition() {
        ensureSettingsLoaded();
        return popupPosition;
    }

    public static String getPopupPositionLabel() {
        return getPopupPosition().label();
    }

    public static float getPopupScale() {
        ensureSettingsLoaded();
        return popupScale;
    }

    public static int getPopupSpeedTicks() {
        ensureSettingsLoaded();
        return popupSpeedTicks;
    }

    public static float getDurationMultiplier() {
        ensureSettingsLoaded();
        return durationMultiplier;
    }

    public static void setPopupScale(float value) {
        ensureSettingsLoaded();
        popupScale = clamp(value, 0.70f, 1.20f);
        saveSettings();
    }

    public static void setPopupSpeedTicks(int value) {
        ensureSettingsLoaded();
        popupSpeedTicks = Math.max(4, Math.min(14, value));
        saveSettings();
    }

    public static void setDurationMultiplier(float value) {
        ensureSettingsLoaded();
        durationMultiplier = clamp(value, 0.60f, 1.50f);
        saveSettings();
    }

    public static void setMaxVisible(int value) {
        ensureSettingsLoaded();
        maxVisible = Math.max(1, Math.min(5, value));
        saveSettings();
    }

    public static void cyclePopupPosition() {
        ensureSettingsLoaded();
        PopupPosition[] values = PopupPosition.values();
        popupPosition = values[(popupPosition.ordinal() + 1) % values.length];
        saveSettings();
    }

    public static void cycleSize() {
        ensureSettingsLoaded();
        setPopupScale(nextFloat(popupScale, SIZE_VALUES));
    }

    public static void cycleSpeed() {
        ensureSettingsLoaded();
        setPopupSpeedTicks(nextInt(popupSpeedTicks, SPEED_VALUES));
    }

    public static void cycleDuration() {
        ensureSettingsLoaded();
        setDurationMultiplier(nextFloat(durationMultiplier, DURATION_VALUES));
    }

    public static void cycleMaxVisible() {
        ensureSettingsLoaded();
        setMaxVisible(nextInt(maxVisible, MAX_VISIBLE_VALUES));
    }

    /** Tick処理 */
    public static void tick() {
        notifications.removeIf(n -> { n.ticksAlive++; return n.ticksAlive > n.maxTicks; });
        popups.removeIf(n -> { n.ticksAlive++; return n.ticksAlive > n.maxTicks; });
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
            int textWidth = n.textWidth;
            int nx = (width - textWidth) / 2;
            int ny = baseY - idx * 12;
            graphics.fill(nx - 4, ny - 1, nx + textWidth + 4, ny + 10, bgColor);
            graphics.drawString(mc.font, text, nx, ny, color, false);
            idx++;
        }
    }

    public static void renderPopups(GuiGraphics graphics, Minecraft mc, int width, int height) {
        if (popups.isEmpty()) return;
        ensureSettingsLoaded();

        float uiScale = popupScale;
        float textScale = Math.max(0.72f, 0.82f * uiScale);
        int boxW = Math.min(Math.round(236 * uiScale), width - 18);
        int gap = Math.max(3, Math.round(4 * uiScale));
        List<PopupLayout> layouts = new ArrayList<>();
        int totalHeight = 0;
        int textWidth = Math.max(24, Math.round((boxW - 16) / textScale));
        int lineH = Math.max(7, Math.round(8 * uiScale));
        for (PopupNotification popup : popups) {
            List<FormattedCharSequence> bodyLines = popup.bodyLines(mc, textWidth);
            int boxH = Math.round(26 * uiScale) + Math.min(MAX_POPUP_BODY_LINES, bodyLines.size()) * lineH;
            layouts.add(new PopupLayout(popup, bodyLines, boxH));
            totalHeight += boxH + gap;
        }
        if (totalHeight > 0) totalHeight -= gap;

        PopupAnchor anchor = resolveAnchor(width, height, boxW, totalHeight, uiScale);
        int idx = 0;
        for (PopupLayout layout : layouts) {
            PopupNotification popup = layout.popup;
            int animTicks = popupSpeedTicks;
            float progress = popup.ticksAlive < animTicks
                ? popup.ticksAlive / (float) animTicks
                : popup.ticksAlive > popup.maxTicks - animTicks
                    ? Math.max(0.0f, (popup.maxTicks - popup.ticksAlive) / (float) animTicks)
                : 1.0f;
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            float eased = 1.0f - (float)Math.pow(1.0f - progress, 3.0f);
            int x = anchor.x + Math.round((1.0f - eased) * (boxW + 12) * anchor.slideDirection);
            int bg = 0xE607111F;
            int border = 0xDD000000 | (popup.color & 0xFFFFFF);
            int textColor = 0xEEFFFFFF;
            int accent = 0xEE000000 | (popup.color & 0xFFFFFF);

            int boxH = layout.boxHeight;
            int yy = anchor.y + idx;

            graphics.fill(x, yy, x + boxW, yy + boxH, bg);
            graphics.fill(x, yy, x + 3, yy + boxH, accent);
            graphics.renderOutline(x, yy, boxW, boxH, border);

            String typeText = popup.type;
            graphics.pose().pushPose();
            graphics.pose().translate(x + Math.round(7 * uiScale), yy + Math.round(4 * uiScale), 0);
            graphics.pose().scale(textScale, textScale, 1.0f);
            graphics.drawString(mc.font, typeText, 0, 0, accent, false);
            graphics.drawString(mc.font, popup.title, mc.font.width(typeText) + 10, 0, textColor, false);
            graphics.pose().popPose();

            int lineY = yy + Math.round(15 * uiScale);
            int lines = 0;
            for (FormattedCharSequence line : layout.bodyLines) {
                if (lines >= MAX_POPUP_BODY_LINES) break;
                graphics.pose().pushPose();
                graphics.pose().translate(x + Math.round(7 * uiScale), lineY, 0);
                graphics.pose().scale(textScale, textScale, 1.0f);
                graphics.drawString(mc.font, line, 0, 0, textColor, false);
                graphics.pose().popPose();
                lineY += lineH;
                lines++;
            }

            idx += boxH + gap;
        }
    }

    public static int getAboveHudPopupHeight(Minecraft mc, int width) {
        if (popups.isEmpty()) return 0;
        ensureSettingsLoaded();
        if (popupPosition != PopupPosition.ABOVE_HUD) return 0;

        float uiScale = popupScale;
        float textScale = Math.max(0.72f, 0.82f * uiScale);
        int boxW = Math.min(Math.round(236 * uiScale), width - 18);
        int gap = Math.max(3, Math.round(4 * uiScale));
        int textWidth = Math.max(24, Math.round((boxW - 16) / textScale));
        int lineH = Math.max(7, Math.round(8 * uiScale));
        int totalHeight = 0;
        for (PopupNotification popup : popups) {
            List<FormattedCharSequence> bodyLines = popup.bodyLines(mc, textWidth);
            int boxH = Math.round(26 * uiScale) + Math.min(MAX_POPUP_BODY_LINES, bodyLines.size()) * lineH;
            totalHeight += boxH + gap;
        }
        return totalHeight > 0 ? totalHeight : 0;
    }

    private static void ensureSettingsLoaded() {
        if (settingsLoaded) return;
        settingsLoaded = true;
        File file = settingsFile();
        if (!file.isFile()) return;
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
            popupScale = clamp(parseFloat(properties.getProperty("scale"),
                legacyFloat(properties.getProperty("size"), SIZE_VALUES, popupScale)), 0.70f, 1.20f);
            popupSpeedTicks = Math.max(4, Math.min(14, parseInt(properties.getProperty("speedTicks"),
                legacyInt(properties.getProperty("speed"), SPEED_VALUES, popupSpeedTicks))));
            durationMultiplier = clamp(parseFloat(properties.getProperty("durationMultiplier"),
                legacyFloat(properties.getProperty("duration"), DURATION_VALUES, durationMultiplier)), 0.60f, 1.50f);
            maxVisible = Math.max(1, Math.min(5, parseInt(properties.getProperty("maxVisible"),
                legacyInt(properties.getProperty("maxVisible"), MAX_VISIBLE_VALUES, maxVisible))));
            popupPosition = parseEnum(PopupPosition.class, properties.getProperty("position"), popupPosition);
        } catch (Exception ignored) {}
    }

    private static void saveSettings() {
        File file = settingsFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        Properties properties = new Properties();
        properties.setProperty("scale", String.valueOf(popupScale));
        properties.setProperty("speedTicks", String.valueOf(popupSpeedTicks));
        properties.setProperty("durationMultiplier", String.valueOf(durationMultiplier));
        properties.setProperty("maxVisible", String.valueOf(maxVisible));
        properties.setProperty("position", popupPosition.name());
        try (FileOutputStream output = new FileOutputStream(file)) {
            properties.store(output, "Tac Rogue popup settings");
        } catch (Exception ignored) {}
    }

    private static File settingsFile() {
        Minecraft mc = Minecraft.getInstance();
        return new File(mc.gameDirectory, "config/tac_rogue_popup.properties");
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return value == null ? fallback : Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float legacyFloat(String value, float[] options, float fallback) {
        int index = parseInt(value, -1);
        return index >= 0 && index < options.length ? options[index] : fallback;
    }

    private static int legacyInt(String value, int[] options, int fallback) {
        int index = parseInt(value, -1);
        return index >= 0 && index < options.length ? options[index] : fallback;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float nextFloat(float value, float[] options) {
        for (float option : options) {
            if (option > value + 0.01f) return option;
        }
        return options[0];
    }

    private static int nextInt(int value, int[] options) {
        for (int option : options) {
            if (option > value) return option;
        }
        return options[0];
    }

    private static PopupAnchor resolveAnchor(int width, int height, int boxW, int totalHeight, float uiScale) {
        PopupPosition position = getPopupPosition();
        return switch (position) {
            case MINIMAP_UNDER -> new PopupAnchor(width - boxW - 8,
                Math.max(64, Math.round(82 * uiScale)), 1);
            case ABOVE_HUD -> {
                HudSettings.Bounds hud = HudRenderer.getHudBounds(width, height);
                int x = clampInt(hud.x, 8, Math.max(8, width - boxW - 8));
                int y = hud.y - totalHeight - 6;
                if (y < 8) y = Math.min(Math.max(8, height - totalHeight - 8), hud.y + hud.height + 6);
                int direction = x < width / 2 ? -1 : 1;
                yield new PopupAnchor(x, y, direction);
            }
            case TOP_RIGHT -> new PopupAnchor(width - boxW - 8,
                Math.max(18, Math.round(26 * uiScale)), 1);
        };
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String stripFormatting(String text) {
        if (text == null || text.indexOf('§') < 0) return text == null ? "" : text;
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private static final class PopupLayout {
        final PopupNotification popup;
        final List<FormattedCharSequence> bodyLines;
        final int boxHeight;

        PopupLayout(PopupNotification popup, List<FormattedCharSequence> bodyLines, int boxHeight) {
            this.popup = popup;
            this.bodyLines = bodyLines;
            this.boxHeight = boxHeight;
        }
    }

    private static final class PopupAnchor {
        final int x;
        final int y;
        final int slideDirection;

        PopupAnchor(int x, int y, int slideDirection) {
            this.x = x;
            this.y = y;
            this.slideDirection = slideDirection;
        }
    }
}
