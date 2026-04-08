package com.levanilla.rogue.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class StashSavedData extends SavedData {
    private final Map<UUID, PlayerStash> playerStashes = new HashMap<>();

    public static class PlayerStash extends SimpleContainer {
        public int unlockedLines = 2; // 初期は 2 行 (18 スロット)
        public final Map<Integer, ItemStack> items = new HashMap<>();
        private Runnable dirtyCallback = () -> {};

        public PlayerStash(UUID owner) {
            super(54); // Max chest size
        }

        public void setDirtyCallback(Runnable cb) { this.dirtyCallback = cb; }

        @Override public int getContainerSize() { return 54; } // 常に最大54(Chest)として扱う
        @Override public boolean isEmpty() { return items.isEmpty(); }
        @Override public net.minecraft.world.item.ItemStack getItem(int slot) { 
            if (slot >= unlockedLines * 9) return net.minecraft.world.item.ItemStack.EMPTY;
            return items.getOrDefault(slot, net.minecraft.world.item.ItemStack.EMPTY); 
        }
        @Override public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) {
            if (slot >= unlockedLines * 9) return net.minecraft.world.item.ItemStack.EMPTY;
            net.minecraft.world.item.ItemStack stack = getItem(slot);
            if (stack.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
            net.minecraft.world.item.ItemStack split = stack.split(amount);
            if (stack.isEmpty()) items.put(slot, net.minecraft.world.item.ItemStack.EMPTY);
            dirtyCallback.run();
            return split;
        }
        @Override public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) { 
            if (slot >= unlockedLines * 9) return net.minecraft.world.item.ItemStack.EMPTY;
            net.minecraft.world.item.ItemStack stack = items.remove(slot);
            dirtyCallback.run();
            return stack;
        }
        @Override public void setItem(int slot, net.minecraft.world.item.ItemStack stack) { 
            if (slot >= unlockedLines * 9) return;
            items.put(slot, stack); 
            dirtyCallback.run(); 
        }
        @Override public void setChanged() { dirtyCallback.run(); }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { items.clear(); dirtyCallback.run(); }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Lines", unlockedLines);
            ListTag list = new ListTag();
            for (Map.Entry<Integer, net.minecraft.world.item.ItemStack> entry : items.entrySet()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", entry.getKey());
                entry.getValue().save(itemTag);
                list.add(itemTag);
            }
            tag.put("Items", list);
            return tag;
        }

        public static PlayerStash load(UUID owner, CompoundTag tag) {
            PlayerStash stash = new PlayerStash(owner);
            stash.unlockedLines = tag.getInt("Lines");
            if (stash.unlockedLines < 1) stash.unlockedLines = 1;
            ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                stash.items.put(itemTag.getInt("Slot"), ItemStack.of(itemTag));
            }
            return stash;
        }
    }

    public PlayerStash getStash(UUID uuid) {
        PlayerStash stash = playerStashes.computeIfAbsent(uuid, k -> new PlayerStash(k));
        stash.setDirtyCallback(this::setDirty);
        return stash;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Map.Entry<UUID, PlayerStash> entry : playerStashes.entrySet()) {
            tag.put(entry.getKey().toString(), entry.getValue().save());
        }
        return tag;
    }

    public static StashSavedData load(CompoundTag tag) {
        StashSavedData data = new StashSavedData();
        for (String key : tag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                PlayerStash stash = PlayerStash.load(uuid, tag.getCompound(key));
                stash.setDirtyCallback(data::setDirty);
                data.playerStashes.put(uuid, stash);
            } catch (Exception e) {}
        }
        return data;
    }

    public static StashSavedData get(ServerLevel level) {
        // 全次元で共通のスタッシュデータを扱うため、LOBBY_DIM をマスターとする
        ServerLevel targetLevel = level.getServer().getLevel(CommonEventHandler.LOBBY_DIM);
        if (targetLevel == null) {
            targetLevel = level;
        }
        return targetLevel.getDataStorage().computeIfAbsent(StashSavedData::load, StashSavedData::new, "tac_rogue_stash");
    }
}
