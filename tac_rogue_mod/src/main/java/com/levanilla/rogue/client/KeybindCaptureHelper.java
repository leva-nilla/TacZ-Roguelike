package com.levanilla.rogue.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

public final class KeybindCaptureHelper {
    private KeybindCaptureHelper() {}

    public static boolean isModifierKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
            || keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
            || keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT;
    }

    public static KeyModifier modifierFromGlfw(int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) return KeyModifier.ALT;
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) return KeyModifier.CONTROL;
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) return KeyModifier.SHIFT;
        return KeyModifier.NONE;
    }

    public static KeyModifier modifierFromKey(InputConstants.Key key) {
        if (key == null || key.getType() != InputConstants.Type.KEYSYM) return KeyModifier.NONE;
        int code = key.getValue();
        if (code == GLFW.GLFW_KEY_LEFT_ALT || code == GLFW.GLFW_KEY_RIGHT_ALT) return KeyModifier.ALT;
        if (code == GLFW.GLFW_KEY_LEFT_CONTROL || code == GLFW.GLFW_KEY_RIGHT_CONTROL) return KeyModifier.CONTROL;
        if (code == GLFW.GLFW_KEY_LEFT_SHIFT || code == GLFW.GLFW_KEY_RIGHT_SHIFT) return KeyModifier.SHIFT;
        return KeyModifier.NONE;
    }

    public static boolean canUseForgeModifier(java.util.List<InputConstants.Key> keys) {
        if (keys.size() != 2) return false;
        return (modifierFromKey(keys.get(0)) != KeyModifier.NONE && modifierFromKey(keys.get(1)) == KeyModifier.NONE)
            || (modifierFromKey(keys.get(1)) != KeyModifier.NONE && modifierFromKey(keys.get(0)) == KeyModifier.NONE);
    }

    public static KeyModifier forgeModifier(java.util.List<InputConstants.Key> keys) {
        KeyModifier first = modifierFromKey(keys.get(0));
        return first != KeyModifier.NONE ? first : modifierFromKey(keys.get(1));
    }

    public static InputConstants.Key mainKey(java.util.List<InputConstants.Key> keys) {
        for (InputConstants.Key key : keys) {
            if (modifierFromKey(key) == KeyModifier.NONE) return key;
        }
        return keys.isEmpty() ? InputConstants.UNKNOWN : keys.get(keys.size() - 1);
    }

    public static MutableComponent comboLabel(java.util.List<InputConstants.Key> keys) {
        if (keys == null || keys.isEmpty()) return Component.literal("...");
        MutableComponent label = Component.literal("");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) label.append(" + ");
            label.append(keys.get(i).getDisplayName());
        }
        return label;
    }

    public static KeyModifier activeModifier(long window) {
        if (isDown(window, GLFW.GLFW_KEY_LEFT_ALT) || isDown(window, GLFW.GLFW_KEY_RIGHT_ALT)) return KeyModifier.ALT;
        if (isDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || isDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) return KeyModifier.CONTROL;
        if (isDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || isDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) return KeyModifier.SHIFT;
        return KeyModifier.NONE;
    }

    public static void assign(KeyMapping mapping, KeyModifier modifier, InputConstants.Key key) {
        if (mapping != null) {
            mapping.setKeyModifierAndCode(modifier, key);
            KeyMapping.resetMapping();
        }
    }

    private static boolean isDown(long window, int keyCode) {
        return GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
    }
}
