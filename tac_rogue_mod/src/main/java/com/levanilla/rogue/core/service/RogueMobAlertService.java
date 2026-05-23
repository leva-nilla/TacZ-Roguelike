package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.GameConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RogueMobAlertService {
    public static final String LAST_ALERT_TICK = "LastAlertTick";
    public static final String ALERT_LEVEL = "TacRogueAlertLevel";
    public static final String INVESTIGATE_END_TIME = "InvestigateEndTime";
    public static final String INVESTIGATE_X = "InvestigateX";
    public static final String INVESTIGATE_Y = "InvestigateY";
    public static final String INVESTIGATE_Z = "InvestigateZ";
    public static final String FLASHLIGHT_FOCUS_TICK = "TacRogueFlashlightFocusTick";
    public static final String FLASHLIGHT_FOCUS_TARGET = "TacRogueFlashlightFocusTarget";

    public enum AlertLevel {
        NONE,
        INVESTIGATE,
        WARNED,
        ENGAGED
    }

    private static final long SUPPRESSED_BURST_WINDOW_TICKS = 30L;
    private static final int SUPPRESSED_BURST_SHOTS = 3;
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
        double radius = clampGunshotRadius(soundRadius);
        double directRadius = suppressed
            ? Math.max(4.0D, radius * 0.28D)
            : radius * 0.36D;
        long now = shooter.level().getGameTime();
        boolean suppressedBurst = suppressed && recordShot(shooter.getUUID(), now) >= SUPPRESSED_BURST_SHOTS;
        AABB area = new AABB(shooter.blockPosition()).inflate(radius);
        List<Mob> mobs = shooter.level().getEntitiesOfClass(
            Mob.class, area, m -> m.isAlive() && isRogueMob(m));
        for (Mob mob : mobs) {
            if (hasActiveTarget(mob)) continue;
            double dist = mob.distanceTo(shooter);
            boolean hasLos = mob.hasLineOfSight(shooter);
            AlertLevel current = getAlertLevel(mob);
            boolean alreadyInvestigating = current.ordinal() >= AlertLevel.INVESTIGATE.ordinal();
            boolean alreadyWarned = current.ordinal() >= AlertLevel.WARNED.ordinal();

            if (shouldEngageGunshot(suppressed, suppressedBurst, dist, radius, directRadius, hasLos, alreadyInvestigating, alreadyWarned)) {
                engage(mob, shooter, 120L);
            } else if (shouldWarnGunshot(suppressed, dist, radius, directRadius, hasLos, alreadyInvestigating)) {
                warn(mob, shooter.position(), suppressed ? 60L : 100L, suppressed ? 0.95D : 1.12D);
            } else {
                investigate(mob, shooter.position(), suppressed ? 45L : 80L, suppressed ? 0.85D : 1.0D);
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
        handleAllyHit(attacker, hurt, RANGED_HIT_PROFILE);
    }

    public static void onMeleeAllyHit(ServerPlayer attacker, LivingEntity hurt) {
        handleAllyHit(attacker, hurt, MELEE_HIT_PROFILE);
    }

    private static void handleAllyHit(ServerPlayer attacker, LivingEntity hurt, HitAlertProfile profile) {
        if (attacker == null || hurt == null || hurt.level().dimension() != CommonEventHandler.ROGUE_DIM) return;
        if (!isRogueTarget(hurt)) return;

        if (hurt instanceof Mob hurtMob && isRogueMob(hurtMob)) {
            if (!hasActiveTarget(hurtMob)) {
                engage(hurtMob, attacker, profile.victimDuration());
            }
        }

        AABB area = new AABB(hurt.blockPosition()).inflate(profile.alertRadius());
        List<Mob> mobs = hurt.level().getEntitiesOfClass(
            Mob.class, area, m -> m.isAlive() && m != hurt && isRogueMob(m));
        for (Mob mob : mobs) {
            if (hasActiveTarget(mob)) continue;
            double dist = mob.distanceTo(hurt);
            AlertLevel current = getAlertLevel(mob);
            boolean hasLos = mob.hasLineOfSight(attacker);
            if ((profile.engageThroughWalls() && dist <= profile.engageRadius())
                || (!profile.engageThroughWalls() && dist <= profile.engageRadius() && hasLos)
                || (hasLos && dist <= profile.losEngageRadius())
                || current.ordinal() >= AlertLevel.WARNED.ordinal()) {
                engage(mob, attacker, profile.engageDuration());
            } else if (dist <= profile.warnRadius() || hasLos || current.ordinal() >= AlertLevel.INVESTIGATE.ordinal()) {
                warn(mob, hurt.position(), profile.warnDuration(), profile.warnSpeed());
            } else {
                investigate(mob, hurt.position(), profile.investigateDuration(), profile.investigateSpeed());
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
            engage(mob, player, 100L);
            return true;
        }

        warn(mob, player.position(), 55L, 0.92D);
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
        if (level == AlertLevel.ENGAGED) {
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive()) {
                return AlertLevel.ENGAGED;
            }
            warn(mob, getInvestigatePos(mob), 60L, 0.95D);
            return AlertLevel.WARNED;
        }

        long end = mob.getPersistentData().getLong(INVESTIGATE_END_TIME);
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

    public static boolean promoteVisibleInvestigation(Mob mob, Player player) {
        if (mob == null || player == null || !player.isAlive() || player.isSpectator()) return false;
        AlertLevel current = getAlertLevel(mob);
        if (current == AlertLevel.WARNED || current == AlertLevel.ENGAGED) return true;
        if (current != AlertLevel.INVESTIGATE) return false;

        double distance = mob.distanceTo(player);
        if (distance <= 10.0D) {
            engage(mob, player, 95L);
            return true;
        }
        if (distance <= 16.0D) {
            warn(mob, player.position(), 70L, 1.02D);
        }
        return false;
    }

    private static void investigate(Mob mob, Vec3 pos, long duration, double speed) {
        markAlert(mob, AlertLevel.INVESTIGATE, pos, duration);
        mob.getLookControl().setLookAt(pos.x, pos.y, pos.z, 20.0F, 20.0F);
        mob.getNavigation().moveTo(pos.x, pos.y, pos.z, speed);
    }

    private static void warn(Mob mob, Vec3 pos, long duration, double speed) {
        markAlert(mob, AlertLevel.WARNED, pos, duration);
        mob.getLookControl().setLookAt(pos.x, pos.y, pos.z, 25.0F, 25.0F);
        mob.getNavigation().moveTo(pos.x, pos.y, pos.z, speed);
    }

    private static void engage(Mob mob, LivingEntity target, long duration) {
        markAlert(mob, AlertLevel.ENGAGED, target.position(), duration);
        mob.setTarget(target);
    }

    private static void markAlert(Mob mob, AlertLevel level, Vec3 pos, long duration) {
        long now = mob.level().getGameTime();
        mob.getPersistentData().putLong(LAST_ALERT_TICK, now);
        mob.getPersistentData().putString(ALERT_LEVEL, level.name());
        mob.getPersistentData().putLong(INVESTIGATE_END_TIME, now + Math.max(20L, duration));
        mob.getPersistentData().putDouble(INVESTIGATE_X, pos.x);
        mob.getPersistentData().putDouble(INVESTIGATE_Y, pos.y);
        mob.getPersistentData().putDouble(INVESTIGATE_Z, pos.z);
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
            return dist <= directRadius || alreadyWarned;
        }
        if (dist <= directRadius) {
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

    private static Vec3 getInvestigatePos(Mob mob) {
        if (mob == null) return Vec3.ZERO;
        var data = mob.getPersistentData();
        if (data.contains(INVESTIGATE_X) && data.contains(INVESTIGATE_Y) && data.contains(INVESTIGATE_Z)) {
            return new Vec3(
                data.getDouble(INVESTIGATE_X),
                data.getDouble(INVESTIGATE_Y),
                data.getDouble(INVESTIGATE_Z));
        }
        return mob.position();
    }

    private static void clearAlert(Mob mob) {
        if (mob == null) return;
        mob.getPersistentData().remove(ALERT_LEVEL);
        mob.getPersistentData().remove(INVESTIGATE_END_TIME);
        mob.getPersistentData().remove(INVESTIGATE_X);
        mob.getPersistentData().remove(INVESTIGATE_Y);
        mob.getPersistentData().remove(INVESTIGATE_Z);
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

    private static boolean isRogueTarget(LivingEntity entity) {
        return entity != null
            && (entity.getTags().contains("tac_rogue_spawned") || entity.getTags().contains("rogue:boss"));
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
    }
}
