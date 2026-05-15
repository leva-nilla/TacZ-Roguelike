package com.levanilla.rogue.client.model;

import com.levanilla.rogue.world.TacRogueBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class TacRogueBossModel extends EntityModel<TacRogueBossEntity> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart commandAntenna;
    private final ModelPart pyroTanks;
    private final ModelPart leviathanPlates;
    private final ModelPart voidCrown;

    public TacRogueBossModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.commandAntenna = root.getChild("command_antenna");
        this.pyroTanks = root.getChild("pyro_tanks");
        this.leviathanPlates = root.getChild("leviathan_plates");
        this.voidCrown = root.getChild("void_crown");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.5f, -8.5f, -4.5f, 9.0f, 8.5f, 9.0f, none)
            .texOffs(38, 0).addBox(-5.1f, -9.1f, -5.1f, 10.2f, 3.0f, 10.2f, new CubeDeformation(0.05f))
            .texOffs(80, 0).addBox(-3.6f, -5.7f, -5.0f, 7.2f, 3.0f, 0.7f, none)
            .texOffs(100, 0).addBox(-2.6f, -4.7f, -5.45f, 1.6f, 1.0f, 0.5f, none)
            .texOffs(106, 0).addBox(1.0f, -4.7f, -5.45f, 1.6f, 1.0f, 0.5f, none),
            PartPose.offset(0.0f, -1.0f, 0.0f));

        head.addOrReplaceChild("jaw_guard", CubeListBuilder.create()
            .texOffs(0, 20).addBox(-3.2f, -2.7f, -5.35f, 6.4f, 2.6f, 1.0f, none),
            PartPose.ZERO);

        root.addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(0, 28).addBox(-6.0f, 0.0f, -3.3f, 12.0f, 13.5f, 6.6f, none)
            .texOffs(40, 28).addBox(-6.8f, 0.8f, -4.0f, 13.6f, 5.0f, 8.0f, new CubeDeformation(0.06f))
            .texOffs(82, 28).addBox(-5.2f, 6.1f, -4.2f, 10.4f, 5.2f, 1.1f, none)
            .texOffs(0, 48).addBox(-4.6f, 2.0f, 3.15f, 9.2f, 9.5f, 2.4f, none)
            .texOffs(30, 50).addBox(-7.4f, 1.2f, -3.7f, 3.0f, 3.2f, 7.4f, none)
            .texOffs(52, 50).addBox(4.4f, 1.2f, -3.7f, 3.0f, 3.2f, 7.4f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
            .texOffs(0, 64).addBox(-4.0f, -1.0f, -2.4f, 4.8f, 13.5f, 4.8f, none)
            .texOffs(22, 64).addBox(-4.45f, -1.55f, -2.85f, 5.7f, 3.2f, 5.7f, none)
            .texOffs(48, 64).addBox(-4.35f, 8.3f, -2.75f, 5.4f, 4.0f, 5.4f, none),
            PartPose.offset(-7.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
            .texOffs(0, 84).addBox(-0.8f, -1.0f, -2.4f, 4.8f, 13.5f, 4.8f, none)
            .texOffs(22, 84).addBox(-1.25f, -1.55f, -2.85f, 5.7f, 3.2f, 5.7f, none)
            .texOffs(48, 84).addBox(-1.05f, 8.3f, -2.75f, 5.4f, 4.0f, 5.4f, none),
            PartPose.offset(7.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
            .texOffs(76, 60).addBox(-2.6f, 0.0f, -2.5f, 5.2f, 12.0f, 5.0f, none)
            .texOffs(100, 60).addBox(-2.95f, 8.4f, -2.9f, 5.9f, 3.8f, 5.8f, none),
            PartPose.offset(-3.1f, 13.0f, 0.0f));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
            .texOffs(76, 80).addBox(-2.6f, 0.0f, -2.5f, 5.2f, 12.0f, 5.0f, none)
            .texOffs(100, 80).addBox(-2.95f, 8.4f, -2.9f, 5.9f, 3.8f, 5.8f, none),
            PartPose.offset(3.1f, 13.0f, 0.0f));

        root.addOrReplaceChild("command_antenna", CubeListBuilder.create()
            .texOffs(0, 104).addBox(4.6f, -9.6f, 3.1f, 0.8f, 8.6f, 0.8f, none)
            .texOffs(6, 104).addBox(4.2f, -10.3f, 2.7f, 1.6f, 1.6f, 1.6f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("pyro_tanks", CubeListBuilder.create()
            .texOffs(18, 104).addBox(-5.6f, 2.0f, 4.8f, 3.0f, 10.0f, 3.0f, none)
            .texOffs(34, 104).addBox(2.6f, 2.0f, 4.8f, 3.0f, 10.0f, 3.0f, none)
            .texOffs(50, 104).addBox(-3.8f, 11.3f, 5.1f, 7.6f, 1.4f, 2.4f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("leviathan_plates", CubeListBuilder.create()
            .texOffs(74, 104).addBox(-6.7f, 5.4f, -4.8f, 13.4f, 2.0f, 1.3f, none)
            .texOffs(74, 110).addBox(-6.2f, 8.2f, -4.8f, 12.4f, 2.0f, 1.3f, none)
            .texOffs(74, 116).addBox(-5.2f, 11.0f, -4.6f, 10.4f, 1.8f, 1.2f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("void_crown", CubeListBuilder.create()
            .texOffs(108, 104).addBox(-5.5f, -11.0f, -5.0f, 1.2f, 3.0f, 1.2f, none)
            .texOffs(114, 104).addBox(4.3f, -11.0f, -5.0f, 1.2f, 3.0f, 1.2f, none)
            .texOffs(120, 104).addBox(-0.6f, -12.0f, -5.1f, 1.2f, 4.0f, 1.2f, none),
            PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(TacRogueBossEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;

        float swing = Mth.sin(limbSwing * 0.6662F) * 1.1F * limbSwingAmount;
        this.rightArm.xRot = -0.22f - swing;
        this.leftArm.xRot = -0.22f + swing;
        this.rightLeg.xRot = swing * 0.55f;
        this.leftLeg.xRot = -swing * 0.55f;
        this.body.yRot = Mth.sin(ageInTicks * 0.035f) * 0.018f;

        TacRogueBossEntity.BossRole role = entity.getRole();
        this.commandAntenna.visible = role == TacRogueBossEntity.BossRole.COMMANDER;
        this.pyroTanks.visible = role == TacRogueBossEntity.BossRole.PYRO;
        this.leviathanPlates.visible = role == TacRogueBossEntity.BossRole.LEVIATHAN;
        this.voidCrown.visible = role == TacRogueBossEntity.BossRole.VOID_WARDEN;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
