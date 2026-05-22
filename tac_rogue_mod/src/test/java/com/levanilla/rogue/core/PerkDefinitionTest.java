package com.levanilla.rogue.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerkDefinitionTest {

    @Test
    void constructorClampsLevelToSupportedRange() {
        assertEquals(1, new PerkDefinition(PerkDefinition.Category.VITALITY, PerkDefinition.Modifier.NONE, -5).level);
        assertEquals(10, new PerkDefinition(PerkDefinition.Category.VITALITY, PerkDefinition.Modifier.NONE, 99).level);
    }

    @Test
    void effectUsesLevelBaseAndModifierMultiplier() {
        PerkDefinition perk = new PerkDefinition(
            PerkDefinition.Category.DAMAGE,
            PerkDefinition.Modifier.TITANIC,
            4
        );

        assertEquals(69.80625f, perk.calculateEffect(), 0.0001f);
    }

    @Test
    void displayNameOmitsEmptyModifierPrefix() {
        assertEquals(
            "Vitality Lv.3",
            new PerkDefinition(PerkDefinition.Category.VITALITY, PerkDefinition.Modifier.NONE, 3).getDisplayName()
        );
        assertEquals(
            "Cursed Damage Lv.2",
            new PerkDefinition(PerkDefinition.Category.DAMAGE, PerkDefinition.Modifier.CURSED, 2).getDisplayName()
        );
    }

    @Test
    void tagRoundTripRestoresDefinition() {
        PerkDefinition original = new PerkDefinition(
            PerkDefinition.Category.FIRE_RATE,
            PerkDefinition.Modifier.OVERCLOCKED,
            7
        );

        PerkDefinition restored = PerkDefinition.fromTag(original.toTag());

        assertSame(original.category, restored.category);
        assertSame(original.modifier, restored.modifier);
        assertEquals(original.level, restored.level);
    }

    @Test
    void invalidTagFallsBackToVitalityLevelOne() {
        PerkDefinition fallback = PerkDefinition.fromTag("not-a-perk");

        assertSame(PerkDefinition.Category.VITALITY, fallback.category);
        assertSame(PerkDefinition.Modifier.NONE, fallback.modifier);
        assertEquals(1, fallback.level);
    }

    @Test
    void cursedPenaltyTargetIsDeterministicForSameInputs() {
        UUID playerId = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertSame(
            PerkDefinition.resolveCursedPenaltyTarget(playerId, "perk:DAMAGE:CURSED:5"),
            PerkDefinition.resolveCursedPenaltyTarget(playerId, "perk:DAMAGE:CURSED:5")
        );
    }

    @Test
    void rareModifierStartsAtTierThree() {
        assertTrue(PerkDefinition.Modifier.REINFORCED.isRare());
    }
}
