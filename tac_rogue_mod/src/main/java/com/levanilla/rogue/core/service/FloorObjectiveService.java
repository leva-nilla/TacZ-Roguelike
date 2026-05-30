package com.levanilla.rogue.core.service;

import com.levanilla.rogue.networking.PopupNotificationMessage;
import com.levanilla.rogue.networking.SyncDataMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.world.ThemeManager;
import com.levanilla.rogue.world.generation.plan.RoomRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FloorObjectiveService {
    public static final String ELITE_ROOM_TAG = "TacRogueEliteRoom";
    public static final String OBJECTIVE_ELITE_TAG = "TacRogueObjectiveElite";
    public static final String OBJECTIVE_BLOCK_KEY = "TacRogueObjectiveBlock";
    public static final String OBJECTIVE_TYPE_KEY = "TacRogueObjectiveType";
    private static final String OBJECTIVE_CACHE_FILLED_KEY = "TacRogueObjectiveCacheFilled";

    private static final int SECURE_TERMINAL_TICKS = 25 * 20;
    private static final int HOLD_POSITION_TICKS = 45 * 20;
    private static final double OBJECTIVE_RADIUS_SQR = 6.5D * 6.5D;
    private static final double HOLD_CONTEST_RADIUS = 7.0D;
    private static final long OBJECTIVE_LURE_INTERVAL_TICKS = 35L;
    private static final int OBJECTIVE_LURE_MOBS_PER_PULSE = 12;
    private static final java.util.Set<UUID> REWARDED_ELITES = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, ObjectiveType> DEBUG_OBJECTIVE_OVERRIDES = new ConcurrentHashMap<>();

    private FloorObjectiveService() {}

    public enum ObjectiveType {
        ELIMINATE("Eliminate hostiles", "Neutralize all hostile contacts"),
        SECURE_TERMINAL("Secure terminal", "Hold the objective terminal"),
        HOLD_POSITION("Hold position", "Secure the marked defensive point"),
        RECOVER_CACHE("Recover cache", "Reach and secure the supply cache"),
        HUNT_ELITE("Hunt elite", "Eliminate the elite contact"),
        ESCAPE_ROUTE("Escape route", "Reach the extraction route");

        public final String title;
        public final String description;

        ObjectiveType(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public static ObjectiveType parse(String raw) {
            if (raw == null || raw.isBlank()) return ELIMINATE;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ELIMINATE;
            }
        }
    }

    public static void prepareInstance(MinecraftServer server, FloorInstanceManager.FloorInstance instance,
                                       ThemeManager.ThemeInstance theme) {
        if (server == null || instance == null) return;
        ObjectiveType type = consumeDebugObjectiveOverride(instance.ownerUuid);
        if (type == null) {
            type = selectObjective(instance.floor, theme, server.getWorldData().worldGenOptions().seed(),
                instance.floorSeedSalt, ThemeManager.isBossFloor(instance.floor));
        }
        instance.objectiveType = type.name();
        instance.objectiveProgress = 0;
        instance.objectiveTarget = targetFor(type);
        instance.objectiveHoldTicks = 0;
        instance.objectiveCompleted = false;
        instance.objectiveActivated = type != ObjectiveType.SECURE_TERMINAL;
        instance.objectiveContested = false;
        instance.objectiveStatusKey = initialStatusFor(type);
        instance.lastObjectiveLureTick = 0L;
        instance.objectiveLurePulseCount = 0;
        instance.objectiveLureCursor = 0;
        instance.objectiveTargetPos = null;
        instance.objectiveEliteUuid = null;
        instance.objectiveEliteRegistered = false;
    }

    public static String objectiveNameForGeneration(String instanceId, int floor, ThemeManager.ThemeInstance theme,
                                                    boolean bossFloor, long runSeed, long floorSeedSalt) {
        FloorInstanceManager.FloorInstance instance = instanceId == null ? null : FloorInstanceManager.INSTANCES.get(instanceId);
        if (instance != null && instance.objectiveType != null && !instance.objectiveType.isBlank()) {
            return instance.objectiveType;
        }
        return selectObjective(floor, theme, runSeed, floorSeedSalt, bossFloor).name();
    }

    public static ObjectiveType objectiveType(FloorInstanceManager.FloorInstance instance) {
        return instance == null ? ObjectiveType.ELIMINATE : ObjectiveType.parse(instance.objectiveType);
    }

    public static void registerObjectiveRoom(String instanceId, int floor, RoomRole role, BlockPos center) {
        if (instanceId == null || instanceId.isBlank() || center == null) return;
        FloorInstanceManager.FloorInstance instance = FloorInstanceManager.INSTANCES.get(instanceId);
        if (instance == null || instance.floor != floor || instance.objectiveTargetPos != null) return;
        ObjectiveType type = objectiveType(instance);
        if (isPreferredObjectiveRoom(type, role)) {
            instance.objectiveTargetPos = center.immutable();
        }
    }

    public static void registerObjectiveTargetBlock(String instanceId, int floor, ObjectiveType type, BlockPos pos) {
        if (instanceId == null || instanceId.isBlank() || type == null || pos == null) return;
        FloorInstanceManager.FloorInstance instance = FloorInstanceManager.INSTANCES.get(instanceId);
        if (instance == null || instance.floor != floor || objectiveType(instance) != type) return;
        instance.objectiveTargetPos = pos.immutable();
    }

    public static void markObjectiveBlock(BlockEntity blockEntity, String instanceId, int floor,
                                          FloorInstanceManager.EntryMode mode, ObjectiveType type) {
        if (blockEntity == null || type == null) return;
        FloorInstanceManager.stampBlockEntity(blockEntity, instanceId, floor, mode);
        blockEntity.getPersistentData().putBoolean(OBJECTIVE_BLOCK_KEY, true);
        blockEntity.getPersistentData().putString(OBJECTIVE_TYPE_KEY, type.name());
        blockEntity.setChanged();
    }

    public static void registerEliteMob(Mob mob, boolean objectiveElite) {
        if (mob == null) return;
        mob.addTag(ELITE_ROOM_TAG);
        mob.setPersistenceRequired();
        if (objectiveElite) {
            mob.addTag(OBJECTIVE_ELITE_TAG);
            String instanceId = mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
            FloorInstanceManager.FloorInstance instance = FloorInstanceManager.INSTANCES.get(instanceId);
            if (instance != null && instance.objectiveEliteUuid == null) {
                instance.objectiveEliteUuid = mob.getUUID();
            }
            if (instance != null) {
                instance.objectiveEliteRegistered = true;
            }
        }
    }

    public static void onEliteKilled(ServerPlayer killer, Entity killed) {
        if (killer == null || killed == null || !killed.getTags().contains(ELITE_ROOM_TAG)) return;
        if (!REWARDED_ELITES.add(killed.getUUID())) return;
        List<ServerPlayer> recipients = killed instanceof LivingEntity living
            ? FloorInstanceManager.getParticipantsForEntity(living)
            : List.of(killer);
        if (recipients.isEmpty()) recipients = List.of(killer);
        int floor = Math.max(1, killed.getPersistentData().getInt(FloorInstanceManager.FLOOR_KEY));
        int reward = 350 + Math.min(1800, floor * 45);
        for (ServerPlayer player : recipients) {
            GoldGainService.award(player, reward, false);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.REWARD,
                Component.literal("Elite neutralized"),
                Component.literal("Bonus secured: $" + reward),
                130);
        }
    }

    public static boolean shouldClearInstance(MinecraftServer server, ServerLevel level,
                                              FloorInstanceManager.FloorInstance instance,
                                              List<Mob> aliveSpawnedMobs) {
        if (server == null || level == null || instance == null) return false;
        ObjectiveType type = objectiveType(instance);
        boolean bossFloor = ThemeManager.isBossFloor(instance.floor);
        boolean bossAlive = aliveSpawnedMobs.stream().anyMatch(mob -> mob.getTags().contains("rogue:boss"));
        if (bossFloor) return !bossAlive;

        boolean complete = switch (type) {
            case ELIMINATE -> tickEliminateObjective(instance, aliveSpawnedMobs);
            case HUNT_ELITE -> objectiveEliteDefeated(level, instance, aliveSpawnedMobs);
            case SECURE_TERMINAL, HOLD_POSITION -> tickHoldObjective(server, level, instance, type, aliveSpawnedMobs);
            case RECOVER_CACHE, ESCAPE_ROUTE -> instance.objectiveCompleted;
        };
        instance.objectiveCompleted = complete;
        if (complete) {
            instance.objectiveProgress = instance.objectiveTarget;
        }
        return complete;
    }

    public static void sync(MinecraftServer server, FloorInstanceManager.FloorInstance instance, boolean force) {
        if (server == null || instance == null) return;
        long tick = server.getTickCount();
        if (!force && tick - instance.lastObjectiveSyncTick < 20) return;
        instance.lastObjectiveSyncTick = tick;
        ObjectiveType type = objectiveType(instance);
        String pos = instance.objectiveTargetPos == null
            ? ""
            : instance.objectiveTargetPos.getX() + "," + instance.objectiveTargetPos.getY() + "," + instance.objectiveTargetPos.getZ();
        String payload = "objective:" + type.name()
            + ":" + Math.max(0, instance.objectiveProgress)
            + ":" + Math.max(1, instance.objectiveTarget)
            + ":" + instance.objectiveCompleted
            + ":" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(type.title.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            + ":" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(pos.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            + ":" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(statusFor(instance, type).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (UUID uuid : List.copyOf(instance.participants)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                TacRogueNetworking.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SyncDataMessage(payload));
            }
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        TacRogueNetworking.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncDataMessage("objective_clear"));
    }

    public static int roomWeight(RoomRole role, ObjectiveType type) {
        if (role == null) return 2;
        return switch (role) {
            case START, SIDE_REWARD, LOCKED_REWARD -> 0;
            case STEALTH_ROUTE -> 1;
            case DARK_ROOM -> 2;
            case OBJECTIVE_TERMINAL, DEFENSE_POINT -> type == ObjectiveType.ELIMINATE ? 1 : 2;
            case COMBAT_SMALL, COVER_DENSE -> 2;
            case COMBAT_LONG, SUPPLY_RISK, AMBUSH -> 3;
            case ELITE, ELITE_ARENA -> 4;
            case BOSS_ENTRY, BOSS_ARENA -> 0;
        };
    }

    public static int roomCap(RoomRole role, ObjectiveType type, int maxPerRoom) {
        int base = Math.max(1, maxPerRoom);
        return switch (role) {
            case STEALTH_ROUTE -> Math.max(1, base - 3);
            case OBJECTIVE_TERMINAL, DEFENSE_POINT, DARK_ROOM -> Math.max(2, base - 2);
            case ELITE, ELITE_ARENA -> Math.max(1, Math.min(base, 2));
            case SIDE_REWARD, LOCKED_REWARD -> 0;
            default -> base;
        };
    }

    public static int progressTarget(ObjectiveType type) {
        return targetFor(type);
    }

    public static boolean matchesObjectiveRoom(ObjectiveType type, RoomRole role) {
        if (type == null || role == null) return false;
        return isPreferredObjectiveRoom(type, role);
    }

    public static void setDebugObjectiveOverride(UUID playerUuid, ObjectiveType type) {
        if (playerUuid == null || type == null) return;
        DEBUG_OBJECTIVE_OVERRIDES.put(playerUuid, type);
    }

    public static ObjectiveType peekDebugObjectiveOverride(UUID playerUuid) {
        return playerUuid == null ? null : DEBUG_OBJECTIVE_OVERRIDES.get(playerUuid);
    }

    public static boolean handleObjectiveBlockInteract(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || player.level().dimension() != com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM) {
            return false;
        }
        FloorInstanceManager.FloorInstance instance = FloorInstanceManager.getInstanceForPlayer(player);
        if (instance == null || instance.state != FloorInstanceManager.State.ACTIVE) return false;
        ObjectiveType type = objectiveType(instance);
        if (!isInteractObjective(type)) return false;
        BlockPos target = instance.objectiveTargetPos;
        if (target == null || target.distSqr(pos) > 2.25D) return false;

        switch (type) {
            case SECURE_TERMINAL -> {
                instance.objectiveActivated = true;
                instance.objectiveStatusKey = "objective.tac_rogue.status.secure_active";
                player.displayClientMessage(Component.translatable("message.tac_rogue.objective_terminal_started"), true);
                sync(player.server, instance, true);
                return true;
            }
            case RECOVER_CACHE -> {
                int supplyCount = fillRecoverCache(player, instance, target);
                instance.objectiveActivated = true;
                instance.objectiveCompleted = true;
                instance.objectiveHoldTicks = instance.objectiveTarget;
                instance.objectiveProgress = instance.objectiveTarget;
                instance.objectiveStatusKey = "objective.tac_rogue.status.recovered";
                player.displayClientMessage(Component.translatable("message.tac_rogue.objective_cache_recovered"), true);
                PopupNotificationMessage.send(
                    player,
                    PopupNotificationMessage.PopupType.REWARD,
                    Component.translatable("popup.tac_rogue.objective_cache.title"),
                    Component.translatable("message.tac_rogue.objective_cache_supplies", supplyCount),
                    140);
                openObjectiveContainer(player, target);
                sync(player.server, instance, true);
                return true;
            }
            case ESCAPE_ROUTE -> {
                if (instance.objectiveCompleted) return true;
                instance.objectiveActivated = true;
                instance.objectiveCompleted = true;
                instance.objectiveHoldTicks = instance.objectiveTarget;
                instance.objectiveProgress = instance.objectiveTarget;
                instance.objectiveStatusKey = "objective.tac_rogue.status.escape_ready";
                player.displayClientMessage(Component.translatable("message.tac_rogue.objective_escape_confirmed"), true);
                sync(player.server, instance, true);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static void callObjectiveSupport(ServerLevel level, FloorInstanceManager.FloorInstance instance) {
        callObjectiveSupport(level, instance, List.of());
    }

    public static void callObjectiveSupport(ServerLevel level, FloorInstanceManager.FloorInstance instance, List<BlockPos> reservedSpawns) {
        if (level == null || instance == null) return;

        List<Mob> targets = level.getEntitiesOfClass(Mob.class, new AABB(instance.origin).inflate(110.0D, 48.0D, 110.0D), mob ->
            mob != null
                && mob.isAlive()
                && !mob.isRemoved()
                && mob.getTags().contains("tac_rogue_spawned")
                && !mob.getTags().contains("rogue:boss")
                && instance.id.equals(mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY)));

        spawnSupportOperators(level, instance, reservedSpawns);

        BlockPos soundPos = instance.objectiveTargetPos != null ? instance.objectiveTargetPos : instance.origin;
        level.playSound(null, soundPos, com.tacz.guns.init.ModSounds.GUN.get(), SoundSource.HOSTILE, 0.8F, 0.82F);
        level.playSound(null, soundPos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 0.75F, 1.15F);

        for (ServerPlayer player : FloorInstanceManager.getParticipants(level, instance.id)) {
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.translatable("popup.tac_rogue.objective_support.title"),
                targets.isEmpty()
                    ? Component.translatable("message.tac_rogue.objective_support_ready")
                    : Component.translatable("message.tac_rogue.objective_support_arrived", targets.size()),
                140);
        }
    }

    private static void scheduleSupportSweep(ServerLevel level, List<Mob> targets, BlockPos soundPos) {
        if (targets == null || targets.isEmpty()) return;
        MinecraftServer server = level.getServer();
        if (server == null) {
            for (Mob mob : targets) supportStrikeTarget(level, mob, soundPos);
            return;
        }

        int index = 0;
        for (Mob mob : targets) {
            int delay = Math.min(80, 5 + index * 3);
            server.tell(new TickTask(server.getTickCount() + delay, () -> supportStrikeTarget(level, mob, soundPos)));
            index++;
        }
    }

    private static void supportStrikeTarget(ServerLevel level, Mob mob, BlockPos fallbackSoundPos) {
        if (level == null || mob == null || mob.level() != level || !mob.isAlive() || mob.isRemoved()) return;

        double x = mob.getX();
        double y = mob.getY() + mob.getBbHeight() * 0.62D;
        double z = mob.getZ();
        float pitch = 0.82F + level.getRandom().nextFloat() * 0.28F;
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 0.72F, pitch);
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.ARROW_HIT_PLAYER, SoundSource.HOSTILE, 0.5F, 1.25F);
        level.sendParticles(ParticleTypes.CRIT, x, y, z, 14, 0.22D, 0.28D, 0.22D, 0.06D);
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, 10, 0.28D, 0.32D, 0.28D, 0.025D);
        level.sendParticles(ParticleTypes.POOF, x, y + 0.15D, z, 8, 0.24D, 0.2D, 0.24D, 0.02D);
        mob.remove(Entity.RemovalReason.DISCARDED);

        if (fallbackSoundPos != null && level.getRandom().nextInt(4) == 0) {
            level.playSound(null, fallbackSoundPos, SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.35F, 1.15F);
        }
    }

    private static ObjectiveType selectObjective(int floor, ThemeManager.ThemeInstance theme, long runSeed,
                                                 long floorSeedSalt, boolean bossFloor) {
        if (bossFloor || floor < 4) return ObjectiveType.ELIMINATE;
        ThemeManager.ThemeGenerationStyle style = ThemeManager.generationStyle(theme);
        java.util.Random rand = new java.util.Random(mix(runSeed ^ floorSeedSalt ^ ((long) floor * 0x9E3779B97F4A7C15L)
            ^ (long) style.ordinal() * 0xC2B2AE3D27D4EB4FL));
        ObjectiveType[] bag = switch (style) {
            case LAB_COMPLEX -> new ObjectiveType[] {
                ObjectiveType.SECURE_TERMINAL, ObjectiveType.SECURE_TERMINAL, ObjectiveType.HOLD_POSITION,
                ObjectiveType.ELIMINATE, ObjectiveType.HUNT_ELITE
            };
            case ORGANIC_CAVE, VOID_ALIEN -> new ObjectiveType[] {
                ObjectiveType.RECOVER_CACHE, ObjectiveType.ESCAPE_ROUTE, ObjectiveType.HUNT_ELITE,
                ObjectiveType.ELIMINATE, ObjectiveType.ELIMINATE
            };
            case ROOFTOP_OPEN, RADAR_OPEN, MILITARY_COMPOUND -> new ObjectiveType[] {
                ObjectiveType.HUNT_ELITE, ObjectiveType.SECURE_TERMINAL, ObjectiveType.HOLD_POSITION,
                ObjectiveType.ELIMINATE, ObjectiveType.ELIMINATE
            };
            case SUBWAY, PIPELINE, SEWER -> new ObjectiveType[] {
                ObjectiveType.ESCAPE_ROUTE, ObjectiveType.RECOVER_CACHE, ObjectiveType.ELIMINATE,
                ObjectiveType.ELIMINATE, ObjectiveType.HUNT_ELITE
            };
            case TEMPLE_AXIS -> new ObjectiveType[] {
                ObjectiveType.SECURE_TERMINAL, ObjectiveType.HUNT_ELITE, ObjectiveType.RECOVER_CACHE,
                ObjectiveType.ELIMINATE, ObjectiveType.ELIMINATE
            };
            default -> new ObjectiveType[] {
                ObjectiveType.ELIMINATE, ObjectiveType.ELIMINATE, ObjectiveType.SECURE_TERMINAL,
                ObjectiveType.RECOVER_CACHE, ObjectiveType.HUNT_ELITE, ObjectiveType.HOLD_POSITION
            };
        };
        return bag[rand.nextInt(bag.length)];
    }

    private static ObjectiveType consumeDebugObjectiveOverride(UUID playerUuid) {
        return playerUuid == null ? null : DEBUG_OBJECTIVE_OVERRIDES.remove(playerUuid);
    }

    private static int targetFor(ObjectiveType type) {
        return switch (type) {
            case ELIMINATE, HUNT_ELITE, RECOVER_CACHE, ESCAPE_ROUTE -> 1;
            case SECURE_TERMINAL -> SECURE_TERMINAL_TICKS;
            case HOLD_POSITION -> HOLD_POSITION_TICKS;
        };
    }

    private static boolean isPreferredObjectiveRoom(ObjectiveType type, RoomRole role) {
        return switch (type) {
            case ELIMINATE -> false;
            case SECURE_TERMINAL -> role == RoomRole.OBJECTIVE_TERMINAL;
            case HOLD_POSITION -> role == RoomRole.DEFENSE_POINT;
            case RECOVER_CACHE -> role == RoomRole.LOCKED_REWARD || role == RoomRole.SUPPLY_RISK;
            case HUNT_ELITE -> role == RoomRole.ELITE_ARENA || role == RoomRole.ELITE;
            case ESCAPE_ROUTE -> role == RoomRole.STEALTH_ROUTE || role == RoomRole.COMBAT_LONG;
        };
    }

    private static boolean objectiveEliteDefeated(ServerLevel level, FloorInstanceManager.FloorInstance instance,
                                                  List<Mob> aliveSpawnedMobs) {
        if (instance.objectiveEliteUuid != null) {
            Entity entity = level.getEntity(instance.objectiveEliteUuid);
            boolean alive = entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved();
            instance.objectiveProgress = alive ? 0 : 1;
            instance.objectiveStatusKey = alive
                ? "objective.tac_rogue.status.elite_alive"
                : "objective.tac_rogue.status.eliminated";
            return !alive;
        }
        boolean anyObjectiveElite = aliveSpawnedMobs.stream().anyMatch(mob -> mob.getTags().contains(OBJECTIVE_ELITE_TAG));
        instance.objectiveProgress = anyObjectiveElite ? 0 : 1;
        instance.objectiveStatusKey = anyObjectiveElite
            ? "objective.tac_rogue.status.elite_alive"
            : "objective.tac_rogue.status.elite_pending";
        return instance.objectiveEliteRegistered && !anyObjectiveElite;
    }

    private static boolean tickEliminateObjective(FloorInstanceManager.FloorInstance instance, List<Mob> aliveSpawnedMobs) {
        int alive = aliveSpawnedMobs == null ? 0 : aliveSpawnedMobs.size();
        instance.objectiveTarget = Math.max(instance.objectiveTarget, alive);
        instance.objectiveProgress = Math.max(0, instance.objectiveTarget - alive);
        instance.objectiveStatusKey = alive <= 0
            ? "objective.tac_rogue.status.eliminated"
            : "objective.tac_rogue.status.eliminate_remaining";
        return alive <= 0;
    }

    private static boolean tickHoldObjective(MinecraftServer server, ServerLevel level,
                                             FloorInstanceManager.FloorInstance instance, ObjectiveType type,
                                             List<Mob> aliveSpawnedMobs) {
        BlockPos target = instance.objectiveTargetPos;
        if (target == null) return false;
        if (type == ObjectiveType.SECURE_TERMINAL && !instance.objectiveActivated) {
            instance.objectiveStatusKey = "objective.tac_rogue.status.secure_idle";
            instance.objectiveProgress = Math.min(instance.objectiveTarget, instance.objectiveHoldTicks);
            return false;
        }

        boolean occupied = false;
        double progressMultiplier = 1.0D;
        for (UUID uuid : List.copyOf(instance.participants)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || player.level() != level || !player.isAlive() || player.isSpectator()) continue;
            if (player.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) <= OBJECTIVE_RADIUS_SQR) {
                occupied = true;
                progressMultiplier = Math.max(progressMultiplier,
                    RogueUtilityItemService.getObjectiveProgressMultiplier(player, type));
            }
        }

        boolean contested = type == ObjectiveType.HOLD_POSITION && isContested(level, target, aliveSpawnedMobs);
        instance.objectiveContested = contested;
        if (shouldPulseObjectiveLure(type, instance, occupied)) {
            pulseObjectiveLure(server, level, instance, type, target, aliveSpawnedMobs, contested);
        }
        int step = Math.max(1, com.levanilla.rogue.core.GameConstants.FLOOR_CLEAR_CHECK_INTERVAL);
        if (occupied && !contested) {
            int gain = Math.max(1, (int)Math.round(step * progressMultiplier));
            instance.objectiveHoldTicks = Math.min(instance.objectiveTarget, instance.objectiveHoldTicks + gain);
            instance.objectiveStatusKey = type == ObjectiveType.HOLD_POSITION
                ? (instance.objectiveLurePulseCount > 0 ? "objective.tac_rogue.status.hold_beacon" : "objective.tac_rogue.status.hold_securing")
                : (instance.objectiveLurePulseCount > 0 ? "objective.tac_rogue.status.secure_signal" : "objective.tac_rogue.status.secure_uploading");
        } else if (contested) {
            instance.objectiveStatusKey = "objective.tac_rogue.status.contested";
        } else {
            instance.objectiveHoldTicks = Math.max(0, instance.objectiveHoldTicks - Math.max(1, step / 2));
            instance.objectiveStatusKey = type == ObjectiveType.HOLD_POSITION
                ? "objective.tac_rogue.status.hold_enter"
                : "objective.tac_rogue.status.secure_return";
        }
        instance.objectiveProgress = Math.min(instance.objectiveTarget, instance.objectiveHoldTicks);
        return instance.objectiveHoldTicks >= instance.objectiveTarget;
    }

    private static boolean shouldPulseObjectiveLure(ObjectiveType type, FloorInstanceManager.FloorInstance instance, boolean occupied) {
        if (instance == null || instance.objectiveCompleted) return false;
        return switch (type) {
            case SECURE_TERMINAL -> instance.objectiveActivated && (occupied || instance.objectiveHoldTicks > 0);
            case HOLD_POSITION -> occupied || instance.objectiveHoldTicks > 0;
            default -> false;
        };
    }

    private static void pulseObjectiveLure(MinecraftServer server, ServerLevel level,
                                           FloorInstanceManager.FloorInstance instance, ObjectiveType type,
                                           BlockPos target, List<Mob> aliveSpawnedMobs, boolean contested) {
        if (server == null || level == null || instance == null || target == null) return;
        long now = server.getTickCount();
        if (now - instance.lastObjectiveLureTick < OBJECTIVE_LURE_INTERVAL_TICKS) return;
        instance.lastObjectiveLureTick = now;
        instance.objectiveLurePulseCount++;

        Vec3 lurePos = Vec3.atCenterOf(target);
        int lured = 0;
        if (aliveSpawnedMobs != null) {
            List<Mob> candidates = new ArrayList<>(aliveSpawnedMobs);
            candidates.sort(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(lurePos)));
            int size = candidates.size();
            int start = size == 0 ? 0 : Math.floorMod(instance.objectiveLureCursor, size);
            int inspected = 0;
            while (inspected < size && lured < OBJECTIVE_LURE_MOBS_PER_PULSE) {
                Mob mob = candidates.get((start + inspected) % size);
                inspected++;
                if (mob == null || !mob.isAlive() || mob.level() != level) continue;
                if (mob.distanceToSqr(lurePos) <= HOLD_CONTEST_RADIUS * HOLD_CONTEST_RADIUS) continue;
                RogueMobAlertService.drawToObjective(
                    mob,
                    lurePos,
                    contested || instance.objectiveLurePulseCount > 1,
                    type == ObjectiveType.SECURE_TERMINAL ? "terminal_signal" : "hold_beacon");
                lured++;
            }
            instance.objectiveLureCursor = size == 0 ? 0 : Math.floorMod(start + Math.max(1, inspected), size);
        }

        if (type == ObjectiveType.SECURE_TERMINAL) {
            level.playSound(null, target, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.78F, 0.65F + level.random.nextFloat() * 0.12F);
            level.playSound(null, target, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.45F, 1.45F);
            level.sendParticles(ParticleTypes.END_ROD,
                target.getX() + 0.5D, target.getY() + 1.15D, target.getZ() + 0.5D,
                10, 0.32D, 0.22D, 0.32D, 0.015D);
        } else {
            level.playSound(null, target, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.62F, 0.92F);
            level.playSound(null, target, SoundEvents.CROSSBOW_LOADING_START, SoundSource.BLOCKS, 0.35F, 0.75F);
            level.sendParticles(ParticleTypes.SMOKE,
                target.getX() + 0.5D, target.getY() + 0.35D, target.getZ() + 0.5D,
                12, 0.48D, 0.12D, 0.48D, 0.018D);
        }
    }

    private static boolean isContested(ServerLevel level, BlockPos target, List<Mob> aliveSpawnedMobs) {
        if (aliveSpawnedMobs == null || aliveSpawnedMobs.isEmpty()) return false;
        AABB area = new AABB(target).inflate(HOLD_CONTEST_RADIUS, 3.0D, HOLD_CONTEST_RADIUS);
        for (Mob mob : aliveSpawnedMobs) {
            if (mob == null || !mob.isAlive() || mob.level() != level) continue;
            if (area.contains(mob.position()) && hasObjectiveLineOfSight(level, mob, target)) return true;
        }
        return false;
    }

    private static boolean hasObjectiveLineOfSight(ServerLevel level, Mob mob, BlockPos target) {
        if (level == null || mob == null || target == null) return false;
        Vec3 start = mob.getEyePosition();
        Vec3 end = Vec3.atCenterOf(target);
        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(end) <= 1.15D;
    }

    private static boolean isInteractObjective(ObjectiveType type) {
        return type == ObjectiveType.SECURE_TERMINAL || type == ObjectiveType.RECOVER_CACHE || type == ObjectiveType.ESCAPE_ROUTE;
    }

    private static int fillRecoverCache(ServerPlayer player, FloorInstanceManager.FloorInstance instance, BlockPos target) {
        if (player == null || instance == null || target == null) return 0;
        BlockEntity blockEntity = player.level().getBlockEntity(target);
        if (!(blockEntity instanceof Container container)) return 0;
        if (blockEntity.getPersistentData().getBoolean(OBJECTIVE_CACHE_FILLED_KEY)) {
            return countContainerItems(container);
        }

        int floor = Math.max(1, instance.floor);
        List<ItemStack> supplies = new ArrayList<>();
        Set<String> ammoIds = equippedAmmoIds(player);
        if (ammoIds.isEmpty()) {
            List<String> allAmmo = com.levanilla.rogue.core.TacZRegistryHelper.getAllAmmoIds();
            if (!allAmmo.isEmpty()) ammoIds.add(allAmmo.get(0));
        }
        for (String ammoId : ammoIds) {
            addIfPresent(supplies, RogueItemFactory.createAmmoStack(player, ammoId));
        }
        addIfPresent(supplies, RogueItemFactory.createRecoveryItem("rogue:field_ration"));
        addIfPresent(supplies, RogueItemFactory.createRecoveryItem("rogue:bandage"));
        if (floor >= 3) addIfPresent(supplies, RogueItemFactory.createRecoveryItem("rogue:armor_plate"));
        if (floor >= 8) addIfPresent(supplies, RogueItemFactory.createRecoveryItem("rogue:stamina_shot"));
        if (floor >= 15) addIfPresent(supplies, RogueItemFactory.createRecoveryItem("rogue:medkit"));
        if (RogueUtilityItemService.hasRecoveryBeacon(player)) {
            addIfPresent(supplies, RogueItemFactory.createRecoveryItem("rogue:armor_plate"));
            addIfPresent(supplies, RogueItemFactory.createRecoveryItem("rogue:maintenance_kit"));
            if (floor >= 10) addIfPresent(supplies, RogueItemFactory.createRecoveryItem("rogue:medkit"));
        }

        int delivered = 0;
        container.clearContent();
        for (ItemStack stack : supplies) {
            if (stack == null || stack.isEmpty()) continue;
            if (!placeIntoContainer(container, stack)) continue;
            delivered++;
        }
        blockEntity.getPersistentData().putBoolean(OBJECTIVE_CACHE_FILLED_KEY, true);
        blockEntity.setChanged();
        return delivered;
    }

    private static void openObjectiveContainer(ServerPlayer player, BlockPos target) {
        if (player == null || target == null) return;
        BlockEntity blockEntity = player.level().getBlockEntity(target);
        if (blockEntity instanceof MenuProvider provider) {
            player.openMenu(provider);
        }
    }

    private static boolean placeIntoContainer(Container container, ItemStack source) {
        if (container == null || source == null || source.isEmpty()) return false;
        ItemStack remaining = source.copy();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack existing = container.getItem(i);
            if (existing.isEmpty() || !RogueStackingService.canMerge(existing, remaining)) continue;
            int move = Math.min(remaining.getCount(), Math.max(0, existing.getMaxStackSize() - existing.getCount()));
            if (move <= 0) continue;
            existing.grow(move);
            remaining.shrink(move);
            if (remaining.isEmpty()) {
                container.setChanged();
                return true;
            }
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) continue;
            container.setItem(i, remaining.copy());
            container.setChanged();
            return true;
        }
        return false;
    }

    private static int countContainerItems(Container container) {
        if (container == null) return 0;
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) count++;
        }
        return count;
    }

    private static Set<String> equippedAmmoIds(ServerPlayer player) {
        Set<String> ids = new LinkedHashSet<>();
        if (player == null) return ids;
        int[] gunSlots = {
            com.levanilla.rogue.core.GameConstants.SLOT_GUN_START,
            com.levanilla.rogue.core.GameConstants.SLOT_GUN_END
        };
        for (int slot : gunSlots) {
            ItemStack gun = player.getInventory().items.get(slot);
            if (gun.isEmpty() || !gun.hasTag() || !gun.getTag().contains("GunId")) continue;
            String ammoId = com.levanilla.rogue.core.TacZRegistryHelper.getAmmoForGun(gun.getTag().getString("GunId"));
            if (ammoId != null && !ammoId.isBlank()) ids.add(ammoId);
        }
        return ids;
    }

    private static void spawnSupportOperators(ServerLevel level, FloorInstanceManager.FloorInstance instance, List<BlockPos> reservedSpawns) {
        if (level == null || instance == null) return;
        List<ServerPlayer> participants = FloorInstanceManager.getParticipants(level, instance.id);
        BlockPos base = instance.objectiveTargetPos != null ? instance.objectiveTargetPos : instance.origin;
        List<BlockPos> occupied = new ArrayList<>();
        if (reservedSpawns != null) occupied.addAll(reservedSpawns);
        int spawned = 0;
        for (ServerPlayer player : participants) {
            if (spawned >= 4) break;
            BlockPos pos = findSupportSpawnNear(level, player, occupied, spawned);
            com.levanilla.rogue.world.TacRogueNpcEntity npc =
                com.levanilla.rogue.world.NpcManager.spawnSupportOperator(level, pos, instance.floor, spawned);
            if (npc != null) {
                FloorInstanceManager.stampEntity(npc, instance.id, instance.floor, instance.mode, player.getUUID());
                occupied.add(npc.blockPosition());
                spawned++;
            }
        }
        for (; spawned < 3; spawned++) {
            BlockPos pos = findSupportSpawnNear(level, base, occupied, spawned);
            com.levanilla.rogue.world.TacRogueNpcEntity npc =
                com.levanilla.rogue.world.NpcManager.spawnSupportOperator(
                    level,
                    pos,
                    instance.floor,
                    spawned);
            if (npc != null) {
                UUID owner = participants.isEmpty() ? null : participants.get(0).getUUID();
                FloorInstanceManager.stampEntity(npc, instance.id, instance.floor, instance.mode, owner);
                occupied.add(npc.blockPosition());
            }
        }
    }

    private static BlockPos findSupportSpawnNear(ServerLevel level, ServerPlayer player, List<BlockPos> occupied, int index) {
        BlockPos base = player.blockPosition();
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() < 0.001D) {
            forward = Vec3.directionFromRotation(0.0F, player.getYRot()).multiply(1.0D, 0.0D, 1.0D);
        }
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double side = (index & 1) == 0 ? 1.0D : -1.0D;
        BlockPos[] candidates = new BlockPos[] {
            offset(base, right.x * side * 4.0D - forward.x * 2.0D, right.z * side * 4.0D - forward.z * 2.0D),
            offset(base, right.x * side * 5.0D + forward.x * 1.5D, right.z * side * 5.0D + forward.z * 1.5D),
            offset(base, -right.x * side * 4.0D - forward.x * 3.0D, -right.z * side * 4.0D - forward.z * 3.0D),
            offset(base, forward.x * 5.0D + right.x * side * 2.5D, forward.z * 5.0D + right.z * side * 2.5D)
        };
        for (BlockPos candidate : candidates) {
            BlockPos safe = findSupportSafeY(level, candidate, base.getY(), occupied);
            if (safe != null) return safe;
        }
        return findSupportSpawnNear(level, base, occupied, index);
    }

    private static BlockPos findSupportSpawnNear(ServerLevel level, BlockPos base, List<BlockPos> occupied, int index) {
        int startRadius = 3 + Math.max(0, index);
        for (int radius = startRadius; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos safe = findSupportSafeY(level, base.offset(dx, 0, dz), base.getY(), occupied);
                    if (safe != null) return safe;
                }
            }
        }
        return base;
    }

    private static BlockPos findSupportSafeY(ServerLevel level, BlockPos pos, int baseY, List<BlockPos> occupied) {
        for (int dy = 1; dy >= -4; dy--) {
            BlockPos candidate = new BlockPos(pos.getX(), baseY + dy, pos.getZ());
            if (isSupportSpawnSafe(level, candidate, occupied)) return candidate;
        }
        return null;
    }

    private static boolean isSupportSpawnSafe(ServerLevel level, BlockPos pos, List<BlockPos> occupied) {
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        if (isNearOccupiedSpawn(pos, occupied)) return false;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        return !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static boolean isNearOccupiedSpawn(BlockPos pos, List<BlockPos> occupied) {
        if (occupied == null || occupied.isEmpty()) return false;
        for (BlockPos other : occupied) {
            if (other == null) continue;
            int dx = other.getX() - pos.getX();
            int dy = other.getY() - pos.getY();
            int dz = other.getZ() - pos.getZ();
            if (dx * dx + dy * dy + dz * dz <= 9) return true;
        }
        return false;
    }

    private static BlockPos offset(BlockPos base, double x, double z) {
        return base.offset((int)Math.round(x), 0, (int)Math.round(z));
    }

    private static void addIfPresent(List<ItemStack> supplies, ItemStack stack) {
        if (supplies != null && stack != null && !stack.isEmpty()) supplies.add(stack);
    }

    private static String initialStatusFor(ObjectiveType type) {
        return switch (type) {
            case ELIMINATE -> "objective.tac_rogue.status.eliminate_remaining";
            case SECURE_TERMINAL -> "objective.tac_rogue.status.secure_idle";
            case HOLD_POSITION -> "objective.tac_rogue.status.hold_enter";
            case RECOVER_CACHE -> "objective.tac_rogue.status.cache_find";
            case HUNT_ELITE -> "objective.tac_rogue.status.elite_alive";
            case ESCAPE_ROUTE -> "objective.tac_rogue.status.escape_find";
        };
    }

    private static String statusFor(FloorInstanceManager.FloorInstance instance, ObjectiveType type) {
        if (instance.objectiveCompleted) return "objective.tac_rogue.status.complete";
        if (instance.objectiveStatusKey != null && !instance.objectiveStatusKey.isBlank()) return instance.objectiveStatusKey;
        return initialStatusFor(type);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public static void clearMemory() {
        REWARDED_ELITES.clear();
        DEBUG_OBJECTIVE_OVERRIDES.clear();
    }
}
