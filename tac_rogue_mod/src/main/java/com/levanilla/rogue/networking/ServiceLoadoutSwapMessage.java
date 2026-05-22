package com.levanilla.rogue.networking;

import com.levanilla.rogue.world.NpcManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServiceLoadoutSwapMessage {
    private final int inventorySlot;
    private final int stashSlot;

    public ServiceLoadoutSwapMessage(int inventorySlot, int stashSlot) {
        this.inventorySlot = inventorySlot;
        this.stashSlot = stashSlot;
    }

    public static void encode(ServiceLoadoutSwapMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.inventorySlot);
        buf.writeVarInt(msg.stashSlot);
    }

    public static ServiceLoadoutSwapMessage decode(FriendlyByteBuf buf) {
        return new ServiceLoadoutSwapMessage(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ServiceLoadoutSwapMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                NpcManager.swapServiceLoadoutSlot(player, msg.inventorySlot, msg.stashSlot);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
