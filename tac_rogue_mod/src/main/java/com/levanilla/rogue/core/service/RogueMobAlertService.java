package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.GameConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class RogueMobAlertService {
    public static final String LAST_ALERT_TICK = "LastAlertTick";
    public static final String ALERT_LEVEL = "TacRogueAlertLevel";
    public static final String INVESTIGATE_END_TIME = "InvestigateEndTime";
    public static final String INVESTIGATE_X = "InvestigateX";
    public static final String INVESTIGATE_Y = "InvestigateY";
    public static final String INVESTIGATE_Z = "InvestigateZ";
    public static final String ALERT_REASON = "TacRogueAlertReason";
    public static final String ALERT_TARGET_UUID = "TacRogueAlertTargetUuid";
    public static final String LAST_SEEN_TICK = "TacRogueLastSeenTick";
    public static final String LAST_SEEN_X = "TacRogueLastSeenX";
    public static final String LAST_SEEN_Y = "TacRogueLastSeenY";
    public static final String LAST_SEEN_Z = "TacRogueLastSeenZ";
    public static final String LOS_LOST_SINCE = "TacRogueLosLostSince";
    public static final String FLASHLIGHT_FOCUS_TICK = "TacRogueFlashlightFocusTick";
    public static final String FLASHLIGHT_FOCUS_TARGET = "TacRogueFlashlightFocusTarget";
    public static final String DECOY_X = "DecoyX";
    public static final String DECOY_Y = "DecoyY";
    public static final String DECOY_Z = "DecoyZ";
    public static final String DECOY_END_TIME = "DecoyEndTime";
    public static final String DECOY_OWNER = "DecoyOwner";

    public enum AlertLevel {
        NONE,
        INVESTIGATE,
        WARNED,
        ENGAGED
    }

    private static final long SUPPRESSED_BURST_WINDOW_TICKS = 30L;
    private static final int SUPPRESSED_BURST_SHOTS = 3;
    private static final double UNSUPPRESSED_LOS_ENGAGE_RANGE = 22.0D;
    private static final double SUPPRESSED_LOS_ENGAGE_RANGE = 5.0D;
    private static final double INVESTIGATE_VISIBLE_ENGAGE_RANGE = 12.0D;
    private static final HitAlertProfile RANGED_HIT_PROFILE =
        new HitAlertProfile(14.0D, 8.0D, 12.0D, 12.0D, true, 130L, 80L, 60L, 1.05D, 0.95D);
    private static final HitAlertProfile MELEE_HIT_PROFILE =
        new HitAlertProfile(8.0D, 3.5D, 6.0D, 6.0D, false, 90L, 60L, 40L, 0.92D, 0.82D);
    private static final ConcurrentHashMap<UUID, ShotBurst> RECENT_SHOTS = new ConcurrentHashMap<>();

    private RogueMobAlertService() {}

    public static void onGunshot(ServerPlayer shooter, boolean suppressed) {
        double radius = suppressed ? GameConstants.SUPPRESSED_ALERT_RADIUS : GameConstants.GUNSHOT_ALERT_RADIUS;
        onGunshot(shooter, suppressed, radius);
    }

    public static void onGunshot(ServerPlayer shooter, boolean suppressed, double soundRadius) {
        if (shooter == null || shooter.level().dimension() != CommonEventHandler.ROGUE_DIM) return;
        if (!(shooter.level() instanceof ServerLevel level)) return;
        double radius = clampGunshotRadius(soundRadius);
        double directRadius = suppressed
            ? Math.max(4.0D, radius * 0.28D)
            : radius * 0.36D;
        long now = shooter.level().getGameTime();
        boolean suppressedBurst = suppressed && recordShot(shooter.getUUID(), now) >= SUPPRESSED_BURST_SHOTS;
        AABB area = new AABB(shooter.blockPosition()).inflate(radius);
        List<Mob> mobs = nearbyRogueMobs(level, area, m -> sameInstance(shooter, m));
        applyGunshotAlert(shooter, suppressed, radius, directRadius, suppressedBurst, mobs);
    }

    public static void applyGunshotAlertToMobsForSmoke(ServerPlayer shooter, boolean suppressed, double soundRadius, List<Mob> mobs) {
        if (shooter == null || shooter.level().dimension() != CommonEventHandler.ROGUE_DIM || mobs == null) return;
        double radius = clampGunshotRadius(soundRadius);
        double directRadius = suppressed
            ? Math.max(4.0D, radius * 0.28D)
            : radius * 0.36D;
        long now = shooter.level().getGameTime();
        boolean suppressedBurst = suppressed && recordShot(shooter.getUUID(), now) >= SUPPRESSED_BURST_SHOTS;
        applyGunshotAlert(shooter, suppressed, radius, directRadius, suppressedBurst, mobs);
    }

    private static void applyGunshotAlert(ServerPlayer shooter, boolean suppressed, double radius, double directRadius,
                                          boolean suppressedBurst, List<Mob> mobs) {
        for (Mob mob : mobs) {
            if (mob == null || !mob.isAlive() || !isRogueMob(mob) || !sameInstance(shooter, mob)) continue;
            double dist = mob.distanceTo(shooter);
            if (dist > radius) continue;
            boolean hasLos = mob.hasLineOfSight(shooter);
            if (hasActiveTarget(mob) && !shouldOverrideActiveTarget(dist, directRadius, radius, hasLos)) continue;
            AlertLevel current = getAlertLevel(mob);
            boolean alreadyInvestigating = current.ordinal() >= AlertLevel.INVESTIGATE.ordinal();
            boolean alreadyWarned = current.ordinal() >= AlertLevel.WARNED.ordinal();

            if (shouldEngageGunshot(suppressed, suppressedBurst, dist, radius, directRadius, hasLos, alreadyInvestigating, alreadyWarned)) {
                engage(mob, shooter, 120L, suppressed ? "suppressed_gunshot" : "gunshot");
            } else if (shouldWarnGunshot(suppressed, dist, radius, directRadius, hasLos, alreadyInvestigating)) {
                warn(mob, shooter.position(), suppressed ? 90L : 140L, suppressed ? 0.95D : 1.12D,
                    suppressed ? "suppressed_gunshot" : "gunshot", shooter);
            } else {
                investigate(mob, shooter.position(), suppressed ? 45L : 80L, suppressed ? 0.85D : 1.0D,
                    suppressed ? "suppressed_gunshot" : "gunshot", shooter);
            }
        }
    }

    private static double clampGunshotRadius(double radius) {
        if (!Double.isFinite(radius)) {
            return GameConstants.GUNSHOT_ALERT_RADIUS;
        }
        return Math.max(
            GameConstants.SUPPRESSED_ALERT_RADIUS,
            Math.min(GameConstants.GUNSHOT_ALERT_RADIUS, radius));
    }

    public static void onAllyHit(ServerPlayer attacker, LivingEntity hurt) {
        handleAllyHit(attacker, hurt, RANGED_HIT_PROFILE, null);
    }

    public static void onMeleeAllyHit(ServerPlayer attacker, LivingEntity hurt) {
        handleAllyHit(attacker, hurt, MELEE_HIT_PROFILE, null);
    }

    public static void applyAllyHitAlertToMobsForSmoke(ServerPlayer attacker, LivingEntity hurt, List<Mob> mobs) {
        handleAllyHit(attacker, hurt, RANGED_HIT_PROFILE, mobs);
    }

    private static void handleAllyHit(ServerPlayer attacker, LivingEntity hurt, HitAlertProfile profile, List<Mob> candidates) {
        if (attacker == null || hurt == null || hurt.level().dimension() != CommonEventHandler.ROGUE_DIM) return;
        if (!isRogueTarget(hurt)) return;

        if (hurt instanceof Mob hurtMob && isRogueMob(hurtMob)) {
            engage(hurtMob, attacker, profile.victimDuration(), profile.reason());
        }

        AABB area = new AABB(hurt.blockPosition()).inflate(profile.alertRadius());
        List<Mob> mobs;
        if (candidates != null) {
            mobs = candidates;
        } else {
            if (!(hurt.level() instanceof ServerLevel level)) return;
            mobs = nearbyRogueMobs(level, area, m -> m != hurt && sameInstance(hurt, m));
        }
        for (Mob mob : mobs) {
            if (mob == null || !mob.isAlive() || mob == hurt || !isRogueMob(mob) || !sameInstance(hurt, mob)) continue;
            double dist = mob.distanceTo(hurt);
            if (dist > profile.alertRadius()) continue;
            AlertLevel current = getAlertLevel(mob);
            boolean hasLos = mob.hasLineOfSight(attacker);
            if (hasActiveTarget(mob) && !(dist <= profile.engageRadius() || hasLos)) continue;
            if ((profile.engageThroughWalls() && dist <= profile.engageRadius())
                || (!profile.engageThroughWalls() && dist <= profile.engageRadius() && hasLos)
                || (hasLos && dist <= profile.losEngageRadius())
                || current.ordinal() >= AlertLevel.WARNED.ordinal()) {
                engage(mob, attacker, profile.engageDuration(), profile.reason());
            } else if (dist <= profile.warnRadius() || hasLos || current.ordinal() >= AlertLevel.INVESTIGATE.ordinal()) {
                warn(mob, hurt.position(), profile.warnDuration(), profile.warnSpeed(), profile.reason(), attacker);
            } else {
                investigate(mob, hurt.position(), profile.investigateDuration(), profile.investigateSpeed(), profile.reason(), attacker);
            }
        }
    }

    public static boolean reactToFlashlight(Mob mob, Player player) {
        if (mob == null || player == null || !isRogueMob(mob) || !player.isAlive() || player.isSpectator()) return false;
        long now = mob.level().getGameTime();
        int level = player.getPersistentData().getInt("TacRogueFlashlightLevel");
        double range = 8.0D + level * 2.0D;
        if (mob.distanceTo(player) > range || !player.hasLineOfSight(mob) || !isPlayerLookingAtMob(mob, player, 0.90D)) {
            clearFlashlightFocus(mob, player);
            return false;
        }

        String targetId = player.getUUID().toString();
        if (!targetId.equals(mob.getPersistentData().getString(FLASHLIGHT_FOCUS_TARGET))) {
            mob.getPersistentData().putString(FLASHLIGHT_FOCUS_TARGET, targetId);
            mob.getPersistentData().putLong(FLASHLIGHT_FOCUS_TICK, now);
        }
        long focusTicks = now - mob.getPersistentData().getLong(FLASHLIGHT_FOCUS_TICK);
        double distance = mob.distanceTo(player);
        AlertLevel current = getAlertLevel(mob);
        if (distance <= 7.0D || focusTicks >= Math.max(18L, 36L - level * 4L)
            || current.ordinal() >= AlertLevel.WARNED.ordinal()) {
            engage(mob, player, 100L, "flashlight");
            return true;
        }

        warn(mob, player.position(), 55L, 0.92D, "flashlight", player);
        return false;
    }

    public static AlertLevel getAlertLevel(Mob mob) {
        if (mob == null) return AlertLevel.NONE;
        String raw = mob.getPersistentData().getString(ALERT_LEVEL);
        AlertLevel level;
        try {
            level = raw == null || raw.isBlank() ? AlertLevel.NONE : AlertLevel.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return AlertLevel.NONE;
        }
        if (level == AlertLevel.NONE) return AlertLevel.NONE;

        long now = mob.level().getGameTime();
        long end = mob.getPersistentData().getLong(INVESTIGATE_END_TIME);
        if (level == AlertLevel.ENGAGED) {
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive() && !target.isSpectator()) {
                boolean hasLos = mob.hasLineOfSight(target);
                if (end > 0L && now > end) {
                    if (hasLos) {
                        String reason = mob.getPersistentData().getString(ALERT_REASON);
                        markAlert(mob, AlertLevel.ENGAGED, target.position(), 40L,
                            reason == null || reason.isBlank() ? "visual_contact" : reason, target);
                        rememberSeenTarget(mob, target);
                        return AlertLevel.ENGAGED;
                    }
                    mob.setTarget(null);
                    clearAlert(mob);
                    return AlertLevel.NONE;
                }
                if (hasLos) {
                    rememberSeenTarget(mob, target);
                }
                return AlertLevel.ENGAGED;
            }
            if (end > 0L && now > end) {
                mob.setTarget(null);
                clearAlert(mob);
                return AlertLevel.NONE;
            }
            mob.setTarget(null);
            warn(mob, getInvestigatePos(mob), 60L, 0.95D, "lost_target", null);
            return AlertLevel.WARNED;
        }

        if (end > 0L && now > end) {
            clearAlert(mob);
            return AlertLevel.NONE;
        }
        return level;
    }

    public static boolean isRecentlyAlerted(Mob mob, long ticks) {
        if (mob == null) return false;
        long last = mob.getPersistentData().getLong(LAST_ALERT_TICK);
        return last > 0L && mob.level().getGameTime() - last < ticks;
    }

    public static void clearShotHistoryForSmoke(UUID shooterId) {
        if (shooterId != null) {
            RECENT_SHOTS.remove(shooterId);
        }
    }

    public static boolean promoteVisibleInvestigation(Mob mob, Player player) {
        if (mob == null || player == null || !player.isAlive() || player.isSpectator()) return false;
        rememberSeenTarget(mob, player);
        AlertLevel current = getAlertLevel(mob);
        if (current == AlertLevel.ENGAGED) return true;
        if (current == AlertLevel.WARNED) {
            engage(mob, player, 110L, "warned_visual");
            return true;
        }
        if (current != AlertLevel.INVESTIGATE) return false;

        double distance = mob.distanceTo(player);
        if (distance <= INVESTIGATE_VISIBLE_ENGAGE_RANGE) {
            engage(mob, player, 95L, "visible_investigation");
            return true;
        }
        if (distance <= 16.0D) {
            warn(mob, player.position(), 70L, 1.02D, "visible_investigation", player);
        }
        return false;
    }

    public static void engageFromVision(Mob mob, LivingEntity target) {
        if (mob == null || target == null || !target.isAlive() || target.isSpectator()) return;
        AlertLevel current = getAlertLevel(mob);
        long duration = current.ordinal() >= AlertLevel.WARNED.ordinal() ? 120L : 100L;
        engage(mob, target, duration, "visual_contact");
    }

    public static Vec3 getInvestigatePos(Mob mob) {
        if (mob == null) return Vec3.ZERO;
        var data = mob.getPersistentData();
        if (isDecoyActive(mob)) {
            return new Vec3(
                data.getDouble(DECOY_X),
                data.getDouble(DECOY_Y),
                data.getDouble(DECOY_Z));
        }
        if (data.contains(INVESTIGATE_X) && data.contains(INVESTIGATE_Y) && data.contains(INVESTIGATE_Z)) {
            return new Vec3(
                data.getDouble(INVESTIGATE_X),
                data.getDouble(INVESTIGATE_Y),
                data.getDouble(INVESTIGATE_Z));
        }
        return mob.position();
    }

    public static boolean hasAlertMemory(Mob mob) {
        return mob != null && (isDecoyActive(mob) || mob.getPersistentData().contains(INVESTIGATE_X));
    }

    public static boolean isDecoyActive(Mob mob) {
        if (mob == null) return false;
        long end = mob.getPersistentData().getLong(DECOY_END_TIME);
        return end > mob.level().getGameTime();
    }

    public static void applyDecoy(Mob mob, Vec3 pos, LivingEntity owner, long durationTicks) {
        if (mob == null || pos == null) return;
        Vec3 memoryPos = resolveInvestigationPosition(mob, pos);
        long now = mob.level().getGameTime();
        long duration = Math.max(20L, durationTicks);
        var data = mob.getPersistentData();
        data.putDouble(DECOY_X, memoryPos.x);
        data.putDouble(DECOY_Y, memoryPos.y);
        data.putDouble(DECOY_Z, memoryPos.z);
        data.putLong(DECOY_END_TIME, now + duration);
        if (owner != null) {
            data.putString(DECOY_OWNER, owner.getUUID().toString());
        } else {
            data.remove(DECOY_OWNER);
        }
        AlertLevel current = getAlertLevel(mob);
        if (current == AlertLevel.NONE) {
            markAlert(mob, AlertLevel.INVESTIGATE, memoryPos, duration, "decoy", owner);
        } else if (current != AlertLevel.ENGAGED) {
            data.putLong(LAST_ALERT_TICK, now);
            data.putString(ALERT_REASON, "decoy");
            long existingEnd = data.getLong(INVESTIGATE_END_TIME);
            data.putLong(INVESTIGATE_END_TIME, Math.max(existingEnd, now + duration));
        }
    }

    public static void rememberSeenTarget(Mob mob, LivingEntity target) {
        if (mob == null || target == null) return;
        var data = mob.getPersistentData();
        long now = mob.level().getGameTime();
        Vec3 pos = target.position();
        data.putLong(LAST_SEEN_TICK, now);
        data.putDouble(LAST_SEEN_X, pos.x);
        data.putDouble(LAST_SEEN_Y, pos.y);
        data.putDouble(LAST_SEEN_Z, pos.z);
        data.putDouble(INVESTIGATE_X, pos.x);
        data.putDouble(INVESTIGATE_Y, pos.y);
        data.putDouble(INVESTIGATE_Z, pos.z);
        data.putString(ALERT_TARGET_UUID, target.getUUID().toString());
    }

    public static boolean shouldTacticalGoalRun(Mob mob) {
        if (!isRogueMob(mob)) return false;
        AlertLevel level = getAlertLevel(mob);
        if (level == AlertLevel.NONE || level == AlertLevel.ENGAGED) return false;
        return !hasActiveTarget(mob);
    }

    public static double tacticalSpeed(Mob mob) {
        AlertLevel level = getAlertLevel(mob);
        if (level == AlertLevel.ENGAGED) return 1.12D;
        if (level == AlertLevel.WARNED) return 0.98D;
        return 0.82D;
    }

    public static void downgradeLostTarget(Mob mob) {
        if (mob == null || getAlertLevel(mob) != AlertLevel.ENGAGED) return;
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive() && !target.isSpectator()) return;
        mob.setTarget(null);
        warn(mob, getInvestigatePos(mob), 60L, 0.95D, "lost_target", null);
    }

    public static boolean monitorEngagedTarget(Mob mob, LivingEntity target, boolean hasLos) {
        if (mob == null || target == null || !target.isAlive() || target.isSpectator()) return false;
        if (mob.getTags().contains("rogue:boss")) return true;
        AlertLevel level = getAlertLevel(mob);
        if (level != AlertLevel.ENGAGED) return true;

        long now = mob.level().getGameTime();
        if (hasLos) {
            mob.getPersistentData().remove(LOS_LOST_SINCE);
            rememberSeenTarget(mob, target);
            return true;
        }

        var data = mob.getPersistentData();
        if (!data.contains(LOS_LOST_SINCE)) {
            data.putLong(LOS_LOST_SINCE, now);
        }
        mob.setTarget(null);
        mob.getNavigation().stop();
        warn(mob, getInvestigatePos(mob), 60L, 0.98D, "lost_los", target);
        return false;
    }

    private static void investigate(Mob mob, Vec3 pos, long duration, double speed, String reason, LivingEntity target) {
        markAlert(mob, AlertLevel.INVESTIGATE, pos, duration, reason, target);
        Vec3 memoryPos = getInvestigatePos(mob);
        mob.getLookControl().setLookAt(memoryPos.x, memoryPos.y, memoryPos.z, 20.0F, 20.0F);
        mob.getNavigation().moveTo(memoryPos.x, memoryPos.y, memoryPos.z, speed);
    }

    private static void warn(Mob mob, Vec3 pos, long duration, double speed, String reason, LivingEntity target) {
        markAlert(mob, AlertLevel.WARNED, pos, duration, reason, target);
        Vec3 memoryPos = getInvestigatePos(mob);
        mob.getLookControl().setLookAt(memoryPos.x, memoryPos.y, memoryPos.z, 25.0F, 25.0F);
        mob.getNavigation().moveTo(memoryPos.x, memoryPos.y, memoryPos.z, speed);
    }

    private static void engage(Mob mob, LivingEntity target, long duration, String reason) {
        markAlert(mob, AlertLevel.ENGAGED, target.position(), duration, reason, target);
        mob.setTarget(target);
    }

    private static void markAlert(Mob mob, AlertLevel level, Vec3 pos, long duration, String reason, LivingEntity target) {
        long now = mob.level().getGameTime();
        Vec3 memoryPos = resolveInvestigationPosition(mob, pos);
        mob.getPersistentData().putLong(LAST_ALERT_TICK, now);
        mob.getPersistentData().putString(ALERT_LEVEL, level.name());
        mob.getPersistentData().putString(ALERT_REASON, reason == null ? "" : reason);
        mob.getPersistentData().putLong(INVESTIGATE_END_TIME, now + Math.max(20L, duration));
        mob.getPersistentData().putDouble(INVESTIGATE_X, memoryPos.x);
        mob.getPersistentData().putDouble(INVESTIGATE_Y, memoryPos.y);
        mob.getPersistentData().putDouble(INVESTIGATE_Z, memoryPos.z);
        if (level == AlertLevel.ENGAGED) {
            mob.getPersistentData().remove(LOS_LOST_SINCE);
        }
        if (target != null) {
            rememberAlertTarget(mob, target);
        }
    }

    private static Vec3 resolveInvestigationPosition(Mob mob, Vec3 pos) {
        if (mob == null || pos == null) return pos == null ? Vec3.ZERO : pos;
        BlockPos center = BlockPos.containing(pos);
        for (int dy = 1; dy >= -5; dy--) {
            BlockPos candidate = center.offset(0, dy, 0);
            if (isStandable(mob, candidate)) {
                return new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
            }
        }
        for (int radius = 1; radius <= 3; radius++) {
            for (int dy = 1; dy >= -4; dy--) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                        BlockPos candidate = center.offset(dx, dy, dz);
                        if (isStandable(mob, candidate)) {
                            return new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                        }
                    }
                }
            }
        }
        return pos;
    }

    private static boolean isStandable(Mob mob, BlockPos pos) {
        return !mob.level().getBlockState(pos.below()).isAir()
            && mob.level().getBlockState(pos).isAir()
            && mob.level().getBlockState(pos.above()).isAir();
    }

    private static boolean shouldEngageGunshot(
        boolean suppressed,
        boolean suppressedBurst,
        double dist,
        double radius,
        double directRadius,
        boolean hasLos,
        boolean alreadyInvestigating,
        boolean alreadyWarned
    ) {
        if (!suppressed) {
            return dist <= directRadius || (hasLos && dist <= UNSUPPRESSED_LOS_ENGAGE_RANGE) || alreadyWarned;
        }
        if (dist <= directRadius || (hasLos && dist <= SUPPRESSED_LOS_ENGAGE_RANGE)) {
            return true;
        }
        if (alreadyWarned && (hasLos || dist <= Math.max(directRadius, radius * 0.5D))) {
            return true;
        }
        return suppressedBurst && (hasLos || alreadyInvestigating || dist <= Math.max(directRadius, radius * 0.65D));
    }

    private static boolean shouldWarnGunshot(
        boolean suppressed,
        double dist,
        double radius,
        double directRadius,
        boolean hasLos,
        boolean alreadyInvestigating
    ) {
        if (!suppressed) {
            return hasLos || alreadyInvestigating || dist <= directRadius;
        }
        return hasLos || alreadyInvestigating || dist <= Math.max(8.0D, radius * 0.5D);
    }

    private static int recordShot(UUID shooterId, long now) {
        ShotBurst burst = RECENT_SHOTS.compute(shooterId, (id, previous) -> {
            if (previous == null || now - previous.lastTick > SUPPRESSED_BURST_WINDOW_TICKS) {
                return new ShotBurst(now, now, 1);
            }
            int nextCount = now - previous.firstTick <= SUPPRESSED_BURST_WINDOW_TICKS
                ? previous.count + 1
                : 1;
            long firstTick = nextCount == 1 ? now : previous.firstTick;
            return new ShotBurst(firstTick, now, nextCount);
        });
        return burst == null ? 1 : burst.count;
    }

    private static void clearAlert(Mob mob) {
        if (mob == null) return;
        mob.getPersistentData().remove(ALERT_LEVEL);
        mob.getPersistentData().remove(INVESTIGATE_END_TIME);
        mob.getPersistentData().remove(INVESTIGATE_X);
        mob.getPersistentData().remove(INVESTIGATE_Y);
        mob.getPersistentData().remove(INVESTIGATE_Z);
        mob.getPersistentData().remove(ALERT_REASON);
        mob.getPersistentData().remove(ALERT_TARGET_UUID);
        mob.getPersistentData().remove(LAST_SEEN_TICK);
        mob.getPersistentData().remove(LAST_SEEN_X);
        mob.getPersistentData().remove(LAST_SEEN_Y);
        mob.getPersistentData().remove(LAST_SEEN_Z);
        mob.getPersistentData().remove(LOS_LOST_SINCE);
    }

    private static void clearFlashlightFocus(Mob mob, Player player) {
        if (mob == null || player == null) return;
        if (player.getUUID().toString().equals(mob.getPersistentData().getString(FLASHLIGHT_FOCUS_TARGET))) {
            mob.getPersistentData().remove(FLASHLIGHT_FOCUS_TARGET);
            mob.getPersistentData().remove(FLASHLIGHT_FOCUS_TICK);
        }
    }

    private static boolean isPlayerLookingAtMob(Mob mob, Player player, double threshold) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 toMob = mob.position()
            .add(0.0D, mob.getBbHeight() * 0.55D, 0.0D)
            .subtract(player.getEyePosition(1.0F))
            .normalize();
        return look.dot(toMob) >= threshold;
    }

    private static boolean isRogueMob(Mob mob) {
        return mob != null && mob.getTags().contains("tac_rogue_spawned");
    }

    private static List<Mob> nearbyRogueMobs(ServerLevel level, AABB area, Predicate<Mob> extraFilter) {
        Predicate<Mob> filter = mob -> mob.isAlive() && isRogueMob(mob) && extraFilter.test(mob);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area, filter::test);
        if (!mobs.isEmpty()) return mobs;

        // EntityJoinLevelEvent and smoke/debug commands can query during the same server tick as addFreshEntity.
        // getEntitiesOfClass may not see that entity until the section index catches up, while getAllEntities does.
        List<Mob> fallback = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && area.contains(mob.position()) && filter.test(mob)) {
                fallback.add(mob);
            }
        }
        return fallback;
    }

    private static boolean isRogueTarget(LivingEntity entity) {
        return entity != null
            && (entity.getTags().contains("tac_rogue_spawned") || entity.getTags().contains("rogue:boss"));
    }

    private static void rememberAlertTarget(Mob mob, LivingEntity target) {
        if (mob == null || target == null) return;
        var data = mob.getPersistentData();
        Vec3 pos = target.position();
        data.putLong(LAST_SEEN_TICK, mob.level().getGameTime());
        data.putDouble(LAST_SEEN_X, pos.x);
        data.putDouble(LAST_SEEN_Y, pos.y);
        data.putDouble(LAST_SEEN_Z, pos.z);
        data.putString(ALERT_TARGET_UUID, target.getUUID().toString());
    }

    private static boolean sameInstance(Entity source, Entity mob) {
        if (source == null || mob == null) return true;
        String sourceInstance = source.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        String mobInstance = mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        return sourceInstance == null || sourceInstance.isBlank()
            || mobInstance == null || mobInstance.isBlank()
            || sourceInstance.equals(mobInstance);
    }

    private static boolean shouldOverrideActiveTarget(double dist, double directRadius, double radius, boolean hasLos) {
        return dist <= directRadius || (hasLos && dist <= Math.max(directRadius, radius * 0.5D));
    }

    private static boolean hasActiveTarget(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target == null) return false;
        if (target.isAlive() && !target.isSpectator()) return true;
        mob.setTarget(null);
        return false;
    }

    private record ShotBurst(long firstTick, long lastTick, int count) {}

    private record HitAlertProfile(
        double alertRadius,
        double engageRadius,
        double losEngageRadius,
        double warnRadius,
        boolean engageThroughWalls,
        long engageDuration,
        long warnDuration,
        long investigateDuration,
        double warnSpeed,
        double investigateSpeed
    ) {
        private long victimDuration() {
            return Math.max(engageDuration, 100L);
        }

        private String reason() {
            return engageThroughWalls ? "ranged_hit" : "melee_hit";
        }
    }
}
