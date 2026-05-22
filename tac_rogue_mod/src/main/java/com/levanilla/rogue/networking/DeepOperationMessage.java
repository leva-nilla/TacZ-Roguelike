package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.service.DeepProgressService;
import com.levanilla.rogue.world.NpcManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DeepOperationMessage {
    private final String action;
    private final int gunSlot;
    private final int tokenSlot;
    private final String upgradeKey;

    public DeepOperationMessage(String action, int gunSlot, int tokenSlot, String upgradeKey) {
        this.action = action == null ? "" : action;
        this.gunSlot = gunSlot;
        this.tokenSlot = tokenSlot;
        this.upgradeKey = upgradeKey == null ? "" : upgradeKey;
    }

    public static void encode(DeepOperationMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action);
        buf.writeInt(msg.gunSlot);
        buf.writeInt(msg.tokenSlot);
        buf.writeUtf(msg.upgradeKey);
    }

    public static DeepOperationMessage decode(FriendlyByteBuf buf) {
        return new DeepOperationMessage(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readUtf());
    }

    public static void handle(DeepOperationMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!DeepProgressService.isUnlocked(RunManager.getData(player))) {
                player.sendSystemMessage(Component.translatable("message.tac_rogue.deep_locked"), true);
                return;
            }
            if (!NpcManager.canUseDeepOperations(player)) {
                player.sendSystemMessage(Component.translatable("message.tac_rogue.deep_terminal_required"), true);
                return;
            }
            DeepProgressService.handleDeepOperation(player, msg.action, msg.gunSlot, msg.tokenSlot, msg.upgradeKey);
        });
        ctx.get().setPacketHandled(true);
    }
}
