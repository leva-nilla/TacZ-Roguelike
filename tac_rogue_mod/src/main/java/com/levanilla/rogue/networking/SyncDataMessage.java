package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * サーバーからクライアントへ、現在の「ラン（Run）」の状態（階層、クリア状況など）を同期するためのパケット
 */
public class SyncDataMessage {
    private final String data; // 同期するデータ（文字列形式）

    public SyncDataMessage(String data) {
        this.data = data;
    }

    /**
     * パケットのエンコード（書き込み）
     */
    public static void encode(SyncDataMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.data);
    }

    /**
     * パケットのデコード（読み込み）
     */
    public static SyncDataMessage decode(FriendlyByteBuf buf) {
        return new SyncDataMessage(buf.readUtf());
    }

    /**
     * パケットの受信処理
     */
    public static void handle(SyncDataMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // クライアント側でデータを受け取り、RunManager に反映させる
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                com.levanilla.rogue.core.RunManager.onSync(msg.data);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
