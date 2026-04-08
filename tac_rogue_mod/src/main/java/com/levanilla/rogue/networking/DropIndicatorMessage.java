package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * S→C: アイテムドロップ表記の表示要求。
 */
public class DropIndicatorMessage {
    private final String name;
    private final double x, y, z;

    public DropIndicatorMessage(String name, double x, double y, double z) {
        this.name = name;
        this.x = x; this.y = y; this.z = z;
    }

    public static void encode(DropIndicatorMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
    }

    public static DropIndicatorMessage decode(FriendlyByteBuf buf) {
        return new DropIndicatorMessage(buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(DropIndicatorMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                com.levanilla.rogue.client.ClientEventHandler.addDropIndicator(msg.x, msg.y, msg.z, msg.name);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
