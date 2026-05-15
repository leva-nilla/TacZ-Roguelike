package com.levanilla.rogue.networking;

import com.levanilla.rogue.world.NpcManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class NpcMenuActionMessage {
    public final String role;
    public final String action;

    public NpcMenuActionMessage(String role, String action) {
        this.role = role;
        this.action = action;
    }

    public static void encode(NpcMenuActionMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.role);
        buf.writeUtf(msg.action);
    }

    public static NpcMenuActionMessage decode(FriendlyByteBuf buf) {
        return new NpcMenuActionMessage(buf.readUtf(), buf.readUtf());
    }

    public static void handle(NpcMenuActionMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                NpcManager.handleMenuAction(player, msg.role, msg.action);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
