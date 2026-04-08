package com.levanilla.rogue.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 3D ダメージ表記 + ドロップ表記の管理と描画。
 */
public final class DamageIndicatorRenderer {

    private DamageIndicatorRenderer() {}

    // ===== ダメージ表記 =====
    private static final java.util.concurrent.CopyOnWriteArrayList<DamageIndicator> damageIndicators = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static class DamageIndicator {
        public final double x, y, z;
        public final float damage;
        public int ticksAlive;
        public final boolean isCritical;
        public final boolean isShotgun;
        public final double offsetX, offsetZ;

        public DamageIndicator(double x, double y, double z, float damage, boolean isCritical, boolean isShotgun) {
            this.x = x; this.y = y; this.z = z;
            this.damage = damage; this.ticksAlive = 0;
            this.isCritical = isCritical;
            this.isShotgun = isShotgun;
            this.offsetX = isShotgun ? (Math.random() - 0.5) * 1.5 : 0;
            this.offsetZ = isShotgun ? (Math.random() - 0.5) * 1.5 : 0;
        }
    }

    // ===== ドロップ表記 =====
    private static final java.util.concurrent.CopyOnWriteArrayList<DropIndicator> dropIndicators = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static class DropIndicator {
        public final double x, y, z;
        public final String name;
        public int ticksAlive;
        public DropIndicator(double x, double y, double z, String name) {
            this.x = x; this.y = y; this.z = z;
            this.name = name; this.ticksAlive = 0;
        }
    }

    // ===== 追加 API =====

    public static void addDamageIndicator(double x, double y, double z, float damage, boolean isCritical) {
        damageIndicators.add(new DamageIndicator(x, y, z, damage, isCritical, false));
    }

    public static void addShotgunDamageIndicator(double x, double y, double z, float damage, boolean isCritical) {
        damageIndicators.add(new DamageIndicator(x, y, z, damage, isCritical, true));
    }

    public static void addDropIndicator(double x, double y, double z, String itemName) {
        dropIndicators.add(new DropIndicator(x, y, z, itemName));
    }

    // ===== Tick =====

    public static void tick() {
        damageIndicators.removeIf(di -> {
            di.ticksAlive++;
            return di.ticksAlive > 40;
        });
        dropIndicators.removeIf(di -> {
            di.ticksAlive++;
            return di.ticksAlive > 60;
        });
    }

    public static boolean isEmpty() {
        return damageIndicators.isEmpty() && dropIndicators.isEmpty();
    }

    // ===== 3D 描画 =====

    public static void renderWorld(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        net.minecraft.client.Camera camera = event.getCamera();
        net.minecraft.world.phys.Vec3 camPos = camera.getPosition();
        com.mojang.blaze3d.vertex.PoseStack poseStack = event.getPoseStack();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // ダメージ表記
        for (DamageIndicator di : damageIndicators) {
            float progress = di.ticksAlive / 40.0f;
            float alpha = 1.0f - (progress * progress);
            float yOffset = (float)(Math.log1p(di.ticksAlive * 0.3) * 0.6);

            double dx = di.x - camPos.x;
            double dy = di.y - camPos.y;
            double dz = di.z - camPos.z;
            if (dx * dx + dy * dy + dz * dz > 900) continue;

            String text;
            int color;
            float sizeScale = di.isShotgun ? 0.7f : Math.min(2.0f, 1.0f + di.damage / 50.0f);

            if (di.isCritical) {
                text = "§e§l✦ " + String.format("%.0f", di.damage) + " ✦";
                color = 0xFFFFAA00;
            } else if (di.damage >= 15) {
                text = "§6§l" + String.format("%.0f", di.damage);
                color = 0xFFFF5555;
            } else {
                text = "§f" + String.format("%.0f", di.damage);
                color = 0xFFFFFFFF;
            }

            int alphaInt = Math.max(0, Math.min(255, (int)(alpha * 255)));
            int finalColor = (alphaInt << 24) | (color & 0xFFFFFF);

            poseStack.pushPose();
            poseStack.translate(di.x + di.offsetX, di.y + yOffset, di.z + di.offsetZ);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-camera.getYRot()));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(camera.getXRot()));

            float scale = 0.025f * sizeScale;
            poseStack.scale(-scale, -scale, scale);

            org.joml.Matrix4f matrix4f = poseStack.last().pose();
            float xPosition = (float)(-mc.font.width(text) / 2);
            mc.font.drawInBatch(text, xPosition, 0, finalColor, false, matrix4f, bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
            poseStack.popPose();
        }

        // ドロップ表記
        for (DropIndicator di : dropIndicators) {
            float alpha = di.ticksAlive < 40 ? 1.0f : 1.0f - ((di.ticksAlive - 40) / 20.0f);
            float yOffset = (float)(Math.log1p(di.ticksAlive * 0.2) * 0.5);

            double dx = di.x - camPos.x;
            double dy = di.y - camPos.y;
            double dz = di.z - camPos.z;
            if (dx * dx + dy * dy + dz * dz > 900) continue;

            String text = "§a+ " + di.name;
            int alphaInt = Math.max(0, Math.min(255, (int)(alpha * 255)));
            int finalColor = (alphaInt << 24) | 0x55FF55;

            poseStack.pushPose();
            poseStack.translate(di.x, di.y + yOffset + 0.5, di.z);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-camera.getYRot()));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(camera.getXRot()));
            poseStack.scale(-0.02f, -0.02f, 0.02f);

            org.joml.Matrix4f matrix4f = poseStack.last().pose();
            float xPos = (float)(-mc.font.width(text) / 2);
            mc.font.drawInBatch(text, xPos, 0, finalColor, false, matrix4f, bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
            poseStack.popPose();
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }
}
