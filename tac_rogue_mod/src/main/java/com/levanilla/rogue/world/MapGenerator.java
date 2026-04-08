package com.levanilla.rogue.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ダンジョン生成エンジン v1.1 — Grid-Fill アーキテクチャ
 *
 * 改善点:
 *  - GRID_SIZE 縮小 (240→160) で処理負荷を大幅削減
 *  - clearPreviousDungeon を軽量化: VOIDセル(AIR)はスキップ
 *  - generateGrid を中心座標対称に修正
 *  - 重複していた Phase 5.6 の VOID埋め処理を削除(sealWalls で完結)
 *  - 外周壁を実際の部屋配置範囲+マージンにバインド
 */
public class MapGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapGenerator.class);

    // ===== レイアウトパターン =====
    public enum LayoutPattern {
        SCATTER, LINEAR, RING, GRID, BRANCH, HYBRID
    }

    // ===== グリッドセルの状態 =====
    private static final int VOID   = 0;
    private static final int ROOM   = 1;
    private static final int WALL   = 2;
    private static final int CORR   = 3;

    // ===== パラメータ =====
    private static final int GRID_SIZE    = 160;          // 部屋の最大配置半径70+余裕
    private static final int GRID_CENTER  = GRID_SIZE / 2; // = 80
    private static final int MIN_ROOM     = 7;
    private static final int MAX_ROOM     = 18;
    private static final int ROOM_HEIGHT  = 5;
    private static final int CORRIDOR_W   = 3;
    private static final int WALL_THICK   = 1;
    private static final int ROOM_RADIUS  = 70;           // 部屋配置の有効半径

    // ===================================================================
    //  メインエントリポイント
    // ===================================================================
    public static BlockPos generateRoom(ServerLevel level, BlockPos center,
                                         ThemeManager.ThemeInstance themeOverride, int floor, long runSeed) {
        clearPreviousDungeon(level, center);

        long seed = System.nanoTime() ^ (floor * 7919L) ^ runSeed;
        Random rand = new Random(seed);

        long worldSeed = level.getSeed();
        ThemeManager.ThemeInstance theme = themeOverride != null ? themeOverride : ThemeManager.getThemeForFloor(floor, worldSeed);

        LayoutPattern pattern = LayoutPattern.values()[rand.nextInt(LayoutPattern.values().length)];
        boolean isBoss = ThemeManager.isBossFloor(floor);
        if (isBoss) pattern = LayoutPattern.RING;

        int biomeIndex = ThemeManager.getBiomeIndex(floor, worldSeed);

        // --- Phase 1: 部屋の配置計画 ---
        List<int[]> rooms = generateLayout(pattern, rand, isBoss);

        // --- Phase 2: 2Dグリッドの構築 ---
        int[][] grid = new int[GRID_SIZE][GRID_SIZE];

        // 2a: 部屋をグリッドに描画
        for (int[] room : rooms) {
            int gx = GRID_CENTER + room[0];
            int gz = GRID_CENTER + room[1];
            int rw = room[2];
            int rd = room[3];
            stampRoom(grid, gx, gz, rw, rd);
        }

        // 2b: 通路をグリッドに描画（直線接続）
        for (int i = 0; i < rooms.size() - 1; i++) {
            int[] from = rooms.get(i);
            int[] to   = rooms.get(i + 1);
            int fx = GRID_CENTER + from[0] + from[2] / 2;
            int fz = GRID_CENTER + from[1] + from[3] / 2;
            int tx = GRID_CENTER + to[0] + to[2] / 2;
            int tz = GRID_CENTER + to[1] + to[3] / 2;
            stampCorridor(grid, fx, fz, tx, tz);
        }

        // ループ接続（50%確率）
        for (int i = 0; i < rooms.size() - 2; i++) {
            if (rand.nextFloat() < 0.5f) {
                int j = i + 2 + rand.nextInt(Math.min(3, rooms.size() - i - 2));
                if (j < rooms.size()) {
                    int[] from = rooms.get(i);
                    int[] to   = rooms.get(j);
                    int fx = GRID_CENTER + from[0] + from[2] / 2;
                    int fz = GRID_CENTER + from[1] + from[3] / 2;
                    int tx = GRID_CENTER + to[0] + to[2] / 2;
                    int tz = GRID_CENTER + to[1] + to[3] / 2;
                    stampCorridor(grid, fx, fz, tx, tz);
                }
            }
        }

        // 2c: VOIDセルでROOM/CORRに隣接するものをWALLに昇格
        sealWalls(grid);

        // --- Phase 3: グリッドからワールドへブロック配置（VOIDはスキップ） ---
        int baseY = center.getY();
        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                int cellType = grid[gx][gz];
                if (cellType == VOID) continue;  // VOIDは何も置かない

                int worldX = center.getX() + (gx - GRID_CENTER);
                int worldZ = center.getZ() + (gz - GRID_CENTER);

                if (cellType == WALL) {
                    for (int y = -2; y <= ROOM_HEIGHT + 1; y++) {
                        setBlock(level, new BlockPos(worldX, baseY + y, worldZ), theme.wall);
                    }
                } else {
                    // ROOM or CORR
                    setBlock(level, new BlockPos(worldX, baseY - 2, worldZ), theme.floor);
                    setBlock(level, new BlockPos(worldX, baseY - 1, worldZ), theme.floor);
                    setBlock(level, new BlockPos(worldX, baseY,     worldZ), theme.floor);
                    for (int y = 1; y < ROOM_HEIGHT; y++) {
                        setBlock(level, new BlockPos(worldX, baseY + y, worldZ), Blocks.AIR.defaultBlockState());
                    }
                    setBlock(level, new BlockPos(worldX, baseY + ROOM_HEIGHT,     worldZ), theme.ceil);
                    setBlock(level, new BlockPos(worldX, baseY + ROOM_HEIGHT + 1, worldZ), theme.ceil);
                }
            }
        }

        // --- Phase 4: 照明配置（天井に埋め込み）---
        // D3: 30%の部屋を暗闇エリアにする（照明スキップ）
        Set<int[]> darkRooms = new HashSet<>();
        for (int ri = 1; ri < rooms.size(); ri++) { // スポーン部屋(0)は常に照明あり
            if (rand.nextFloat() < 0.30f) {
                darkRooms.add(rooms.get(ri));
            }
        }

        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                if (grid[gx][gz] == ROOM || grid[gx][gz] == CORR) {
                    if (gx % 4 == 0 && gz % 4 == 0) {
                        // この座標が暗闇部屋に含まれるかチェック
                        boolean isDark = false;
                        for (int[] darkRoom : darkRooms) {
                            int rgx = GRID_CENTER + darkRoom[0];
                            int rgz = GRID_CENTER + darkRoom[1];
                            if (gx >= rgx && gx < rgx + darkRoom[2] && gz >= rgz && gz < rgz + darkRoom[3]) {
                                isDark = true;
                                break;
                            }
                        }
                        if (!isDark) {
                            int wx = center.getX() + (gx - GRID_CENTER);
                            int wz = center.getZ() + (gz - GRID_CENTER);
                            setBlock(level, new BlockPos(wx, baseY + ROOM_HEIGHT, wz), theme.light);
                        }
                    }
                }
            }
        }

        // --- Phase 5: 部屋内装飾 (decor/accent活用 + ステルスカバー) ---
        for (int ri = 0; ri < rooms.size(); ri++) {
            int[] room = rooms.get(ri);
            int rx = center.getX() + room[0];
            int rz = center.getZ() + room[1];
            int rw = room[2];
            int rd = room[3];

            // A. コーナー柱 (ランダムに2箇所)
            int[][] corners = {{0, 0}, {rw - 1, 0}, {0, rd - 1}, {rw - 1, rd - 1}};
            List<int[]> cornerList = new ArrayList<>(java.util.Arrays.asList(corners));
            java.util.Collections.shuffle(cornerList, rand);
            for (int ci = 0; ci < Math.min(2, cornerList.size()); ci++) {
                int cx = rx + cornerList.get(ci)[0];
                int cz = rz + cornerList.get(ci)[1];
                for (int py = 1; py <= 3; py++) {
                    setBlock(level, new BlockPos(cx, baseY + py, cz), theme.accent);
                }
            }

            // B. 壁際装飾 (各壁の中央付近にランダム配置)
            if (rw > 5 && rd > 5 && rand.nextFloat() < 0.6f) {
                int side = rand.nextInt(4);
                int dx = switch (side) { case 0 -> rw / 2; case 1 -> rw / 2; case 2 -> 0; default -> rw - 1; };
                int dz = switch (side) { case 0 -> 0; case 1 -> rd - 1; case 2 -> rd / 2; default -> rd / 2; };
                
                int gx = room[0] + dx + GRID_CENTER;
                int gz = room[1] + dz + GRID_CENTER;
                // 通路の入り口(CORR)を塞がないようにチェック
                if (gx >= 0 && gx < GRID_SIZE && gz >= 0 && gz < GRID_SIZE && grid[gx][gz] != CORR) {
                    setBlock(level, new BlockPos(rx + dx, baseY + 1, rz + dz), theme.decor);
                }
            }

            // C. ステルスカバー (大きい部屋のみ、中央付近にL字/T字カバー)
            if (rw >= 10 && rd >= 10 && ri > 0 && rand.nextFloat() < 0.5f) {
                int coverX = rx + rw / 2;
                int coverZ = rz + rd / 2;
                // L字型カバー (半ブロック高の壁)
                for (int cx = -1; cx <= 1; cx++) {
                    setBlock(level, new BlockPos(coverX + cx, baseY + 1, coverZ), theme.wall);
                    setBlock(level, new BlockPos(coverX + cx, baseY + 2, coverZ), theme.wall);
                }
                int coverDir = rand.nextInt(2);
                for (int cz = 1; cz <= 2; cz++) {
                    int endX = coverDir == 0 ? coverX - 1 : coverX + 1;
                    setBlock(level, new BlockPos(endX, baseY + 1, coverZ + cz), theme.wall);
                    setBlock(level, new BlockPos(endX, baseY + 2, coverZ + cz), theme.wall);
                }
            } else if (rw >= 8 && rd >= 8 && ri > 0 && rand.nextFloat() < 0.4f) {
                // 小規模カバー: 単独バリケード
                int bx = rx + 2 + rand.nextInt(rw - 4);
                int bz = rz + 2 + rand.nextInt(rd - 4);
                setBlock(level, new BlockPos(bx, baseY + 1, bz), theme.wall);
                setBlock(level, new BlockPos(bx + 1, baseY + 1, bz), theme.wall);
            }
        }

        // --- Phase 5.5: 通路のアクセント壁 ---
        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                if (grid[gx][gz] != CORR) continue;
                // 通路の壁隣接セルをアクセントに置換 (25%確率)
                if (gx % 6 == 0 && gz % 6 == 0 && rand.nextFloat() < 0.25f) {
                    int wx = center.getX() + (gx - GRID_CENTER);
                    int wz = center.getZ() + (gz - GRID_CENTER);
                    setBlock(level, new BlockPos(wx, baseY + 1, wz), theme.decor);
                }
            }
        }

        // --- Phase 5.6: スポーン部屋の特別装飾 ---
        if (!rooms.isEmpty()) {
            int[] spawnRoom = rooms.get(0);
            int sx = center.getX() + spawnRoom[0];
            int sz = center.getZ() + spawnRoom[1];
            int sw = spawnRoom[2];
            int sd = spawnRoom[3];
            // 角に照明柱
            int[][] spawnCorners = {{0, 0}, {sw - 1, 0}, {0, sd - 1}, {sw - 1, sd - 1}};
            for (int[] sc : spawnCorners) {
                for (int py = 1; py <= 3; py++) {
                    setBlock(level, new BlockPos(sx + sc[0], baseY + py, sz + sc[1]), theme.accent);
                }
                setBlock(level, new BlockPos(sx + sc[0], baseY + 4, sz + sc[1]), theme.light);
            }
        }

        // --- Phase 5.7: ボス部屋アリーナ装飾 (RING中心) ---
        if (isBoss && rooms.size() > 1) {
            int[] bossRoom = rooms.get(0); // RING中心
            int bx = center.getX() + bossRoom[0];
            int bz = center.getZ() + bossRoom[1];
            int bw = bossRoom[2];
            int bd = bossRoom[3];
            // アリーナ柱 (4本) — 部屋の1/4位置
            int qw = bw / 4;
            int qd = bd / 4;
            int[][] pillarPos = {{qw, qd}, {bw - qw, qd}, {qw, bd - qd}, {bw - qw, bd - qd}};
            for (int[] pp : pillarPos) {
                for (int py = 1; py <= ROOM_HEIGHT - 1; py++) {
                    setBlock(level, new BlockPos(bx + pp[0], baseY + py, bz + pp[1]), theme.accent);
                }
            }
            // 床パターン (チェッカー)
            for (int dx = 1; dx < bw - 1; dx++) {
                for (int dz = 1; dz < bd - 1; dz++) {
                    if ((dx + dz) % 3 == 0) {
                        setBlock(level, new BlockPos(bx + dx, baseY, bz + dz), theme.accent);
                    }
                }
            }
        }

        // --- Phase 6: 敵スポーン ---
        BlockPos spawnPos = center.above(1);
        int[] spawnRoom = null;
        if (!rooms.isEmpty()) {
            spawnRoom = rooms.get(0);
            int sx = center.getX() + spawnRoom[0] + spawnRoom[2] / 2;
            int sz = center.getZ() + spawnRoom[1] + spawnRoom[3] / 2;
            spawnPos = new BlockPos(sx, baseY + 1, sz);
        }
        ensureSpawnSafety(level, spawnPos, theme);

        int totalMobsSpawned = 0;
        for (int i = 1; i < rooms.size(); i++) {
            int[] room = rooms.get(i);
            int rx = center.getX() + room[0] + room[2] / 2;
            int rz = center.getZ() + room[1] + room[3] / 2;

            if (spawnRoom != null && !isBoss) {
                int sx = center.getX() + spawnRoom[0] + spawnRoom[2] / 2;
                int sz = center.getZ() + spawnRoom[1] + spawnRoom[3] / 2;
                double dist = Math.sqrt(Math.pow(rx - sx, 2) + Math.pow(rz - sz, 2));
                if (dist < 25.0) {
                    continue; // 近すぎる部屋はスキップ
                }
            }

            BlockPos mobSpawn = new BlockPos(rx, baseY + 1, rz);

            if (isBoss && i == 1) {
                RoomManager.spawnBoss(level, mobSpawn, floor, biomeIndex);
                RoomManager.spawnMobs(level, mobSpawn, 3, floor, biomeIndex);
                totalMobsSpawned += 4;
            } else {
                int mobCount = RoomManager.getMobCountForFloor(level, floor);
                RoomManager.spawnMobs(level, mobSpawn, mobCount, floor, biomeIndex);
                totalMobsSpawned += mobCount;
            }
        }
        LOGGER.info("[TacRogue] Floor {} generated: {} rooms, {} mobs spawned", floor, rooms.size(), totalMobsSpawned);

        // --- Phase 5.5: 外周封鎖壁（部屋配置有効範囲の外側に配置） ---
        // GRID_SIZE全体ではなく、実際の配置範囲+マージンのみ囲む
        int wallR = ROOM_RADIUS + 8;  // 有効半径70 + 余裕8 = 78
        for (int i = -wallR; i <= wallR; i++) {
            int[][] edgePairs = {
                {-wallR, i}, {wallR, i}, {i, -wallR}, {i, wallR}
            };
            for (int[] edge : edgePairs) {
                int ex = center.getX() + edge[0];
                int ez = center.getZ() + edge[1];
                for (int ey = -3; ey <= ROOM_HEIGHT + 4; ey++) {
                    setBlock(level, new BlockPos(ex, baseY + ey, ez), theme.wall);
                }
            }
        }

        return spawnPos;
    }

    // ===================================================================
    //  グリッド操作
    // ===================================================================

    private static void stampRoom(int[][] grid, int gx, int gz, int w, int d) {
        for (int x = -WALL_THICK; x < w + WALL_THICK; x++) {
            for (int z = -WALL_THICK; z < d + WALL_THICK; z++) {
                int px = gx + x;
                int pz = gz + z;
                if (px < 0 || px >= GRID_SIZE || pz < 0 || pz >= GRID_SIZE) continue;

                boolean isInterior = (x >= 0 && x < w && z >= 0 && z < d);
                if (isInterior) {
                    grid[px][pz] = ROOM;
                } else if (grid[px][pz] == VOID) {
                    grid[px][pz] = WALL;
                }
            }
        }
    }

    private static void stampCorridor(int[][] grid, int fx, int fz, int tx, int tz) {
        int halfW = CORRIDOR_W / 2;

        // X軸方向
        int xDir = fx < tx ? 1 : -1;
        for (int x = fx; x != tx; x += xDir) {
            for (int w = -halfW; w <= halfW; w++) {
                stampCorridorCell(grid, x, fz + w);
            }
            stampCorridorWall(grid, x, fz - halfW - 1);
            stampCorridorWall(grid, x, fz + halfW + 1);
        }

        // 交差点
        for (int dx = -halfW - 1; dx <= halfW + 1; dx++) {
            for (int dz = -halfW - 1; dz <= halfW + 1; dz++) {
                boolean isEdge = (Math.abs(dx) == halfW + 1 || Math.abs(dz) == halfW + 1);
                if (isEdge) {
                    stampCorridorWall(grid, tx + dx, fz + dz);
                } else {
                    stampCorridorCell(grid, tx + dx, fz + dz);
                }
            }
        }

        // Z軸方向
        int zDir = fz < tz ? 1 : -1;
        for (int z = fz; z != tz; z += zDir) {
            for (int w = -halfW; w <= halfW; w++) {
                stampCorridorCell(grid, tx + w, z);
            }
            stampCorridorWall(grid, tx - halfW - 1, z);
            stampCorridorWall(grid, tx + halfW + 1, z);
        }
    }

    private static void stampCorridorCell(int[][] grid, int x, int z) {
        if (x < 0 || x >= GRID_SIZE || z < 0 || z >= GRID_SIZE) return;
        if (grid[x][z] != ROOM) grid[x][z] = CORR;
    }

    private static void stampCorridorWall(int[][] grid, int x, int z) {
        if (x < 0 || x >= GRID_SIZE || z < 0 || z >= GRID_SIZE) return;
        if (grid[x][z] == VOID) grid[x][z] = WALL;
    }

    private static void sealWalls(int[][] grid) {
        int[][] copy = new int[GRID_SIZE][GRID_SIZE];
        for (int x = 0; x < GRID_SIZE; x++) {
            System.arraycopy(grid[x], 0, copy[x], 0, GRID_SIZE);
        }

        int[] dx = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dz = {0, 0, -1, 1, -1, 1, -1, 1};

        for (int x = 0; x < GRID_SIZE; x++) {
            for (int z = 0; z < GRID_SIZE; z++) {
                if (copy[x][z] != VOID) continue;
                for (int d = 0; d < 8; d++) {
                    int nx = x + dx[d];
                    int nz = z + dz[d];
                    if (nx >= 0 && nx < GRID_SIZE && nz >= 0 && nz < GRID_SIZE) {
                        if (copy[nx][nz] == ROOM || copy[nx][nz] == CORR) {
                            grid[x][z] = WALL;
                            break;
                        }
                    }
                }
            }
        }
    }

    // ===================================================================
    //  レイアウト生成（Phase 1）
    // ===================================================================

    private static List<int[]> generateLayout(LayoutPattern pattern, Random rand, boolean isBoss) {
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
        int x = -(ROOM_RADIUS - 15);  // 左端から開始して中心を通る
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
        // 中心スポーン部屋
        rooms.add(new int[]{-8, -8, 16, 16});
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
        int gridN = 4 + rand.nextInt(2);  // 4~5列
        int spacing = 20 + rand.nextInt(6);
        // 中心を原点に整列: 左端 = -(gridN/2)*spacing
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
        if (Math.abs(x) > ROOM_RADIUS || Math.abs(z) > ROOM_RADIUS) return;
        int w = MIN_ROOM + rand.nextInt(8);
        int d = MIN_ROOM + rand.nextInt(8);
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
        // 左半分: Linear
        List<int[]> linear = generateLinear(rand, false);
        for (int[] r : linear) if (r[0] < 5) rooms.add(r);
        // 右半分: Grid (正側)
        List<int[]> grid = generateGrid(rand, false);
        for (int[] r : grid) if (r[0] >= 0) rooms.add(r);
        return rooms;
    }

    // ===================================================================
    //  ユーティリティ
    // ===================================================================

    private static boolean overlaps(List<int[]> rooms, int x, int z, int w, int d) {
        for (int[] room : rooms) {
            if (x < room[0] + room[2] + 3 && x + w + 3 > room[0] &&
                z < room[1] + room[3] + 3 && z + d + 3 > room[1]) {
                return true;
            }
        }
        return false;
    }

    private static void ensureSpawnSafety(ServerLevel level, BlockPos pos, ThemeManager.ThemeInstance theme) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(pos.offset(dx, -1, dz), theme.floor, 2);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * 最新のダンジョン領域を確実に消去するため、
     * 生成予定の center 周辺を「前回履歴に関わらず」完全にクリアする
     */
    private static void clearPreviousDungeon(ServerLevel level, BlockPos center) {
        int r = ROOM_RADIUS + 8 + 1; // wallRの分 + 1マージン
        int cx = center.getX();
        int cz = center.getZ();
        int minY = center.getY() - 4;
        int maxY = center.getY() + ROOM_HEIGHT + 4;
        LOGGER.info("[TacRogue] Forcibly clearing dungeon area at {} (radius {})", center, r);

        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (!level.getBlockState(bp).isAir()) {
                        level.setBlock(bp, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
    }

    private static void setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2 | 16); // 2=クライアント通知, 16=ライト計算スキップ
    }
}
