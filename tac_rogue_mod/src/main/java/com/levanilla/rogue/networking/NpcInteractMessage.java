package com.levanilla.rogue.networking;

import com.levanilla.rogue.world.NpcManager;
import com.levanilla.rogue.world.TacRogueNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class NpcInteractMessage {
    private final int entityId;

    public NpcInteractMessage(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(NpcInteractMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
    }

    public static NpcInteractMessage decode(FriendlyByteBuf buf) {
        return new NpcInteractMessage(buf.readVarInt());
    }

    public static void handle(NpcInteractMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity entity = player.level().getEntity(msg.entityId);
            if (!(entity instanceof TacRogueNpcEntity npc)) return;
            if (npc.level().dimension() != player.level().dimension()) return;
            if (npc.distanceToSqr(player) > 36.0D) return;
            NpcManager.handleCustomNpcInteraction(player, npc);
        });
        ctx.get().setPacketHandled(true);
    }
}
