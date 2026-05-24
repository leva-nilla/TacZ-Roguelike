package com.levanilla.rogue.world.goal;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.FlashlightManager;
import com.levanilla.rogue.core.service.RogueMobAlertService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * カスタム視界ゴール — ローグダンジョン専用
 *
 * 発見条件:
 *  1. 視野角60°以内にプレイヤーがいる (正面判定)
 *  2. 視線上にブロックがない (LOS判定)
 *  3. 距離が VISION_RANGE 以内
 *
 * 既に発見済み(aggro中)の場合は LOS なしでも追跡継続。
 * ダメージを受けた場合は攻撃者を即座にターゲット。
 */
public class RogueMobVisionGoal extends TargetGoal {

    /** 通常視界範囲 (ブロック) */
    private static final double VISION_RANGE = 24.0;
    /** 視野角 (度) — 正面±60° */
    private static final double FOV_COS = Math.cos(Math.toRadians(60.0));
    /** 発見後の追跡維持距離 */
    private static final double CHASE_RANGE = 32.0;
    private static final double CLOSE_AWARENESS_RANGE = 3.0;
    private static final double CLOSE_AWARENESS_MIN_DOT = 0.0D;
    private static final double INVESTIGATE_FOV_COS = Math.cos(Math.toRadians(95.0D));
    private static final double WARNED_FOV_COS = Math.cos(Math.toRadians(120.0D));

    private final Mob mob;
    private Player targetPlayer;

    public RogueMobVisionGoal(Mob mob) {
        super(mob, false, false);
        this.mob = mob;
        this.setFlags(java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (isBossMob()) {
            targetPlayer = findNearestBossPlayer();
            return targetPlayer != null;
        }

        if (RogueMobAlertService.isDecoyActive(mob)
                && RogueMobAlertService.getAlertLevel(mob) != RogueMobAlertService.AlertLevel.ENGAGED) {
            if (mob.getTarget() instanceof Player) {
                mob.setTarget(null);
            }
            return false;
        }

        // 既にターゲットがいる場合はこのゴールの canUse で再評価しない（canContinueToUse が管理）
        Player current = (mob.getTarget() instanceof Player p) ? p : null;
        if (current != null && !current.isDeadOrDying() && mob.distanceTo(current) < CHASE_RANGE) {
            if (mob.hasLineOfSight(current)) {
                RogueMobAlertService.rememberSeenTarget(mob, current);
            }
            targetPlayer = current;
            return true;
        }

        Player alertedVisible = findAlertedVisiblePlayer();
        if (alertedVisible != null) {
            targetPlayer = alertedVisible;
            return true;
        }

        // 視界内のプレイヤーを探す
        Player spotted = findPlayerInSight();
        if (spotted != null) {
            targetPlayer = spotted;
            return true;
        }
        Player lightSource = findPlayerIlluminatingMob();
        if (lightSource != null) {
            if (RogueMobAlertService.reactToFlashlight(mob, lightSource)) {
                targetPlayer = lightSource;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetPlayer == null || targetPlayer.isDeadOrDying()) return false;
        if (isBossMob()) return !targetPlayer.isSpectator();

        if (mob.distanceTo(targetPlayer) < CHASE_RANGE) {
            boolean hasLos = mob.hasLineOfSight(targetPlayer);
            return RogueMobAlertService.monitorEngagedTarget(mob, targetPlayer, hasLos);
        }
        mob.setTarget(null);
        return false;
    }

    @Override
    public void start() {
        RogueMobAlertService.engageFromVision(mob, targetPlayer);
        super.start();
    }

    @Override
    public void stop() {
        // ターゲットは維持（他ゴールに引き継がせる）
        targetPlayer = null;
        super.stop();
    }

    /**
     * 視野角 + 視線チェックでプレイヤーを探す。
     * スニーク・伏せ時は検知範囲と視野角が狭まる。
     */
    private Player findPlayerInSight() {
        net.minecraft.world.entity.Entity found = mob.level().getNearestPlayer(
            mob.getX(), mob.getY() + mob.getEyeHeight(), mob.getZ(),
            VISION_RANGE,
            e -> e instanceof Player p && !p.isSpectator() && !p.isDeadOrDying() && isInFovAndLOS(p)
        );
        return (found instanceof Player p) ? p : null;
    }

    private Player findAlertedVisiblePlayer() {
        RogueMobAlertService.AlertLevel alertLevel = RogueMobAlertService.getAlertLevel(mob);
        if (alertLevel.ordinal() < RogueMobAlertService.AlertLevel.INVESTIGATE.ordinal()) return null;

        Player nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : mob.level().players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            double distance = mob.distanceTo(player);
            if (distance > CHASE_RANGE || !mob.hasLineOfSight(player)) continue;
            if (!isInAlertFov(player, alertLevel)) continue;
            if (!RogueMobAlertService.promoteVisibleInvestigation(mob, player)) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private boolean isInAlertFov(Player player, RogueMobAlertService.AlertLevel alertLevel) {
        Vec3 mobDir = bodyForward();
        Vec3 toPlayer = player.position()
            .add(0, player.getEyeHeight() / 2.0, 0)
            .subtract(mob.position().add(0, mob.getEyeHeight(), 0))
            .normalize();
        double dot = mobDir.dot(toPlayer);
        double required = alertLevel == RogueMobAlertService.AlertLevel.WARNED
            ? WARNED_FOV_COS
            : INVESTIGATE_FOV_COS;
        return dot >= required;
    }

    private boolean isBossMob() {
        return mob.getTags().contains("rogue:boss");
    }

    private Player findNearestBossPlayer() {
        if (mob.level() instanceof ServerLevel serverLevel) {
            ServerPlayer owner = RunManager.findPlayerForDungeonPosition(serverLevel, mob.getX(), mob.getZ(), 300.0D);
            if (owner != null && owner.isAlive() && !owner.isSpectator()) {
                return owner;
            }
        }

        Player nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : mob.level().players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            double distance = mob.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    /**
     * 視野角内かつLOSが通っているかを判定。
     * プレイヤーの姿勢によって検知範囲・FOVが変動する。
     */
    private boolean isInFovAndLOS(Player player) {
        // プレイヤーの姿勢による検知修正
        boolean isCrawling = com.levanilla.rogue.core.CombatPostureHelper.isProne(player);
        boolean isSneaking = player.isShiftKeyDown();
        double rangeMultiplier = isCrawling ? 0.2 : (isSneaking ? 0.4 : 1.0);
        double fovDeg = isCrawling ? 30.0 : (isSneaking ? 40.0 : 60.0);
        double fovCos = Math.cos(Math.toRadians(fovDeg));

        // 距離チェック（姿勢による範囲縮小）
        double effectiveRange = VISION_RANGE * rangeMultiplier;
        double distance = mob.distanceTo(player);
        if (distance > effectiveRange) return false;
        if (!mob.hasLineOfSight(player)) return false;

        // Mobの視線方向ベクトル
        Vec3 mobDir = bodyForward();
        // Mobからプレイヤーへの方向ベクトル
        Vec3 toPlayer = player.position()
            .add(0, player.getEyeHeight() / 2.0, 0)
            .subtract(mob.position().add(0, mob.getEyeHeight(), 0))
            .normalize();

        // 視野角チェック
        double dot = mobDir.dot(toPlayer);
        if (dot < fovCos) {
            boolean closeButNotBehind = distance <= CLOSE_AWARENESS_RANGE
                && dot >= CLOSE_AWARENESS_MIN_DOT
                && !isSneaking
                && !isCrawling;
            if (!closeButNotBehind) return false;
        }
        RogueMobAlertService.rememberSeenTarget(mob, player);
        return true;
    }

    private Player findPlayerIlluminatingMob() {
        for (Player player : mob.level().players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (!FlashlightManager.isEnabled(player.getUUID())) continue;
            int level = player.getPersistentData().getInt("TacRogueFlashlightLevel");
            double range = 8.0D + level * 2.0D;
            if (mob.distanceTo(player) > range) continue;
            if (!player.hasLineOfSight(mob)) continue;
            if (isPlayerLookingAtMob(player, 0.90D)) return player;
        }
        return null;
    }

    private boolean isPlayerLookingAtMob(Player player, double threshold) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 toMob = mob.position()
            .add(0.0D, mob.getBbHeight() * 0.55D, 0.0D)
            .subtract(player.getEyePosition(1.0F))
            .normalize();
        return look.dot(toMob) >= threshold;
    }

    private Vec3 bodyForward() {
        double radians = Math.toRadians(mob.yBodyRot);
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians)).normalize();
    }
}
