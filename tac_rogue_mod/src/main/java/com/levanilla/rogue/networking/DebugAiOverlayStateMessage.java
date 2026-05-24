package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class DebugAiOverlayStateMessage {
    private static final int MAX_STRING = 160;
    private static final int MAX_ENTRIES = 12;

    private final List<Entry> entries;

    public DebugAiOverlayStateMessage(List<Entry> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static void encode(DebugAiOverlayStateMessage msg, FriendlyByteBuf buf) {
        int count = Math.min(MAX_ENTRIES, msg.entries.size());
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = msg.entries.get(i);
            buf.writeInt(entry.entityId);
            buf.writeUtf(entry.entityType, MAX_STRING);
            buf.writeUtf(entry.displayName, MAX_STRING);
            buf.writeDouble(entry.distance);
            buf.writeUtf(entry.alertLevel, MAX_STRING);
            buf.writeUtf(entry.reason, MAX_STRING);
            buf.writeUtf(entry.targetInfo, MAX_STRING);
            buf.writeBoolean(entry.hasLosToPlayer);
            buf.writeBoolean(entry.hasActiveTarget);
            buf.writeInt(entry.alertTicksLeft);
            buf.writeInt(entry.lastSeenAgeTicks);
            buf.writeUtf(entry.memoryPos, MAX_STRING);
            buf.writeInt(entry.decoyTicksLeft);
        }
    }

    public static DebugAiOverlayStateMessage decode(FriendlyByteBuf buf) {
        int count = Math.max(0, Math.min(MAX_ENTRIES, buf.readInt()));
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(
                buf.readInt(),
                buf.readUtf(MAX_STRING),
                buf.readUtf(MAX_STRING),
                buf.readDouble(),
                buf.readUtf(MAX_STRING),
                buf.readUtf(MAX_STRING),
                buf.readUtf(MAX_STRING),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(MAX_STRING),
                buf.readInt()
            ));
        }
        return new DebugAiOverlayStateMessage(entries);
    }

    public static void handle(DebugAiOverlayStateMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.levanilla.rogue.client.DebugAiOverlayManager.update(msg.entries)));
        ctx.get().setPacketHandled(true);
    }

    public record Entry(
        int entityId,
        String entityType,
        String displayName,
        double distance,
        String alertLevel,
        String reason,
        String targetInfo,
        boolean hasLosToPlayer,
        boolean hasActiveTarget,
        int alertTicksLeft,
        int lastSeenAgeTicks,
        String memoryPos,
        int decoyTicksLeft
    ) {}
}
