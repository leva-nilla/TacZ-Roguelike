package com.levanilla.rogue.networking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * TacRogue の共通ネットワーク・チャンネル管理クラス
 * サーバーとクライアント間のすべてのパケット通信（メッセージング）をここで定義し、登録します
 */
public class TacRogueNetworking {
    private static final String PROTOCOL_VERSION = "5"; // 通信プロトコルのバージョン（互換性の確認用）
    
    // チャンネルの定義
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("tac_rogue", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0; // 各パケットに割り振る一意のID

    /**
     * パケットの登録処理
     * サーバーからクライアント(PLAY_TO_CLIENT)か、クライアントからサーバー(PLAY_TO_SERVER)かを定義します
     */
    public static void register() {
        // 現在のランの状態（階層など）を同期するパケット (S -> C)
        CHANNEL.messageBuilder(SyncRunMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRunMessage::encode)
                .decoder(SyncRunMessage::decode)
                .consumerMainThread(SyncRunMessage::handle)
                .add();
        
        // パーク選択画面を開くためのパケット (S -> C)
        CHANNEL.messageBuilder(OpenPerkScreenMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenPerkScreenMessage::encode)
                .decoder(OpenPerkScreenMessage::decode)
                .consumerMainThread(OpenPerkScreenMessage::handle)
                .add();

        // 階層クリア画面を開くためのパケット (S -> C)
        CHANNEL.messageBuilder(OpenFloorClearScreenMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenFloorClearScreenMessage::encode)
                .decoder(OpenFloorClearScreenMessage::decode)
                .consumerMainThread(OpenFloorClearScreenMessage::handle)
                .add();

        // プレイヤーの行動（ボタン押下など）をサーバーに伝えるパケット (C -> S)
        CHANNEL.messageBuilder(RogueActionMessage.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RogueActionMessage::encode)
                .decoder(RogueActionMessage::decode)
                .consumerMainThread(RogueActionMessage::handle)
                .add();

        // スタッシュ画面の同期・表示用パケット (S -> C)
        CHANNEL.messageBuilder(SyncStashMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncStashMessage::encode)
                .decoder(SyncStashMessage::decode)
                .consumerMainThread(SyncStashMessage::handle)
                .add();

        // 各種データの汎用同期 (S -> C)
        CHANNEL.messageBuilder(SyncDataMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncDataMessage::encode)
                .decoder(SyncDataMessage::decode)
                .consumerMainThread(SyncDataMessage::handle)
                .add();

        // 初期装備選択画面を開く (S -> C)
        CHANNEL.messageBuilder(OpenStarterGearMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenStarterGearMessage::encode)
                .decoder(OpenStarterGearMessage::decode)
                .consumerMainThread(OpenStarterGearMessage::handle)
                .add();

        // ショップ画面を開く (S -> C)
        CHANNEL.messageBuilder(OpenShopMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenShopMessage::encode)
                .decoder(OpenShopMessage::decode)
                .consumerMainThread(OpenShopMessage::handle)
                .add();

        // パーク選択をサーバーに通知 (C -> S)
        CHANNEL.messageBuilder(PerkActionMessage.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PerkActionMessage::encode)
                .decoder(PerkActionMessage::decode)
                .consumerMainThread(PerkActionMessage::handle)
                .add();

        // === 型安全な新メッセージ群 ===

        // ゴールド残高同期 (S -> C)
        CHANNEL.messageBuilder(SyncGoldMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncGoldMessage::encode)
                .decoder(SyncGoldMessage::decode)
                .consumerMainThread(SyncGoldMessage::handle)
                .add();

        // パーク一覧同期 (S -> C)
        CHANNEL.messageBuilder(SyncPerksMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPerksMessage::encode)
                .decoder(SyncPerksMessage::decode)
                .consumerMainThread(SyncPerksMessage::handle)
                .add();

        // ダメージインジケーター (S -> C)
        CHANNEL.messageBuilder(DamageIndicatorMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DamageIndicatorMessage::encode)
                .decoder(DamageIndicatorMessage::decode)
                .consumerMainThread(DamageIndicatorMessage::handle)
                .add();

        // ドロップインジケーター (S -> C)
        CHANNEL.messageBuilder(DropIndicatorMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DropIndicatorMessage::encode)
                .decoder(DropIndicatorMessage::decode)
                .consumerMainThread(DropIndicatorMessage::handle)
                .add();

        // パーク選択画面 (S -> C) — 3種を1メッセージに統合
        CHANNEL.messageBuilder(OpenPerkChoiceMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenPerkChoiceMessage::encode)
                .decoder(OpenPerkChoiceMessage::decode)
                .consumerMainThread(OpenPerkChoiceMessage::handle)
                .add();

        // デバッグメニューを開く (S -> C)
        CHANNEL.messageBuilder(OpenDebugMenuMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenDebugMenuMessage::encode)
                .decoder(OpenDebugMenuMessage::decode)
                .consumerMainThread(OpenDebugMenuMessage::handle)
                .add();

        // デバッグGUI操作 (C -> S)
        CHANNEL.messageBuilder(DebugActionMessage.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DebugActionMessage::encode)
                .decoder(DebugActionMessage::decode)
                .consumerMainThread(DebugActionMessage::handle)
                .add();

        // プレイヤー向けポップアップ通知 (S -> C)
        CHANNEL.messageBuilder(PopupNotificationMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PopupNotificationMessage::encode)
                .decoder(PopupNotificationMessage::decode)
                .consumerMainThread(PopupNotificationMessage::handle)
                .add();

        CHANNEL.messageBuilder(OpenNpcMenuMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenNpcMenuMessage::encode)
                .decoder(OpenNpcMenuMessage::decode)
                .consumerMainThread(OpenNpcMenuMessage::handle)
                .add();

        CHANNEL.messageBuilder(NpcMenuActionMessage.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NpcMenuActionMessage::encode)
                .decoder(NpcMenuActionMessage::decode)
                .consumerMainThread(NpcMenuActionMessage::handle)
                .add();

        CHANNEL.messageBuilder(NpcInteractMessage.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NpcInteractMessage::encode)
                .decoder(NpcInteractMessage::decode)
                .consumerMainThread(NpcInteractMessage::handle)
                .add();

        CHANNEL.messageBuilder(AdsInputMessage.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AdsInputMessage::encode)
                .decoder(AdsInputMessage::decode)
                .consumerMainThread(AdsInputMessage::handle)
                .add();

        // プレイヤー強化メタデータ同期 (S -> C)
        CHANNEL.messageBuilder(SyncMetaMessage.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncMetaMessage::encode)
                .decoder(SyncMetaMessage::decode)
                .consumerMainThread(SyncMetaMessage::handle)
                .add();
    }

    /**
     * 特定のプレイヤーにメッセージを送信する (Server -> Client)
     */
    public static void sendToClient(SyncRunMessage message, ServerPlayer player) {
        CHANNEL.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
    
    /**
     * 全プレイヤーにメッセージを送信する
     */
    public static void sendToAll(SyncRunMessage message) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), message);
    }

    // 各種画面を開くためのショートカットメソッド群
    public static void openStarterGear(ServerPlayer player) {
        CHANNEL.sendTo(new OpenStarterGearMessage(), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void openPerkScreen(ServerPlayer player) {
        OpenPerkChoiceMessage.sendPerkChoices(player, OpenPerkChoiceMessage.PerkScreenType.NORMAL);
    }

    public static void openFloorClear(ServerPlayer player) {
        OpenPerkChoiceMessage.sendPerkChoices(player, OpenPerkChoiceMessage.PerkScreenType.NORMAL);
    }

    public static void openShop(ServerPlayer player) {
        CHANNEL.sendTo(new OpenShopMessage(), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void openDebugMenu(ServerPlayer player) {
        CHANNEL.sendTo(new OpenDebugMenuMessage(), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void openNpcMenu(ServerPlayer player, String role, boolean dungeon, boolean floorCleared) {
        CHANNEL.sendTo(new OpenNpcMenuMessage(role, dungeon, floorCleared), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
