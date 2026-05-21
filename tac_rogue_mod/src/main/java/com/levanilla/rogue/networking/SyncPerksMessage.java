package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * S→C: プレイヤーのパークタグ一覧の同期。
 */
public class SyncPerksMessage {
    private final String perkTags; // カンマ区切り

    public SyncPerksMessage(String perkTags) {
        this.perkTags = perkTags;
    }

    public static void encode(SyncPerksMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.perkTags);
    }

    public static SyncPerksMessage decode(FriendlyByteBuf buf) {
        return new SyncPerksMessage(buf.readUtf());
    }

    public static void handle(SyncPerksMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                handleClient(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(SyncPerksMessage msg) {
        com.levanilla.rogue.core.ClientSyncHandler.applyPerks(msg.perkTags);
    }
}
