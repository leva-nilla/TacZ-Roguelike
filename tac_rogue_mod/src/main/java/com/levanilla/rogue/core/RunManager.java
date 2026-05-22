package com.levanilla.rogue.core;

import com.levanilla.rogue.world.ThemeManager;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
 *  - 同じフロア番号でも各プレイヤーのダンジョンは個別に生成する。
 *  - 各プレイヤーのダンジョン基準座標は永続化したスロットから分離する。
 */
public class RunManager {

    /** デバッグチャットの有効/無効フラグ */
    public static final boolean DEBUG_ENABLED = false;

    /** マルチプレイヤー座標分離: Z 軸オフセット (フロア番号 × この値) */
    public static final int FLOOR_OFFSET_Z = 500;
    private static final int ORIGIN_SLOT_COUNT = 50_000;
    private static final String ORIGIN_X_KEY = "TacRogueDungeonOriginX";
    private static final String ORIGIN_Y_KEY = "TacRogueDungeonOriginY";
    private static final String ORIGIN_Z_KEY = "TacRogueDungeonOriginZ";
    private static final String PERK_TAGS_KEY = "TacRoguePerkTags";
    private static final Map<UUID, Set<String>> perkTagMemory = new ConcurrentHashMap<>();

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

    public static ServerPlayer findPlayerForDungeonPosition(ServerLevel level, double x, double z) {
        double maxDistance = Math.min(GameConstants.FLOOR_CLEAR_RADIUS, (FLOOR_OFFSET_Z * 0.5D) - 8.0D);
        return findPlayerForDungeonPosition(level, x, z, maxDistance);
    }

    public static ServerPlayer findPlayerForDungeonPosition(ServerLevel level, double x, double z, double maxDistance) {
        if (level == null || level.getServer() == null) return null;

        ServerPlayer instanceParticipant = FloorInstanceManager.findNearestParticipantForPosition(level, x, z, maxDistance);
        if (instanceParticipant != null) return instanceParticipant;

        ServerPlayer bestPlayer = null;
        double bestDistance = Double.MAX_VALUE;
        double maxDistanceSqr = maxDistance * maxDistance;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) continue;
            PlayerRunData data = getDataOrNull(player.getUUID());
            if (data == null || !data.isRunActive() || data.getDungeonOrigin() == null) continue;

            BlockPos origin = data.getDungeonOrigin();
            double dx = x - origin.getX();
            double dz = z - origin.getZ();
            double distanceSqr = dx * dx + dz * dz;
            if (distanceSqr <= maxDistanceSqr && distanceSqr < bestDistance) {
                bestDistance = distanceSqr;
                bestPlayer = player;
            }
        }
        return bestPlayer;
    }

    public static PlayerRunData findDataForDungeonPosition(ServerLevel level, double x, double z) {
        ServerPlayer owner = findPlayerForDungeonPosition(level, x, z);
        return owner == null ? null : getDataOrNull(owner.getUUID());
    }

    public static PlayerRunData findDataForDungeonPosition(ServerLevel level, double x, double z, double maxDistance) {
        ServerPlayer owner = findPlayerForDungeonPosition(level, x, z, maxDistance);
        return owner == null ? null : getDataOrNull(owner.getUUID());
    }

    /** プレイヤーデータを削除（ログアウト時など） */
    public static void removeData(UUID uuid) {
        playerData.remove(uuid);
        perkTagMemory.remove(uuid);
    }

    public static void clearMemory() {
        playerData.clear();
        perkTagMemory.clear();
    }

    // ===== クライアント側: 自プレイヤー同期データ =====

    public static void setClientStashLines(int lines) { ClientRunState.setStashLines(lines); }
    public static int getClientStashLines() { return ClientRunState.getStashLines(); }
    public static void setClientGold(int g) { ClientRunState.setGold(g); }
    public static int getClientGold() { return ClientRunState.getGold(); }
    public static int getClientAmmoCapacityLevel() { return ClientRunState.getAmmoCapacityLevel(); }
    public static int getClientFlashlightLevel() { return ClientRunState.getFlashlightLevel(); }

    // ===== クライアント側アクセサ (HUD/GUI 用) =====

    public static int getCurrentFloor() { return ClientRunState.getFloor(); }
    public static int getMaxReachedFloor() { return ClientRunState.getMaxReachedFloor(); }
    public static boolean isRunActive() { return ClientRunState.isRunActive(); }
    public static boolean isFloorCleared() { return ClientRunState.isFloorCleared(); }
    public static String getCurrentThemeName() { return ClientRunState.getThemeName(); }

    // レガシー互換
    public static Theme getCurrentTheme() {
        return Theme.values()[Math.abs(ClientRunState.getFloor()) % Theme.values().length];
    }

    /** クライアント同期データ受信 */
    public static void setClientData(int floor, String theme, boolean active, int maxF) {
        ClientSyncHandler.applyRunData(floor, theme, active, maxF);
    }

    public static void setClientFloorCleared(boolean v) { ClientRunState.setFloorCleared(v); }

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

    /** サーバー/クライアント共通の弾薬スタック上限レベルを取得する */
    public static int getGlobalAmmoCapacityLevel() {
        if (ClientRunState.getAmmoCapacityLevel() > 0) return ClientRunState.getAmmoCapacityLevel();
        int maxLevel = 0;
        for (PlayerRunData data : playerData.values()) {
            if (data.getAmmoCapacityLevel() > maxLevel) {
                maxLevel = data.getAmmoCapacityLevel();
            }
        }
        return maxLevel;
    }

    public static int getAmmoStackLimit(ServerPlayer player, int baseSize) {
        int capLevel = player == null ? 0 : getData(player).getAmmoCapacityLevel();
        return Math.max(1, Math.round(baseSize * (1.0f + capLevel * 0.5f)));
    }

    public static int getAmmoReserveStackLimit(ServerPlayer player, int baseSize) {
        int capLevel = player == null ? 0 : getData(player).getAmmoCapacityLevel();
        return Math.max(1, Math.round(baseSize * (1.5f + capLevel * 0.5f)));
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
        data.setDungeonOrigin(resolveDungeonOrigin(player));
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
        if (data.isFloorCleared() && data.getCurrentFloor() + 1 < data.getMaxReachedFloor()) {
            // Revisited floors should continue from the newest unlocked route, not from the replayed floor.
            data.setCurrentFloor(data.getMaxReachedFloor() - 1);
        }
        data.advanceFloor();
        data.rerollFloorSeedSalt(player.server.getTickCount());
        // フロアクリア判定の猶予タイマー設定
        data.setFloorStartTick(player.server.getTickCount());
        updateThemeName(data);

        ServerLevel rogueLevel = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogueLevel != null) {
            com.levanilla.rogue.core.service.FloorService.clearDungeonEntities(rogueLevel, data.getDungeonOrigin());
            BlockPos spawnPos = com.levanilla.rogue.world.MapGenerator.generateRoom(
                rogueLevel, data.getDungeonOrigin(), null, data.getCurrentFloor(), data.getRunSeed(), data.getFloorSeedSalt());

            safeTeleport(player, rogueLevel, spawnPos);
            syncPlayer(player);

            long seed = player.server.getWorldData().worldGenOptions().seed();
            ThemeManager.ThemeInstance theme = ThemeManager.getThemeForFloor(data.getCurrentFloor(), seed);
            com.levanilla.rogue.networking.PopupNotificationMessage.send(
                player,
                com.levanilla.rogue.networking.PopupNotificationMessage.PopupType.SYSTEM,
                Component.translatable("popup.tac_rogue.floor.title", data.getCurrentFloor()),
                Component.translatable("message.tac_rogue.floor_enter", data.getCurrentFloor(), theme.displayName)
            );
        }
    }

    /** 任意のフロアに飛ぶ（ステータス保持の周回/金策用） */
    public static void gotoFloor(ServerPlayer player, int targetFloor) {
        PlayerRunData data = getData(player);
        data.setCurrentFloor(targetFloor);
        data.setRunActive(true);
        data.setFloorCleared(false);
        data.setFloorStartTick(player.server.getTickCount());
        data.rerollFloorSeedSalt(player.server.getTickCount());
        updateThemeName(data);

        ServerLevel rogueLevel = player.server.getLevel(CommonEventHandler.ROGUE_DIM);
        if (rogueLevel != null) {
            com.levanilla.rogue.core.service.FloorService.clearDungeonEntities(rogueLevel, data.getDungeonOrigin());
            BlockPos spawnPos = com.levanilla.rogue.world.MapGenerator.generateRoom(
                rogueLevel, data.getDungeonOrigin(), null, data.getCurrentFloor(), data.getRunSeed(), data.getFloorSeedSalt());
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
            FloorInstanceManager.leaveInstance(player, false);
            PlayerRunData data = getData(player);
            data.setRunActive(false);
            player.teleportTo(lobbyLevel, 0.5, 201, 0.5, 0, 0);
            syncPlayer(player);
        }
    }

    // ===== 同フロア検索 =====

    /**
     * 旧合流ロジック用の検索。現在は個別生成のため呼び出さない。
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
     * 現在はプレイヤーごとに個別領域を再生成するため、常に false。
     */
    public static boolean hasOtherPlayersOnSameFloor(ServerPlayer player) {
        return false;
    }

    private static BlockPos resolveDungeonOrigin(ServerPlayer player) {
        CompoundTag pData = player.getPersistentData();
        if (pData.contains(ORIGIN_X_KEY) && pData.contains(ORIGIN_Y_KEY) && pData.contains(ORIGIN_Z_KEY)) {
            return new BlockPos(pData.getInt(ORIGIN_X_KEY), pData.getInt(ORIGIN_Y_KEY), pData.getInt(ORIGIN_Z_KEY));
        }

        Set<Integer> usedSlots = new HashSet<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(player.getUUID())) continue;
            PlayerRunData otherData = getDataOrNull(other.getUUID());
            if (otherData != null && otherData.getDungeonOrigin() != null) {
                usedSlots.add(Math.floorDiv(otherData.getDungeonOrigin().getX(), FLOOR_OFFSET_Z));
            }
        }

        UUID uuid = player.getUUID();
        int slot = Math.floorMod((int)(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits()), ORIGIN_SLOT_COUNT);
        for (int i = 0; i < ORIGIN_SLOT_COUNT && usedSlots.contains(slot); i++) {
            slot = (slot + 1) % ORIGIN_SLOT_COUNT;
        }

        BlockPos origin = new BlockPos(slot * FLOOR_OFFSET_Z, GameConstants.DUNGEON_BASE_Y, 0);
        storeDungeonOrigin(player, origin);
        return origin;
    }

    public static BlockPos getPrivateDungeonOrigin(ServerPlayer player) {
        return resolveDungeonOrigin(player);
    }

    private static void storeDungeonOrigin(ServerPlayer player, BlockPos origin) {
        CompoundTag pData = player.getPersistentData();
        pData.putInt(ORIGIN_X_KEY, origin.getX());
        pData.putInt(ORIGIN_Y_KEY, origin.getY());
        pData.putInt(ORIGIN_Z_KEY, origin.getZ());
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

    public static void refreshThemeName(PlayerRunData data) {
        updateThemeName(data);
    }

    // ===== 永続化 (NBT Save/Load) =====

    public static void saveToPlayerNbt(ServerPlayer player) {
        PlayerRunData data = getDataOrNull(player.getUUID());
        if (data != null) {
            player.getPersistentData().put("TacRogueRunData", data.saveToNbt());
        }
        savePerkTagsPreservingStored(player);
    }

    public static void loadFromPlayerNbt(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag pData = player.getPersistentData();
        if (pData.contains("TacRogueRunData")) {
            PlayerRunData data = getData(player);
            data.loadFromNbt(pData.getCompound("TacRogueRunData"));
            if (data.getDungeonOrigin() != null
                && !pData.contains(ORIGIN_X_KEY)
                && !pData.contains(ORIGIN_Y_KEY)
                && !pData.contains(ORIGIN_Z_KEY)) {
                storeDungeonOrigin(player, data.getDungeonOrigin());
            }
        }
        if (pData.contains("TacRogueQuestData") && !pData.getBoolean("TacRogueQuestDataMigrated")) {
            com.levanilla.rogue.core.QuestManager.migrateLegacyProgress(player, pData.getCompound("TacRogueQuestData"));
            pData.putBoolean("TacRogueQuestDataMigrated", true);
        }
        restorePerkTags(player);
    }

    public static void savePerkTags(ServerPlayer player) {
        Set<String> current = collectPerkTags(player);
        rememberPerkTags(player, current);
        writePerkTags(player, current);
    }

    private static void savePerkTagsPreservingStored(ServerPlayer player) {
        Set<String> current = collectPerkTags(player);
        if (current.isEmpty()) {
            Set<String> stored = getStoredPerkTags(player);
            if (!stored.isEmpty()) {
                applyPerkTags(player, stored);
                rememberPerkTags(player, stored);
                return;
            }
        }
        rememberPerkTags(player, current);
        writePerkTags(player, current);
    }

    private static Set<String> collectPerkTags(ServerPlayer player) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:")) {
                result.add(tag);
            }
        }
        return result;
    }

    private static void writePerkTags(ServerPlayer player, Set<String> perkTags) {
        net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
        for (String tag : perkTags) {
            tags.add(net.minecraft.nbt.StringTag.valueOf(tag));
        }
        player.getPersistentData().put(PERK_TAGS_KEY, tags);
    }

    public static void restorePerkTags(ServerPlayer player) {
        if (!collectPerkTags(player).isEmpty()) return;
        Set<String> stored = getStoredPerkTags(player);
        if (stored.isEmpty()) return;
        applyPerkTags(player, stored);
        rememberPerkTags(player, stored);
    }

    private static Set<String> getStoredPerkTags(ServerPlayer player) {
        Set<String> stored = new java.util.LinkedHashSet<>();
        Set<String> remembered = perkTagMemory.get(player.getUUID());
        if (remembered != null) {
            stored.addAll(remembered);
        }

        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        if (!data.contains(PERK_TAGS_KEY)) return stored;
        net.minecraft.nbt.ListTag tags = data.getList(PERK_TAGS_KEY, net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.getString(i);
            if (tag.startsWith("perk:")) stored.add(tag);
        }
        return stored;
    }

    private static void applyPerkTags(ServerPlayer player, Set<String> perkTags) {
        for (String tag : perkTags) {
            if (tag.startsWith("perk:")) player.addTag(tag);
        }
    }

    private static void rememberPerkTags(ServerPlayer player, Set<String> perkTags) {
        if (perkTags.isEmpty()) {
            perkTagMemory.remove(player.getUUID());
        } else {
            perkTagMemory.put(player.getUUID(), Set.copyOf(perkTags));
        }
    }

    // ===== 同期 =====

    /** 特定プレイヤーにそのプレイヤーのデータを同期する */
    public static void syncPlayer(ServerPlayer player) {
        restorePerkTags(player);
        saveToPlayerNbt(player); // 念のため毎同期時にNBTへ保存

        PlayerRunData data = getData(player);
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncRunMessage(
                data.getCurrentFloor(),
                data.getThemeName(),
                data.isRunActive(),
                data.isFloorCleared(),
                data.getMaxReachedFloor(),
                data.getAmmoCapacityLevel())
        );

        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        int meleeLevel = player.getPersistentData().getInt("TacRogueMeleeLevel");
        int randomPerkBuys = player.getPersistentData().getInt("RandomPerkBuys");
        int flashlightLevel = player.getPersistentData().getInt("TacRogueFlashlightLevel");
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncMetaMessage(invLevel, meleeLevel, randomPerkBuys, flashlightLevel)
        );

        // ゴールド同期
        int gold = CurrencyManager.getGold(player);
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncGoldMessage(gold)
        );

        // パーク同期
        Set<String> effectivePerks = collectPerkTags(player);
        if (effectivePerks.isEmpty()) {
            effectivePerks = getStoredPerkTags(player);
            if (!effectivePerks.isEmpty()) {
                applyPerkTags(player, effectivePerks);
                rememberPerkTags(player, effectivePerks);
            }
        }
        StringBuilder perks = new StringBuilder();
        for (String tag : effectivePerks) {
            perks.append(tag).append(",");
        }
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncPerksMessage(perks.toString())
        );

        long medicalBuffUntil = player.getPersistentData().getLong("TacRogueMedicalBuffUntil");
        int medicalBuffTicks = (int)Math.max(0L, medicalBuffUntil - player.level().getGameTime());
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("medical_buff:" + medicalBuffTicks)
        );

        com.levanilla.rogue.core.service.DeepProgressService.sync(player);
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
        ClientSyncHandler.onSync(data);
    }
    public static boolean isBossFloor() {
        return ThemeManager.isBossFloor(getCurrentFloor());
    }

    /** サーバー側でプレイヤー固有のボスフロア判定 */
    public static boolean isBossFloor(ServerPlayer player) {
        return ThemeManager.isBossFloor(getData(player).getCurrentFloor());
    }
}
