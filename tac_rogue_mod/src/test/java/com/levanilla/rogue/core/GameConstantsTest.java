package com.levanilla.rogue.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameConstantsTest {

    @Test
    void economyConstantsStayWithinExpectedBounds() {
        assertAll(
            () -> assertEquals(100, GameConstants.INITIAL_GOLD),
            () -> assertTrue(GameConstants.KILL_REWARD_BASE > 0),
            () -> assertTrue(GameConstants.KILL_REWARD_PER_FLOOR > 0),
            () -> assertTrue(GameConstants.KILL_REWARD_MAX_CAP >= GameConstants.KILL_REWARD_BASE),
            () -> assertTrue(GameConstants.DEATH_PENALTY_RATE >= 0.0f),
            () -> assertTrue(GameConstants.DEATH_PENALTY_RATE <= 1.0f)
        );
    }

    @Test
    void slotRangesDoNotOverlap() {
        assertAll(
            () -> assertEquals(0, GameConstants.SLOT_GUN_START),
            () -> assertTrue(GameConstants.SLOT_GUN_END < GameConstants.SLOT_MELEE),
            () -> assertTrue(GameConstants.SLOT_MELEE < GameConstants.SLOT_ITEM_START),
            () -> assertTrue(GameConstants.SLOT_ITEM_END < GameConstants.SLOT_AMMO_START),
            () -> assertTrue(GameConstants.SLOT_AMMO_START <= GameConstants.SLOT_AMMO_END)
        );
    }

    @Test
    void combatCapsRemainProtective() {
        assertAll(
            () -> assertTrue(GameConstants.AMMO_SAVE_MAX_CHANCE < 1.0f),
            () -> assertTrue(GameConstants.SPECIAL_RESISTANCE_MAX < 1.0f),
            () -> assertTrue(GameConstants.MIN_EFFECTIVE_RELOAD_MULT > 0.0f),
            () -> assertTrue(GameConstants.MAX_EFFECTIVE_FIRE_RATE_RPM > 0),
            () -> assertTrue(GameConstants.FIRE_RATE_HARD_CAP >= GameConstants.FIRE_RATE_SOFTCAP_START)
        );
    }
}
