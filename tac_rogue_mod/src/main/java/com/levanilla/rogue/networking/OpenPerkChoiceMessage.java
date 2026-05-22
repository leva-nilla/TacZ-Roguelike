package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PerkGenerator;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.PlayerRunData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * S→C: パーク選択画面を開く。サーバーで生成した3つのパーク候補をクライアントに送信する。
 * サーバー側でセッションを登録し、APPLY_PERK 受信時に照合することでチートを防ぐ。
 */
public class OpenPerkChoiceMessage {

    public enum PerkScreenType {
        INITIAL, NORMAL, BOSS
    }

    private final PerkScreenType type;
    private final List<String> perkTags; // サーバーで生成したパーク候補のタグ

    public OpenPerkChoiceMessage(PerkScreenType type, List<String> perkTags) {
        this.type = type;
        this.perkTags = perkTags;
    }

    public static void encode(OpenPerkChoiceMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.type);
        buf.writeInt(msg.perkTags.size());
        for (String tag : msg.perkTags) {
            buf.writeUtf(tag);
        }
    }

    public static OpenPerkChoiceMessage decode(FriendlyByteBuf buf) {
        PerkScreenType type = buf.readEnum(PerkScreenType.class);
        int size = buf.readInt();
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            tags.add(buf.readUtf());
        }
        return new OpenPerkChoiceMessage(type, tags);
    }

    public static void handle(OpenPerkChoiceMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                // サーバーから受け取ったパーク候補を復元
                List<PerkDefinition> choices = msg.perkTags.stream()
                    .map(PerkDefinition::fromTag)
                    .toList();
                com.levanilla.rogue.client.PerkManager.openPerkScreenWithChoices(choices,
                    switch (msg.type) {
                        case INITIAL -> com.levanilla.rogue.client.PerkManager.OpenPerkChoiceMode.INITIAL;
                        case BOSS -> com.levanilla.rogue.client.PerkManager.OpenPerkChoiceMode.BOSS;
                        case NORMAL -> com.levanilla.rogue.client.PerkManager.OpenPerkChoiceMode.NORMAL;
                    });
            });
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * サーバー側ヘルパー: パーク候補を生成し、セッション登録してクライアントに送信する。
     * 全てのパーク画面表示はこのメソッドを経由すべき。
     */
    public static void sendPerkChoices(ServerPlayer player, PerkScreenType type) {
        // プレイヤーの既存パークを収集
        Set<String> existing = new HashSet<>();
        int overclockedCount = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:")) {
                existing.add(PerkDefinition.fromTag(tag).toTag());
                if (tag.contains(":OVERCLOCKED:")) overclockedCount++;
            }
        }

        // サーバー側でパーク候補を生成
        List<PerkDefinition> choices;
        int selectionBonus = com.levanilla.rogue.core.service.DeepProgressService.getSelectionBonus(player);
        int choiceCount = 3 + selectionBonus;
        if (type == PerkScreenType.INITIAL) {
            choices = PerkGenerator.generateInitialChoices(choiceCount);
        } else {
            PlayerRunData data = RunManager.getData(player);
            int floor = data.getCurrentFloor();
            boolean isBoss = (type == PerkScreenType.BOSS);
            choices = PerkGenerator.generateChoices(floor, isBoss, existing, overclockedCount, choiceCount);
        }

        // セッションに候補を登録（APPLY_PERK 受信時の照合用）
        RogueActionMessage.registerPerkChoices(player.getUUID(), choices);

        // パーク候補をクライアントに送信
        List<String> tags = choices.stream().map(PerkDefinition::toTag).toList();
        TacRogueNetworking.CHANNEL.sendTo(
            new OpenPerkChoiceMessage(type, tags),
            player.connection.connection,
            NetworkDirection.PLAY_TO_CLIENT
        );
    }
}
