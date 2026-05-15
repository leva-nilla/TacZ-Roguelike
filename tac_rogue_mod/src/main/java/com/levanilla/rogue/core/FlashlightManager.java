package com.levanilla.rogue.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * タクティカルフラッシュライト: プレイヤーの視線方向に拡散するライトブロックを配置。
 * Qキーでトグル。正面の扇形(5-7ブロック先)に複数のライトブロックを配置し、
 * プレイヤーの移動・視点変更に追従する。
 */
@Mod.EventBusSubscriber(modid = "tac_rogue")
public class FlashlightManager {

    /** プレイヤーごとのフラッシュライト有効状態 */
    private static final Map<UUID, Boolean> flashlightEnabled = new ConcurrentHashMap<>();
    /** プレイヤーごとの設置済みライトブロック位置 */
    private static final Map<UUID, List<BlockPos>> lastLightPositions = new ConcurrentHashMap<>();

    /** ライトの輝度レベル */
    private static final int CENTER_LIGHT = 15;
    private static final int EDGE_LIGHT = 10;
    /** ビームの基本長（ブロック） */
    private static final int BEAM_LENGTH = 7;
    /** ビームの拡散幅（端でのオフセット） */
    private static final double SPREAD = 2.5;

    /** フラッシュライトのON/OFFを切り替え */
    public static boolean toggle(UUID playerId) {
        boolean current = flashlightEnabled.getOrDefault(playerId, false);
        flashlightEnabled.put(playerId, !current);
        return !current;
    }

    /** フラッシュライトが有効かどうか */
    public static boolean isEnabled(UUID playerId) {
        return flashlightEnabled.getOrDefault(playerId, false);
    }

    /** プレイヤーログアウト時のクリーンアップ */
    public static void cleanup(UUID playerId) {
        flashlightEnabled.remove(playerId);
        lastLightPositions.remove(playerId);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (var server : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            UUID uuid = server.getUUID();

            // OFFの場合、残存ライトを除去
            if (!isEnabled(uuid)) {
                clearLights(server, uuid);
                continue;
            }

            // ローグダンジョン内でのみ動作
            if (!server.level().dimension().location().getNamespace().equals("tac_rogue")) {
                continue;
            }

            // 3tickに1回更新（パフォーマンス対策）
            if (server.tickCount % 3 != 0) continue;

            updateFlashlight(server, uuid);
        }
    }

    /**
     * 視線方向に扇形のライトブロック群を配置。
     * 中心線 + 左右に1ブロックずつオフセットした3列のビーム。
     */
    private static void updateFlashlight(ServerPlayer player, UUID uuid) {
        // 古いライトを除去
        clearLights(player, uuid);

        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookDir = player.getLookAngle().normalize();

        // 水平方向に制限（Y成分を抑える）
        Vec3 flatDir = new Vec3(lookDir.x, lookDir.y * 0.3, lookDir.z).normalize();

        // 左右方向ベクトル（水平面上）
        Vec3 rightDir = new Vec3(-flatDir.z, 0, flatDir.x).normalize();

        List<BlockPos> newPositions = new ArrayList<>();

        int upgradeLevel = player.getPersistentData().getInt("TacRogueFlashlightLevel");
        int beamLength = BEAM_LENGTH + upgradeLevel * 2;
        double maxSpread = SPREAD + upgradeLevel * 0.35D;

        // ビームのサンプルポイント: 中距離から最大射程まで中心+左右
        int[] distances = {2, Math.max(3, beamLength / 2), Math.max(5, beamLength - 2), beamLength};
        double[] spreads = {0.0, 0.8, Math.min(maxSpread, 1.6 + upgradeLevel * 0.25D), maxSpread};

        for (int di = 0; di < distances.length; di++) {
            int dist = distances[di];
            double spread = spreads[di];
            int lightLevel = (di < 2) ? CENTER_LIGHT : EDGE_LIGHT;

            // 中心
            Vec3 centerPoint = eyePos.add(flatDir.scale(dist));
            tryPlaceLight(player, centerPoint, lightLevel, newPositions);

            // 左右拡散
            if (spread > 0) {
                Vec3 leftPoint = centerPoint.add(rightDir.scale(-spread));
                Vec3 rightPoint = centerPoint.add(rightDir.scale(spread));
                tryPlaceLight(player, leftPoint, EDGE_LIGHT, newPositions);
                tryPlaceLight(player, rightPoint, EDGE_LIGHT, newPositions);
            }
        }

        lastLightPositions.put(uuid, newPositions);
    }

    /**
     * 指定位置にライトブロックを配置（空気ブロックの場合のみ）。
     */
    private static void tryPlaceLight(ServerPlayer player, Vec3 pos, int lightLevel, List<BlockPos> positions) {
        BlockPos bp = BlockPos.containing(pos.x, pos.y, pos.z);

        // 範囲チェック（プレイヤーから離れすぎないように）
        int beamLength = BEAM_LENGTH + player.getPersistentData().getInt("TacRogueFlashlightLevel") * 2;
        if (bp.distSqr(player.blockPosition()) > (beamLength + 3) * (beamLength + 3)) return;

        // 同じ位置に既にあるなら重複しない
        if (positions.contains(bp)) return;

        BlockState currentState = player.level().getBlockState(bp);
        if (currentState.isAir()) {
            BlockState lightState = Blocks.LIGHT.defaultBlockState()
                .setValue(BlockStateProperties.LEVEL, lightLevel);
            player.level().setBlockAndUpdate(bp, lightState);
            positions.add(bp);
        }
    }

    /** 既存のライトブロックを全除去 */
    private static void clearLights(ServerPlayer player, UUID uuid) {
        List<BlockPos> oldPositions = lastLightPositions.remove(uuid);
        if (oldPositions == null) return;
        for (BlockPos pos : oldPositions) {
            if (player.level().getBlockState(pos).is(Blocks.LIGHT)) {
                player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearLights(player, player.getUUID());
            cleanup(player.getUUID());
        }
    }
}
