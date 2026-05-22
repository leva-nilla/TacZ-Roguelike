package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.world.NpcManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class QuestSelectionMessage {
    private final List<String> questIds;

    public QuestSelectionMessage(List<String> questIds) {
        this.questIds = List.copyOf(questIds);
    }

    public static void encode(QuestSelectionMessage msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.questIds.size());
        for (String id : msg.questIds) {
            buf.writeUtf(id);
        }
    }

    public static QuestSelectionMessage decode(FriendlyByteBuf buf) {
        int count = Math.max(0, buf.readInt());
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            if (ids.size() < QuestManager.REQUIRED_SIDE_QUESTS) {
                ids.add(id);
            }
        }
        return new QuestSelectionMessage(ids);
    }

    public static void handle(QuestSelectionMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            boolean accepted = QuestManager.selectChapterQuests(player, msg.questIds);
            NpcManager.sendQuestData(player);
            if (!accepted) {
                player.sendSystemMessage(Component.translatable("message.tac_rogue.quest_selection_rejected"), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
