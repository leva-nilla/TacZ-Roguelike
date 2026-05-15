package com.levanilla.rogue.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーの所持ゴールド（ゲーム内通貨）を SavedData で永続化するクラス。
 * サーバー再起動後もゴールドが維持される。
 */
public class CurrencyManager extends SavedData {

    private final Map<UUID, Integer> gold = new HashMap<>();

    public CurrencyManager() {}

    /** 指定プレイヤーの所持ゴールドを取得 */
    public int getGold(UUID uuid) {
        return gold.getOrDefault(uuid, 0);
    }

    /** ゴールドを加算 */
    public void addGold(UUID uuid, int amount) {
        gold.put(uuid, getGold(uuid) + amount);
        setDirty();
    }

    /** ゴールドを消費（残高チェック付き） */
    public boolean consumeGold(UUID uuid, int amount) {
        int current = getGold(uuid);
        if (current >= amount) {
            gold.put(uuid, current - amount);
            setDirty();
            return true;
        }
        return false;
    }

    /** ゴールドを直接設定 */
    public void setGold(UUID uuid, int amount) {
        gold.put(uuid, amount);
        setDirty();
    }

    // ===== 静的ヘルパーメソッド（既存 API との互換性） =====

    /** サーバーレベルから CurrencyManager を取得 */
    public static CurrencyManager get(ServerLevel level) {
        // 全次元で同一の DataStorage を使用するため、Overworld を固定で使う
        ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) overworld = level; // フォールバック
        return overworld.getDataStorage()
            .computeIfAbsent(CurrencyManager::load, CurrencyManager::new, "tac_rogue_currency");
    }

    /** Player からゴールドを取得する便利メソッド */
    public static int getGold(Player player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            return get(serverLevel).getGold(player.getUUID());
        }
        // クライアントサイドではタグベースのフォールバック
        return 0;
    }

    /** Player にゴールドを加算し、実収入として GOLD_EARN クエストを進める */
    public static void addGold(Player player, int amount) {
        addGold(player, amount, true);
    }

    /** 返金/補填など、GOLD_EARN を進めないゴールド加算 */
    public static void addGoldNoQuest(Player player, int amount) {
        addGold(player, amount, false);
    }

    private static void addGold(Player player, int amount, boolean countsForQuest) {
        if (player.level() instanceof ServerLevel serverLevel) {
            get(serverLevel).addGold(player.getUUID(), amount);
            if (countsForQuest && amount > 0 && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestManager.advanceQuest(sp, QuestManager.QuestType.GOLD_EARN, amount);
            }
        }
    }

    /** Player からゴールドを消費する便利メソッド */
    public static boolean consumeGold(Player player, int amount) {
        if (player.level() instanceof ServerLevel serverLevel) {
            return get(serverLevel).consumeGold(player.getUUID(), amount);
        }
        return false;
    }

    /** Player のゴールドを直接設定する便利メソッド */
    public static void setGold(Player player, int amount) {
        if (player.level() instanceof ServerLevel serverLevel) {
            get(serverLevel).setGold(player.getUUID(), amount);
        }
    }

    // ===== NBT シリアライゼーション =====

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (Map.Entry<UUID, Integer> entry : gold.entrySet()) {
            tag.putInt(entry.getKey().toString(), entry.getValue());
        }
        return tag;
    }

    public static CurrencyManager load(CompoundTag tag) {
        CurrencyManager data = new CurrencyManager();
        for (String key : tag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                data.gold.put(uuid, tag.getInt(key));
            } catch (Exception e) { /* skip invalid keys */ }
        }
        return data;
    }
}
