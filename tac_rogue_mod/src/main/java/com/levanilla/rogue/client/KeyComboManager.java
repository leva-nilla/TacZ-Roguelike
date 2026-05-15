package com.levanilla.rogue.client;

import com.levanilla.rogue.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class KeyComboManager {
    private static final Map<String, Combo> COMBOS = new LinkedHashMap<>();
    private static final Path CONFIG_PATH = Path.of("config", "tac_rogue_key_combos.properties");
    private static boolean loaded;

    private KeyComboManager() {}

    public static void setCombo(KeyMapping mapping, List<InputConstants.Key> keys) {
        if (mapping == null || keys == null || keys.isEmpty()) return;
        ensureLoaded();
        COMBOS.put(mapping.getName(), new Combo(copyUnique(keys), false));
        mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        save();
    }

    public static void clearCombo(KeyMapping mapping) {
        if (mapping == null) return;
        ensureLoaded();
        if (COMBOS.remove(mapping.getName()) != null) {
            save();
        }
    }

    public static boolean hasCombo(KeyMapping mapping) {
        if (mapping == null) return false;
        ensureLoaded();
        return COMBOS.containsKey(mapping.getName());
    }

    public static Component label(KeyMapping mapping) {
        if (mapping == null) return Component.literal("N/A");
        ensureLoaded();
        Combo combo = COMBOS.get(mapping.getName());
        return combo != null ? KeybindCaptureHelper.comboLabel(combo.keys()) : mapping.getTranslatedKeyMessage();
    }

    public static void tick(Minecraft mc) {
        ensureLoaded();
        if (mc == null || mc.player == null || mc.screen != null) {
            releaseAll(mc);
            return;
        }
        long window = mc.getWindow().getWindow();
        for (Map.Entry<String, Combo> entry : COMBOS.entrySet()) {
            KeyMapping mapping = findMapping(mc, entry.getKey());
            if (mapping == null) continue;
            Combo combo = entry.getValue();
            boolean down = combo.isDown(window);
            if (down && !combo.wasDown()) {
                mapping.setDown(true);
                if (mapping instanceof KeyMappingAccessor accessor) {
                    accessor.tacRogue$setClickCount(accessor.tacRogue$getClickCount() + 1);
                }
            } else if (down) {
                mapping.setDown(true);
            } else if (combo.wasDown()) {
                mapping.setDown(false);
            }
            combo.setWasDown(down);
        }
    }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        COMBOS.clear();
        if (!Files.exists(CONFIG_PATH)) return;
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
            props.load(reader);
            for (String name : props.stringPropertyNames()) {
                List<InputConstants.Key> keys = parse(props.getProperty(name));
                if (!keys.isEmpty()) COMBOS.put(name, new Combo(keys, false));
            }
        } catch (IOException ignored) {
            COMBOS.clear();
        }
    }

    private static void save() {
        Properties props = new Properties();
        for (Map.Entry<String, Combo> entry : COMBOS.entrySet()) {
            props.setProperty(entry.getKey(), serialize(entry.getValue().keys()));
        }
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (var writer = Files.newBufferedWriter(CONFIG_PATH)) {
                props.store(writer, "TacZ Roguelike custom key combos");
            }
        } catch (IOException ignored) {
        }
    }

    private static void releaseAll(Minecraft mc) {
        if (mc == null) return;
        for (Map.Entry<String, Combo> entry : COMBOS.entrySet()) {
            Combo combo = entry.getValue();
            if (!combo.wasDown()) continue;
            KeyMapping mapping = findMapping(mc, entry.getKey());
            if (mapping != null) mapping.setDown(false);
            combo.setWasDown(false);
        }
    }

    private static KeyMapping findMapping(Minecraft mc, String name) {
        if (mc.options == null) return null;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping.getName().equals(name)) return mapping;
        }
        return null;
    }

    private static List<InputConstants.Key> copyUnique(List<InputConstants.Key> keys) {
        List<InputConstants.Key> out = new ArrayList<>();
        for (InputConstants.Key key : keys) {
            if (key == null || key == InputConstants.UNKNOWN || out.contains(key)) continue;
            out.add(key);
        }
        return out;
    }

    private static String serialize(List<InputConstants.Key> keys) {
        StringBuilder sb = new StringBuilder();
        for (InputConstants.Key key : keys) {
            if (sb.length() > 0) sb.append("+");
            sb.append(key.getName());
        }
        return sb.toString();
    }

    private static List<InputConstants.Key> parse(String value) {
        List<InputConstants.Key> keys = new ArrayList<>();
        if (value == null || value.isBlank()) return keys;
        for (String part : value.split("\\+")) {
            try {
                InputConstants.Key key = InputConstants.getKey(part.trim());
                if (key != InputConstants.UNKNOWN && !keys.contains(key)) keys.add(key);
            } catch (Exception ignored) {
            }
        }
        return keys;
    }

    private record Combo(List<InputConstants.Key> keys, boolean initialWasDown) {
        private static final Map<Combo, Boolean> STATE = new java.util.WeakHashMap<>();

        boolean wasDown() {
            return STATE.getOrDefault(this, initialWasDown);
        }

        void setWasDown(boolean value) {
            STATE.put(this, value);
        }

        boolean isDown(long window) {
            for (InputConstants.Key key : keys) {
                if (key.getType() == InputConstants.Type.KEYSYM) {
                    if (GLFW.glfwGetKey(window, key.getValue()) != GLFW.GLFW_PRESS) return false;
                } else if (key.getType() == InputConstants.Type.MOUSE) {
                    if (GLFW.glfwGetMouseButton(window, key.getValue()) != GLFW.GLFW_PRESS) return false;
                } else {
                    return false;
                }
            }
            return true;
        }
    }
}
