package com.levanilla.rogue.world.generation;

public record FloorGenerationContext(
    long runSeed,
    int floor,
    long floorSeedSalt,
    int attemptIndex,
    String instanceId,
    String mode,
    String objectiveType,
    int participantCount,
    long worldSeed
) {
    private static final long MIX_A = 0x9E3779B97F4A7C15L;
    private static final long MIX_B = 0xC2B2AE3D27D4EB4FL;
    private static final long MIX_C = 0x165667B19E3779F9L;

    public long layoutSeed() {
        return seed("layout", 0x4C41594F55544CL);
    }

    public long roomSeed() {
        return seed("rooms", 0x524F4F4D53L);
    }

    public long corridorSeed() {
        return seed("corridors", 0x434F525249444F52L);
    }

    public long encounterSeed() {
        return seed("encounters", 0x454E434F554E5445L);
    }

    public long lootSeed() {
        return seed("loot", 0x4C4F4F54L);
    }

    public long decorationSeed() {
        return seed("decor", 0x4445434F52L);
    }

    public long rerollSeed(int rerollIndex) {
        return layoutSeed() ^ ((long) Math.max(0, rerollIndex) * MIX_B);
    }

    private long seed(String stream, long salt) {
        long value = runSeed
            ^ Long.rotateLeft(floorSeedSalt, 17)
            ^ ((long) floor * MIX_A)
            ^ ((long) Math.max(0, attemptIndex) * MIX_B)
            ^ ((long) Math.max(1, participantCount) * MIX_C)
            ^ Long.rotateLeft(worldSeed, 29)
            ^ (long) safe(instanceId).hashCode() * 0xD6E8FEB86659FD93L
            ^ (long) safe(mode).hashCode() * 0xA0761D6478BD642FL
            ^ (long) safe(objectiveType).hashCode() * 0xE1BB7A44D6B1A27BL
            ^ (long) stream.hashCode() * 0xE7037ED1A0B428DBL
            ^ salt;
        return mix64(value == 0L ? 0xD1B54A32D192ED03L : value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
