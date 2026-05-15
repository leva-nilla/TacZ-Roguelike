package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenDebugMenuMessage {
    public OpenDebugMenuMessage() {}

    public static void encode(OpenDebugMenuMessage msg, FriendlyByteBuf buf) {}

    public static OpenDebugMenuMessage decode(FriendlyByteBuf buf) {
        return new OpenDebugMenuMessage();
    }

    public static void handle(OpenDebugMenuMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.levanilla.rogue.client.DebugMenuScreen.open()));
        ctx.get().setPacketHandled(true);
    }
}
