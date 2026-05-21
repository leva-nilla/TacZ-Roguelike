package com.levanilla.rogue.world.generation;

import com.levanilla.rogue.world.MapGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DungeonLayoutPlanner {
    private static final int MIN_ROOM = 7;
    private static final int MAX_ROOM = 18;
    private static final int ROOM_RADIUS = 70;

    private DungeonLayoutPlanner() {}

    public static List<int[]> generateLayout(MapGenerator.LayoutPattern pattern, Random rand, boolean isBoss) {
        return switch (pattern) {
            case LINEAR  -> generateLinear(rand, isBoss);
            case RING    -> generateRing(rand, isBoss);
            case GRID    -> generateGrid(rand, isBoss);
            case BRANCH  -> generateBranch(rand, isBoss);
            case HYBRID  -> generateHybrid(rand, isBoss);
            default      -> generateScatter(rand, isBoss);
        };
    }

    private static List<int[]> generateScatter(Random rand, boolean isBoss) {
        List<int[]> rooms = new ArrayList<>();
        int roomCount = isBoss ? 8 : 20 + rand.nextInt(12);
        int radius = ROOM_RADIUS - 10;  // 60

        for (int attempt = 0; attempt < 300 && rooms.size() < roomCount; attempt++) {
            int w = MIN_ROOM + rand.nextInt(MAX_ROOM - MIN_ROOM);
            int d = MIN_ROOM + rand.nextInt(MAX_ROOM - MIN_ROOM);
            int x = rand.nextInt(radius * 2) - radius;
            int z = rand.nextInt(radius * 2) - radius;
            if (!overlaps(rooms, x, z, w, d)) {
                rooms.add(new int[]{x, z, w, d});
            }
        }
        return rooms;
    }

    private static List<int[]> generateLinear(Random rand, boolean isBoss) {
        List<int[]> rooms = new ArrayList<>();
        int x = -(ROOM_RADIUS - 15);
        int roomCount = 12 + rand.nextInt(8);
        for (int i = 0; i < roomCount; i++) {
            int w = MIN_ROOM + rand.nextInt(8);
            int d = MIN_ROOM + rand.nextInt(8);
            int z = (rand.nextBoolean() ? 1 : -1) * (10 + rand.nextInt(15));
            if (Math.abs(x) + w < ROOM_RADIUS) {
                rooms.add(new int[]{x, z, w, d});
                if (rand.nextFloat() < 0.5f && Math.abs(x) + w < ROOM_RADIUS) {
                    rooms.add(new int[]{x - w / 2, -z, w, d});
                }
            }
            x += w + 5 + rand.nextInt(8);
        }
        return rooms;
    }

    private static List<int[]> generateRing(Random rand, boolean isBoss) {
        List<int[]> rooms = new ArrayList<>();
        int centerSize = isBoss ? 24 : 16;
        int centerHalf = centerSize / 2;
        rooms.add(new int[]{-centerHalf, -centerHalf, centerSize, centerSize});
        int count = isBoss ? 8 : (10 + rand.nextInt(6));
        double angleStep = 2.0 * Math.PI / count;
        int radius = 35 + rand.nextInt(15);
        for (int i = 0; i < count; i++) {
            double angle = angleStep * i;
            int x = (int) (Math.cos(angle) * radius);
            int z = (int) (Math.sin(angle) * radius);
            int w = MIN_ROOM + rand.nextInt(8);
            int d = MIN_ROOM + rand.nextInt(8);
            rooms.add(new int[]{x - w / 2, z - d / 2, w, d});
        }
        return rooms;
    }

    private static List<int[]> generateGrid(Random rand, boolean isBoss) {
        List<int[]> rooms = new ArrayList<>();
        int gridN = 4 + rand.nextInt(2);
        int spacing = 20 + rand.nextInt(6);
        int offsetX = -(gridN / 2) * spacing;
        int offsetZ = -(gridN / 2) * spacing;
        for (int gx = 0; gx < gridN; gx++) {
            for (int gz = 0; gz < gridN; gz++) {
                if (rand.nextFloat() < 0.2f && !(gx == gridN / 2 && gz == gridN / 2)) continue;
                int w = MIN_ROOM + rand.nextInt(6);
                int d = MIN_ROOM + rand.nextInt(6);
                int x = offsetX + gx * spacing;
                int z = offsetZ + gz * spacing;
                if (Math.abs(x) < ROOM_RADIUS && Math.abs(z) < ROOM_RADIUS) {
                    rooms.add(new int[]{x, z, w, d});
                }
            }
        }
        return rooms;
    }

    private static List<int[]> generateBranch(Random rand, boolean isBoss) {
        List<int[]> rooms = new ArrayList<>();
        generateBranchRecursive(rooms, rand, 0, 0, 0, 4, 0);
        return rooms;
    }

    private static void generateBranchRecursive(List<int[]> rooms, Random rand,
                                                  int x, int z, int depth, int maxDepth, int direction) {
        if (depth > maxDepth || rooms.size() >= 30) return;
        int w = MIN_ROOM + rand.nextInt(8);
        int d = MIN_ROOM + rand.nextInt(8);
        if (x < -ROOM_RADIUS || z < -ROOM_RADIUS || x + w > ROOM_RADIUS || z + d > ROOM_RADIUS) return;
        rooms.add(new int[]{x, z, w, d});
        int branches = 2 + rand.nextInt(2);
        for (int b = 0; b < branches; b++) {
            int newDir = rand.nextInt(4);
            int dist = 18 + rand.nextInt(12);
            int nx = x + (newDir == 0 ? dist : newDir == 1 ? -dist : 0);
            int nz = z + (newDir == 2 ? dist : newDir == 3 ? -dist : 0);
            generateBranchRecursive(rooms, rand, nx, nz, depth + 1, maxDepth, newDir);
        }
    }

    private static List<int[]> generateHybrid(Random rand, boolean isBoss) {
        List<int[]> rooms = new ArrayList<>();
        List<int[]> linear = generateLinear(rand, false);
        for (int[] r : linear) if (r[0] < 5) rooms.add(r);
        List<int[]> grid = generateGrid(rand, false);
        for (int[] r : grid) if (r[0] >= 0) rooms.add(r);
        return rooms;
    }

    private static boolean overlaps(List<int[]> rooms, int x, int z, int w, int d) {
        for (int[] room : rooms) {
            if (x < room[0] + room[2] + 3 && x + w + 3 > room[0] &&
                z < room[1] + room[3] + 3 && z + d + 3 > room[1]) {
                return true;
            }
        }
        return false;
    }
}
