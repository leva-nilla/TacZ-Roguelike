package com.levanilla.rogue.world.generation.plan;

public record DungeonCorridor(
    int fromRoomId,
    int toRoomId,
    CorridorVariant variant,
    int width
) {
}
