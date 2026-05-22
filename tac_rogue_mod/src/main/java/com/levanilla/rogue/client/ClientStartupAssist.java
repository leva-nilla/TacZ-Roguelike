package com.levanilla.rogue.client;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ClientStartupAssist {
    public static final String MAKEUP_ULTRA_FAST = "MakeUp-UltraFast-9.5a.zip";

    private ClientStartupAssist() {}

    public static void ensureDefaultShaderConfig() {
        Properties props = loadOculusProperties();
        boolean changed = false;
        if (!props.containsKey("colorSpace")) {
            props.setProperty("colorSpace", "SRGB");
            changed = true;
        }
        if (!props.containsKey("disableUpdateMessage")) {
            props.setProperty("disableUpdateMessage", "false");
            changed = true;
        }
        if (!props.containsKey("enableDebugOptions")) {
            props.setProperty("enableDebugOptions", "false");
            changed = true;
        }
        if (!props.containsKey("maxShadowRenderDistance")) {
            props.setProperty("maxShadowRenderDistance", "24");
            changed = true;
        }
        if (!props.containsKey("enableShaders")) {
            props.setProperty("enableShaders", "true");
            changed = true;
        }
        if (!props.containsKey("shaderPack") && Boolean.parseBoolean(props.getProperty("enableShaders", "true"))) {
            props.setProperty("shaderPack", MAKEUP_ULTRA_FAST);
            changed = true;
        }
        if (changed) {
            saveOculusProperties(props);
        }
    }

    public static boolean isShaderEnabled() {
        Properties props = loadOculusProperties();
        return Boolean.parseBoolean(props.getProperty("enableShaders", "true"))
            && MAKEUP_ULTRA_FAST.equals(props.getProperty("shaderPack", MAKEUP_ULTRA_FAST));
    }

    public static void setShaderEnabled(boolean enabled) {
        Properties props = loadOculusProperties();
        props.setProperty("colorSpace", props.getProperty("colorSpace", "SRGB"));
        props.setProperty("disableUpdateMessage", props.getProperty("disableUpdateMessage", "false"));
        props.setProperty("enableDebugOptions", props.getProperty("enableDebugOptions", "false"));
        props.setProperty("maxShadowRenderDistance", props.getProperty("maxShadowRenderDistance", "24"));
        props.setProperty("enableShaders", Boolean.toString(enabled));
        props.setProperty("shaderPack", enabled ? MAKEUP_ULTRA_FAST : "");
        saveOculusProperties(props);
    }

    public static String shaderStatusText() {
        return isShaderEnabled() ? "ON / MakeUp Ultra Fast" : "OFF";
    }

    public static long maxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
    }

    public static String memoryAdvice() {
        long mb = maxMemoryMb();
        if (mb < 4096L) {
            return "RAM " + mb + " MB / 推奨 6144-8192 MB: かなり少なめ";
        }
        if (mb < 6144L) {
            return "RAM " + mb + " MB / 推奨 6144-8192 MB: 少し少なめ";
        }
        if (mb > 10240L) {
            return "RAM " + mb + " MB / 推奨 6144-8192 MB: 多め";
        }
        return "RAM " + mb + " MB / 推奨範囲内";
    }

    private static Properties loadOculusProperties() {
        Properties props = new Properties();
        Path path = oculusPath();
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException ignored) {
            }
        }
        return props;
    }

    private static void saveOculusProperties(Properties props) {
        Path path = oculusPath();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "TacZ Roguelike shader startup preference");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path oculusPath() {
        return FMLPaths.CONFIGDIR.get().resolve("oculus.properties");
    }
}
