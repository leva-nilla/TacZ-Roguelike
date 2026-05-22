package com.levanilla.rogue.core.service;

import com.levanilla.rogue.networking.PopupNotificationMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class LowHealthChallengeService {
    public static final float CAP_RATIO = 0.35f;

    private static final String REQUEST_KEY = "TacRogueLowHealthRequested";
    private static final String ACTIVE_KEY = "TacRogueLowHealthActive";

    private LowHealthChallengeService() {}

    public static boolean isRequestedPayload(String raw) {
        if (raw == null || raw.isBlank()) return false;
        return raw.toLowerCase(java.util.Locale.ROOT).contains("lowhp");
    }

    public static void setRequested(ServerPlayer player, boolean requested) {
        if (player == null) return;
        if (requested) {
            player.getPersistentData().putBoolean(REQUEST_KEY, true);
        } else {
            player.getPersistentData().remove(REQUEST_KEY);
        }
    }

    public static void activateForFloor(ServerPlayer player) {
        if (player == null) return;
        boolean requested = player.getPersistentData().getBoolean(REQUEST_KEY);
        player.getPersistentData().remove(REQUEST_KEY);
        if (requested) {
            player.getPersistentData().putBoolean(ACTIVE_KEY, true);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.translatable("popup.tac_rogue.low_health_mode.title"),
                Component.translatable("message.tac_rogue.low_health_mode.enabled"),
                120);
        } else {
            clearActive(player);
        }
    }

    public static boolean isActive(ServerPlayer player) {
        return player != null && player.getPersistentData().getBoolean(ACTIVE_KEY);
    }

    public static void clearActive(ServerPlayer player) {
        if (player == null) return;
        player.getPersistentData().remove(ACTIVE_KEY);
    }

    public static float capHealth(ServerPlayer player) {
        if (player == null) return 1.0f;
        return Math.max(1.0f, player.getMaxHealth() * CAP_RATIO);
    }

    public static void applyEntryHealth(ServerPlayer player) {
        if (!isActive(player)) return;
        player.setHealth(capHealth(player));
    }

    public static void enforceCap(ServerPlayer player) {
        if (!isActive(player)) return;
        float cap = capHealth(player);
        if (player.getHealth() > cap) {
            player.setHealth(cap);
        }
    }
}
