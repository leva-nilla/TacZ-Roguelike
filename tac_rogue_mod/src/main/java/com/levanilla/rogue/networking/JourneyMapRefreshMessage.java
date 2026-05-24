package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class JourneyMapRefreshMessage {
    private final int centerX;
    private final int centerZ;
    private final int radius;

    public JourneyMapRefreshMessage(int centerX, int centerZ, int radius) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    public static void encode(JourneyMapRefreshMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.centerX);
        buf.writeInt(msg.centerZ);
        buf.writeInt(msg.radius);
    }

    public static JourneyMapRefreshMessage decode(FriendlyByteBuf buf) {
        return new JourneyMapRefreshMessage(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(JourneyMapRefreshMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.levanilla.rogue.client.compat.JourneyMapRefreshCompat.requestRefresh(
                    msg.centerX, msg.centerZ, msg.radius)));
        ctx.get().setPacketHandled(true);
    }
}
