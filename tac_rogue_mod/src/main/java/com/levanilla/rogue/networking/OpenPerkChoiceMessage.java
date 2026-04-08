package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * S→C: パーク選択画面を開く。3種類のパーク画面を1メッセージで統合。
 */
public class OpenPerkChoiceMessage {

    public enum PerkScreenType {
        INITIAL, NORMAL, BOSS
    }

    private final PerkScreenType type;

    public OpenPerkChoiceMessage(PerkScreenType type) {
        this.type = type;
    }

    public static void encode(OpenPerkChoiceMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.type);
    }

    public static OpenPerkChoiceMessage decode(FriendlyByteBuf buf) {
        return new OpenPerkChoiceMessage(buf.readEnum(PerkScreenType.class));
    }

    public static void handle(OpenPerkChoiceMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                switch (msg.type) {
                    case INITIAL -> com.levanilla.rogue.client.PerkManager.openInitialPerkScreen();
                    case NORMAL  -> com.levanilla.rogue.client.PerkManager.openPerkScreen();
                    case BOSS    -> com.levanilla.rogue.client.PerkManager.openBossPerkScreen();
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
