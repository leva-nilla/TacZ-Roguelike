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
    private static final long[] PROFILE_SECTION_TOTAL_NS = new long[ProfileSection.values().length];
    private static final int[] PROFILE_SECTION_COUNTS = new int[ProfileSection.values().length];
    private static final java.util.Map<Integer, Long> TRACKED_LIVING_RENDER_START_NS = new java.util.HashMap<>();
    private static final java.lang.management.ThreadMXBean THREAD_BEAN = java.lang.management.ManagementFactory.getThreadMXBean();
    private static final com.sun.management.OperatingSystemMXBean OS_BEAN = resolveOperatingSystemBean();
    private static final java.util.Map<Long, Long> LAST_THREAD_CPU_NS = new java.util.HashMap<>();
    private static long lastProcessCpuNs = -1L;
    private static long lastCpuWallNs = -1L;
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
        WORLD_DAMAGE,
        WORLD_OBJECTIVE,
        NPC_RENDER,
        SUPPORT_NPC_RENDER,
        BOSS_RENDER
    }

    public enum ProfileSection {
        LEAWINDS_AIM_SYNC,
        THIRD_PERSON_CROSSHAIR_TRACE,
        DYNAMIC_LIGHT_TICK,
        LIVING_RENDER_PRE
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
        java.util.Arrays.fill(PROFILE_SECTION_TOTAL_NS, 0L);
        java.util.Arrays.fill(PROFILE_SECTION_COUNTS, 0);
        TRACKED_LIVING_RENDER_START_NS.clear();
        FRAME_TIMES_NS.clear();
        resetCpuSampling();

        try {
            Path dir = mc.gameDirectory.toPath().resolve("tac_rogue_perf");
            Files.createDirectories(dir);
            outputPath = dir.resolve("perf-" + FILE_TIME.format(LocalDateTime.now()) + "-" + label + ".csv");
            writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
            writer.write("time_ms,fps,avg_frame_ms,p95_frame_ms,p99_frame_ms,hud_avg_ms,"
                + "hud_hotbar_ms,hud_hotbar_bg_ms,hud_hotbar_guns_ms,hud_hotbar_melee_ms,hud_hotbar_items_ms,hud_hotbar_ammo_ms,"
                + "hud_hotbar_item_labels_ms,hud_hotbar_item_icons_ms,hud_hotbar_ammo_icons_ms,hud_hotbar_ammo_decorations_ms,"
                + "hud_status_ms,hud_notification_ms,hud_popup_ms,hud_prewarm_ms,hud_crosshair_ms,world_damage_ms,world_objective_ms,"
                + "npc_render_ms,support_npc_render_ms,boss_render_ms,"
                + "leawinds_aim_sync_ms,leawinds_aim_sync_count,third_person_crosshair_trace_ms,third_person_crosshair_trace_count,"
                + "dynamic_light_tick_ms,dynamic_light_tick_count,living_render_pre_ms,living_render_pre_count,"
                + "jvm_process_cpu_ms,jvm_process_cpu_cores,render_thread_cpu_ms,client_thread_cpu_ms,server_thread_cpu_ms,worker_thread_cpu_ms,top_cpu_thread,top_cpu_thread_ms,"
                + "dynamic_lights,enemy_hint_count,run_active,dimension,screen,camera,main_item,tacz_gun,player_speed,player_moving,shader_pack\n");
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

    public static long onProfileSectionStart() {
        return active ? System.nanoTime() : 0L;
    }

    public static void onProfileSectionEnd(ProfileSection section, long startNs) {
        if (!active || section == null || startNs <= 0L) return;
        int ordinal = section.ordinal();
        PROFILE_SECTION_TOTAL_NS[ordinal] += Math.max(0L, System.nanoTime() - startNs);
        PROFILE_SECTION_COUNTS[ordinal]++;
    }

    private static void writeSample(Minecraft mc) {
        if (writer == null || mc.player == null || mc.player.tickCount % 20 != 0) return;
        try {
            ClientRunState.EnemyDirectionState enemy = ClientRunState.getEnemyDirectionState();
            int hudFrameSampleCount = Math.max(1, hudFrames);
            CpuSnapshot cpu = sampleCpu();
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
                formatMs(hudSectionAverageMs(HudSection.WORLD_OBJECTIVE, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.NPC_RENDER, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.SUPPORT_NPC_RENDER, hudFrameSampleCount)),
                formatMs(hudSectionAverageMs(HudSection.BOSS_RENDER, hudFrameSampleCount)),
                formatProfileSectionAverageMs(ProfileSection.LEAWINDS_AIM_SYNC),
                Integer.toString(profileSectionCount(ProfileSection.LEAWINDS_AIM_SYNC)),
                formatProfileSectionAverageMs(ProfileSection.THIRD_PERSON_CROSSHAIR_TRACE),
                Integer.toString(profileSectionCount(ProfileSection.THIRD_PERSON_CROSSHAIR_TRACE)),
                formatProfileSectionAverageMs(ProfileSection.DYNAMIC_LIGHT_TICK),
                Integer.toString(profileSectionCount(ProfileSection.DYNAMIC_LIGHT_TICK)),
                formatProfileSectionAverageMs(ProfileSection.LIVING_RENDER_PRE),
                Integer.toString(profileSectionCount(ProfileSection.LIVING_RENDER_PRE)),
                formatMs(cpu.processCpuMs()),
                formatMs(cpu.processCpuCores()),
                formatMs(cpu.renderThreadCpuMs()),
                formatMs(cpu.clientThreadCpuMs()),
                formatMs(cpu.serverThreadCpuMs()),
                formatMs(cpu.workerThreadCpuMs()),
                cpu.topCpuThread(),
                formatMs(cpu.topCpuThreadMs()),
                Integer.toString(DynamicLightManager.getActiveLightCount()),
                Integer.toString(enemy == null ? 0 : enemy.count()),
                Boolean.toString(RunManager.isRunActive()),
                mc.level == null ? "none" : mc.level.dimension().location().toString(),
                mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(),
                mc.options == null ? "unknown" : mc.options.getCameraType().name(),
                mainItemName(mc),
                Boolean.toString(isHoldingTacZGun(mc)),
                formatMs(horizontalSpeed(mc)),
                Boolean.toString(isPlayerMoving(mc)),
                shaderPackName()) + "\n");
            writer.flush();
        } catch (IOException ignored) {
            stop(true);
        }
    }

    private static void stop(boolean notify) {
        boolean wasActive = active;
        active = false;
        TRACKED_LIVING_RENDER_START_NS.clear();
        java.util.Arrays.fill(PROFILE_SECTION_TOTAL_NS, 0L);
        java.util.Arrays.fill(PROFILE_SECTION_COUNTS, 0);
        LAST_THREAD_CPU_NS.clear();
        lastProcessCpuNs = -1L;
        lastCpuWallNs = -1L;
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

    private static String formatProfileSectionAverageMs(ProfileSection section) {
        if (section == null) return "0.000";
        int ordinal = section.ordinal();
        long total = PROFILE_SECTION_TOTAL_NS[ordinal];
        int count = Math.max(1, PROFILE_SECTION_COUNTS[ordinal]);
        PROFILE_SECTION_TOTAL_NS[ordinal] = 0L;
        return formatMs((total / (double) count) / 1_000_000.0D);
    }

    private static int profileSectionCount(ProfileSection section) {
        if (section == null) return 0;
        int ordinal = section.ordinal();
        int count = PROFILE_SECTION_COUNTS[ordinal];
        PROFILE_SECTION_COUNTS[ordinal] = 0;
        return count;
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

    public static void onTrackedLivingRenderStart(net.minecraft.world.entity.Entity entity) {
        if (!active || entity == null || trackedLivingSection(entity) == null) return;
        TRACKED_LIVING_RENDER_START_NS.put(entity.getId(), System.nanoTime());
    }

    public static void onTrackedLivingRenderEnd(net.minecraft.world.entity.Entity entity) {
        if (!active || entity == null) return;
        Long startNs = TRACKED_LIVING_RENDER_START_NS.remove(entity.getId());
        if (startNs == null || startNs <= 0L) return;
        HudSection section = trackedLivingSection(entity);
        if (section != null) onHudSectionEnd(section, startNs);
    }

    private static HudSection trackedLivingSection(net.minecraft.world.entity.Entity entity) {
        if (entity.getTags().contains("rogue:boss")) return HudSection.BOSS_RENDER;
        if (entity.getTags().contains("tac_rogue_support_npc")) return HudSection.SUPPORT_NPC_RENDER;
        if (entity.getTags().contains("tac_rogue_npc")) return HudSection.NPC_RENDER;
        return null;
    }

    private static void resetCpuSampling() {
        LAST_THREAD_CPU_NS.clear();
        lastProcessCpuNs = OS_BEAN == null ? -1L : safeProcessCpuTime();
        lastCpuWallNs = System.nanoTime();
        try {
            if (THREAD_BEAN.isThreadCpuTimeSupported() && !THREAD_BEAN.isThreadCpuTimeEnabled()) {
                THREAD_BEAN.setThreadCpuTimeEnabled(true);
            }
        } catch (SecurityException | UnsupportedOperationException ignored) {
        }
    }

    private static CpuSnapshot sampleCpu() {
        long nowWallNs = System.nanoTime();
        double processCpuMs = 0.0D;
        double processCpuCores = 0.0D;
        if (OS_BEAN != null) {
            long processCpuNs = safeProcessCpuTime();
            if (processCpuNs >= 0L && lastProcessCpuNs >= 0L && lastCpuWallNs > 0L && nowWallNs > lastCpuWallNs) {
                long cpuDeltaNs = Math.max(0L, processCpuNs - lastProcessCpuNs);
                processCpuMs = cpuDeltaNs / 1_000_000.0D;
                processCpuCores = cpuDeltaNs / (double) (nowWallNs - lastCpuWallNs);
            }
            lastProcessCpuNs = processCpuNs;
            lastCpuWallNs = nowWallNs;
        }

        double renderMs = 0.0D;
        double clientMs = 0.0D;
        double serverMs = 0.0D;
        double workerMs = 0.0D;
        double topMs = 0.0D;
        String topName = "none";

        if (isThreadCpuUsable()) {
            long[] ids = THREAD_BEAN.getAllThreadIds();
            java.lang.management.ThreadInfo[] infos = THREAD_BEAN.getThreadInfo(ids, 0);
            java.util.HashSet<Long> seen = new java.util.HashSet<>();
            for (int i = 0; i < ids.length; i++) {
                long id = ids[i];
                seen.add(id);
                long cpuNs = safeThreadCpuTime(id);
                if (cpuNs < 0L) continue;
                Long previous = LAST_THREAD_CPU_NS.put(id, cpuNs);
                if (previous == null || cpuNs < previous) continue;
                double deltaMs = (cpuNs - previous) / 1_000_000.0D;
                java.lang.management.ThreadInfo info = infos == null || i >= infos.length ? null : infos[i];
                String name = info == null ? "thread_" + id : info.getThreadName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (name.contains("Render thread")) renderMs += deltaMs;
                if (name.contains("Client thread")) clientMs += deltaMs;
                if (name.contains("Server thread")) serverMs += deltaMs;
                if (lower.contains("worker") || lower.contains("forkjoin") || lower.contains("pool")) {
                    workerMs += deltaMs;
                }
                if (deltaMs > topMs) {
                    topMs = deltaMs;
                    topName = sanitize(name);
                }
            }
            LAST_THREAD_CPU_NS.keySet().removeIf(id -> !seen.contains(id));
        } else {
            topName = "unsupported";
        }

        return new CpuSnapshot(processCpuMs, processCpuCores, renderMs, clientMs, serverMs, workerMs, topName, topMs);
    }

    private static boolean isThreadCpuUsable() {
        try {
            return THREAD_BEAN.isThreadCpuTimeSupported() && THREAD_BEAN.isThreadCpuTimeEnabled();
        } catch (SecurityException | UnsupportedOperationException ignored) {
            return false;
        }
    }

    private static long safeThreadCpuTime(long id) {
        try {
            return THREAD_BEAN.getThreadCpuTime(id);
        } catch (SecurityException | UnsupportedOperationException ignored) {
            return -1L;
        }
    }

    private static long safeProcessCpuTime() {
        try {
            return OS_BEAN == null ? -1L : OS_BEAN.getProcessCpuTime();
        } catch (SecurityException | UnsupportedOperationException ignored) {
            return -1L;
        }
    }

    private static com.sun.management.OperatingSystemMXBean resolveOperatingSystemBean() {
        java.lang.management.OperatingSystemMXBean bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            return osBean;
        }
        return null;
    }

    private record CpuSnapshot(
        double processCpuMs,
        double processCpuCores,
        double renderThreadCpuMs,
        double clientThreadCpuMs,
        double serverThreadCpuMs,
        double workerThreadCpuMs,
        String topCpuThread,
        double topCpuThreadMs
    ) {}

    private static String mainItemName(Minecraft mc) {
        if (mc == null || mc.player == null) return "none";
        net.minecraft.world.item.ItemStack stack = mc.player.getMainHandItem();
        if (stack == null || stack.isEmpty()) return "empty";
        return sanitize(stack.getItem().builtInRegistryHolder().key().location().toString());
    }

    private static boolean isHoldingTacZGun(Minecraft mc) {
        if (mc == null || mc.player == null) return false;
        net.minecraft.world.item.ItemStack stack = mc.player.getMainHandItem();
        if (stack == null || stack.isEmpty()) return false;
        try {
            if (com.tacz.guns.api.item.IGun.getIGunOrNull(stack) != null) return true;
        } catch (Throwable ignored) {
        }
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("GunId");
    }

    private static double horizontalSpeed(Minecraft mc) {
        if (mc == null || mc.player == null) return 0.0D;
        var delta = mc.player.getDeltaMovement();
        return Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    }

    private static boolean isPlayerMoving(Minecraft mc) {
        if (mc == null || mc.player == null) return false;
        return horizontalSpeed(mc) > 0.01D;
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
