package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PerkGenerator;
import com.levanilla.rogue.core.PlayerRunData;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.service.PerkStorageService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * S→C: フロアクリア画面を開く。
 * isFarming=false の場合、サーバーで生成した3つのパーク候補も含む。
 * サーバー側でセッション登録し、APPLY_PERK 受信時に照合する（SEC-1対策）。
 */
public class OpenFloorClearScreenMessage {
    public final boolean isFarming;
    public final List<String> perkTags; // サーバーで生成したパーク候補（farmingの場合は空）

    public OpenFloorClearScreenMessage(boolean isFarming, List<String> perkTags) {
        this.isFarming = isFarming;
        this.perkTags = perkTags != null ? perkTags : List.of();
    }

    public static void encode(OpenFloorClearScreenMessage msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isFarming);
        buf.writeInt(msg.perkTags.size());
        for (String tag : msg.perkTags) {
            buf.writeUtf(tag);
        }
    }

    public static OpenFloorClearScreenMessage decode(FriendlyByteBuf buf) {
        boolean isFarming = buf.readBoolean();
        int size = buf.readInt();
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            tags.add(buf.readUtf());
        }
        return new OpenFloorClearScreenMessage(isFarming, tags);
    }

    public static void handle(OpenFloorClearScreenMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft.getInstance().tell(() -> {
                    // サーバーから受け取ったパーク候補を復元
                    List<PerkDefinition> choices = msg.perkTags.stream()
                        .map(PerkDefinition::fromTag)
                        .toList();
                    net.minecraft.client.Minecraft.getInstance().setScreen(
                        new com.levanilla.rogue.client.FloorClearScreen(msg.isFarming, choices));
                });
            });
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * サーバー側ヘルパー: パーク候補を生成し、セッション登録してクライアントに送信する。
     */
    public static void sendFloorClear(ServerPlayer player, boolean isFarming) {
        List<String> perkTags = List.of();

        if (!isFarming) {
            // プレイヤーの既存パークを収集
            Set<String> existing = new HashSet<>();
            int overclockedCount = 0;
            for (String tag : PerkStorageService.getPerkTags(player)) {
                existing.add(PerkDefinition.fromTag(tag).toTag());
                if (tag.contains(":OVERCLOCKED:")) overclockedCount++;
            }

            PlayerRunData data = RunManager.getData(player);
            int floor = data.getCurrentFloor();
            List<PerkDefinition> choices = PerkGenerator.generateChoices(
                floor,
                com.levanilla.rogue.world.ThemeManager.isBossFloor(floor),
                existing,
                overclockedCount);

            // セッションに候補を登録（APPLY_PERK 受信時の照合用）
            RogueActionMessage.registerPerkChoices(player.getUUID(), choices);

            perkTags = choices.stream().map(PerkDefinition::toTag).toList();
        }

        TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new OpenFloorClearScreenMessage(isFarming, perkTags));
    }
}
