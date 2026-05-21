package com.levanilla.rogue.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponRarityTest {

    @Test
    void rarityMetadataScalesWithStars() {
        assertEquals("[C]", WeaponRarity.Rarity.COMMON.getStarsDisplay());
        assertEquals("[L]", WeaponRarity.Rarity.LEGENDARY.getStarsDisplay());
        assertTrue(WeaponRarity.Rarity.LEGENDARY.damageMult > WeaponRarity.Rarity.COMMON.damageMult);
        assertTrue(WeaponRarity.Rarity.LEGENDARY.reloadMult < WeaponRarity.Rarity.COMMON.reloadMult);
        assertTrue(WeaponRarity.Rarity.LEGENDARY.fireRateMult > WeaponRarity.Rarity.COMMON.fireRateMult);
    }

    @Test
    void atLeastRaisesOnlyWhenBelowMinimum() {
        assertSame(WeaponRarity.Rarity.RARE, WeaponRarity.atLeast(WeaponRarity.Rarity.COMMON, WeaponRarity.Rarity.RARE));
        assertSame(WeaponRarity.Rarity.EPIC, WeaponRarity.atLeast(WeaponRarity.Rarity.EPIC, WeaponRarity.Rarity.RARE));
    }

    @Test
    void shopRarityIsDeterministicAndFloorGated() {
        String itemId = "tacz:glock_17";

        assertSame(WeaponRarity.rollShopRarity(1, itemId), WeaponRarity.rollShopRarity(1, itemId));
        assertTrue(WeaponRarity.rollShopRarity(1, itemId).stars <= WeaponRarity.Rarity.UNCOMMON.stars);
        assertTrue(WeaponRarity.rollShopRarity(9, itemId).stars <= WeaponRarity.Rarity.UNCOMMON.stars);
        assertTrue(WeaponRarity.rollShopRarity(19, itemId).stars <= WeaponRarity.Rarity.RARE.stars);
        assertTrue(WeaponRarity.rollShopRarity(50, itemId).stars <= WeaponRarity.Rarity.EPIC.stars);
    }

    @Test
    void fireRatePerkBonusIsZeroWithoutHolder() {
        assertEquals(0.0f, WeaponRarity.getFireRatePerkBonusPercent(null), 0.0001f);
    }

    @Test
    void softcapPercentHandlesLowerBoundAndCompression() throws Exception {
        Method method = WeaponRarity.class.getDeclaredMethod(
            "softcapPercent",
            float.class,
            float.class,
            float.class,
            float.class
        );
        method.setAccessible(true);

        assertEquals(0.0f, (float) method.invoke(null, -10.0f, 80.0f, 0.2f, 125.0f), 0.0001f);
        assertEquals(50.0f, (float) method.invoke(null, 50.0f, 80.0f, 0.2f, 125.0f), 0.0001f);
        assertEquals(100.0f, (float) method.invoke(null, 180.0f, 80.0f, 0.2f, 125.0f), 0.0001f);
        assertEquals(125.0f, (float) method.invoke(null, 1000.0f, 80.0f, 0.2f, 125.0f), 0.0001f);
    }
}
