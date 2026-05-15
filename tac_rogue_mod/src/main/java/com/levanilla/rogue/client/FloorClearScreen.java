package com.levanilla.rogue.client;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.networking.RogueActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * フロアクリア後に表示されるパーク選択画面。
 * 3つのランダムパークから1つを選択 → その後 NEXT FLOOR / RETURN LOBBY を選択。
 */
public class FloorClearScreen extends Screen {

    private boolean perkSelected = false;
    private final List<PerkDefinition> choices;
    private PerkDefinition selectedPerk = null;
    private final boolean isFarming;

    /**
     * サーバーから受け取ったパーク候補で画面を構築する。
     * SEC-1対策: 候補はサーバー側で生成・セッション登録済み。
     */
    public FloorClearScreen(boolean isFarming, List<PerkDefinition> serverChoices) {
        super(Component.translatable("gui.tac_rogue.floor_clear.title"));
        this.isFarming = isFarming;
        this.choices = serverChoices != null ? serverChoices : new ArrayList<>();
        if (isFarming || choices.isEmpty()) {
            this.perkSelected = true;
        }
    }

    // パーク候補の生成はサーバー側 (OpenFloorClearScreenMessage.sendFloorClear) で行う

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;

        if (!perkSelected) {
            // パーク選択フェーズ: 3つのパークカードを横並びで表示
            int cardW = 130;
            int startX = cx - (cardW * 3 + 10 * 2) / 2;

            for (int i = 0; i < choices.size(); i++) {
                PerkDefinition perk = choices.get(i);
                int cardX = startX + i * (cardW + 10);
                
                this.addRenderableWidget(Button.builder(
                    Component.literal("§l" + perk.getDisplayName()),
                    b -> {
                        selectedPerk = perk;
                        perkSelected = true;
                        // パークをプレイヤーに適用 (サーバーへ送信)
                        TacRogueNetworking.CHANNEL.sendToServer(
                            new RogueActionMessage(RogueActionMessage.ActionType.APPLY_PERK, perk.toTag()));
                        // UIを再構築して NEXT/LOBBY ボタンを表示
                        this.init(this.minecraft, this.width, this.height);
                    }
                ).bounds(cardX, cy + 50, cardW, 20).build());
            }

            // --- REROLL BUTTON ---
            this.addRenderableWidget(Button.builder(
                Component.translatable("gui.tac_rogue.perk_screen.reroll", com.levanilla.rogue.core.GameConstants.PERK_REROLL_COST),
                b -> {
                    TacRogueNetworking.CHANNEL.sendToServer(
                        new RogueActionMessage(RogueActionMessage.ActionType.REROLL_PERK, "floor_clear"));
                }
            ).bounds(cx - 50, cy + 80, 100, 20).build());
        } else {
            // パーク選択済み: NEXT FLOOR / RETURN LOBBY を表示
            this.addRenderableWidget(Button.builder(
                Component.translatable("gui.tac_rogue.floor_clear.next"),
                b -> {
                    TacRogueNetworking.CHANNEL.sendToServer(
                        new RogueActionMessage(RogueActionMessage.ActionType.START_NEXT_FLOOR));
                    this.onClose();
                }
            ).bounds(cx - 100, cy + 20, 200, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.translatable("gui.tac_rogue.floor_clear.return"),
                b -> {
                    TacRogueNetworking.CHANNEL.sendToServer(
                        new RogueActionMessage(RogueActionMessage.ActionType.RETURN_TO_LOBBY));
                    this.onClose();
                }
            ).bounds(cx - 100, cy + 50, 200, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;

        // 背景パネル
        graphics.fill(cx - 220, cy - 80, cx + 220, cy + 110, 0xCC001122);
        graphics.renderOutline(cx - 220, cy - 80, 440, 190, 0xFF00AAFF);

        // タイトル
        graphics.drawCenteredString(this.font,
            Component.translatable("gui.tac_rogue.floor_clear.secured"),
            cx, cy - 70, 0xFF00FF00);

        if (!perkSelected) {
            // パーク選択フェーズ
            graphics.drawCenteredString(this.font,
                Component.translatable("gui.tac_rogue.floor_clear.select_perk"),
                cx, cy - 55, 0xFFFFFF00);

            int cardW = 130;
            int startX = cx - (cardW * 3 + 10 * 2) / 2;

            for (int i = 0; i < choices.size(); i++) {
                PerkDefinition perk = choices.get(i);
                int cardX = startX + i * (cardW + 10);
                
                // カード背景
                graphics.fill(cardX - 2, cy - 38, cardX + cardW + 2, cy + 46, 0x66000000);
                graphics.renderOutline(cardX - 2, cy - 38, cardW + 4, 84, perk.getRarityColor());

                // パーク名
                graphics.drawString(this.font, "§l" + perk.modifier.prefix,
                    cardX + 3, cy - 34, perk.getRarityColor(), false);
                graphics.drawString(this.font, perk.category.displayName + " Lv." + perk.level,
                    cardX + 3, cy - 22, perk.category.color, false);

                // 効果説明 (改行対応)
                graphics.pose().pushPose();
                graphics.pose().translate(cardX + 3, cy - 8, 0);
                graphics.pose().scale(0.85f, 0.85f, 1.0f);
                net.minecraft.network.chat.Component descComp = perk.getDescriptionComponent();
                java.util.List<net.minecraft.util.FormattedCharSequence> wrappedDesc = this.font.split(descComp, (int)(cardW / 0.85f) - 6);
                int descLineY = 0;
                for (net.minecraft.util.FormattedCharSequence line : wrappedDesc) {
                    graphics.drawString(this.font, line, 0, descLineY, 0xFFCCCCCC, false);
                    descLineY += 10;
                    if (descLineY > 36) break; // allow up to 4 lines
                }
                graphics.pose().popPose();

                // トレードオフ
                if (!perk.modifier.tradeoff.isEmpty()) {
                    graphics.pose().pushPose();
                    int tradeoffY = (cy - 8) + (int)(descLineY * 0.85f) + 2;
                    graphics.pose().translate(cardX + 3, tradeoffY, 0);
                    graphics.pose().scale(0.75f, 0.75f, 1.0f);
                    java.util.List<net.minecraft.util.FormattedCharSequence> tradeoffLines = splitJapaneseFriendly(
                        Component.literal("§c! ").append(Component.translatable(perk.modifier.tradeoff)),
                        (int)(cardW / 0.75f) - 8);
                    int lineY = 0;
                    int maxY = (int)((cy + 44 - tradeoffY) / 0.75f);
                    for (int li = 0; li < tradeoffLines.size() && li < 3; li++) {
                        if (lineY + 10 > maxY) break;
                        graphics.drawString(this.font, tradeoffLines.get(li), 0, lineY, 0xFFFF4444, false);
                        lineY += 10;
                    }
                    graphics.pose().popPose();
                }
            }
        } else {
            // パーク選択済み または 周回プレイ
            if (isFarming) {
                graphics.drawCenteredString(this.font,
                    Component.translatable("gui.tac_rogue.floor_clear.reward_secured"),
                    cx, cy - 45, 0xFF00FF00);
            } else {
                graphics.drawCenteredString(this.font,
                    Component.translatable("gui.tac_rogue.floor_clear.perk_acquired", selectedPerk.getDisplayName()),
                    cx, cy - 45, selectedPerk.getRarityColor());
                graphics.drawCenteredString(this.font,
                    Component.literal("§7" + selectedPerk.getDescription()),
                    cx, cy - 30, 0xFFCCCCCC);
            }
            graphics.drawCenteredString(this.font,
                Component.translatable("gui.tac_rogue.floor_clear.choose_next"),
                cx, cy + 5, 0xFF888888);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private java.util.List<net.minecraft.util.FormattedCharSequence> splitJapaneseFriendly(Component component, int width) {
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        String text = component.getString()
            .replace("。", "。\n")
            .replace("。§", "。\n§")
            .replace(". ", ".\n")
            .replace("; ", ";\n");
        for (String paragraph : text.split("\\n")) {
            if (paragraph.isBlank()) continue;
            lines.addAll(this.font.split(Component.literal(paragraph), width));
        }
        return lines;
    }
}
