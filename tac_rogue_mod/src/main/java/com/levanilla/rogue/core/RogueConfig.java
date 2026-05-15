package com.levanilla.rogue.core;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RogueConfig {
    private RogueConfig() {}

    public static final ForgeConfigSpec COMMON_SPEC;

    private static final ForgeConfigSpec.BooleanValue DEBUG_LOGS;
    private static final ForgeConfigSpec.BooleanValue RECOIL_DEBUG_LOGS;
    private static final ForgeConfigSpec.LongValue RECOIL_DEBUG_WINDOW_MS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("debug");
        DEBUG_LOGS = builder
            .comment("Enable verbose TacZ Roguelike debug logs. Keep false for distributed packs.")
            .define("debugLogs", false);
        RECOIL_DEBUG_LOGS = builder
            .comment("Enable detailed third-person recoil/camera diagnostic logs.")
            .define("recoilDebugLogs", false);
        RECOIL_DEBUG_WINDOW_MS = builder
            .comment("How long recoil debug logs stay active after each shot, in milliseconds.")
            .defineInRange("recoilDebugWindowMs", 1400L, 100L, 10000L);
        builder.pop();
        COMMON_SPEC = builder.build();
    }

    public static boolean debugLogs() {
        return getBoolean(DEBUG_LOGS, "tac_rogue.verboseLogs", false);
    }

    public static boolean recoilDebugLogs() {
        return getBoolean(RECOIL_DEBUG_LOGS, "tac_rogue.recoilDebug", false);
    }

    public static long recoilDebugWindowMs() {
        try {
            return RECOIL_DEBUG_WINDOW_MS.get();
        } catch (IllegalStateException ignored) {
            return Long.getLong("tac_rogue.recoilDebugWindowMs", 1400L);
        }
    }

    private static boolean getBoolean(ForgeConfigSpec.BooleanValue value, String legacyProperty, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return Boolean.parseBoolean(System.getProperty(legacyProperty, Boolean.toString(fallback)));
        }
    }
}
