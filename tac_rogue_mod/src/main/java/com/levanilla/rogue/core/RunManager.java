package com.levanilla.rogue.core;

import com.levanilla.rogue.world.ThemeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ローグライクの進行状況をプレイヤーごとに管理するクラス (v1.0.0 — Multiplayer対応)
 *
 * サーバー側: UUID → PlayerRunData の Map で個別管理。
 * クライアント側: 自プレイヤーの同期データを static フィールドに保持。
 *
 * マルチプレイヤールール:
 *  - ラン開始/進行はプレイヤーごとに独立。
 *  - 同じフロア番号のプレイヤーがいる場合、後発は先行プレイヤーの座標に合流。
 *  - 複数人が同フロアにいる場合、RETRY は再生成ではなくスポーン位置テレポート。
 *  - 各プレイヤーのダンジョン基準座標は floor * FLOOR_OFFSET_Z で分離。
 */
public class RunManager {

    /** デバッグチャットの有効/無効フラグ */
    public static final boolean DEBUG_ENABLED = false;

    /** マルチプレイヤー座標分離: Z 軸オフセット (フロア番号 × この値) */
    public static final int FLOOR_OFFSET_Z = 500;

    // レガシー互換 Theme enum
    public enum Theme {
        MANSION, LABORATORY, HOSPITAL, INDUSTRIAL, RUINED
    }

    // ===== サーバー側: プレイヤー個別データ =====

    private static final Map<UUID, PlayerRunData> playerData = new ConcurrentHashMap<>();

    /** プレイヤーのランデータを取得（なければ作成） */
    public static PlayerRunData getData(ServerPlayer player) {
        return playerData.computeIfAbsent(player.getUUID(), k -> new PlayerRunData());
    }

    /** UUID指定で取得（nullの可能性あり） */
    public static PlayerRunData getDataOrNull(UUID uuid) {
        return playerData.get(uuid);
    }

    /** プレイヤーデータを削除（ログアウト時など） */
    public static void removeData(UUID uuid) {
        playerData.remove(uuid);
    }

    public static void clearMemory() {
        playerData.clear();
    }

    // ===== クライアント側: 自プレイヤー同期データ =====

    private static int clientFloor = 0;
    private static int clientMaxReachedFloor = 0;
    private static boolean clientRunActive = false;
    private static boolean clientFloorCleared = false;
    private static int clientStashLines = 2;
    private static String clientThemeName = "RUINS - OVERGROWN";
    private static int clientGold = 0;

    public static void setClientStashLines(int lines) { clientStashLines = lines; }
    public static int getClientStashLines() { return clientStashLines; }
    public static void setClientGold(int g) { clientGold = g; }
    public static int getClientGold() { return clientGold; }

    // ===== クライアント側アクセサ (HUD/GUI 用) =====

    public static int getCurrentFloor() { return clientFloor; }
    public static int getMaxReachedFloor() { return clientMaxReachedFloor; }
    public static boolean isRunActive() { return clientRunActive; }
    public static boolean isFloorCleared() { return clientFloorCleared; }
    public static String getCurrentThemeName() { return clientThemeName; }

    // レガシー互換
    public static Theme getCurrentTheme() {
        return Theme.values()[Math.abs(clientFloor) % Theme.values().length];
    }

    /** クライアント同期データ受信 */
    public static void setClientData(int floor, String theme, boolean active, int maxF) {
        clientFloor = floor;
        clientThemeName = theme;
        clientRunActive = active;
        clientMaxReachedFloor = maxF;
    }

    public static void setClientFloorCleared(boolean v) { clientFloorCleared = v; }

    // ===== サーバー側アクセサ (後方互換ラッパー — 全プレイヤーに対して操作) =====

    /** @deprecated サーバー側では getData(player).setRunActive(v) を使用 */
    public static void setRunActive(boolean v) {
        // 後方互換: 全プレイヤーに適用
        for (PlayerRunData data : playerData.values()) data.setRunActive(v);
    }

    /** @deprecated サーバー側では getData(player).setFloorCleared(v) を使用 */
    public static void setFloorCleared(boolean v) {
        for (PlayerRunData data : playerData.values()) data.setFloorCleared(v);
    }

    /** @deprecated サーバーでは getData(player).getRunSeed() を使用 */
    public static long getRunSeed() {
        // 最初に見つかったアクティブなランのシードを返す
        for (PlayerRunData data : playerData.values()) {
            if (data.isRunActive() || data.getCurrentFloor() > 0) return data.getRunSeed();
        }
        return 0;
    }

    // ===== 共通テレポートロジック =====

    public static void safeTeleport(ServerPlayer player, ServerLevel targetLevel, BlockPos spawnPos) {
        targetLevel.getChunkSource().addRegionTicket(
            TicketType.POST_TELEPORT, new ChunkPos(spawnPos), 1, player.getId());
        player.teleportTo(targetLevel, spawnPos.getX() + 0.5, spawnPos.getY() + 0.2, spawnPos.getZ() + 0.5, 0, 0);
    }

    // ===== ライフサイクル (プレイヤー固有) =====

    /** 新しいランを開始する */
    public static void startRun(ServerPlayer player) {
        PlayerRunData data = getData(player);
        data.startRun();
        // フロアクリア判定の猶予タイマー設定（5秒=100tick）
        data.setFloorStartTick(player.server.getTickCount());
        // 座標オフセット計算: 各プレイヤーの UUID ハッシュから一意なオフセットを決定
        int playerSlot = Math.abs(player.getUUID().hashCode()) % 1000;
        BlockPos origin = new BlockPos(playerSlot * FLOOR_OFFSET_Z, GameConstants.DUNGEON_BASE_Y, 0);
        data.setDungeonOrigin(origin);
        updateThemeName(data);
        syncPlayer(player);
    }

    /** @deprecated レガシー互換 — startRun(ServerPlayer) を使用 */
    public static void startRun() {
        // 後方互換のためサーバー上の全プレイヤーに対して呼び出す
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                startRun(p);
            }
        }
    }

    /** 次の階層へ進む */
    public static void startNextFloor(ServerPlayer player) {
        PlayerRunData data = getData(player);
        data.advanceFloor();
        // フロアクリア判定の猶予タイマー設定
        data.setFloorStartTick(player.server.getTickCount());
        updateThemeName(data);

        ServerLevel rogueLevel = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogueLevel != null) {
            // 同フロアに既にいるプレイヤーを検索
            ServerPlayer existingPlayer = findPlayerOnFloor(player.server, data.getCurrentFloor(), player.getUUID());

            BlockPos spawnPos;
            if (existingPlayer != null) {
                // 先行プレイヤーの座標へ直接テレポートするのは危険なので、フロアの初期地点の上空にテレポート
                spawnPos = data.getDungeonOrigin().above(2);
            } else {
                // 新規生成の前に確実に前回のエンティティを消去する
                com.levanilla.rogue.core.service.FloorService.clearDungeonEntities(rogueLevel, data.getDungeonOrigin());
                // 新規生成
                spawnPos = com.levanilla.rogue.world.MapGenerator.generateRoom(
                    rogueLevel, data.getDungeonOrigin(), null, data.getCurrentFloor(), data.getRunSeed());
            }

            safeTeleport(player, rogueLevel, spawnPos);
            syncPlayer(player);

            long seed = player.server.getWorldData().worldGenOptions().seed();
            ThemeManager.ThemeInstance theme = ThemeManager.getThemeForFloor(data.getCurrentFloor(), seed);
            player.sendSystemMessage(Component.translatable(
                "message.tac_rogue.floor_enter", data.getCurrentFloor(), theme.displayName));
        }
    }

    /** 任意のフロアに飛ぶ（ステータス保持の周回/金策用） */
    public static void gotoFloor(ServerPlayer player, int targetFloor) {
        PlayerRunData data = getData(player);
        data.setCurrentFloor(targetFloor);
        data.setRunActive(true);
        data.setFloorCleared(false);
        data.setFloorStartTick(player.server.getTickCount());
        data.setRunSeed(System.nanoTime()); // 新しいフロアシード
        updateThemeName(data);

        ServerLevel rogueLevel = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogueLevel != null) {
            ServerPlayer existingPlayer = findPlayerOnFloor(player.server, data.getCurrentFloor(), player.getUUID());
            BlockPos spawnPos;
            if (existingPlayer != null) {
                spawnPos = data.getDungeonOrigin().above(2);
            } else {
                com.levanilla.rogue.core.service.FloorService.clearDungeonEntities(rogueLevel, data.getDungeonOrigin());
                spawnPos = com.levanilla.rogue.world.MapGenerator.generateRoom(
                    rogueLevel, data.getDungeonOrigin(), null, data.getCurrentFloor(), data.getRunSeed());
            }
            safeTeleport(player, rogueLevel, spawnPos);
            syncPlayer(player);

            long seed = player.server.getWorldData().worldGenOptions().seed();
            ThemeManager.ThemeInstance theme = ThemeManager.getThemeForFloor(data.getCurrentFloor(), seed);
            player.sendSystemMessage(Component.literal("§e[FARMING] §fRevisiting Floor " + targetFloor + " (" + theme.displayName + ")"));
        }
    }

    /** ロビーへ帰還する */
    public static void returnToLobby(ServerPlayer player) {
        ServerLevel lobbyLevel = player.server.getLevel(CommonEventHandler.LOBBY_DIM);
        if (lobbyLevel != null) {
            PlayerRunData data = getData(player);
            data.setRunActive(false);
            player.teleportTo(lobbyLevel, 0.5, 201, 0.5, 0, 0);
            syncPlayer(player);
        }
    }

    // ===== 同フロア検索 =====

    /**
     * 指定フロアに既にいるプレイヤーを検索する（自分自身は除外）。
     * 同フロア合流ロジック: 先行プレイヤーの位置をスポーン先として使用。
     */
    private static ServerPlayer findPlayerOnFloor(net.minecraft.server.MinecraftServer server, int floor, UUID excludeUUID) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(excludeUUID)) continue;
            PlayerRunData otherData = getDataOrNull(p.getUUID());
            if (otherData != null && otherData.isRunActive() && otherData.getCurrentFloor() == floor) {
                return p;
            }
        }
        return null;
    }

    /**
     * 同じフロアに他プレイヤーがいるか判定する。
     * trueの場合、RETRY は再生成ではなくスポーンテレポートのみ。
     */
    public static boolean hasOtherPlayersOnSameFloor(ServerPlayer player) {
        PlayerRunData data = getData(player);
        return findPlayerOnFloor(player.server, data.getCurrentFloor(), player.getUUID()) != null;
    }

    // ===== テーマ管理 =====

    private static void updateThemeName(PlayerRunData data) {
        if (data.getCurrentFloor() > 0) {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            long seed = server != null ? server.getWorldData().worldGenOptions().seed() : 0;
            ThemeManager.ThemeInstance theme = ThemeManager.getThemeForFloor(data.getCurrentFloor(), seed);
            data.setThemeName(theme.displayName);
        }
        data.setTheme(Theme.values()[data.getCurrentFloor() % Theme.values().length]);
    }

    // ===== 永続化 (NBT Save/Load) =====

    public static void saveToPlayerNbt(ServerPlayer player) {
        PlayerRunData data = getDataOrNull(player.getUUID());
        if (data != null) {
            player.getPersistentData().put("TacRogueRunData", data.saveToNbt());
        }
    }

    public static void loadFromPlayerNbt(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag pData = player.getPersistentData();
        if (pData.contains("TacRogueRunData")) {
            PlayerRunData data = getData(player);
            data.loadFromNbt(pData.getCompound("TacRogueRunData"));
        }
    }

    // ===== 同期 =====

    /** 特定プレイヤーにそのプレイヤーのデータを同期する */
    public static void syncPlayer(ServerPlayer player) {
        saveToPlayerNbt(player); // 念のため毎同期時にNBTへ保存

        PlayerRunData data = getData(player);
        String syncData = data.getCurrentFloor() + ":" + data.getThemeName() + ":"
            + data.isRunActive() + ":" + data.isFloorCleared() + ":" + data.getMaxReachedFloor();
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage(syncData)
        );

        // ゴールド同期
        int gold = CurrencyManager.getGold(player);
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("gold:" + gold)
        );

        // パーク同期
        StringBuilder perks = new StringBuilder("perks:");
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:")) perks.append(tag).append(",");
        }
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage(perks.toString())
        );
    }

    /** @deprecated レガシー互換 — 全プレイヤーに個別同期 */
    public static void sync() {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncPlayer(player);
            }
        }
    }

    /** クライアント側のデータ受信ハンドラ */
    public static void onSync(String data) {
        try {
            if (data.startsWith("perks:")) {
                String[] tags = data.substring(6).split(",");
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                    net.minecraft.client.player.LocalPlayer clientPlayer = net.minecraft.client.Minecraft.getInstance().player;
                    if (clientPlayer != null) {
                        clientPlayer.getTags().removeIf(t -> t.startsWith("perk:"));
                        for (String t : tags) {
                            if (!t.isEmpty()) clientPlayer.addTag(t);
                        }
                    }
                });
                return;
            }

            if (data.startsWith("dmg:")) {
                String[] parts = data.substring(4).split(":");
                if (parts.length >= 5) {
                    float damage = Float.parseFloat(parts[0]);
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    boolean isCritical = Boolean.parseBoolean(parts[4]);
                    boolean isShotgun = parts.length >= 6 && Boolean.parseBoolean(parts[5]);
                    if (isShotgun) {
                        com.levanilla.rogue.client.ClientEventHandler.addShotgunDamageIndicator(x, y, z, damage, isCritical);
                    } else {
                        com.levanilla.rogue.client.ClientEventHandler.addDamageIndicator(x, y, z, damage, isCritical);
                    }
                }
                return;
            }

            if (data.startsWith("drop:")) {
                String[] parts = data.substring(5).split(":");
                if (parts.length >= 4) {
                    com.levanilla.rogue.client.ClientEventHandler.addDropIndicator(
                        Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), parts[0]);
                }
                return;
            }

            if (data.startsWith("gold:")) {
                clientGold = Integer.parseInt(data.substring(5));
                return;
            }

            if (data.startsWith("quest_data:")) {
                String payload = data.substring(11); // chapter|quest1,quest2,...
                String[] chapterAndQuests = payload.split("\\|", 2);
                int chapter = Integer.parseInt(chapterAndQuests[0]);
                java.util.List<String[]> questList = new java.util.ArrayList<>();
                if (chapterAndQuests.length > 1 && !chapterAndQuests[1].isEmpty()) {
                    String[] questEntries = chapterAndQuests[1].split(",");
                    for (String entry : questEntries) {
                        if (entry.isEmpty()) continue;
                        String[] fields = entry.split(";");
                        if (fields.length >= 6) {
                            questList.add(fields);
                        }
                    }
                }
                com.levanilla.rogue.client.RogueInventoryScreen.syncQuestData(chapter, questList);
                return;
            }

            if (data.equals("open_quest_tab")) {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                    com.levanilla.rogue.client.ClientEventHandler.openInventoryWithTab(
                        com.levanilla.rogue.client.RogueInventoryScreen.Tab.QUEST);
                });
                return;
            }

            if (data.startsWith("perk_init:")) { com.levanilla.rogue.client.PerkManager.openInitialPerkScreen(); return; }
            if (data.startsWith("perk_boss:")) { com.levanilla.rogue.client.PerkManager.openBossPerkScreen(); return; }
            if (data.startsWith("perk_normal:")) { com.levanilla.rogue.client.PerkManager.openPerkScreen(); return; }
            
            if (data.startsWith("open_floor_selection:")) {
                String[] split = data.substring(21).split(":");
                int maxF = Integer.parseInt(split[0]);
                long seed = split.length > 1 ? Long.parseLong(split[1]) : 0L;
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                    com.levanilla.rogue.client.ClientEventHandler.openFloorSelectionScreen(maxF, seed);
                });
                return;
            }

            String[] parts = data.split(":", 5);
            if (parts.length >= 3) {
                int maxF = parts.length >= 5 ? Integer.parseInt(parts[4]) : 0;
                setClientData(Integer.parseInt(parts[0]), parts[1], Boolean.parseBoolean(parts[2]), maxF);
                if (parts.length >= 4) {
                    clientFloorCleared = Boolean.parseBoolean(parts[3]);
                }
            }
        } catch (Exception e) { /* ignore parse errors */ }
    }

    public static boolean isBossFloor() {
        return ThemeManager.isBossFloor(getCurrentFloor());
    }

    /** サーバー側でプレイヤー固有のボスフロア判定 */
    public static boolean isBossFloor(ServerPlayer player) {
        return ThemeManager.isBossFloor(getData(player).getCurrentFloor());
    }
}
