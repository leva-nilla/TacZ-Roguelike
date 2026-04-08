package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.RunManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class SyncRunMessage {
    private final int floor;
    private final String theme;
    private final boolean active;

    public SyncRunMessage(int floor, String theme, boolean active) {
        this.floor = floor;
        this.theme = theme;
        this.active = active;
    }

    public static void encode(SyncRunMessage msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.floor);
        buffer.writeUtf(msg.theme);
        buffer.writeBoolean(msg.active);
    }

    public static SyncRunMessage decode(FriendlyByteBuf buffer) {
        return new SyncRunMessage(buffer.readInt(), buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(SyncRunMessage msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Client-side logic
            RunManager.setClientData(msg.floor, msg.theme, msg.active);
        });
        context.setPacketHandled(true);
    }
}
