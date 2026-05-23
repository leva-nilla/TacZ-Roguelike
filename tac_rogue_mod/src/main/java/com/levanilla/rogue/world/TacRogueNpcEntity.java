package com.levanilla.rogue.world;

import com.levanilla.rogue.core.service.FloorInstanceManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class TacRogueNpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> ROLE =
        SynchedEntityData.defineId(TacRogueNpcEntity.class, EntityDataSerializers.STRING);
    private static final String EXTRACTION_OFFICER_KEY = "TacRogueExtractionOfficer";
    private static final double EXTRACTION_FOLLOW_START_DISTANCE_SQR = 4.5D * 4.5D;
    private static final double EXTRACTION_FOLLOW_STOP_DISTANCE_SQR = 2.5D * 2.5D;
    private static final double EXTRACTION_FOLLOW_SPEED = 1.05D;

    public TacRogueNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
        this.addTag("tac_rogue_npc");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ROLE, "commander");
    }

    public void setRole(NpcManager.NpcRole role) {
        this.entityData.set(ROLE, role.id);
        for (String tag : new java.util.ArrayList<>(this.getTags())) {
            if (tag.startsWith("npc_role:")) {
                this.removeTag(tag);
            }
        }
        this.addTag("npc_role:" + role.id);
    }

    public void markLobbyNpc(NpcManager.NpcRole role) {
        this.addTag("tac_rogue_npc");
        this.addTag("tac_rogue_lobby_npc");
        this.getPersistentData().putBoolean("TacRogueLobbyNpc", true);
        this.getPersistentData().putString("TacRogueLobbyRole", role.id);
    }

    public void markExtractionOfficer() {
        this.addTag("tac_rogue_npc");
        this.addTag("tac_rogue_extraction_npc");
        this.getPersistentData().putBoolean(EXTRACTION_OFFICER_KEY, true);
        enableExtractionMovement();
    }

    public boolean isLobbyNpc() {
        return this.getPersistentData().getBoolean("TacRogueLobbyNpc")
            || this.getTags().contains("tac_rogue_lobby_npc");
    }

    public NpcManager.NpcRole getRole() {
        String roleId = this.entityData.get(ROLE);
        for (NpcManager.NpcRole role : NpcManager.NpcRole.values()) {
            if (role.id.equals(roleId)) return role;
        }
        return NpcManager.NpcRole.COMMANDER;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.tickCount % 10 == 0) {
            tickExtractionFollow();
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            NpcManager.handleCustomNpcInteraction(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("TacRogueRole", this.entityData.get(ROLE));
        if (this.getPersistentData().getBoolean("TacRogueLobbyNpc")) {
            tag.putBoolean("TacRogueLobbyNpc", true);
            tag.putString("TacRogueLobbyRole", this.getPersistentData().getString("TacRogueLobbyRole"));
        }
        if (this.getPersistentData().getBoolean(EXTRACTION_OFFICER_KEY)) {
            tag.putBoolean(EXTRACTION_OFFICER_KEY, true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TacRogueRole")) {
            this.entityData.set(ROLE, tag.getString("TacRogueRole"));
            this.addTag("npc_role:" + tag.getString("TacRogueRole"));
        }
        if (tag.getBoolean("TacRogueLobbyNpc")) {
            this.getPersistentData().putBoolean("TacRogueLobbyNpc", true);
            this.getPersistentData().putString("TacRogueLobbyRole", tag.getString("TacRogueLobbyRole"));
            this.addTag("tac_rogue_lobby_npc");
        }
        this.addTag("tac_rogue_npc");
        if (tag.getBoolean(EXTRACTION_OFFICER_KEY)) {
            this.getPersistentData().putBoolean(EXTRACTION_OFFICER_KEY, true);
            this.addTag("tac_rogue_extraction_npc");
            enableExtractionMovement();
        } else {
            this.setNoAi(true);
        }
        this.setInvulnerable(true);
    }

    private void tickExtractionFollow() {
        if (!this.getPersistentData().getBoolean(EXTRACTION_OFFICER_KEY)) return;
        if (!(this.level() instanceof ServerLevel)) return;
        enableExtractionMovement();

        ServerPlayer target = findExtractionFollowTarget();
        if (target == null || target.isSpectator()) {
            this.getNavigation().stop();
            return;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr > EXTRACTION_FOLLOW_START_DISTANCE_SQR) {
            this.getNavigation().moveTo(target, EXTRACTION_FOLLOW_SPEED);
        } else if (distanceSqr < EXTRACTION_FOLLOW_STOP_DISTANCE_SQR) {
            this.getNavigation().stop();
        }
        this.getLookControl().setLookAt(target, 35.0F, 35.0F);
    }

    private ServerPlayer findExtractionFollowTarget() {
        String ownerRaw = this.getPersistentData().getString(FloorInstanceManager.OWNER_KEY);
        if (!ownerRaw.isBlank()) {
            try {
                java.util.UUID ownerId = java.util.UUID.fromString(ownerRaw);
                if (this.level() instanceof ServerLevel serverLevel) {
                    ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
                    if (owner != null && owner.isAlive()) return owner;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        java.util.List<ServerPlayer> participants = FloorInstanceManager.getParticipantsForEntity(this);
        if (participants.isEmpty()) return null;

        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer participant : participants) {
            if (!participant.isAlive()) continue;
            double distance = this.distanceToSqr(participant);
            if (distance < best) {
                best = distance;
                nearest = participant;
            }
        }
        return nearest;
    }

    private void enableExtractionMovement() {
        this.setNoAi(false);
        var movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null && movement.getBaseValue() < 0.2D) {
            movement.setBaseValue(0.24D);
        }
    }
}
