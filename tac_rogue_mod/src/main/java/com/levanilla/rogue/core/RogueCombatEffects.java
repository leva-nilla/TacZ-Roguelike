package com.levanilla.rogue.core;

import net.minecraft.server.level.ServerPlayer;

public final class RogueCombatEffects {
    private RogueCombatEffects() {}

    private static final String ADRENALINE_DAMAGE_UNTIL = "TacRogueAdrenalineDamageUntil";
    private static final float ADRENALINE_DAMAGE_MULT = 1.30f;
    public static final int ADRENALINE_DURATION_TICKS = 400;

    public static void activateAdrenaline(ServerPlayer player) {
        if (player == null) return;
        player.getPersistentData().putLong(
            ADRENALINE_DAMAGE_UNTIL,
            player.level().getGameTime() + ADRENALINE_DURATION_TICKS);
    }

    public static boolean hasAdrenalineDamage(ServerPlayer player) {
        if (player == null) return false;
        long until = player.getPersistentData().getLong(ADRENALINE_DAMAGE_UNTIL);
        return until > player.level().getGameTime();
    }

    public static float applyAdrenalineDamage(ServerPlayer player, float damage) {
        return hasAdrenalineDamage(player) ? damage * ADRENALINE_DAMAGE_MULT : damage;
    }
}
