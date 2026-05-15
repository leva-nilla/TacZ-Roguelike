package com.levanilla.rogue.client.compat;

import com.github.leawind.thirdperson.ThirdPersonStatus;
import com.github.leawind.thirdperson.api.client.event.ThirdPersonCameraSetupEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ViewportEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RecoilDebugLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("TacRogue/RecoilDebug");
    private static long lastShotTimestamp = Long.MIN_VALUE;
    private static int shotSequence = 0;

    private RecoilDebugLogger() {
    }

    public static void logTacZCamera(String phase, ViewportEvent.ComputeCameraAngles event, boolean ownedByLeaWinds) {
        if (!isActive()) return;
        log(phase, "TACZ", event.getPitch(), event.getYaw(), ownedByLeaWinds, "");
    }

    public static void logLeaWindsCamera(String phase, ThirdPersonCameraSetupEvent event) {
        if (!isActive()) return;
        log(phase, "LEAWINDS", event.xRot, event.yRot, LeaWindsRecoilController.shouldOwnTacZRecoil(), "");
    }

    public static void logAimSync(String phase) {
        if (!isActive()) return;
        log(phase, "AIM_SYNC", Float.NaN, Float.NaN, LeaWindsRecoilController.shouldOwnTacZRecoil(), "");
    }

    private static boolean isActive() {
        if (!com.levanilla.rogue.core.RogueConfig.recoilDebugLogs()) return false;
        TacZReflection.init();
        long ts = TacZReflection.getShootTimeStamp();
        if (ts <= 0) return false;
        if (ts != lastShotTimestamp) {
            lastShotTimestamp = ts;
            shotSequence++;
            LOGGER.info("[recoil-debug] SHOT seq={} shootTs={} now={}", shotSequence, ts, System.currentTimeMillis());
        }
        return System.currentTimeMillis() - ts <= com.levanilla.rogue.core.RogueConfig.recoilDebugWindowMs();
    }

    private static void log(String phase, String source, float eventPitch, float eventYaw,
                            boolean ownedByLeaWinds, String extra) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float[] recoil = TacZReflection.getCurrentRecoilOffsets();
        float[] consumed = TacZReflection.getConsumedRecoilOffsets();
        float[] visual = LeaWindsRecoilController.getVisualRecoilOffsets();
        boolean leawindsRendering = false;
        try {
            leawindsRendering = LeaWindsCompat.isLeawindAvailable()
                && ThirdPersonStatus.isRenderingInThirdPerson();
        } catch (Throwable ignored) {
        }

        var camera = mc.gameRenderer == null ? null : mc.gameRenderer.getMainCamera();
        float cameraPitch = camera == null ? Float.NaN : camera.getXRot();
        float cameraYaw = camera == null ? Float.NaN : camera.getYRot();

        LOGGER.info(
            "[recoil-debug] seq={} phase={} source={} mode={} owned={} cameraType={} " +
                "event=({},{}) player=({},{}) mainCamera=({},{}) recoilAbs=({},{}) consumed=({},{}) visual=({},{}) {}",
            shotSequence,
            phase,
            source,
            leawindsRendering ? "LEAWINDS_3P" : "VANILLA_OR_FIRST",
            ownedByLeaWinds,
            mc.options.getCameraType(),
            fmt(eventPitch),
            fmt(eventYaw),
            fmt(mc.player.getXRot()),
            fmt(mc.player.getYRot()),
            fmt(cameraPitch),
            fmt(cameraYaw),
            fmt(recoil[0]),
            fmt(recoil[1]),
            fmt(consumed[0]),
            fmt(consumed[1]),
            fmt(visual[0]),
            fmt(visual[1]),
            extra == null ? "" : extra
        );
    }

    private static String fmt(float value) {
        if (Float.isNaN(value)) return "nan";
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
