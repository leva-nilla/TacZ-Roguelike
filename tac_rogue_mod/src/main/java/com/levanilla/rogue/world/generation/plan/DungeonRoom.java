package com.levanilla.rogue.world.generation.plan;

public record DungeonRoom(
    int id,
    int x,
    int z,
    int width,
    int depth,
    RoomRole role
) {
    public int centerX() {
        return x + width / 2;
    }

    public int centerZ() {
        return z + depth / 2;
    }

    public int[] toLegacyArray() {
        return new int[] { x, z, width, depth };
    }
}
