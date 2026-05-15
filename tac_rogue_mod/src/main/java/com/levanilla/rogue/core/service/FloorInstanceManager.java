package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.PlayerRunData;
import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.event.CombatEventHandler;
import com.levanilla.rogue.networking.PopupNotificationMessage;
import com.levanilla.rogue.world.MapGenerator;
import com.levanilla.rogue.world.NpcManager;
import com.levanilla.rogue.world.RoomManager;
import com.levanilla.rogue.world.ThemeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FloorInstanceManager {
    public static final String INSTANCE_ID_KEY = "TacRogueInstanceId";
    public static final String FLOOR_KEY = "TacRogueFloor";
    public static final String MODE_KEY = "TacRogueMode";
    public static final String OWNER_KEY = "TacRogueOwnerUuid";
    public static final String DEBUG_PARTY_SIZE_KEY = "TacRogueDebugPartySize";

    private static final int PUBLIC_ORIGIN_X_BASE = -2_000_000;
    private static final int PUBLIC_ORIGIN_STRIDE = RunManager.FLOOR_OFFSET_Z;
    private static final int WAIT_TICKS = 15 * 20;
    private static final int TRACK_AUDIT_INTERVAL_TICKS = 100;
    private static final double BOSS_JOIN_HP_RATIO = 0.70D;

    private static final Map<String, FloorInstance> INSTANCES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PLAYER_INSTANCES = new ConcurrentHashMap<>();
    private static final Map<Integer, String> PUBLIC_INSTANCES_BY_FLOOR = new ConcurrentHashMap<>();

    private FloorInstanceManager() {}

    public enum EntryMode {
        SOLO,
        PUBLIC;

        public static EntryMode parse(String raw) {
            if (raw == null || raw.isBlank()) return SOLO;
            String lower = raw.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("solo") ? SOLO : PUBLIC;
        }
    }

    public enum State {
        WAITING,
        PREPARING,
        ACTIVE,
        CLEARED
    }

    public static final class FloorInstance {
        public final String id;
        public final int floor;
        public final EntryMode mode;
        public final UUID ownerUuid;
        public final Set<UUID> participants = ConcurrentHashMap.newKeySet();
        private final Set<UUID> trackedEntityUuids = ConcurrentHashMap.newKeySet();
        private final Set<UUID> trackedMobUuids = ConcurrentHashMap.newKeySet();
        private final Set<UUID> trackedSpawnedMobUuids = ConcurrentHashMap.newKeySet();
        private final Set<UUID> trackedBossUuids = ConcurrentHashMap.newKeySet();
        public final BlockPos origin;
        public final long createdTick;
        public final long runSeed;
        public final long floorSeedSalt;
        public volatile State state;
        public volatile long activeTick;
        public volatile long lastWaitSyncTick;
        public volatile long lastTrackingAuditTick;
        public volatile int initialParticipantCount;
        public volatile BlockPos spawnPos;
        public volatile MapGenerator.GenerationJob generationJob;

        private FloorInstance(String id, int floor, EntryMode mode, UUID ownerUuid, BlockPos origin,
                              long createdTick, long runSeed, long floorSeedSalt) {
            this.id = id;
            this.floor = floor;
            this.mode = mode;
            this.ownerUuid = ownerUuid;
            this.origin = origin;
            this.createdTick = createdTick;
            this.runSeed = runSeed;
            this.floorSeedSalt = floorSeedSalt;
            this.state = mode == EntryMode.SOLO ? State.ACTIVE : State.WAITING;
        }
    }

    public static FloorInstance getInstanceForPlayer(ServerPlayer player) {
        if (player == null) return null;
        String id = PLAYER_INSTANCES.get(player.getUUID());
        return id == null ? null : INSTANCES.get(id);
    }

    public static Collection<FloorInstance> getInstancesSnapshot() {
        return List.copyOf(INSTANCES.values());
    }

    public static void enterFloor(ServerPlayer player, int floor, EntryMode mode) {
        if (player == null || player.server == null || floor <= 0) return;
        PlayerRunData data = RunManager.getData(player);
        if (floor > Math.max(1, data.getMaxReachedFloor())) {
            player.sendSystemMessage(Component.literal("§c[LR-TAC] This floor is not unlocked for you."));
            return;
        }

        if (mode == EntryMode.SOLO) {
            createSoloInstance(player, floor);
        } else {
            joinOrCreatePublicInstance(player, floor);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        long tick = server.getTickCount();

        for (FloorInstance instance : getInstancesSnapshot()) {
            if (instance.state == State.WAITING && tick - instance.createdTick >= WAIT_TICKS) {
                activateInstance(server, instance);
            } else if (instance.state == State.PREPARING) {
                tickPreparingInstance(server, instance);
            } else if (instance.state == State.WAITING && tick - instance.lastWaitSyncTick >= 20) {
                syncWaitingParticipants(server, instance);
                instance.lastWaitSyncTick = tick;
            }
        }

        if (tick % GameConstants.FLOOR_CLEAR_CHECK_INTERVAL != 0) return;
        ServerLevel rogueLevel = server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogueLevel == null) return;

        for (FloorInstance instance : getInstancesSnapshot()) {
            if (instance.state != State.ACTIVE) continue;
            if (tick - instance.activeTick < 100) continue;
            auditTrackedEntities(rogueLevel, instance, false);
            if (isInstanceCleared(rogueLevel, instance)) {
                completeInstance(server, rogueLevel, instance);
            }
        }
    }

    public static void leaveInstance(ServerPlayer player, boolean markRunInactive) {
        if (player == null) return;
        String id = PLAYER_INSTANCES.remove(player.getUUID());
        syncWaitClear(player);
        if (id == null) {
            if (markRunInactive) {
                RunManager.getData(player).setRunActive(false);
            }
            return;
        }

        FloorInstance instance = INSTANCES.get(id);
        if (instance != null) {
            instance.participants.remove(player.getUUID());
            if (instance.participants.isEmpty()) {
                destroyInstance(player.server, instance);
            }
        }

        if (markRunInactive) {
            RunManager.getData(player).setRunActive(false);
        }
    }

    public static ServerPlayer findNearestParticipantForPosition(ServerLevel level, double x, double z, double maxDistance) {
        if (level == null || level.getServer() == null) return null;
        double best = Double.MAX_VALUE;
        ServerPlayer bestPlayer = null;
        double maxSqr = maxDistance * maxDistance;

        for (FloorInstance instance : INSTANCES.values()) {
            if (instance.state != State.ACTIVE && instance.state != State.CLEARED) continue;
            double dx = x - instance.origin.getX();
            double dz = z - instance.origin.getZ();
            double originDistance = dx * dx + dz * dz;
            if (originDistance > maxSqr) continue;

            for (UUID uuid : instance.participants) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || player.level() != level || !player.isAlive() || player.isSpectator()) continue;
                double distance = player.distanceToSqr(x, player.getY(), z);
                if (distance < best) {
                    best = distance;
                    bestPlayer = player;
                }
            }
        }
        return bestPlayer;
    }

    public static List<ServerPlayer> getParticipants(ServerLevel level, String instanceId) {
        FloorInstance instance = INSTANCES.get(instanceId);
        if (instance == null || level == null || level.getServer() == null) return List.of();
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID uuid : instance.participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) players.add(player);
        }
        return players;
    }

    public static List<ServerPlayer> getParticipantsForEntity(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return List.of();
        String id = entity.getPersistentData().getString(INSTANCE_ID_KEY);
        if (id.isBlank()) return List.of();
        return getParticipants(level, id);
    }

    public static boolean isParticipant(Entity entity, ServerPlayer player) {
        if (entity == null || player == null) return false;
        String id = entity.getPersistentData().getString(INSTANCE_ID_KEY);
        if (id.isBlank()) return true;
        FloorInstance instance = INSTANCES.get(id);
        return instance == null || instance.participants.contains(player.getUUID());
    }

    public static void stampEntity(Entity entity, String instanceId, int floor, EntryMode mode, UUID ownerUuid) {
        if (entity == null || instanceId == null || instanceId.isBlank()) return;
        entity.getPersistentData().putString(INSTANCE_ID_KEY, instanceId);
        entity.getPersistentData().putInt(FLOOR_KEY, floor);
        entity.getPersistentData().putString(MODE_KEY, mode.name());
        if (ownerUuid != null) {
            entity.getPersistentData().putString(OWNER_KEY, ownerUuid.toString());
        }
        registerStampedEntity(entity, instanceId);
    }

    private static void registerStampedEntity(Entity entity, String instanceId) {
        FloorInstance instance = INSTANCES.get(instanceId);
        if (entity == null || instance == null) return;
        UUID uuid = entity.getUUID();
        instance.trackedEntityUuids.add(uuid);
        if (entity instanceof Mob mob) {
            instance.trackedMobUuids.add(uuid);
            if (mob.getTags().contains("tac_rogue_spawned")) {
                instance.trackedSpawnedMobUuids.add(uuid);
            }
            if (mob.getTags().contains("rogue:boss")) {
                instance.trackedBossUuids.add(uuid);
            }
        }
    }

    public static void stampBlockEntity(BlockEntity blockEntity, String instanceId, int floor, EntryMode mode) {
        if (blockEntity == null || instanceId == null || instanceId.isBlank()) return;
        blockEntity.getPersistentData().putString(INSTANCE_ID_KEY, instanceId);
        blockEntity.getPersistentData().putInt(FLOOR_KEY, floor);
        blockEntity.getPersistentData().putString(MODE_KEY, mode.name());
        blockEntity.setChanged();
    }

    public static boolean hasChestClaimed(ChestBlockEntity chest, UUID playerUuid) {
        if (chest == null || playerUuid == null) return false;
        return chest.getPersistentData().getBoolean("TacRogueChestClaimed_" + playerUuid);
    }

    public static void markChestClaimed(ChestBlockEntity chest, UUID playerUuid) {
        if (chest == null || playerUuid == null) return;
        chest.getPersistentData().putBoolean("TacRogueChestClaimed_" + playerUuid, true);
        chest.setChanged();
    }

    public static void protectDropFor(ItemEntity item, ServerPlayer owner) {
        if (item == null || owner == null) return;
        String instanceId = PLAYER_INSTANCES.get(owner.getUUID());
        if (instanceId != null) {
            FloorInstance instance = INSTANCES.get(instanceId);
            if (instance != null) {
                stampEntity(item, instance.id, instance.floor, instance.mode, owner.getUUID());
            }
        }
        item.getPersistentData().putString(OWNER_KEY, owner.getUUID().toString());
    }

    public static boolean canPickupProtectedDrop(ItemEntity item, ServerPlayer player) {
        if (item == null || player == null) return true;
        String owner = item.getPersistentData().getString(OWNER_KEY);
        return owner.isBlank() || owner.equals(player.getUUID().toString());
    }

    private static void createSoloInstance(ServerPlayer player, int floor) {
        leaveInstance(player, false);
        long tick = player.server.getTickCount();
        String id = "solo-" + player.getUUID() + "-" + tick + "-" + UUID.randomUUID();
        BlockPos origin = RunManager.getPrivateDungeonOrigin(player);
        FloorInstance instance = new FloorInstance(id, floor, EntryMode.SOLO, player.getUUID(), origin,
            tick, System.nanoTime(), System.nanoTime() ^ tick);
        instance.participants.add(player.getUUID());
        instance.initialParticipantCount = 1;
        INSTANCES.put(id, instance);
        PLAYER_INSTANCES.put(player.getUUID(), id);
        syncWaitClear(player);
        activateInstance(player.server, instance);
    }

    private static void joinOrCreatePublicInstance(ServerPlayer player, int floor) {
        leaveInstance(player, false);
        FloorInstance existing = getPublicInstance(floor);
        if (existing != null && existing.state == State.ACTIVE) {
            if (!canJoinActiveInstance(player, existing)) return;
            addParticipant(player, existing, true);
            teleportParticipant(player, existing);
            applyMidRunJoinScaling(player.server, existing);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.literal("CO-OP JOIN"),
                Component.literal("Joined active Floor " + floor + " instance."),
                100);
            return;
        }
        if (existing != null && existing.state == State.PREPARING) {
            addParticipant(player, existing, false);
            syncWaitUpdate(player, existing, 1);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.literal("PUBLIC CO-OP"),
                Component.literal("Floor " + floor + " is deploying. Joining launch group."),
                100);
            return;
        }

        if (existing == null || existing.state == State.CLEARED) {
            long tick = player.server.getTickCount();
            String id = "public-floor-" + floor + "-" + tick + "-" + UUID.randomUUID();
            existing = new FloorInstance(id, floor, EntryMode.PUBLIC, player.getUUID(), publicOriginForFloor(floor),
                tick, System.nanoTime(), System.nanoTime() ^ ((long) floor << 32));
            INSTANCES.put(id, existing);
            PUBLIC_INSTANCES_BY_FLOOR.put(floor, id);
        }

        addParticipant(player, existing, false);
        int secondsLeft = waitingSecondsLeft(player.server, existing);
        syncWaitUpdate(player, existing, secondsLeft);
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.SYSTEM,
            Component.literal("PUBLIC CO-OP"),
            Component.literal("Floor " + floor + " starts in " + secondsLeft + "s."),
            120);
    }

    private static FloorInstance getPublicInstance(int floor) {
        String id = PUBLIC_INSTANCES_BY_FLOOR.get(floor);
        if (id == null) return null;
        FloorInstance instance = INSTANCES.get(id);
        if (instance == null || instance.mode != EntryMode.PUBLIC) {
            PUBLIC_INSTANCES_BY_FLOOR.remove(floor);
            return null;
        }
        return instance;
    }

    private static boolean canJoinActiveInstance(ServerPlayer player, FloorInstance instance) {
        if (ThemeManager.isBossFloor(instance.floor)) {
            LivingEntity boss = findBoss(player.server.getLevel(CommonEventHandler.ROGUE_DIM), instance);
            if (boss != null && boss.getHealth() <= boss.getMaxHealth() * BOSS_JOIN_HP_RATIO) {
                player.sendSystemMessage(Component.literal("§c[LR-TAC] Boss engagement is too far along to join."));
                return false;
            }
        }
        return true;
    }

    private static void addParticipant(ServerPlayer player, FloorInstance instance, boolean active) {
        instance.participants.add(player.getUUID());
        PLAYER_INSTANCES.put(player.getUUID(), instance.id);
        PlayerRunData data = RunManager.getData(player);
        data.setCurrentFloor(instance.floor);
        data.setDungeonOrigin(instance.origin);
        data.setFloorCleared(false);
        data.setRunActive(active);
        if (data.getMaxReachedFloor() < 1) data.setMaxReachedFloor(1);
        data.setRunSeed(instance.runSeed);
        data.setFloorSeedSalt(instance.floorSeedSalt);
        if (active && player.server != null) {
            data.setFloorStartTick(player.server.getTickCount());
        }
        RunManager.refreshThemeName(data);
        RunManager.syncPlayer(player);
    }

    private static void activateInstance(MinecraftServer server, FloorInstance instance) {
        if (server == null || instance.state == State.ACTIVE && instance.spawnPos != null) return;
        if (instance.state == State.PREPARING) return;
        ServerLevel rogueLevel = server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogueLevel == null) return;
        if (instance.participants.isEmpty()) {
            destroyInstance(server, instance);
            return;
        }

        instance.state = State.PREPARING;
        instance.initialParticipantCount = Math.max(Math.max(1, instance.participants.size()), getDebugPartySize(server, instance));
        FloorService.clearDungeonEntities(rogueLevel, instance.origin);
        instance.generationJob = MapGenerator.generateRoomJob(
            rogueLevel,
            instance.origin,
            null,
            instance.floor,
            instance.runSeed,
            instance.floorSeedSalt,
            instance.id,
            instance.mode.name(),
            instance.initialParticipantCount);
    }

    private static void tickPreparingInstance(MinecraftServer server, FloorInstance instance) {
        if (server == null || instance == null) return;
        if (instance.participants.isEmpty()) {
            destroyInstance(server, instance);
            return;
        }
        MapGenerator.GenerationJob job = instance.generationJob;
        if (job == null) {
            destroyInstance(server, instance);
            return;
        }
        if (!job.tick(MapGenerator.DEFAULT_GENERATION_BLOCKS_PER_TICK)) {
            if (instance.mode == EntryMode.PUBLIC && server.getTickCount() - instance.lastWaitSyncTick >= 20) {
                for (UUID uuid : List.copyOf(instance.participants)) {
                    ServerPlayer participant = server.getPlayerList().getPlayer(uuid);
                    if (participant != null) syncWaitUpdate(participant, instance, 1);
                }
                instance.lastWaitSyncTick = server.getTickCount();
            }
            return;
        }
        instance.spawnPos = job.getSpawnPos();
        instance.generationJob = null;
        finalizeActivation(server, instance);
    }

    private static void finalizeActivation(MinecraftServer server, FloorInstance instance) {
        if (server == null || instance == null || instance.spawnPos == null) return;
        instance.state = State.ACTIVE;
        instance.activeTick = server.getTickCount();
        ServerLevel rogueLevel = server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogueLevel != null) {
            auditTrackedEntities(rogueLevel, instance, true);
            FloorService.clearRetryStaging(rogueLevel, instance.origin);
        }

        for (UUID uuid : List.copyOf(instance.participants)) {
            ServerPlayer participant = server.getPlayerList().getPlayer(uuid);
            if (participant == null) {
                instance.participants.remove(uuid);
                PLAYER_INSTANCES.remove(uuid);
                continue;
            }
            addParticipant(participant, instance, true);
            PlayerPerkTickService.invalidateSnapshot(participant);
            PlayerPerkTickService.applyPerkStats(participant);
            syncWaitClear(participant);
            teleportParticipant(participant, instance);

            long seed = server.getWorldData().worldGenOptions().seed();
            ThemeManager.ThemeInstance theme = ThemeManager.getThemeForFloor(instance.floor, seed);
            PopupNotificationMessage.send(
                participant,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.translatable("popup.tac_rogue.floor.title", instance.floor),
                Component.translatable("message.tac_rogue.floor_enter", instance.floor, theme.displayName),
                120);
        }

        if (instance.participants.isEmpty()) {
            destroyInstance(server, instance);
        }
    }

    private static void teleportParticipant(ServerPlayer player, FloorInstance instance) {
        ServerLevel rogueLevel = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogueLevel == null || instance.spawnPos == null) return;
        RunManager.safeTeleport(player, rogueLevel, instance.spawnPos);
    }

    private static void syncWaitingParticipants(MinecraftServer server, FloorInstance instance) {
        if (server == null || instance == null || instance.state != State.WAITING) return;
        int secondsLeft = waitingSecondsLeft(server, instance);
        for (UUID uuid : List.copyOf(instance.participants)) {
            ServerPlayer participant = server.getPlayerList().getPlayer(uuid);
            if (participant != null) {
                syncWaitUpdate(participant, instance, secondsLeft);
            }
        }
    }

    private static int waitingSecondsLeft(MinecraftServer server, FloorInstance instance) {
        if (server == null || instance == null) return 1;
        return Math.max(1, (int) Math.ceil((WAIT_TICKS - (server.getTickCount() - instance.createdTick)) / 20.0D));
    }

    private static void syncWaitUpdate(ServerPlayer player, FloorInstance instance, int secondsLeft) {
        if (player == null || instance == null) return;
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("coop_wait:" + instance.floor + ":" + Math.max(1, secondsLeft)));
    }

    private static void syncWaitClear(ServerPlayer player) {
        if (player == null) return;
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("coop_wait_clear"));
    }

    private static boolean isInstanceCleared(ServerLevel level, FloorInstance instance) {
        List<Mob> alive = getAliveSpawnedMobs(level, instance);
        boolean bossFloor = ThemeManager.isBossFloor(instance.floor);
        boolean bossAlive = alive.stream().anyMatch(mob -> mob.getTags().contains("rogue:boss"));
        return alive.isEmpty() || (bossFloor && !bossAlive);
    }

    private static void completeInstance(MinecraftServer server, ServerLevel level, FloorInstance instance) {
        instance.state = State.CLEARED;

        for (UUID uuid : List.copyOf(instance.participants)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            Entity npc = NpcManager.spawnExtractionOfficer(level, NpcManager.findExtractionSpawnNear(level, player), instance.floor);
            stampEntity(npc, instance.id, instance.floor, instance.mode, null);

            PlayerRunData data = RunManager.getData(player);
            data.setFloorCleared(true);
            data.setRunActive(true);
            data.setMaxReachedFloor(Math.max(data.getMaxReachedFloor(), instance.floor + 1));
            RunManager.syncPlayer(player);

            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.translatable("popup.tac_rogue.extraction.title"),
                Component.translatable("message.tac_rogue.extraction_arrived"),
                150);

            QuestManager.advanceQuest(player, QuestManager.QuestType.FLOOR_CLEAR, 1);

            int speedrunLimitTicks = (120 + instance.floor * 10) * 20;
            long elapsedTicks = server.getTickCount() - data.getFloorStartTick();
            if (elapsedTicks <= speedrunLimitTicks) {
                QuestManager.advanceQuest(player, QuestManager.QuestType.SPEEDRUN, 1);
            }
            if (player.getHealth() >= player.getMaxHealth() * 0.5f) {
                QuestManager.advanceQuest(player, QuestManager.QuestType.SURVIVE, 1);
            }
            if (player.getHealth() <= player.getMaxHealth() * 0.35f) {
                QuestManager.advanceQuest(player, QuestManager.QuestType.LOW_HEALTH_CLEAR, 1);
            }
            long lastDmg = CombatEventHandler.getLastDamageTick(player.getUUID());
            if (lastDmg <= data.getFloorStartTick()) {
                QuestManager.advanceQuest(player, QuestManager.QuestType.NO_DAMAGE, 1);
            }
        }
    }

    private static void applyMidRunJoinScaling(MinecraftServer server, FloorInstance instance) {
        ServerLevel level = server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (level == null) return;
        auditTrackedEntities(level, instance, true);
        for (Mob mob : getAliveInstanceMobs(level, instance)) {
            double bonus = mob.getTags().contains("rogue:boss") ? 0.18D : 0.12D;
            var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth == null) continue;
            double oldMax = maxHealth.getBaseValue();
            double newMax = oldMax * (1.0D + bonus);
            maxHealth.setBaseValue(newMax);
            mob.setHealth((float) Math.min(newMax, mob.getHealth() + (newMax - oldMax)));
        }

        if (!ThemeManager.isBossFloor(instance.floor) && instance.spawnPos != null) {
            int reinforcements = Math.min(4, 1 + instance.floor / 20);
            RoomManager.spawnMobs(level, instance.spawnPos.offset(4, 0, 4), reinforcements,
                instance.floor, ThemeManager.getBiomeIndex(instance.floor, level.getSeed()),
                instance.id, instance.mode.name(), Math.max(1, instance.participants.size()));
        }
    }

    public static void debugSetPartySize(ServerPlayer player, int players) {
        if (player == null) return;
        int clamped = Math.max(1, Math.min(8, players));
        player.getPersistentData().putInt(DEBUG_PARTY_SIZE_KEY, clamped);
        player.sendSystemMessage(Component.literal("§a[MP-DEBUG] virtual party size=" + clamped));
    }

    public static int debugDescribe(ServerPlayer player) {
        if (player == null || player.server == null) return 0;
        player.sendSystemMessage(Component.literal("§b[MP-DEBUG] active instances=" + INSTANCES.size()));
        String ownId = PLAYER_INSTANCES.get(player.getUUID());
        for (FloorInstance instance : getInstancesSnapshot()) {
            boolean own = instance.id.equals(ownId);
            long age = Math.max(0L, player.server.getTickCount() - instance.createdTick);
            player.sendSystemMessage(Component.literal((own ? "§e* " : "§7- ")
                + "id=" + shortId(instance.id)
                + " floor=" + instance.floor
                + " mode=" + instance.mode
                + " state=" + instance.state
                + " participants=" + instance.participants.size()
                + " initial=" + instance.initialParticipantCount
                + " ageTicks=" + age
                + " origin=" + instance.origin));
        }
        int debugParty = player.getPersistentData().getInt(DEBUG_PARTY_SIZE_KEY);
        player.sendSystemMessage(Component.literal("§b[MP-DEBUG] your virtual party size=" + Math.max(1, debugParty)));
        return INSTANCES.size();
    }

    public static boolean debugForceStart(ServerPlayer player) {
        return requestStartWaitingInstance(player, true);
    }

    public static boolean requestStartWaitingInstance(ServerPlayer player) {
        return requestStartWaitingInstance(player, false);
    }

    private static boolean requestStartWaitingInstance(ServerPlayer player, boolean debug) {
        FloorInstance instance = getInstanceForPlayer(player);
        if (player == null || player.server == null || instance == null) return false;
        if (instance.state != State.WAITING) return false;
        activateInstance(player.server, instance);
        player.sendSystemMessage(Component.literal((debug ? "§a[MP-DEBUG] forced start " : "§a[CO-OP] started waiting instance ")
            + shortId(instance.id)));
        return true;
    }

    public static boolean debugForceComplete(ServerPlayer player) {
        FloorInstance instance = getInstanceForPlayer(player);
        if (player == null || player.server == null || instance == null) return false;
        ServerLevel level = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (level == null) return false;
        completeInstance(player.server, level, instance);
        player.sendSystemMessage(Component.literal("§a[MP-DEBUG] forced complete " + shortId(instance.id)));
        return true;
    }

    public static boolean debugApplyVirtualJoin(ServerPlayer player, int count) {
        FloorInstance instance = getInstanceForPlayer(player);
        if (player == null || player.server == null || instance == null || instance.state != State.ACTIVE) return false;
        int loops = Math.max(1, Math.min(7, count));
        for (int i = 0; i < loops; i++) {
            applyMidRunJoinScaling(player.server, instance);
        }
        player.sendSystemMessage(Component.literal("§a[MP-DEBUG] applied virtual mid-run join x" + loops));
        return true;
    }

    public static boolean debugLeave(ServerPlayer player) {
        if (player == null) return false;
        boolean hadInstance = getInstanceForPlayer(player) != null;
        leaveInstance(player, true);
        player.sendSystemMessage(Component.literal("§a[MP-DEBUG] left instance=" + hadInstance));
        return hadInstance;
    }

    private static int getDebugPartySize(MinecraftServer server, FloorInstance instance) {
        if (server == null || instance == null || instance.ownerUuid == null) return 1;
        ServerPlayer owner = server.getPlayerList().getPlayer(instance.ownerUuid);
        if (owner == null) return 1;
        return Math.max(1, Math.min(8, owner.getPersistentData().getInt(DEBUG_PARTY_SIZE_KEY)));
    }

    private static String shortId(String id) {
        if (id == null) return "";
        return id.length() <= 12 ? id : id.substring(0, 12);
    }

    private static LivingEntity findBoss(ServerLevel level, FloorInstance instance) {
        if (level == null) return null;
        boolean hadTrackedBoss = !instance.trackedBossUuids.isEmpty();
        LivingEntity trackedBoss = findTrackedBoss(level, instance);
        if (trackedBoss != null || hadTrackedBoss) return trackedBoss;

        List<Mob> bosses = level.getEntitiesOfClass(Mob.class, instanceSearchArea(instance), mob ->
            mob.isAlive()
                && mob.getTags().contains("rogue:boss")
                && instance.id.equals(mob.getPersistentData().getString(INSTANCE_ID_KEY)));
        return bosses.isEmpty() ? null : bosses.get(0);
    }

    private static void destroyInstance(MinecraftServer server, FloorInstance instance) {
        if (instance == null) return;
        INSTANCES.remove(instance.id);
        if (instance.mode == EntryMode.PUBLIC) {
            PUBLIC_INSTANCES_BY_FLOOR.remove(instance.floor, instance.id);
        }
        for (UUID uuid : List.copyOf(instance.participants)) {
            PLAYER_INSTANCES.remove(uuid, instance.id);
        }

        if (server == null) return;
        ServerLevel rogueLevel = server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogueLevel == null) return;
        if (instance.state == State.PREPARING && instance.generationJob != null) {
            instance.generationJob.cancel();
            instance.generationJob = null;
            MapGenerator.clearStoredDungeon(rogueLevel, instance.origin);
        }
        auditTrackedEntities(rogueLevel, instance, true);
        boolean hadTrackedEntities = !instance.trackedEntityUuids.isEmpty();
        discardTrackedEntities(rogueLevel, instance);
        if (!hadTrackedEntities) {
            discardInstanceEntitiesByArea(rogueLevel, instance);
        }
    }

    private static List<Mob> getAliveSpawnedMobs(ServerLevel level, FloorInstance instance) {
        List<Mob> tracked = getTrackedAliveMobs(level, instance, instance.trackedSpawnedMobUuids, true);
        return tracked != null ? tracked : level.getEntitiesOfClass(Mob.class, instanceSearchArea(instance), mob ->
            mob.isAlive()
                && mob.getTags().contains("tac_rogue_spawned")
                && instance.id.equals(mob.getPersistentData().getString(INSTANCE_ID_KEY))
                && mob.getPersistentData().getInt("TacRogueSpawnFloor") == instance.floor);
    }

    private static List<Mob> getAliveInstanceMobs(ServerLevel level, FloorInstance instance) {
        List<Mob> tracked = getTrackedAliveMobs(level, instance, instance.trackedMobUuids, false);
        return tracked != null ? tracked : level.getEntitiesOfClass(Mob.class, instanceSearchArea(instance), mob ->
            mob.isAlive() && instance.id.equals(mob.getPersistentData().getString(INSTANCE_ID_KEY)));
    }

    private static List<Mob> getTrackedAliveMobs(ServerLevel level, FloorInstance instance, Set<UUID> uuids,
                                                 boolean requireSpawnedFloor) {
        if (uuids.isEmpty()) return null;
        List<Mob> alive = new ArrayList<>();
        for (UUID uuid : List.copyOf(uuids)) {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof Mob mob) || entity.isRemoved()) {
                forgetTrackedEntity(instance, uuid);
                continue;
            }
            if (!instance.id.equals(mob.getPersistentData().getString(INSTANCE_ID_KEY))) {
                forgetTrackedEntity(instance, uuid);
                continue;
            }
            if (!mob.isAlive()) {
                forgetTrackedMob(instance, uuid);
                continue;
            }
            if (requireSpawnedFloor
                && (!mob.getTags().contains("tac_rogue_spawned")
                    || mob.getPersistentData().getInt("TacRogueSpawnFloor") != instance.floor)) {
                instance.trackedSpawnedMobUuids.remove(uuid);
                continue;
            }
            alive.add(mob);
        }
        return alive;
    }

    private static void auditTrackedEntities(ServerLevel level, FloorInstance instance, boolean force) {
        if (level == null || instance == null) return;
        long tick = level.getServer() == null ? level.getGameTime() : level.getServer().getTickCount();
        if (!force && tick >= instance.lastTrackingAuditTick
            && tick - instance.lastTrackingAuditTick < TRACK_AUDIT_INTERVAL_TICKS) {
            return;
        }
        instance.lastTrackingAuditTick = tick;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, instanceSearchArea(instance), entity ->
            !(entity instanceof ServerPlayer)
                && instance.id.equals(entity.getPersistentData().getString(INSTANCE_ID_KEY)))) {
            registerStampedEntity(entity, instance.id);
        }
    }

    private static LivingEntity findTrackedBoss(ServerLevel level, FloorInstance instance) {
        if (instance.trackedBossUuids.isEmpty()) return null;
        for (UUID uuid : List.copyOf(instance.trackedBossUuids)) {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof Mob mob) || entity.isRemoved()) {
                forgetTrackedEntity(instance, uuid);
                continue;
            }
            if (!instance.id.equals(mob.getPersistentData().getString(INSTANCE_ID_KEY))) {
                forgetTrackedEntity(instance, uuid);
                continue;
            }
            if (!mob.isAlive() || !mob.getTags().contains("rogue:boss")) {
                instance.trackedBossUuids.remove(uuid);
                continue;
            }
            return mob;
        }
        return null;
    }

    private static void discardTrackedEntities(ServerLevel level, FloorInstance instance) {
        AABB area = instanceSearchArea(instance);
        for (UUID uuid : List.copyOf(instance.trackedEntityUuids)) {
            Entity entity = level.getEntity(uuid);
            if (entity == null || entity.isRemoved()) {
                forgetTrackedEntity(instance, uuid);
                continue;
            }
            if (entity instanceof ServerPlayer) {
                forgetTrackedEntity(instance, uuid);
                continue;
            }
            if (!instance.id.equals(entity.getPersistentData().getString(INSTANCE_ID_KEY))) {
                forgetTrackedEntity(instance, uuid);
                continue;
            }
            if (!area.contains(entity.position())) continue;
            entity.remove(Entity.RemovalReason.DISCARDED);
            forgetTrackedEntity(instance, uuid);
        }
    }

    private static void discardInstanceEntitiesByArea(ServerLevel level, FloorInstance instance) {
        for (Entity entity : List.copyOf(level.getEntitiesOfClass(Entity.class, instanceSearchArea(instance), entity ->
            !(entity instanceof ServerPlayer)
                && instance.id.equals(entity.getPersistentData().getString(INSTANCE_ID_KEY))))) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    private static AABB instanceSearchArea(FloorInstance instance) {
        return new AABB(instance.origin).inflate(GameConstants.FLOOR_CLEAR_RADIUS);
    }

    private static void forgetTrackedEntity(FloorInstance instance, UUID uuid) {
        instance.trackedEntityUuids.remove(uuid);
        forgetTrackedMob(instance, uuid);
    }

    private static void forgetTrackedMob(FloorInstance instance, UUID uuid) {
        instance.trackedMobUuids.remove(uuid);
        instance.trackedSpawnedMobUuids.remove(uuid);
        instance.trackedBossUuids.remove(uuid);
    }

    private static BlockPos publicOriginForFloor(int floor) {
        return new BlockPos(PUBLIC_ORIGIN_X_BASE + floor * PUBLIC_ORIGIN_STRIDE, GameConstants.DUNGEON_BASE_Y, 0);
    }
}
