package com.levanilla.rogue.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class JourneyMapRefreshCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(JourneyMapRefreshCompat.class);
    private static final int MAX_RADIUS = 192;
    private static final int DEFAULT_ATTEMPTS = 5;
    private static final int RETRY_DELAY_TICKS = 10;

    private static PendingRefresh pending;
    private static ReflectionAccess access;
    private static boolean lookupAttempted;
    private static boolean unavailableLogged;

    private JourneyMapRefreshCompat() {}

    public static void requestRefresh(int centerX, int centerZ, int radius) {
        int clampedRadius = Math.max(16, Math.min(MAX_RADIUS, radius));
        pending = new PendingRefresh(centerX, centerZ, clampedRadius, DEFAULT_ATTEMPTS, RETRY_DELAY_TICKS, true);
    }

    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !isRogueDimension(mc)) {
            return;
        }
        if (pending.delayTicks > 0) {
            pending.delayTicks--;
            return;
        }

        if (refreshNow(mc, pending)) {
            pending.attemptsLeft--;
            pending.clearRegionCache = false;
            pending.delayTicks = RETRY_DELAY_TICKS;
            if (pending.attemptsLeft <= 0) {
                pending = null;
            }
        } else {
            pending = null;
        }
    }

    private static boolean refreshNow(Minecraft mc, PendingRefresh refresh) {
        ReflectionAccess jm = journeyMap();
        if (jm == null) return false;
        try {
            if (refresh.clearRegionCache) {
                jm.regionImageCacheClear.invoke(jm.regionImageCache);
            }
            jm.invalidateChunkMDCache.invoke(jm.dataCache);

            int minChunkX = Math.floorDiv(refresh.centerX - refresh.radius, 16);
            int maxChunkX = Math.floorDiv(refresh.centerX + refresh.radius, 16);
            int minChunkZ = Math.floorDiv(refresh.centerZ - refresh.radius, 16);
            int maxChunkZ = Math.floorDiv(refresh.centerZ + refresh.radius, 16);
            int refreshed = 0;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (!mc.level.hasChunk(chunkX, chunkZ)) continue;
                    Object chunkMd = jm.getChunkMD.invoke(jm.dataCache, ChunkPos.asLong(chunkX, chunkZ));
                    if (chunkMd != null) {
                        jm.resetRenderTimes.invoke(chunkMd);
                        refreshed++;
                    }
                }
            }
            LOGGER.debug("[TacRogue] Requested JourneyMap rerender for {} chunks around {},{}",
                refreshed, refresh.centerX, refresh.centerZ);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.debug("[TacRogue] JourneyMap refresh failed", ex);
            return false;
        }
    }

    private static boolean isRogueDimension(Minecraft mc) {
        return mc.level.dimension().location().getNamespace().equals("tac_rogue")
            && mc.level.dimension().location().getPath().equals("rogue_dimension");
    }

    private static ReflectionAccess journeyMap() {
        if (access != null) return access;
        if (lookupAttempted) return null;
        lookupAttempted = true;
        try {
            Class<?> dataCacheClass = Class.forName("journeymap.client.data.DataCache");
            Field dataCacheInstanceField = dataCacheClass.getField("INSTANCE");
            Object dataCache = dataCacheInstanceField.get(null);
            Method invalidateChunkMDCache = dataCacheClass.getMethod("invalidateChunkMDCache");
            Method getChunkMD = dataCacheClass.getMethod("getChunkMD", long.class);

            Class<?> chunkMdClass = Class.forName("journeymap.client.model.ChunkMD");
            Method resetRenderTimes = chunkMdClass.getMethod("resetRenderTimes");

            Class<?> regionImageCacheClass = Class.forName("journeymap.client.model.RegionImageCache");
            Field regionImageCacheInstanceField = regionImageCacheClass.getField("INSTANCE");
            Object regionImageCache = regionImageCacheInstanceField.get(null);
            Method regionImageCacheClear = regionImageCacheClass.getMethod("clear");

            access = new ReflectionAccess(dataCache, invalidateChunkMDCache, getChunkMD,
                resetRenderTimes, regionImageCache, regionImageCacheClear);
            return access;
        } catch (ReflectiveOperationException | LinkageError ex) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                LOGGER.debug("[TacRogue] JourneyMap refresh hooks unavailable");
            }
            return null;
        }
    }

    private static final class PendingRefresh {
        private final int centerX;
        private final int centerZ;
        private final int radius;
        private int attemptsLeft;
        private int delayTicks;
        private boolean clearRegionCache;

        private PendingRefresh(int centerX, int centerZ, int radius, int attemptsLeft,
                               int delayTicks, boolean clearRegionCache) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.attemptsLeft = attemptsLeft;
            this.delayTicks = delayTicks;
            this.clearRegionCache = clearRegionCache;
        }
    }

    private record ReflectionAccess(
        Object dataCache,
        Method invalidateChunkMDCache,
        Method getChunkMD,
        Method resetRenderTimes,
        Object regionImageCache,
        Method regionImageCacheClear
    ) {}
}
