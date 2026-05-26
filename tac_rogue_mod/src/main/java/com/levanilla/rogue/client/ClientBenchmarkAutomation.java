package com.levanilla.rogue.client;

import com.levanilla.rogue.core.ClientRunState;
import com.levanilla.rogue.core.smoke.SmokeLogger;
import com.levanilla.rogue.networking.RogueActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class ClientBenchmarkAutomation {
    private enum Step {
        WAIT_JOIN,
        ENSURE_LOBBY,
        LOBBY_TRACE,
        START_FLOOR,
        WAIT_DUNGEON,
        DUNGEON_TRACE,
        FINISH,
        FAILED
    }

    private enum GunPose {
        NONE,
        HOLD,
        ADS
    }

    private enum MovementMode {
        NONE,
        WALK_STRAIGHT,
        SPRINT_STRAIGHT,
        WALK_WANDER,
        SPRINT_WANDER,
        WALK_CIRCLE,
        SPRINT_CIRCLE
    }

    private enum CameraMode {
        KEEP,
        FIRST,
        THIRD
    }

    private static final String LOBBY_DIM = "tac_rogue:lobby_dimension";
    private static final String ROGUE_DIM = "tac_rogue:rogue_dimension";

    private static boolean initialized;
    private static boolean lobbyCommandSent;
    private static boolean loadoutRequestSent;
    private static boolean floorPrepSent;
    private static boolean floorStartSent;
    private static boolean finishRequested;
    private static Step step = Step.WAIT_JOIN;
    private static int stepTicks;
    private static int totalTicks;
    private static int lobbySeconds;
    private static int dungeonSeconds;
    private static int timeoutSeconds;
    private static String label;
    private static GunPose gunPose;
    private static MovementMode movementMode;
    private static CameraMode cameraMode;
    private static float movementYaw;
    private static int stuckTicks;

    private ClientBenchmarkAutomation() {}

    public static void tick(Minecraft mc) {
        if (!isEnabled() || mc == null || finishRequested) return;
        if (!initialized) init();

        totalTicks++;
        stepTicks++;
        if (totalTicks > timeoutSeconds * 20) {
            fail(mc, "timeout step=" + step.name().toLowerCase(Locale.ROOT));
            return;
        }

        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            return;
        }

        closeBlockingScreen(mc);

        switch (step) {
            case WAIT_JOIN -> next(Step.ENSURE_LOBBY, "world joined");
            case ENSURE_LOBBY -> ensureLobby(mc);
            case LOBBY_TRACE -> traceLobby(mc);
            case START_FLOOR -> startFloor(mc);
            case WAIT_DUNGEON -> waitDungeon(mc);
            case DUNGEON_TRACE -> traceDungeon(mc);
            case FINISH -> finish(mc);
            case FAILED -> {
            }
        }
    }

    private static void init() {
        initialized = true;
        step = Step.WAIT_JOIN;
        stepTicks = 0;
        totalTicks = 0;
        lobbyCommandSent = false;
        loadoutRequestSent = false;
        floorPrepSent = false;
        floorStartSent = false;
        finishRequested = false;
        disablePauseOnLostFocus();
        label = sanitize(System.getProperty("tacrogue.benchmarkLabel", "auto"));
        lobbySeconds = clampInt(System.getProperty("tacrogue.benchmarkLobbySeconds"), 10, 300, 30);
        dungeonSeconds = clampInt(System.getProperty("tacrogue.benchmarkDungeonSeconds"), 10, 300, 45);
        timeoutSeconds = clampInt(System.getProperty("tacrogue.benchmarkTimeoutSeconds"), 90, 900, 360);
        gunPose = parseGunPose(System.getProperty("tacrogue.benchmarkGunPose", "hold"));
        movementMode = parseMovementMode(System.getProperty("tacrogue.benchmarkMovement", "none"));
        cameraMode = parseCameraMode(System.getProperty("tacrogue.benchmarkCamera", "keep"));
        movementYaw = 0.0F;
        stuckTicks = 0;
        SmokeLogger.startRun("client-perf-" + label);
        SmokeLogger.pass("client-perf", "benchmark.init", "benchmark automation starts",
            "label=" + label + " lobby=" + lobbySeconds + " dungeon=" + dungeonSeconds
                + " gunPose=" + gunPose.name().toLowerCase(Locale.ROOT)
                + " movement=" + movementMode.name().toLowerCase(Locale.ROOT)
                + " camera=" + cameraMode.name().toLowerCase(Locale.ROOT), "", 0L);
    }

    private static void ensureLobby(Minecraft mc) {
        if (LOBBY_DIM.equals(dimension(mc))) {
            if (!ensureBenchmarkLoadout(mc)) return;
            stabilizeView(mc);
            ClientPerformanceProfiler.start(lobbySeconds, label + "_lobby");
            next(Step.LOBBY_TRACE, "lobby trace started");
            return;
        }

        if (!lobbyCommandSent || stepTicks % 100 == 0) {
            mc.getConnection().sendCommand("rogue_admin debug rebuild_lobby");
            lobbyCommandSent = true;
            SmokeLogger.pass("client-perf", "benchmark.lobby_command", "request lobby teleport",
                "sent", "", 0L);
        }
    }

    private static void traceLobby(Minecraft mc) {
        stabilizeView(mc);
        if (stepTicks >= (lobbySeconds + 2) * 20) {
            ClientPerformanceProfiler.stop();
            next(Step.START_FLOOR, "lobby trace finished");
        }
    }

    private static void startFloor(Minecraft mc) {
        if (!floorPrepSent) {
            mc.getConnection().sendCommand("rogue_admin debug setfloor 1");
            floorPrepSent = true;
            SmokeLogger.pass("client-perf", "benchmark.floor_prep", "prepare floor state",
                "setfloor 1", "", 0L);
            return;
        }
        if (stepTicks < 20) return;

        if (!floorStartSent) {
            TacRogueNetworking.CHANNEL.sendToServer(
                new RogueActionMessage(RogueActionMessage.ActionType.START_NEXT_FLOOR, "solo"));
            floorStartSent = true;
            SmokeLogger.pass("client-perf", "benchmark.floor_start", "request solo floor start",
                "sent", "", 0L);
        }
        next(Step.WAIT_DUNGEON, "floor start requested");
    }

    private static void waitDungeon(Minecraft mc) {
        closeBlockingScreen(mc);
        if (!ROGUE_DIM.equals(dimension(mc))) return;
        if (ClientRunState.getGenerationStatus() != null) return;
        if (stepTicks < 80) return;

        stabilizeView(mc);
        ClientPerformanceProfiler.start(dungeonSeconds, label + "_dungeon");
        next(Step.DUNGEON_TRACE, "dungeon trace started");
    }

    private static void traceDungeon(Minecraft mc) {
        closeBlockingScreen(mc);
        stabilizeView(mc);
        if (stepTicks >= (dungeonSeconds + 2) * 20) {
            ClientPerformanceProfiler.stop();
            next(Step.FINISH, "dungeon trace finished");
        }
    }

    private static void finish(Minecraft mc) {
        if (finishRequested) return;
        finishRequested = true;
        releaseInputKeys(mc);
        SmokeLogger.pass("client-perf", "benchmark.finish", "benchmark finishes",
            "finished", "", 0L);
        notify(mc, "FPS benchmark finished.");
        if (Boolean.getBoolean("tacrogue.benchmarkAutoQuit")) {
            mc.stop();
        }
    }

    private static void fail(Minecraft mc, String reason) {
        step = Step.FAILED;
        finishRequested = true;
        releaseInputKeys(mc);
        ClientPerformanceProfiler.stop();
        SmokeLogger.fail("client-perf", "benchmark.fail", "benchmark completes before timeout",
            reason, "", 0L);
        notify(mc, "FPS benchmark failed: " + reason);
        if (Boolean.getBoolean("tacrogue.benchmarkAutoQuit")) {
            mc.stop();
        }
    }

    private static void next(Step next, String detail) {
        SmokeLogger.pass("client-perf", "benchmark.step." + next.name().toLowerCase(Locale.ROOT),
            "advance benchmark step", detail, "", 0L);
        step = next;
        stepTicks = 0;
    }

    private static void closeBlockingScreen(Minecraft mc) {
        if (mc.screen != null && mc.player != null && mc.level != null) {
            mc.setScreen(null);
        }
    }

    private static void disablePauseOnLostFocus() {
        try {
            Minecraft.getInstance().options.pauseOnLostFocus = false;
        } catch (Throwable ignored) {
        }
    }

    private static void stabilizeView(Minecraft mc) {
        if (mc.player == null) return;
        updateMovementYaw(mc);
        float yaw = rotatesCamera() ? (stepTicks * 2.4F) % 360.0F : movementYaw;
        mc.player.setYRot(yaw);
        mc.player.setXRot(0.0F);
        mc.player.yRotO = yaw;
        mc.player.xRotO = 0.0F;
        applyCameraMode(mc);
        applyGunPose(mc);
        applyMovement(mc);
    }

    private static boolean ensureBenchmarkLoadout(Minecraft mc) {
        if (gunPose == GunPose.NONE || mc.player == null) return true;
        if (hasGunInSlot0(mc)) return true;

        if (!loadoutRequestSent) {
            TacRogueNetworking.CHANNEL.sendToServer(
                new RogueActionMessage(RogueActionMessage.ActionType.SELECT_GEAR_BALANCED, ""));
            loadoutRequestSent = true;
            SmokeLogger.pass("client-perf", "benchmark.loadout_request", "request benchmark gun loadout",
                "balanced", "", 0L);
        }
        return stepTicks > 40 && hasGunInSlot0(mc);
    }

    private static void applyGunPose(Minecraft mc) {
        if (mc.player == null) return;
        if (gunPose == GunPose.NONE) {
            mc.player.getInventory().selected = com.levanilla.rogue.core.GameConstants.SLOT_ITEM_START;
            releaseUseKey(mc);
            return;
        }

        mc.player.getInventory().selected = 0;
        mc.options.keyUse.setDown(gunPose == GunPose.ADS);
    }

    private static void applyMovement(Minecraft mc) {
        if (mc.options == null) return;
        boolean moving = movementMode != MovementMode.NONE;
        mc.options.keyUp.setDown(moving);
        mc.options.keySprint.setDown(movementMode == MovementMode.SPRINT_CIRCLE
            || movementMode == MovementMode.SPRINT_STRAIGHT
            || movementMode == MovementMode.SPRINT_WANDER);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    private static void applyCameraMode(Minecraft mc) {
        if (mc.options == null || cameraMode == CameraMode.KEEP) return;
        if (cameraMode == CameraMode.FIRST) {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        } else {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        }
    }

    private static boolean hasGunInSlot0(Minecraft mc) {
        if (mc.player == null) return false;
        var stack = mc.player.getInventory().getItem(0);
        var tag = stack.getTag();
        return !stack.isEmpty() && tag != null && tag.contains("GunId");
    }

    private static void releaseUseKey(Minecraft mc) {
        if (mc != null && mc.options != null) {
            mc.options.keyUse.setDown(false);
        }
    }

    private static void updateMovementYaw(Minecraft mc) {
        if (rotatesCamera()) return;
        if (movementMode == MovementMode.NONE || movementMode == MovementMode.WALK_STRAIGHT
            || movementMode == MovementMode.SPRINT_STRAIGHT) {
            movementYaw = 0.0F;
            stuckTicks = 0;
            return;
        }
        if (mc.player == null) return;
        boolean movingSlowly = horizontalSpeed(mc) < 0.025D;
        if (movingSlowly && stepTicks > 30) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        if (stuckTicks > 16) {
            movementYaw = (movementYaw + 103.0F) % 360.0F;
            stuckTicks = 0;
        } else if (stepTicks > 0 && stepTicks % 180 == 0) {
            movementYaw = (movementYaw + 37.0F) % 360.0F;
        }
    }

    private static void releaseInputKeys(Minecraft mc) {
        if (mc == null || mc.options == null) return;
        mc.options.keyUse.setDown(false);
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
    }

    private static String dimension(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().location().toString();
    }

    private static void notify(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[TacRogue PERF] " + text), false);
        }
    }

    private static boolean isEnabled() {
        return Boolean.getBoolean("tacrogue.benchmarkMode");
    }

    private static int clampInt(String value, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static GunPose parseGunPose(String value) {
        if (value == null || value.isBlank()) return GunPose.HOLD;
        try {
            return GunPose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return GunPose.HOLD;
        }
    }

    private static MovementMode parseMovementMode(String value) {
        if (value == null || value.isBlank()) return MovementMode.NONE;
        try {
            return MovementMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return MovementMode.NONE;
        }
    }

    private static CameraMode parseCameraMode(String value) {
        if (value == null || value.isBlank()) return CameraMode.KEEP;
        try {
            return CameraMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return CameraMode.KEEP;
        }
    }

    private static boolean rotatesCamera() {
        return movementMode == MovementMode.WALK_CIRCLE || movementMode == MovementMode.SPRINT_CIRCLE;
    }

    private static double horizontalSpeed(Minecraft mc) {
        if (mc == null || mc.player == null) return 0.0D;
        var delta = mc.player.getDeltaMovement();
        return Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    }

    private static String sanitize(String raw) {
        String value = raw == null || raw.isBlank() ? "auto" : raw;
        return value.replace(',', '_').replace(' ', '_').replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
