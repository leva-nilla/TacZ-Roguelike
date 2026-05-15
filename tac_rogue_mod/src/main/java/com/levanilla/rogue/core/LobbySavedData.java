package com.levanilla.rogue.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class LobbySavedData extends SavedData {
    private int version = 0;

    public LobbySavedData() {}

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", version);
        return tag;
    }

    public static LobbySavedData load(CompoundTag tag) {
        LobbySavedData data = new LobbySavedData();
        data.version = tag.getInt("Version");
        return data;
    }

    public static LobbySavedData get(ServerLevel level) {
        ServerLevel targetLevel = level.getServer().getLevel(CommonEventHandler.LOBBY_DIM);
        if (targetLevel == null) {
            targetLevel = level;
        }
        return targetLevel.getDataStorage().computeIfAbsent(
            LobbySavedData::load, LobbySavedData::new, "tac_rogue_lobby");
    }
}
