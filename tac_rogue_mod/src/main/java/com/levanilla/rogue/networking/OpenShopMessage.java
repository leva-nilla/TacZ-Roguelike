package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class OpenShopMessage {
    public OpenShopMessage() {}

    public static void encode(OpenShopMessage msg, FriendlyByteBuf buf) {}

    public static OpenShopMessage decode(FriendlyByteBuf buf) {
        return new OpenShopMessage();
    }

    public static void handle(OpenShopMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft.getInstance().setScreen(new com.levanilla.rogue.client.ShopScreen());
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
