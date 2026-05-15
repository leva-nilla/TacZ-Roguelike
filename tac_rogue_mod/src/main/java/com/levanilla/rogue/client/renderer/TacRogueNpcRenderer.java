package com.levanilla.rogue.client.renderer;

import com.levanilla.rogue.client.model.TacRogueNpcModel;
import com.levanilla.rogue.world.NpcManager;
import com.levanilla.rogue.world.TacRogueNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TacRogueNpcRenderer extends MobRenderer<TacRogueNpcEntity, TacRogueNpcModel> {
    public static final ModelLayerLocation LAYER =
        new ModelLayerLocation(new ResourceLocation("tac_rogue", "npc"), "main");

    private static final ResourceLocation COMMANDER =
        new ResourceLocation("tac_rogue", "textures/entity/npc/commander.png");
    private static final ResourceLocation QUARTERMASTER =
        new ResourceLocation("tac_rogue", "textures/entity/npc/quartermaster.png");
    private static final ResourceLocation INTEL =
        new ResourceLocation("tac_rogue", "textures/entity/npc/intel.png");
    private static final ResourceLocation MEDIC =
        new ResourceLocation("tac_rogue", "textures/entity/npc/medic.png");

    public TacRogueNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new TacRogueNpcModel(context.bakeLayer(LAYER)), 0.35f);
    }

    @Override
    public ResourceLocation getTextureLocation(TacRogueNpcEntity entity) {
        return switch (entity.getRole()) {
            case COMMANDER -> COMMANDER;
            case QUARTERMASTER -> QUARTERMASTER;
            case INTEL_OFFICER -> INTEL;
            case MEDIC -> MEDIC;
        };
    }

    @Override
    protected void scale(TacRogueNpcEntity entity, PoseStack poseStack, float partialTickTime) {
        NpcManager.NpcRole role = entity.getRole();
        switch (role) {
            case COMMANDER -> poseStack.scale(1.04f, 1.07f, 1.04f);
            case QUARTERMASTER -> poseStack.scale(1.14f, 1.06f, 1.14f);
            case INTEL_OFFICER -> poseStack.scale(0.92f, 1.02f, 0.92f);
            case MEDIC -> poseStack.scale(1.00f, 1.00f, 1.00f);
        }
    }
}
