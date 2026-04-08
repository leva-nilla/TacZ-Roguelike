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
        PlayerRunData data = RunManager.getData(player);

        if (!data.isRunActive() && !data.isFloorCleared() && data.getCurrentFloor() <= 0) {
            // 初回：ラン開始
            startNewRun(player);
        } else if (data.isFloorCleared()) {
            // フロアクリア済み → 次フロアへ
            RunManager.startNextFloor(player);
        } else {
            // フロア未クリア → リトライ
            retryCurrentFloor(player);
        }
    }

    /**
     * 明示的なリトライ要求。
     * マルチプレイヤー: 同フロアに他プレイヤーがいる場合は再生成せずスポーンテレポートのみ。
     */
    public static void handleRetryFloor(ServerPlayer player) {
        PlayerRunData data = RunManager.getData(player);
        player.sendSystemMessage(Component.literal(
            "\u00a7e[RETRY] Retrying floor " + data.getCurrentFloor()));
        data.setFloorCleared(false);
        data.setRunActive(true);
        retryCurrentFloor(player);
    }

    /** 過去のフロアに飛んで再試行する（金策用） */
    public static void handleGotoFloor(ServerPlayer player, int targetFloor) {
        PlayerRunData data = RunManager.getData(player);
        if (targetFloor > 0 && targetFloor <= data.getMaxReachedFloor()) {
            RunManager.gotoFloor(player, targetFloor);
        } else {
            player.sendSystemMessage(Component.literal("§c[ERROR] Invalid floor selection: " + targetFloor));
        }
    }

    // ===== 内部ロジック =====

    private static void startNewRun(ServerPlayer player) {
        RunManager.startRun(player);
        PlayerRunData data = RunManager.getData(player);
        ServerLevel rogueLevel = player.server.getLevel(ROGUE_DIM);
        if (rogueLevel != null) {
            clearDungeonEntities(rogueLevel, data.getDungeonOrigin());
            BlockPos spawnPos = MapGenerator.generateRoom(
                rogueLevel, data.getDungeonOrigin(), null, data.getCurrentFloor(), data.getRunSeed());
            RunManager.safeTeleport(player, rogueLevel, spawnPos);
            RunManager.syncPlayer(player);
            CurrencyManager.addGold(player, GameConstants.INITIAL_GOLD);
            RunManager.syncPlayer(player);
        }
    }

    private static void retryCurrentFloor(ServerPlayer player) {
        PlayerRunData data = RunManager.getData(player);
        ServerLevel rogueLevel = player.server.getLevel(ROGUE_DIM);
        if (rogueLevel != null) {
            // マルチプレイヤー: 同フロアに他プレイヤーがいる場合は再生成しない
            if (RunManager.hasOtherPlayersOnSameFloor(player)) {
                // スポーン位置にテレポートするだけ
                BlockPos origin = data.getDungeonOrigin();
                BlockPos spawnPos = origin.above(2);
                RunManager.safeTeleport(player, rogueLevel, spawnPos);
                data.setFloorCleared(false);
                data.setRunActive(true);
                data.setFloorStartTick(player.server.getTickCount());
                RunManager.syncPlayer(player);
                player.sendSystemMessage(Component.literal(
                    "§e[RETRY] Teleported to spawn — other players on this floor"));
            } else {
                clearDungeonEntities(rogueLevel, data.getDungeonOrigin());

                BlockPos spawnPos = MapGenerator.generateRoom(
                    rogueLevel, data.getDungeonOrigin(), null, data.getCurrentFloor(), data.getRunSeed());
                RunManager.safeTeleport(player, rogueLevel, spawnPos);
                data.setFloorCleared(false);
                data.setRunActive(true);
                data.setFloorStartTick(player.server.getTickCount());
                RunManager.syncPlayer(player);
                player.sendSystemMessage(Component.literal(
                    "§e[RETRY] FLOOR " + data.getCurrentFloor() + " — 再生成完了"));
            }
        }
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
