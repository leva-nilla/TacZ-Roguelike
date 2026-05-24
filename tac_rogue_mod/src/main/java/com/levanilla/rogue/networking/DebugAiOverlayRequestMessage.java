package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import com.levanilla.rogue.core.service.RogueMobAlertService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class DebugAiOverlayRequestMessage {
    private final double radius;
    private final int maxEntries;

    public DebugAiOverlayRequestMessage(double radius, int maxEntries) {
        this.radius = radius;
        this.maxEntries = maxEntries;
    }

    public static void encode(DebugAiOverlayRequestMessage msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.radius);
        buf.writeInt(msg.maxEntries);
    }

    public static DebugAiOverlayRequestMessage decode(FriendlyByteBuf buf) {
        return new DebugAiOverlayRequestMessage(buf.readDouble(), buf.readInt());
    }

    public static void handle(DebugAiOverlayRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            List<DebugAiOverlayStateMessage.Entry> entries = collect(player, msg.radius, msg.maxEntries);
            TacRogueNetworking.CHANNEL.sendTo(
                new DebugAiOverlayStateMessage(entries),
                player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT
            );
        });
        ctx.get().setPacketHandled(true);
    }

    private static List<DebugAiOverlayStateMessage.Entry> collect(ServerPlayer player, double rawRadius, int rawMaxEntries) {
        if (player.level().dimension() != CommonEventHandler.ROGUE_DIM) return List.of();
        double radius = Math.max(8.0D, Math.min(192.0D, rawRadius));
        int maxEntries = Math.max(1, Math.min(20, rawMaxEntries));
        String playerInstance = player.getPersistentData().getString(FloorInstanceManager.INSTANCE_ID_KEY);
        AABB area = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(Mob.class, area, mob ->
                mob.isAlive())
            .stream()
            .sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)))
            .limit(maxEntries)
            .map(mob -> toEntry(player, mob, playerInstance))
            .toList();
    }

    private static DebugAiOverlayStateMessage.Entry toEntry(ServerPlayer player, Mob mob, String playerInstance) {
        CompoundTag data = mob.getPersistentData();
        RogueMobAlertService.AlertLevel level = RogueMobAlertService.getAlertLevel(mob);
        long now = mob.level().getGameTime();
        LivingEntity target = mob.getTarget();
        boolean hasActiveTarget = target != null && target.isAlive() && !target.isSpectator();
        boolean hasLosToPlayer = mob.hasLineOfSight(player);
        long alertEnd = data.getLong(RogueMobAlertService.INVESTIGATE_END_TIME);
        long lastSeen = data.getLong(RogueMobAlertService.LAST_SEEN_TICK);
        long decoyEnd = data.getLong(RogueMobAlertService.DECOY_END_TIME);
        Vec3 memory = RogueMobAlertService.getInvestigatePos(mob);
        String targetInfo = "-";
        if (hasActiveTarget) {
            targetInfo = target.getType().toShortString() + "#" + target.getId()
                + " " + String.format(Locale.ROOT, "%.1fm", mob.distanceTo(target));
        } else {
            String remembered = data.getString(RogueMobAlertService.ALERT_TARGET_UUID);
            if (remembered != null && !remembered.isBlank()) {
                targetInfo = remembered.substring(0, Math.min(8, remembered.length()));
            }
        }
        boolean rogueTagged = mob.getTags().contains("tac_rogue_spawned") || mob.getTags().contains("rogue:boss");
        String mobInstance = data.getString(FloorInstanceManager.INSTANCE_ID_KEY);
        boolean sameInstance = sameInstance(playerInstance, mobInstance);
        String reason = data.getString(RogueMobAlertService.ALERT_REASON);
        if (!rogueTagged) {
            reason = appendDebugReason(reason, "UNTRACKED");
        }
        if (!sameInstance) {
            reason = appendDebugReason(reason, "INSTANCE " + shorten(mobInstance));
        }
        return new DebugAiOverlayStateMessage.Entry(
            mob.getId(),
            mob.getType().toShortString(),
            mob.getDisplayName().getString(),
            mob.distanceTo(player),
            level.name(),
            reason,
            targetInfo,
            hasLosToPlayer,
            hasActiveTarget,
            Math.max(0, (int)(alertEnd - now)),
            lastSeen > 0L ? Math.max(0, (int)(now - lastSeen)) : -1,
            pos(memory),
            Math.max(0, (int)(decoyEnd - now))
        );
    }

    private static String appendDebugReason(String base, String extra) {
        if (base == null || base.isBlank()) return extra;
        return base + "," + extra;
    }

    private static String shorten(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.length() <= 12 ? value : value.substring(0, 12);
    }

    private static String pos(Vec3 pos) {
        if (pos == null) return "-";
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", pos.x, pos.y, pos.z);
    }

    private static boolean sameInstance(String playerInstance, String mobInstance) {
        return playerInstance == null || playerInstance.isBlank()
            || mobInstance == null || mobInstance.isBlank()
            || playerInstance.equals(mobInstance);
    }
}
