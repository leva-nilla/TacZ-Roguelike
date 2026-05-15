package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenStarterGearMessage {
    public OpenStarterGearMessage() {}

    public static void encode(OpenStarterGearMessage msg, FriendlyByteBuf buf) {}

    public static OpenStarterGearMessage decode(FriendlyByteBuf buf) {
        return new OpenStarterGearMessage();
    }

    public static void handle(OpenStarterGearMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.screen == null) {
                    mc.setScreen(new com.levanilla.rogue.client.StarterGearScreen());
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
