package com.levanilla.taczstartuphelper;

import com.tacz.guns.client.resource.ClientAssetsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = TaczStartupHelper.MOD_ID, value = Dist.CLIENT)
public final class ClientPrewarmManager {
    private static final long SOUND_BUDGET_NS = Long.getLong("taczStartupHelper.soundPrewarmBudgetNs", 4_000_000L);
    private static final int LOBBY_DELAY_TICKS = Integer.getInteger("taczStartupHelper.soundPrewarmLobbyDelayTicks", 40);

    private static final Object LOCK = new Object();
    private static final ArrayDeque<ResourceLocation> SOUND_QUEUE = new ArrayDeque<>();
    private static final Set<ResourceLocation> PENDING_SOUNDS = new HashSet<>();
    private static int lobbyTicks;
    private static boolean started;
    private static boolean completedLogged = true;
    private static int totalSounds;

    private ClientPrewarmManager() {}

    public static void setSoundIds(Collection<ResourceLocation> soundIds) {
        synchronized (LOCK) {
            SOUND_QUEUE.clear();
            PENDING_SOUNDS.clear();
            soundIds.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(id -> {
                    SOUND_QUEUE.addLast(id);
                    PENDING_SOUNDS.add(id);
                });
            totalSounds = PENDING_SOUNDS.size();
            lobbyTicks = 0;
            started = false;
            completedLogged = SOUND_QUEUE.isEmpty();
        }
        TaczStartupHelper.LOGGER.info("{} queued {} TacZ sounds for lobby prewarm", TaczStartupHelper.PREFIX, soundIds.size());
    }

    public static void markSoundLoaded(ResourceLocation id) {
        synchronized (LOCK) {
            PENDING_SOUNDS.remove(id);
        }
    }

    public static void prioritizeSoundIds(Collection<ResourceLocation> soundIds) {
        synchronized (LOCK) {
            List<ResourceLocation> sorted = new ArrayList<>(soundIds);
            sorted.sort(Comparator.comparing(ResourceLocation::toString));
            SOUND_QUEUE.removeIf(soundIds::contains);
            Collections.reverse(sorted);
            for (ResourceLocation id : sorted) {
                if (PENDING_SOUNDS.contains(id)) {
                    SOUND_QUEUE.addFirst(id);
                }
            }
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

        int remaining;
        synchronized (LOCK) {
            remaining = PENDING_SOUNDS.size();
        }
        if (remaining <= 0) {
            if (!completedLogged) {
                TaczStartupHelper.LOGGER.info("{} TacZ sound prewarm complete", TaczStartupHelper.PREFIX);
                completedLogged = true;
            }
            return;
        }

        if (!started) {
            TaczStartupHelper.LOGGER.info("{} starting lobby sound prewarm with {} sounds and {} ns/tick budget",
                TaczStartupHelper.PREFIX, remaining, SOUND_BUDGET_NS);
            started = true;
        }

        long tickStart = System.nanoTime();
        int loadedThisTick = 0;
        while (System.nanoTime() - tickStart < SOUND_BUDGET_NS || loadedThisTick == 0) {
            ResourceLocation next = pollNextSound();
            if (next == null) {
                break;
            }
            ClientAssetsManager.INSTANCE.getSoundBuffers(next);
            loadedThisTick++;
        }
    }

    private static ResourceLocation pollNextSound() {
        synchronized (LOCK) {
            while (!SOUND_QUEUE.isEmpty()) {
                ResourceLocation next = SOUND_QUEUE.removeFirst();
                if (PENDING_SOUNDS.contains(next)) {
                    return next;
                }
            }
        }
        return null;
    }

    public static int getTotalSoundCount() {
        synchronized (LOCK) {
            return totalSounds;
        }
    }

    public static int getPendingSoundCount() {
        synchronized (LOCK) {
            return PENDING_SOUNDS.size();
        }
    }

    public static int getLoadedSoundCount() {
        synchronized (LOCK) {
            return Math.max(0, totalSounds - PENDING_SOUNDS.size());
        }
    }

    public static boolean hasPendingPrewarm() {
        synchronized (LOCK) {
            return totalSounds > 0 && !PENDING_SOUNDS.isEmpty();
        }
    }

    public static boolean hasStartedPrewarm() {
        synchronized (LOCK) {
            return started;
        }
    }

    public static float getProgress() {
        synchronized (LOCK) {
            if (totalSounds <= 0) return 1.0F;
            return Math.max(0.0F, Math.min(1.0F, (totalSounds - PENDING_SOUNDS.size()) / (float) totalSounds));
        }
    }
}
