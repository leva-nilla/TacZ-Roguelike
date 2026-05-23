package com.levanilla.rogue.client;

import com.levanilla.rogue.client.compat.LeaWindsCompat;
import com.levanilla.rogue.client.hud.WelcomeScreen;
import com.levanilla.rogue.core.ClientRunState;
import com.levanilla.rogue.core.smoke.SmokeLogger;
import com.levanilla.rogue.mixin.CreateWorldScreenAccessor;
import com.levanilla.rogue.mixin.MixinScreenAccessor;
import com.levanilla.rogue.networking.RogueActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.util.Locale;

public final class SmokeClientAutomation {
    private static boolean initialized;
    private static boolean commandSent;
    private static boolean finished;
    private static int ticks;
    private static int step;
    private static String mode;
    private static String smokeWorld;
    private static String runId;
    private static boolean autoCreateWorld;
    private static boolean autoEnterDungeon;
    private static boolean createWorldScreenOpened;
    private static boolean createWorldInvoked;
    private static boolean createWorldConfirmationPressed;
    private static boolean dungeonEnterRequested;
    private static boolean dungeonReadyLogged;
    private static boolean welcomeDismissed;
    private static boolean starterGearSelected;
    private static boolean initialPerkSelected;
    private static int dungeonEnterRequestTick;
    private static int dungeonEnterFirstRequestTick;

    private SmokeClientAutomation() {}

    public static void tick(Minecraft mc) {
        if (!isEnabled() || finished) return;
        if (!initialized) init();

        ticks++;
        if (ticks < 20) return;

        if (mc.player == null || mc.level == null) {
            tickMenuOnly(mc);
            return;
        }

        if (autoEnterDungeon && handleBlockingSmokeScreen(mc)) {
            return;
        }

        if (autoEnterDungeon && !isInRogueDimension(mc)) {
            tickAutoEnterDungeon(mc);
            return;
        }

        if (autoEnterDungeon && !dungeonReadyLogged) {
            dungeonReadyLogged = true;
            SmokeLogger.pass("client", "client.dungeon_entered", "client enters rogue dungeon dimension",
                mc.level.dimension().location().toString(), "", 0L);
        }

        if (!commandSent && mc.getConnection() != null) {
            String suite = normalizeMode(mode);
            mc.getConnection().sendCommand("rogue_admin debug smoke " + suite);
            commandSent = true;
            SmokeLogger.pass("client", "client.command_sent", "send smoke command after world join",
                "/rogue_admin debug smoke " + suite, "", 0L);
        }

        if (ticks % 20 != 0) return;
        runScreenStep(mc);
    }

    private static void init() {
        mode = System.getProperty("tacrogue.smokeMode", "quick");
        smokeWorld = System.getProperty("tacrogue.smokeWorld", "TacRogueSmokeWorld");
        autoCreateWorld = Boolean.parseBoolean(System.getProperty("tacrogue.smokeAutoCreateWorld", "false"));
        autoEnterDungeon = Boolean.parseBoolean(System.getProperty("tacrogue.smokeAutoEnterDungeon", "false"));
        runId = SmokeLogger.startRun("client-" + normalizeMode(mode));
        initialized = true;
        SmokeLogger.pass("client", "client.init", "client smoke automation starts",
            "mode=" + mode + " runId=" + runId + " autoEnterDungeon=" + autoEnterDungeon, "", 0L);
    }

    private static void tickMenuOnly(Minecraft mc) {
        if (autoCreateWorld && !smokeWorldExists(mc)) {
            tickAutoCreateWorld(mc);
            return;
        }

        if (ticks == 40) {
            Screen screen = mc.screen;
            SmokeLogger.pass("client", "client.menu_reached", "client reaches a menu screen",
                screen == null ? "none" : screen.getClass().getSimpleName(), "", 0L);
            if (screen instanceof TitleScreen) {
                mc.setScreen(new TacRogueTitleScreen());
                SmokeLogger.pass("client", "client.custom_title_open", "custom title can be opened",
                    "TacRogueTitleScreen", "", 0L);
            }
        }
        if (ticks == 80 && mc.screen instanceof TacRogueTitleScreen) {
            mc.setScreen(new RogueWorldSelectScreen(mc.screen));
            SmokeLogger.pass("client", "client.world_select_open", "custom world select can be opened",
                "RogueWorldSelectScreen", "", 0L);
        }
        if (ticks > 140 && Boolean.getBoolean("tacrogue.smokeAutoQuit")) {
            SmokeLogger.skip("client", "client.world_join", "quickPlaySingleplayer loads smoke world",
                "no world loaded", "Create TacRogueSmokeWorld once or pass an existing quickPlay world", 0L);
            finish(mc);
        }
    }

    private static void tickAutoCreateWorld(Minecraft mc) {
        if (ticks == 40) {
            Screen screen = mc.screen;
            SmokeLogger.pass("client", "client.menu_reached", "client reaches a menu screen",
                screen == null ? "none" : screen.getClass().getSimpleName(), "", 0L);
        }

        if (!createWorldScreenOpened && ticks >= 60) {
            Screen parent = mc.screen == null ? new TitleScreen() : mc.screen;
            try {
                CreateWorldScreen.openFresh(mc, parent);
                createWorldScreenOpened = true;
                SmokeLogger.pass("client", "client.smoke_world_create_screen", "smoke world create screen opens",
                    smokeWorld, "", 0L);
            } catch (Throwable ex) {
                SmokeLogger.fail("client", "client.smoke_world_create_screen", "smoke world create screen opens",
                    ex.getClass().getSimpleName(), ex.getMessage(), 0L);
                finish(mc);
            }
            return;
        }

        if (createWorldScreenOpened && !createWorldInvoked && ticks >= 100) {
            if (!(mc.screen instanceof CreateWorldScreen screen)) {
                SmokeLogger.skip("client", "client.smoke_world_create_invoke", "smoke world create screen is active",
                    mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), "", 0L);
                return;
            }
            try {
                screen.getUiState().setName(smokeWorld);
                ((CreateWorldScreenAccessor) screen).invokeOnCreate();
                createWorldInvoked = true;
                SmokeLogger.pass("client", "client.smoke_world_create_invoke", "smoke world creation is invoked",
                    smokeWorld, "", 0L);
            } catch (Throwable ex) {
                SmokeLogger.fail("client", "client.smoke_world_create_invoke", "smoke world creation is invoked",
                    ex.getClass().getSimpleName(), ex.getMessage(), 0L);
                finish(mc);
            }
            return;
        }

        if (createWorldInvoked && !createWorldConfirmationPressed && ticks >= 120
            && mc.screen != null && !(mc.screen instanceof CreateWorldScreen)) {
            if (pressFirstActiveButton(mc.screen)) {
                createWorldConfirmationPressed = true;
                SmokeLogger.pass("client", "client.smoke_world_confirm", "smoke world confirmation is accepted",
                    mc.screen.getClass().getSimpleName(), "", 0L);
            }
            return;
        }

        if (ticks > 900 && Boolean.getBoolean("tacrogue.smokeAutoQuit")) {
            SmokeLogger.fail("client", "client.smoke_world_join_timeout", "auto-created smoke world loads",
                "timeout screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()), smokeWorld, 0L);
            finish(mc);
        }
    }

    private static boolean smokeWorldExists(Minecraft mc) {
        if (smokeWorld == null || smokeWorld.isBlank()) return false;
        try {
            return Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(smokeWorld));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean pressFirstActiveButton(Screen screen) {
        try {
            for (Renderable renderable : ((MixinScreenAccessor) screen).tacRogue$getRenderables()) {
                if (renderable instanceof Button button && button.visible && button.active) {
                    button.onPress();
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void tickAutoEnterDungeon(Minecraft mc) {
        if (mc.getConnection() == null) return;

        boolean firstRequest = !dungeonEnterRequested;
        if (firstRequest || ticks - dungeonEnterRequestTick >= 100) {
            TacRogueNetworking.CHANNEL.sendToServer(
                new RogueActionMessage(RogueActionMessage.ActionType.START_NEXT_FLOOR, "solo"));
            dungeonEnterRequested = true;
            dungeonEnterRequestTick = ticks;
            if (firstRequest) {
                dungeonEnterFirstRequestTick = ticks;
                SmokeLogger.pass("client", "client.dungeon_enter_request", "send floor entry packet",
                    "RogueActionMessage.START_NEXT_FLOOR", "solo", 0L);
            }
            return;
        }

        if (dungeonEnterFirstRequestTick > 0
            && ticks - dungeonEnterFirstRequestTick > 1200
            && Boolean.getBoolean("tacrogue.smokeAutoQuit")) {
            SmokeLogger.fail("client", "client.dungeon_enter_timeout", "client enters rogue dungeon dimension",
                mc.level.dimension().location().toString(), "timeout waiting for floor entry", 0L);
            finish(mc);
        }
    }

    private static boolean handleBlockingSmokeScreen(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) return false;

        if (screen instanceof WelcomeScreen) {
            TacRogueNetworking.CHANNEL.sendToServer(
                new RogueActionMessage(RogueActionMessage.ActionType.TUTORIAL_DONE, ""));
            if (mc.player != null) {
                mc.player.addTag("rogue:tutorial_seen");
            }
            ClientPreferenceManager.markWelcomeSeenForCurrentWorld();
            mc.options.save();
            mc.setScreen(null);
            resetDungeonEnterRequest();
            if (!welcomeDismissed) {
                welcomeDismissed = true;
                SmokeLogger.pass("client", "client.welcome_dismissed", "first-run welcome screen dismissed",
                    "WelcomeScreen", "", 0L);
            }
            return true;
        }

        if (screen instanceof StarterGearScreen) {
            TacRogueNetworking.CHANNEL.sendToServer(
                new RogueActionMessage(RogueActionMessage.ActionType.SELECT_GEAR_BALANCED, ""));
            mc.setScreen(null);
            resetDungeonEnterRequest();
            if (!starterGearSelected) {
                starterGearSelected = true;
                SmokeLogger.pass("client", "client.starter_gear_selected", "starter gear auto-selected",
                    "BALANCED", "", 0L);
            }
            return true;
        }

        if (screen instanceof FloorClearScreen || screen instanceof PerkScreen) {
            if (pressFirstActiveButton(screen)) {
                resetDungeonEnterRequest();
                if (!initialPerkSelected) {
                    initialPerkSelected = true;
                    SmokeLogger.pass("client", "client.initial_perk_selected", "initial perk auto-selected",
                        screen.getClass().getSimpleName(), "", 0L);
                }
                return true;
            }
        }

        return false;
    }

    private static void resetDungeonEnterRequest() {
        dungeonEnterRequested = false;
        dungeonEnterRequestTick = 0;
        dungeonEnterFirstRequestTick = 0;
    }

    private static boolean isInRogueDimension(Minecraft mc) {
        return mc.level != null
            && "tac_rogue".equals(mc.level.dimension().location().getNamespace())
            && "rogue_dimension".equals(mc.level.dimension().location().getPath());
    }

    private static void runScreenStep(Minecraft mc) {
        switch (step++) {
            case 0 -> openAndLog(mc, new DebugMenuScreen(), "debug_gui");
            case 1 -> closeAndLog(mc, "debug_gui");
            case 2 -> {
                ClientEventHandler.openInventoryWithTab(RogueInventoryScreen.Tab.STATUS);
                SmokeLogger.pass("client", "client.status_screen_open", "status screen can be opened",
                    "RogueInventoryScreen.STATUS", "", 0L);
            }
            case 3 -> closeAndLog(mc, "status_screen");
            case 4 -> openAndLog(mc, new ShopScreen(), "shop_gui");
            case 5 -> closeAndLog(mc, "shop_gui");
            case 6 -> openAndLog(mc, new QuestScreen(), "quest_gui");
            case 7 -> closeAndLog(mc, "quest_gui");
            case 8 -> openAndLog(mc, new DeepOperationsScreen(), "deep_gui");
            case 9 -> closeAndLog(mc, "deep_gui");
            case 10 -> {
                ClientEventHandler.openFloorSelectionScreen(Math.max(1, ClientRunState.getMaxReachedFloor()), 0L);
                SmokeLogger.pass("client", "client.floor_select_open", "floor select screen can be opened",
                    "FloorSelectionScreen", "", 0L);
            }
            case 11 -> closeAndLog(mc, "floor_select");
            case 12 -> {
                if (runsExtendedClientChecks()) {
                    runThirdPersonCameraToggle(mc);
                } else {
                    finish(mc);
                }
            }
            case 13 -> runThirdPersonAimResolver(mc);
            case 14 -> runShootingClientCheck(mc);
            case 15 -> runEnemyDirectionStateCheck();
            default -> finish(mc);
        }
    }

    private static boolean runsExtendedClientChecks() {
        String normalized = normalizeMode(mode);
        return switch (normalized) {
            case "full", "thirdperson", "shooting", "monster" -> true;
            default -> false;
        };
    }

    private static void runThirdPersonCameraToggle(Minecraft mc) {
        CameraType previous = mc.options.getCameraType();
        try {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            SmokeLogger.pass("client", "client.thirdperson.camera_toggle", "camera can switch to third person",
                mc.options.getCameraType().name(), "", 0L);
        } catch (Throwable ex) {
            SmokeLogger.fail("client", "client.thirdperson.camera_toggle", "camera can switch to third person",
                ex.getClass().getSimpleName(), ex.getMessage(), 0L);
        } finally {
            mc.options.setCameraType(previous);
        }
    }

    private static void runThirdPersonAimResolver(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            SmokeLogger.skip("client", "client.thirdperson.aim_resolver", "third-person aim resolver callable",
                "no client world", "", 0L);
            return;
        }
        try {
            LeaWindsCompat.GunAimResult result = LeaWindsCompat.resolveThirdPersonGunAimForRender(mc, 64.0D);
            SmokeLogger.pass("client", "client.thirdperson.aim_resolver", "third-person aim resolver callable",
                result == null ? "no target" : result.target().toString(), "", 0L);
        } catch (Throwable ex) {
            SmokeLogger.fail("client", "client.thirdperson.aim_resolver", "third-person aim resolver callable",
                ex.getClass().getSimpleName(), ex.getMessage(), 0L);
        }
    }

    private static void runShootingClientCheck(Minecraft mc) {
        if (mc.player == null) {
            SmokeLogger.skip("client", "client.shooting.holding_gun", "TacZ gun hold state can be read",
                "no client player", "", 0L);
            return;
        }
        try {
            boolean holding = LeaWindsCompat.isHoldingTacZGun(mc.player);
            if (holding) {
                SmokeLogger.pass("client", "client.shooting.holding_gun", "TacZ gun hold state can be read",
                    "holding TacZ gun", "", 0L);
            } else {
                SmokeLogger.skip("client", "client.shooting.holding_gun", "TacZ gun hold state can be read",
                    "not holding TacZ gun", "server full smoke prepares a representative gun after world join", 0L);
            }
        } catch (Throwable ex) {
            SmokeLogger.fail("client", "client.shooting.holding_gun", "TacZ gun hold state can be read",
                ex.getClass().getSimpleName(), ex.getMessage(), 0L);
        }
    }

    private static void runEnemyDirectionStateCheck() {
        ClientRunState.setEnemyDirection(1.0D, 0.0D, 2, 1000L);
        ClientRunState.EnemyDirectionState state = ClientRunState.getEnemyDirectionState();
        boolean valid = state != null && state.count() == 2 && Math.abs(state.dx() - 1.0D) < 0.001D;
        if (valid) {
            SmokeLogger.pass("client", "client.monster.enemy_direction_state", "enemy direction HUD state can be set/read",
                "dx=" + state.dx() + " dz=" + state.dz() + " count=" + state.count(), "", 0L);
        } else {
            SmokeLogger.fail("client", "client.monster.enemy_direction_state", "enemy direction HUD state can be set/read",
                String.valueOf(state), "", 0L);
        }
        ClientRunState.clearEnemyDirection();
    }

    private static void openAndLog(Minecraft mc, Screen screen, String caseId) {
        mc.setScreen(screen);
        SmokeLogger.pass("client", "client." + caseId + "_open", caseId + " opens",
            screen.getClass().getSimpleName(), "", 0L);
    }

    private static void closeAndLog(Minecraft mc, String caseId) {
        mc.setScreen(null);
        SmokeLogger.pass("client", "client." + caseId + "_close", caseId + " closes",
            "closed", "", 0L);
    }

    private static void finish(Minecraft mc) {
        if (finished) return;
        finished = true;
        SmokeLogger.pass("client", "client.finish", "client smoke automation finishes",
            "finished", "", 0L);
        if (Boolean.getBoolean("tacrogue.smokeAutoQuit")) {
            mc.stop();
        }
    }

    private static boolean isEnabled() {
        String value = System.getProperty("tacrogue.smokeMode", "");
        return value != null && !value.isBlank() && !"off".equalsIgnoreCase(value);
    }

    private static String normalizeMode(String value) {
        if (value == null || value.isBlank()) return "quick";
        String lower = value.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "quick", "floor", "combat", "economy", "quest", "deep", "ui", "registry",
                "world", "thirdperson", "shooting", "monster", "generation", "generation_view", "all", "full" -> lower;
            default -> "quick";
        };
    }
}
