package com.levanilla.rogue.core;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupplyChestTrackerTest {
    @Test
    void retryDoesNotShrinkOrRefillTheOriginalFloorAllowance() {
        SupplyChestTracker tracker = new SupplyChestTracker();
        tracker.initializeMaxClaims(4, 2);
        assertEquals(0, tracker.claimNext(4, tracker.getMaxClaims(4)));

        tracker.initializeMaxClaims(4, 1);
        assertEquals(2, tracker.getMaxClaims(4));
        assertEquals(1, tracker.claimNext(4, tracker.getMaxClaims(4)));
        assertEquals(-1, tracker.claimNext(4, tracker.getMaxClaims(4)));
    }

    @Test
    void laterPartyCannotIncreaseAllowanceAndClaimsSurviveReload() {
        SupplyChestTracker tracker = new SupplyChestTracker();
        tracker.initializeMaxClaims(2, 1);
        tracker.claimNext(2, tracker.getMaxClaims(2));
        CompoundTag saved = new CompoundTag();
        tracker.saveTo(saved);

        SupplyChestTracker restored = new SupplyChestTracker();
        restored.loadFrom(saved);
        restored.initializeMaxClaims(2, 2);
        assertEquals(1, restored.getMaxClaims(2));
        assertEquals(-1, restored.claimNext(2, restored.getMaxClaims(2)));
    }
}
