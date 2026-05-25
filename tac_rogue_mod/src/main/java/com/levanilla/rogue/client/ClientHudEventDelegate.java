package com.levanilla.rogue.client;

import com.levanilla.rogue.client.compat.LeaWindsCompat;
import com.levanilla.rogue.client.hud.DamageIndicatorRenderer;
import com.levanilla.rogue.client.hud.HotbarRenderer;
import com.levanilla.rogue.client.hud.HudRenderer;
import com.levanilla.rogue.client.hud.NotificationManager;
import com.levanilla.rogue.client.hud.TutorialGuideManager;
import com.levanilla.rogue.core.ClientRunState;
import com.levanilla.rogue.core.RunManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

final class ClientHudEventDelegate {
    private ClientHudEventDelegate() {}

    static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientInputEventDelegate.isRogueContext(mc)) return;
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())
            && mc.options != null
            && !mc.options.getCameraType().isFirstPerson()) {
            event.setCanceled(true);
            return;
        }
        if (event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) ||
            event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id()) ||
            event.getOverlay().id().equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) ||
            event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) ||
            event.getOverlay().id().equals(VanillaGuiOverlay.AIR_LEVEL.id())) {
            event.setCanceled(true);
        }
    }

    static void onRenderHotbar(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean isRogueDim = mc.level.dimension().location().getNamespace().equals("tac_rogue");
        if (!isRogueDim && !RunManager.isRunActive()) return;

        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            event.setCanceled(true);

            Player player = mc.player;
            if (player == null || player.isSpectator()) return;

            GuiGraphics graphics = event.getGuiGraphics();
            int width = event.getWindow().getGuiScaledWidth();
            int height = event.getWindow().getGuiScaledHeight();

            HotbarRenderer.render(graphics, mc, player, width, height);
            HudRenderer.render(graphics, mc, player, width, height);
            PublicCoopWaitState.render(graphics, mc, width, height);
            DamageIndicatorRenderer.renderGui(graphics, mc, width, height);
            TutorialGuideManager.render(graphics, mc, width, height);
            NotificationManager.render(graphics, mc, width, height);
            NotificationManager.renderPopups(graphics, mc, width, height);
            renderBackgroundLoadIndicator(graphics, mc, width);
            renderThirdPersonCrosshair(graphics, mc, player, width, height);
            renderStealthTakedownHint(graphics, mc, player, width, height);
            renderObjectiveInteractHint(graphics, mc, width, height);
            DebugAiOverlayManager.render(graphics, mc, width, height);
        }
    }

    private static void renderThirdPersonCrosshair(GuiGraphics graphics, Minecraft mc, Player player, int width, int height) {
        if (mc.options.getCameraType().isFirstPerson()) return;
        net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
        if (isTacZGunStack(mainHand)) {
            renderThirdPersonGunCrosshair(graphics, mc, width, height);
        } else {
            renderThirdPersonInteractionCrosshair(graphics, mc, width, height);
        }
    }

    private static boolean isTacZGunStack(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (com.tacz.guns.api.item.IGun.getIGunOrNull(stack) != null) return true;
        } catch (Throwable ignored) {
        }
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("GunId");
    }

    private static void renderThirdPersonGunCrosshair(GuiGraphics graphics, Minecraft mc, int width, int height) {
        try {
            net.minecraft.resources.ResourceLocation crosshairLoc =
                com.tacz.guns.client.renderer.crosshair.CrosshairType.getTextureLocation(
                    com.tacz.guns.config.client.RenderConfig.CROSSHAIR_TYPE.get()
                );

            int texSize = 16;
            int cx = width / 2;
            int cy = height / 2;

            LeaWindsCompat.GunAimResult aim =
                LeaWindsCompat.resolveThirdPersonGunAimForRender(mc, 96.0D);
            if (aim == null || aim.target() == null) return;

            ScreenPoint targetPoint = projectToScreen(mc, aim.target(), width, height);
            if (targetPoint != null) {
                cx = targetPoint.x();
                cy = targetPoint.y();
            }

            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );

            if (aim.cameraObstructed()) {
                drawCameraObstructionMarker(graphics, cx, cy);
            }

            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.9f);

            graphics.blit(crosshairLoc, cx - texSize / 2, cy - texSize / 2, 0, 0, texSize, texSize, texSize, texSize);

            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Exception e) {
            // フォールバック
        }
    }

    private static void renderThirdPersonInteractionCrosshair(GuiGraphics graphics, Minecraft mc, int width, int height) {
        try {
            LeaWindsCompat.InteractionAimResult aim = LeaWindsCompat.resolveThirdPersonInteractionTargetForRender(mc);
            if (aim == null || aim.target() == null) return;
            ScreenPoint point = projectToScreen(mc, aim.target(), width, height);
            if (point == null) return;

            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            drawInteractionMarker(graphics, point.x(), point.y(), aim.reachable());
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Exception ignored) {
        }
    }

    private static ScreenPoint projectToScreen(Minecraft mc, net.minecraft.world.phys.Vec3 worldPos, int width, int height) {
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 camPos = camera.getPosition();
        org.joml.Vector4f pos = new org.joml.Vector4f(
            (float)(worldPos.x - camPos.x),
            (float)(worldPos.y - camPos.y),
            (float)(worldPos.z - camPos.z),
            1.0f
        );

        ClientEventHandler.lastViewMatrix.transform(pos);
        ClientEventHandler.lastProjectionMatrix.transform(pos);

        if (pos.w() <= 0.0f) return null;
        pos.div(pos.w());
        if (pos.z() <= -1.0f || pos.z() >= 1.0f) return null;
        int x = (int) ((pos.x() + 1.0f) / 2.0f * width);
        int y = (int) ((1.0f - pos.y()) / 2.0f * height);
        return new ScreenPoint(x, y);
    }

    private static void drawCameraObstructionMarker(GuiGraphics graphics, int x, int y) {
        int outer = 0xCC101820;
        int inner = 0xDDEE5B4A;
        graphics.fill(x - 10, y - 1, x - 7, y + 1, outer);
        graphics.fill(x + 8, y - 1, x + 11, y + 1, outer);
        graphics.fill(x - 1, y - 10, x + 1, y - 7, outer);
        graphics.fill(x - 1, y + 8, x + 1, y + 11, outer);
        graphics.fill(x - 9, y, x - 7, y + 1, inner);
        graphics.fill(x + 8, y, x + 10, y + 1, inner);
        graphics.fill(x, y - 9, x + 1, y - 7, inner);
        graphics.fill(x, y + 8, x + 1, y + 10, inner);
    }

    private static void drawInteractionMarker(GuiGraphics graphics, int x, int y, boolean reachable) {
        int outer = reachable ? 0xCC101820 : 0xAA101820;
        int inner = reachable ? 0xDDE7E1D5 : 0x887D8792;
        graphics.fill(x - 4, y, x - 1, y + 1, outer);
        graphics.fill(x + 2, y, x + 5, y + 1, outer);
        graphics.fill(x, y - 4, x + 1, y - 1, outer);
        graphics.fill(x, y + 2, x + 1, y + 5, outer);
        graphics.fill(x - 3, y, x - 1, y + 1, inner);
        graphics.fill(x + 2, y, x + 4, y + 1, inner);
        graphics.fill(x, y - 3, x + 1, y - 1, inner);
        graphics.fill(x, y + 2, x + 1, y + 4, inner);
    }

    private static void renderStealthTakedownHint(GuiGraphics graphics, Minecraft mc, Player player, int width, int height) {
        if (mc.level == null || player == null || player.isSpectator()) return;
        if (!"tac_rogue".equals(mc.level.dimension().location().getNamespace())) return;
        if (RunManager.isFloorCleared()) return;
        if (isTacZGunStack(player.getMainHandItem())) return;

        int targetId = ClientRunState.getStealthTakedownTargetId();
        if (targetId < 0) return;
        net.minecraft.world.entity.Entity target = mc.level.getEntity(targetId);
        if (!(target instanceof net.minecraft.world.entity.Mob mob) || !mob.isAlive()) return;

        int x = width / 2;
        int y = height / 2;
        if (!mc.options.getCameraType().isFirstPerson()) {
            ScreenPoint point = projectToScreen(mc, mob.getEyePosition(1.0F).add(0.0D, -0.3D, 0.0D), width, height);
            if (point != null) {
                x = point.x();
                y = point.y();
            }
        }
        drawStealthHint(graphics, x, y);
    }

    private static void drawStealthHint(GuiGraphics graphics, int x, int y) {
        int outer = 0xD0081116;
        int inner = 0xFFE6D16A;
        int gap = 12;
        int len = 5;
        graphics.fill(x - gap - len, y - gap, x - gap, y - gap + 1, outer);
        graphics.fill(x - gap, y - gap, x - gap + 1, y - gap + len, outer);
        graphics.fill(x + gap, y - gap, x + gap + len, y - gap + 1, outer);
        graphics.fill(x + gap, y - gap, x + gap + 1, y - gap + len, outer);
        graphics.fill(x - gap - len, y + gap, x - gap, y + gap + 1, outer);
        graphics.fill(x - gap, y + gap - len, x - gap + 1, y + gap + 1, outer);
        graphics.fill(x + gap, y + gap, x + gap + len, y + gap + 1, outer);
        graphics.fill(x + gap, y + gap - len, x + gap + 1, y + gap + 1, outer);

        graphics.fill(x - gap - len + 1, y - gap, x - gap, y - gap + 1, inner);
        graphics.fill(x - gap, y - gap + 1, x - gap + 1, y - gap + len, inner);
        graphics.fill(x + gap, y - gap, x + gap + len - 1, y - gap + 1, inner);
        graphics.fill(x + gap, y - gap + 1, x + gap + 1, y - gap + len, inner);
        graphics.fill(x - gap - len + 1, y + gap, x - gap, y + gap + 1, inner);
        graphics.fill(x - gap, y + gap - len + 1, x - gap + 1, y + gap, inner);
        graphics.fill(x + gap, y + gap, x + gap + len - 1, y + gap + 1, inner);
        graphics.fill(x + gap, y + gap - len + 1, x + gap + 1, y + gap, inner);
    }

    private static void renderObjectiveInteractHint(GuiGraphics graphics, Minecraft mc, int width, int height) {
        if (mc.player == null || mc.player.isSpectator()) return;
        if (!ClientInputEventDelegate.hasObjectiveInteractTarget(mc)) return;

        String key = ClientInputEventDelegate.getTacZInteractKeyName(mc);
        Component text = Component.translatable("hud.tac_rogue.objective_interact", key);
        int textWidth = mc.font.width(text);
        int x = width / 2 - textWidth / 2;
        int y = height / 2 + 24;
        graphics.fill(x - 6, y - 4, x + textWidth + 6, y + 11, 0xB0061018);
        graphics.fill(x - 6, y - 4, x - 4, y + 11, 0xDD55DDAA);
        graphics.drawString(mc.font, text, x, y, 0xFFE7E1D5, true);
    }

    private record ScreenPoint(int x, int y) {}

    private static void renderBackgroundLoadIndicator(GuiGraphics graphics, Minecraft mc, int screenWidth) {
        if (mc.level == null || !"tac_rogue:lobby_dimension".equals(mc.level.dimension().location().toString())) return;
        PrewarmStatus status = readPrewarmStatus();
        if (status == null || !status.pending || status.total <= 0) return;

        int w = 142;
        int h = 29;
        int x = Math.max(8, screenWidth - w - 10);
        int y = 10;
        int loaded = Math.max(0, Math.min(status.loaded, status.total));
        float progress = Math.max(0.0F, Math.min(1.0F, status.progress));
        int fillW = Math.round((w - 14) * progress);

        graphics.fill(x, y, x + w, y + h, 0xD8041019);
        graphics.fill(x, y, x + 2, y + h, 0xEE55DDAA);
        graphics.renderOutline(x, y, w, h, 0xAA55DDAA);
        graphics.drawString(mc.font, Component.translatable("hud.tac_rogue.loading.title"), x + 7, y + 4, 0xFFBFFFF3, false);
        graphics.drawString(mc.font,
            Component.translatable(status.started ? "hud.tac_rogue.loading.sound_cache" : "hud.tac_rogue.loading.waiting"),
            x + 7, y + 14, 0xFF8C99A6, false);
        graphics.fill(x + 7, y + h - 6, x + w - 7, y + h - 3, 0xFF17242C);
        graphics.fill(x + 7, y + h - 6, x + 7 + fillW, y + h - 3, 0xFF55DDAA);
        String count = loaded + "/" + status.total;
        graphics.drawString(mc.font, count, x + w - 7 - mc.font.width(count), y + 4, 0xFFD7E8EA, false);
    }

    private static PrewarmStatus readPrewarmStatus() {
        try {
            Class<?> manager = Class.forName("com.levanilla.taczstartuphelper.ClientPrewarmManager");
            boolean pending = (boolean) manager.getMethod("hasPendingPrewarm").invoke(null);
            if (!pending) return null;
            int total = (int) manager.getMethod("getTotalSoundCount").invoke(null);
            int loaded = (int) manager.getMethod("getLoadedSoundCount").invoke(null);
            boolean started = (boolean) manager.getMethod("hasStartedPrewarm").invoke(null);
            float progress = (float) manager.getMethod("getProgress").invoke(null);
            return new PrewarmStatus(pending, started, total, loaded, progress);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record PrewarmStatus(boolean pending, boolean started, int total, int loaded, float progress) {}
}
