package com.levanilla.rogue.world.goal;

import com.levanilla.rogue.core.service.RogueMobAlertService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

public class RogueMobEngagedTargetMonitorGoal extends Goal {
    private static final int UPDATE_INTERVAL_TICKS = 4;

    private final Mob mob;
    private int nextUpdateTick;

    public RogueMobEngagedTargetMonitorGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null
            && RogueMobAlertService.getAlertLevel(mob) == RogueMobAlertService.AlertLevel.ENGAGED;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        nextUpdateTick = 0;
    }

    @Override
    public void tick() {
        if (nextUpdateTick-- > 0) return;
        nextUpdateTick = UPDATE_INTERVAL_TICKS;

        LivingEntity target = mob.getTarget();
        if (target == null) return;
        boolean hasLos = mob.hasLineOfSight(target);
        RogueMobAlertService.monitorEngagedTarget(mob, target, hasLos);
    }
}
