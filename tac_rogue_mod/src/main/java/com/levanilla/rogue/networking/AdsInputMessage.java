package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.StaminaManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AdsInputMessage {
    private final boolean aiming;

    public AdsInputMessage(boolean aiming) {
        this.aiming = aiming;
    }

    public static void encode(AdsInputMessage msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.aiming);
    }

    public static AdsInputMessage decode(FriendlyByteBuf buf) {
        return new AdsInputMessage(buf.readBoolean());
    }

    public static void handle(AdsInputMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StaminaManager.setClientAdsInput(player, msg.aiming);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
