package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class SyncStashMessage {
    private final int lines;

    public SyncStashMessage(int lines) {
        this.lines = lines;
    }

    public static void encode(SyncStashMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.lines);
    }

    public static SyncStashMessage decode(FriendlyByteBuf buf) {
        return new SyncStashMessage(buf.readInt());
    }

    public static void handle(SyncStashMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                com.levanilla.rogue.networking.SyncStashMessage.handleClient(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(SyncStashMessage msg) {
        // StashScreen はサーバー側が ChestMenu を開き、ClientEventHandler の
        // onScreenOpening で ContainerScreen → StashScreen に差し替えるため、
        // ここでは unlocked lines のクライアントキャッシュのみ更新。
        com.levanilla.rogue.core.RunManager.setClientStashLines(msg.lines);
    }
}
