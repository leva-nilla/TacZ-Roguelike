package com.levanilla.rogue.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalingEngineTest {

    @Test
    void prefixDefinitionsKeepExpectedTradeoffs() {
        assertEquals(0.8, ScalingEngine.Prefix.SWIFT.hpMult, 0.0001);
        assertEquals(1.4, ScalingEngine.Prefix.SWIFT.spdMult, 0.0001);
        assertEquals(2.0, ScalingEngine.Prefix.TANKY.hpMult, 0.0001);
        assertEquals(2.5, ScalingEngine.Prefix.BERSERK.dmgMult, 0.0001);
        assertEquals(1.0, ScalingEngine.Prefix.NORMAL.hpMult, 0.0001);
    }

    @Test
    void earlyDamageMultiplierRampsToFullValue() throws Exception {
        Method method = ScalingEngine.class.getDeclaredMethod("getEarlyDamageMult", int.class);
        method.setAccessible(true);

        assertEquals(0.65D, (double) method.invoke(null, 1), 0.0001D);
        assertEquals(0.75D, (double) method.invoke(null, 2), 0.0001D);
        assertEquals(0.85D, (double) method.invoke(null, 3), 0.0001D);
        assertEquals(0.95D, (double) method.invoke(null, 4), 0.0001D);
        assertEquals(1.0D, (double) method.invoke(null, 5), 0.0001D);
        assertEquals(0.65D, (double) method.invoke(null, -10), 0.0001D);
    }

    @Test
    void specialPrefixesHaveNonNormalIdentity() {
        for (ScalingEngine.Prefix prefix : ScalingEngine.Prefix.values()) {
            assertTrue(prefix.name.length() > 0);
            assertTrue(prefix.hpMult > 0.0D);
            assertTrue(prefix.dmgMult > 0.0D);
            assertTrue(prefix.spdMult > 0.0D);
        }
    }
}
