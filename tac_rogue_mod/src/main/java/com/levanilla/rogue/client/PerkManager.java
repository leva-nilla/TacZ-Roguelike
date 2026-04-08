package com.levanilla.rogue.client;

import net.minecraft.client.Minecraft;

/**
 * パーク画面の表示を管理するクラス。
 * サーバーからのパケットに応じてパーク選択画面を開く。
 */
public class PerkManager {

    /** 通常フロアクリア時のパーク選択画面を開く */
    public static void openPerkScreen() {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new PerkScreen(false))
        );
    }

    /** ボスフロアクリア時の高品質パーク選択画面を開く */
    public static void openBossPerkScreen() {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new PerkScreen(true))
        );
    }

    /** 初期パーク選択画面を開く */
    public static void openInitialPerkScreen() {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new PerkScreen())
        );
    }
}
