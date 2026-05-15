package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * クライアント → サーバー: パーク選択通知パケット。
 * プレイヤーが選択したパークのシリアライズされたタグを送信する。
 */
public class PerkActionMessage {

    private final String perkTag;

    public PerkActionMessage(String perkTag) {
        this.perkTag = perkTag;
    }

    public static void encode(PerkActionMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.perkTag);
    }

    public static PerkActionMessage decode(FriendlyByteBuf buf) {
        return new PerkActionMessage(buf.readUtf());
    }

    public static void handle(PerkActionMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            com.levanilla.rogue.core.service.PerkApplyService.applySelectedPerk(player, msg.perkTag);
        });
        ctx.get().setPacketHandled(true);
    }
}
