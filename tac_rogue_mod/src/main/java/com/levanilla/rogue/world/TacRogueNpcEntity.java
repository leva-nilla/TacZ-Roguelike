package com.levanilla.rogue.world;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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
    private static final double SUPPORT_SEARCH_RADIUS = 34.0D;
    private static final double SUPPORT_SHOOT_RADIUS_SQR = 34.0D * 34.0D;
    private static final double SUPPORT_AIM_RADIUS_SQR = 26.0D * 26.0D;
    private static final double SUPPORT_APPROACH_DISTANCE_SQR = 13.0D * 13.0D;
    private static final double SUPPORT_STOP_DISTANCE_SQR = 7.0D * 7.0D;
    private static final double SUPPORT_MOVE_SPEED = 1.16D;
    private static final int SUPPORT_MIN_SHOOT_COOLDOWN = 4;
    private static final int SUPPORT_RANDOM_SHOOT_COOLDOWN = 4;
    private static final int SUPPORT_AIM_SETTLE_TICKS = 4;
    private static final int SUPPORT_TACTICAL_STEP_MIN_TICKS = 26;
    private static final int SUPPORT_TACTICAL_STEP_RANDOM_TICKS = 18;
    private static final double SUPPORT_TACTICAL_STEP_DISTANCE = 2.35D;
    private static final double SUPPORT_TOO_CLOSE_DISTANCE_SQR = 4.6D * 4.6D;

    private int supportShootCooldown = 8;
    private int supportTargetRefreshTicks = 0;
    private int supportGunOperatorRefreshTicks = 0;
    private int supportAimReadyTicks = 0;
    private int supportTacticalStepTicks = 10;
    private String supportPreparedGunKey = "";
    private Mob supportTarget;

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

    public void prepareSupportGunOperator() {
        if (this.level().isClientSide || !isSupportOperator()) return;
        ItemStack held = this.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(held);
        if (gun == null) {
            supportPreparedGunKey = "";
            return;
        }
        IGunOperator operator = IGunOperator.fromLivingEntity(this);
        String key = supportGunKey(held, gun);
        if (operator.getDataHolder().currentGunItem == null || operator.getCacheProperty() == null || !key.equals(supportPreparedGunKey)) {
            operator.draw(this::getMainHandItem);
            supportPreparedGunKey = key;
        }
        operator.aim(true);
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
            if (supportGunOperatorRefreshTicks-- <= 0) {
                prepareSupportGunOperator();
                supportGunOperatorRefreshTicks = 20;
            }
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

        Mob target = getSupportCombatTarget(serverLevel);
        if (target == null) {
            if (tickCount % 20 == 0) {
                this.getNavigation().stop();
            }
            return;
        }

        double distanceSqr = this.distanceToSqr(target);
        boolean hasLos = hasSupportLineOfFire(target);
        if (!hasLos) {
            supportAimReadyTicks = 0;
            if (!hasSupportPathTo(target)) {
                supportTarget = null;
                supportTargetRefreshTicks = 0;
                this.getNavigation().stop();
                return;
            }
            if (tickCount % 6 == 0 || this.getNavigation().isDone()) {
                this.getNavigation().moveTo(target, SUPPORT_MOVE_SPEED);
            }
            return;
        }
        this.getLookControl().setLookAt(target, 55.0F, 45.0F);
        if (distanceSqr > SUPPORT_APPROACH_DISTANCE_SQR) {
            this.getNavigation().moveTo(target, SUPPORT_MOVE_SPEED);
        } else if (distanceSqr <= SUPPORT_STOP_DISTANCE_SQR) {
            this.getNavigation().stop();
        }
        tickSupportTacticalMovement(target, distanceSqr);

        if (supportShootCooldown > 0) {
            supportShootCooldown--;
        }
        if (hasLos && distanceSqr <= SUPPORT_AIM_RADIUS_SQR) {
            supportAimReadyTicks++;
        } else {
            supportAimReadyTicks = 0;
        }

        if (supportShootCooldown <= 0
            && hasLos
            && distanceSqr <= SUPPORT_AIM_RADIUS_SQR
            && supportAimReadyTicks >= SUPPORT_AIM_SETTLE_TICKS
            && IGunOperator.fromLivingEntity(this).getSynAimingProgress() >= 1.0F) {
            ShootResult result = fireSupportTacZShot(target);
            supportShootCooldown = result == ShootResult.SUCCESS
                ? SUPPORT_MIN_SHOOT_COOLDOWN + this.getRandom().nextInt(SUPPORT_RANDOM_SHOOT_COOLDOWN)
                : 2 + this.getRandom().nextInt(3);
        }
    }

    private Mob getSupportCombatTarget(ServerLevel level) {
        if (supportTargetRefreshTicks > 0 && isTrackableSupportTarget(supportTarget)) {
            supportTargetRefreshTicks--;
            return supportTarget;
        }
        supportTargetRefreshTicks = 8 + this.getRandom().nextInt(5);
        supportTarget = findSupportTarget(level);
        return supportTarget;
    }

    private Mob findSupportTarget(ServerLevel level) {
        AABB area = getBoundingBox().inflate(SUPPORT_SEARCH_RADIUS, 18.0D, SUPPORT_SEARCH_RADIUS);
        java.util.List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area, this::isSupportTarget);
        if (mobs.isEmpty()) return null;

        Mob bestMob = null;
        double bestScore = Double.MAX_VALUE;
        for (Mob mob : mobs) {
            boolean hasLos = hasSupportLineOfFire(mob);
            if (!hasLos && !hasSupportPathTo(mob)) continue;
            double distance = this.distanceToSqr(mob);
            double score = distance;
            if (hasLos) {
                score *= 0.42D;
            } else {
                score *= 1.25D;
            }
            if (mob.getTarget() instanceof ServerPlayer) score *= 0.7D;
            if (score < bestScore) {
                bestScore = score;
                bestMob = mob;
            }
        }
        return bestMob;
    }

    private boolean isTrackableSupportTarget(Mob mob) {
        return mob != null
            && isSupportTarget(mob)
            && this.distanceToSqr(mob) <= SUPPORT_SEARCH_RADIUS * SUPPORT_SEARCH_RADIUS
            && (hasSupportLineOfFire(mob) || hasSupportPathTo(mob));
    }

    private boolean hasSupportLineOfFire(Mob target) {
        if (target == null || !target.isAlive() || target.isRemoved()) return false;
        if (!this.hasLineOfSight(target)) return false;
        Vec3 start = this.position().add(0.0D, this.getBbHeight() * 0.78D, 0.0D);
        Vec3 end = target.position().add(0.0D, target.getBbHeight() * 0.62D, 0.0D);
        HitResult hit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(end) <= 0.85D;
    }

    private boolean hasSupportPathTo(Mob target) {
        if (target == null || !target.isAlive() || target.isRemoved()) return false;
        Path path = this.getNavigation().createPath(target.blockPosition(), 1);
        return path != null && path.canReach();
    }

    private void tickSupportTacticalMovement(Mob target, double distanceSqr) {
        if (target == null || !target.isAlive()) return;
        if (distanceSqr > SUPPORT_AIM_RADIUS_SQR) return;
        if (supportTacticalStepTicks > 0) {
            supportTacticalStepTicks--;
            return;
        }
        supportTacticalStepTicks = SUPPORT_TACTICAL_STEP_MIN_TICKS + this.getRandom().nextInt(SUPPORT_TACTICAL_STEP_RANDOM_TICKS);
        Vec3 step = findSupportTacticalStep(target, distanceSqr);
        if (step != null) {
            this.getNavigation().moveTo(step.x, step.y, step.z, SUPPORT_MOVE_SPEED * 0.84D);
        }
    }

    private Vec3 findSupportTacticalStep(Mob target, double distanceSqr) {
        Vec3 toTarget = target.position().subtract(this.position());
        Vec3 flat = new Vec3(toTarget.x, 0.0D, toTarget.z);
        if (flat.lengthSqr() < 0.001D) return null;
        Vec3 forward = flat.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        if (((this.getId() + this.tickCount / 40) & 1) == 0) {
            side = side.scale(-1.0D);
        }
        Vec3 away = forward.scale(-1.0D);
        Vec3[] offsets = distanceSqr <= SUPPORT_TOO_CLOSE_DISTANCE_SQR
            ? new Vec3[] {
                away.scale(2.0D).add(side.scale(1.35D)),
                away.scale(2.0D).add(side.scale(-1.35D)),
                away.scale(2.8D)
            }
            : new Vec3[] {
                side.scale(SUPPORT_TACTICAL_STEP_DISTANCE),
                side.scale(-SUPPORT_TACTICAL_STEP_DISTANCE),
                side.scale(SUPPORT_TACTICAL_STEP_DISTANCE * 0.65D).add(forward.scale(1.15D)),
                side.scale(-SUPPORT_TACTICAL_STEP_DISTANCE * 0.65D).add(forward.scale(1.15D))
            };
        for (Vec3 offset : offsets) {
            Vec3 candidate = this.position().add(offset);
            BlockPos pos = BlockPos.containing(candidate);
            if (!isSupportStandable(pos)) continue;
            Path path = this.getNavigation().createPath(pos, 0);
            if (path != null && path.canReach()) {
                return Vec3.atBottomCenterOf(pos);
            }
        }
        return null;
    }

    private boolean isSupportStandable(BlockPos pos) {
        if (pos == null) return false;
        if (!this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty()) return false;
        BlockPos head = pos.above();
        if (!this.level().getBlockState(head).getCollisionShape(this.level(), head).isEmpty()) return false;
        BlockPos floor = pos.below();
        return !this.level().getBlockState(floor).getCollisionShape(this.level(), floor).isEmpty();
    }

    private boolean isSupportTarget(Mob mob) {
        if (mob == null || !mob.isAlive() || mob.isRemoved()) return false;
        if (!mob.getTags().contains("tac_rogue_spawned")) return false;
        if (mob.getTags().contains("tac_rogue_npc")) return false;
        String ownInstance = this.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        if (ownInstance == null || ownInstance.isBlank()) return true;
        return ownInstance.equals(mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY));
    }

    private ShootResult fireSupportTacZShot(Mob target) {
        if (target == null || !target.isAlive()) return ShootResult.UNKNOWN_FAIL;
        ItemStack held = this.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(held);
        if (gun == null) return ShootResult.NOT_GUN;

        prepareSupportGunOperator();
        SupportAim aim = aimAt(target);
        IGunOperator operator = IGunOperator.fromLivingEntity(this);
        ShootResult result = operator.shoot(() -> aim.pitch, () -> aim.yaw);
        if (result == ShootResult.SUCCESS) {
            return result;
        }
        if (result == ShootResult.NO_AMMO || result == ShootResult.NEED_BOLT) {
            primeSupportGunAmmo(held, gun);
            result = operator.shoot(() -> aim.pitch, () -> aim.yaw);
        }
        return result;
    }

    private SupportAim aimAt(Mob target) {
        Vec3 start = this.getEyePosition();
        Vec3 end = target.position().add(0.0D, target.getBbHeight() * 0.62D, 0.0D);
        Vec3 delta = end.subtract(start);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float)(-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;
        this.getLookControl().setLookAt(end.x, end.y, end.z, 60.0F, 50.0F);
        return new SupportAim(pitch, yaw);
    }

    private void primeSupportGunAmmo(ItemStack held, IGun gun) {
        if (held.isEmpty() || gun == null) return;
        ResourceLocation gunId = gun.getGunId(held);
        if (gunId == null) return;
        int base = com.levanilla.rogue.core.TacZRegistryHelper.getMagazineSize(gunId.toString());
        int magazineSize = com.levanilla.rogue.core.TacZMagazineHelper.getEffectiveMagazineSize(held, null, base);
        gun.setCurrentAmmoCount(held, Math.max(1, magazineSize));
        gun.setBulletInBarrel(held, true);
        IGunOperator.fromLivingEntity(this).draw(this::getMainHandItem);
    }

    private static String supportGunKey(ItemStack held, IGun gun) {
        ResourceLocation gunId = gun.getGunId(held);
        return held.getItem().builtInRegistryHolder().key().location() + "|" + (gunId == null ? "" : gunId);
    }

    private record SupportAim(float pitch, float yaw) {
    }
}
