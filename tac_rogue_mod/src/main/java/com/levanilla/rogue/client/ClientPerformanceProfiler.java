package com.levanilla.rogue.client;

import com.levanilla.rogue.core.ClientRunState;
import com.levanilla.rogue.core.RunManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Locale;

public final class ClientPerformanceProfiler {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ArrayDeque<Long> FRAME_TIMES_NS = new ArrayDeque<>();
    private static final int MAX_FRAME_SAMPLES = 600;

    private static boolean active;
    private static String label = "manual";
    private static long endMillis;
    private static long lastFrameNs;
    private static long hudStartNs;
    private static long hudTotalNs;
    private static int hudFrames;
    private static final long[] HUD_SECTION_TOTAL_NS = new long[HudSection.values().length];
    private static BufferedWriter writer;
    private static Path outputPath;

    private ClientPerformanceProfiler() {}

    public enum HudSection {
        HOTBAR,
        HOTBAR_BG,
        HOTBAR_GUNS,
        HOTBAR_MELEE,
        HOTBAR_ITEMS,
        HOTBAR_ITEM_LABELS,
        HOTBAR_ITEM_ICONS,
        HOTBAR_AMMO,
        HOTBAR_AMMO_ICONS,
        HOTBAR_AMMO_DECORATIONS,
        STATUS,
        NOTIFICATION,
        POPUP,
        PREWARM,
        CROSSHAIR,
        WORLD_DAMAGE
    }

    public static void start(int seconds, String rawLabel) {
        stop(false);
        Minecraft mc = Minecraft.getInstance();
        label = sanitize(rawLabel == null || rawLabel.isBlank() ? "manual" : rawLabel);
        int duration = Math.max(10, Math.min(600, seconds));
        endMillis = System.currentTimeMillis() + duration * 1000L;
        lastFrameNs = 0L;
        hudStartNs = 0L;
        hudTotalNs = 0L;
        hudFrames = 0;
        java.util.Arrays.fill(HUD_SECTION_TOTAL_NS, 0L);
        FRAME_TIMES_NS.clear();

        try {
            Path dir = mc.gameDirectory.toPath().resolve("tac_rogue_perf");
            Files.createDirectories(dir);
            outputPath = dir.resolve("perf-" + FILE_TIME.format(LocalDateTime.now()) + "-" + label + ".csv");
            writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
            writer.write("time_ms,fps,avg_frame_ms,p95_frame_ms,p99_frame_ms,hud_avg_ms,"
                + "hud_hotbar_ms,hud_hotbar_bg_ms,hud_hotbar_guns_ms,hud_hotbar_melee_ms,hud_hotbar_items_ms,hud_hotbar_ammo_ms,"
                + "hud_hotbar_item_labels_ms,hud_hotbar_item_icons_ms,hud_hotbar_ammo_icons_ms,hud_hotbar_ammo_decorations_ms,"
                + "hud_status_ms,hud_notification_ms,hud_popup_ms,hud_prewarm_ms,hud_crosshair_ms,world_damage_ms,"
                + "dynamic_lights,enemy_hint_count,run_active,dimension,screen,shader_pack\n");
            active = true;
            notify("FPS trace started: " + outputPath.getFileName());
        } catch (IOException ex) {
            writer = null;
            outputPath = null;
            active = false;
            notify("FPS trace failed: " + ex.getClass().getSimpleName());
        }
    }

    public static void stop() {
        stop(true);
    }

    public static void onClientTick(Minecraft mc) {
        if (!active || mc == null) return;
        if (System.currentTimeMillis() >= endMillis) {
            stop(true);
            return;
        }
        writeSample(mc);
    }

    public static void onHudRenderStart() {
        if (active) hudStartNs = System.nanoTime();
    }

    public static void onHudRenderEnd() {
        if (!active) return;
        long now = System.nanoTime();
        if (lastFrameNs > 0L) {
            FRAME_TIMES_NS.addLast(now - lastFrameNs);
            while (FRAME_TIMES_NS.size() > MAX_FRAME_SAMPLES) FRAME_TIMES_NS.removeFirst();
        }
        lastFrameNs = now;
        if (hudStartNs > 0L) {
            hudTotalNs += Math.max(0L, now - hudStartNs);
            hudFrames++;
            hudStartNs = 0L;
        }
    }

    public static long onHudSectionStart() {
        return active ? System.nanoTime() : 0L;
    }

    public static void onHudSectionEnd(HudSection section, long startNs) {
        if (!active || section == null || startNs <= 0L) return;
        HUD_SECTION_TOTAL_NS[section.ordinal()] += Math.max(0L, System.nanoTime() - startNs);
    }

    private static void writeSample(Minecraft mc) {
        if (writer == null || mc.player == null || mc.player.tickCount % 20 != 0) return;
        try {
            ClientRunState.EnemyDirectionState enemy = ClientRunState.getEnemyDirectionState();
            int hudFrameSampleCount = Math.max(1, hudFrames);
            writer.write(String.join(",",
                Long.toString(System.currentTimeMillis()),
                Integer.toString(fps()),
                formatMs(averageFrameMs()),
                formatMs(percentileFrameMs(0.95D)),
                formatMs(percentileFrameMs(0.99D)),
                formatMs(hudAverageMs(hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.HOTBAR, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.HOTBAR_BG, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.HOTBAR_GUNS, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.HOTBAR_MELEE, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.HOTBAR_ITEMS, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.HOTBAR_AMMO, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.HOTBAR_ITEM_LABELS, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.HOTBAR_ITEM_ICONS, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.HOTBAR_AMMO_ICONS, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.HOTBAR_AMMO_DECORATIONS, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.STATUS, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.NOTIFICATION, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.POPUP, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.PREWARM, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.CROSSHAIR, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.WORLD_DAMAGE, hudFrameSampleCount)),
                Integer.toString(DynamicLightManager.getActiveLightCount()),
                Integer.toString(enemy == null ? 0 : enemy.count()),
                Boolean.toString(RunManager.isRunActive()),
                mc.level == null ? "none" : mc.level.dimension().location().toString(),
                mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(),
                shaderPackName()) + "\n");
            writer.flush();
        } catch (IOException ignored) {
            stop(true);
        }
    }

    private static void stop(boolean notify) {
        boolean wasActive = active;
        active = false;
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
        if (notify && wasActive && outputPath != null) {
            notify("FPS trace saved: " + outputPath.getFileName());
        }
    }

    private static int fps() {
        try {
            return Minecraft.getInstance().getFps();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static double averageFrameMs() {
        if (FRAME_TIMES_NS.isEmpty()) return 0.0D;
        long sum = 0L;
        for (long value : FRAME_TIMES_NS) sum += value;
        return (sum / (double) FRAME_TIMES_NS.size()) / 1_000_000.0D;
    }

    private static double percentileFrameMs(double percentile) {
        if (FRAME_TIMES_NS.isEmpty()) return 0.0D;
        long[] values = new long[FRAME_TIMES_NS.size()];
        int i = 0;
        for (long value : FRAME_TIMES_NS) values[i++] = value;
        java.util.Arrays.sort(values);
        int index = Math.max(0, Math.min(values.length - 1, (int) Math.ceil(values.length * percentile) - 1));
        return values[index] / 1_000_000.0D;
    }

    private static double hudAverageMs(int frames) {
        if (frames <= 0) return 0.0D;
        double value = (hudTotalNs / (double) frames) / 1_000_000.0D;
        hudTotalNs = 0L;
        hudFrames = 0;
        return value;
    }

    private static double hudSectionAverageMs(HudSection section, int frames) {
        if (section == null) return 0.0D;
        long total = HUD_SECTION_TOTAL_NS[section.ordinal()];
        HUD_SECTION_TOTAL_NS[section.ordinal()] = 0L;
        return (total / (double) Math.max(1, frames)) / 1_000_000.0D;
    }

    private static String shaderPackName() {
        try {
            Path props = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("oculus.properties");
            if (!Files.isRegularFile(props)) return "unknown";
            String pack = "unknown";
            boolean enabled = true;
            for (String line : Files.readAllLines(props, StandardCharsets.UTF_8)) {
                if (line.startsWith("shaderPack=")) pack = sanitize(line.substring("shaderPack=".length()));
                if (line.startsWith("enableShaders=")) enabled = Boolean.parseBoolean(line.substring("enableShaders=".length()));
            }
            return enabled ? pack : "off_" + pack;
        } catch (IOException ignored) {
        }
        return "unknown";
    }

    private static String sanitize(String raw) {
        return raw.replace(',', '_').replace(' ', '_').replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String formatMs(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static void notify(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[TacRogue PERF] " + message), false);
        }
    }
}
