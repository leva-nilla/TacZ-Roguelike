package com.levanilla.rogue.networking;

import com.levanilla.rogue.client.QuartermasterServicesScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncServiceStashMessage {
    public final List<Entry> hotbarEntries;
    public final List<Entry> stashEntries;

    public SyncServiceStashMessage(List<Entry> hotbarEntries, List<Entry> stashEntries) {
        this.hotbarEntries = List.copyOf(hotbarEntries);
        this.stashEntries = List.copyOf(stashEntries);
    }

    public static void encode(SyncServiceStashMessage msg, FriendlyByteBuf buf) {
        writeEntries(buf, msg.hotbarEntries);
        writeEntries(buf, msg.stashEntries);
    }

    public static SyncServiceStashMessage decode(FriendlyByteBuf buf) {
        return new SyncServiceStashMessage(readEntries(buf), readEntries(buf));
    }

    public static void handle(SyncServiceStashMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                QuartermasterServicesScreen.syncLoadoutPanel(msg.hotbarEntries, msg.stashEntries);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void writeEntries(FriendlyByteBuf buf, List<Entry> entries) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeVarInt(entry.slot());
            buf.writeItem(entry.stack());
            buf.writeUtf(entry.category());
        }
    }

    private static List<Entry> readEntries(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readVarInt(), buf.readItem(), buf.readUtf()));
        }
        return entries;
    }

    public record Entry(int slot, ItemStack stack, String category) {}
}
