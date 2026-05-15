package com.levanilla.rogue.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * World-anchored billboard indicators.
 *
 * Indicators never retain entity references or screen anchors. They animate from
 * the immutable world position received at creation time.
 */
public final class DamageIndicatorRenderer {
    private static final int DAMAGE_LIFE_TICKS = 42;
    private static final int DROP_LIFE_TICKS = 64;
    private static final int MAX_DAMAGE_INDICATORS = 96;
    private static final int MAX_DROP_INDICATORS = 48;

    private static final CopyOnWriteArrayList<DamageIndicator> damageIndicators = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<DropIndicator> dropIndicators = new CopyOnWriteArrayList<>();

    private DamageIndicatorRenderer() {}

    private record DamageIndicator(
        Vec3 anchor,
        float damage,
        boolean critical,
        boolean headshot,
        boolean shotgun,
        long createdTick
    ) {}

    private record DropIndicator(Vec3 anchor, String name, long createdTick) {}

    public static void addDamageIndicator(double x, double y, double z, float damage, boolean isCritical, boolean isHeadShot) {
        addDamageIndicator(x, y, z, damage, isCritical, isHeadShot, false);
    }

    public static void addShotgunDamageIndicator(double x, double y, double z, float damage, boolean isCritical, boolean isHeadShot) {
        addDamageIndicator(x, y, z, damage, isCritical, isHeadShot, true);
    }

    private static void addDamageIndicator(double x, double y, double z, float damage, boolean isCritical, boolean isHeadShot, boolean isShotgun) {
        if (!isFinite(x, y, z) || !Float.isFinite(damage)) return;
        Vec3 anchor = new Vec3(x, y, z).add(stableSpread(isShotgun));
        damageIndicators.add(new DamageIndicator(anchor, damage, isCritical, isHeadShot, isShotgun, currentGameTick()));
        trim(damageIndicators, MAX_DAMAGE_INDICATORS);
    }

    public static void addDropIndicator(double x, double y, double z, String itemName) {
        if (!isFinite(x, y, z)) return;
        dropIndicators.add(new DropIndicator(new Vec3(x, y, z), itemName == null ? "" : itemName, currentGameTick()));
        trim(dropIndicators, MAX_DROP_INDICATORS);
    }

    public static void tick() {
        long now = currentGameTick();
        damageIndicators.removeIf(indicator -> now - indicator.createdTick() > DAMAGE_LIFE_TICKS);
        dropIndicators.removeIf(indicator -> now - indicator.createdTick() > DROP_LIFE_TICKS);
    }

    public static boolean isEmpty() {
        return damageIndicators.isEmpty() && dropIndicators.isEmpty();
    }

    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        long nowTick = mc.level.getGameTime();
        float partialTick = clamp(event.getPartialTick(), 0.0F, 1.0F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (DamageIndicator indicator : damageIndicators) {
            float age = ageTicks(indicator.createdTick(), nowTick, partialTick);
            if (age <= DAMAGE_LIFE_TICKS) {
                renderDamage(mc, poseStack, buffer, camera, cameraPos, indicator, age);
            }
        }
        for (DropIndicator indicator : dropIndicators) {
            float age = ageTicks(indicator.createdTick(), nowTick, partialTick);
            if (age <= DROP_LIFE_TICKS) {
                renderDrop(mc, poseStack, buffer, camera, cameraPos, indicator, age);
            }
        }

        buffer.endBatch();
        RenderSystem.disableBlend();
    }

    public static void renderGui(GuiGraphics graphics, Minecraft mc, int width, int height) {
        // Reserved for old call sites. Damage numbers are rendered as 3D billboards.
    }

    private static void renderDamage(
        Minecraft mc,
        PoseStack poseStack,
        MultiBufferSource.BufferSource buffer,
        Camera camera,
        Vec3 cameraPos,
        DamageIndicator indicator,
        float age
    ) {
        Vec3 anchor = indicator.anchor();
        double distanceSq = anchor.distanceToSqr(cameraPos);
        if (distanceSq > 1024.0D) return;

        float life = clamp(age / DAMAGE_LIFE_TICKS, 0.0F, 1.0F);
        float rise = 0.16F + easeOutCubic(life) * 0.54F;
        float alpha = age < 26.0F ? 1.0F : 1.0F - ((age - 26.0F) / 16.0F);
        alpha = clamp(alpha, 0.0F, 1.0F);

        int color = damageColor(indicator);
        String label = damageLabel(indicator);
        float pop = age < 5.0F ? 1.08F - (age / 5.0F) * 0.08F : 1.0F;
        float scale = 0.021F * pop * damageScale(indicator);

        renderBillboardText(mc, poseStack, buffer, camera, cameraPos,
            anchor.x, anchor.y + rise, anchor.z, label, color, alpha, scale, true);
    }

    private static void renderDrop(
        Minecraft mc,
        PoseStack poseStack,
        MultiBufferSource.BufferSource buffer,
        Camera camera,
        Vec3 cameraPos,
        DropIndicator indicator,
        float age
    ) {
        Vec3 anchor = indicator.anchor();
        double distanceSq = anchor.distanceToSqr(cameraPos);
        if (distanceSq > 1024.0D) return;

        float life = clamp(age / DROP_LIFE_TICKS, 0.0F, 1.0F);
        float rise = 0.24F + easeOutCubic(life) * 0.58F;
        float alpha = age < 44.0F ? 1.0F : 1.0F - ((age - 44.0F) / 20.0F);
        alpha = clamp(alpha, 0.0F, 1.0F);

        renderBillboardText(mc, poseStack, buffer, camera, cameraPos,
            anchor.x, anchor.y + rise, anchor.z, "+ " + indicator.name(), 0x55FF66, alpha, 0.019F, false);
    }

    private static void renderBillboardText(
        Minecraft mc,
        PoseStack poseStack,
        MultiBufferSource.BufferSource buffer,
        Camera camera,
        Vec3 cameraPos,
        double x,
        double y,
        double z,
        String text,
        int rgb,
        float alpha,
        float scale,
        boolean shadow
    ) {
        if (alpha <= 0.01F || text.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-scale, -scale, scale);

        Font font = mc.font;
        Matrix4f matrix = poseStack.last().pose();
        int argb = ((int)(alpha * 255.0F) << 24) | (rgb & 0xFFFFFF);
        float textX = -font.width(text) / 2.0F;
        float textY = -font.lineHeight / 2.0F;

        font.drawInBatch(text, textX, textY, argb, shadow, matrix, buffer,
            Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);

        poseStack.popPose();
    }

    private static String damageLabel(DamageIndicator indicator) {
        String value = String.format(Locale.ROOT, "%.1f", Math.max(0.0F, indicator.damage()));
        if (indicator.headshot()) return "HEAD " + value;
        if (indicator.critical()) return "CRIT " + value;
        return value;
    }

    private static int damageColor(DamageIndicator indicator) {
        if (indicator.headshot() && indicator.critical()) return 0xFF9A22;
        if (indicator.headshot()) return 0xFF3333;
        if (indicator.critical()) return 0xFFD84A;
        return indicator.shotgun() ? 0xD9F2FF : 0xFFFFFF;
    }

    private static float damageScale(DamageIndicator indicator) {
        float scale = indicator.shotgun() ? 0.76F : 1.0F;
        scale *= Math.min(1.35F, 1.0F + Math.max(0.0F, indicator.damage()) / 95.0F);
        if (indicator.headshot()) scale *= 1.12F;
        else if (indicator.critical()) scale *= 1.06F;
        return scale;
    }

    private static Vec3 stableSpread(boolean shotgun) {
        if (!shotgun) return Vec3.ZERO;
        double angle = Math.random() * Math.PI * 2.0D;
        double radius = 0.08D + Math.random() * 0.28D;
        return new Vec3(Math.cos(angle) * radius, (Math.random() - 0.5D) * 0.16D, Math.sin(angle) * radius);
    }

    private static float ageTicks(long createdTick, long nowTick, float partialTick) {
        return Math.max(0.0F, (nowTick - createdTick) + partialTick);
    }

    private static long currentGameTick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    private static boolean isFinite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    private static float easeOutCubic(float t) {
        float inv = 1.0F - clamp(t, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <T> void trim(CopyOnWriteArrayList<T> list, int max) {
        while (list.size() > max) {
            list.remove(0);
        }
    }
}
