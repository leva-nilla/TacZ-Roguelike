package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.StashSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenShopMessage {
    private final List<StashEntry> stashEntries;

    public OpenShopMessage() {
        this(List.of());
    }

    public OpenShopMessage(List<StashEntry> stashEntries) {
        this.stashEntries = List.copyOf(stashEntries);
    }

    public List<StashEntry> stashEntries() {
        return stashEntries;
    }

    public static void encode(OpenShopMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.stashEntries.size());
        for (StashEntry entry : msg.stashEntries) {
            buf.writeVarInt(entry.slot());
            buf.writeItem(entry.stack());
        }
    }

    public static OpenShopMessage decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<StashEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new StashEntry(buf.readVarInt(), buf.readItem()));
        }
        return new OpenShopMessage(entries);
    }

    public static void handle(OpenShopMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.screen instanceof com.levanilla.rogue.client.ShopScreen shopScreen) {
                    shopScreen.updateStashEntries(msg.stashEntries());
                } else {
                    mc.setScreen(new com.levanilla.rogue.client.ShopScreen(msg.stashEntries()));
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    public static List<StashEntry> collectStashEntries(ServerPlayer player) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
        int maxSlots = Math.min(stash.unlockedLines * 9, stash.getContainerSize());
        List<StashEntry> entries = new ArrayList<>();
        for (int slot = 0; slot < maxSlots; slot++) {
            ItemStack stack = stash.getItem(slot);
            if (!stack.isEmpty()) {
                entries.add(new StashEntry(slot, stack.copy()));
            }
        }
        return entries;
    }

    public record StashEntry(int slot, ItemStack stack) {}
}
