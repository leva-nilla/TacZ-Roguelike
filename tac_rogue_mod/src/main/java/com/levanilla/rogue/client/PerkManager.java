package com.levanilla.rogue.client;

import com.levanilla.rogue.core.PerkDefinition;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * パーク画面の表示を管理するクラス。
 * サーバーからのパケットに応じてパーク選択画面を開く。
 */
public class PerkManager {

    /** 通常フロアクリア時のパーク選択画面を開く（レガシー: クライアント側生成） */
    public static void openPerkScreen() {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new PerkScreen(false))
        );
    }

    /** ボスフロアクリア時の高品質パーク選択画面を開く（レガシー: クライアント側生成） */
    public static void openBossPerkScreen() {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new PerkScreen(true))
        );
    }

    /** 初期パーク選択画面を開く（レガシー: クライアント側生成） */
    public static void openInitialPerkScreen() {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new PerkScreen())
        );
    }

    /**
     * サーバーから受け取ったパーク候補で選択画面を開く（推奨ルート）。
     * SEC-1対策: パーク候補はサーバー側で生成・検証済み。
     */
    public static void openPerkScreenWithChoices(List<PerkDefinition> choices, boolean isBoss) {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new PerkScreen(choices, isBoss))
        );
    }
}
