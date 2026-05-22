package com.levanilla.rogue.client;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PerkGenerator;
import com.levanilla.rogue.core.RunManager;
import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * パーク画面の表示を管理するクラス。
 * サーバーからのパケットに応じてパーク選択画面を開く。
 */
public class PerkManager {

    /** 通常フロアクリア時のパーク選択画面を開く（レガシー: クライアント側生成） */
    public static void openPerkScreen() {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new FloorClearScreen(FloorClearScreen.Mode.NORMAL,
                generateClientChoices(false)))
        );
    }

    /** ボスフロアクリア時の高品質パーク選択画面を開く（レガシー: クライアント側生成） */
    public static void openBossPerkScreen() {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new FloorClearScreen(FloorClearScreen.Mode.BOSS,
                generateClientChoices(true)))
        );
    }

    /** 初期パーク選択画面を開く（レガシー: クライアント側生成） */
    public static void openInitialPerkScreen() {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new FloorClearScreen(FloorClearScreen.Mode.INITIAL,
                PerkGenerator.generateInitialChoices()))
        );
    }

    /**
     * サーバーから受け取ったパーク候補で選択画面を開く（推奨ルート）。
     * SEC-1対策: パーク候補はサーバー側で生成・検証済み。
     */
    public static void openPerkScreenWithChoices(List<PerkDefinition> choices, boolean isBoss) {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new FloorClearScreen(
                isBoss ? FloorClearScreen.Mode.BOSS : FloorClearScreen.Mode.NORMAL, choices))
        );
    }

    public static void openPerkScreenWithChoices(List<PerkDefinition> choices, OpenPerkChoiceMode mode) {
        Minecraft.getInstance().tell(() ->
            Minecraft.getInstance().setScreen(new FloorClearScreen(mode.floorClearMode(), choices))
        );
    }

    public enum OpenPerkChoiceMode {
        INITIAL(FloorClearScreen.Mode.INITIAL),
        NORMAL(FloorClearScreen.Mode.NORMAL),
        BOSS(FloorClearScreen.Mode.BOSS);

        private final FloorClearScreen.Mode floorClearMode;

        OpenPerkChoiceMode(FloorClearScreen.Mode floorClearMode) {
            this.floorClearMode = floorClearMode;
        }

        public FloorClearScreen.Mode floorClearMode() {
            return floorClearMode;
        }
    }

    private static List<PerkDefinition> generateClientChoices(boolean isBoss) {
        Set<String> existing = new HashSet<>();
        int overclockedCount = 0;
        net.minecraft.client.player.LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            for (String tag : localPlayer.getTags()) {
                if (tag.startsWith("perk:")) {
                    existing.add(PerkDefinition.fromTag(tag).toTag());
                    if (tag.contains(":OVERCLOCKED:")) overclockedCount++;
                }
            }
        }
        return PerkGenerator.generateChoices(RunManager.getCurrentFloor(), isBoss, existing, overclockedCount);
    }
}
