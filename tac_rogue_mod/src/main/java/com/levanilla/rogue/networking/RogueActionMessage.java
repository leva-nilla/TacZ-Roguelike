package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.service.FloorService;
import com.levanilla.rogue.core.service.GearService;
import com.levanilla.rogue.core.service.ShopService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * クライアント → サーバーのアクション要求パケット。
 * 各アクションの実際の処理ロジックは Service クラスに委譲する。
 */
public class RogueActionMessage {
    public enum ActionType {
        START_NEXT_FLOOR,
        RETRY_FLOOR,
        RETURN_TO_LOBBY,
        SELECT_GEAR_BALANCED,
        SELECT_GEAR_POWER,
        SELECT_GEAR_CLASSIC,
        BUY_ITEM,
        SYNC_DATA,
        UPGRADE_STASH,
        SYNC_STASH,
        SET_DIFFICULTY,
        APPLY_PERK,
        FLASHLIGHT_TOGGLE,
        SELL_ITEM,
        TUTORIAL_DONE
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
                    try {
                        int ordinal = Integer.parseInt(msg.data);
                        com.levanilla.rogue.core.DifficultyManager.setDifficulty(ordinal);
                        com.levanilla.rogue.core.DifficultyManager.Difficulty diff =
                            com.levanilla.rogue.core.DifficultyManager.getDifficulty();
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.difficulty_set", diff.displayName));
                    } catch (Exception ignored) {}
                }

                // --- パーク ---
                case APPLY_PERK -> {
                    if (!msg.data.isEmpty()) {
                        long perkCount = player.getTags().stream().filter(t -> t.startsWith("perk:")).count();
                        if (perkCount > 150) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A7c[ANTI-CHEAT] Too many perks!"));
                            return;
                        }
                        com.levanilla.rogue.core.PerkDefinition perk =
                            com.levanilla.rogue.core.PerkDefinition.fromTag(msg.data);
                        String uniqueTag = perk.toTag() + ":" + System.currentTimeMillis();
                        player.addTag(uniqueTag);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.perk_acquired", perk.getDisplayName()));
                        RunManager.syncPlayer(player);
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
                        "§e[FLASHLIGHT] " + (enabled ? "§aON" : "§cOFF")));
                }

                // --- ショップ ---
                case BUY_ITEM       -> ShopService.handleBuyItem(player, msg.data);
                case SELL_ITEM      -> ShopService.handleSellItem(player, msg.data);
                case UPGRADE_STASH  -> ShopService.handleUpgradeStash(player);
                case SYNC_STASH     -> ShopService.handleSyncStash(player);

                // --- 装備選択 ---
                case SELECT_GEAR_BALANCED -> GearService.applyBalanced(player);
                case SELECT_GEAR_POWER    -> GearService.applyPower(player);
                case SELECT_GEAR_CLASSIC  -> GearService.applyClassic(player);

                // --- フロア管理 ---
                case START_NEXT_FLOOR -> FloorService.handleStartNextFloor(player);
                case RETRY_FLOOR      -> FloorService.handleRetryFloor(player);
                case RETURN_TO_LOBBY  -> RunManager.returnToLobby(player);

                // --- 同期 ---
                case SYNC_DATA -> RunManager.sync();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
