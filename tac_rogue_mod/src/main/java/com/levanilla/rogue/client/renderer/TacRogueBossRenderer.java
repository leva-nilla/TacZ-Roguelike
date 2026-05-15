package com.levanilla.rogue.client.renderer;

import com.levanilla.rogue.client.model.TacRogueBossModel;
import com.levanilla.rogue.world.TacRogueBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TacRogueBossRenderer extends MobRenderer<TacRogueBossEntity, TacRogueBossModel> {
    public static final ModelLayerLocation LAYER =
        new ModelLayerLocation(new ResourceLocation("tac_rogue", "boss"), "main");

    private static final ResourceLocation BREACHER =
        new ResourceLocation("tac_rogue", "textures/entity/boss/breacher.png");
    private static final ResourceLocation COMMANDER =
        new ResourceLocation("tac_rogue", "textures/entity/boss/commander.png");
    private static final ResourceLocation VOID_WARDEN =
        new ResourceLocation("tac_rogue", "textures/entity/boss/void_warden.png");
    private static final ResourceLocation PYRO =
        new ResourceLocation("tac_rogue", "textures/entity/boss/pyro.png");
    private static final ResourceLocation LEVIATHAN =
        new ResourceLocation("tac_rogue", "textures/entity/boss/leviathan.png");

    public TacRogueBossRenderer(EntityRendererProvider.Context context) {
        super(context, new TacRogueBossModel(context.bakeLayer(LAYER)), 0.85f);
    }

    @Override
    public ResourceLocation getTextureLocation(TacRogueBossEntity entity) {
        return switch (entity.getRole()) {
            case COMMANDER -> COMMANDER;
            case VOID_WARDEN -> VOID_WARDEN;
            case PYRO -> PYRO;
            case LEVIATHAN -> LEVIATHAN;
            default -> BREACHER;
        };
    }

    @Override
    protected void scale(TacRogueBossEntity entity, PoseStack poseStack, float partialTickTime) {
        float scale = switch (entity.getRole()) {
            case LEVIATHAN -> 1.65f;
            case VOID_WARDEN -> 1.55f;
            case COMMANDER -> 1.35f;
            default -> 1.45f;
        };
        poseStack.scale(scale, scale, scale);
    }
}
