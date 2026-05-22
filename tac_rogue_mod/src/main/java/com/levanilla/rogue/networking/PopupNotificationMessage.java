package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PopupNotificationMessage {
    public enum PopupType {
        QUEST(0xFF55FF88),
        REWARD(0xFFFFAAFF),
        NPC(0xFF55CCFF),
        SYSTEM(0xFFFFDD66),
        WARNING(0xFFFF6666);

        public final int color;

        PopupType(int color) {
            this.color = color;
        }
    }

    private final PopupType type;
    private final Component title;
    private final Component body;
    private final int durationTicks;

    public PopupNotificationMessage(PopupType type, Component title, Component body, int durationTicks) {
        this.type = type;
        this.title = title;
        this.body = body;
        this.durationTicks = Math.max(60, Math.min(200, durationTicks));
    }

    public static void encode(PopupNotificationMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.type);
        buf.writeComponent(msg.title);
        buf.writeComponent(msg.body);
        buf.writeVarInt(msg.durationTicks);
    }

    public static PopupNotificationMessage decode(FriendlyByteBuf buf) {
        return new PopupNotificationMessage(
            buf.readEnum(PopupType.class),
            buf.readComponent(),
            buf.readComponent(),
            buf.readVarInt()
        );
    }

    public static void handle(PopupNotificationMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                if (com.levanilla.rogue.client.DeepOperationsScreen.offerDeepPopup(
                    msg.type.name(), msg.title, msg.body, msg.type.color, msg.durationTicks)) {
                    return;
                }
                if (com.levanilla.rogue.client.QuartermasterServicesScreen.offerServicePopup(
                    msg.type.name(), msg.title, msg.body, msg.type.color, msg.durationTicks)) {
                    return;
                }
                if (com.levanilla.rogue.client.MedicalSupportScreen.offerServicePopup(
                    msg.type.name(), msg.title, msg.body, msg.type.color, msg.durationTicks)) {
                    return;
                }
                com.levanilla.rogue.client.ClientEventHandler.addPopupNotification(
                    msg.type.name(), msg.title, msg.body, msg.type.color, msg.durationTicks);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void send(ServerPlayer player, PopupType type, Component title, Component body) {
        send(player, type, title, body, 120);
    }

    public static void send(ServerPlayer player, PopupType type, Component title, Component body, int durationTicks) {
        TacRogueNetworking.CHANNEL.sendTo(
            new PopupNotificationMessage(type, title, body, durationTicks),
            player.connection.connection,
            NetworkDirection.PLAY_TO_CLIENT
        );
    }
}
