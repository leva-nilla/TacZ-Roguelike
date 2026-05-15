package com.levanilla.rogue.client.hud;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.Properties;

public final class HudSettings {
    private HudSettings() {}

    public enum HudStyle {
        COMPACT("Compact"),
        TACTICAL("Tactical"),
        MINIMAL("Minimal");

        private final String label;

        HudStyle(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum HudPosition {
        LEFT_BOTTOM("左下"),
        LEFT_MID("左中央"),
        RIGHT_BOTTOM("右下");

        private final String label;

        HudPosition(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final class Bounds {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static boolean loaded = false;
    private static float scale = 0.90f;
    private static float opacity = 0.75f;
    private static HudStyle style = HudStyle.TACTICAL;
    private static HudPosition position = HudPosition.LEFT_BOTTOM;

    public static float getScale() {
        ensureLoaded();
        return scale;
    }

    public static float getOpacity() {
        ensureLoaded();
        return opacity;
    }

    public static HudStyle getStyle() {
        ensureLoaded();
        return style;
    }

    public static HudPosition getPosition() {
        ensureLoaded();
        return position;
    }

    public static String getScaleLabel() {
        return Math.round(getScale() * 100.0f) + "%";
    }

    public static String getOpacityLabel() {
        return Math.round(getOpacity() * 100.0f) + "%";
    }

    public static String getStyleLabel() {
        return getStyle().label();
    }

    public static String getPositionLabel() {
        return getPosition().label();
    }

    public static void setScale(float value) {
        ensureLoaded();
        scale = clamp(value, 0.70f, 1.20f);
        save();
    }

    public static void setOpacity(float value) {
        ensureLoaded();
        opacity = clamp(value, 0.35f, 1.00f);
        save();
    }

    public static void cycleStyle() {
        ensureLoaded();
        HudStyle[] values = HudStyle.values();
        style = values[(style.ordinal() + 1) % values.length];
        save();
    }

    public static void cyclePosition() {
        ensureLoaded();
        HudPosition[] values = HudPosition.values();
        position = values[(position.ordinal() + 1) % values.length];
        save();
    }

    public static Bounds getBounds(int screenWidth, int screenHeight) {
        return getBounds(screenWidth, screenHeight, getScale(), getStyle(), getPosition());
    }

    public static Bounds getBounds(int screenWidth, int screenHeight, float scaleValue, HudStyle styleValue, HudPosition positionValue) {
        int panelW = Math.round(baseWidth(styleValue) * scaleValue);
        int panelH = Math.round(baseHeight(styleValue) * scaleValue);
        int margin = 12;
        int hotbarSafeBottom = Math.max(48, screenHeight - 40);

        int x = switch (positionValue) {
            case RIGHT_BOTTOM -> screenWidth - panelW - margin;
            case LEFT_BOTTOM, LEFT_MID -> margin;
        };
        int y = switch (positionValue) {
            case LEFT_MID -> screenHeight / 2 - panelH / 2;
            case LEFT_BOTTOM, RIGHT_BOTTOM -> hotbarSafeBottom - panelH;
        };

        x = clampInt(x, margin, Math.max(margin, screenWidth - panelW - margin));
        y = clampInt(y, margin, Math.max(margin, hotbarSafeBottom - panelH));
        return new Bounds(x, y, panelW, panelH);
    }

    public static int baseWidth(HudStyle styleValue) {
        return switch (styleValue) {
            case COMPACT -> 126;
            case MINIMAL -> 118;
            case TACTICAL -> 140;
        };
    }

    public static int baseHeight(HudStyle styleValue) {
        return switch (styleValue) {
            case COMPACT -> 52;
            case MINIMAL -> 42;
            case TACTICAL -> 65;
        };
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = settingsFile();
        if (!file.isFile()) return;
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
            scale = clamp(parseFloat(properties.getProperty("scale"), scale), 0.70f, 1.20f);
            opacity = clamp(parseFloat(properties.getProperty("opacity"), opacity), 0.35f, 1.00f);
            style = parseEnum(HudStyle.class, properties.getProperty("style"), style);
            position = parseEnum(HudPosition.class, properties.getProperty("position"), position);
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        File file = settingsFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        Properties properties = new Properties();
        properties.setProperty("scale", String.valueOf(scale));
        properties.setProperty("opacity", String.valueOf(opacity));
        properties.setProperty("style", style.name());
        properties.setProperty("position", position.name());
        try (FileOutputStream output = new FileOutputStream(file)) {
            properties.store(output, "Tac Rogue HUD settings");
        } catch (Exception ignored) {
        }
    }

    private static File settingsFile() {
        Minecraft mc = Minecraft.getInstance();
        return new File(mc.gameDirectory, "config/tac_rogue_hud.properties");
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return value == null ? fallback : Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
