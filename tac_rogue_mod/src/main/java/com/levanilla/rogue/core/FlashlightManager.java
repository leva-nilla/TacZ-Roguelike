package com.levanilla.rogue.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative flashlight.
 *
 * Client-side brightness mixins are deliberately not used: real minecraft:light
 * blocks are sparse-placed along the beam so shader pipelines can see them.
 */
@Mod.EventBusSubscriber(modid = "tac_rogue")
public final class FlashlightManager {

    private FlashlightManager() {}

    private static final Map<UUID, Boolean> flashlightEnabled = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<LightKey>> playerLights = new ConcurrentHashMap<>();
    private static final Map<LightKey, Set<UUID>> lightOwners = new ConcurrentHashMap<>();
    private static final Map<UUID, BeamState> lastBeamStates = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> lastUpdateTicks = new ConcurrentHashMap<>();

    private static final int BASE_BEAM_LENGTH = 8;
    private static final int BASE_CENTER_LIGHT = 15;
    private static final int MAX_LIGHTS_PER_PLAYER = 18;
    private static final int UPDATE_INTERVAL_TICKS = 4;
    private static final int LIGHT_BLOCK_FLAGS = 2 | 16;

    public static boolean toggle(UUID playerId) {
        boolean enabled = !flashlightEnabled.getOrDefault(playerId, false);
        flashlightEnabled.put(playerId, enabled);
        lastBeamStates.remove(playerId);
        lastUpdateTicks.remove(playerId);
        return enabled;
    }

    public static boolean isEnabled(UUID playerId) {
        return flashlightEnabled.getOrDefault(playerId, false);
    }

    public static void cleanup(UUID playerId) {
        flashlightEnabled.remove(playerId);
        lastBeamStates.remove(playerId);
        lastUpdateTicks.remove(playerId);
        playerLights.remove(playerId);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (!isEnabled(uuid) || !isRogueDungeon(player)) {
                clearPlayerLights(server, uuid);
                lastBeamStates.remove(uuid);
                lastUpdateTicks.remove(uuid);
                continue;
            }

            updateFlashlight(player, uuid);
        }
    }

    private static void updateFlashlight(ServerPlayer player, UUID uuid) {
        ServerLevel level = player.serverLevel();
        int upgradeLevel = Math.max(0, player.getPersistentData().getInt("TacRogueFlashlightLevel"));
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookDir = player.getLookAngle().normalize();

        BeamState state = new BeamState(
            level.dimension(),
            BlockPos.containing(eyePos),
            Math.round(player.getYRot() / 10.0F),
            Math.round(player.getXRot() / 10.0F),
            upgradeLevel
        );

        int tick = player.tickCount;
        if (state.equals(lastBeamStates.get(uuid))
            && tick - lastUpdateTicks.getOrDefault(uuid, -UPDATE_INTERVAL_TICKS) < UPDATE_INTERVAL_TICKS) {
            return;
        }

        if (state.equals(lastBeamStates.get(uuid)) && playerLights.containsKey(uuid)) {
            lastUpdateTicks.put(uuid, tick);
            return;
        }

        LinkedHashMap<BlockPos, Integer> targetLights = computeBeamLights(player, level, eyePos, lookDir, upgradeLevel);
        reconcileLights(player.server, uuid, level, targetLights);
        lastBeamStates.put(uuid, state);
        lastUpdateTicks.put(uuid, tick);
    }

    private static LinkedHashMap<BlockPos, Integer> computeBeamLights(
        ServerPlayer player,
        ServerLevel level,
        Vec3 eyePos,
        Vec3 lookDir,
        int upgradeLevel
    ) {
        LinkedHashMap<BlockPos, Integer> targets = new LinkedHashMap<>();
        int beamLength = BASE_BEAM_LENGTH + upgradeLevel * 2;
        int midLight = Math.min(14, 10 + upgradeLevel);
        int edgeLight = Math.min(12, 8 + upgradeLevel);
        int ambientLight = Math.min(9, 6 + upgradeLevel / 2);

        ClipContext context = new ClipContext(
            eyePos,
            eyePos.add(lookDir.scale(beamLength)),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        );
        HitResult hit = level.clip(context);
        Vec3 hitPos = hit.getType() == HitResult.Type.MISS
            ? eyePos.add(lookDir.scale(beamLength))
            : hit.getLocation().subtract(lookDir.scale(0.18D));
        double hitDistance = Math.max(1.25D, Math.min(beamLength, eyePos.distanceTo(hitPos)));

        Vec3 rightDir = new Vec3(-lookDir.z, 0.0D, lookDir.x);
        if (rightDir.lengthSqr() < 0.0001D) {
            rightDir = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            rightDir = rightDir.normalize();
        }
        Vec3 upDir = rightDir.cross(lookDir).normalize();

        addLightTarget(player, level, targets, eyePos.add(lookDir.scale(0.75D)), 7);
        addLightTarget(player, level, targets, eyePos.add(lookDir.scale(1.55D)), 10);

        double[] sections = new double[] {0.25D, 0.55D, 0.86D};
        for (int i = 0; i < sections.length; i++) {
            double t = sections[i];
            double distance = Math.max(1.15D, hitDistance * t);
            if (distance > hitDistance + 0.2D) continue;
            Vec3 center = eyePos.add(lookDir.scale(distance));
            double radius = coneRadius(t, hitDistance, upgradeLevel);
            int centerLight = Math.max(9, BASE_CENTER_LIGHT - i * 2);
            int ringLight = i < 2 ? midLight : edgeLight;

            addLightTarget(player, level, targets, center, centerLight);
            addLightRing(player, level, targets, center, rightDir, upDir, radius, ringLight, i == sections.length - 1);
            if (i == sections.length - 1) {
                addDiagonalLightRing(player, level, targets, center, rightDir, upDir, radius * 0.72D, ambientLight);
            }
        }

        Vec3 impact = hitPos;
        double impactRadius = coneRadius(1.0D, hitDistance, upgradeLevel);
        addLightTarget(player, level, targets, impact, BASE_CENTER_LIGHT);
        addLightRing(player, level, targets, impact, rightDir, upDir, impactRadius, edgeLight, true);
        addDiagonalLightRing(player, level, targets, impact, rightDir, upDir, impactRadius * 0.72D, ambientLight);

        return targets;
    }

    private static double coneRadius(double t, double hitDistance, int upgradeLevel) {
        double maxRadius = Math.min(3.0D + upgradeLevel * 0.25D, Math.max(1.05D, hitDistance * 0.32D));
        return Math.max(0.45D, Math.pow(Math.max(0.0D, t), 0.82D) * maxRadius);
    }

    private static void addLightRing(
        ServerPlayer player,
        ServerLevel level,
        LinkedHashMap<BlockPos, Integer> targets,
        Vec3 center,
        Vec3 rightDir,
        Vec3 upDir,
        double radius,
        int lightLevel,
        boolean includeVertical
    ) {
        addLightTarget(player, level, targets, center.add(rightDir.scale(radius)), lightLevel);
        addLightTarget(player, level, targets, center.add(rightDir.scale(-radius)), lightLevel);
        if (includeVertical) {
            addLightTarget(player, level, targets, center.add(upDir.scale(radius * 0.72D)), Math.max(7, lightLevel - 1));
            addLightTarget(player, level, targets, center.add(upDir.scale(-radius * 0.72D)), Math.max(7, lightLevel - 1));
        }
    }

    private static void addDiagonalLightRing(
        ServerPlayer player,
        ServerLevel level,
        LinkedHashMap<BlockPos, Integer> targets,
        Vec3 center,
        Vec3 rightDir,
        Vec3 upDir,
        double radius,
        int lightLevel
    ) {
        int light = Math.max(5, lightLevel);
        addLightTarget(player, level, targets, center.add(rightDir.scale(radius)).add(upDir.scale(radius)), light);
        addLightTarget(player, level, targets, center.add(rightDir.scale(-radius)).add(upDir.scale(radius)), light);
        addLightTarget(player, level, targets, center.add(rightDir.scale(radius)).add(upDir.scale(-radius)), light);
        addLightTarget(player, level, targets, center.add(rightDir.scale(-radius)).add(upDir.scale(-radius)), light);
    }

    private static void addLightTarget(
        ServerPlayer player,
        ServerLevel level,
        LinkedHashMap<BlockPos, Integer> targets,
        Vec3 pos,
        int lightLevel
    ) {
        if (targets.size() >= MAX_LIGHTS_PER_PLAYER) return;

        BlockPos base = BlockPos.containing(pos);
        BlockPos placePos = findPlaceableLightPos(level, player, base);
        if (placePos == null) return;

        int clampedLight = Math.max(1, Math.min(15, lightLevel));
        targets.merge(placePos.immutable(), clampedLight, Math::max);
    }

    private static BlockPos findPlaceableLightPos(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos found = findPlaceableExact(level, player, base);
        if (found != null) return found;

        for (Direction direction : Direction.values()) {
            found = findPlaceableExact(level, player, base.relative(direction));
            if (found != null) return found;
        }

        for (Direction first : Direction.values()) {
            for (Direction second : Direction.values()) {
                if (first.getOpposite() == second) continue;
                found = findPlaceableExact(level, player, base.relative(first).relative(second));
                if (found != null) return found;
            }
        }

        return null;
    }

    private static BlockPos findPlaceableExact(ServerLevel level, ServerPlayer player, BlockPos pos) {
        int beamLength = BASE_BEAM_LENGTH + Math.max(0, player.getPersistentData().getInt("TacRogueFlashlightLevel")) * 2;
        if (pos.distSqr(player.blockPosition()) > (beamLength + 4) * (beamLength + 4)) return null;
        if (!level.isLoaded(pos)) return null;

        LightKey key = new LightKey(level.dimension(), pos.immutable());
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return pos.immutable();
        if (state.is(Blocks.LIGHT) && lightOwners.containsKey(key)) return pos.immutable();
        return null;
    }

    private static void reconcileLights(
        MinecraftServer server,
        UUID uuid,
        ServerLevel level,
        LinkedHashMap<BlockPos, Integer> targetLights
    ) {
        Set<LightKey> oldLights = playerLights.getOrDefault(uuid, Set.of());
        Set<LightKey> newLights = new LinkedHashSet<>();

        for (Map.Entry<BlockPos, Integer> entry : targetLights.entrySet()) {
            LightKey key = new LightKey(level.dimension(), entry.getKey().immutable());
            placeOrRefreshLight(level, key, entry.getValue(), uuid);
            newLights.add(key);
        }

        for (LightKey oldKey : new ArrayList<>(oldLights)) {
            if (!newLights.contains(oldKey)) {
                releaseLight(server, oldKey, uuid);
            }
        }

        if (newLights.isEmpty()) {
            playerLights.remove(uuid);
        } else {
            playerLights.put(uuid, newLights);
        }
    }

    private static void placeOrRefreshLight(ServerLevel level, LightKey key, int lightLevel, UUID uuid) {
        BlockState current = level.getBlockState(key.pos());
        if (!current.isAir() && !current.is(Blocks.LIGHT)) return;
        if (current.is(Blocks.LIGHT) && !lightOwners.containsKey(key)) return;

        lightOwners.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(uuid);

        BlockState target = Blocks.LIGHT.defaultBlockState()
            .setValue(BlockStateProperties.LEVEL, Math.max(1, Math.min(15, lightLevel)));
        if (!current.is(Blocks.LIGHT)
            || current.getValue(BlockStateProperties.LEVEL) < target.getValue(BlockStateProperties.LEVEL)) {
            level.setBlock(key.pos(), target, LIGHT_BLOCK_FLAGS);
        }
    }

    private static void clearPlayerLights(MinecraftServer server, UUID uuid) {
        Set<LightKey> lights = playerLights.remove(uuid);
        if (lights == null || lights.isEmpty()) return;

        for (LightKey light : new HashSet<>(lights)) {
            releaseLight(server, light, uuid);
        }
    }

    private static void releaseLight(MinecraftServer server, LightKey key, UUID uuid) {
        Set<UUID> owners = lightOwners.get(key);
        if (owners != null) {
            owners.remove(uuid);
            if (!owners.isEmpty()) return;
            lightOwners.remove(key);
        }

        ServerLevel level = server.getLevel(key.dimension());
        if (level != null && level.isLoaded(key.pos()) && level.getBlockState(key.pos()).is(Blocks.LIGHT)) {
            level.setBlock(key.pos(), Blocks.AIR.defaultBlockState(), LIGHT_BLOCK_FLAGS);
        }
    }

    private static boolean isRogueDungeon(ServerPlayer player) {
        return isRogueDungeon(player.level().dimension());
    }

    private static boolean isRogueDungeon(ResourceKey<Level> dimension) {
        return "tac_rogue".equals(dimension.location().getNamespace())
            && "rogue_dimension".equals(dimension.location().getPath());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayerLights(player.server, player.getUUID());
            cleanup(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayerLights(player.server, player.getUUID());
            lastBeamStates.remove(player.getUUID());
            lastUpdateTicks.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearPlayerLights(player.server, player.getUUID());
            lastBeamStates.remove(player.getUUID());
            lastUpdateTicks.remove(player.getUUID());
        }
    }

    private record LightKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private record BeamState(ResourceKey<Level> dimension, BlockPos pos, int yawBucket, int pitchBucket, int upgradeLevel) {}
}
