package com.levanilla.rogue.world.goal;

import com.levanilla.rogue.core.service.RogueMobAlertService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RogueMobTacticalAlertGoal extends Goal {
    private static final double ARRIVAL_DISTANCE_SQR = 2.25D;
    private static final int UPDATE_INTERVAL_TICKS = 4;

    private final Mob mob;
    private int nextUpdateTick;

    public RogueMobTacticalAlertGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return RogueMobAlertService.shouldTacticalGoalRun(mob);
    }

    @Override
    public boolean canContinueToUse() {
        return RogueMobAlertService.shouldTacticalGoalRun(mob);
    }

    @Override
    public void start() {
        nextUpdateTick = 0;
    }

    @Override
    public void tick() {
        if (nextUpdateTick-- > 0) return;
        nextUpdateTick = UPDATE_INTERVAL_TICKS;

        RogueMobAlertService.AlertLevel level = RogueMobAlertService.getAlertLevel(mob);
        if (level == RogueMobAlertService.AlertLevel.NONE) return;

        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive() && !target.isSpectator()) {
            if (mob.hasLineOfSight(target)) {
                RogueMobAlertService.rememberSeenTarget(mob, target);
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                return;
            }
            moveToMemory(level);
            return;
        }

        if (level == RogueMobAlertService.AlertLevel.ENGAGED) {
            RogueMobAlertService.downgradeLostTarget(mob);
        }
        moveToMemory(RogueMobAlertService.getAlertLevel(mob));
    }

    private void moveToMemory(RogueMobAlertService.AlertLevel level) {
        if (!RogueMobAlertService.hasAlertMemory(mob)) return;
        Vec3 pos = RogueMobAlertService.getInvestigatePos(mob);
        mob.getLookControl().setLookAt(pos.x, pos.y, pos.z, 25.0F, 25.0F);
        if (mob.distanceToSqr(pos) > ARRIVAL_DISTANCE_SQR) {
            moveTowardInvestigation(pos, RogueMobAlertService.tacticalSpeed(mob));
        } else if (level == RogueMobAlertService.AlertLevel.INVESTIGATE) {
            mob.getNavigation().stop();
        }
    }

    private void moveTowardInvestigation(Vec3 pos, double speed) {
        if (mob.getNavigation().moveTo(pos.x, pos.y, pos.z, speed)) return;

        BlockPos center = BlockPos.containing(pos);
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos candidate = center.offset(dx, 0, dz);
                    if (!isStandable(candidate)) continue;
                    if (mob.getNavigation().moveTo(
                        candidate.getX() + 0.5D,
                        candidate.getY(),
                        candidate.getZ() + 0.5D,
                        speed)) {
                        return;
                    }
                }
            }
        }
    }

    private boolean isStandable(BlockPos pos) {
        return !mob.level().getBlockState(pos.below()).isAir()
            && mob.level().getBlockState(pos).isAir()
            && mob.level().getBlockState(pos.above()).isAir();
    }
}
