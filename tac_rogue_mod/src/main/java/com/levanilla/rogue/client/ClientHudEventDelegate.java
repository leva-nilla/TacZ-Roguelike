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
    private static final double CROSSHAIR_SMOOTHING = 0.35D;
    private static final double CROSSHAIR_SNAP_DISTANCE = 36.0D;
    private static final SmoothCrosshair gunCrosshairSmoothing = new SmoothCrosshair();
    private static final SmoothCrosshair interactionCrosshairSmoothing = new SmoothCrosshair();
    private static CachedGunAim cachedGunAim;
    private static CachedInteractionAim cachedInteractionAim;

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

        HudContext context = HudContext.of(mc);
        if (!context.enabled()) return;

        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            event.setCanceled(true);

            Player player = mc.player;
            if (player == null || player.isSpectator()) return;

            GuiGraphics graphics = event.getGuiGraphics();
            int width = event.getWindow().getGuiScaledWidth();
            int height = event.getWindow().getGuiScaledHeight();

            ClientPerformanceProfiler.onHudRenderStart();
            try {
                long sectionStart = ClientPerformanceProfiler.onHudSectionStart();
                HotbarRenderer.render(graphics, mc, player, width, height);
                ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.HOTBAR, sectionStart);
                sectionStart = ClientPerformanceProfiler.onHudSectionStart();
                HudRenderer.render(graphics, mc, player, width, height);
                ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.STATUS, sectionStart);
                PublicCoopWaitState.render(graphics, mc, width, height);
                if (context.dungeon()) {
                    DamageIndicatorRenderer.renderGui(graphics, mc, width, height);
                }
                if (context.lobby() || context.dungeon()) {
                    TutorialGuideManager.render(graphics, mc, width, height);
                }
                sectionStart = ClientPerformanceProfiler.onHudSectionStart();
                NotificationManager.render(graphics, mc, width, height);
                ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.NOTIFICATION, sectionStart);
                sectionStart = ClientPerformanceProfiler.onHudSectionStart();
                NotificationManager.renderPopups(graphics, mc, width, height);
                ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.POPUP, sectionStart);
                sectionStart = ClientPerformanceProfiler.onHudSectionStart();
                renderBackgroundLoadIndicator(graphics, mc, width);
                ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.PREWARM, sectionStart);
                renderThirdPersonCrosshair(graphics, mc, player, width, height);
                if (context.dungeon() && RunManager.isRunActive()) {
                    renderStealthTakedownHint(graphics, mc, player, width, height);
                    renderObjectiveInteractHint(graphics, mc, width, height);
                }
                DebugAiOverlayManager.render(graphics, mc, width, height);
            } finally {
                ClientPerformanceProfiler.onHudRenderEnd();
            }
        }
    }

    private static void renderThirdPersonCrosshair(GuiGraphics graphics, Minecraft mc, Player player, int width, int height) {
        long perfStart = ClientPerformanceProfiler.onHudSectionStart();
        try {
            if (mc.options.getCameraType().isFirstPerson()) return;
            net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
            if (isTacZGunStack(mainHand)) {
                renderThirdPersonGunCrosshair(graphics, mc, width, height);
            } else {
                renderThirdPersonInteractionCrosshair(graphics, mc, width, height);
            }
        } finally {
            ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.CROSSHAIR, perfStart);
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

            long traceStart = ClientPerformanceProfiler.onProfileSectionStart();
            LeaWindsCompat.GunAimResult aim;
            try {
                aim = resolveCachedGunAim(mc);
            } finally {
                ClientPerformanceProfiler.onProfileSectionEnd(
                    ClientPerformanceProfiler.ProfileSection.THIRD_PERSON_CROSSHAIR_TRACE,
                    traceStart);
            }
            if (aim == null || aim.target() == null) return;

            ScreenPoint targetPoint = projectToScreen(mc, aim.target(), width, height);
            if (targetPoint != null) {
                ScreenPoint smoothPoint = gunCrosshairSmoothing.update(targetPoint, aim.cameraObstructed(), width, height);
                cx = smoothPoint.x();
                cy = smoothPoint.y();
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
            long traceStart = ClientPerformanceProfiler.onProfileSectionStart();
            LeaWindsCompat.InteractionAimResult aim;
            try {
                aim = resolveCachedInteractionAim(mc);
            } finally {
                ClientPerformanceProfiler.onProfileSectionEnd(
                    ClientPerformanceProfiler.ProfileSection.THIRD_PERSON_CROSSHAIR_TRACE,
                    traceStart);
            }
            if (aim == null || aim.target() == null) return;
            ScreenPoint point = projectToScreen(mc, aim.target(), width, height);
            if (point == null) return;
            point = interactionCrosshairSmoothing.update(point, aim.reachable(), width, height);

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

    private static LeaWindsCompat.GunAimResult resolveCachedGunAim(Minecraft mc) {
        AimFingerprint fingerprint = AimFingerprint.capture(mc, true);
        if (cachedGunAim != null && cachedGunAim.matches(fingerprint)) {
            return cachedGunAim.result;
        }
        LeaWindsCompat.GunAimResult result = LeaWindsCompat.resolveThirdPersonGunAimForRender(mc, 96.0D);
        cachedGunAim = new CachedGunAim(fingerprint, result);
        return result;
    }

    private static LeaWindsCompat.InteractionAimResult resolveCachedInteractionAim(Minecraft mc) {
        AimFingerprint fingerprint = AimFingerprint.capture(mc, false);
        if (cachedInteractionAim != null && cachedInteractionAim.matches(fingerprint)) {
            return cachedInteractionAim.result;
        }
        LeaWindsCompat.InteractionAimResult result = LeaWindsCompat.resolveThirdPersonInteractionTargetForRender(mc);
        cachedInteractionAim = new CachedInteractionAim(fingerprint, result);
        return result;
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

    private static final class SmoothCrosshair {
        private boolean initialized;
        private boolean stateKey;
        private int screenWidth;
        private int screenHeight;
        private double x;
        private double y;
        private long lastNanos;

        ScreenPoint update(ScreenPoint target, boolean stateKey, int width, int height) {
            long now = System.nanoTime();
            boolean reset = !initialized
                || this.stateKey != stateKey
                || this.screenWidth != width
                || this.screenHeight != height
                || lastNanos <= 0L;

            if (reset) {
                snap(target, stateKey, width, height, now);
                return target;
            }

            double dx = target.x() - x;
            double dy = target.y() - y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance > CROSSHAIR_SNAP_DISTANCE) {
                snap(target, stateKey, width, height, now);
                return target;
            }

            double frameScale = Math.max(0.25D, Math.min(3.0D, (now - lastNanos) / 16_666_666.0D));
            double alpha = 1.0D - Math.pow(1.0D - CROSSHAIR_SMOOTHING, frameScale);
            x += dx * alpha;
            y += dy * alpha;
            lastNanos = now;
            return new ScreenPoint((int)Math.round(x), (int)Math.round(y));
        }

        private void snap(ScreenPoint target, boolean stateKey, int width, int height, long now) {
            initialized = true;
            this.stateKey = stateKey;
            this.screenWidth = width;
            this.screenHeight = height;
            this.x = target.x();
            this.y = target.y();
            this.lastNanos = now;
        }
    }

    private record CachedGunAim(AimFingerprint fingerprint, LeaWindsCompat.GunAimResult result) {
        boolean matches(AimFingerprint other) {
            return fingerprint.matches(other);
        }
    }

    private record CachedInteractionAim(AimFingerprint fingerprint, LeaWindsCompat.InteractionAimResult result) {
        boolean matches(AimFingerprint other) {
            return fingerprint.matches(other);
        }
    }

    private record AimFingerprint(long gameTime, int itemHash, int posX, int posY, int posZ, int yaw, int pitch, boolean gun) {
        static AimFingerprint capture(Minecraft mc, boolean gun) {
            long gameTime = mc.level == null ? -1L : mc.level.getGameTime();
            net.minecraft.world.entity.player.Player player = mc.player;
            net.minecraft.world.item.ItemStack stack = player == null ? net.minecraft.world.item.ItemStack.EMPTY : player.getMainHandItem();
            net.minecraft.world.phys.Vec3 pos = player == null ? net.minecraft.world.phys.Vec3.ZERO : player.getEyePosition(1.0F);
            int itemHash = stack.isEmpty() ? 0 : 31 * net.minecraft.world.item.Item.getId(stack.getItem()) + stack.getDamageValue()
                + (stack.getTag() == null ? 0 : stack.getTag().hashCode());
            return new AimFingerprint(
                gameTime,
                itemHash,
                quantize(pos.x, 0.02D),
                quantize(pos.y, 0.02D),
                quantize(pos.z, 0.02D),
                quantize(player == null ? 0.0D : player.getYRot(), 0.20D),
                quantize(player == null ? 0.0D : player.getXRot(), 0.20D),
                gun);
        }

        boolean matches(AimFingerprint other) {
            return other != null
                && this.gameTime == other.gameTime
                && this.itemHash == other.itemHash
                && this.posX == other.posX
                && this.posY == other.posY
                && this.posZ == other.posZ
                && this.yaw == other.yaw
                && this.pitch == other.pitch
                && this.gun == other.gun;
        }

        private static int quantize(double value, double step) {
            return (int)Math.round(value / step);
        }
    }

    private record HudContext(boolean enabled, boolean lobby, boolean dungeon) {
        static HudContext of(Minecraft mc) {
            if (mc.level == null) return new HudContext(RunManager.isRunActive(), false, false);
            net.minecraft.resources.ResourceLocation dimension = mc.level.dimension().location();
            boolean tacRogue = "tac_rogue".equals(dimension.getNamespace());
            boolean lobby = tacRogue && "lobby_dimension".equals(dimension.getPath());
            boolean dungeon = tacRogue && "rogue_dimension".equals(dimension.getPath());
            return new HudContext(tacRogue || RunManager.isRunActive(), lobby, dungeon);
        }
    }

    private static void renderBackgroundLoadIndicator(GuiGraphics graphics, Minecraft mc, int screenWidth) {
        if (mc.level == null || !"tac_rogue:lobby_dimension".equals(mc.level.dimension().location().toString())) return;
        PrewarmStatus status = readPrewarmStatus();
        if (status == null || !status.display || status.total <= 0) return;

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
            Component.translatable(!status.pending ? "hud.tac_rogue.loading.sound_cache_done" : status.started ? "hud.tac_rogue.loading.sound_cache" : "hud.tac_rogue.loading.waiting"),
            x + 7, y + 14, 0xFF8C99A6, false);
        graphics.fill(x + 7, y + h - 6, x + w - 7, y + h - 3, 0xFF17242C);
        graphics.fill(x + 7, y + h - 6, x + 7 + fillW, y + h - 3, 0xFF55DDAA);
        String count = loaded + "/" + status.total;
        graphics.drawString(mc.font, count, x + w - 7 - mc.font.width(count), y + 4, 0xFFD7E8EA, false);
    }

    private static PrewarmStatus readPrewarmStatus() {
        try {
            Class<?> manager = Class.forName("com.levanilla.taczstartuphelper.ClientPrewarmManager");
            boolean display = (boolean) manager.getMethod("shouldDisplayPrewarmStatus").invoke(null);
            if (!display) return null;
            boolean pending = (boolean) manager.getMethod("hasPendingPrewarm").invoke(null);
            int total = (int) manager.getMethod("getTotalSoundCount").invoke(null);
            int loaded = (int) manager.getMethod("getLoadedSoundCount").invoke(null);
            boolean started = (boolean) manager.getMethod("hasStartedPrewarm").invoke(null);
            float progress = (float) manager.getMethod("getProgress").invoke(null);
            return new PrewarmStatus(display, pending, started, total, loaded, progress);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record PrewarmStatus(boolean display, boolean pending, boolean started, int total, int loaded, float progress) {}
}
