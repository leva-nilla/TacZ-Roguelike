package com.levanilla.rogue.world.generation;

import com.levanilla.rogue.world.generation.plan.CorridorVariant;
import com.levanilla.rogue.world.generation.plan.DungeonCorridor;
import com.levanilla.rogue.world.generation.plan.DungeonPlan;
import com.levanilla.rogue.world.generation.plan.DungeonRoom;
import com.levanilla.rogue.world.generation.plan.MacroArchetype;
import com.levanilla.rogue.world.generation.plan.RoomRole;
import com.levanilla.rogue.world.ThemeManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Data-first dungeon layout planner.
 *
 * <p>The block stamping pipeline still lives in MapGenerator. This class only
 * decides rooms, macro shape, and graph edges so layout variety can evolve
 * without touching spawn/reward compatibility code every time.</p>
 */
public final class DungeonPlanGenerator {
    private static final int MIN_ROOM = 7;
    private static final int MAX_ROOM = 18;
    private static final int ROOM_RADIUS = 70;
    private static final int MAX_REROLLS = 5;
    private static final int RECENT_HASH_LIMIT = 6;

    private static final Map<String, Deque<Long>> RECENT_LAYOUT_HASHES = new HashMap<>();

    private DungeonPlanGenerator() {}

    public static DungeonPlan generate(FloorGenerationContext context, boolean bossFloor) {
        return generate(context, bossFloor, ThemeManager.ThemeGenerationStyle.DEFAULT);
    }

    public static DungeonPlan generate(FloorGenerationContext context, boolean bossFloor, ThemeManager.ThemeInstance theme) {
        return generate(context, bossFloor, ThemeManager.generationStyle(theme));
    }

    public static DungeonPlan generate(FloorGenerationContext context, boolean bossFloor, ThemeManager.ThemeGenerationStyle style) {
        DungeonPlan last = null;
        for (int reroll = 0; reroll < MAX_REROLLS; reroll++) {
            Random rand = new Random(context.rerollSeed(reroll));
            MacroArchetype archetype = bossFloor ? MacroArchetype.BOSS_RING : pickArchetype(rand, context.floor(), style);
            DungeonPlan plan = buildPlan(context, archetype, rand, bossFloor);
            DungeonPlanValidator.ValidationResult result = DungeonPlanValidator.validate(plan, bossFloor);
            if (result.valid() && rememberIfFresh(context, plan)) {
                return plan;
            }
            last = plan;
        }

        DungeonPlan fallback = fallbackPlan(context, bossFloor);
        DungeonPlanValidator.ValidationResult result = DungeonPlanValidator.validate(fallback, bossFloor);
        if (result.valid()) {
            rememberIfFresh(context, fallback);
            return fallback;
        }
        return last != null ? last : fallback;
    }

    private static MacroArchetype pickArchetype(Random rand, int floor, ThemeManager.ThemeGenerationStyle style) {
        MacroArchetype themed = pickThemedArchetype(rand, style);
        if (themed != null && !(floor < 3 && (themed == MacroArchetype.DENSE_CLUSTER || themed == MacroArchetype.LONG_CORRIDOR_COMBAT))) {
            return themed;
        }
        List<MacroArchetype> candidates = new ArrayList<>(EnumSet.allOf(MacroArchetype.class));
        candidates.remove(MacroArchetype.BOSS_RING);
        if (floor < 3) {
            candidates.remove(MacroArchetype.DENSE_CLUSTER);
            candidates.remove(MacroArchetype.LONG_CORRIDOR_COMBAT);
        }
        return candidates.get(rand.nextInt(candidates.size()));
    }

    private static MacroArchetype pickThemedArchetype(Random rand, ThemeManager.ThemeGenerationStyle style) {
        return switch (style == null ? ThemeManager.ThemeGenerationStyle.DEFAULT : style) {
            case ORGANIC_CAVE, BROKEN_RUINS -> rand.nextBoolean()
                ? MacroArchetype.DENSE_CLUSTER
                : MacroArchetype.LOOPED_COMPOUND;
            case FLOODED_LOW -> rand.nextBoolean()
                ? MacroArchetype.LOOPED_COMPOUND
                : MacroArchetype.TWO_LANES;
            case SHIPWRECK -> MacroArchetype.TWO_LANES;
            case PIPELINE, SUBWAY, SEWER -> MacroArchetype.LONG_CORRIDOR_COMBAT;
            case LAB_COMPLEX -> rand.nextBoolean()
                ? MacroArchetype.LOOPED_COMPOUND
                : MacroArchetype.HUB_AND_SPOKES;
            case MILITARY_COMPOUND -> rand.nextBoolean()
                ? MacroArchetype.TWO_LANES
                : MacroArchetype.HUB_AND_SPOKES;
            case NETHER_FORTRESS -> rand.nextBoolean()
                ? MacroArchetype.LINEAR_BRANCHING
                : MacroArchetype.LOOPED_COMPOUND;
            case URBAN_INTERIOR -> rand.nextBoolean()
                ? MacroArchetype.LOOPED_COMPOUND
                : MacroArchetype.DENSE_CLUSTER;
            case HANGAR, RADAR_OPEN, TEMPLE_AXIS, ROOFTOP_OPEN -> rand.nextBoolean()
                ? MacroArchetype.HUB_AND_SPOKES
                : MacroArchetype.LOOPED_COMPOUND;
            case VOID_ALIEN -> rand.nextBoolean()
                ? MacroArchetype.LOOPED_COMPOUND
                : MacroArchetype.HUB_AND_SPOKES;
            default -> null;
        };
    }

    private static DungeonPlan buildPlan(FloorGenerationContext context, MacroArchetype archetype,
                                         Random rand, boolean bossFloor) {
        PlanBuilder builder = new PlanBuilder(context, archetype, rand);
        switch (archetype) {
            case BOSS_RING -> buildBossRing(builder);
            case HUB_AND_SPOKES -> buildHubAndSpokes(builder);
            case TWO_LANES -> buildTwoLanes(builder);
            case LONG_CORRIDOR_COMBAT -> buildLongCorridor(builder);
            case DENSE_CLUSTER -> buildDenseCluster(builder);
            case LOOPED_COMPOUND -> buildLoopedCompound(builder);
            case LINEAR_BRANCHING -> buildLinearBranching(builder);
        }
        return builder.toPlan(bossFloor ? 1 : 0);
    }

    private static void buildBossRing(PlanBuilder builder) {
        builder.addFixed(-12, -12, 24, 24, RoomRole.BOSS_ARENA);
        builder.addFixed(-7, 46, 14, 12, RoomRole.BOSS_ENTRY);
        int ring = 42 + builder.rand.nextInt(7);
        int count = 7 + builder.rand.nextInt(3);
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D * (i + 0.5D) / count) + builder.rand.nextDouble() * 0.16D;
            int w = builder.roomSize(9, 15);
            int d = builder.roomSize(9, 15);
            int x = (int) Math.round(Math.cos(angle) * ring) - w / 2;
            int z = (int) Math.round(Math.sin(angle) * ring) - d / 2;
            RoomRole role = i % 3 == 0 ? RoomRole.ELITE : (i % 3 == 1 ? RoomRole.COVER_DENSE : RoomRole.COMBAT_SMALL);
            builder.addNear(x, z, w, d, role);
        }
        builder.connect(1, 0, CorridorVariant.WIDE_COVER, 5);
        builder.connectRingFrom(1, CorridorVariant.LOOP_CONNECTOR, 5);
    }

    private static void buildHubAndSpokes(PlanBuilder builder) {
        builder.addFixed(-8, -8, 16, 16, RoomRole.START);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int branch = 0; branch < dirs.length; branch++) {
            int prev = 0;
            int len = 2 + builder.rand.nextInt(2);
            for (int step = 1; step <= len; step++) {
                int w = builder.roomSize(9, 16);
                int d = builder.roomSize(9, 16);
                int x = dirs[branch][0] * (18 + step * 17) - w / 2 + builder.rand.nextInt(7) - 3;
                int z = dirs[branch][1] * (18 + step * 17) - d / 2 + builder.rand.nextInt(7) - 3;
                int id = builder.addNear(x, z, w, d, builder.roleFor(step + branch));
                if (id >= 0) {
                    builder.connect(prev, id, builder.variant(step), step == 1 ? 4 : 3);
                    prev = id;
                }
            }
        }
        builder.addOptionalLoops(4);
    }

    private static void buildTwoLanes(PlanBuilder builder) {
        int lastTop = builder.addFixed(-62, -22, 11, 11, RoomRole.START);
        int lastBottom = builder.addFixed(-58, 18, 11, 11, RoomRole.COVER_DENSE);
        int columns = 5 + builder.rand.nextInt(2);
        for (int i = 1; i <= columns; i++) {
            int baseX = -62 + i * 22;
            int top = builder.addNear(baseX, -24 + builder.rand.nextInt(7), builder.roomSize(9, 15),
                builder.roomSize(8, 14), builder.roleFor(i));
            int bottom = builder.addNear(baseX + builder.rand.nextInt(7) - 3, 18 + builder.rand.nextInt(7),
                builder.roomSize(9, 15), builder.roomSize(8, 14), builder.roleFor(i + 2));
            if (top >= 0) {
                builder.connect(lastTop, top, CorridorVariant.STRAIGHT, 3);
                lastTop = top;
            }
            if (bottom >= 0) {
                builder.connect(lastBottom, bottom, CorridorVariant.BROKEN_ALCOVE, 3);
                lastBottom = bottom;
            }
            if (top >= 0 && bottom >= 0 && builder.rand.nextFloat() < 0.55F) {
                builder.connect(top, bottom, CorridorVariant.LOOP_CONNECTOR, 3);
            }
        }
        builder.addOptionalLoops(3);
    }

    private static void buildLongCorridor(PlanBuilder builder) {
        int prev = builder.addFixed(-62, -6, 12, 12, RoomRole.START);
        int count = 8 + builder.rand.nextInt(4);
        for (int i = 1; i < count; i++) {
            int w = builder.roomSize(8, 15);
            int d = builder.roomSize(8, 15);
            int x = -62 + i * 16 + builder.rand.nextInt(7) - 3;
            int z = (i % 2 == 0 ? -12 : 10) + builder.rand.nextInt(9) - 4;
            int id = builder.addNear(x, z, w, d, i % 3 == 0 ? RoomRole.COMBAT_LONG : builder.roleFor(i));
            if (id >= 0) {
                builder.connect(prev, id, i % 2 == 0 ? CorridorVariant.DOGLEG : CorridorVariant.NARROW_PRESSURE, 3);
                prev = id;
            }
        }
        builder.addOptionalLoops(2);
    }

    private static void buildDenseCluster(PlanBuilder builder) {
        builder.addFixed(-8, -8, 16, 16, RoomRole.START);
        int target = 14 + builder.rand.nextInt(6);
        for (int attempt = 0; attempt < 160 && builder.rooms.size() < target; attempt++) {
            int w = builder.roomSize(8, 15);
            int d = builder.roomSize(8, 15);
            int x = builder.rand.nextInt(100) - 50;
            int z = builder.rand.nextInt(100) - 50;
            int id = builder.addNear(x, z, w, d, builder.roleFor(attempt));
            if (id >= 0) {
                builder.connect(builder.nearestRoom(id), id, builder.variant(attempt), 3);
            }
        }
        builder.addOptionalLoops(6);
    }

    private static void buildLoopedCompound(PlanBuilder builder) {
        builder.addFixed(-8, -8, 16, 16, RoomRole.START);
        int count = 11 + builder.rand.nextInt(5);
        int radius = 36 + builder.rand.nextInt(14);
        int firstRing = -1;
        int prev = 0;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count + builder.rand.nextDouble() * 0.18D;
            int w = builder.roomSize(8, 15);
            int d = builder.roomSize(8, 15);
            int x = (int) Math.round(Math.cos(angle) * radius) - w / 2;
            int z = (int) Math.round(Math.sin(angle) * radius) - d / 2;
            int id = builder.addNear(x, z, w, d, builder.roleFor(i));
            if (id >= 0) {
                if (firstRing < 0) firstRing = id;
                builder.connect(prev, id, CorridorVariant.LOOP_CONNECTOR, 3);
                prev = id;
            }
        }
        if (firstRing >= 0 && prev != firstRing) builder.connect(prev, firstRing, CorridorVariant.LOOP_CONNECTOR, 3);
        builder.addOptionalLoops(3);
    }

    private static void buildLinearBranching(PlanBuilder builder) {
        int prev = builder.addFixed(-58, -8, 13, 13, RoomRole.START);
        int mainCount = 7 + builder.rand.nextInt(4);
        List<Integer> main = new ArrayList<>();
        main.add(prev);
        for (int i = 1; i < mainCount; i++) {
            int w = builder.roomSize(8, 15);
            int d = builder.roomSize(8, 15);
            int x = -58 + i * 18 + builder.rand.nextInt(7) - 3;
            int z = builder.rand.nextInt(17) - 8;
            int id = builder.addNear(x, z, w, d, i == mainCount - 1 ? RoomRole.ELITE : builder.roleFor(i));
            if (id >= 0) {
                builder.connect(prev, id, builder.variant(i), 3);
                main.add(id);
                prev = id;
                if (i > 1 && builder.rand.nextFloat() < 0.6F) {
                    int sideW = builder.roomSize(8, 14);
                    int sideD = builder.roomSize(8, 14);
                    int sideZ = z + (builder.rand.nextBoolean() ? 24 : -24);
                    int side = builder.addNear(x + builder.rand.nextInt(7) - 3, sideZ, sideW, sideD,
                        builder.rand.nextFloat() < 0.45F ? RoomRole.SIDE_REWARD : RoomRole.AMBUSH);
                    if (side >= 0) builder.connect(id, side, CorridorVariant.BROKEN_ALCOVE, 3);
                }
            }
        }
        if (main.size() > 4) builder.connect(main.get(1), main.get(main.size() - 2), CorridorVariant.LOOP_CONNECTOR, 3);
    }

    private static DungeonPlan fallbackPlan(FloorGenerationContext context, boolean bossFloor) {
        Random rand = new Random(context.layoutSeed() ^ 0x51ED5EEDL);
        PlanBuilder builder = new PlanBuilder(context, bossFloor ? MacroArchetype.BOSS_RING : MacroArchetype.LINEAR_BRANCHING, rand);
        if (bossFloor) {
            buildBossRing(builder);
            return builder.toPlan(1);
        }
        int prev = builder.addFixed(-54, -6, 12, 12, RoomRole.START);
        for (int i = 1; i < 9; i++) {
            int id = builder.addFixed(-54 + i * 14, (i % 2 == 0 ? -8 : 10), 10, 10, builder.roleFor(i));
            builder.connect(prev, id, CorridorVariant.STRAIGHT, 3);
            prev = id;
        }
        return builder.toPlan(0);
    }

    private static boolean rememberIfFresh(FloorGenerationContext context, DungeonPlan plan) {
        String key = context.runSeed() + ":" + context.floor() + ":" + context.mode();
        synchronized (RECENT_LAYOUT_HASHES) {
            Deque<Long> recent = RECENT_LAYOUT_HASHES.computeIfAbsent(key, unused -> new ArrayDeque<>());
            if (recent.contains(plan.layoutHash())) return false;
            recent.addLast(plan.layoutHash());
            while (recent.size() > RECENT_HASH_LIMIT) recent.removeFirst();
            return true;
        }
    }

    private static final class PlanBuilder {
        private final FloorGenerationContext context;
        private final MacroArchetype archetype;
        private final Random rand;
        private final List<DungeonRoom> rooms = new ArrayList<>();
        private final List<DungeonCorridor> corridors = new ArrayList<>();

        private PlanBuilder(FloorGenerationContext context, MacroArchetype archetype, Random rand) {
            this.context = context;
            this.archetype = archetype;
            this.rand = rand;
        }

        private int addFixed(int x, int z, int width, int depth, RoomRole role) {
            int id = rooms.size();
            rooms.add(new DungeonRoom(id, x, z, width, depth, role));
            return id;
        }

        private int addNear(int x, int z, int width, int depth, RoomRole role) {
            int clampedX = clamp(x, -ROOM_RADIUS + 1, ROOM_RADIUS - width - 1);
            int clampedZ = clamp(z, -ROOM_RADIUS + 1, ROOM_RADIUS - depth - 1);
            for (int attempt = 0; attempt < 24; attempt++) {
                int tx = clamp(clampedX + rand.nextInt(13) - 6, -ROOM_RADIUS + 1, ROOM_RADIUS - width - 1);
                int tz = clamp(clampedZ + rand.nextInt(13) - 6, -ROOM_RADIUS + 1, ROOM_RADIUS - depth - 1);
                if (!overlaps(tx, tz, width, depth)) {
                    return addFixed(tx, tz, width, depth, role);
                }
            }
            return -1;
        }

        private boolean overlaps(int x, int z, int width, int depth) {
            for (DungeonRoom room : rooms) {
                if (x < room.x() + room.width() + 3
                    && x + width + 3 > room.x()
                    && z < room.z() + room.depth() + 3
                    && z + depth + 3 > room.z()) {
                    return true;
                }
            }
            return false;
        }

        private int roomSize(int min, int maxInclusive) {
            int low = Math.max(MIN_ROOM, min);
            int high = Math.min(MAX_ROOM, maxInclusive);
            return low + rand.nextInt(Math.max(1, high - low + 1));
        }

        private RoomRole roleFor(int index) {
            return switch (Math.floorMod(index + rand.nextInt(3), 7)) {
                case 0 -> RoomRole.COMBAT_SMALL;
                case 1 -> RoomRole.COMBAT_LONG;
                case 2 -> RoomRole.COVER_DENSE;
                case 3 -> RoomRole.SUPPLY_RISK;
                case 4 -> RoomRole.SIDE_REWARD;
                case 5 -> RoomRole.AMBUSH;
                default -> RoomRole.ELITE;
            };
        }

        private CorridorVariant variant(int index) {
            return switch (Math.floorMod(index + rand.nextInt(2), 5)) {
                case 0 -> CorridorVariant.STRAIGHT;
                case 1 -> CorridorVariant.DOGLEG;
                case 2 -> CorridorVariant.WIDE_COVER;
                case 3 -> CorridorVariant.NARROW_PRESSURE;
                default -> CorridorVariant.BROKEN_ALCOVE;
            };
        }

        private void connectRingFrom(int startId, CorridorVariant variant, int width) {
            List<Integer> ids = new ArrayList<>();
            for (DungeonRoom room : rooms) {
                if (room.id() != 0 && room.id() != startId) ids.add(room.id());
            }
            Collections.sort(ids);
            int prev = startId;
            for (int id : ids) {
                connect(prev, id, variant, width);
                prev = id;
            }
            if (!ids.isEmpty()) connect(prev, ids.get(0), variant, width);
        }

        private void addOptionalLoops(int maxLoops) {
            int loops = Math.min(maxLoops, Math.max(0, rooms.size() / 4));
            for (int i = 0; i < loops; i++) {
                int a = rand.nextInt(rooms.size());
                int b = rand.nextInt(rooms.size());
                if (a == b || areConnected(a, b)) continue;
                connect(a, b, CorridorVariant.LOOP_CONNECTOR, 3);
            }
        }

        private int nearestRoom(int roomId) {
            DungeonRoom target = room(roomId);
            int bestId = 0;
            int bestDist = Integer.MAX_VALUE;
            for (DungeonRoom room : rooms) {
                if (room.id() == roomId) continue;
                int dx = room.centerX() - target.centerX();
                int dz = room.centerZ() - target.centerZ();
                int dist = dx * dx + dz * dz;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestId = room.id();
                }
            }
            return bestId;
        }

        private DungeonRoom room(int id) {
            for (DungeonRoom room : rooms) {
                if (room.id() == id) return room;
            }
            return rooms.get(0);
        }

        private void connect(int from, int to, CorridorVariant variant, int width) {
            if (from == to || from < 0 || to < 0 || areConnected(from, to)) return;
            corridors.add(new DungeonCorridor(from, to, variant, Math.max(3, width)));
        }

        private boolean areConnected(int a, int b) {
            for (DungeonCorridor corridor : corridors) {
                if ((corridor.fromRoomId() == a && corridor.toRoomId() == b)
                    || (corridor.fromRoomId() == b && corridor.toRoomId() == a)) {
                    return true;
                }
            }
            return false;
        }

        private DungeonPlan toPlan(int spawnRoomId) {
            long hash = computeHash();
            return new DungeonPlan(context, archetype, hash, spawnRoomId, rooms, corridors);
        }

        private long computeHash() {
            long h = 0xCBF29CE484222325L ^ archetype.ordinal();
            for (DungeonRoom room : rooms) {
                h = mix(h ^ room.id());
                h = mix(h ^ room.x() * 31L ^ room.z() * 131L ^ room.width() * 17L ^ room.depth() * 37L);
                h = mix(h ^ room.role().ordinal() * 0x9E3779B97F4A7C15L);
            }
            for (DungeonCorridor corridor : corridors) {
                h = mix(h ^ corridor.fromRoomId() * 53L ^ corridor.toRoomId() * 97L);
                h = mix(h ^ corridor.variant().ordinal() * 193L ^ corridor.width() * 389L);
            }
            return h;
        }

        private static long mix(long value) {
            value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
            value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
            return value ^ (value >>> 31);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
