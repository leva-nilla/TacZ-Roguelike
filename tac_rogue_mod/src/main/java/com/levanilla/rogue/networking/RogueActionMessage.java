package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import com.levanilla.rogue.core.service.FloorService;
import com.levanilla.rogue.core.service.GearService;
import com.levanilla.rogue.core.service.LowHealthChallengeService;
import com.levanilla.rogue.core.service.ShopService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * クライアント → サーバーのアクション要求パケット。
 * 各アクションの実際の処理ロジックは Service クラスに委譲する。
 */
public class RogueActionMessage {

    /**
     * サーバー側パーク選択セッション管理。
     * パーク選択画面を開いた際に候補を登録し、APPLY_PERK 受信時に照合する。
     * 未登録のパークタグは拒否される（チート防止）。
     */
    private static final Map<UUID, List<String>> pendingPerkChoices = new ConcurrentHashMap<>();

    /** パーク候補をサーバー側に登録する（パーク画面表示時に呼び出す） */
    public static void registerPerkChoices(UUID playerId, List<PerkDefinition> choices) {
        List<String> tags = choices.stream().map(PerkDefinition::toTag).toList();
        pendingPerkChoices.put(playerId, tags);
    }

    /** パーク候補をクリアする */
    public static void clearPerkChoices(UUID playerId) {
        pendingPerkChoices.remove(playerId);
    }

    /** パーク候補を取得する（PerkActionMessage からの照合用） */
    public static List<String> getPendingPerkChoices(UUID playerId) {
        return pendingPerkChoices.get(playerId);
    }

    /** サーバー停止時にセッションデータをクリア */
    public static void clearMemory() {
        pendingPerkChoices.clear();
    }

    public enum ActionType {
        START_NEXT_FLOOR,
        RETRY_FLOOR,
        RETURN_TO_LOBBY,
        SELECT_GEAR_BALANCED,
        SELECT_GEAR_POWER,
        SELECT_GEAR_CLASSIC,
        BUY_ITEM,
        GOTO_FLOOR,
        SYNC_DATA,
        UPGRADE_STASH,
        SYNC_STASH,
        SET_DIFFICULTY,
        APPLY_PERK,
        FLASHLIGHT_TOGGLE,
        INTERACT_STASH,
        INTERACT_OBJECTIVE,
        SELL_ITEM,
        TUTORIAL_DONE,
        REROLL_PERK,
        START_WAITING_FLOOR
    }

    private final ActionType action;
    private final String data;

    public RogueActionMessage(ActionType action) {
        this(action, "");
    }

    public RogueActionMessage(ActionType action, String data) {
        this.action = action;
        this.data = data;
    }

    public ActionType getAction() { return action; }
    public String getData() { return data; }

    public static void encode(RogueActionMessage msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.data);
    }

    public static RogueActionMessage decode(FriendlyByteBuf buf) {
        return new RogueActionMessage(buf.readEnum(ActionType.class), buf.readUtf());
    }

    /**
     * パケットハンドラ: 各アクションを対応する Service へディスパッチ。
     */
    public static void handle(RogueActionMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.action) {
                // --- 難易度 ---
                case SET_DIFFICULTY -> {
                    if (!player.hasPermissions(2)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "\u00A7c[ERROR] OP permission required to change difficulty."), true);
                        return;
                    }
                    try {
                        int ordinal = Integer.parseInt(msg.data);
                        com.levanilla.rogue.core.DifficultyManager.setDifficultyAndSave(
                            player.serverLevel(), ordinal);
                        com.levanilla.rogue.core.DifficultyManager.Difficulty diff =
                            com.levanilla.rogue.core.DifficultyManager.getDifficulty();
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.difficulty_set", diff.displayName), true);
                    } catch (Exception ignored) {}
                }

                // --- パーク ---
                case APPLY_PERK -> {
                    com.levanilla.rogue.core.service.PerkApplyService.applySelectedPerk(player, msg.data);
                }
                case REROLL_PERK -> {
                    if (com.levanilla.rogue.core.CurrencyManager.consumeGold(player, com.levanilla.rogue.core.GameConstants.PERK_REROLL_COST)) {
                        RunManager.syncPlayer(player);
                        if ("floor_clear".equals(msg.data)) {
                            OpenFloorClearScreenMessage.sendFloorClear(player, false);
                        } else if ("initial".equals(msg.data)) {
                            OpenPerkChoiceMessage.sendPerkChoices(player,
                                OpenPerkChoiceMessage.PerkScreenType.INITIAL);
                        } else if ("boss".equals(msg.data)) {
                            OpenPerkChoiceMessage.sendPerkChoices(player,
                                OpenPerkChoiceMessage.PerkScreenType.BOSS);
                        } else {
                            OpenPerkChoiceMessage.sendPerkChoices(player,
                                OpenPerkChoiceMessage.PerkScreenType.NORMAL);
                        }
                    } else {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.tac_rogue.inventory.insufficient_funds"), true);
                    }
                }

                // --- チュートリアルフラグ ---
                case TUTORIAL_DONE -> {
                    if (!player.getTags().contains("rogue:tutorial_seen")) {
                        player.addTag("rogue:tutorial_seen");
                    }
                }

                // --- フラッシュライト ---
                case FLASHLIGHT_TOGGLE -> {
                    boolean enabled = com.levanilla.rogue.core.FlashlightManager.toggle(player.getUUID());
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        enabled ? "§eFlashlight ON" : "§7Flashlight OFF"), true);
                }
                case INTERACT_STASH -> {
                    try {
                        String[] parts = msg.data.split(":");
                        if (parts.length != 3) return;
                        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]));
                        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 36.0) return;
                        if (player.level().getBlockState(pos).is(com.levanilla.rogue.core.ModBlocks.STASH_TERMINAL.get())) {
                            com.levanilla.rogue.world.StashBlock.openFor(player);
                        }
                    } catch (Exception ignored) {}
                }
                case INTERACT_OBJECTIVE -> {
                    try {
                        String[] parts = msg.data.split(":");
                        if (parts.length != 3) return;
                        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]));
                        com.levanilla.rogue.core.service.FloorObjectiveService.handleObjectiveBlockInteract(player, pos);
                    } catch (Exception ignored) {}
                }

                // --- ショップ ---
                case BUY_ITEM -> {
                    if (requireQuartermaster(player)) ShopService.handleBuyItem(player, msg.data);
                }
                case SELL_ITEM -> {
                    if (requireQuartermaster(player)) ShopService.handleSellItem(player, msg.data);
                }
                case UPGRADE_STASH -> {
                    if (requireQuartermaster(player)) ShopService.handleUpgradeStash(player);
                }
                case SYNC_STASH     -> ShopService.handleSyncStash(player);

                // --- 装備選択 ---
                case SELECT_GEAR_BALANCED -> GearService.applyBalanced(player);
                case SELECT_GEAR_POWER    -> GearService.applyPower(player);
                case SELECT_GEAR_CLASSIC  -> GearService.applyClassic(player);

                // --- フロア管理 ---
                case START_NEXT_FLOOR -> {
                    LowHealthChallengeService.setRequested(player, LowHealthChallengeService.isRequestedPayload(msg.data));
                    FloorService.handleStartNextFloor(player, FloorInstanceManager.EntryMode.parse(msg.data));
                }
                case RETRY_FLOOR      -> {
                    LowHealthChallengeService.setRequested(player, LowHealthChallengeService.isRequestedPayload(msg.data));
                    FloorService.handleRetryFloor(player, FloorInstanceManager.EntryMode.parse(msg.data));
                }
                case START_WAITING_FLOOR -> {
                    if (!FloorInstanceManager.requestStartWaitingInstance(player)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[CO-OP] No public waiting instance to start."), true);
                    }
                }
                case RETURN_TO_LOBBY  -> RunManager.returnToLobby(player);
                case GOTO_FLOOR -> {
                    try {
                        String[] parts = msg.data == null ? new String[0] : msg.data.split("[:|]", 2);
                        int f = Integer.parseInt(parts.length > 0 ? parts[0] : msg.data);
                        LowHealthChallengeService.setRequested(player, LowHealthChallengeService.isRequestedPayload(msg.data));
                        FloorService.handleGotoFloor(player, f, FloorInstanceManager.EntryMode.parse(msg.data));
                    } catch (NumberFormatException ignored) {}
                }

                // --- 同期 ---
                case SYNC_DATA -> RunManager.syncPlayer(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean requireQuartermaster(ServerPlayer player) {
        if (com.levanilla.rogue.world.NpcManager.canUseQuartermaster(player)) return true;
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.WARNING,
            net.minecraft.network.chat.Component.literal("QUARTERMASTER"),
            net.minecraft.network.chat.Component.translatable("gui.tac_rogue.npc_menu.too_far"),
            100);
        return false;
    }
}
