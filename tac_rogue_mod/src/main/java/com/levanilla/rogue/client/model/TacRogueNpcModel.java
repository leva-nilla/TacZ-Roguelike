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
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final RoleParts commanderParts;
    private final RoleParts quartermasterParts;
    private final RoleParts intelParts;
    private final RoleParts medicParts;

    public TacRogueNpcModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.commanderParts = new RoleParts(
            headParts(),
            headParts("commander_hair"),
            headParts("commander_cap", "commander_bill"),
            rootParts(root, "commander_coat", "commander_trim", "commander_shoulders", "commander_tablet", "commander_badges", "commander_holster"));
        this.quartermasterParts = new RoleParts(
            headParts(),
            headParts("quartermaster_hair", "quartermaster_bangs", "quartermaster_side_locks", "quartermaster_ponytail"),
            headParts("quartermaster_cap", "quartermaster_goggles", "quartermaster_hairpin"),
            rootParts(root, "quartermaster_vest", "quartermaster_apron", "quartermaster_backpack", "quartermaster_pouches", "quartermaster_ammo_belt", "quartermaster_crate"));
        this.intelParts = new RoleParts(
            headParts(),
            headParts("intel_hair", "intel_swept_hair"),
            headParts("intel_glasses", "intel_headset"),
            rootParts(root, "intel_coat", "intel_terminal", "intel_side_screen", "intel_radio_pack", "intel_cables", "intel_tablet"));
        this.medicParts = new RoleParts(
            headParts(),
            headParts("medic_hair", "medic_bangs", "medic_side_locks", "medic_ponytail"),
            headParts("medic_headband", "medic_hair_clip"),
            rootParts(root, "medic_coat", "medic_stethoscope", "medic_bag", "medic_cross", "medic_armband", "medic_badge", "medic_injectors"));
        this.commanderParts.setVisible(false);
        this.quartermasterParts.setVisible(false);
        this.intelParts.setVisible(false);
        this.medicParts.setVisible(false);
    }

    private ModelPart[] headParts(String... names) {
        ModelPart[] parts = new ModelPart[names.length];
        for (int i = 0; i < names.length; i++) {
            parts[i] = this.head.getChild(names[i]);
        }
        return parts;
    }

    private static ModelPart[] rootParts(ModelPart root, String... names) {
        ModelPart[] parts = new ModelPart[names.length];
        for (int i = 0; i < names.length; i++) {
            parts[i] = root.getChild(names[i]);
        }
        return parts;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;
        CubeDeformation soft = new CubeDeformation(0.05f);

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, none),
            PartPose.ZERO);

        addHeadHairParts(head, none, soft);
        addHeadDecorationParts(head, none);
        addBaseBody(root, none, soft);
        addCommander(root, none);
        addQuartermaster(root, none);
        addIntel(root, none);
        addMedic(root, none);

        return LayerDefinition.create(mesh, 512, 512);
    }

    private static void addHeadHairParts(PartDefinition head, CubeDeformation none, CubeDeformation soft) {
        head.addOrReplaceChild("commander_hair", CubeListBuilder.create()
            .texOffs(232, 96).addBox(-4.38f, -6.35f, -3.85f, 0.75f, 3.9f, 1.2f, none)
            .texOffs(244, 96).addBox(3.63f, -6.35f, -3.85f, 0.75f, 3.9f, 1.2f, none)
            .texOffs(248, 96).addBox(-4.1f, -7.8f, -4.15f, 8.2f, 1.1f, 8.3f, soft),
            PartPose.ZERO);

        head.addOrReplaceChild("quartermaster_hair", CubeListBuilder.create()
            .texOffs(0, 112).addBox(-4.25f, -8.45f, -4.25f, 8.5f, 1.25f, 8.5f, soft)
            .texOffs(40, 112).addBox(-4.35f, -7.35f, 3.65f, 8.7f, 5.85f, 1.15f, none)
            .texOffs(72, 112).addBox(-4.62f, -6.6f, -2.95f, 0.9f, 5.65f, 6.85f, none)
            .texOffs(104, 112).addBox(3.72f, -6.35f, -2.75f, 0.9f, 4.75f, 6.65f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("quartermaster_bangs", CubeListBuilder.create()
            .texOffs(328, 112).addBox(-3.85f, -7.52f, -4.55f, 2.4f, 1.05f, 0.42f, none)
            .texOffs(344, 112).addBox(-1.35f, -7.68f, -4.58f, 2.15f, 0.88f, 0.44f, none)
            .texOffs(364, 112).addBox(1.05f, -7.35f, -4.55f, 2.15f, 0.95f, 0.42f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("quartermaster_side_locks", CubeListBuilder.create()
            .texOffs(400, 112).addBox(-4.95f, -4.95f, -2.4f, 0.75f, 4.6f, 1.85f, none)
            .texOffs(412, 112).addBox(4.2f, -4.45f, -2.1f, 0.75f, 3.65f, 1.75f, none)
            .texOffs(424, 112).addBox(-3.25f, -1.15f, 4.55f, 1.15f, 2.8f, 1.25f, none)
            .texOffs(440, 112).addBox(2.3f, -1.35f, 4.55f, 1.15f, 3.25f, 1.25f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("quartermaster_ponytail", CubeListBuilder.create()
            .texOffs(280, 112).addBox(-1.55f, -2.95f, 4.65f, 3.1f, 4.9f, 1.65f, none)
            .texOffs(304, 112).addBox(-1.05f, 1.25f, 4.9f, 2.1f, 3.0f, 1.25f, none),
            PartPose.ZERO);

        head.addOrReplaceChild("intel_hair", CubeListBuilder.create()
            .texOffs(0, 128).addBox(-4.35f, -8.45f, -4.35f, 8.7f, 2.35f, 8.7f, soft)
            .texOffs(44, 128).addBox(-4.25f, -6.25f, 3.85f, 8.5f, 2.4f, 1.1f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("intel_swept_hair", CubeListBuilder.create()
            .texOffs(320, 128).addBox(-4.05f, -7.2f, -4.5f, 2.55f, 0.95f, 0.42f, none)
            .texOffs(344, 128).addBox(-1.4f, -7.45f, -4.55f, 2.05f, 1.02f, 0.44f, none)
            .texOffs(368, 128).addBox(1.05f, -7.08f, -4.5f, 2.2f, 0.92f, 0.42f, none)
            .texOffs(392, 128).addBox(-4.7f, -5.65f, -2.8f, 0.72f, 3.65f, 1.5f, none)
            .texOffs(404, 128).addBox(3.95f, -5.35f, -2.4f, 0.72f, 3.1f, 1.5f, none),
            PartPose.ZERO);

        head.addOrReplaceChild("medic_hair", CubeListBuilder.create()
            .texOffs(0, 144).addBox(-4.3f, -8.55f, -4.3f, 8.6f, 1.45f, 8.6f, soft)
            .texOffs(42, 144).addBox(-4.45f, -7.35f, 3.7f, 8.9f, 5.55f, 1.18f, none)
            .texOffs(72, 144).addBox(-4.78f, -6.35f, -2.75f, 0.95f, 5.05f, 6.6f, none)
            .texOffs(102, 144).addBox(3.83f, -6.25f, -2.9f, 0.95f, 5.35f, 6.75f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("medic_bangs", CubeListBuilder.create()
            .texOffs(240, 144).addBox(-3.85f, -7.45f, -4.52f, 1.95f, 1.08f, 0.42f, none)
            .texOffs(256, 144).addBox(-1.75f, -7.72f, -4.58f, 2.25f, 0.95f, 0.44f, none)
            .texOffs(276, 144).addBox(0.7f, -7.32f, -4.52f, 2.65f, 0.98f, 0.42f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("medic_side_locks", CubeListBuilder.create()
            .texOffs(312, 144).addBox(-5.02f, -4.75f, -2.35f, 0.75f, 4.15f, 1.7f, none)
            .texOffs(324, 144).addBox(4.27f, -4.95f, -2.55f, 0.75f, 4.55f, 1.9f, none)
            .texOffs(336, 144).addBox(-3.1f, -0.9f, 4.55f, 1.15f, 3.0f, 1.25f, none)
            .texOffs(352, 144).addBox(2.65f, -1.15f, 4.55f, 1.1f, 3.2f, 1.25f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("medic_ponytail", CubeListBuilder.create()
            .texOffs(196, 144).addBox(1.25f, -3.05f, 4.62f, 2.55f, 4.95f, 1.55f, none)
            .texOffs(216, 144).addBox(1.75f, 1.15f, 4.88f, 1.75f, 3.05f, 1.2f, none),
            PartPose.ZERO);
    }

    private static void addHeadDecorationParts(PartDefinition head, CubeDeformation none) {
        head.addOrReplaceChild("commander_cap", CubeListBuilder.create()
            .texOffs(0, 96).addBox(-4.85f, -9.15f, -4.75f, 9.7f, 1.6f, 9.5f, none)
            .texOffs(44, 96).addBox(-3.4f, -9.85f, -2.0f, 6.8f, 0.9f, 4.0f, none)
            .texOffs(76, 96).addBox(-1.0f, -10.35f, -0.9f, 2.0f, 0.65f, 1.8f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("commander_bill", CubeListBuilder.create()
            .texOffs(96, 96).addBox(-3.45f, -8.2f, -6.25f, 6.9f, 0.65f, 2.25f, none)
            .texOffs(128, 96).addBox(-0.85f, -8.65f, -6.45f, 1.7f, 0.35f, 0.65f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("quartermaster_cap", CubeListBuilder.create()
            .texOffs(136, 112).addBox(-4.55f, -8.95f, -4.65f, 9.1f, 1.0f, 9.1f, none)
            .texOffs(172, 112).addBox(-3.1f, -8.15f, -5.65f, 6.2f, 0.48f, 1.65f, none)
            .texOffs(196, 112).addBox(-0.75f, -8.55f, -5.9f, 1.5f, 0.35f, 0.5f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("quartermaster_goggles", CubeListBuilder.create()
            .texOffs(208, 112).addBox(-3.55f, -8.05f, -5.78f, 2.45f, 0.4f, 0.32f, none)
            .texOffs(220, 112).addBox(1.1f, -8.05f, -5.78f, 2.45f, 0.4f, 0.32f, none)
            .texOffs(232, 112).addBox(-0.75f, -7.95f, -5.82f, 1.5f, 0.28f, 0.32f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("quartermaster_hairpin", CubeListBuilder.create()
            .texOffs(244, 112).addBox(2.35f, -7.25f, -4.75f, 1.15f, 0.35f, 0.25f, none)
            .texOffs(252, 112).addBox(2.78f, -7.68f, -4.72f, 0.35f, 1.15f, 0.25f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("intel_glasses", CubeListBuilder.create()
            .texOffs(80, 128).addBox(-3.35f, -5.62f, -4.62f, 2.7f, 0.22f, 0.24f, none)
            .texOffs(96, 128).addBox(-3.35f, -4.36f, -4.62f, 2.7f, 0.22f, 0.24f, none)
            .texOffs(112, 128).addBox(-3.35f, -5.62f, -4.62f, 0.22f, 1.48f, 0.24f, none)
            .texOffs(80, 132).addBox(-0.87f, -5.62f, -4.62f, 0.22f, 1.48f, 0.24f, none)
            .texOffs(96, 132).addBox(0.65f, -5.62f, -4.62f, 2.7f, 0.22f, 0.24f, none)
            .texOffs(112, 132).addBox(0.65f, -4.36f, -4.62f, 2.7f, 0.22f, 0.24f, none)
            .texOffs(80, 136).addBox(0.65f, -5.62f, -4.62f, 0.22f, 1.48f, 0.24f, none)
            .texOffs(88, 136).addBox(3.13f, -5.62f, -4.62f, 0.22f, 1.48f, 0.24f, none)
            .texOffs(112, 136).addBox(-0.65f, -4.95f, -4.65f, 1.3f, 0.22f, 0.24f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("intel_headset", CubeListBuilder.create()
            .texOffs(128, 128).addBox(-5.05f, -6.5f, -1.25f, 1.05f, 3.3f, 2.5f, none)
            .texOffs(144, 128).addBox(4.0f, -6.5f, -1.25f, 1.05f, 3.3f, 2.5f, none)
            .texOffs(160, 128).addBox(-4.0f, -8.65f, -0.55f, 8.0f, 0.8f, 1.1f, none)
            .texOffs(196, 128).addBox(3.35f, -3.9f, -4.85f, 0.7f, 0.65f, 3.95f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("medic_headband", CubeListBuilder.create()
            .texOffs(120, 144).addBox(-4.45f, -7.95f, -4.72f, 8.9f, 0.55f, 0.62f, none)
            .texOffs(156, 144).addBox(-4.55f, -7.9f, -4.1f, 0.55f, 0.55f, 8.2f, none)
            .texOffs(168, 144).addBox(4.0f, -7.9f, -4.1f, 0.55f, 0.55f, 8.2f, none),
            PartPose.ZERO);
        head.addOrReplaceChild("medic_hair_clip", CubeListBuilder.create()
            .texOffs(232, 144).addBox(-3.55f, -7.08f, -4.75f, 1.15f, 0.35f, 0.28f, none)
            .texOffs(232, 150).addBox(-3.17f, -7.46f, -4.72f, 0.35f, 1.1f, 0.25f, none),
            PartPose.ZERO);
    }

    private static void addBaseBody(PartDefinition root, CubeDeformation none, CubeDeformation soft) {
        root.addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(0, 32).addBox(-4.4f, 0.0f, -2.45f, 8.8f, 12.0f, 4.9f, none)
            .texOffs(40, 32).addBox(-5.0f, 0.7f, -3.05f, 10.0f, 10.7f, 6.0f, soft)
            .texOffs(92, 32).addBox(-4.8f, 11.0f, -2.95f, 9.6f, 1.45f, 5.9f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
            .texOffs(0, 56).addBox(-3.0f, -1.0f, -2.0f, 4.0f, 12.0f, 4.0f, none)
            .texOffs(24, 56).addBox(-3.35f, -1.25f, -2.35f, 4.7f, 2.4f, 4.7f, none)
            .texOffs(52, 56).addBox(-3.2f, 8.4f, -2.2f, 4.4f, 2.9f, 4.4f, none),
            PartPose.offset(-5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
            .texOffs(0, 72).addBox(-1.0f, -1.0f, -2.0f, 4.0f, 12.0f, 4.0f, none)
            .texOffs(24, 72).addBox(-1.35f, -1.25f, -2.35f, 4.7f, 2.4f, 4.7f, none)
            .texOffs(52, 72).addBox(-1.2f, 8.4f, -2.2f, 4.4f, 2.9f, 4.4f, none),
            PartPose.offset(5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
            .texOffs(96, 56).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, none)
            .texOffs(120, 56).addBox(-2.25f, 9.0f, -2.3f, 4.5f, 3.2f, 4.6f, none),
            PartPose.offset(-2.1f, 12.0f, 0.0f));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
            .texOffs(96, 72).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, none)
            .texOffs(120, 72).addBox(-2.25f, 9.0f, -2.3f, 4.5f, 3.2f, 4.6f, none),
            PartPose.offset(2.1f, 12.0f, 0.0f));
    }

    private static void addCommander(PartDefinition root, CubeDeformation none) {
        root.addOrReplaceChild("commander_coat", CubeListBuilder.create()
            .texOffs(0, 192).addBox(-5.25f, 0.8f, -3.35f, 2.5f, 13.4f, 0.85f, none)
            .texOffs(28, 192).addBox(2.75f, 0.8f, -3.35f, 2.5f, 13.4f, 0.85f, none)
            .texOffs(56, 192).addBox(-5.1f, 0.95f, 2.55f, 10.2f, 13.2f, 0.9f, none)
            .texOffs(112, 192).addBox(-5.2f, 12.5f, -3.0f, 10.4f, 1.65f, 6.0f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("commander_trim", CubeListBuilder.create()
            .texOffs(0, 224).addBox(-3.0f, 1.0f, -4.5f, 0.55f, 11.8f, 0.45f, none)
            .texOffs(8, 224).addBox(2.45f, 1.0f, -4.5f, 0.55f, 11.8f, 0.45f, none)
            .texOffs(16, 224).addBox(-4.55f, 1.15f, -4.45f, 9.1f, 0.5f, 0.45f, none)
            .texOffs(56, 224).addBox(-4.55f, 12.55f, -4.45f, 9.1f, 0.5f, 0.45f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("commander_shoulders", CubeListBuilder.create()
            .texOffs(96, 224).addBox(-6.25f, 0.65f, -2.9f, 3.0f, 0.8f, 5.8f, none)
            .texOffs(128, 224).addBox(3.25f, 0.65f, -2.9f, 3.0f, 0.8f, 5.8f, none)
            .texOffs(160, 224).addBox(-5.9f, 1.6f, -3.85f, 2.0f, 0.45f, 0.65f, none)
            .texOffs(176, 224).addBox(3.9f, 1.6f, -3.85f, 2.0f, 0.45f, 0.65f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("commander_tablet", CubeListBuilder.create()
            .texOffs(208, 224).addBox(-2.45f, 2.0f, -4.75f, 4.9f, 3.4f, 0.55f, none)
            .texOffs(240, 224).addBox(-1.8f, 2.55f, -5.05f, 3.6f, 2.0f, 0.35f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("commander_badges", CubeListBuilder.create()
            .texOffs(272, 224).addBox(-0.5f, 2.3f, -4.9f, 1.0f, 1.5f, 0.35f, none)
            .texOffs(284, 224).addBox(2.25f, 2.0f, -4.85f, 1.9f, 0.4f, 0.35f, none)
            .texOffs(284, 230).addBox(2.25f, 2.8f, -4.85f, 1.9f, 0.4f, 0.35f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("commander_holster", CubeListBuilder.create()
            .texOffs(312, 224).addBox(4.65f, 7.2f, -1.0f, 1.4f, 4.4f, 2.0f, none)
            .texOffs(332, 224).addBox(4.9f, 6.55f, -1.45f, 0.85f, 1.15f, 2.9f, none),
            PartPose.ZERO);
    }

    private static void addQuartermaster(PartDefinition root, CubeDeformation none) {
        root.addOrReplaceChild("quartermaster_vest", CubeListBuilder.create()
            .texOffs(0, 256).addBox(-4.9f, 1.0f, -4.45f, 9.8f, 7.2f, 0.72f, none)
            .texOffs(40, 256).addBox(-5.05f, 1.05f, 2.75f, 10.1f, 8.2f, 0.75f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("quartermaster_apron", CubeListBuilder.create()
            .texOffs(80, 256).addBox(-4.35f, 7.0f, -4.55f, 8.7f, 6.3f, 0.72f, none)
            .texOffs(132, 256).addBox(-4.3f, 10.9f, -4.35f, 8.6f, 2.35f, 0.75f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("quartermaster_backpack", CubeListBuilder.create()
            .texOffs(224, 256).addBox(-5.3f, 1.6f, 2.65f, 10.6f, 10.2f, 3.8f, none)
            .texOffs(288, 256).addBox(-5.7f, 7.8f, 2.2f, 2.4f, 3.0f, 2.4f, none)
            .texOffs(312, 256).addBox(3.3f, 7.8f, 2.2f, 2.4f, 3.0f, 2.4f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("quartermaster_pouches", CubeListBuilder.create()
            .texOffs(0, 288).addBox(-5.55f, 7.1f, -4.45f, 2.0f, 2.7f, 1.25f, none)
            .texOffs(20, 288).addBox(-2.8f, 7.35f, -4.6f, 2.2f, 2.5f, 1.25f, none)
            .texOffs(44, 288).addBox(0.6f, 7.35f, -4.6f, 2.2f, 2.5f, 1.25f, none)
            .texOffs(68, 288).addBox(3.55f, 7.1f, -4.45f, 2.0f, 2.7f, 1.25f, none)
            .texOffs(92, 288).addBox(-4.95f, 6.55f, -4.1f, 9.9f, 0.85f, 0.75f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("quartermaster_ammo_belt", CubeListBuilder.create()
            .texOffs(152, 288).addBox(-5.0f, 3.0f, -4.65f, 1.0f, 2.1f, 0.8f, none)
            .texOffs(164, 288).addBox(-3.5f, 3.25f, -4.65f, 1.0f, 2.1f, 0.8f, none)
            .texOffs(176, 288).addBox(2.5f, 3.25f, -4.65f, 1.0f, 2.1f, 0.8f, none)
            .texOffs(188, 288).addBox(4.0f, 3.0f, -4.65f, 1.0f, 2.1f, 0.8f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("quartermaster_crate", CubeListBuilder.create()
            .texOffs(216, 288).addBox(-3.5f, 8.1f, -5.85f, 7.0f, 4.1f, 3.2f, none)
            .texOffs(264, 288).addBox(-3.7f, 9.55f, -6.05f, 7.4f, 0.55f, 3.6f, none),
            PartPose.ZERO);
    }

    private static void addIntel(PartDefinition root, CubeDeformation none) {
        root.addOrReplaceChild("intel_coat", CubeListBuilder.create()
            .texOffs(0, 320).addBox(-4.6f, 0.8f, -3.3f, 2.2f, 12.7f, 0.75f, none)
            .texOffs(24, 320).addBox(2.4f, 0.8f, -3.3f, 2.2f, 12.7f, 0.75f, none)
            .texOffs(48, 320).addBox(-4.35f, 0.95f, 2.55f, 8.7f, 12.5f, 0.75f, none)
            .texOffs(96, 320).addBox(-0.3f, 0.95f, -4.3f, 0.6f, 12.1f, 0.45f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("intel_terminal", CubeListBuilder.create()
            .texOffs(116, 320).addBox(-3.2f, 2.1f, -4.7f, 6.4f, 4.8f, 0.55f, none)
            .texOffs(156, 320).addBox(-2.55f, 2.75f, -5.0f, 5.1f, 3.0f, 0.35f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("intel_side_screen", CubeListBuilder.create()
            .texOffs(196, 320).addBox(4.9f, 3.0f, -2.1f, 0.8f, 4.4f, 4.2f, none)
            .texOffs(224, 320).addBox(5.35f, 3.65f, -1.5f, 0.35f, 3.1f, 3.0f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("intel_radio_pack", CubeListBuilder.create()
            .texOffs(252, 320).addBox(-3.7f, 3.0f, 2.65f, 7.4f, 6.9f, 2.55f, none)
            .texOffs(300, 320).addBox(3.9f, -6.6f, 3.0f, 0.65f, 5.8f, 0.65f, none)
            .texOffs(310, 320).addBox(3.6f, -7.2f, 2.7f, 1.25f, 1.25f, 1.25f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("intel_cables", CubeListBuilder.create()
            .texOffs(0, 352).addBox(-3.75f, 1.5f, -4.75f, 0.45f, 6.6f, 0.4f, none)
            .texOffs(8, 352).addBox(3.3f, 1.5f, -4.75f, 0.45f, 6.6f, 0.4f, none)
            .texOffs(16, 352).addBox(-3.45f, 7.6f, -4.77f, 6.9f, 0.45f, 0.4f, none)
            .texOffs(48, 352).addBox(4.45f, 2.2f, 2.45f, 0.55f, 8.0f, 0.55f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("intel_tablet", CubeListBuilder.create()
            .texOffs(72, 352).addBox(-1.65f, 5.8f, -5.55f, 3.3f, 4.3f, 0.55f, none)
            .texOffs(96, 352).addBox(-1.25f, 6.4f, -5.85f, 2.5f, 3.0f, 0.35f, none),
            PartPose.ZERO);
    }

    private static void addMedic(PartDefinition root, CubeDeformation none) {
        root.addOrReplaceChild("medic_coat", CubeListBuilder.create()
            .texOffs(0, 384).addBox(-5.45f, 0.6f, -3.4f, 2.55f, 14.4f, 0.9f, none)
            .texOffs(28, 384).addBox(2.9f, 0.6f, -3.4f, 2.55f, 14.4f, 0.9f, none)
            .texOffs(56, 384).addBox(-5.25f, 0.8f, 2.75f, 10.5f, 14.2f, 0.95f, none)
            .texOffs(112, 384).addBox(-5.15f, 13.6f, -3.05f, 10.3f, 1.45f, 6.0f, none)
            .texOffs(164, 384).addBox(-0.35f, 0.9f, -4.55f, 0.7f, 12.7f, 0.45f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("medic_stethoscope", CubeListBuilder.create()
            .texOffs(184, 384).addBox(-3.1f, 1.3f, -4.75f, 0.55f, 5.1f, 0.45f, none)
            .texOffs(192, 384).addBox(2.55f, 1.3f, -4.75f, 0.55f, 5.1f, 0.45f, none)
            .texOffs(200, 384).addBox(-2.55f, 6.1f, -4.8f, 5.1f, 0.55f, 0.45f, none)
            .texOffs(232, 384).addBox(-0.65f, 6.4f, -5.0f, 1.3f, 1.3f, 0.55f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("medic_bag", CubeListBuilder.create()
            .texOffs(252, 384).addBox(4.7f, 4.8f, -2.15f, 3.6f, 5.6f, 4.5f, none)
            .texOffs(288, 384).addBox(5.25f, 5.9f, -2.6f, 2.4f, 3.3f, 0.55f, none)
            .texOffs(308, 384).addBox(3.8f, 3.5f, -0.2f, 1.1f, 3.2f, 1.1f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("medic_cross", CubeListBuilder.create()
            .texOffs(0, 416).addBox(-0.55f, 2.0f, -4.9f, 1.1f, 3.0f, 0.45f, none)
            .texOffs(12, 416).addBox(-1.45f, 2.9f, -4.92f, 2.9f, 1.1f, 0.45f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("medic_armband", CubeListBuilder.create()
            .texOffs(44, 416).addBox(-8.35f, 2.2f, -2.45f, 4.8f, 1.8f, 4.9f, none)
            .texOffs(84, 416).addBox(3.55f, 2.2f, -2.45f, 4.8f, 1.8f, 4.9f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("medic_badge", CubeListBuilder.create()
            .texOffs(124, 416).addBox(2.35f, 2.2f, -4.9f, 1.8f, 2.35f, 0.4f, none)
            .texOffs(140, 416).addBox(2.75f, 2.55f, -5.15f, 1.0f, 1.55f, 0.35f, none),
            PartPose.ZERO);
        root.addOrReplaceChild("medic_injectors", CubeListBuilder.create()
            .texOffs(164, 416).addBox(-5.65f, 6.8f, -4.25f, 0.65f, 4.2f, 0.65f, none)
            .texOffs(172, 416).addBox(-5.9f, 6.35f, -4.4f, 1.15f, 0.6f, 0.95f, none)
            .texOffs(188, 416).addBox(4.95f, 6.8f, -4.25f, 0.65f, 4.2f, 0.65f, none),
            PartPose.ZERO);
    }

    @Override
    public void setupAnim(TacRogueNpcEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        this.rightArm.xRot = -0.08f;
        this.rightArm.zRot = 0.04f;
        this.leftArm.xRot = 0.08f;
        this.leftArm.zRot = -0.04f;

        NpcManager.NpcRole role = entity.getRole();
        this.commanderParts.setVisible(role == NpcManager.NpcRole.COMMANDER);
        this.quartermasterParts.setVisible(role == NpcManager.NpcRole.QUARTERMASTER);
        this.intelParts.setVisible(role == NpcManager.NpcRole.INTEL_OFFICER);
        this.medicParts.setVisible(role == NpcManager.NpcRole.MEDIC);

        if (role == NpcManager.NpcRole.QUARTERMASTER) {
            this.rightArm.xRot = -0.44f;
            this.leftArm.xRot = -0.44f;
        } else if (role == NpcManager.NpcRole.COMMANDER) {
            this.rightArm.xRot = -0.34f;
            this.leftArm.xRot = -0.20f;
        } else if (role == NpcManager.NpcRole.INTEL_OFFICER) {
            this.rightArm.xRot = -0.30f;
            this.leftArm.xRot = -0.30f;
        } else if (role == NpcManager.NpcRole.MEDIC) {
            this.rightArm.xRot = -0.24f;
            this.leftArm.xRot = -0.12f;
        }
    }

    private static void setVisible(ModelPart[] parts, boolean visible) {
        for (ModelPart part : parts) {
            part.visible = visible;
        }
    }

    private record RoleParts(ModelPart[] faceParts, ModelPart[] hairParts, ModelPart[] decorationParts, ModelPart[] bodyGearParts) {
        void setVisible(boolean visible) {
            TacRogueNpcModel.setVisible(this.faceParts, visible);
            TacRogueNpcModel.setVisible(this.hairParts, visible);
            TacRogueNpcModel.setVisible(this.decorationParts, visible);
            TacRogueNpcModel.setVisible(this.bodyGearParts, visible);
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
