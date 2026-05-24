package com.levanilla.rogue.world.goal;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;

public final class RogueMobGoalUtils {
    private RogueMobGoalUtils() {}

    public static void removePassivePlayerLookGoals(Mob mob) {
        if (mob == null) return;
        mob.goalSelector.getAvailableGoals().removeIf(wrapped ->
            wrapped.getGoal() instanceof LookAtPlayerGoal);
    }
}
