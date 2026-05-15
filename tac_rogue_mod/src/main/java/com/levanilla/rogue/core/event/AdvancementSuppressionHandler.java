package com.levanilla.rogue.core.event;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "tac_rogue")
public final class AdvancementSuppressionHandler {
    private static final String SUPPRESSED_ONCE_KEY = "TacRogueAdvancementsSuppressedOnce";
    private static final Set<UUID> REVOKING = ConcurrentHashMap.newKeySet();

    private AdvancementSuppressionHandler() {}

    @SubscribeEvent
    public static void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getProgressType() != AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT) return;
        revokeCriterion(player, event.getAdvancement(), event.getCriterionName());
    }

    @SubscribeEvent
    public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            revokeCompleted(player, event.getAdvancement());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!player.getPersistentData().getBoolean(SUPPRESSED_ONCE_KEY)) {
                player.getPersistentData().putBoolean(SUPPRESSED_ONCE_KEY, true);
                player.server.execute(() -> revokeAll(player));
            }
        }
    }

    private static void revokeAll(ServerPlayer player) {
        if (!REVOKING.add(player.getUUID())) return;
        try {
            for (Advancement advancement : player.server.getAdvancements().getAllAdvancements()) {
                revokeCompletedUnchecked(player, advancement);
            }
        } finally {
            REVOKING.remove(player.getUUID());
        }
    }

    private static void revokeCompleted(ServerPlayer player, Advancement advancement) {
        if (!REVOKING.add(player.getUUID())) return;
        try {
            revokeCompletedUnchecked(player, advancement);
        } finally {
            REVOKING.remove(player.getUUID());
        }
    }

    private static void revokeCompletedUnchecked(ServerPlayer player, Advancement advancement) {
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        List<String> completed = new ArrayList<>();
        for (String criterion : progress.getCompletedCriteria()) {
            completed.add(criterion);
        }
        for (String criterion : completed) {
            player.getAdvancements().revoke(advancement, criterion);
        }
    }

    private static void revokeCriterion(ServerPlayer player, Advancement advancement, String criterion) {
        if (advancement == null || criterion == null || criterion.isBlank()) return;
        if (!REVOKING.add(player.getUUID())) return;
        try {
            player.getAdvancements().revoke(advancement, criterion);
        } finally {
            REVOKING.remove(player.getUUID());
        }
    }
}
