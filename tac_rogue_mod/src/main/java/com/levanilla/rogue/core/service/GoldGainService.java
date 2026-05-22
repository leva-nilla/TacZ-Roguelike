package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CurrencyManager;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.networking.SyncDataMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class GoldGainService {
    private GoldGainService() {}

    public static void award(ServerPlayer player, int amount) {
        award(player, amount, true);
    }

    public static void award(ServerPlayer player, int amount, boolean countsForQuest) {
        if (player == null || amount <= 0) return;
        if (countsForQuest) {
            CurrencyManager.addGold(player, amount);
        } else {
            CurrencyManager.addGoldNoQuest(player, amount);
        }
        sendHudGain(player, amount);
        RunManager.syncPlayer(player);
    }

    public static void sendHudGain(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return;
        TacRogueNetworking.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncDataMessage("gold_gain:" + amount));
    }
}
