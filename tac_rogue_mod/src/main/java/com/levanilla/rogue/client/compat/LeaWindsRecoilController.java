package com.levanilla.rogue.client.compat;

import com.github.leawind.thirdperson.ThirdPerson;
import com.github.leawind.thirdperson.ThirdPersonStatus;
import com.github.leawind.thirdperson.api.client.event.ThirdPersonCameraSetupEvent;
import com.github.leawind.thirdperson.core.CameraAgent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.Locale;

public final class LeaWindsRecoilController {
    private static final float RECOIL_RENDER_MULTIPLIER = 1.0f;
    private static final float EPSILON = 0.0001f;
    private static final double RECOVERY_HALF_LIFE_MS = 65.0D;
    private static final long RECOVERY_DELAY_MS = 0L;
    private static final String MODE = System.getProperty("tac_rogue.leawindsRecoilMode", "integrated")
        .trim()
        .toLowerCase(Locale.ROOT);
    private static long lastShootTimestamp = Long.MIN_VALUE;
    private static long lastUpdateMs = 0L;
    private static float lastRawPitch = 0f;
    private static float lastRawYaw = 0f;
    private static float visualPitch = 0f;
    private static float visualYaw = 0f;

    private LeaWindsRecoilController() {
    }

    public static boolean shouldOwnTacZRecoil() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
            && mc.level != null
            && LeaWindsCompat.isLeawindAvailable()
            && ThirdPerson.isAvailable()
            && ThirdPersonStatus.isRenderingInThirdPerson()
            && !mc.options.getCameraType().isFirstPerson()
            && IGun.mainHandHoldGun(mc.player);
    }

    public static boolean consumeTacZPlayerRecoilIfOwned() {
        if (!shouldOwnTacZRecoil()) return false;
        TacZReflection.init();
        TacZReflection.consumeCurrentRecoilFrame();
        return true;
    }

    public static boolean isRecoilActive() {
        if (!shouldOwnTacZRecoil()) return false;
        TacZReflection.init();
        float[] recoil = TacZReflection.getCurrentRecoilOffsets();
        return Math.abs(recoil[0]) >= EPSILON || Math.abs(recoil[1]) >= EPSILON
            || Math.abs(visualPitch) >= EPSILON || Math.abs(visualYaw) >= EPSILON;
    }

    public static void applyToLeaWindsRotation(CameraAgent cameraAgent) {
        if (!useIntegratedMode()) return;
        if (!shouldOwnTacZRecoil()) {
            resetVisualState();
            return;
        }

        TacZReflection.init();
        long now = System.currentTimeMillis();
        updateIntegratedRecoil(cameraAgent, now);
    }

    public static void applyToCamera(ThirdPersonCameraSetupEvent event) {
        RecoilDebugLogger.logLeaWindsCamera("LEAWINDS_CAMERA_BEFORE", event);
        if (useIntegratedMode()) {
            RecoilDebugLogger.logLeaWindsCamera("LEAWINDS_CAMERA_FINAL", event);
            return;
        }
        if (!useOverlayMode()) return;
        if (!shouldOwnTacZRecoil()) {
            resetVisualState();
            return;
        }

        TacZReflection.init();
        long now = System.currentTimeMillis();
        updateVisualRecoil(now);

        float pitch = visualPitch * RECOIL_RENDER_MULTIPLIER;
        float yaw = visualYaw * RECOIL_RENDER_MULTIPLIER;
        if (Math.abs(pitch) < EPSILON && Math.abs(yaw) < EPSILON) return;

        event.setRotation(
            Mth.clamp(event.xRot - pitch, -89.8f, 89.8f),
            event.yRot - yaw
        );
        RecoilDebugLogger.logLeaWindsCamera("LEAWINDS_CAMERA_AFTER", event);
    }

    public static float[] getVisualRecoilOffsets() {
        return new float[]{visualPitch, visualYaw};
    }

    private static void updateIntegratedRecoil(CameraAgent cameraAgent, long now) {
        float[] recoil = TacZReflection.getCurrentRecoilOffsets();
        long shootTimestamp = TacZReflection.getShootTimeStamp();

        if (shootTimestamp != lastShootTimestamp) {
            lastShootTimestamp = shootTimestamp;
            lastRawPitch = 0f;
            lastRawYaw = 0f;
        }

        float deltaPitch = recoil[0] - lastRawPitch;
        float deltaYaw = recoil[1] - lastRawYaw;
        lastRawPitch = recoil[0];
        lastRawYaw = recoil[1];

        if (Math.abs(deltaPitch) >= EPSILON || Math.abs(deltaYaw) >= EPSILON) {
            visualPitch += deltaPitch;
            visualYaw += deltaYaw;
            applyLeaWindsDelta(cameraAgent, deltaPitch, deltaYaw);
            RecoilDebugLogger.logAimSync("LEAWINDS_ROT_APPLIED");
        }

        long deltaMs = lastUpdateMs <= 0L ? 0L : Math.max(0L, now - lastUpdateMs);
        lastUpdateMs = now;
        boolean rawSettled = Math.abs(recoil[0]) < EPSILON && Math.abs(recoil[1]) < EPSILON;
        boolean recoveryReady = shootTimestamp <= 0L || now - shootTimestamp > RECOVERY_DELAY_MS;
        if (deltaMs > 0L && rawSettled && recoveryReady) {
            visualPitch = 0f;
            visualYaw = 0f;
        }
    }

    private static void applyLeaWindsDelta(CameraAgent cameraAgent, float deltaPitch, float deltaYaw) {
        cameraAgent.turnCamera(
            -deltaYaw * RECOIL_RENDER_MULTIPLIER,
            -deltaPitch * RECOIL_RENDER_MULTIPLIER
        );
    }

    private static void updateVisualRecoil(long now) {
        float[] recoil = TacZReflection.getCurrentRecoilOffsets();
        long shootTimestamp = TacZReflection.getShootTimeStamp();

        if (shootTimestamp != lastShootTimestamp) {
            lastShootTimestamp = shootTimestamp;
            lastRawPitch = 0f;
            lastRawYaw = 0f;
        }

        visualPitch += recoil[0] - lastRawPitch;
        visualYaw += recoil[1] - lastRawYaw;
        lastRawPitch = recoil[0];
        lastRawYaw = recoil[1];

        long deltaMs = lastUpdateMs <= 0L ? 0L : Math.max(0L, now - lastUpdateMs);
        lastUpdateMs = now;
        boolean rawSettled = Math.abs(recoil[0]) < EPSILON && Math.abs(recoil[1]) < EPSILON;
        boolean recoveryReady = shootTimestamp <= 0L || now - shootTimestamp > RECOVERY_DELAY_MS;
        if (deltaMs > 0L && rawSettled && recoveryReady) {
            double decay = Math.pow(0.5D, deltaMs / RECOVERY_HALF_LIFE_MS);
            visualPitch *= (float) decay;
            visualYaw *= (float) decay;
            if (Math.abs(visualPitch) < EPSILON) visualPitch = 0f;
            if (Math.abs(visualYaw) < EPSILON) visualYaw = 0f;
        }
    }

    private static boolean useIntegratedMode() {
        return !"overlay".equals(MODE) && !"legacy".equals(MODE) && !"off".equals(MODE);
    }

    private static boolean useOverlayMode() {
        return "overlay".equals(MODE) || "legacy".equals(MODE);
    }

    private static void resetVisualState() {
        lastShootTimestamp = Long.MIN_VALUE;
        lastUpdateMs = 0L;
        lastRawPitch = 0f;
        lastRawYaw = 0f;
        visualPitch = 0f;
        visualYaw = 0f;
    }
}
