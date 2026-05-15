package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenNpcMenuMessage {
    public final String role;
    public final boolean dungeon;
    public final boolean floorCleared;

    public OpenNpcMenuMessage(String role, boolean dungeon, boolean floorCleared) {
        this.role = role;
        this.dungeon = dungeon;
        this.floorCleared = floorCleared;
    }

    public static void encode(OpenNpcMenuMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.role);
        buf.writeBoolean(msg.dungeon);
        buf.writeBoolean(msg.floorCleared);
    }

    public static OpenNpcMenuMessage decode(FriendlyByteBuf buf) {
        return new OpenNpcMenuMessage(buf.readUtf(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(OpenNpcMenuMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.levanilla.rogue.client.NpcMenuScreen(msg.role, msg.dungeon, msg.floorCleared)));
        });
        ctx.get().setPacketHandled(true);
    }
}
