package com.levanilla.rogue.core;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

public final class CombatPostureHelper {
    private CombatPostureHelper() {}

    public static boolean isProne(LivingEntity entity) {
        return entity != null
            && entity.getPose() == Pose.SWIMMING
            && !entity.isSwimming();
    }
}
