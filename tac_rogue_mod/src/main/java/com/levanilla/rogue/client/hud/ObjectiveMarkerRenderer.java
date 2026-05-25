package com.levanilla.rogue.client.hud;

import com.levanilla.rogue.core.ClientRunState;
import com.levanilla.rogue.core.RunManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 目的地点の控えめなワールドマーカー。
 * DisplayMode.NORMAL で描くので、壁越しESPにはしない。
 */
public final class ObjectiveMarkerRenderer {
    private static final double MAX_DISTANCE_SQR = 64.0D * 64.0D;

    private ObjectiveMarkerRenderer() {}

    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (!RunManager.isRunActive() || RunManager.isFloorCleared()) return;
        if (!"tac_rogue".equals(mc.level.dimension().location().getNamespace())) return;

        ClientRunState.ObjectiveState objective = ClientRunState.getObjectiveState();
        if (objective == null || objective.complete() || !objective.hasTarget()) return;

        Vec3 target = new Vec3(objective.targetX() + 0.5D, objective.targetY() + 1.2D, objective.targetZ() + 0.5D);
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        if (target.distanceToSqr(cameraPos) > MAX_DISTANCE_SQR) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            renderBillboard(mc, poseStack, buffer, camera, cameraPos, target, objective);
            buffer.endBatch();
        } finally {
            RenderSystem.disableBlend();
        }
    }

    private static void renderBillboard(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                                        Camera camera, Vec3 cameraPos, Vec3 target,
                                        ClientRunState.ObjectiveState objective) {
        String label = Component.translatable(
            "objective.tac_rogue." + objective.type().toLowerCase(java.util.Locale.ROOT) + ".title").getString();
        float distance = (float)Math.sqrt(target.distanceToSqr(cameraPos));
        float scale = Math.max(0.018F, Math.min(0.032F, distance * 0.0018F));
        renderText(mc, poseStack, buffer, camera, cameraPos, target.x, target.y + 0.38D, target.z,
            "◇", 0xFFFFD166, 0.95F, scale * 1.35F);
        renderText(mc, poseStack, buffer, camera, cameraPos, target.x, target.y + 0.08D, target.z,
            label, 0xFFE9F0F4, 0.86F, scale);
        renderText(mc, poseStack, buffer, camera, cameraPos, target.x, target.y - 0.24D, target.z,
            "│", 0xFFFFD166, 0.52F, scale * 1.10F);
    }

    private static void renderText(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                                   Camera camera, Vec3 cameraPos,
                                   double x, double y, double z, String text,
                                   int rgb, float alpha, float scale) {
        if (text == null || text.isBlank() || alpha <= 0.01F) return;
        poseStack.pushPose();
        poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-scale, -scale, scale);

        Font font = mc.font;
        Matrix4f matrix = poseStack.last().pose();
        int argb = ((int)(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) << 24) | (rgb & 0xFFFFFF);
        font.drawInBatch(text, -font.width(text) / 2.0F, -font.lineHeight / 2.0F,
            argb, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
