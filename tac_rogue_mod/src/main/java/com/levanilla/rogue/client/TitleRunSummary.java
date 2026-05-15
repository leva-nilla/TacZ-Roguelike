package com.levanilla.rogue.client;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class TitleRunSummary {
    private static final String FILE_NAME = "tac_rogue_title_status.properties";
    private static final Properties DATA = new Properties();
    private static boolean loaded = false;

    private TitleRunSummary() {}

    public static Snapshot snapshot() {
        load();
        return new Snapshot(
            DATA.getProperty("world", "NO RECENT WORLD"),
            readInt("floor", 0),
            readInt("maxFloor", 0),
            readInt("perkCount", 0),
            DATA.getProperty("theme", "UNKNOWN"),
            Boolean.parseBoolean(DATA.getProperty("active", "false")),
            readInt("gold", 0)
        );
    }

    public static void recordRun(int floor, String theme, boolean active, int maxFloor) {
        load();
        DATA.setProperty("world", resolveWorldName());
        DATA.setProperty("floor", Integer.toString(Math.max(0, floor)));
        DATA.setProperty("maxFloor", Integer.toString(Math.max(0, maxFloor)));
        DATA.setProperty("theme", theme == null || theme.isBlank() ? "UNKNOWN" : theme);
        DATA.setProperty("active", Boolean.toString(active));
        save();
    }

    public static void recordGold(int gold) {
        load();
        DATA.setProperty("gold", Integer.toString(Math.max(0, gold)));
        save();
    }

    public static void recordPerks(String[] tags) {
        load();
        int count = 0;
        for (String tag : tags) {
            if (tag != null && tag.startsWith("perk:")) {
                count++;
            }
        }
        DATA.setProperty("perkCount", Integer.toString(count));
        save();
    }

    private static int readInt(String key, int fallback) {
        try {
            return Integer.parseInt(DATA.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String resolveWorldName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        if (mc.getCurrentServer() != null && mc.getCurrentServer().name != null) {
            return mc.getCurrentServer().name;
        }
        return "LOCAL OPERATION";
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Path path = path();
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            DATA.load(reader);
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                DATA.store(writer, "TacZ Roguelike title screen status");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    public record Snapshot(String world, int floor, int maxFloor, int perkCount, String theme, boolean active, int gold) {}
}
