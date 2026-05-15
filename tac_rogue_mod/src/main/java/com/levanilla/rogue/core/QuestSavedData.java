package com.levanilla.rogue.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クエスト進捗のワールド単位永続化。
 * QuestManager の playerProgress をディスクに保存・復元する。
 */
public class QuestSavedData extends SavedData {

    private final Map<UUID, QuestManager.QuestProgress> playerProgress = new ConcurrentHashMap<>();

    public QuestSavedData() {}

    public Map<UUID, QuestManager.QuestProgress> getPlayerProgress() {
        return playerProgress;
    }

    public QuestManager.QuestProgress getProgress(UUID uuid) {
        return playerProgress.computeIfAbsent(uuid, k -> new QuestManager.QuestProgress());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Map.Entry<UUID, QuestManager.QuestProgress> entry : playerProgress.entrySet()) {
            tag.put(entry.getKey().toString(), entry.getValue().saveToNbt());
        }
        return tag;
    }

    public static QuestSavedData load(CompoundTag tag) {
        QuestSavedData data = new QuestSavedData();
        for (String key : tag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                QuestManager.QuestProgress progress = new QuestManager.QuestProgress();
                progress.loadFromNbt(tag.getCompound(key));
                data.playerProgress.put(uuid, progress);
            } catch (Exception ignored) {}
        }
        return data;
    }

    /**
     * ワールド単位の SavedData を取得する。
     * ロビー次元をマスターとし、存在しない場合は現在の次元を使用する。
     */
    public static QuestSavedData get(ServerLevel level) {
        ServerLevel targetLevel = level.getServer().getLevel(CommonEventHandler.LOBBY_DIM);
        if (targetLevel == null) {
            targetLevel = level;
        }
        return targetLevel.getDataStorage().computeIfAbsent(
            QuestSavedData::load, QuestSavedData::new, "tac_rogue_quests");
    }
}
