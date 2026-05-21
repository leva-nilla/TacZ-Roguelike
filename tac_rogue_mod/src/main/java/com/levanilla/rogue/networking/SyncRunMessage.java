package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.RunManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class SyncRunMessage {
    private final int floor;
    private final String theme;
    private final boolean active;
    private final boolean cleared;
    private final int maxFloor;
    private final int ammoCapLevel;
    private final boolean extended;

    public SyncRunMessage(int floor, String theme, boolean active) {
        this(floor, theme, active, false, 0, 0, false);
    }

    public SyncRunMessage(int floor, String theme, boolean active, boolean cleared, int maxFloor, int ammoCapLevel) {
        this(floor, theme, active, cleared, maxFloor, ammoCapLevel, true);
    }

    private SyncRunMessage(int floor, String theme, boolean active, boolean cleared, int maxFloor, int ammoCapLevel, boolean extended) {
        this.floor = floor;
        this.theme = theme;
        this.active = active;
        this.cleared = cleared;
        this.maxFloor = maxFloor;
        this.ammoCapLevel = ammoCapLevel;
        this.extended = extended;
    }

    public static void encode(SyncRunMessage msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.floor);
        buffer.writeUtf(msg.theme);
        buffer.writeBoolean(msg.active);
        if (msg.extended) {
            buffer.writeBoolean(msg.cleared);
            buffer.writeInt(msg.maxFloor);
            buffer.writeInt(msg.ammoCapLevel);
        }
    }

    public static SyncRunMessage decode(FriendlyByteBuf buffer) {
        int floor = buffer.readInt();
        String theme = buffer.readUtf();
        boolean active = buffer.readBoolean();
        if (buffer.readableBytes() <= 0) {
            return new SyncRunMessage(floor, theme, active);
        }
        boolean cleared = buffer.readBoolean();
        int maxFloor = buffer.readableBytes() >= Integer.BYTES ? buffer.readInt() : 0;
        int ammoCapLevel = buffer.readableBytes() >= Integer.BYTES ? buffer.readInt() : 0;
        return new SyncRunMessage(floor, theme, active, cleared, maxFloor, ammoCapLevel);
    }

    public static void handle(SyncRunMessage msg, Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                if (msg.extended) {
                    com.levanilla.rogue.core.ClientSyncHandler.applyRunData(
                        msg.floor, msg.theme, msg.active, msg.cleared, msg.maxFloor, msg.ammoCapLevel);
                } else {
                    RunManager.setClientData(msg.floor, msg.theme, msg.active, RunManager.getMaxReachedFloor());
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
