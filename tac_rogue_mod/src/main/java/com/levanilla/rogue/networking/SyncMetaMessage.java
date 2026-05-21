package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S->C: player upgrade metadata used by inventory/shop HUDs.
 */
public class SyncMetaMessage {
    private final int invLevel;
    private final int meleeLevel;
    private final int randomPerkBuys;
    private final int flashlightLevel;

    public SyncMetaMessage(int invLevel, int meleeLevel, int randomPerkBuys, int flashlightLevel) {
        this.invLevel = invLevel;
        this.meleeLevel = meleeLevel;
        this.randomPerkBuys = randomPerkBuys;
        this.flashlightLevel = flashlightLevel;
    }

    public static void encode(SyncMetaMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.invLevel);
        buf.writeInt(msg.meleeLevel);
        buf.writeInt(msg.randomPerkBuys);
        buf.writeInt(msg.flashlightLevel);
    }

    public static SyncMetaMessage decode(FriendlyByteBuf buf) {
        return new SyncMetaMessage(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(SyncMetaMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                com.levanilla.rogue.core.ClientSyncHandler.applyMeta(
                    msg.invLevel, msg.meleeLevel, msg.randomPerkBuys, msg.flashlightLevel);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
