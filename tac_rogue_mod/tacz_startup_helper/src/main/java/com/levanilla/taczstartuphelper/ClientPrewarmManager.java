package com.levanilla.taczstartuphelper;

import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = TaczStartupHelper.MOD_ID, value = Dist.CLIENT)
public final class ClientPrewarmManager {
    private static final int LOBBY_DELAY_TICKS = Integer.getInteger("taczStartupHelper.assetPrewarmLobbyDelayTicks", 40);
    private static final int COMPLETE_LINGER_TICKS = Integer.getInteger("taczStartupHelper.assetPrewarmCompleteLingerTicks", 80);
    private static final int ASSET_BATCH_PER_TICK = Integer.getInteger("taczStartupHelper.assetPrewarmBatchPerTick", 2);

    private static final Object LOCK = new Object();
    private static final ArrayDeque<AssetTask> ASSET_QUEUE = new ArrayDeque<>();
    private static final Set<String> PENDING_ASSETS = new HashSet<>();
    private static final Set<String> SUBMITTED_ASSETS = new HashSet<>();

    private static int lobbyTicks;
    private static boolean indexed;
    private static boolean started;
    private static boolean completedLogged = true;
    private static int completeLingerTicks;
    private static int totalAssets;
    private static int knownDisplayCount = -1;

    private static volatile Field modelWarmUpTaskField;
    private static volatile Field lodWarmUpTaskField;
    private static volatile Field animationWarmUpTaskField;
    private static volatile boolean futureFieldsMissing;

    private ClientPrewarmManager() {}

    public static void resetAssetWarmup() {
        synchronized (LOCK) {
            ASSET_QUEUE.clear();
            PENDING_ASSETS.clear();
            SUBMITTED_ASSETS.clear();
            lobbyTicks = 0;
            indexed = false;
            started = false;
            completedLogged = true;
            completeLingerTicks = 0;
            totalAssets = 0;
            knownDisplayCount = -1;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            lobbyTicks = 0;
            return;
        }
        if (!"tac_rogue:lobby_dimension".equals(minecraft.level.dimension().location().toString())) {
            return;
        }
        if (++lobbyTicks < LOBBY_DELAY_TICKS) {
            return;
        }

        ensureAssetQueue();

        int remaining;
        synchronized (LOCK) {
            remaining = PENDING_ASSETS.size();
        }
        if (remaining <= 0) {
            tickCompleteLinger();
            return;
        }

        if (!started) {
            logDebug("starting lobby TacZ asset prewarm with " + remaining + " tasks and batch=" + ASSET_BATCH_PER_TICK);
            started = true;
        }

        int submitted = 0;
        while (submitted < Math.max(1, ASSET_BATCH_PER_TICK)) {
            AssetTask task = pollNextAsset();
            if (task == null) {
                break;
            }
            submitAssetWarmup(task);
            submitted++;
        }
    }

    private static void ensureAssetQueue() {
        int displayCount = ClientIndexManager.GUN_DISPLAY.size();
        synchronized (LOCK) {
            if (indexed && knownDisplayCount == displayCount) {
                return;
            }
            ASSET_QUEUE.clear();
            PENDING_ASSETS.clear();
            SUBMITTED_ASSETS.clear();
            totalAssets = 0;
            indexed = false;
            started = false;
            completedLogged = true;
            completeLingerTicks = 0;
            knownDisplayCount = displayCount;
        }
        if (displayCount <= 0) {
            return;
        }

        ClientIndexManager.GUN_DISPLAY.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> {
                enqueueAsset(entry.getKey(), AssetPhase.MODEL);
                enqueueAsset(entry.getKey(), AssetPhase.LOD);
                enqueueAsset(entry.getKey(), AssetPhase.RUNTIME);
            });

        synchronized (LOCK) {
            indexed = true;
            completedLogged = totalAssets <= 0;
        }
        logDebug("queued " + totalAssets + " TacZ asset warmup tasks from " + displayCount + " gun displays");
    }

    private static void enqueueAsset(ResourceLocation displayId, AssetPhase phase) {
        AssetTask task = new AssetTask(displayId, phase);
        synchronized (LOCK) {
            ASSET_QUEUE.addLast(task);
            PENDING_ASSETS.add(task.key());
            totalAssets = PENDING_ASSETS.size();
        }
    }

    private static AssetTask pollNextAsset() {
        synchronized (LOCK) {
            while (!ASSET_QUEUE.isEmpty()) {
                AssetTask next = ASSET_QUEUE.removeFirst();
                if (PENDING_ASSETS.contains(next.key()) && SUBMITTED_ASSETS.add(next.key())) {
                    return next;
                }
            }
        }
        return null;
    }

    private static void submitAssetWarmup(AssetTask task) {
        GunDisplayInstance display = ClientIndexManager.GUN_DISPLAY.get(task.displayId());
        if (display == null) {
            markAssetLoaded(task.key());
            return;
        }
        try {
            switch (task.phase()) {
                case MODEL -> {
                    display.warmUpModel();
                    trackWarmupFuture(display, "modelWarmUpTask", task.key());
                }
                case LOD -> {
                    display.warmUpLod();
                    trackWarmupFuture(display, "lodWarmUpTask", task.key());
                }
                case RUNTIME -> {
                    display.warmUpRuntime();
                    trackWarmupFuture(display, "animationWarmUpTask", task.key());
                }
            }
        } catch (Throwable throwable) {
            logDebug("TacZ asset warmup failed for " + task.key() + ": " + throwable.getClass().getSimpleName());
            markAssetLoaded(task.key());
        }
    }

    private static void trackWarmupFuture(GunDisplayInstance display, String fieldName, String key) {
        if (futureFieldsMissing) {
            markAssetLoaded(key);
            return;
        }
        try {
            Field field = futureField(fieldName);
            Object value = field.get(display);
            if (value instanceof CompletableFuture<?> future) {
                future.whenComplete((ignored, throwable) -> markAssetLoaded(key));
            } else {
                markAssetLoaded(key);
            }
        } catch (Throwable throwable) {
            futureFieldsMissing = true;
            logDebug("TacZ warmup future fields are unavailable; falling back to submitted progress");
            markAssetLoaded(key);
        }
    }

    private static Field futureField(String fieldName) throws NoSuchFieldException {
        Field cached = switch (fieldName) {
            case "modelWarmUpTask" -> modelWarmUpTaskField;
            case "lodWarmUpTask" -> lodWarmUpTaskField;
            case "animationWarmUpTask" -> animationWarmUpTaskField;
            default -> null;
        };
        if (cached != null) {
            return cached;
        }
        Field field = GunDisplayInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        switch (fieldName) {
            case "modelWarmUpTask" -> modelWarmUpTaskField = field;
            case "lodWarmUpTask" -> lodWarmUpTaskField = field;
            case "animationWarmUpTask" -> animationWarmUpTaskField = field;
            default -> {
            }
        }
        return field;
    }

    private static void markAssetLoaded(String key) {
        boolean completeNow = false;
        synchronized (LOCK) {
            PENDING_ASSETS.remove(key);
            if (totalAssets > 0 && PENDING_ASSETS.isEmpty() && !completedLogged) {
                completeNow = true;
                completedLogged = true;
                completeLingerTicks = COMPLETE_LINGER_TICKS;
            }
        }
        if (completeNow) {
            logDebug("TacZ asset prewarm complete");
        }
    }

    private static void tickCompleteLinger() {
        synchronized (LOCK) {
            if (!completedLogged && totalAssets > 0) {
                completedLogged = true;
                completeLingerTicks = COMPLETE_LINGER_TICKS;
            } else if (completeLingerTicks > 0) {
                completeLingerTicks--;
            }
        }
    }

    private static void logDebug(String message) {
        if (TaczStartupHelper.debugLogs()) {
            TaczStartupHelper.LOGGER.info("{} {}", TaczStartupHelper.PREFIX, message);
        } else {
            TaczStartupHelper.LOGGER.debug("{} {}", TaczStartupHelper.PREFIX, message);
        }
    }

    public static void setSoundIds(Collection<ResourceLocation> soundIds) {
        resetAssetWarmup();
    }

    public static void markSoundLoaded(ResourceLocation id) {
        if (id != null) {
            markAssetLoaded(id.toString());
        }
    }

    public static void prioritizeSoundIds(Collection<ResourceLocation> soundIds) {
        // TacZ 1.1.8 moved sound preparation into Minecraft's sound reload path.
    }

    public static int getTotalSoundCount() {
        synchronized (LOCK) {
            return totalAssets;
        }
    }

    public static int getPendingSoundCount() {
        synchronized (LOCK) {
            return PENDING_ASSETS.size();
        }
    }

    public static int getLoadedSoundCount() {
        synchronized (LOCK) {
            return Math.max(0, totalAssets - PENDING_ASSETS.size());
        }
    }

    public static boolean hasPendingPrewarm() {
        synchronized (LOCK) {
            return totalAssets > 0 && !PENDING_ASSETS.isEmpty();
        }
    }

    public static boolean shouldDisplayPrewarmStatus() {
        synchronized (LOCK) {
            return totalAssets > 0 && (!PENDING_ASSETS.isEmpty() || completeLingerTicks > 0);
        }
    }

    public static boolean hasStartedPrewarm() {
        synchronized (LOCK) {
            return started;
        }
    }

    public static float getProgress() {
        synchronized (LOCK) {
            if (totalAssets <= 0) return 1.0F;
            return Math.max(0.0F, Math.min(1.0F, (totalAssets - PENDING_ASSETS.size()) / (float) totalAssets));
        }
    }

    private enum AssetPhase {
        MODEL,
        LOD,
        RUNTIME
    }

    private record AssetTask(ResourceLocation displayId, AssetPhase phase) {
        String key() {
            return displayId + "#" + phase.name();
        }
    }
}
