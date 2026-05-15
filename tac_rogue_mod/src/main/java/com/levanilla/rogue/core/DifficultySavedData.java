package com.levanilla.rogue.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 難易度の永続化。ワールド単位で SavedData に保存する。
 * サーバー再起動時にロビー次元から自動的にロードされ、DifficultyManager に復元する。
 */
public class DifficultySavedData extends SavedData {

    private DifficultyManager.Difficulty difficulty = DifficultyManager.Difficulty.NORMAL;

    public DifficultySavedData() {}

    public DifficultyManager.Difficulty getDifficulty() { return difficulty; }

    public void setDifficulty(DifficultyManager.Difficulty d) {
        this.difficulty = d;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Difficulty", difficulty.ordinal());
        return tag;
    }

    public static DifficultySavedData load(CompoundTag tag) {
        DifficultySavedData data = new DifficultySavedData();
        int ordinal = tag.getInt("Difficulty");
        DifficultyManager.Difficulty[] values = DifficultyManager.Difficulty.values();
        if (ordinal >= 0 && ordinal < values.length) {
            data.difficulty = values[ordinal];
        }
        return data;
    }

    /**
     * ワールド単位の SavedData を取得する。
     * ロビー次元をマスターとし、存在しない場合は現在の次元を使用する。
     */
    public static DifficultySavedData get(ServerLevel level) {
        ServerLevel targetLevel = level.getServer().getLevel(CommonEventHandler.LOBBY_DIM);
        if (targetLevel == null) {
            targetLevel = level;
        }
        return targetLevel.getDataStorage().computeIfAbsent(
            DifficultySavedData::load, DifficultySavedData::new, "tac_rogue_difficulty");
    }
}
