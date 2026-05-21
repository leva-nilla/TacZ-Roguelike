package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * S→C: ゴールド残高の同期。型安全な int 値。
 */
public class SyncGoldMessage {
    private final int gold;

    public SyncGoldMessage(int gold) {
        this.gold = gold;
    }

    public static void encode(SyncGoldMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.gold);
    }

    public static SyncGoldMessage decode(FriendlyByteBuf buf) {
        return new SyncGoldMessage(buf.readInt());
    }

    public static void handle(SyncGoldMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                com.levanilla.rogue.core.ClientSyncHandler.applyGold(msg.gold);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
