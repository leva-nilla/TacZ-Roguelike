package com.levanilla.rogue.core.service;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SupplyCacheContentsTest {
    @Test
    void playersAtTheSameChestKeepSeparateRemainingLoot() {
        var floor = new SupplyCacheService.Contents<List<String>>();
        BlockPos pos = new BlockPos(1, 100, 2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<String> firstLoot = floor.getOrClaim(pos, first, () -> new ArrayList<>(List.of("ammo", "bandage")));
        firstLoot.remove("ammo");
        List<String> secondLoot = floor.getOrClaim(pos, second, () -> new ArrayList<>(List.of("medkit")));

        assertNotSame(firstLoot, secondLoot);
        assertEquals(List.of("medkit"), secondLoot);
        assertEquals(List.of("bandage"), floor.getOrClaim(pos, first, () -> fail("rerolled")));
    }

    @Test
    void emptiedChestDoesNotRerollButNewGenerationHasNoPrivateContents() {
        var floor = new SupplyCacheService.Contents<List<String>>();
        BlockPos pos = new BlockPos(1, 100, 2);
        UUID player = UUID.randomUUID();
        floor.getOrClaim(pos, player, () -> new ArrayList<>(List.of("ammo"))).clear();

        assertTrue(floor.getOrClaim(pos, player, () -> fail("rerolled empty chest")).isEmpty());
        assertEquals(List.of("bandage"), floor.getOrClaim(pos.east(), player, () -> List.of("bandage")));
        assertNull(new SupplyCacheService.Contents<List<String>>().getOrClaim(pos, player, () -> null));
    }
}
