package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class OpenPerkScreenMessage {
    public OpenPerkScreenMessage() {}

    public static void encode(OpenPerkScreenMessage msg, FriendlyByteBuf buf) {}

    public static OpenPerkScreenMessage decode(FriendlyByteBuf buf) {
        return new OpenPerkScreenMessage();
    }

    public static void handle(OpenPerkScreenMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                com.levanilla.rogue.client.PerkManager.openPerkScreen();
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
