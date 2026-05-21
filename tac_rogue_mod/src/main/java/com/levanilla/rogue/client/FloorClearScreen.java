package com.levanilla.rogue.client;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.networking.RogueActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * フロアクリア後に表示されるパーク選択画面。
 * 3つのランダムパークから1つを選択 → その後 NEXT FLOOR / RETURN LOBBY を選択。
 */
public class FloorClearScreen extends Screen {

    public enum Mode {
        FLOOR_CLEAR,
        INITIAL,
        NORMAL,
        BOSS
    }

    private boolean perkSelected = false;
    private final List<PerkDefinition> choices;
    private PerkDefinition selectedPerk = null;
    private final boolean isFarming;
    private final Mode mode;
    private int perkScroll = 0;
    private boolean perkListLayout = false;
    private int perkCardW = 130;
    private int perkCardH = 84;
    private int perkListLeft = 0;
    private int perkListVisibleW = 0;
    private int perkListTop = 0;
    private int perkListVisibleH = 0;

    /**
     * サーバーから受け取ったパーク候補で画面を構築する。
     * SEC-1対策: 候補はサーバー側で生成・セッション登録済み。
     */
    public FloorClearScreen(boolean isFarming, List<PerkDefinition> serverChoices) {
        super(Component.translatable("gui.tac_rogue.floor_clear.title"));
        this.mode = Mode.FLOOR_CLEAR;
        this.isFarming = isFarming;
        this.choices = serverChoices != null ? serverChoices : new ArrayList<>();
        if (isFarming || choices.isEmpty()) {
            this.perkSelected = true;
        }
    }

    public FloorClearScreen(Mode mode, List<PerkDefinition> serverChoices) {
        super(Component.translatable(titleKey(mode)));
        this.mode = mode == null ? Mode.NORMAL : mode;
        this.isFarming = false;
        this.choices = serverChoices != null ? serverChoices : new ArrayList<>();
        this.perkSelected = choices.isEmpty();
    }

    // パーク候補の生成はサーバー側 (OpenFloorClearScreenMessage.sendFloorClear) で行う

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;

        if (!perkSelected) {
            perkListLayout = choices.size() > 3 || this.width < 620 || this.height < 330;
            perkCardW = perkListLayout ? Math.min(170, Math.max(126, (this.width - 86) / 2)) : 130;
            perkCardH = perkListLayout ? Math.min(132, Math.max(104, this.height - 176)) : 124;
            perkListLeft = perkListLayout ? Math.max(28, cx - Math.min(this.width - 56, perkCardW * 2 + 10) / 2) : 0;
            perkListVisibleW = perkListLayout ? Math.min(this.width - perkListLeft * 2, perkCardW * 2 + 10) : 0;
            perkListTop = perkListLayout ? Math.max(66, cy - 42) : cy - 38;
            perkListVisibleH = perkListLayout ? perkCardH : perkCardH;
            perkScroll = Math.max(0, Math.min(perkScroll, maxPerkScroll()));
            int startX = perkListLayout ? perkListLeft : cx - (perkCardW * choices.size() + 10 * Math.max(0, choices.size() - 1)) / 2;

            for (int i = 0; i < choices.size(); i++) {
                PerkDefinition perk = choices.get(i);
                int cardX = startX + i * (perkCardW + 10) - (perkListLayout ? perkScroll : 0);
                int cardY = perkListLayout ? perkListTop : cy - 38;
                
                PerkCardButton button = new PerkCardButton(
                    cardX, cardY, perkCardW, perkCardH, perk,
                    b -> {
                        selectedPerk = perk;
                        perkSelected = true;
                        // パークをプレイヤーに適用 (サーバーへ送信)
                        TacRogueNetworking.CHANNEL.sendToServer(
                            new RogueActionMessage(RogueActionMessage.ActionType.APPLY_PERK, perk.toTag()));
                        if (showPostSelectionActions()) {
                            // UIを再構築して NEXT/LOBBY ボタンを表示
                            this.init(this.minecraft, this.width, this.height);
                        } else {
                            this.onClose();
                        }
                    }
                );
                button.visible = !perkListLayout || (cardX + perkCardW > perkListLeft && cardX < perkListLeft + perkListVisibleW);
                this.addRenderableWidget(button);
            }

            // --- REROLL BUTTON ---
            this.addRenderableWidget(new TacticalButton(
                cx - 70, this.height - 28, 140, 20,
                Component.translatable("gui.tac_rogue.perk_screen.reroll", com.levanilla.rogue.core.GameConstants.PERK_REROLL_COST),
                b -> {
                    TacRogueNetworking.CHANNEL.sendToServer(
                        new RogueActionMessage(RogueActionMessage.ActionType.REROLL_PERK, rerollContext()));
                }
            ));
        } else if (showPostSelectionActions()) {
            // パーク選択済み: NEXT FLOOR / RETURN LOBBY を表示
            this.addRenderableWidget(new TacticalButton(
                cx - 104, cy + 20, 208, 22,
                Component.translatable("gui.tac_rogue.floor_clear.next"),
                b -> {
                    TacRogueNetworking.CHANNEL.sendToServer(
                        new RogueActionMessage(RogueActionMessage.ActionType.START_NEXT_FLOOR));
                    this.onClose();
                }
            ));

            this.addRenderableWidget(new TacticalButton(
                cx - 104, cy + 52, 208, 22,
                Component.translatable("gui.tac_rogue.floor_clear.return"),
                b -> {
                    TacRogueNetworking.CHANNEL.sendToServer(
                        new RogueActionMessage(RogueActionMessage.ActionType.RETURN_TO_LOBBY));
                    this.onClose();
                }
            ));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;

        int panelW;
        int panelH;
        if (!perkSelected) {
            panelW = perkListLayout
                ? Math.min(this.width - 20, perkListVisibleW + 36)
                : Math.min(this.width - 20,
                    perkCardW * Math.max(1, choices.size()) + 10 * Math.max(0, choices.size() - 1) + 36);
            panelH = Math.min(this.height - 18, perkCardH + 82);
        } else {
            panelW = Math.min(this.width - 20, 440);
            panelH = 190;
        }
        int panelX = cx - panelW / 2;
        int panelY = !perkSelected
            ? Math.max(10, (perkListLayout ? perkListTop : cy - 38) - 48)
            : cy - 80;

        // 背景パネル
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC001122);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xFF00AAFF);

        // タイトル
        graphics.drawCenteredString(this.font,
            Component.translatable(titleKey(mode)),
            cx, panelY + 10, 0xFF00FF00);

        if (!perkSelected) {
            // パーク選択フェーズ
            graphics.drawCenteredString(this.font,
                Component.translatable(subtitleKey(mode)),
                cx, panelY + 25, 0xFFFFFF00);
            drawRerollBudget(graphics, cx, this.height - 39);
            if (perkListLayout && maxPerkScroll() > 0) {
                int barX = perkListLeft;
                int barY = perkListTop + perkListVisibleH + 8;
                int barW = perkListVisibleW;
                int totalW = choices.size() * perkCardW + Math.max(0, choices.size() - 1) * 10;
                int thumbW = Math.max(24, barW * barW / Math.max(barW, totalW));
                int thumbX = barX + (barW - thumbW) * perkScroll / maxPerkScroll();
                graphics.fill(barX, barY, barX + barW, barY + 3, 0x552A5262);
                graphics.fill(thumbX, barY - 1, thumbX + thumbW, barY + 4, 0xAA55FFE0);
            }
        } else {
            // パーク選択済み または 周回プレイ
            if (!showPostSelectionActions()) {
                graphics.drawCenteredString(this.font,
                    Component.translatable("gui.tac_rogue.floor_clear.reward_secured"),
                    cx, cy - 45, 0xFF00FF00);
            } else if (isFarming) {
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
        return !showPostSelectionActions();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!perkSelected && perkListLayout) {
            int next = Math.max(0, Math.min(maxPerkScroll(), perkScroll - (int)Math.signum(delta) * 48));
            if (next != perkScroll) {
                perkScroll = next;
                init(this.minecraft, this.width, this.height);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int maxPerkScroll() {
        if (choices.isEmpty()) return 0;
        int total = choices.size() * perkCardW + Math.max(0, choices.size() - 1) * 10;
        return Math.max(0, total - perkListVisibleW);
    }

    private void drawRerollBudget(GuiGraphics graphics, int cx, int y) {
        int gold = RunManager.getClientGold();
        int rerollCost = com.levanilla.rogue.core.GameConstants.PERK_REROLL_COST;
        int rerolls = rerollCost <= 0 ? 0 : gold / rerollCost;
        Component budget = Component.translatable("gui.tac_rogue.perk_screen.reroll_budget", gold, rerolls);
        graphics.drawCenteredString(this.font, budget, cx, y, 0xFFFFD45C);
    }

    private boolean showPostSelectionActions() {
        return mode == Mode.FLOOR_CLEAR;
    }

    private String rerollContext() {
        return switch (mode) {
            case FLOOR_CLEAR -> "floor_clear";
            case INITIAL -> "initial";
            case BOSS -> "boss";
            case NORMAL -> "normal";
        };
    }

    private static String titleKey(Mode mode) {
        return switch (mode == null ? Mode.NORMAL : mode) {
            case FLOOR_CLEAR -> "gui.tac_rogue.floor_clear.secured";
            case INITIAL -> "gui.tac_rogue.perk_screen.title.initial";
            case BOSS -> "gui.tac_rogue.perk_screen.title.boss";
            case NORMAL -> "gui.tac_rogue.perk_screen.title.mission";
        };
    }

    private static String subtitleKey(Mode mode) {
        return switch (mode == null ? Mode.NORMAL : mode) {
            case INITIAL -> "gui.tac_rogue.perk_screen.subtitle.initial";
            case FLOOR_CLEAR, NORMAL, BOSS -> "gui.tac_rogue.floor_clear.select_perk";
        };
    }

}
