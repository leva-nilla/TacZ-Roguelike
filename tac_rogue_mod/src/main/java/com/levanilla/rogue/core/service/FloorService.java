package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.world.MapGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;

/**
 * フロア進行（開始・リトライ・次フロア）のビジネスロジック。
 * v1.0.0: プレイヤー個別管理対応。
 */
public final class FloorService {

    private FloorService() {}

    /**
     * 「次のフロアへ / 再挑戦 / 初回開始」の振り分けロジック。
     */
    public static void handleStartNextFloor(ServerPlayer player) {
        handleStartNextFloor(player, FloorInstanceManager.EntryMode.SOLO);
    }

    public static void handleStartNextFloor(ServerPlayer player, FloorInstanceManager.EntryMode mode) {
        if (FloorInstanceManager.requestStartWaitingInstance(player)) {
            return;
        }

        PlayerRunData data = RunManager.getData(player);

        if (!data.isRunActive() && !data.isFloorCleared() && data.getCurrentFloor() <= 0) {
            // 初回：ラン開始
            startNewRun(player, mode);
        } else if (data.isFloorCleared()) {
            // フロアクリア済み → 次フロアへ
            int targetFloor = Math.max(data.getCurrentFloor() + 1, data.getMaxReachedFloor());
            data.setCurrentFloor(targetFloor);
            data.setFloorCleared(false);
            data.setRunActive(false);
            data.startFloorAttempt(player.server.getTickCount());
            RunManager.refreshThemeName(data);
            parkInRogueStagingWhileRegenerating(player, data.getDungeonOrigin());
            FloorInstanceManager.enterFloor(player, targetFloor, mode);
        } else {
            // フロア未クリア → リトライ
            retryCurrentFloor(player, mode);
        }
    }

    /**
     * 明示的なリトライ要求。
     * プレイヤーごとに独立した生成領域を持つため、常に自分の領域を再生成する。
     */
    public static void handleRetryFloor(ServerPlayer player) {
        handleRetryFloor(player, FloorInstanceManager.EntryMode.SOLO);
    }

    public static void handleRetryFloor(ServerPlayer player, FloorInstanceManager.EntryMode mode) {
        PlayerRunData data = RunManager.getData(player);
        player.sendSystemMessage(Component.literal(
            "\u00a7e[RETRY] Retrying floor " + data.getCurrentFloor()));
        data.setFloorCleared(false);
        data.setRunActive(false);
        retryCurrentFloor(player, mode);
    }

    /** 過去のフロアに飛んで再試行する（金策用） */
    public static void handleGotoFloor(ServerPlayer player, int targetFloor) {
        handleGotoFloor(player, targetFloor, FloorInstanceManager.EntryMode.SOLO);
    }

    public static void handleGotoFloor(ServerPlayer player, int targetFloor, FloorInstanceManager.EntryMode mode) {
        PlayerRunData data = RunManager.getData(player);
        if (targetFloor > 0 && targetFloor <= Math.max(1, data.getMaxReachedFloor())) {
            if (data.getCurrentFloor() <= 0) {
                RunManager.startRun(player);
                CurrencyManager.addGoldNoQuest(player, GameConstants.INITIAL_GOLD);
            }
            data.setCurrentFloor(targetFloor);
            data.setFloorCleared(false);
            data.setRunActive(false);
            data.startFloorAttempt(player.server.getTickCount());
            RunManager.refreshThemeName(data);
            parkInRogueStagingWhileRegenerating(player, data.getDungeonOrigin());
            FloorInstanceManager.enterFloor(player, targetFloor, mode);
        } else {
            player.sendSystemMessage(Component.literal("§c[ERROR] Invalid floor selection: " + targetFloor));
        }
    }

    // ===== 内部ロジック =====

    private static void startNewRun(ServerPlayer player, FloorInstanceManager.EntryMode mode) {
        RunManager.startRun(player);
        PlayerRunData data = RunManager.getData(player);
        CurrencyManager.addGoldNoQuest(player, GameConstants.INITIAL_GOLD);
        data.setRunActive(false);
        FloorInstanceManager.enterFloor(player, data.getCurrentFloor(), mode);
        RunManager.syncPlayer(player);
    }

    private static void retryCurrentFloor(ServerPlayer player, FloorInstanceManager.EntryMode mode) {
        PlayerRunData data = RunManager.getData(player);
        data.nextFloorAttempt(player.server.getTickCount());
        data.setFloorCleared(false);
        data.setRunActive(false);
        parkInRogueStagingWhileRegenerating(player, data.getDungeonOrigin());
        FloorInstanceManager.enterFloor(player, Math.max(1, data.getCurrentFloor()), mode);
        player.sendSystemMessage(Component.literal(
            "§e[RETRY] FLOOR " + data.getCurrentFloor() + " — regeneration queued"));
    }

    private static void parkInRogueStagingWhileRegenerating(ServerPlayer player, BlockPos origin) {
        if (player == null || player.server == null || player.level().dimension() != ROGUE_DIM) return;
        ServerLevel rogueLevel = player.server.getLevel(ROGUE_DIM);
        if (rogueLevel == null || origin == null) return;
        BlockPos center = retryStagingCenter(origin);
        buildRetryStaging(rogueLevel, center);
        RunManager.safeTeleport(player, rogueLevel, center);
    }

    public static void clearRetryStaging(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null) return;
        BlockPos center = retryStagingCenter(origin);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BARRIER)
                        || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LIGHT)
                        || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BLACK_CONCRETE)) {
                        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
    }

    private static BlockPos retryStagingCenter(BlockPos origin) {
        return new BlockPos(origin.getX(), origin.getY() + 28, origin.getZ());
    }

    private static void buildRetryStaging(ServerLevel level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    boolean shell = dx == -2 || dx == 2 || dz == -2 || dz == 2 || dy == -1 || dy == 3;
                    level.setBlock(center.offset(dx, dy, dz),
                        shell
                            ? net.minecraft.world.level.block.Blocks.BLACK_CONCRETE.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        2 | 16);
                }
            }
        }
        level.setBlock(center.offset(0, 2, 0),
            net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 2 | 16);
    }

    public static void clearDungeonEntities(ServerLevel rogueLevel, BlockPos origin) {
        // チャンクがアンロードされていて前回の敵(ボス含む)が消えないバグ対策として
        // 以前はここで周辺900チャンクの強制同期ロードを行っていたが、数秒の深刻なラグ(TPS低下)の原因になっていた。
        // ※現在は SpawnAndWorldHandler に「亡霊処理システム (TacRogueSpawnTick)」が入ったため、
        // アンロードのまま残った敵は、目覚めた瞬間に自壊するようになり、重い強制ロードは完全に不要になった。
        
        int rad = (int) GameConstants.FLOOR_CLEAR_RADIUS;

        // 単独: 既存モブ・ボスを全消去して再生成
        net.minecraft.world.phys.AABB killArea = new net.minecraft.world.phys.AABB(origin).inflate(rad);
        java.util.List<net.minecraft.world.entity.LivingEntity> entitiesToKill = rogueLevel.getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class, killArea);
        
        for (net.minecraft.world.entity.LivingEntity e : entitiesToKill) {
            if (!(e instanceof net.minecraft.world.entity.player.Player)) {
                e.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        java.util.List<net.minecraft.world.entity.item.ItemEntity> looseItems = rogueLevel.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class, killArea);
        for (net.minecraft.world.entity.item.ItemEntity item : looseItems) {
            item.remove(Entity.RemovalReason.DISCARDED);
        }

        // 追加安全策: tac_rogue_spawned タグ付きの全エンティティも消去
        for (net.minecraft.world.entity.Entity entity : rogueLevel.getAllEntities()) {
            if (!(entity instanceof net.minecraft.world.entity.player.Player) && entity.getTags().contains("tac_rogue_spawned")) {
                double dist = entity.distanceToSqr(origin.getX(), origin.getY(), origin.getZ());
                if (dist < rad * rad) {
                    entity.remove(Entity.RemovalReason.DISCARDED);
                }
            }
        }
    }
}
