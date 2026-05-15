package com.levanilla.rogue.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * S→C: 3D ダメージインジケーター表示。型安全なフィールド群。
 */
public class DamageIndicatorMessage {
    private final float damage;
    private final double x, y, z;
    private final boolean isCritical;
    private final boolean isHeadShot;
    private final boolean isShotgun;

    public DamageIndicatorMessage(float damage, double x, double y, double z, boolean isCritical, boolean isHeadShot, boolean isShotgun) {
        this.damage = damage;
        this.x = x; this.y = y; this.z = z;
        this.isCritical = isCritical;
        this.isHeadShot = isHeadShot;
        this.isShotgun = isShotgun;
    }

    public static void encode(DamageIndicatorMessage msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.damage);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeBoolean(msg.isCritical);
        buf.writeBoolean(msg.isHeadShot);
        buf.writeBoolean(msg.isShotgun);
    }

    public static DamageIndicatorMessage decode(FriendlyByteBuf buf) {
        return new DamageIndicatorMessage(
            buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(DamageIndicatorMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                handleClient(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(DamageIndicatorMessage msg) {
        if (msg.isShotgun) {
            com.levanilla.rogue.client.ClientEventHandler.addShotgunDamageIndicator(msg.x, msg.y, msg.z, msg.damage, msg.isCritical, msg.isHeadShot);
        } else {
            com.levanilla.rogue.client.ClientEventHandler.addDamageIndicator(msg.x, msg.y, msg.z, msg.damage, msg.isCritical, msg.isHeadShot);
        }
    }
}
