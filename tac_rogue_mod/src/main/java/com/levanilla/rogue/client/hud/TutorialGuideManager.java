package com.levanilla.rogue.client.hud;

import com.levanilla.rogue.client.ClientPreferenceManager;
import com.levanilla.rogue.client.ClientKeyBinds;
import com.levanilla.rogue.client.QuestScreen;
import com.levanilla.rogue.client.ShopScreen;
import com.levanilla.rogue.client.StarterGearScreen;
import com.levanilla.rogue.core.RunManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public final class TutorialGuideManager {
    private static Step current = Step.LOBBY_OPERATIONS;
    private static int stepTicks = 0;
    private static int idleTicks = 0;

    private TutorialGuideManager() {}

    public static void resetForWorldJoin() {
        current = Step.LOBBY_OPERATIONS;
        stepTicks = 0;
        idleTicks = 0;
    }

    public static void restartGuideForCurrentWorld() {
        ClientPreferenceManager.clearTutorialGuideCompletedForCurrentWorld();
        resetForWorldJoin();
    }

    public static java.util.List<GuideEntry> guideEntries() {
        java.util.List<GuideEntry> entries = new java.util.ArrayList<>();
        for (Step step : Step.values()) {
            entries.add(new GuideEntry(step.titleKey, step.bodyKey));
        }
        return java.util.Collections.unmodifiableList(entries);
    }

    public static void tick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            idleTicks = 0;
            return;
        }
        if (ClientPreferenceManager.hasCompletedTutorialGuideForCurrentWorld()) return;
        if (!ClientPreferenceManager.hasSeenWelcomeForCurrentWorld()) return;
        if (!isRogueContext(mc)) return;

        stepTicks++;
        if (isStepComplete(mc, current) || stepTicks >= current.maxTicks) {
            advance();
        }
    }

    public static void advanceManually(Minecraft mc) {
        if (!canShow(mc)) return;
        if (mc.screen != null) return;
        if (stepTicks < 20) return;
        advance();
    }

    public static void render(GuiGraphics graphics, Minecraft mc, int width, int height) {
        if (!canShow(mc)) return;
        if (mc.screen != null) return;
        if (stepTicks < 12) return;

        idleTicks++;
        float alpha = Math.min(1.0F, Math.min(stepTicks - 12, idleTicks) / 18.0F);
        drawPanel(graphics, mc, width, height, alpha);
    }

    private static boolean canShow(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return false;
        if (ClientPreferenceManager.hasCompletedTutorialGuideForCurrentWorld()) return false;
        if (!ClientPreferenceManager.hasSeenWelcomeForCurrentWorld()) return false;
        return isRogueContext(mc);
    }

    private static void advance() {
        int next = current.ordinal() + 1;
        if (next >= Step.values().length) {
            ClientPreferenceManager.markTutorialGuideCompletedForCurrentWorld();
            return;
        }
        current = Step.values()[next];
        stepTicks = 0;
        idleTicks = 0;
    }

    private static boolean isStepComplete(Minecraft mc, Step step) {
        Player player = mc.player;
        return switch (step) {
            case LOBBY_OPERATIONS -> mc.screen instanceof QuestScreen;
            case LOBBY_LOADOUT -> mc.screen instanceof ShopScreen || mc.screen instanceof StarterGearScreen;
            case PERSPECTIVE -> mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                || mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT;
            case THIRD_PERSON_AIM -> !mc.options.getCameraType().isFirstPerson() && isHoldingTacZGun(player);
            case STEALTH -> RunManager.isRunActive()
                && (player.isShiftKeyDown() || player.hasPose(Pose.CROUCHING) || player.hasPose(Pose.SWIMMING));
            case SOUND -> RunManager.isRunActive() && stepTicks >= 220;
            case FLOOR_CLEAR -> RunManager.isFloorCleared() || RunManager.getCurrentFloor() > 1;
        };
    }

    private static boolean isHoldingTacZGun(Player player) {
        if (player == null) return false;
        try {
            return com.tacz.guns.api.item.IGun.mainHandHoldGun(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isRogueContext(Minecraft mc) {
        if (mc.level == null) return RunManager.isRunActive();
        return mc.level.dimension().location().getNamespace().equals("tac_rogue") || RunManager.isRunActive();
    }

    private static void drawPanel(GuiGraphics graphics, Minecraft mc, int width, int height, float alpha) {
        int boxW = Math.min(330, Math.max(230, width - 24));
        HotbarRenderer.Bounds hotbar = HotbarRenderer.bounds(width, height);
        int x = Math.max(8, Math.min(width - boxW - 8, hotbar.x()));
        int y = Math.max(8, hotbar.y() - 66);

        int bodyWidth = boxW - 20;
        java.util.List<net.minecraft.util.FormattedCharSequence> body =
            mc.font.split(Component.translatable(current.bodyKey), bodyWidth);
        int boxH = Math.max(58, 30 + body.size() * 10 + 20);
        y = Math.max(8, y - Math.max(0, boxH - 56));

        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int bg = (a * 210 / 255 << 24) | 0x071118;
        int line = (a * 180 / 255 << 24) | 0x55DDAA;
        int text = (a << 24) | 0xE8FFF7;
        int muted = (a * 210 / 255 << 24) | 0x93A5B1;
        int accent = (a << 24) | 0xFFD166;

        RenderSystem.enableBlend();
        graphics.fill(x, y, x + boxW, y + boxH, bg);
        graphics.fill(x, y, x + 2, y + boxH, line);
        graphics.fill(x, y, x + boxW, y + 1, line);
        graphics.renderOutline(x, y, boxW, boxH, (a * 90 / 255 << 24) | 0x55DDAA);

        graphics.drawString(mc.font, Component.translatable("hud.tac_rogue.tutorial.header",
            current.ordinal() + 1, Step.values().length), x + 10, y + 7, muted, false);
        graphics.drawString(mc.font, Component.translatable(current.titleKey), x + 10, y + 18, accent, false);
        int ty = y + 31;
        for (net.minecraft.util.FormattedCharSequence lineText : body) {
            graphics.drawString(mc.font, lineText, x + 10, ty, text, false);
            ty += 10;
        }
        graphics.drawString(mc.font, Component.translatable("hud.tac_rogue.tutorial.next_hint",
            ClientKeyBinds.TUTORIAL_NEXT.getTranslatedKeyMessage()), x + 10, y + boxH - 12, muted, false);
    }

    private enum Step {
        LOBBY_OPERATIONS("hud.tac_rogue.tutorial.lobby.title", "hud.tac_rogue.tutorial.lobby.body", 280),
        LOBBY_LOADOUT("hud.tac_rogue.tutorial.loadout.title", "hud.tac_rogue.tutorial.loadout.body", 260),
        PERSPECTIVE("hud.tac_rogue.tutorial.perspective.title", "hud.tac_rogue.tutorial.perspective.body", 260),
        THIRD_PERSON_AIM("hud.tac_rogue.tutorial.crosshair.title", "hud.tac_rogue.tutorial.crosshair.body", 300),
        STEALTH("hud.tac_rogue.tutorial.stealth.title", "hud.tac_rogue.tutorial.stealth.body", 340),
        SOUND("hud.tac_rogue.tutorial.sound.title", "hud.tac_rogue.tutorial.sound.body", 300),
        FLOOR_CLEAR("hud.tac_rogue.tutorial.clear.title", "hud.tac_rogue.tutorial.clear.body", 300);

        final String titleKey;
        final String bodyKey;
        final int maxTicks;

        Step(String titleKey, String bodyKey, int maxTicks) {
            this.titleKey = titleKey;
            this.bodyKey = bodyKey;
            this.maxTicks = maxTicks;
        }
    }

    public record GuideEntry(String titleKey, String bodyKey) {}
}
