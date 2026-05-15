package com.levanilla.rogue.client.model;

import com.levanilla.rogue.world.NpcManager;
import com.levanilla.rogue.world.TacRogueNpcEntity;
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

public class TacRogueNpcModel extends EntityModel<TacRogueNpcEntity> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart facePlate;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart commanderBeret;
    private final ModelPart commanderBadge;
    private final ModelPart quartermasterHeadgear;
    private final ModelPart quartermasterPack;
    private final ModelPart quartermasterCrate;
    private final ModelPart quartermasterAmmo;
    private final ModelPart intelHeadset;
    private final ModelPart intelAntenna;
    private final ModelPart intelRadioPack;
    private final ModelPart intelTablet;
    private final ModelPart medicMask;
    private final ModelPart medicBag;
    private final ModelPart medicCross;
    private final ModelPart crate;
    private final ModelPart medkit;
    private final ModelPart tablet;

    public TacRogueNpcModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.facePlate = this.head.getChild("face_plate");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.commanderBeret = this.head.getChild("commander_beret");
        this.commanderBadge = root.getChild("commander_badge");
        this.quartermasterHeadgear = this.head.getChild("quartermaster_headgear");
        this.quartermasterPack = root.getChild("quartermaster_pack");
        this.quartermasterCrate = root.getChild("quartermaster_crate");
        this.quartermasterAmmo = root.getChild("quartermaster_ammo");
        this.intelHeadset = this.head.getChild("intel_headset");
        this.intelAntenna = root.getChild("intel_antenna");
        this.intelRadioPack = root.getChild("intel_radio_pack");
        this.intelTablet = root.getChild("intel_tablet");
        this.medicMask = this.head.getChild("medic_mask");
        this.medicBag = root.getChild("medic_bag");
        this.medicCross = root.getChild("medic_cross");
        this.crate = root.getChild("crate");
        this.medkit = root.getChild("medkit");
        this.tablet = root.getChild("tablet");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, none)
            .texOffs(32, 0).addBox(-4.4f, -8.45f, -4.4f, 8.8f, 3.2f, 8.8f, new CubeDeformation(0.05f))
            .texOffs(64, 0).addBox(-4.2f, -1.0f, -4.25f, 8.4f, 1.0f, 8.5f, new CubeDeformation(0.03f))
            .texOffs(96, 0).addBox(-3.2f, -0.2f, -4.3f, 6.4f, 1.2f, 1.0f, new CubeDeformation(0.02f)),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        head.addOrReplaceChild("face_plate", CubeListBuilder.create()
            .texOffs(0, 112).addBox(-3.5f, -6.9f, -4.62f, 7.0f, 5.6f, 0.3f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        head.addOrReplaceChild("commander_beret", CubeListBuilder.create()
            .texOffs(0, 96).addBox(-4.8f, -9.3f, -4.6f, 9.6f, 1.7f, 9.2f, none)
            .texOffs(40, 96).addBox(1.3f, -10.15f, -1.8f, 2.5f, 1.0f, 2.2f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        head.addOrReplaceChild("quartermaster_headgear", CubeListBuilder.create()
            .texOffs(44, 108).addBox(-4.75f, -8.95f, -4.8f, 9.5f, 1.6f, 9.6f, none)
            .texOffs(82, 108).addBox(-4.35f, -5.95f, -5.15f, 8.7f, 1.5f, 1.0f, none)
            .texOffs(104, 108).addBox(-4.9f, -6.2f, -1.1f, 1.0f, 2.2f, 2.2f, none)
            .texOffs(112, 108).addBox(3.9f, -6.2f, -1.1f, 1.0f, 2.2f, 2.2f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        head.addOrReplaceChild("intel_headset", CubeListBuilder.create()
            .texOffs(58, 96).addBox(-5.05f, -6.45f, -1.2f, 1.0f, 3.4f, 2.4f, none)
            .texOffs(66, 96).addBox(4.05f, -6.45f, -1.2f, 1.0f, 3.4f, 2.4f, none)
            .texOffs(74, 96).addBox(-4.0f, -8.85f, -0.55f, 8.0f, 0.9f, 1.1f, none)
            .texOffs(94, 96).addBox(3.35f, -3.85f, -5.35f, 1.0f, 1.0f, 4.5f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        head.addOrReplaceChild("medic_mask", CubeListBuilder.create()
            .texOffs(84, 108).addBox(-3.4f, -4.2f, -4.85f, 6.8f, 2.5f, 0.75f, none)
            .texOffs(100, 116).addBox(-0.45f, -8.85f, -4.75f, 0.9f, 2.5f, 0.6f, none)
            .texOffs(104, 116).addBox(-1.25f, -8.05f, -4.8f, 2.5f, 0.9f, 0.6f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        root.addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(0, 24).addBox(-4.5f, 0.0f, -2.5f, 9.0f, 12.0f, 5.0f, none)
            .texOffs(32, 24).addBox(-5.0f, 0.8f, -3.05f, 10.0f, 10.7f, 6.0f, new CubeDeformation(0.12f))
            .texOffs(70, 24).addBox(-4.9f, 1.3f, -3.65f, 9.8f, 3.2f, 1.0f, none)
            .texOffs(94, 24).addBox(-4.7f, 5.5f, -3.7f, 3.0f, 2.2f, 1.1f, none)
            .texOffs(104, 24).addBox(1.7f, 5.5f, -3.7f, 3.0f, 2.2f, 1.1f, none)
            .texOffs(70, 32).addBox(-5.3f, 11.1f, -3.0f, 10.6f, 1.4f, 6.0f, none)
            .texOffs(100, 32).addBox(-1.2f, -1.1f, -2.0f, 2.4f, 1.5f, 4.0f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
            .texOffs(0, 52).addBox(-3.0f, -1.0f, -2.0f, 4.0f, 12.0f, 4.0f, none)
            .texOffs(18, 52).addBox(-3.35f, -1.35f, -2.35f, 4.7f, 2.5f, 4.7f, none)
            .texOffs(38, 52).addBox(-3.15f, 8.4f, -2.15f, 4.3f, 2.9f, 4.3f, none),
            PartPose.offset(-5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
            .texOffs(0, 70).addBox(-1.0f, -1.0f, -2.0f, 4.0f, 12.0f, 4.0f, none)
            .texOffs(18, 70).addBox(-1.35f, -1.35f, -2.35f, 4.7f, 2.5f, 4.7f, none)
            .texOffs(38, 70).addBox(-1.15f, 8.4f, -2.15f, 4.3f, 2.9f, 4.3f, none),
            PartPose.offset(5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
            .texOffs(56, 52).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, none)
            .texOffs(74, 52).addBox(-2.25f, 4.4f, -2.45f, 4.5f, 2.2f, 0.9f, none)
            .texOffs(88, 52).addBox(-2.25f, 9.2f, -2.25f, 4.5f, 3.0f, 4.5f, none),
            PartPose.offset(-2.1f, 12.0f, 0.0f));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
            .texOffs(56, 70).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, none)
            .texOffs(74, 70).addBox(-2.25f, 4.4f, -2.45f, 4.5f, 2.2f, 0.9f, none)
            .texOffs(88, 70).addBox(-2.25f, 9.2f, -2.25f, 4.5f, 3.0f, 4.5f, none),
            PartPose.offset(2.1f, 12.0f, 0.0f));

        root.addOrReplaceChild("commander_badge", CubeListBuilder.create()
            .texOffs(0, 104).addBox(-0.6f, 2.2f, -3.95f, 1.2f, 1.6f, 0.45f, none)
            .texOffs(6, 104).addBox(2.2f, 1.8f, -3.95f, 1.8f, 0.45f, 0.45f, none)
            .texOffs(6, 106).addBox(2.2f, 2.7f, -3.95f, 1.8f, 0.45f, 0.45f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));
        root.addOrReplaceChild("quartermaster_pack", CubeListBuilder.create()
            .texOffs(16, 104).addBox(-5.2f, 1.7f, 2.65f, 10.4f, 10.0f, 3.6f, none)
            .texOffs(50, 104).addBox(-5.7f, 4.0f, -3.95f, 2.4f, 4.4f, 2.0f, none)
            .texOffs(60, 104).addBox(3.3f, 4.0f, -3.95f, 2.4f, 4.4f, 2.0f, none)
            .texOffs(70, 104).addBox(-5.0f, 7.8f, 2.2f, 2.2f, 3.0f, 2.2f, none)
            .texOffs(80, 104).addBox(2.8f, 7.8f, 2.2f, 2.2f, 3.0f, 2.2f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));
        root.addOrReplaceChild("quartermaster_crate", CubeListBuilder.create()
            .texOffs(0, 114).addBox(-3.4f, 8.0f, -5.75f, 6.8f, 4.2f, 3.1f, none)
            .texOffs(24, 114).addBox(-3.6f, 9.55f, -5.95f, 7.2f, 0.55f, 3.4f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));
        root.addOrReplaceChild("quartermaster_ammo", CubeListBuilder.create()
            .texOffs(48, 114).addBox(-4.8f, 8.7f, -3.95f, 1.2f, 2.8f, 1.2f, none)
            .texOffs(54, 114).addBox(3.6f, 8.7f, -3.95f, 1.2f, 2.8f, 1.2f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));
        root.addOrReplaceChild("intel_antenna", CubeListBuilder.create()
            .texOffs(64, 114).addBox(4.0f, -6.5f, 3.1f, 0.6f, 5.8f, 0.6f, none)
            .texOffs(68, 114).addBox(3.7f, -7.1f, 2.8f, 1.2f, 1.2f, 1.2f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));
        root.addOrReplaceChild("intel_radio_pack", CubeListBuilder.create()
            .texOffs(76, 114).addBox(-3.6f, 3.0f, 2.65f, 7.2f, 6.8f, 2.4f, none)
            .texOffs(98, 114).addBox(-1.0f, 3.8f, 5.0f, 2.0f, 3.8f, 0.6f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));
        root.addOrReplaceChild("intel_tablet", CubeListBuilder.create()
            .texOffs(108, 96).addBox(-1.7f, 5.7f, -5.2f, 3.4f, 4.4f, 0.55f, none)
            .texOffs(108, 106).addBox(-1.35f, 6.25f, -5.55f, 2.7f, 3.25f, 0.35f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));
        root.addOrReplaceChild("medic_bag", CubeListBuilder.create()
            .texOffs(0, 82).addBox(4.7f, 4.8f, -2.1f, 3.5f, 5.5f, 4.4f, none)
            .texOffs(18, 82).addBox(5.25f, 5.9f, -2.55f, 2.3f, 3.2f, 0.55f, none)
            .texOffs(28, 82).addBox(3.8f, 3.5f, -0.2f, 1.1f, 3.2f, 1.1f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));
        root.addOrReplaceChild("medic_cross", CubeListBuilder.create()
            .texOffs(36, 82).addBox(-0.55f, 2.0f, -4.0f, 1.1f, 3.0f, 0.45f, none)
            .texOffs(42, 82).addBox(-1.45f, 2.9f, -4.02f, 2.9f, 1.1f, 0.45f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        root.addOrReplaceChild("tablet", CubeListBuilder.create()
            .texOffs(52, 82).addBox(-1.6f, 5.9f, -5.2f, 3.2f, 4.1f, 0.55f, none)
            .texOffs(64, 82).addBox(-1.2f, 6.5f, -5.55f, 2.4f, 2.9f, 0.35f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));
        root.addOrReplaceChild("crate", CubeListBuilder.create()
            .texOffs(78, 82).addBox(-3.0f, 8.0f, -5.2f, 6.0f, 4.0f, 3.0f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));
        root.addOrReplaceChild("medkit", CubeListBuilder.create()
            .texOffs(100, 82).addBox(-2.1f, 7.2f, -5.4f, 4.2f, 3.2f, 2.1f, none)
            .texOffs(116, 82).addBox(-0.35f, 7.7f, -5.7f, 0.7f, 2.2f, 0.35f, none)
            .texOffs(120, 82).addBox(-1.2f, 8.45f, -5.72f, 2.4f, 0.7f, 0.35f, none),
            PartPose.offset(0.0f, 0.0f, 0.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(TacRogueNpcEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        this.rightArm.xRot = -0.08f;
        this.rightArm.zRot = 0.04f;
        this.leftArm.xRot = 0.08f;
        this.leftArm.zRot = -0.04f;
        this.facePlate.visible = true;

        NpcManager.NpcRole role = entity.getRole();
        this.commanderBeret.visible = role == NpcManager.NpcRole.COMMANDER;
        this.commanderBadge.visible = role == NpcManager.NpcRole.COMMANDER;
        this.quartermasterHeadgear.visible = role == NpcManager.NpcRole.QUARTERMASTER;
        this.quartermasterPack.visible = role == NpcManager.NpcRole.QUARTERMASTER;
        this.quartermasterCrate.visible = role == NpcManager.NpcRole.QUARTERMASTER;
        this.quartermasterAmmo.visible = role == NpcManager.NpcRole.QUARTERMASTER;
        this.intelHeadset.visible = role == NpcManager.NpcRole.INTEL_OFFICER;
        this.intelAntenna.visible = role == NpcManager.NpcRole.INTEL_OFFICER;
        this.intelRadioPack.visible = role == NpcManager.NpcRole.INTEL_OFFICER;
        this.intelTablet.visible = role == NpcManager.NpcRole.INTEL_OFFICER;
        this.medicMask.visible = role == NpcManager.NpcRole.MEDIC;
        this.medicBag.visible = role == NpcManager.NpcRole.MEDIC;
        this.medicCross.visible = role == NpcManager.NpcRole.MEDIC;
        this.tablet.visible = role == NpcManager.NpcRole.COMMANDER;
        this.crate.visible = false;
        this.medkit.visible = role == NpcManager.NpcRole.MEDIC;

        if (role == NpcManager.NpcRole.QUARTERMASTER) {
            this.rightArm.xRot = -0.42f;
            this.leftArm.xRot = -0.42f;
        } else if (role == NpcManager.NpcRole.COMMANDER || role == NpcManager.NpcRole.INTEL_OFFICER) {
            this.rightArm.xRot = -0.32f;
            this.leftArm.xRot = -0.25f;
        } else if (role == NpcManager.NpcRole.MEDIC) {
            this.rightArm.xRot = -0.24f;
            this.leftArm.xRot = -0.12f;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
