package com.levanilla.rogue.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

    public TacRogueNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
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
        this.addTag("npc_role:" + role.id);
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TacRogueRole")) {
            this.entityData.set(ROLE, tag.getString("TacRogueRole"));
            this.addTag("npc_role:" + tag.getString("TacRogueRole"));
        }
        this.setNoAi(true);
        this.setInvulnerable(true);
    }
}
