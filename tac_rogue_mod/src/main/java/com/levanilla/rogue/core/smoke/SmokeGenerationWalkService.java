package com.levanilla.rogue.core.smoke;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import com.levanilla.rogue.world.MapGenerator;
import com.levanilla.rogue.world.ThemeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "tac_rogue")
public final class SmokeGenerationWalkService {
    private static final BlockPos CENTER = new BlockPos(7500, 80, 7500);
    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();

    private SmokeGenerationWalkService() {}

    public static int start(ServerPlayer player, int secondsPerTheme) {
        if (player == null) return 0;
        ServerLevel rogue = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogue == null) {
            player.sendSystemMessage(Component.literal("[SMOKE] Rogue dimension is not loaded."));
            return 0;
        }
        int intervalTicks = Math.max(40, Math.min(600, secondsPerTheme * 20));
        stop(player, true);
        Session session = new Session(player.getUUID(), intervalTicks, player.server.getTickCount(),
            SmokeLogger.startRun("generation_walk"));
        SESSIONS.put(player.getUUID(), session);
        player.sendSystemMessage(Component.literal("[SMOKE] generation_walk started interval="
            + secondsPerTheme + "s. Use /rogue_admin debug smoke generation_stop to stop."));
        generateCurrent(player.server, player, session);
        return 1;
    }

    public static int stop(ServerPlayer player, boolean clearWorld) {
        if (player == null) return 0;
        Session removed = SESSIONS.remove(player.getUUID());
        ServerLevel rogue = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (clearWorld && rogue != null) {
            discardSmokeEntities(rogue);
            MapGenerator.clearStoredDungeon(rogue, CENTER);
        }
        if (removed != null) {
            SmokeLogger.pass("generation_walk", "walk.stop", "generation walk stops",
                "index=" + removed.index, "clearWorld=" + clearWorld, 0L);
            player.sendSystemMessage(Component.literal("[SMOKE] generation_walk stopped."));
            return 1;
        }
        return 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long tick = server.getTickCount();
        for (Session session : java.util.List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                SESSIONS.remove(session.playerId);
                continue;
            }
            if (session.finished || tick < session.nextSwitchTick) continue;
            session.index++;
            if (session.index >= totalThemes()) {
                session.finished = true;
                SmokeLogger.pass("generation_walk", "walk.finish", "all themes were shown",
                    "shown=" + session.index, "last remains for inspection", 0L);
                player.sendSystemMessage(Component.literal("[SMOKE] generation_walk finished. Last theme remains."));
                continue;
            }
            generateCurrent(server, player, session);
        }
    }

    private static void generateCurrent(MinecraftServer server, ServerPlayer player, Session session) {
        ServerLevel rogue = server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogue == null) return;
        ThemeRef ref = themeAt(session.index);
        if (ref == null) return;
        ThemeManager.ThemeInstance theme = ThemeManager.getThemeForIndices(ref.biomeIndex, ref.variantIndex);
        String instanceId = "smoke-walk-" + session.index;
        discardSmokeEntities(rogue);
        MapGenerator.clearStoredDungeon(rogue, CENTER);

        try {
            MapGenerator.GenerationJob job = MapGenerator.generateRoomJob(
                rogue,
                CENTER,
                theme,
                session.index + 1,
                0x6A11_0800L,
                0x2200L + session.index,
                instanceId,
                "SMOKE_WALK",
                1,
                0,
                session.index + 1);
            int ticks = 0;
            while (!job.isComplete() && ticks < 80) {
                job.tick(25000);
                ticks++;
            }
            BlockPos spawn = job.getSpawnPos();
            if (spawn != null) {
                player.teleportTo(rogue, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    Direction.SOUTH.toYRot(), 0.0F);
                player.getPersistentData().putString(FloorInstanceManager.INSTANCE_ID_KEY, instanceId);
                RunManager.requestJourneyMapRefresh(player, spawn, 128);
            }
            boolean entered = spawn != null && player.level() == rogue && player.blockPosition().distSqr(spawn) <= 4.0D;
            boolean pass = job.isComplete() && entered && job.totalBlockUpdates() > 1000;
            SmokeLogger.record(session.runId, "generation_walk", caseId(theme),
                pass ? "PASS" : "FAIL",
                "theme is generated and entered for visual inspection",
                "complete=" + job.isComplete() + " entered=" + entered + " blocks=" + job.totalBlockUpdates(),
                "index=" + (session.index + 1) + "/" + totalThemes() + " spawn=" + spawn
                    + " style=" + ThemeManager.generationStyle(theme),
                0L);
            player.sendSystemMessage(Component.literal("[SMOKE] theme " + (session.index + 1) + "/" + totalThemes()
                + " " + theme.biomeName + "/" + theme.variantName
                + " style=" + ThemeManager.generationStyle(theme)
                + " next in " + (session.intervalTicks / 20) + "s"));
        } catch (Throwable ex) {
            SmokeLogger.record(session.runId, "generation_walk", caseId(theme), "FAIL",
                "theme walk generation does not throw", ex.getClass().getSimpleName(), ex.getMessage(), 0L);
            player.sendSystemMessage(Component.literal("[SMOKE] generation_walk failed: "
                + theme.biomeName + "/" + theme.variantName + " " + ex.getClass().getSimpleName()));
        }
        session.nextSwitchTick = server.getTickCount() + session.intervalTicks;
    }

    private static String caseId(ThemeManager.ThemeInstance theme) {
        return "theme." + theme.biomeName.toLowerCase(Locale.ROOT) + "."
            + theme.variantName.toLowerCase(Locale.ROOT);
    }

    private static ThemeRef themeAt(int index) {
        int remaining = index;
        for (int biome = 0; biome < ThemeManager.biomeCount(); biome++) {
            int variants = ThemeManager.variantCount(biome);
            if (remaining < variants) return new ThemeRef(biome, remaining);
            remaining -= variants;
        }
        return null;
    }

    private static int totalThemes() {
        int total = 0;
        for (int biome = 0; biome < ThemeManager.biomeCount(); biome++) {
            total += ThemeManager.variantCount(biome);
        }
        return total;
    }

    private static void discardSmokeEntities(ServerLevel level) {
        AABB bounds = new AABB(CENTER).inflate(96.0D, 32.0D, 96.0D);
        level.getEntitiesOfClass(Mob.class, bounds, mob ->
            mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY).startsWith("smoke-walk-")
                || mob.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY).startsWith("smoke-generation-"))
            .forEach(Mob::discard);
    }

    private record ThemeRef(int biomeIndex, int variantIndex) {}

    private static final class Session {
        private final UUID playerId;
        private final int intervalTicks;
        private final String runId;
        private int index;
        private long nextSwitchTick;
        private boolean finished;

        private Session(UUID playerId, int intervalTicks, long nowTick, String runId) {
            this.playerId = playerId;
            this.intervalTicks = intervalTicks;
            this.runId = runId;
            this.index = 0;
            this.nextSwitchTick = nowTick + intervalTicks;
        }
    }
}
