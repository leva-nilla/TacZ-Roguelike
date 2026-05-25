package com.levanilla.rogue.world;

import com.tacz.guns.init.ModSounds;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TacRogueNpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> ROLE =
        SynchedEntityData.defineId(TacRogueNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SUPPORT =
        SynchedEntityData.defineId(TacRogueNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final String EXTRACTION_OFFICER_KEY = "TacRogueExtractionOfficer";
    private static final String SUPPORT_OPERATOR_KEY = "TacRogueSupportOperator";
    private static final String SUPPORT_EXPIRES_AT_KEY = "TacRogueSupportExpiresAt";
    private static final double EXTRACTION_FOLLOW_START_DISTANCE_SQR = 4.5D * 4.5D;
    private static final double EXTRACTION_FOLLOW_STOP_DISTANCE_SQR = 2.5D * 2.5D;
    private static final double EXTRACTION_FOLLOW_SPEED = 1.05D;
    private static final double SUPPORT_SEARCH_RADIUS = 46.0D;
    private static final double SUPPORT_SHOOT_RADIUS_SQR = 34.0D * 34.0D;
    private static final double SUPPORT_APPROACH_DISTANCE_SQR = 13.0D * 13.0D;
    private static final double SUPPORT_STOP_DISTANCE_SQR = 7.0D * 7.0D;
    private static final double SUPPORT_MOVE_SPEED = 1.16D;
    private static final float SUPPORT_SHOT_DAMAGE = 8.5F;
    private static final int SUPPORT_MIN_SHOOT_COOLDOWN = 7;
    private static final int SUPPORT_RANDOM_SHOOT_COOLDOWN = 6;

    private int supportShootCooldown = 8;

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
        this.entityData.define(SUPPORT, false);
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
        this.entityData.set(SUPPORT, false);
        this.addTag("tac_rogue_npc");
        this.addTag("tac_rogue_lobby_npc");
        this.getPersistentData().putBoolean("TacRogueLobbyNpc", true);
        this.getPersistentData().putString("TacRogueLobbyRole", role.id);
    }

    public void markExtractionOfficer() {
        this.entityData.set(SUPPORT, false);
        this.addTag("tac_rogue_npc");
        this.addTag("tac_rogue_extraction_npc");
        this.getPersistentData().putBoolean(EXTRACTION_OFFICER_KEY, true);
        enableExtractionMovement();
    }

    public void markSupportOperator(int lifetimeTicks) {
        this.entityData.set(SUPPORT, true);
        this.addTag("tac_rogue_npc");
        this.addTag("tac_rogue_support_npc");
        this.getPersistentData().putBoolean(SUPPORT_OPERATOR_KEY, true);
        long now = level().getServer() != null ? level().getServer().getTickCount() : 0L;
        this.getPersistentData().putLong(SUPPORT_EXPIRES_AT_KEY, now + Math.max(20, lifetimeTicks));
        this.setNoAi(false);
        var movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null && movement.getBaseValue() < 0.24D) {
            movement.setBaseValue(0.24D);
        }
    }

    public boolean isSupportOperator() {
        return this.entityData.get(SUPPORT)
            || this.getPersistentData().getBoolean(SUPPORT_OPERATOR_KEY)
            || this.getTags().contains("tac_rogue_support_npc");
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
        if (this.getPersistentData().getBoolean(SUPPORT_OPERATOR_KEY)) {
            tag.putBoolean(SUPPORT_OPERATOR_KEY, true);
            tag.putLong(SUPPORT_EXPIRES_AT_KEY, this.getPersistentData().getLong(SUPPORT_EXPIRES_AT_KEY));
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
            this.entityData.set(SUPPORT, false);
            this.getPersistentData().putBoolean(EXTRACTION_OFFICER_KEY, true);
            this.addTag("tac_rogue_extraction_npc");
            enableExtractionMovement();
        } else if (tag.getBoolean(SUPPORT_OPERATOR_KEY)) {
            this.entityData.set(SUPPORT, true);
            this.getPersistentData().putBoolean(SUPPORT_OPERATOR_KEY, true);
            this.getPersistentData().putLong(SUPPORT_EXPIRES_AT_KEY, tag.getLong(SUPPORT_EXPIRES_AT_KEY));
            this.addTag("tac_rogue_support_npc");
            this.setNoAi(false);
            var movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (movement != null && movement.getBaseValue() < 0.24D) {
                movement.setBaseValue(0.24D);
            }
        } else {
            this.entityData.set(SUPPORT, false);
            this.setNoAi(true);
        }
        this.setInvulnerable(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getPersistentData().getBoolean(SUPPORT_OPERATOR_KEY)) {
            long expiresAt = getPersistentData().getLong(SUPPORT_EXPIRES_AT_KEY);
            long now = level().getServer() != null ? level().getServer().getTickCount() : tickCount;
            if (expiresAt > 0L && now >= expiresAt) {
                remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            } else {
                tickSupportCombat();
            }
        }
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

    private void lookAtNearestHostile() {
        if (!(level() instanceof ServerLevel level)) return;
        java.util.List<Mob> mobs = level.getEntitiesOfClass(
            Mob.class,
            getBoundingBox().inflate(16.0D),
            this::isSupportTarget);
        if (mobs.isEmpty()) return;
        Mob nearest = mobs.get(0);
        double best = distanceToSqr(nearest);
        for (Mob mob : mobs) {
            double dist = distanceToSqr(mob);
            if (dist < best) {
                best = dist;
                nearest = mob;
            }
        }
        getLookControl().setLookAt(nearest, 45.0F, 35.0F);
    }

    private void tickSupportCombat() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        var movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null && movement.getBaseValue() < 0.24D) {
            movement.setBaseValue(0.24D);
        }

        Mob target = findSupportTarget(serverLevel);
        if (target == null) {
            if (tickCount % 20 == 0) {
                this.getNavigation().stop();
            }
            return;
        }

        this.getLookControl().setLookAt(target, 55.0F, 45.0F);
        double distanceSqr = this.distanceToSqr(target);
        boolean hasLos = this.hasLineOfSight(target);
        if (distanceSqr > SUPPORT_APPROACH_DISTANCE_SQR || (!hasLos && distanceSqr > SUPPORT_STOP_DISTANCE_SQR)) {
            this.getNavigation().moveTo(target, SUPPORT_MOVE_SPEED);
        } else if (distanceSqr <= SUPPORT_STOP_DISTANCE_SQR) {
            this.getNavigation().stop();
        }

        if (supportShootCooldown > 0) {
            supportShootCooldown--;
        }
        if (supportShootCooldown <= 0 && hasLos && distanceSqr <= SUPPORT_SHOOT_RADIUS_SQR) {
            fireSupportBurst(serverLevel, target);
            supportShootCooldown = SUPPORT_MIN_SHOOT_COOLDOWN + this.getRandom().nextInt(SUPPORT_RANDOM_SHOOT_COOLDOWN);
        }
    }

    private Mob findSupportTarget(ServerLevel level) {
        AABB area = getBoundingBox().inflate(SUPPORT_SEARCH_RADIUS, 18.0D, SUPPORT_SEARCH_RADIUS);
        java.util.List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area, this::isSupportTarget);
        if (mobs.isEmpty()) return null;

        Mob bestMob = null;
        double bestScore = Double.MAX_VALUE;
        for (Mob mob : mobs) {
            double distance = this.distanceToSqr(mob);
            double score = distance;
            if (this.hasLineOfSight(mob)) score *= 0.55D;
            if (mob.getTarget() instanceof ServerPlayer) score *= 0.7D;
            if (score < bestScore) {
                bestScore = score;
                bestMob = mob;
            }
        }
        return bestMob;
    }

    private boolean isSupportTarget(Mob mob) {
        if (mob == null || !mob.isAlive() || mob.isRemoved()) return false;
        if (!mob.getTags().contains("tac_rogue_spawned")) return false;
        if (mob.getTags().contains("tac_rogue_npc")) return false;
        String ownInstance = this.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        if (ownInstance == null || ownInstance.isBlank()) return true;
        return ownInstance.equals(mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY));
    }

    private void fireSupportBurst(ServerLevel level, Mob target) {
        Vec3 start = this.position().add(0.0D, this.getBbHeight() * 0.78D, 0.0D)
            .add(this.getLookAngle().scale(0.38D));
        Vec3 end = target.position().add(0.0D, target.getBbHeight() * 0.62D, 0.0D);

        level.playSound(null, this.blockPosition(), ModSounds.GUN.get(), SoundSource.HOSTILE,
            1.2F, 0.9F + this.getRandom().nextFloat() * 0.18F);
        level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE,
            0.18F, 1.6F + this.getRandom().nextFloat() * 0.15F);

        int shots = 2 + this.getRandom().nextInt(2);
        for (int i = 0; i < shots && target.isAlive(); i++) {
            spawnSupportTracer(level, start, end);
            level.sendParticles(ParticleTypes.CRIT,
                end.x, end.y, end.z,
                6, 0.16D, 0.18D, 0.16D, 0.045D);
            target.hurt(this.damageSources().mobAttack(this), SUPPORT_SHOT_DAMAGE);
        }
    }

    private void spawnSupportTracer(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length <= 0.05D) return;
        int steps = Math.max(4, Math.min(28, (int) Math.round(length * 1.35D)));
        Vec3 step = delta.scale(1.0D / steps);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = start.add(step.scale(i));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.SMOKE,
            start.x, start.y, start.z,
            3, 0.05D, 0.05D, 0.05D, 0.012D);
    }
}
