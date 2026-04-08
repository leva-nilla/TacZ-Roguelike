package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class OpenFloorClearScreenMessage {
    public OpenFloorClearScreenMessage() {}

    public static void encode(OpenFloorClearScreenMessage msg, FriendlyByteBuf buf) {}

    public static OpenFloorClearScreenMessage decode(FriendlyByteBuf buf) {
        return new OpenFloorClearScreenMessage();
    }

    public static void handle(OpenFloorClearScreenMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft.getInstance().tell(() -> {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new com.levanilla.rogue.client.FloorClearScreen());
                });
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
