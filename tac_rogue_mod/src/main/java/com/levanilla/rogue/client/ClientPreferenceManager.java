package com.levanilla.rogue.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ClientPreferenceManager {
    private static final String FILE_NAME = "tac_rogue-client.properties";
    private static final String LANGUAGE_SELECTED = "language_selected";
    private static final String WELCOME_SEEN_PREFIX = "welcome_setup_seen_v2.";
    private static final String TUTORIAL_GUIDE_DONE_PREFIX = "tutorial_guide_done_v1.";

    private ClientPreferenceManager() {}

    public static boolean hasSelectedLanguage() {
        return Boolean.parseBoolean(load().getProperty(LANGUAGE_SELECTED, "false"));
    }

    public static void markLanguageSelected() {
        Properties props = load();
        props.setProperty(LANGUAGE_SELECTED, "true");
        save(props);
    }

    public static boolean hasSeenWelcomeForCurrentWorld() {
        return Boolean.parseBoolean(load().getProperty(WELCOME_SEEN_PREFIX + currentWorldKey(), "false"));
    }

    public static void markWelcomeSeenForCurrentWorld() {
        Properties props = load();
        props.setProperty(WELCOME_SEEN_PREFIX + currentWorldKey(), "true");
        save(props);
    }

    public static boolean hasCompletedTutorialGuideForCurrentWorld() {
        return Boolean.parseBoolean(load().getProperty(TUTORIAL_GUIDE_DONE_PREFIX + currentWorldKey(), "false"));
    }

    public static void markTutorialGuideCompletedForCurrentWorld() {
        Properties props = load();
        props.setProperty(TUTORIAL_GUIDE_DONE_PREFIX + currentWorldKey(), "true");
        save(props);
    }

    public static void clearTutorialGuideCompletedForCurrentWorld() {
        Properties props = load();
        props.remove(TUTORIAL_GUIDE_DONE_PREFIX + currentWorldKey());
        save(props);
    }

    public static boolean applyLanguage(String code) {
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.options.languageCode = code;
            mc.getLanguageManager().setSelected(code);
            mc.options.save();
            mc.reloadResourcePacks();
            markLanguageSelected();
            return true;
        } catch (Throwable ignored) {
            markLanguageSelected();
            try {
                mc.options.save();
            } catch (Throwable ignoredSave) {
            }
            return false;
        }
    }

    private static Properties load() {
        Properties props = new Properties();
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return props;
        }
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException ignored) {
        }
        return props;
    }

    private static void save(Properties props) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "TacZ Roguelike client preferences");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static String currentWorldKey() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
                return "server_" + sanitizeKey(mc.getCurrentServer().ip);
            }
            if (mc.getSingleplayerServer() != null && mc.getSingleplayerServer().getWorldData() != null) {
                return "single_" + sanitizeKey(mc.getSingleplayerServer().getWorldData().getLevelName());
            }
            if (mc.level != null) {
                return "level_" + sanitizeKey(mc.level.dimension().location().toString());
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private static String sanitizeKey(String value) {
        String cleaned = value == null ? "unknown" : value.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9_.-]+", "_");
        return cleaned.isBlank() ? "unknown" : cleaned;
    }
}
