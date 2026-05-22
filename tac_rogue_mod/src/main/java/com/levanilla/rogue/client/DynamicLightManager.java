package com.levanilla.rogue.client;

import com.levanilla.rogue.core.GameConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クライアント側の動的ライト管理。
 * ライトブロックを配置せず、Mixin でレンダリング時の光量を差し替える方式。
 * RayTrace (ClipContext) を追加し、リアルな照射システムを導入。
 */
public final class DynamicLightManager {

    private DynamicLightManager() {}

    /** 動的ライト位置 → 輝度レベル。Mixin から参照される。 */
    private static final Map<BlockPos, Integer> lightPositions = new ConcurrentHashMap<>();

    /** フラッシュライト有効フラグ (UUID → enabled) */
    private static final Map<UUID, Boolean> flashlightEnabled = new ConcurrentHashMap<>();

    /** 前回のプレイヤー位置・視線 (変化検知用) */
    private static Vec3 lastEyePos = Vec3.ZERO;
    private static Vec3 lastLookDir = Vec3.ZERO;

    /** ビームの長さ（ブロック） */
    private static final int BEAM_LENGTH = 7;
    /** ビームの拡散幅 */
    private static final double SPREAD = 2.5;
    /** 位置変化の閾値 (これ未満なら更新スキップ) */
    private static final double MOVE_THRESHOLD_SQ = 0.01;
    /** 視線変化の閾値 */
    private static final double LOOK_THRESHOLD_SQ = 0.005;

    /** フラッシュライトのON/OFFを切り替え */
    public static int getActiveLightCount() {
        return lightPositions.size();
    }

    public static boolean toggle() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        UUID uuid = mc.player.getUUID();
        boolean current = flashlightEnabled.getOrDefault(uuid, false);
        flashlightEnabled.put(uuid, !current);
        if (current) {
            lightPositions.clear(); // OFF時はクリア
        } else {
            lastEyePos = Vec3.ZERO; // ONにした瞬間に強制更新させるためリセット
            lastLookDir = Vec3.ZERO;
        }
        return !current;
    }

    /** フラッシュライトが有効かどうか */
    public static boolean isEnabled() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return flashlightEnabled.getOrDefault(mc.player.getUUID(), false);
    }

    /**
     * 毎クライアントティックで呼ばれるライト位置更新。
     * プレイヤーの位置・視線が変化した場合のみ再計算する。
     */
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isEnabled()) {
            if (!lightPositions.isEmpty()) lightPositions.clear();
            return;
        }

        // ローグダンジョン内でのみ動作
        net.minecraft.resources.ResourceLocation dimension = mc.player.level().dimension().location();
        if (!"tac_rogue".equals(dimension.getNamespace()) || !"rogue_dimension".equals(dimension.getPath())) {
            if (!lightPositions.isEmpty()) lightPositions.clear();
            return;
        }

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 lookDir = mc.player.getLookAngle();

        // 変化検知: 位置・視線が閾値未満なら更新スキップ
        if (eyePos.distanceToSqr(lastEyePos) < MOVE_THRESHOLD_SQ
            && lookDir.distanceToSqr(lastLookDir) < LOOK_THRESHOLD_SQ) {
            return;
        }
        lastEyePos = eyePos;
        lastLookDir = lookDir;

        // ビーム計算
        Map<BlockPos, Integer> newPositions = new HashMap<>();

        int upgradeLevel = com.levanilla.rogue.core.ClientRunState.getFlashlightLevel();
        int beamLength = BEAM_LENGTH + upgradeLevel * 2;
        double maxSpread = SPREAD + upgradeLevel * 0.35D;

        // ClipContext (RayTrace) プレイヤーの目からbeamLength先まで
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
            eyePos, eyePos.add(lookDir.scale(beamLength)),
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            mc.player
        );
        net.minecraft.world.phys.HitResult hit = mc.level.clip(context);

        // 中心点の計算 (壁に当たった場合はその手前、当たらなければBEAM_LENGTH先)
        Vec3 hitVec = hit.getLocation();
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            // 壁の中に埋まらないように少し手前に引く
            hitVec = hitVec.subtract(lookDir.scale(0.1));
        }

        int lightLevel = Math.min(15, GameConstants.FLASHLIGHT_LIGHT_LEVEL + upgradeLevel / 2);

        // 右・上方向ベクトルの計算 (拡散用)
        Vec3 rightDir = new Vec3(-lookDir.z, 0, lookDir.x).normalize();
        if (Double.isNaN(rightDir.x) || Math.abs(lookDir.y) > 0.95) {
            // ほぼ真上・真下を向いている場合のフォールバック
            rightDir = new Vec3(1, 0, 0);
        }
        Vec3 upDir = rightDir.cross(lookDir).normalize();

        // 1. 中心点の光源
        addLightSafe(mc, hitVec, lightLevel, newPositions);

        // 2. 距離に応じた拡散光の計算
        double distToHit = eyePos.distanceTo(hitVec);
        double spread = Math.min(maxSpread, distToHit * 0.35); // 遠いほど広がる

        if (spread > 0.5) {
            // 壁に丸く当たるように上下左右に配置
            addLightSafe(mc, hitVec.add(rightDir.scale(spread)), 10, newPositions);
            addLightSafe(mc, hitVec.add(rightDir.scale(-spread)), 10, newPositions);
            addLightSafe(mc, hitVec.add(upDir.scale(spread)), 10, newPositions);
            addLightSafe(mc, hitVec.add(upDir.scale(-spread)), 10, newPositions);

            // 斜め方向も追加してより滑らかな円の光に
            double diag = spread * 0.7;
            addLightSafe(mc, hitVec.add(rightDir.scale(diag)).add(upDir.scale(diag)), 8, newPositions);
            addLightSafe(mc, hitVec.add(rightDir.scale(-diag)).add(upDir.scale(diag)), 8, newPositions);
            addLightSafe(mc, hitVec.add(rightDir.scale(diag)).add(upDir.scale(-diag)), 8, newPositions);
            addLightSafe(mc, hitVec.add(rightDir.scale(-diag)).add(upDir.scale(-diag)), 8, newPositions);

            // 壁とプレイヤーの間の中間地点にも光源を配置し、ビームの筋を表現
            addLightSafe(mc, eyePos.add(lookDir.scale(distToHit * 0.6)), 8, newPositions);
        } else {
            // プレイヤーが壁に近すぎる場合は、手元のみ明るくする
            addLightSafe(mc, eyePos.add(lookDir.scale(distToHit * 0.5)), 12, newPositions);
        }

        // 3. 手元自身の明るさ（暗闇での完全な視認性喪失を防ぐ）
        addLightSafe(mc, eyePos.subtract(lookDir.scale(0.3)), 8, newPositions);
        addLightSafe(mc, eyePos, 10, newPositions); // 追加: 壁密着や真下向き時にライトが消えるのを防ぐため、目自身を照らす

        lightPositions.clear();
        lightPositions.putAll(newPositions);
    }

    private static void addLightSafe(Minecraft mc, Vec3 pos, int lightLevel, Map<BlockPos, Integer> positions) {
        BlockPos bp = BlockPos.containing(pos.x, pos.y, pos.z);
        if (mc.level != null) {
            net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(bp);
            // 透過性があるブロック（空気、水、草、ガラス等）なら光を通すため配置可能
            if (!state.isSolidRender(mc.level, bp) || state.getLightBlock(mc.level, bp) < 15) {
                positions.merge(bp, lightLevel, Math::max);
            }
        }
    }

    /**
     * 指定位置の動的ライトレベルを取得。
     * Mixin (MixinClientLevel) から呼ばれる。
     * @return 動的ライトレベル。動的ライトがなければ 0。
     */
    public static int getDynamicLightAt(BlockPos pos) {
        Integer level = lightPositions.get(pos);
        return level != null ? level : 0;
    }

    public static boolean hasActiveLights() {
        return !lightPositions.isEmpty();
    }

    /** クリーンアップ */
    public static void cleanup() {
        lightPositions.clear();
        flashlightEnabled.clear();
        lastEyePos = Vec3.ZERO;
        lastLookDir = Vec3.ZERO;
    }
}
