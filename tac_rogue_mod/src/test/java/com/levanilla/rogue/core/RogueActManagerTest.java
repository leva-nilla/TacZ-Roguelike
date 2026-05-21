package com.levanilla.rogue.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RogueActManagerTest {

    @Test
    void actAndFloorInActClampToFirstFloor() {
        assertEquals(1, RogueActManager.getAct(0));
        assertEquals(1, RogueActManager.getFloorInAct(0));
    }

    @Test
    void actAdvancesEveryFiveFloors() {
        assertEquals(1, RogueActManager.getAct(5));
        assertEquals(2, RogueActManager.getAct(6));
        assertEquals(3, RogueActManager.getAct(11));
    }

    @Test
    void phasesFollowFiveFloorRhythm() {
        assertSame(RogueActManager.FloorPhase.SCOUT, RogueActManager.getPhase(1));
        assertSame(RogueActManager.FloorPhase.SUPPLY, RogueActManager.getPhase(2));
        assertSame(RogueActManager.FloorPhase.ELITE, RogueActManager.getPhase(3));
        assertSame(RogueActManager.FloorPhase.DANGER, RogueActManager.getPhase(4));
        assertSame(RogueActManager.FloorPhase.BOSS, RogueActManager.getPhase(5));
        assertSame(RogueActManager.FloorPhase.SCOUT, RogueActManager.getPhase(6));
    }

    @Test
    void preferredArchetypeMatchesPhase() {
        assertSame(RogueActManager.BuildArchetype.ASSASSIN, RogueActManager.getPreferredArchetype(1));
        assertSame(RogueActManager.BuildArchetype.SCAVENGER, RogueActManager.getPreferredArchetype(2));
        assertSame(RogueActManager.BuildArchetype.ASSAULT, RogueActManager.getPreferredArchetype(3));
        assertSame(RogueActManager.BuildArchetype.SURVIVOR, RogueActManager.getPreferredArchetype(4));
        assertSame(RogueActManager.BuildArchetype.BOSS_BREAKER, RogueActManager.getPreferredArchetype(5));
    }

    @Test
    void preferredPerkCategoriesContainExpectedRoleAnchors() {
        List<PerkDefinition.Category> bossPerks = RogueActManager.getPreferredPerkCategories(5);
        List<PerkDefinition.Category> supplyPerks = RogueActManager.getPreferredPerkCategories(2);

        assertTrue(bossPerks.contains(PerkDefinition.Category.DAMAGE));
        assertTrue(bossPerks.contains(PerkDefinition.Category.VAMPIRE));
        assertTrue(supplyPerks.contains(PerkDefinition.Category.SCAVENGER));
        assertTrue(supplyPerks.contains(PerkDefinition.Category.AMMO_EFFICIENCY));
    }

    @Test
    void phaseDirectiveIsNonEmptyForEachPhase() {
        for (int floor = 1; floor <= 5; floor++) {
            assertTrue(RogueActManager.getPhaseDirective(floor).length() > 10);
        }
    }
}
