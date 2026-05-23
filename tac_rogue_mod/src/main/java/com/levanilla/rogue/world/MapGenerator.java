package com.levanilla.rogue.world;

import com.levanilla.rogue.world.generation.DungeonPlanGenerator;
import com.levanilla.rogue.world.generation.FloorGenerationContext;
import com.levanilla.rogue.world.generation.plan.CorridorVariant;
import com.levanilla.rogue.world.generation.plan.DungeonCorridor;
import com.levanilla.rogue.world.generation.plan.DungeonPlan;
import com.levanilla.rogue.world.generation.plan.DungeonRoom;
import com.levanilla.rogue.world.generation.plan.RoomRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
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
    private static final int BOSS_CORRIDOR_W = 5;  // ボスフロアの通路幅（Ravager等の巨体対応）
    private static final int WALL_THICK   = 1;
    private static final int ROOM_RADIUS  = 70;           // 部屋配置の有効半径
    private static final int ROOFTOP_BACKDROP_MARGIN = 26;
    private static final int CLEAR_RADIUS = ROOM_RADIUS + 8 + ROOFTOP_BACKDROP_MARGIN + 1;
    private static final int CLEAR_MIN_Y_OFFSET = -4;
    private static final int CLEAR_MAX_Y_OFFSET = ROOM_HEIGHT + 4;
    private static final int SET_BLOCK_FLAGS = 2 | 16; // 2=クライアント通知, 16=ライト計算スキップ
    private static final int MAX_STORED_FOOTPRINTS = 32;
    private static final float EXTRA_SUPPLY_CHEST_CHANCE =
        readFloatProperty("tac_rogue.extraSupplyChestChance", 0.10F, 0.0F, 1.0F);
    public static final int DEFAULT_GENERATION_BLOCKS_PER_TICK =
        Integer.getInteger("tac_rogue.generationBlocksPerTick", 5000);

    private static final ThreadLocal<DungeonFootprint> ACTIVE_FOOTPRINT = new ThreadLocal<>();
    private static final ThreadLocal<GenerationJob> ACTIVE_GENERATION_JOB = new ThreadLocal<>();
    private static final Map<DungeonKey, DungeonFootprint> DUNGEON_FOOTPRINTS =
        Collections.synchronizedMap(new LinkedHashMap<DungeonKey, DungeonFootprint>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<DungeonKey, DungeonFootprint> eldest) {
                return size() > MAX_STORED_FOOTPRINTS;
            }
        });

    public static final class GenerationJob {
        private final ServerLevel level;
        private final LinkedHashMap<BlockPos, BlockState> blockUpdates = new LinkedHashMap<>();
        private final List<Runnable> completionActions = new ArrayList<>();
        private BlockPos spawnPos;
        private boolean completionActionsRun;
        private int totalBlockUpdates;
        private int supplyChestTotal;

        private GenerationJob(ServerLevel level) {
            this.level = level;
        }

        private void addBlock(BlockPos pos, BlockState state) {
            BlockPos key = pos.immutable();
            blockUpdates.remove(key);
            blockUpdates.put(key, state);
        }

        private void addCompletionAction(Runnable action) {
            completionActions.add(action);
        }

        private void setSpawnPos(BlockPos spawnPos) {
            this.spawnPos = spawnPos == null ? null : spawnPos.immutable();
        }

        public BlockPos getSpawnPos() {
            return spawnPos;
        }

        public int remainingBlockUpdates() {
            return blockUpdates.size();
        }

        private void captureInitialCount() {
            totalBlockUpdates = Math.max(totalBlockUpdates, blockUpdates.size());
        }

        public int totalBlockUpdates() {
            return Math.max(totalBlockUpdates, blockUpdates.size());
        }

        public float progress() {
            int total = totalBlockUpdates();
            if (total <= 0) return 1.0F;
            return 1.0F - Math.max(0.0F, Math.min(1.0F, blockUpdates.size() / (float) total));
        }

        private void setSupplyChestTotal(int total) {
            supplyChestTotal = Math.max(0, total);
        }

        public int supplyChestTotal() {
            return supplyChestTotal;
        }

        public boolean isComplete() {
            return blockUpdates.isEmpty() && completionActionsRun;
        }

        public boolean tick(int maxBlockUpdates) {
            int budget = Math.max(1, maxBlockUpdates);
            int placed = 0;
            Iterator<Map.Entry<BlockPos, BlockState>> iterator = blockUpdates.entrySet().iterator();
            while (placed < budget && iterator.hasNext()) {
                Map.Entry<BlockPos, BlockState> update = iterator.next();
                placeBlockNow(level, update.getKey(), update.getValue());
                iterator.remove();
                placed++;
            }
            if (blockUpdates.isEmpty() && !completionActionsRun) {
                completionActionsRun = true;
                for (Runnable action : List.copyOf(completionActions)) {
                    action.run();
                }
                completionActions.clear();
            }
            return isComplete();
        }

        public void cancel() {
            blockUpdates.clear();
            completionActions.clear();
            completionActionsRun = true;
        }

        private boolean isQueuedForAir(BlockPos pos) {
            return Blocks.AIR.defaultBlockState().equals(blockUpdates.get(pos));
        }
    }

    // ===================================================================
    //  メインエントリポイント
    // ===================================================================
    public static BlockPos generateRoom(ServerLevel level, BlockPos center,
                                         ThemeManager.ThemeInstance themeOverride, int floor, long runSeed) {
        return generateRoom(level, center, themeOverride, floor, runSeed, 0L);
    }

    public static BlockPos generateRoom(ServerLevel level, BlockPos center,
                                         ThemeManager.ThemeInstance themeOverride, int floor, long runSeed,
                                         long floorSeedSalt) {
        return generateRoom(level, center, themeOverride, floor, runSeed, floorSeedSalt, null, "SOLO", 1);
    }

    public static BlockPos generateRoom(ServerLevel level, BlockPos center,
                                         ThemeManager.ThemeInstance themeOverride, int floor, long runSeed,
                                         long floorSeedSalt, int floorAttemptIndex) {
        return generateRoom(level, center, themeOverride, floor, runSeed, floorSeedSalt, null, "SOLO", 1,
            -1, floorAttemptIndex);
    }

    public static BlockPos generateRoom(ServerLevel level, BlockPos center,
                                         ThemeManager.ThemeInstance themeOverride, int floor, long runSeed,
                                         long floorSeedSalt, String instanceId, String mode, int participantCount) {
        return generateRoom(level, center, themeOverride, floor, runSeed, floorSeedSalt, instanceId, mode, participantCount, -1);
    }

    public static BlockPos generateRoom(ServerLevel level, BlockPos center,
                                         ThemeManager.ThemeInstance themeOverride, int floor, long runSeed,
                                         long floorSeedSalt, String instanceId, String mode, int participantCount,
                                         int maxClaimableSupplyChests) {
        return generateRoom(level, center, themeOverride, floor, runSeed, floorSeedSalt, instanceId, mode,
            participantCount, maxClaimableSupplyChests, 1);
    }

    public static BlockPos generateRoom(ServerLevel level, BlockPos center,
                                         ThemeManager.ThemeInstance themeOverride, int floor, long runSeed,
                                         long floorSeedSalt, String instanceId, String mode, int participantCount,
                                         int maxClaimableSupplyChests, int floorAttemptIndex) {
        clearPreviousDungeon(level, center);

        DungeonFootprint footprint = new DungeonFootprint();
        ACTIVE_FOOTPRINT.set(footprint);
        try {

        long worldSeed = level.getSeed();
        ThemeManager.ThemeInstance theme = themeOverride != null ? themeOverride : ThemeManager.getThemeForFloor(floor, worldSeed);

        boolean isBoss = ThemeManager.isBossFloor(floor);
        FloorGenerationContext generationContext = new FloorGenerationContext(
            runSeed,
            floor,
            floorSeedSalt,
            Math.max(1, floorAttemptIndex),
            instanceId,
            mode,
            participantCount,
            worldSeed);
        DungeonPlan plan = DungeonPlanGenerator.generate(generationContext, isBoss, theme);
        Random rand = new Random(generationContext.decorationSeed());

        int biomeIndex = ThemeManager.getBiomeIndex(floor, worldSeed);

        // --- Phase 1: 部屋の配置計画 ---
        List<int[]> rooms = plan.legacyRooms();

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

        // 2b: 通路をグリッドに描画（DungeonPlanの接続グラフを使用）
        for (DungeonCorridor corridor : plan.corridors()) {
            DungeonRoom from = plan.room(corridor.fromRoomId());
            DungeonRoom to = plan.room(corridor.toRoomId());
            if (from == null || to == null) continue;
            int fx = GRID_CENTER + from.centerX();
            int fz = GRID_CENTER + from.centerZ();
            int tx = GRID_CENTER + to.centerX();
            int tz = GRID_CENTER + to.centerZ();
            int corrWidth = Math.max(isBoss ? BOSS_CORRIDOR_W : CORRIDOR_W, corridor.width());
            stampCorridor(grid, fx, fz, tx, tz, corrWidth, corridor.variant());
        }

        // 2c: VOIDセルでROOM/CORRに隣接するものをWALLに昇格
        sealWalls(grid);

        // --- Phase 3: グリッドからワールドへブロック配置（VOIDはスキップ） ---
        int baseY = center.getY();
        ThemeShapeProfile shape = ThemeShapeProfile.of(theme);
        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                int cellType = grid[gx][gz];
                int worldX = center.getX() + (gx - GRID_CENTER);
                int worldZ = center.getZ() + (gz - GRID_CENTER);
                if (cellType == VOID) {
                    if (shape.style() == ThemeManager.ThemeGenerationStyle.ROOFTOP_OPEN) {
                        setBlock(level, new BlockPos(worldX, baseY - 4, worldZ),
                            rooftopBackdropBlock(theme, gx, gz));
                    }
                    continue;
                }

                if (cellType == WALL) {
                    int wallTop = shape.wallTop(gx, gz);
                    for (int y = -2; y <= wallTop; y++) {
                        BlockState wallState = shape.roughWalls() && y >= 1 && cellNoise(gx, gz, y) < 0.18F
                            ? theme.accent
                            : theme.wall;
                        setBlock(level, new BlockPos(worldX, baseY + y, worldZ), wallState);
                    }
                    if (shape.needsInvisibleSafetyCap()) {
                        setBlock(level, new BlockPos(worldX, baseY + wallTop + 1, worldZ),
                            Blocks.BARRIER.defaultBlockState());
                    }
                    if (shape.openCeiling()) {
                        for (int y = wallTop + 1; y <= ROOM_HEIGHT + 1; y++) {
                            if (shape.needsInvisibleSafetyCap() && y == wallTop + 1) continue;
                            setBlock(level, new BlockPos(worldX, baseY + y, worldZ), Blocks.AIR.defaultBlockState());
                        }
                    }
                } else {
                    // ROOM or CORR
                    setBlock(level, new BlockPos(worldX, baseY - 2, worldZ), theme.floor);
                    setBlock(level, new BlockPos(worldX, baseY - 1, worldZ), theme.floor);
                    setBlock(level, new BlockPos(worldX, baseY,     worldZ), theme.floor);
                    int clearTop = shape.openCeiling() ? ROOM_HEIGHT + 1 : ROOM_HEIGHT - 1;
                    for (int y = 1; y <= clearTop; y++) {
                        setBlock(level, new BlockPos(worldX, baseY + y, worldZ), Blocks.AIR.defaultBlockState());
                    }
                    if (!shape.openCeiling()) {
                        if (shape.brokenCeiling() && cellNoise(gx, gz, 71) < shape.ceilingBreakChance()) {
                            setBlock(level, new BlockPos(worldX, baseY + ROOM_HEIGHT,     worldZ), Blocks.AIR.defaultBlockState());
                            setBlock(level, new BlockPos(worldX, baseY + ROOM_HEIGHT + 1, worldZ), Blocks.AIR.defaultBlockState());
                        } else {
                            BlockState ceiling = shape.roughCeiling() && cellNoise(gx, gz, 72) < 0.22F ? theme.accent : theme.ceil;
                            setBlock(level, new BlockPos(worldX, baseY + ROOM_HEIGHT,     worldZ), ceiling);
                            setBlock(level, new BlockPos(worldX, baseY + ROOM_HEIGHT + 1, worldZ), theme.ceil);
                        }
                    }
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
                        if (!isDark && !shape.openCeiling()) {
                            int wx = center.getX() + (gx - GRID_CENTER);
                            int wz = center.getZ() + (gz - GRID_CENTER);
                            setBlock(level, new BlockPos(wx, baseY + ROOM_HEIGHT, wz), theme.light);
                        } else if (!isDark && shape.openCeiling() && gx % 8 == 0 && gz % 8 == 0) {
                            int wx = center.getX() + (gx - GRID_CENTER);
                            int wz = center.getZ() + (gz - GRID_CENTER);
                            setBlock(level, new BlockPos(wx, baseY + 2, wz),
                                Blocks.LIGHT.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 12));
                        }
                    }
                }
            }
        }

        // --- Phase 5: 部屋内装飾 (decor/accent活用 + ステルスカバー) ---
        int supplyChests = 0;
        int maxSupplyChests = isBoss ? 1 : 1 + (rand.nextFloat() < EXTRA_SUPPLY_CHEST_CHANCE ? 1 : 0);
        if (maxClaimableSupplyChests >= 0) {
            maxSupplyChests = Math.min(maxSupplyChests, Math.max(0, maxClaimableSupplyChests));
        }
        for (int ri = 0; ri < rooms.size(); ri++) {
            int[] room = rooms.get(ri);
            int rx = center.getX() + room[0];
            int rz = center.getZ() + room[1];
            int rw = room[2];
            int rd = room[3];
            RoomRole role = ri < plan.rooms().size() ? plan.rooms().get(ri).role() : RoomRole.COMBAT_SMALL;

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

            boolean supplyCandidate = role == RoomRole.SUPPLY_RISK || role == RoomRole.SIDE_REWARD || rand.nextFloat() < 0.30F;
            if (decorateRoomArchetype(level, rand, theme, baseY, ri, isBoss, rx, rz, rw, rd,
                supplyChests < maxSupplyChests && supplyCandidate, floor, instanceId, mode, supplyChests)) {
                supplyChests++;
            }
            decorateRoomVolume(level, rand, theme, shape, baseY, role, isBoss, rx, rz, rw, rd);
            decorateThemeRoomDetails(level, rand, theme, shape, baseY, role, isBoss, rx, rz, rw, rd);
        }
        if (supplyChests == 0 && maxSupplyChests > 0 && !rooms.isEmpty()) {
            int fallbackIndex = isBoss && rooms.size() > 2 ? 2 : Math.min(1, rooms.size() - 1);
            int[] room = rooms.get(fallbackIndex);
            buildSupplyCorner(level, rand, theme, baseY,
                center.getX() + room[0],
                center.getZ() + room[1],
                room[2],
                room[3],
                true,
                floor,
                instanceId,
                mode,
                0);
            supplyChests = 1;
        }
        stampSupplyChestTotals(level, center, floor, instanceId, supplyChests);
        GenerationJob activeJob = ACTIVE_GENERATION_JOB.get();
        if (activeJob != null) activeJob.setSupplyChestTotal(supplyChests);

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
            // アリーナ柱 (4本) — 部屋の1/3位置（中央エリアを広く確保）
            int qw = bw / 3;
            int qd = bd / 3;
            int[][] pillarPos = {{qw, qd}, {bw - qw, qd}, {qw, bd - qd}, {bw - qw, bd - qd}};
            for (int[] pp : pillarPos) {
                for (int py = 1; py <= ROOM_HEIGHT - 1; py++) {
                    setBlock(level, new BlockPos(bx + pp[0], baseY + py, bz + pp[1]), theme.accent);
                }
            }
            // 中心エリアの空間確保: 柱の内側にブロックが残らないようクリア
            for (int dx = qw + 1; dx < bw - qw; dx++) {
                for (int dz = qd + 1; dz < bd - qd; dz++) {
                    for (int py = 1; py < ROOM_HEIGHT; py++) {
                        setBlock(level, new BlockPos(bx + dx, baseY + py, bz + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    }
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
            int spawnRoomId = Math.max(0, Math.min(plan.spawnRoomId(), rooms.size() - 1));
            spawnRoom = rooms.get(spawnRoomId);
            int sx = center.getX() + spawnRoom[0] + spawnRoom[2] / 2;
            int sz = center.getZ() + spawnRoom[1] + spawnRoom[3] / 2;
            spawnPos = new BlockPos(sx, baseY + 1, sz);
        }
        ensureSpawnSafety(level, spawnPos, theme);

        int totalMobsSpawned = 0;

        // ボスフロア: ボスは中心部屋 (index 0) にスポーン
        if (isBoss && !rooms.isEmpty()) {
            int[] bossRoom = rooms.get(0);
            int bossX = center.getX() + bossRoom[0] + bossRoom[2] / 2;
            int bossZ = center.getZ() + bossRoom[1] + bossRoom[3] / 2;
            BlockPos bossSpawnPos = new BlockPos(bossX, baseY + 1, bossZ);
            spawnBossAfterBlocks(level, bossSpawnPos, floor, biomeIndex, instanceId, mode, participantCount);
            totalMobsSpawned++;
        }

        List<int[]> eligibleMobRooms = new ArrayList<>();
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
            if (spawnRoom != null && isBoss && room == spawnRoom) {
                continue; // ボス前室は準備用の安全地帯にする
            }

            BlockPos mobSpawn = new BlockPos(rx, baseY + 1, rz);

            if (isBoss) {
                // ボスフロアの外周部屋: 雑魚を配置
                mobSpawn = pickMobSpawnInRoom(center, room, baseY, rand);
                int bossAdds = floor < 10 ? 1 : 2;
                spawnMobsAfterBlocks(level, mobSpawn, bossAdds, floor, biomeIndex, instanceId, mode, participantCount);
                totalMobsSpawned += bossAdds;
            } else {
                eligibleMobRooms.add(room);
            }
        }
        if (!isBoss && !eligibleMobRooms.isEmpty()) {
            Collections.shuffle(eligibleMobRooms, rand);
            int remainingBudget = RoomManager.getTotalMobBudgetForFloor(level, floor, eligibleMobRooms.size(), participantCount);
            int maxPerRoom = RoomManager.getMaxMobsPerRoomForFloor(floor);

            for (int i = 0; i < eligibleMobRooms.size() && remainingBudget > 0; i++) {
                int[] room = eligibleMobRooms.get(i);
                int roomsLeft = eligibleMobRooms.size() - i;
                int mobCount = Math.min(maxPerRoom, (int) Math.ceil(remainingBudget / (double) roomsLeft));
                BlockPos mobSpawn = pickMobSpawnInRoom(center, room, baseY, rand);

                spawnMobsAfterBlocks(level, mobSpawn, mobCount, floor, biomeIndex, instanceId, mode, participantCount);
                totalMobsSpawned += mobCount;
                remainingBudget -= mobCount;
            }
        }
        if (isVerboseLogging()) {
            LOGGER.info("[TacRogue] Floor {} generated: {} rooms, {} mobs queued, archetype={}, attempt={}, hash={}",
                floor, rooms.size(), totalMobsSpawned, plan.archetype(), generationContext.attemptIndex(), plan.layoutHash());
        } else {
            LOGGER.debug("[TacRogue] Floor {} generated: {} rooms, {} mobs queued, archetype={}, attempt={}, hash={}",
                floor, rooms.size(), totalMobsSpawned, plan.archetype(), generationContext.attemptIndex(), plan.layoutHash());
        }

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
                int outerTop = shape.outerWallTop();
                for (int ey = -3; ey <= outerTop; ey++) {
                    setBlock(level, new BlockPos(ex, baseY + ey, ez), theme.wall);
                }
                if (shape.needsInvisibleSafetyCap()) {
                    setBlock(level, new BlockPos(ex, baseY + outerTop + 1, ez),
                        Blocks.BARRIER.defaultBlockState());
                }
                if (shape.openCeiling()) {
                    for (int ey = outerTop + 1; ey <= ROOM_HEIGHT + 4; ey++) {
                        if (shape.needsInvisibleSafetyCap() && ey == outerTop + 1) continue;
                        setBlock(level, new BlockPos(ex, baseY + ey, ez), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        if (shape.style() == ThemeManager.ThemeGenerationStyle.ROOFTOP_OPEN) {
            buildRooftopBackdrop(level, rand, theme, center, baseY, wallR);
        }

        GenerationJob finalJob = ACTIVE_GENERATION_JOB.get();
        if (finalJob != null) {
            finalJob.addCompletionAction(() -> flushMapVisibleChunkUpdates(level, footprint));
        } else {
            flushMapVisibleChunkUpdates(level, footprint);
        }
        storeDungeonFootprint(level, center, footprint);
        return spawnPos;
        } finally {
            ACTIVE_FOOTPRINT.remove();
        }
    }

    public static GenerationJob generateRoomJob(ServerLevel level, BlockPos center,
                                                ThemeManager.ThemeInstance themeOverride, int floor, long runSeed,
                                                long floorSeedSalt, String instanceId, String mode,
                                                int participantCount) {
        return generateRoomJob(level, center, themeOverride, floor, runSeed, floorSeedSalt, instanceId, mode,
            participantCount, -1);
    }

    public static GenerationJob generateRoomJob(ServerLevel level, BlockPos center,
                                                ThemeManager.ThemeInstance themeOverride, int floor, long runSeed,
                                                long floorSeedSalt, String instanceId, String mode,
                                                int participantCount, int maxClaimableSupplyChests) {
        return generateRoomJob(level, center, themeOverride, floor, runSeed, floorSeedSalt, instanceId, mode,
            participantCount, maxClaimableSupplyChests, 1);
    }

    public static GenerationJob generateRoomJob(ServerLevel level, BlockPos center,
                                                ThemeManager.ThemeInstance themeOverride, int floor, long runSeed,
                                                long floorSeedSalt, String instanceId, String mode,
                                                int participantCount, int maxClaimableSupplyChests,
                                                int floorAttemptIndex) {
        GenerationJob job = new GenerationJob(level);
        ACTIVE_GENERATION_JOB.set(job);
        try {
            job.setSpawnPos(generateRoom(level, center, themeOverride, floor, runSeed, floorSeedSalt,
                instanceId, mode, participantCount, maxClaimableSupplyChests, floorAttemptIndex));
            job.captureInitialCount();
            return job;
        } finally {
            ACTIVE_GENERATION_JOB.remove();
        }
    }

    public static void clearStoredDungeon(ServerLevel level, BlockPos center) {
        clearPreviousDungeon(level, center);
    }

    // ===================================================================
    //  グリッド操作
    // ===================================================================

    private enum CeilingMode {
        SEALED,
        BROKEN,
        OPEN
    }

    private record ThemeShapeProfile(
        ThemeManager.ThemeGenerationStyle style,
        CeilingMode ceilingMode,
        int wallTop,
        int outerWallTop,
        float ceilingBreakChance,
        boolean roughWalls,
        boolean roughCeiling
    ) {
        private static ThemeShapeProfile of(ThemeManager.ThemeInstance theme) {
            ThemeManager.ThemeGenerationStyle style = ThemeManager.generationStyle(theme);
            return switch (style) {
                case ROOFTOP_OPEN -> new ThemeShapeProfile(style, CeilingMode.OPEN, 2, 2, 0.0F, false, false);
                case RADAR_OPEN -> new ThemeShapeProfile(style, CeilingMode.OPEN, 3, 3, 0.0F, false, false);
                case BROKEN_RUINS -> new ThemeShapeProfile(style, CeilingMode.BROKEN, ROOM_HEIGHT + 1, ROOM_HEIGHT + 4, 0.18F, true, true);
                case ORGANIC_CAVE -> new ThemeShapeProfile(style, CeilingMode.BROKEN, ROOM_HEIGHT + 1, ROOM_HEIGHT + 4, 0.10F, true, true);
                case FLOODED_LOW, SHIPWRECK -> new ThemeShapeProfile(style, CeilingMode.BROKEN, ROOM_HEIGHT + 1, ROOM_HEIGHT + 4, 0.12F, true, false);
                case HANGAR -> new ThemeShapeProfile(style, CeilingMode.BROKEN, ROOM_HEIGHT + 1, ROOM_HEIGHT + 4, 0.08F, false, false);
                case LAB_COMPLEX -> new ThemeShapeProfile(style, CeilingMode.SEALED, ROOM_HEIGHT + 1, ROOM_HEIGHT + 4, 0.0F, false, false);
                case MILITARY_COMPOUND -> new ThemeShapeProfile(style, CeilingMode.SEALED, ROOM_HEIGHT + 1, ROOM_HEIGHT + 4, 0.0F, false, false);
                case NETHER_FORTRESS -> new ThemeShapeProfile(style, CeilingMode.BROKEN, ROOM_HEIGHT + 1, ROOM_HEIGHT + 4, 0.08F, false, true);
                case URBAN_INTERIOR -> new ThemeShapeProfile(style, CeilingMode.SEALED, ROOM_HEIGHT + 1, ROOM_HEIGHT + 4, 0.0F, false, false);
                case VOID_ALIEN -> new ThemeShapeProfile(style, CeilingMode.BROKEN, ROOM_HEIGHT + 1, ROOM_HEIGHT + 4, 0.14F, false, true);
                default -> new ThemeShapeProfile(style, CeilingMode.SEALED, ROOM_HEIGHT + 1, ROOM_HEIGHT + 4, 0.0F, false, false);
            };
        }

        private boolean openCeiling() {
            return ceilingMode == CeilingMode.OPEN;
        }

        private boolean brokenCeiling() {
            return ceilingMode == CeilingMode.BROKEN;
        }

        private boolean needsInvisibleSafetyCap() {
            return style == ThemeManager.ThemeGenerationStyle.ROOFTOP_OPEN;
        }

        private int wallTop(int gx, int gz) {
            if (openCeiling()) return wallTop;
            if (roughWalls && cellNoise(gx, gz, 41) < 0.16F) {
                return Math.max(3, wallTop - 1);
            }
            return wallTop;
        }
    }

    private static float cellNoise(int x, int z, int salt) {
        long value = 0x9E3779B97F4A7C15L;
        value ^= (long) x * 0xBF58476D1CE4E5B9L;
        value ^= (long) z * 0x94D049BB133111EBL;
        value ^= (long) salt * 0xD6E8FEB86659FD93L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (float) ((value >>> 40) & 0xFFFFFFL) / (float) 0x1000000;
    }

    private static boolean decorateRoomArchetype(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                              int baseY, int roomIndex, boolean isBoss,
                                              int rx, int rz, int rw, int rd,
                                              boolean allowSupplyChest, int floor, String instanceId, String mode,
                                              int chestIndex) {
        if (rw < 7 || rd < 7) return false;

        int archetype = isBoss && roomIndex == 0 ? 4 : Math.floorMod(roomIndex + rand.nextInt(5), 6);
        switch (archetype) {
            case 0 -> buildOpenCombatMarkers(level, theme, baseY, rx, rz, rw, rd);
            case 1 -> buildCoverRoom(level, theme, baseY, rx, rz, rw, rd);
            case 2 -> buildConnectorRoom(level, theme, baseY, rx, rz, rw, rd);
            case 3 -> {
                if (buildSupplyCorner(level, rand, theme, baseY, rx, rz, rw, rd, allowSupplyChest,
                    floor, instanceId, mode, chestIndex)) {
                    return true;
                }
            }
            case 4 -> buildArenaMarks(level, theme, baseY, rx, rz, rw, rd);
            default -> buildLowVisibilityRoom(level, rand, theme, baseY, rx, rz, rw, rd);
        }
        return false;
    }

    private static void decorateRoomVolume(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                           ThemeShapeProfile shape,
                                           int baseY, RoomRole role, boolean isBoss,
                                           int rx, int rz, int rw, int rd) {
        if (shape.openCeiling()) return;
        if (isBoss || rw < 11 || rd < 11 || role == RoomRole.START) return;
        float chance = switch (role) {
            case COMBAT_LONG, COVER_DENSE -> 0.45F;
            case SIDE_REWARD, SUPPLY_RISK -> 0.35F;
            case ELITE, AMBUSH -> 0.25F;
            default -> 0.15F;
        };
        if (rand.nextFloat() >= chance) return;

        if (rand.nextBoolean()) {
            buildAtriumOpening(level, rand, theme, baseY, rx, rz, rw, rd);
        } else {
            buildRaisedPocket(level, rand, theme, baseY, rx, rz, rw, rd);
        }
    }

    private static void decorateThemeRoomDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                                 ThemeShapeProfile shape, int baseY, RoomRole role, boolean isBoss,
                                                 int rx, int rz, int rw, int rd) {
        if (role == RoomRole.START || rw < 7 || rd < 7) return;
        switch (shape.style()) {
            case ORGANIC_CAVE -> buildOrganicCaveDetails(level, rand, theme, baseY, rx, rz, rw, rd);
            case BROKEN_RUINS -> buildBrokenRuinDetails(level, rand, theme, baseY, rx, rz, rw, rd);
            case FLOODED_LOW -> buildFloodedLowDetails(level, rand, theme, baseY, rx, rz, rw, rd);
            case SHIPWRECK -> buildShipwreckDetails(level, rand, theme, baseY, rx, rz, rw, rd);
            case PIPELINE, SUBWAY, SEWER -> buildLinearUtilityDetails(level, rand, theme, shape.style(), baseY, rx, rz, rw, rd);
            case HANGAR -> buildHangarDetails(level, theme, baseY, rx, rz, rw, rd);
            case RADAR_OPEN -> buildRadarDetails(level, theme, baseY, rx, rz, rw, rd);
            case LAB_COMPLEX -> buildLabDetails(level, rand, theme, baseY, rx, rz, rw, rd);
            case MILITARY_COMPOUND -> buildMilitaryCompoundDetails(level, rand, theme, baseY, rx, rz, rw, rd);
            case NETHER_FORTRESS -> buildNetherFortressDetails(level, rand, theme, baseY, rx, rz, rw, rd);
            case URBAN_INTERIOR -> buildUrbanInteriorDetails(level, rand, theme, baseY, rx, rz, rw, rd);
            case TEMPLE_AXIS -> buildTempleAxisDetails(level, theme, baseY, rx, rz, rw, rd);
            case VOID_ALIEN -> buildVoidAlienDetails(level, rand, theme, baseY, rx, rz, rw, rd);
            case ROOFTOP_OPEN -> buildRooftopDetails(level, rand, theme, baseY, rx, rz, rw, rd);
            default -> {
            }
        }
    }

    private static void buildOrganicCaveDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                                int baseY, int rx, int rz, int rw, int rd) {
        int clusters = Math.max(2, Math.min(6, (rw + rd) / 6));
        for (int i = 0; i < clusters; i++) {
            int x = rx + 2 + rand.nextInt(Math.max(1, rw - 4));
            int z = rz + 2 + rand.nextInt(Math.max(1, rd - 4));
            int height = 1 + rand.nextInt(3);
            for (int y = 1; y <= height; y++) {
                setBlock(level, new BlockPos(x, baseY + y, z), y == height ? theme.accent : theme.wall);
            }
            if (rand.nextBoolean() && rw > 9 && rd > 9) {
                setBlock(level, new BlockPos(x + 1, baseY + 1, z), theme.decor);
            }
        }
    }

    private static void buildBrokenRuinDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                               int baseY, int rx, int rz, int rw, int rd) {
        int rubble = Math.max(2, Math.min(7, (rw * rd) / 26));
        for (int i = 0; i < rubble; i++) {
            int x = rx + 1 + rand.nextInt(Math.max(1, rw - 2));
            int z = rz + 1 + rand.nextInt(Math.max(1, rd - 2));
            setBlock(level, new BlockPos(x, baseY + 1, z), rand.nextBoolean() ? theme.wall : theme.accent);
            if (rand.nextFloat() < 0.35F) {
                setBlock(level, new BlockPos(x, baseY + ROOM_HEIGHT, z), Blocks.AIR.defaultBlockState());
                setBlock(level, new BlockPos(x, baseY + ROOM_HEIGHT + 1, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void buildFloodedLowDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                               int baseY, int rx, int rz, int rw, int rd) {
        int z = rz + rd / 2;
        for (int x = rx + 2; x < rx + rw - 2; x++) {
            if ((x + z) % 3 == 0) {
                setBlock(level, new BlockPos(x, baseY, z), theme.accent);
            }
        }
        if (rand.nextBoolean()) {
            buildRaisedDryPlatform(level, theme, baseY, rx, rz, rw, rd);
        }
    }

    private static void buildShipwreckDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                              int baseY, int rx, int rz, int rw, int rd) {
        int ribs = Math.max(2, rw / 5);
        for (int i = 1; i <= ribs; i++) {
            int x = rx + i * rw / (ribs + 1);
            for (int z = rz + 2; z < rz + rd - 2; z += 2) {
                setBlock(level, new BlockPos(x, baseY + 1, z), theme.accent);
            }
            if (rand.nextBoolean()) {
                setBlock(level, new BlockPos(x, baseY + 2, rz + rd / 2), theme.wall);
            }
        }
    }

    private static void buildLinearUtilityDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                                  ThemeManager.ThemeGenerationStyle style,
                                                  int baseY, int rx, int rz, int rw, int rd) {
        boolean alongX = rw >= rd;
        int mid = alongX ? rz + rd / 2 : rx + rw / 2;
        int start = alongX ? rx + 2 : rz + 2;
        int end = alongX ? rx + rw - 2 : rz + rd - 2;
        for (int p = start; p < end; p++) {
            if (p % 2 != 0) continue;
            BlockPos pos = alongX ? new BlockPos(p, baseY, mid) : new BlockPos(mid, baseY, p);
            setBlock(level, pos, style == ThemeManager.ThemeGenerationStyle.SUBWAY ? theme.accent : theme.wall);
        }
        if (style == ThemeManager.ThemeGenerationStyle.PIPELINE || style == ThemeManager.ThemeGenerationStyle.SEWER) {
            int fixtures = 1 + rand.nextInt(3);
            for (int i = 0; i < fixtures; i++) {
                int p = start + rand.nextInt(Math.max(1, end - start));
                BlockPos pos = alongX ? new BlockPos(p, baseY + 1, mid + 2) : new BlockPos(mid + 2, baseY + 1, p);
                setBlock(level, pos, theme.decor);
            }
        }
    }

    private static void buildHangarDetails(ServerLevel level, ThemeManager.ThemeInstance theme,
                                           int baseY, int rx, int rz, int rw, int rd) {
        for (int x = rx + 2; x < rx + rw - 2; x++) {
            if (x % 4 == 0) {
                setBlock(level, new BlockPos(x, baseY, rz + rd / 2), theme.accent);
            }
        }
        setBlock(level, new BlockPos(rx + rw / 2, baseY + 1, rz + 2), theme.light);
    }

    private static void buildRadarDetails(ServerLevel level, ThemeManager.ThemeInstance theme,
                                          int baseY, int rx, int rz, int rw, int rd) {
        int cx = rx + rw / 2;
        int cz = rz + rd / 2;
        for (int y = 1; y <= 4; y++) {
            setBlock(level, new BlockPos(cx, baseY + y, cz), theme.accent);
        }
        for (int dx = -2; dx <= 2; dx++) {
            setBlock(level, new BlockPos(cx + dx, baseY + 4, cz), theme.light);
        }
        for (int dz = -2; dz <= 2; dz++) {
            setBlock(level, new BlockPos(cx, baseY + 4, cz + dz), theme.light);
        }
    }

    private static void buildLabDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                        int baseY, int rx, int rz, int rw, int rd) {
        int cx = rx + rw / 2;
        int cz = rz + rd / 2;
        for (int x = rx + 2; x < rx + rw - 2; x++) {
            if ((x - rx) % 4 == 0) {
                setBlock(level, new BlockPos(x, baseY + 1, cz), theme.accent);
                setBlock(level, new BlockPos(x, baseY + 2, cz), Blocks.IRON_BARS.defaultBlockState());
            }
        }
        int pods = 1 + rand.nextInt(3);
        for (int i = 0; i < pods; i++) {
            int x = rx + 2 + rand.nextInt(Math.max(1, rw - 4));
            int z = rz + 2 + rand.nextInt(Math.max(1, rd - 4));
            setBlock(level, new BlockPos(x, baseY + 1, z), Blocks.GLASS.defaultBlockState());
            setBlock(level, new BlockPos(x, baseY + 2, z), theme.light);
        }
    }

    private static void buildMilitaryCompoundDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                                     int baseY, int rx, int rz, int rw, int rd) {
        int lanes = Math.max(1, Math.min(3, rd / 5));
        for (int lane = 1; lane <= lanes; lane++) {
            int z = rz + lane * rd / (lanes + 1);
            for (int x = rx + 2; x < rx + rw - 2; x += 5) {
                setBlock(level, new BlockPos(x, baseY + 1, z), theme.wall);
                if (rand.nextBoolean()) {
                    setBlock(level, new BlockPos(x + 1, baseY + 1, z), theme.accent);
                }
            }
        }
        setBlock(level, new BlockPos(rx + rw / 2, baseY + 1, rz + rd - 3), Blocks.BARREL.defaultBlockState());
    }

    private static void buildNetherFortressDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                                   int baseY, int rx, int rz, int rw, int rd) {
        int cx = rx + rw / 2;
        for (int z = rz + 2; z < rz + rd - 2; z += 3) {
            setBlock(level, new BlockPos(cx - 2, baseY + 1, z), theme.accent);
            setBlock(level, new BlockPos(cx + 2, baseY + 1, z), theme.accent);
            if (rand.nextFloat() < 0.35F) {
                setBlock(level, new BlockPos(cx, baseY, z), theme.light);
            }
        }
    }

    private static void buildUrbanInteriorDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                                  int baseY, int rx, int rz, int rw, int rd) {
        boolean splitX = rw >= rd;
        int divider = splitX ? rx + rw / 2 : rz + rd / 2;
        int start = splitX ? rz + 2 : rx + 2;
        int end = splitX ? rz + rd - 2 : rx + rw - 2;
        int doorway = start + rand.nextInt(Math.max(1, end - start));
        for (int p = start; p < end; p++) {
            if (Math.abs(p - doorway) <= 1) continue;
            BlockPos pos = splitX ? new BlockPos(divider, baseY + 1, p) : new BlockPos(p, baseY + 1, divider);
            setBlock(level, pos, theme.wall);
            if (p % 4 == 0) setBlock(level, pos.above(), theme.accent);
        }
        setBlock(level, new BlockPos(rx + 2, baseY + 1, rz + 2), Blocks.BOOKSHELF.defaultBlockState());
        setBlock(level, new BlockPos(rx + rw - 3, baseY + 1, rz + rd - 3), theme.decor);
    }

    private static void buildTempleAxisDetails(ServerLevel level, ThemeManager.ThemeInstance theme,
                                               int baseY, int rx, int rz, int rw, int rd) {
        int cx = rx + rw / 2;
        for (int z = rz + 2; z < rz + rd - 2; z += 4) {
            setBlock(level, new BlockPos(cx - 3, baseY + 1, z), theme.accent);
            setBlock(level, new BlockPos(cx + 3, baseY + 1, z), theme.accent);
        }
        setBlock(level, new BlockPos(cx, baseY + 1, rz + rd - 3), theme.light);
    }

    private static void buildVoidAlienDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                              int baseY, int rx, int rz, int rw, int rd) {
        int marks = Math.max(2, Math.min(5, (rw + rd) / 8));
        for (int i = 0; i < marks; i++) {
            int x = rx + 2 + rand.nextInt(Math.max(1, rw - 4));
            int z = rz + 2 + rand.nextInt(Math.max(1, rd - 4));
            setBlock(level, new BlockPos(x, baseY, z), theme.accent);
            setBlock(level, new BlockPos(x, baseY + ROOM_HEIGHT, z), theme.light);
        }
    }

    private static void buildRooftopDetails(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                            int baseY, int rx, int rz, int rw, int rd) {
        int fixtures = Math.max(1, Math.min(3, (rw + rd) / 12));
        for (int i = 0; i < fixtures; i++) {
            int x = rx + 2 + rand.nextInt(Math.max(1, rw - 4));
            int z = rz + 2 + rand.nextInt(Math.max(1, rd - 4));
            setBlock(level, new BlockPos(x, baseY + 1, z), Blocks.IRON_BLOCK.defaultBlockState());
            if (rand.nextBoolean()) {
                setBlock(level, new BlockPos(x + 1, baseY + 1, z), theme.accent);
            }
        }
    }

    private static void buildRooftopBackdrop(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                             BlockPos center, int baseY, int innerR) {
        int outerR = innerR + ROOFTOP_BACKDROP_MARGIN;
        int roofY = baseY - 4;
        for (int dx = -outerR; dx <= outerR; dx++) {
            for (int dz = -outerR; dz <= outerR; dz++) {
                if (Math.abs(dx) <= innerR && Math.abs(dz) <= innerR) continue;
                int worldX = center.getX() + dx;
                int worldZ = center.getZ() + dz;
                setBlock(level, new BlockPos(worldX, roofY, worldZ), rooftopBackdropBlock(theme, dx, dz));
            }
        }

        int cityBlocks = 26;
        for (int i = 0; i < cityBlocks; i++) {
            int side = i % 4;
            int offset = -outerR + 6 + rand.nextInt(Math.max(1, outerR * 2 - 12));
            int depth = 4 + rand.nextInt(9);
            int width = 4 + rand.nextInt(8);
            int height = 2 + rand.nextInt(5);
            int bx = switch (side) {
                case 0 -> -outerR + 2 + rand.nextInt(Math.max(1, ROOFTOP_BACKDROP_MARGIN / 2));
                case 1 -> outerR - width - 2 - rand.nextInt(Math.max(1, ROOFTOP_BACKDROP_MARGIN / 2));
                default -> offset;
            };
            int bz = switch (side) {
                case 2 -> -outerR + 2 + rand.nextInt(Math.max(1, ROOFTOP_BACKDROP_MARGIN / 2));
                case 3 -> outerR - depth - 2 - rand.nextInt(Math.max(1, ROOFTOP_BACKDROP_MARGIN / 2));
                default -> offset;
            };
            if (Math.abs(bx) <= innerR + 1 && Math.abs(bz) <= innerR + 1) continue;
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    for (int y = 0; y < height; y++) {
                        setBlock(level, new BlockPos(center.getX() + bx + x, roofY + 1 + y, center.getZ() + bz + z),
                            y == height - 1 ? theme.accent : theme.wall);
                    }
                }
            }
        }
    }

    private static BlockState rooftopBackdropBlock(ThemeManager.ThemeInstance theme, int x, int z) {
        return ((Math.floorDiv(x, 7) + Math.floorDiv(z, 7)) & 1) == 0
            ? theme.floor
            : theme.accent;
    }

    private static void buildRaisedDryPlatform(ServerLevel level, ThemeManager.ThemeInstance theme,
                                               int baseY, int rx, int rz, int rw, int rd) {
        int px = rx + rw / 2 - 2;
        int pz = rz + rd / 2 - 2;
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                setBlock(level, new BlockPos(px + dx, baseY + 1, pz + dz), theme.floor);
            }
        }
    }

    private static void buildAtriumOpening(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                           int baseY, int rx, int rz, int rw, int rd) {
        int margin = 3;
        int ox0 = rx + margin;
        int oz0 = rz + margin;
        int ow = Math.max(3, rw - margin * 2);
        int od = Math.max(3, rd - margin * 2);
        if (ow < 4 || od < 4) return;
        for (int dx = 0; dx < ow; dx++) {
            for (int dz = 0; dz < od; dz++) {
                boolean frame = dx == 0 || dz == 0 || dx == ow - 1 || dz == od - 1;
                BlockPos ceiling = new BlockPos(ox0 + dx, baseY + ROOM_HEIGHT, oz0 + dz);
                BlockPos upper = ceiling.above();
                if (frame) {
                    if ((dx + dz) % 3 == 0) setBlock(level, ceiling, theme.accent);
                    continue;
                }
                setBlock(level, ceiling, Blocks.AIR.defaultBlockState());
                setBlock(level, upper, Blocks.AIR.defaultBlockState());
            }
        }
        int lx = ox0 + ow / 2;
        int lz = oz0 + od / 2;
        setBlock(level, new BlockPos(lx, baseY + ROOM_HEIGHT - 1, lz), theme.light);
    }

    private static void buildRaisedPocket(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                          int baseY, int rx, int rz, int rw, int rd) {
        int side = rand.nextInt(4);
        int width = Math.max(4, Math.min(7, rw / 2));
        int depth = Math.max(4, Math.min(7, rd / 2));
        int px = switch (side) {
            case 0, 1 -> rx + rw / 2 - width / 2;
            case 2 -> rx + 2;
            default -> rx + rw - width - 2;
        };
        int pz = switch (side) {
            case 0 -> rz + 2;
            case 1 -> rz + rd - depth - 2;
            default -> rz + rd / 2 - depth / 2;
        };
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                if (dx == 0 || dz == 0 || dx == width - 1 || dz == depth - 1) {
                    setBlock(level, new BlockPos(px + dx, baseY + 1, pz + dz), theme.wall);
                    continue;
                }
                setBlock(level, new BlockPos(px + dx, baseY + 1, pz + dz), Blocks.AIR.defaultBlockState());
                setBlock(level, new BlockPos(px + dx, baseY + 2, pz + dz), Blocks.AIR.defaultBlockState());
                if ((dx + dz) % 4 == 0) {
                    setBlock(level, new BlockPos(px + dx, baseY, pz + dz), theme.accent);
                }
            }
        }
    }

    private static void buildOpenCombatMarkers(ServerLevel level, ThemeManager.ThemeInstance theme,
                                               int baseY, int rx, int rz, int rw, int rd) {
        int cx = rx + rw / 2;
        int cz = rz + rd / 2;
        setBlock(level, new BlockPos(cx, baseY, cz), theme.accent);
        setBlock(level, new BlockPos(cx - 1, baseY, cz), theme.floor);
        setBlock(level, new BlockPos(cx + 1, baseY, cz), theme.floor);
        setBlock(level, new BlockPos(cx, baseY, cz - 1), theme.floor);
        setBlock(level, new BlockPos(cx, baseY, cz + 1), theme.floor);
    }

    private static void buildCoverRoom(ServerLevel level, ThemeManager.ThemeInstance theme,
                                       int baseY, int rx, int rz, int rw, int rd) {
        int cx = rx + rw / 2;
        int cz = rz + rd / 2;
        for (int dx = -2; dx <= 2; dx++) {
            if (dx == 0) continue;
            setBlock(level, new BlockPos(cx + dx, baseY + 1, cz - 2), theme.wall);
            setBlock(level, new BlockPos(cx + dx, baseY + 1, cz + 2), theme.wall);
        }
        setBlock(level, new BlockPos(cx - 2, baseY + 2, cz - 2), theme.accent);
        setBlock(level, new BlockPos(cx + 2, baseY + 2, cz + 2), theme.accent);
    }

    private static void buildConnectorRoom(ServerLevel level, ThemeManager.ThemeInstance theme,
                                           int baseY, int rx, int rz, int rw, int rd) {
        int cx = rx + rw / 2;
        int cz = rz + rd / 2;
        for (int dx = -2; dx <= 2; dx++) {
            setBlock(level, new BlockPos(cx + dx, baseY, cz), theme.accent);
        }
        for (int dz = -2; dz <= 2; dz++) {
            setBlock(level, new BlockPos(cx, baseY, cz + dz), theme.accent);
        }
        setBlock(level, new BlockPos(cx, baseY + ROOM_HEIGHT, cz), theme.light);
    }

    private static boolean buildSupplyCorner(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                             int baseY, int rx, int rz, int rw, int rd,
                                             boolean allowChest, int floor, String instanceId, String mode,
                                             int chestIndex) {
        int sx = rx + 2;
        int sz = rz + rd - 3;
        setBlock(level, new BlockPos(sx, baseY + 1, sz), Blocks.BARREL.defaultBlockState());
        if (allowChest) {
            BlockPos chestPos = new BlockPos(sx + 1, baseY + 1, sz);
            setBlock(level, chestPos, Blocks.CHEST.defaultBlockState());
            markSupplyChest(level, chestPos, floor, chestIndex, instanceId, mode);
        } else {
            setBlock(level, new BlockPos(sx + 1, baseY + 1, sz), theme.decor);
        }
        setBlock(level, new BlockPos(sx, baseY + 2, sz), theme.light);
        setBlock(level, new BlockPos(sx + 2, baseY + 1, sz), theme.wall);
        setBlock(level, new BlockPos(sx + 2, baseY + 2, sz), theme.wall);
        return allowChest;
    }

    private static void markSupplyChest(ServerLevel level, BlockPos pos, int floor, int chestIndex, String instanceId, String mode) {
        runAfterGenerationBlocks(() -> markSupplyChestAfterBlocks(level, pos, floor, chestIndex, instanceId, mode));
    }

    private static void markSupplyChestAfterBlocks(ServerLevel level, BlockPos pos, int floor, int chestIndex, String instanceId, String mode) {
        if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)) return;
        chest.clearContent();
        chest.getPersistentData().putBoolean("TacRogueLootChest", true);
        chest.getPersistentData().putBoolean("TacRogueChestClaimed", false);
        chest.getPersistentData().putInt(com.levanilla.rogue.core.service.ChestLootService.CHEST_INDEX_KEY, Math.max(0, chestIndex));
        com.levanilla.rogue.core.service.FloorInstanceManager.stampBlockEntity(
            chest,
            instanceId,
            floor,
            com.levanilla.rogue.core.service.FloorInstanceManager.EntryMode.parse(mode));
        chest.setCustomName(net.minecraft.network.chat.Component.literal("[ROGUE SUPPLY CACHE]"));
        chest.setChanged();
    }

    private static void stampSupplyChestTotals(ServerLevel level, BlockPos center, int floor, String instanceId, int chestTotal) {
        runAfterGenerationBlocks(() -> stampSupplyChestTotalsAfterBlocks(level, center, floor, instanceId, chestTotal));
    }

    private static void stampSupplyChestTotalsAfterBlocks(ServerLevel level, BlockPos center, int floor,
                                                          String instanceId, int chestTotal) {
        int total = Math.max(1, chestTotal);
        int minY = center.getY();
        int maxY = center.getY() + ROOM_HEIGHT + 2;
        for (int x = center.getX() - CLEAR_RADIUS; x <= center.getX() + CLEAR_RADIUS; x++) {
            for (int z = center.getZ() - CLEAR_RADIUS; z <= center.getZ() + CLEAR_RADIUS; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)) continue;
                    if (!chest.getPersistentData().getBoolean("TacRogueLootChest")) continue;
                    if (floor != chest.getPersistentData().getInt(com.levanilla.rogue.core.service.FloorInstanceManager.FLOOR_KEY)) continue;
                    if (!Objects.equals(instanceId, chest.getPersistentData().getString(com.levanilla.rogue.core.service.FloorInstanceManager.INSTANCE_ID_KEY))) continue;
                    chest.getPersistentData().putInt(com.levanilla.rogue.core.service.ChestLootService.CHEST_TOTAL_KEY, total);
                    chest.setChanged();
                }
            }
        }
    }

    private static void spawnBossAfterBlocks(ServerLevel level, BlockPos pos, int floor, int biomeIndex,
                                             String instanceId, String mode, int participantCount) {
        runAfterGenerationBlocks(() ->
            RoomManager.spawnBoss(level, pos, floor, biomeIndex, instanceId, mode, participantCount));
    }

    private static void spawnMobsAfterBlocks(ServerLevel level, BlockPos pos, int count, int floor, int biomeIndex,
                                             String instanceId, String mode, int participantCount) {
        runAfterGenerationBlocks(() ->
            RoomManager.spawnMobs(level, pos, count, floor, biomeIndex, instanceId, mode, participantCount));
    }

    private static void runAfterGenerationBlocks(Runnable action) {
        GenerationJob job = ACTIVE_GENERATION_JOB.get();
        if (job == null) {
            action.run();
        } else {
            job.addCompletionAction(action);
        }
    }

    private static void buildArenaMarks(ServerLevel level, ThemeManager.ThemeInstance theme,
                                        int baseY, int rx, int rz, int rw, int rd) {
        int cx = rx + rw / 2;
        int cz = rz + rd / 2;
        int radius = Math.max(2, Math.min(rw, rd) / 4);
        for (int d = -radius; d <= radius; d++) {
            setBlock(level, new BlockPos(cx + d, baseY, cz - radius), theme.accent);
            setBlock(level, new BlockPos(cx + d, baseY, cz + radius), theme.accent);
            setBlock(level, new BlockPos(cx - radius, baseY, cz + d), theme.accent);
            setBlock(level, new BlockPos(cx + radius, baseY, cz + d), theme.accent);
        }
    }

    private static void buildLowVisibilityRoom(ServerLevel level, Random rand, ThemeManager.ThemeInstance theme,
                                               int baseY, int rx, int rz, int rw, int rd) {
        int patches = Math.max(2, Math.min(5, (rw + rd) / 7));
        for (int i = 0; i < patches; i++) {
            int px = rx + 2 + rand.nextInt(Math.max(1, rw - 4));
            int pz = rz + 2 + rand.nextInt(Math.max(1, rd - 4));
            setBlock(level, new BlockPos(px, baseY + 1, pz), Blocks.COBWEB.defaultBlockState());
        }
        setBlock(level, new BlockPos(rx + rw / 2, baseY + ROOM_HEIGHT, rz + rd / 2), Blocks.AIR.defaultBlockState());
    }

    private static BlockPos pickMobSpawnInRoom(BlockPos center, int[] room, int baseY, Random rand) {
        int marginX = room[2] >= 8 ? 2 : 1;
        int marginZ = room[3] >= 8 ? 2 : 1;
        int xRange = Math.max(1, room[2] - marginX * 2);
        int zRange = Math.max(1, room[3] - marginZ * 2);
        int x = center.getX() + room[0] + marginX + rand.nextInt(xRange);
        int z = center.getZ() + room[1] + marginZ + rand.nextInt(zRange);
        return new BlockPos(x, baseY + 1, z);
    }

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
        stampCorridor(grid, fx, fz, tx, tz, CORRIDOR_W);
    }

    private static void stampCorridor(int[][] grid, int fx, int fz, int tx, int tz, int corridorWidth) {
        stampCorridor(grid, fx, fz, tx, tz, corridorWidth, CorridorVariant.STRAIGHT);
    }

    private static void stampCorridor(int[][] grid, int fx, int fz, int tx, int tz,
                                      int corridorWidth, CorridorVariant variant) {
        CorridorVariant safeVariant = variant == null ? CorridorVariant.STRAIGHT : variant;
        int width = switch (safeVariant) {
            case WIDE_COVER, LOOP_CONNECTOR -> Math.max(corridorWidth, 5);
            case NARROW_PRESSURE -> Math.max(3, Math.min(corridorWidth, 3));
            default -> corridorWidth;
        };
        if (safeVariant == CorridorVariant.DOGLEG || safeVariant == CorridorVariant.BROKEN_ALCOVE) {
            int midX = fx + (tx - fx) / 2;
            int midZ = fz + (tz - fz) / 2;
            stampCorridorSegmentX(grid, fx, midX, fz, width, safeVariant);
            stampJunction(grid, midX, fz, width);
            stampCorridorSegmentZ(grid, fz, midZ, midX, width, safeVariant);
            stampJunction(grid, midX, midZ, width);
            stampCorridorSegmentX(grid, midX, tx, midZ, width, safeVariant);
            stampJunction(grid, tx, midZ, width);
            stampCorridorSegmentZ(grid, midZ, tz, tx, width, safeVariant);
            return;
        }

        stampCorridorSegmentX(grid, fx, tx, fz, width, safeVariant);
        stampJunction(grid, tx, fz, width);
        stampCorridorSegmentZ(grid, fz, tz, tx, width, safeVariant);
    }

    private static void stampCorridorSegmentX(int[][] grid, int fromX, int toX, int z,
                                              int corridorWidth, CorridorVariant variant) {
        if (fromX == toX) return;
        int halfW = corridorWidth / 2;
        int xDir = fromX < toX ? 1 : -1;
        int step = 0;
        for (int x = fromX; x != toX; x += xDir, step++) {
            int localHalf = corridorHalfWidthAt(halfW, step, variant);
            for (int w = -localHalf; w <= localHalf; w++) {
                stampCorridorCell(grid, x, z + w);
            }
            stampCorridorWall(grid, x, z - localHalf - 1);
            stampCorridorWall(grid, x, z + localHalf + 1);
            stampCorridorAlcoveX(grid, x, z, localHalf, step, variant);
        }
    }

    private static void stampCorridorSegmentZ(int[][] grid, int fromZ, int toZ, int x,
                                              int corridorWidth, CorridorVariant variant) {
        if (fromZ == toZ) return;
        int halfW = corridorWidth / 2;
        int zDir = fromZ < toZ ? 1 : -1;
        int step = 0;
        for (int z = fromZ; z != toZ; z += zDir, step++) {
            int localHalf = corridorHalfWidthAt(halfW, step, variant);
            for (int w = -localHalf; w <= localHalf; w++) {
                stampCorridorCell(grid, x + w, z);
            }
            stampCorridorWall(grid, x - localHalf - 1, z);
            stampCorridorWall(grid, x + localHalf + 1, z);
            stampCorridorAlcoveZ(grid, x, z, localHalf, step, variant);
        }
    }

    private static void stampJunction(int[][] grid, int x, int z, int corridorWidth) {
        int halfW = corridorWidth / 2;
        for (int dx = -halfW - 1; dx <= halfW + 1; dx++) {
            for (int dz = -halfW - 1; dz <= halfW + 1; dz++) {
                boolean isEdge = (Math.abs(dx) == halfW + 1 || Math.abs(dz) == halfW + 1);
                if (isEdge) {
                    stampCorridorWall(grid, x + dx, z + dz);
                } else {
                    stampCorridorCell(grid, x + dx, z + dz);
                }
            }
        }
    }

    private static int corridorHalfWidthAt(int baseHalf, int step, CorridorVariant variant) {
        if (variant == CorridorVariant.WIDE_COVER && step % 11 >= 7) return baseHalf + 1;
        if (variant == CorridorVariant.BROKEN_ALCOVE && step % 13 >= 9) return baseHalf + 1;
        return baseHalf;
    }

    private static void stampCorridorAlcoveX(int[][] grid, int x, int z, int halfW, int step,
                                             CorridorVariant variant) {
        if (variant != CorridorVariant.BROKEN_ALCOVE && variant != CorridorVariant.WIDE_COVER) return;
        if (step % 10 != 5) return;
        int side = (step / 10) % 2 == 0 ? -1 : 1;
        for (int depth = 1; depth <= 2; depth++) {
            stampCorridorCell(grid, x, z + side * (halfW + depth));
            stampCorridorCell(grid, x + 1, z + side * (halfW + depth));
        }
    }

    private static void stampCorridorAlcoveZ(int[][] grid, int x, int z, int halfW, int step,
                                             CorridorVariant variant) {
        if (variant != CorridorVariant.BROKEN_ALCOVE && variant != CorridorVariant.WIDE_COVER) return;
        if (step % 10 != 5) return;
        int side = (step / 10) % 2 == 0 ? -1 : 1;
        for (int depth = 1; depth <= 2; depth++) {
            stampCorridorCell(grid, x + side * (halfW + depth), z);
            stampCorridorCell(grid, x + side * (halfW + depth), z + 1);
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
        // 中心スポーン部屋（ボス時: 24x24 で巨体ボスの移動空間を確保）
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
                setBlock(level, pos.offset(dx, -1, dz), theme.floor);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    setBlock(level, pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /**
     * 通常は前回生成時のフットプリントだけを消す。
     * 履歴がない場合は従来の固定範囲クリアにフォールバックする。
     */
    private static void clearPreviousDungeon(ServerLevel level, BlockPos center) {
        DungeonFootprint footprint = removeDungeonFootprint(level, center);
        if (footprint != null && !footprint.isEmpty()) {
            int clearedBlocks = clearFootprint(level, footprint);
            clearDroppedItemsWhenReady(level, footprint.toAabb(1));
            if (isVerboseLogging()) {
                LOGGER.info("[TacRogue] Cleared dungeon footprint at {} ({} tracked, {} cleared)",
                    center, footprint.blockCount(), clearedBlocks);
            } else {
                LOGGER.debug("[TacRogue] Cleared dungeon footprint at {} ({} tracked, {} cleared)",
                    center, footprint.blockCount(), clearedBlocks);
            }
            if (ACTIVE_GENERATION_JOB.get() == null) {
                flushMapVisibleChunkUpdates(level, footprint);
            }
            return;
        }

        clearPreviousDungeonFallback(level, center);
    }

    private static DungeonFootprint removeDungeonFootprint(ServerLevel level, BlockPos center) {
        synchronized (DUNGEON_FOOTPRINTS) {
            return DUNGEON_FOOTPRINTS.remove(DungeonKey.of(level, center));
        }
    }

    private static void storeDungeonFootprint(ServerLevel level, BlockPos center, DungeonFootprint footprint) {
        if (footprint.isEmpty()) return;
        synchronized (DUNGEON_FOOTPRINTS) {
            DUNGEON_FOOTPRINTS.put(DungeonKey.of(level, center), footprint);
        }
    }

    private static void clearPreviousDungeonFallback(ServerLevel level, BlockPos center) {
        int r = CLEAR_RADIUS; // wallRの分 + 1マージン
        int cx = center.getX();
        int cz = center.getZ();
        int minY = center.getY() + CLEAR_MIN_Y_OFFSET;
        int maxY = center.getY() + CLEAR_MAX_Y_OFFSET;
        int clearedBlocks = 0;
        if (isVerboseLogging()) {
            LOGGER.info("[TacRogue] Fallback clearing dungeon area at {} (radius {})", center, r);
        } else {
            LOGGER.debug("[TacRogue] Fallback clearing dungeon area at {} (radius {})", center, r);
        }

        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (clearDungeonBlock(level, new BlockPos(x, y, z))) clearedBlocks++;
                }
            }
        }

        clearDroppedItemsWhenReady(level, new net.minecraft.world.phys.AABB(
            cx - r, minY, cz - r, cx + r + 1, maxY + 1, cz + r + 1));
        LOGGER.debug("[TacRogue] Fallback cleared {} dungeon blocks at {}", clearedBlocks, center);
    }

    private static int clearFootprint(ServerLevel level, DungeonFootprint footprint) {
        int clearedBlocks = 0;
        for (BlockPos pos : footprint.blocks()) {
            if (clearDungeonBlock(level, pos)) clearedBlocks++;
        }
        return clearedBlocks;
    }

    private static boolean clearDungeonBlock(ServerLevel level, BlockPos pos) {
        GenerationJob job = ACTIVE_GENERATION_JOB.get();
        if (job != null && job.isQueuedForAir(pos)) return false;

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (job != null) {
            job.addBlock(pos, Blocks.AIR.defaultBlockState());
            return true;
        }
        if (state.hasBlockEntity()) {
            clearContainerIfPresent(level, pos);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), SET_BLOCK_FLAGS);
        return true;
    }

    private static void clearDroppedItemsWhenReady(ServerLevel level, net.minecraft.world.phys.AABB area) {
        GenerationJob job = ACTIVE_GENERATION_JOB.get();
        if (job != null) {
            job.addCompletionAction(() -> clearDroppedItems(level, area));
            return;
        }
        clearDroppedItems(level, area);
    }

    private static void clearDroppedItems(ServerLevel level, net.minecraft.world.phys.AABB area) {
        for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)) {
            item.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }

    private static void clearContainerIfBlockEntityPossible(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).hasBlockEntity()) {
            clearContainerIfPresent(level, pos);
        }
    }

    private static void clearContainerIfPresent(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            container.clearContent();
            blockEntity.setChanged();
            level.removeBlockEntity(pos);
        }
    }

    private static void setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        recordTouchedBlock(pos);
        GenerationJob job = ACTIVE_GENERATION_JOB.get();
        if (job != null) {
            job.addBlock(pos, state);
            return;
        }
        placeBlockNow(level, pos, state);
    }

    private static void placeBlockNow(ServerLevel level, BlockPos pos, BlockState state) {
        clearContainerIfBlockEntityPossible(level, pos);
        level.setBlock(pos, state, SET_BLOCK_FLAGS);
    }

    private static void flushMapVisibleChunkUpdates(ServerLevel level, DungeonFootprint footprint) {
        if (level == null || footprint == null || footprint.isEmpty()) return;
        int minChunkX = Math.floorDiv(footprint.minX, 16);
        int maxChunkX = Math.floorDiv(footprint.maxX, 16);
        int minChunkZ = Math.floorDiv(footprint.minZ, 16);
        int maxChunkZ = Math.floorDiv(footprint.maxZ, 16);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    chunk.setUnsaved(true);
                    level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
                }
            }
        }
    }

    private static boolean isVerboseLogging() {
        return com.levanilla.rogue.core.RogueConfig.debugLogs();
    }

    private static float readFloatProperty(String key, float fallback, float min, float max) {
        try {
            String value = System.getProperty(key);
            if (value == null || value.isBlank()) return fallback;
            return Math.max(min, Math.min(max, Float.parseFloat(value)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void recordTouchedBlock(BlockPos pos) {
        DungeonFootprint footprint = ACTIVE_FOOTPRINT.get();
        if (footprint != null) {
            footprint.record(pos);
        }
    }

    private static final class DungeonKey {
        private final String dimension;
        private final int x;
        private final int y;
        private final int z;

        private DungeonKey(String dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static DungeonKey of(ServerLevel level, BlockPos center) {
            return new DungeonKey(level.dimension().location().toString(),
                center.getX(), center.getY(), center.getZ());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof DungeonKey other)) return false;
            return x == other.x && y == other.y && z == other.z && Objects.equals(dimension, other.dimension);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension, x, y, z);
        }
    }

    private static final class DungeonFootprint {
        private final Set<BlockPos> blocks = new HashSet<>();
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        private DungeonFootprint() {
        }

        private void record(BlockPos pos) {
            BlockPos immutablePos = pos.immutable();
            if (!blocks.add(immutablePos)) return;

            int x = immutablePos.getX();
            int y = immutablePos.getY();
            int z = immutablePos.getZ();
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        private boolean isEmpty() {
            return blocks.isEmpty();
        }

        private int blockCount() {
            return blocks.size();
        }

        private Set<BlockPos> blocks() {
            return blocks;
        }

        private net.minecraft.world.phys.AABB toAabb(int margin) {
            return new net.minecraft.world.phys.AABB(
                minX - margin, minY - margin, minZ - margin,
                maxX + margin + 1, maxY + margin + 1, maxZ + margin + 1);
        }
    }
}
