package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.ClientRunState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StealthTakedownHintMessage {
    private final int targetEntityId;
    private final int durationMs;

    public StealthTakedownHintMessage(int targetEntityId, int durationMs) {
        this.targetEntityId = targetEntityId;
        this.durationMs = durationMs;
    }

    public static void encode(StealthTakedownHintMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetEntityId);
        buf.writeInt(msg.durationMs);
    }

    public static StealthTakedownHintMessage decode(FriendlyByteBuf buf) {
        return new StealthTakedownHintMessage(buf.readInt(), buf.readInt());
    }

    public static void handle(StealthTakedownHintMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientRunState.setStealthTakedownTarget(msg.targetEntityId, msg.durationMs));
        ctx.get().setPacketHandled(true);
    }
}
