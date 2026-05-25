package com.levanilla.rogue.world;

import com.levanilla.rogue.core.event.CombatEventHandler;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.ScalingEngine;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import com.levanilla.rogue.core.service.RogueMobAlertService;
import com.levanilla.rogue.world.goal.RogueMobEngagedTargetMonitorGoal;
import com.levanilla.rogue.world.goal.RogueMobTacticalAlertGoal;
import com.levanilla.rogue.world.goal.RogueMobVisionGoal;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class TacRogueBossEntity extends Monster {
    private static final EntityDataAccessor<String> ROLE =
        SynchedEntityData.defineId(TacRogueBossEntity.class, EntityDataSerializers.STRING);
    private static final String ADD_TAG = "tac_rogue_boss_add";
    public static final String SHOCKWAVE_COUNT_KEY = "TacRogueBossShockwaveCount";
    public static final String SUMMON_COUNT_KEY = "TacRogueBossSummonCount";
    public static final String PRESSURE_COUNT_KEY = "TacRogueBossPressureCount";
    public static final String ANTI_KITE_COUNT_KEY = "TacRogueBossAntiKiteCount";

    private int stompCooldown = 120;
    private int summonCooldown = 220;
    private int pressureCooldown = 180;
    private int antiKiteCooldown = 100;
    private int farTargetTicks = 0;
    private int navigationRefreshCooldown = 0;

    public enum BossRole {
        BREACHER("BREACHER"),
        COMMANDER("COMMANDER"),
        VOID_WARDEN("VOID WARDEN"),
        PYRO("PYRO"),
        LEVIATHAN("LEVIATHAN");

        public final String label;

        BossRole(String label) {
            this.label = label;
        }

        public static BossRole forBiome(int biomeIndex) {
            return switch (Math.floorMod(biomeIndex, 9)) {
                case 1, 3, 7 -> COMMANDER;
                case 2, 8 -> VOID_WARDEN;
                case 4 -> PYRO;
                case 5 -> LEVIATHAN;
                default -> BREACHER;
            };
        }
    }

    public TacRogueBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 80;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 100.0D)
            .add(Attributes.ATTACK_DAMAGE, 10.0D)
            .add(Attributes.ARMOR, 8.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
            .add(Attributes.MOVEMENT_SPEED, 0.25D)
            .add(Attributes.FOLLOW_RANGE, 256.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ROLE, BossRole.BREACHER.name());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.05D, true));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 18.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new RogueMobVisionGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public BossRole getRole() {
        try {
            return BossRole.valueOf(this.entityData.get(ROLE));
        } catch (IllegalArgumentException ignored) {
            return BossRole.BREACHER;
        }
    }

    public void setRole(BossRole role) {
        this.entityData.set(ROLE, role.name());
    }

    public void configureForFloor(int floor, int biomeIndex) {
        configureForFloor(floor, biomeIndex, BossRole.forBiome(biomeIndex));
    }

    public void configureForFloor(int floor, int biomeIndex, BossRole role) {
        setRole(role);
        addTag("tac_rogue_spawned");
        addTag("rogue:boss");
        getPersistentData().putInt("TacRogueSpawnFloor", floor);
        getPersistentData().putLong("TacRogueSpawnTick", level().getServer() != null ? level().getServer().getTickCount() : 0L);
        ScalingEngine.applyBossScaling(this, floor, biomeIndex);
        setCustomName(Component.literal("§c§l[BOSS] §e" + role.label));
        setCustomNameVisible(false);
        setGlowingTag(true);
        setNoAi(false);
        setAggressive(true);
        setPersistenceRequired();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        setPersistenceRequired();
        setNoGravity(false);
        setNoAi(false);
        noPhysics = false;

        LivingEntity target = resolveBossTarget();
        if (target == null) return;

        BossRole role = getRole();
        refreshBossNavigation(target, role);

        boolean enraged = getHealth() <= getMaxHealth() * 0.45F;
        int cooldownStep = enraged ? 2 : 1;
        stompCooldown -= cooldownStep;
        summonCooldown -= cooldownStep;
        pressureCooldown -= cooldownStep;
        updateAntiKitePressure(target, role, enraged, cooldownStep);

        if ((role == BossRole.BREACHER || role == BossRole.VOID_WARDEN || role == BossRole.LEVIATHAN)
            && stompCooldown <= 0 && distanceToSqr(target) <= 196.0D) {
            doShockwave(role);
            stompCooldown = role == BossRole.VOID_WARDEN ? 140 : 170;
        }
        if ((role == BossRole.COMMANDER || role == BossRole.PYRO) && summonCooldown <= 0) {
            summonAdds(role);
            summonCooldown = role == BossRole.COMMANDER ? 240 : 300;
        }
        if (pressureCooldown <= 0) {
            doPressure(role);
            pressureCooldown = role == BossRole.PYRO ? 120 : 180;
        }
    }

    private LivingEntity resolveBossTarget() {
        LivingEntity target = getTarget();
        if (isValidBossTarget(target) && tickCount % 10 != 0) {
            return target;
        }

        Player nearest = findNearestBossTarget();
        if (nearest != null) {
            if (nearest != target) {
                setTarget(nearest);
                navigationRefreshCooldown = 0;
            }
            setAggressive(true);
            return nearest;
        }

        if (!isValidBossTarget(target)) {
            setTarget(null);
            setAggressive(false);
            getNavigation().stop();
            return null;
        }
        return target;
    }

    private boolean isValidBossTarget(LivingEntity target) {
        return target != null
            && target.isAlive()
            && !target.isRemoved()
            && !(target instanceof Player player && player.isSpectator());
    }

    private Player findNearestBossTarget() {
        if (level() instanceof ServerLevel serverLevel) {
            ServerPlayer owner = RunManager.findPlayerForDungeonPosition(serverLevel, getX(), getZ(), 300.0D);
            if (owner != null && owner.isAlive() && !owner.isSpectator()) {
                return owner;
            }
        }

        Player nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : level().players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            double distance = distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private void refreshBossNavigation(LivingEntity target, BossRole role) {
        if (navigationRefreshCooldown > 0) {
            navigationRefreshCooldown--;
            return;
        }

        double distanceSqr = distanceToSqr(target);
        navigationRefreshCooldown = distanceSqr > 324.0D ? 5 : 10;
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (distanceSqr > 6.25D || getNavigation().isDone()) {
            double speed = role == BossRole.LEVIATHAN ? 1.08D : 1.16D;
            if (distanceSqr > 324.0D) {
                speed += 0.25D;
            }
            if (distanceSqr > 625.0D) {
                speed += 0.18D;
            }
            if (getHealth() <= getMaxHealth() * 0.45F) {
                speed += 0.08D;
            }
            getNavigation().moveTo(target, speed);
        }
    }

    private void updateAntiKitePressure(LivingEntity target, BossRole role, boolean enraged, int cooldownStep) {
        if (antiKiteCooldown > 0) {
            antiKiteCooldown -= cooldownStep;
        }

        double distanceSqr = distanceToSqr(target);
        boolean far = distanceSqr >= 324.0D;
        if (far) {
            farTargetTicks += cooldownStep;
            if (distanceSqr >= 625.0D && tickCount % 20 == 0) {
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, enraged ? 1 : 0, false, false));
            }
        } else {
            farTargetTicks = Math.max(0, farTargetTicks - 4);
        }

        int triggerTicks = enraged ? 35 : 55;
        if (farTargetTicks >= triggerTicks && antiKiteCooldown <= 0) {
            doAntiKitePressure(role, target, enraged);
            farTargetTicks = 0;
            antiKiteCooldown = getAntiKiteCooldown(role, enraged);
        }
    }

    private int getAntiKiteCooldown(BossRole role, boolean enraged) {
        int base = switch (role) {
            case PYRO -> 120;
            case VOID_WARDEN, BREACHER -> 130;
            case LEVIATHAN -> 140;
            case COMMANDER -> 150;
        };
        return enraged ? Math.max(80, (int)(base * 0.75D)) : base;
    }

    private void doAntiKitePressure(BossRole role, LivingEntity target, boolean enraged) {
        incrementCounter(ANTI_KITE_COUNT_KEY);
        getLookControl().setLookAt(target, 45.0F, 45.0F);
        playAntiKiteEffects(role, target);
        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, enraged ? 1 : 0, false, true));

        if (!hasLineOfSight(target)) {
            getNavigation().moveTo(target, role == BossRole.LEVIATHAN ? 1.35D : 1.48D);
            return;
        }

        if (!(target instanceof Player player)) return;

        float damage = switch (role) {
            case VOID_WARDEN -> 6.0F;
            case PYRO -> 5.0F;
            case LEVIATHAN -> 4.5F;
            case COMMANDER, BREACHER -> 5.0F;
        };
        if (enraged) {
            damage += 1.5F;
        }
        int floor = Math.max(1, getPersistentData().getInt("TacRogueSpawnFloor"));
        damage *= ScalingEngine.getBossSpecialDamageMultiplier(floor);
        if (floor <= 5) {
            damage *= 0.75F;
        }

        int controlDuration = role == BossRole.LEVIATHAN ? 70 : 50;
        if (player instanceof ServerPlayer serverPlayer) {
            damage *= CombatEventHandler.getSpecialResistanceMultiplier(serverPlayer);
            controlDuration = CombatEventHandler.reduceNegativeEffectDuration(serverPlayer, controlDuration);
        }
        player.hurt(damageSources().mobAttack(this), damage);

        switch (role) {
            case PYRO -> player.setSecondsOnFire(player instanceof ServerPlayer serverPlayer
                ? Math.max(1, Math.round(4.0F * CombatEventHandler.getSpecialResistanceMultiplier(serverPlayer)))
                : 4);
            case COMMANDER -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, controlDuration, 0, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.max(25, controlDuration - 20), 0, false, true));
            }
            case VOID_WARDEN -> {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.max(30, controlDuration - 10), 0, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, controlDuration, 0, false, true));
            }
            case LEVIATHAN -> {
                pullTargetTowardBoss(player, 0.78D);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, controlDuration, 1, false, true));
            }
            case BREACHER -> {
                pullTargetTowardBoss(player, 0.56D);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.max(25, controlDuration - 15), 0, false, true));
            }
        }
    }

    private void pullTargetTowardBoss(Player player, double strength) {
        Vec3 pull = position().subtract(player.position());
        if (pull.lengthSqr() < 0.0001D) return;
        Vec3 normalized = pull.normalize().scale(strength);
        player.push(normalized.x, 0.08D, normalized.z);
    }

    private void doShockwave(BossRole role) {
        incrementCounter(SHOCKWAVE_COUNT_KEY);
        double radius = role == BossRole.LEVIATHAN ? 9.0D : 7.0D;
        playShockwaveEffects(role, radius);
        int floor = Math.max(1, getPersistentData().getInt("TacRogueSpawnFloor"));
        float damage = (role == BossRole.VOID_WARDEN ? 9.0F : 7.0F)
            * ScalingEngine.getBossSpecialDamageMultiplier(floor);
        if (floor <= 5) {
            damage *= 0.85F;
        }
        AABB area = getBoundingBox().inflate(radius);
        DamageSource source = damageSources().mobAttack(this);
        for (Player player : level().getEntitiesOfClass(Player.class, area, Player::isAlive)) {
            if (!hasLineOfSight(player)) continue;
            float finalDamage = damage;
            int slowDuration = 60;
            if (player instanceof ServerPlayer serverPlayer) {
                finalDamage *= CombatEventHandler.getSpecialResistanceMultiplier(serverPlayer);
                slowDuration = CombatEventHandler.reduceNegativeEffectDuration(serverPlayer, slowDuration);
            }
            player.hurt(source, finalDamage);
            Vec3 rawPush = player.position().subtract(position());
            Vec3 push = rawPush.lengthSqr() < 0.0001D
                ? new Vec3(getRandom().nextDouble() - 0.5D, 0.0D, getRandom().nextDouble() - 0.5D).normalize()
                : rawPush.normalize();
            player.knockback(role == BossRole.LEVIATHAN ? 0.25D : 0.7D, -push.x, -push.z);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowDuration, role == BossRole.LEVIATHAN ? 2 : 1, false, true));
        }
    }

    private void doPressure(BossRole role) {
        incrementCounter(PRESSURE_COUNT_KEY);
        playPressureEffects(role);
        AABB area = getBoundingBox().inflate(role == BossRole.VOID_WARDEN ? 16.0D : 12.0D);
        for (Player player : level().getEntitiesOfClass(Player.class, area, Player::isAlive)) {
            if (!hasLineOfSight(player)) continue;
            if (role == BossRole.PYRO) {
                int fireSeconds = 3;
                if (player instanceof ServerPlayer serverPlayer) {
                    fireSeconds = Math.max(1, Math.round(fireSeconds * CombatEventHandler.getSpecialResistanceMultiplier(serverPlayer)));
                }
                player.setSecondsOnFire(fireSeconds);
            } else if (role == BossRole.COMMANDER) {
                int duration = 80;
                if (player instanceof ServerPlayer serverPlayer) {
                    duration = CombatEventHandler.reduceNegativeEffectDuration(serverPlayer, duration);
                }
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, false, true));
            } else {
                int duration = 70;
                if (player instanceof ServerPlayer serverPlayer) {
                    duration = CombatEventHandler.reduceNegativeEffectDuration(serverPlayer, duration);
                }
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, 0, false, true));
            }
        }
    }

    private void summonAdds(BossRole role) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        int floor = getPersistentData().getInt("TacRogueSpawnFloor");
        String instanceId = getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        String mode = getPersistentData().getString(FloorInstanceManager.MODE_KEY);
        int existing = level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(24.0D),
            mob -> mob.isAlive() && mob.getTags().contains(ADD_TAG)).size();
        int budget = Math.max(0, 4 - existing);
        int count = Math.min(role == BossRole.COMMANDER ? 3 : 2, budget);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Mob add = role == BossRole.PYRO
                ? EntityType.SKELETON.create(level())
                : EntityType.ZOMBIE.create(level());
            if (add == null) continue;
            Vec3 spawn = findSafeAddSpawn();
            add.setPos(spawn.x, spawn.y, spawn.z);
            add.addTag("tac_rogue_spawned");
            add.addTag(ADD_TAG);
            add.getPersistentData().putInt("TacRogueSpawnFloor", floor);
            add.getPersistentData().putLong("TacRogueSpawnTick", serverLevel.getServer().getTickCount());
            FloorInstanceManager.stampEntity(add, instanceId, floor, FloorInstanceManager.EntryMode.parse(mode), null);
            add.setPersistenceRequired();
            ScalingEngine.applyScaling(add, Math.max(1, floor));
            if (add instanceof Zombie zombie) {
                zombie.setBaby(false);
            }
            add.goalSelector.addGoal(1, new RogueMobTacticalAlertGoal(add));
            add.goalSelector.addGoal(2, new RogueMobEngagedTargetMonitorGoal(add));
            add.targetSelector.removeAllGoals(goal -> true);
            add.targetSelector.addGoal(0, new RogueMobVisionGoal(add));
            if (add instanceof net.minecraft.world.entity.PathfinderMob pathfinderAdd) {
                add.targetSelector.addGoal(1, new HurtByTargetGoal(pathfinderAdd).setAlertOthers());
            }
            if (getTarget() != null) {
                RogueMobAlertService.engageFromVision(add, getTarget());
            }
            serverLevel.addFreshEntity(add);
            playSummonEffects(serverLevel, spawn, role);
            spawned++;
        }
        if (spawned > 0) incrementCounter(SUMMON_COUNT_KEY);
    }

    private void playShockwaveEffects(BossRole role, double radius) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        ParticleOptions primary = switch (role) {
            case VOID_WARDEN -> ParticleTypes.SOUL_FIRE_FLAME;
            case LEVIATHAN -> ParticleTypes.SPLASH;
            default -> ParticleTypes.CRIT;
        };
        ParticleOptions secondary = switch (role) {
            case VOID_WARDEN -> ParticleTypes.ELECTRIC_SPARK;
            case LEVIATHAN -> ParticleTypes.CLOUD;
            default -> ParticleTypes.LARGE_SMOKE;
        };
        int points = role == BossRole.LEVIATHAN ? 36 : 30;
        double y = getY() + 0.25D;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i) / points;
            double x = getX() + Math.cos(angle) * radius;
            double z = getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(primary, x, y, z, 1, 0.04D, 0.03D, 0.04D, 0.01D);
            if ((i & 1) == 0) {
                serverLevel.sendParticles(secondary, x, y + 0.15D, z, 1, 0.06D, 0.04D, 0.06D, 0.01D);
            }
        }
        serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0D, getZ(), 8, 0.7D, 0.25D, 0.7D, 0.02D);
        SoundEvent sound = role == BossRole.VOID_WARDEN ? SoundEvents.WARDEN_SONIC_BOOM : SoundEvents.GENERIC_EXPLODE;
        serverLevel.playSound(null, blockPosition(), sound, SoundSource.HOSTILE, 1.05F, role == BossRole.LEVIATHAN ? 0.65F : 0.82F);
    }

    private void playPressureEffects(BossRole role) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        ParticleOptions particle = switch (role) {
            case PYRO -> ParticleTypes.FLAME;
            case COMMANDER -> ParticleTypes.ENCHANT;
            case VOID_WARDEN -> ParticleTypes.SOUL;
            case LEVIATHAN -> ParticleTypes.SPLASH;
            default -> ParticleTypes.SMOKE;
        };
        ParticleOptions secondary = role == BossRole.PYRO ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD;
        int count = role == BossRole.COMMANDER ? 18 : 16;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D * i) / count;
            double radius = 1.4D + (i % 3) * 0.45D;
            double x = getX() + Math.cos(angle) * radius;
            double z = getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(particle, x, getY() + 1.0D, z, 1, 0.05D, 0.12D, 0.05D, 0.015D);
        }
        serverLevel.sendParticles(secondary, getX(), getY() + 1.0D, getZ(), 6, 0.45D, 0.35D, 0.45D, 0.015D);
        SoundEvent sound = role == BossRole.PYRO ? SoundEvents.BLAZE_SHOOT : SoundEvents.BEACON_POWER_SELECT;
        serverLevel.playSound(null, blockPosition(), sound, SoundSource.HOSTILE, 0.85F, role == BossRole.COMMANDER ? 1.3F : 0.85F);
    }

    private void playAntiKiteEffects(BossRole role, LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        ParticleOptions beam = switch (role) {
            case PYRO -> ParticleTypes.FLAME;
            case COMMANDER -> ParticleTypes.ENCHANT;
            case VOID_WARDEN -> ParticleTypes.ELECTRIC_SPARK;
            case LEVIATHAN -> ParticleTypes.SPLASH;
            default -> ParticleTypes.CRIT;
        };
        ParticleOptions impact = switch (role) {
            case PYRO -> ParticleTypes.LARGE_SMOKE;
            case VOID_WARDEN -> ParticleTypes.SOUL;
            case LEVIATHAN -> ParticleTypes.CLOUD;
            default -> ParticleTypes.POOF;
        };

        Vec3 start = position().add(0.0D, getEyeHeight() * 0.85D, 0.0D);
        Vec3 end = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
        Vec3 delta = end.subtract(start);
        int points = 18;
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Vec3 p = start.add(delta.scale(t));
            serverLevel.sendParticles(beam, p.x, p.y, p.z, 1, 0.035D, 0.035D, 0.035D, 0.01D);
        }
        serverLevel.sendParticles(impact, end.x, end.y, end.z, 10, 0.3D, 0.35D, 0.3D, 0.02D);
        SoundEvent sound = switch (role) {
            case PYRO -> SoundEvents.BLAZE_SHOOT;
            case VOID_WARDEN -> SoundEvents.WARDEN_SONIC_BOOM;
            default -> SoundEvents.BEACON_POWER_SELECT;
        };
        float pitch = switch (role) {
            case LEVIATHAN -> 0.65F;
            case COMMANDER -> 1.35F;
            case VOID_WARDEN -> 0.8F;
            default -> 0.95F;
        };
        serverLevel.playSound(null, blockPosition(), sound, SoundSource.HOSTILE, 0.9F, pitch);
    }

    private void playSummonEffects(ServerLevel serverLevel, Vec3 spawn, BossRole role) {
        ParticleOptions particle = role == BossRole.PYRO ? ParticleTypes.FLAME : ParticleTypes.ENCHANT;
        ParticleOptions secondary = role == BossRole.PYRO ? ParticleTypes.SMOKE : ParticleTypes.CLOUD;
        serverLevel.sendParticles(particle, spawn.x, spawn.y + 0.8D, spawn.z, 12, 0.25D, 0.75D, 0.25D, 0.03D);
        serverLevel.sendParticles(secondary, spawn.x, spawn.y + 0.2D, spawn.z, 8, 0.3D, 0.15D, 0.3D, 0.01D);
        SoundEvent sound = role == BossRole.PYRO ? SoundEvents.BLAZE_SHOOT : SoundEvents.BEACON_ACTIVATE;
        serverLevel.playSound(null, spawn.x, spawn.y, spawn.z, sound, SoundSource.HOSTILE, 0.75F, role == BossRole.PYRO ? 0.8F : 1.2F);
    }

    private Vec3 findSafeAddSpawn() {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = 3.0D + random.nextDouble() * 4.0D;
            int x = Mth.floor(getX() + Math.cos(angle) * dist);
            int z = Mth.floor(getZ() + Math.sin(angle) * dist);
            int baseY = blockPosition().getY();
            for (int dy = -2; dy <= 3; dy++) {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, baseY + dy, z);
                if (!level().getBlockState(pos).isSolid()
                    && !level().getBlockState(pos.above()).isSolid()
                    && level().getBlockState(pos.below()).isSolid()) {
                    return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                }
            }
        }
        return position().add(0.0D, 0.0D, 2.0D);
    }

    private void incrementCounter(String key) {
        getPersistentData().putInt(key, getPersistentData().getInt(key) + 1);
    }

    @Override
    public void die(DamageSource source) {
        removeSummonedAdds();
        super.die(source);
    }

    private void removeSummonedAdds() {
        if (!(level() instanceof ServerLevel)) return;
        int floor = getPersistentData().getInt("TacRogueSpawnFloor");
        String instanceId = getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        List<Mob> adds = level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(64.0D),
            mob -> mob.getTags().contains(ADD_TAG)
                && mob.getPersistentData().getInt("TacRogueSpawnFloor") == floor
                && (instanceId.isBlank() || instanceId.equals(mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY))));
        for (Mob add : adds) {
            add.remove(RemovalReason.DISCARDED);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("TacRogueBossRole", getRole().name());
        tag.putInt("StompCooldown", stompCooldown);
        tag.putInt("SummonCooldown", summonCooldown);
        tag.putInt("PressureCooldown", pressureCooldown);
        tag.putInt("AntiKiteCooldown", antiKiteCooldown);
        tag.putInt("FarTargetTicks", farTargetTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TacRogueBossRole")) {
            this.entityData.set(ROLE, tag.getString("TacRogueBossRole"));
        }
        if (tag.contains("StompCooldown")) stompCooldown = tag.getInt("StompCooldown");
        if (tag.contains("SummonCooldown")) summonCooldown = tag.getInt("SummonCooldown");
        if (tag.contains("PressureCooldown")) pressureCooldown = tag.getInt("PressureCooldown");
        if (tag.contains("AntiKiteCooldown")) antiKiteCooldown = tag.getInt("AntiKiteCooldown");
        if (tag.contains("FarTargetTicks")) farTargetTicks = tag.getInt("FarTargetTicks");
    }
}
