package com.levanilla.rogue.client;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PerkGenerator;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.ClientRunState;
import com.levanilla.rogue.networking.PerkActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * パーク選択画面。3 つの候補をカード形式で表示し、選択するとサーバーに通知する。
 * サイバーパンク風 UI で統一。
 */
public class PerkScreen extends Screen {

    private final List<PerkDefinition> choices;
    private final boolean isBoss;
    private final boolean initialSelection;
    private int layoutCardWidth = 168;
    private int layoutCardHeight = 210;
    private int layoutGap = 8;
    private int scrollOffset = 0;
    private boolean listLayout = false;
    private int listLeft = 24;
    private int listVisibleWidth = 260;
    private int listTop = 52;
    private int listVisibleHeight = 180;

    public PerkScreen(boolean isBoss) {
        super(Component.translatable("gui.tac_rogue.perk_screen.title.select"));
        this.isBoss = isBoss;
        this.initialSelection = false;

        // 既存のパークタグを収集
        Set<String> existing = new HashSet<>();
        for (String tag : ClientRunState.getPerkTags()) {
            if (tag.startsWith("perk:")) {
                existing.add(PerkDefinition.fromTag(tag).toTag());
            }
        }

        // パーク候補を生成
        int floor = RunManager.getCurrentFloor();
        int overclockedCount = 0;
        for (String tag : ClientRunState.getPerkTags()) {
            if (tag.contains(":OVERCLOCKED:")) {
                overclockedCount++;
            }
        }
        this.choices = PerkGenerator.generateChoices(floor, isBoss, existing, overclockedCount);
    }

    /** 初期パーク選択用のコンストラクタ */
    public PerkScreen() {
        super(Component.translatable("gui.tac_rogue.perk_screen.title.initial"));
        this.isBoss = false;
        this.initialSelection = true;
        this.choices = PerkGenerator.generateInitialChoices();
    }

    /**
     * サーバーから受け取ったパーク候補で画面を構築するコンストラクタ（推奨ルート）。
     * SEC-1対策: 候補はサーバー側で生成・セッション登録済み。
     */
    public PerkScreen(List<PerkDefinition> serverChoices, boolean isBoss) {
        super(Component.translatable(isBoss ? "gui.tac_rogue.perk_screen.title.boss" : "gui.tac_rogue.perk_screen.title.mission"));
        this.isBoss = isBoss;
        this.initialSelection = false;
        this.choices = serverChoices;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int availableW = Math.max(300, this.width - 36);
        listLayout = choices.size() > 3 || this.width < 640 || this.height < 330;
        if (listLayout) {
            layoutCardWidth = Math.min(220, Math.max(152, (this.width - 84) / 2));
            layoutCardHeight = Math.min(214, Math.max(156, this.height - 124));
            listLeft = Math.max(18, (this.width - Math.min(this.width - 36, 2 * layoutCardWidth + layoutGap)) / 2);
            listVisibleWidth = Math.min(this.width - listLeft * 2, 2 * layoutCardWidth + layoutGap);
            listTop = Math.max(54, (this.height - layoutCardHeight) / 2 + 2);
            listVisibleHeight = layoutCardHeight;
            int maxScroll = maxScrollOffset();
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            for (int i = 0; i < choices.size(); i++) {
                PerkDefinition perk = choices.get(i);
                int x = listLeft + i * (layoutCardWidth + layoutGap) - scrollOffset;
                int y = listTop;
                com.levanilla.rogue.client.PerkCardButton card = new com.levanilla.rogue.client.PerkCardButton(x, y, layoutCardWidth, layoutCardHeight, perk, b -> {
                    TacRogueNetworking.CHANNEL.sendToServer(new PerkActionMessage(perk.toTag()));
                    this.onClose();
                });
                card.visible = x + layoutCardWidth > listLeft && x < listLeft + listVisibleWidth;
                this.addRenderableWidget(card);
            }
        } else {
            int cardWidth = Math.min(178, Math.max(132, (availableW - layoutGap * (choices.size() - 1)) / Math.max(1, choices.size())));
            int cardHeight = Math.min(240, Math.max(200, this.height - 92));
            layoutCardWidth = cardWidth;
            layoutCardHeight = cardHeight;
            int totalWidth = cardWidth * choices.size() + layoutGap * Math.max(0, choices.size() - 1);
            int startX = (this.width - totalWidth) / 2;
            int startY = Math.max(42, (this.height - cardHeight) / 2 - 4);

            for (int i = 0; i < choices.size(); i++) {
                PerkDefinition perk = choices.get(i);
                int x = startX + i * (cardWidth + layoutGap);
                int y = startY;

                this.addRenderableWidget(new com.levanilla.rogue.client.PerkCardButton(x, y, cardWidth, cardHeight, perk, b -> {
                    TacRogueNetworking.CHANNEL.sendToServer(new PerkActionMessage(perk.toTag()));
                    this.onClose();
                }));
            }
        }

        // REROLL ボタン
        int btnW = 100;
        int btnH = 20;
        int bx = this.width / 2 - btnW / 2;
        int by = this.height - 26;
        this.addRenderableWidget(new TacticalButton(
            bx, by, btnW, btnH,
            Component.translatable("gui.tac_rogue.perk_screen.reroll", com.levanilla.rogue.core.GameConstants.PERK_REROLL_COST),
            b -> {
                TacRogueNetworking.CHANNEL.sendToServer(new com.levanilla.rogue.networking.RogueActionMessage(com.levanilla.rogue.networking.RogueActionMessage.ActionType.REROLL_PERK));
            }
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // 背景パネル
        int contentCards = listLayout ? 1 : Math.max(1, choices.size());
        int panelW = listLayout
            ? Math.min(this.width - 20, listVisibleWidth + 36)
            : Math.min(this.width - 20, layoutCardWidth * contentCards + layoutGap * Math.max(0, contentCards - 1) + 36);
        int panelH = listLayout ? Math.min(this.height - 16, listVisibleHeight + 92) : Math.min(this.height - 18, layoutCardHeight + 72);
        int px = (this.width - panelW) / 2;
        int py = Math.max(8, (this.height - panelH) / 2 - 10);
        graphics.fill(px - 10, py - 30, px + panelW + 10, py + panelH + 30, 0xBB001122);
        graphics.renderOutline(px - 10, py - 30, panelW + 20, panelH + 60, 0xAA00AAFF);

        // タイトル
        Component title = initialSelection
            ? Component.translatable("gui.tac_rogue.perk_screen.title.initial")
            : Component.translatable(isBoss ? "gui.tac_rogue.perk_screen.title.boss" : "gui.tac_rogue.perk_screen.title.mission");
        graphics.drawCenteredString(this.font, title, this.width / 2, py - 20, 0xFFFFFFFF);

        Component subtitle = Component.translatable(initialSelection
            ? "gui.tac_rogue.perk_screen.subtitle.initial"
            : "gui.tac_rogue.perk_screen.subtitle");
        graphics.drawCenteredString(this.font, subtitle, this.width / 2, py - 8, 0xFF888888);

        int gold = RunManager.getClientGold();
        Component goldText = Component.translatable("gui.tac_rogue.perk_screen.gold", gold);
        graphics.drawString(this.font, goldText, px + panelW - this.font.width(goldText), py - 22, 0xFFFFD45C, false);

        if (listLayout) {
            Component countText = Component.translatable("gui.tac_rogue.perk_screen.candidate_count", choices.size());
            graphics.drawString(this.font, countText, px + 8, py - 22, 0xFF8DEFFF, false);
            if (maxScrollOffset() > 0) {
                Component scrollText = Component.translatable("gui.tac_rogue.perk_screen.scroll_hint");
                graphics.drawCenteredString(this.font, scrollText, this.width / 2, listTop + listVisibleHeight + 4, 0xFF8A9AA8);
                int barX = listLeft;
                int barY = listTop + listVisibleHeight + 16;
                int barW = listVisibleWidth;
                int totalW = choices.size() * layoutCardWidth + Math.max(0, choices.size() - 1) * layoutGap;
                int thumbW = Math.max(24, barW * barW / Math.max(barW, totalW));
                int thumbX = barX + (maxScrollOffset() == 0 ? 0 : (barW - thumbW) * scrollOffset / maxScrollOffset());
                graphics.fill(barX, barY, barX + barW, barY + 3, 0x552A5262);
                graphics.fill(thumbX, barY - 1, thumbX + thumbW, barY + 4, 0xAA55FFE0);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!listLayout) return super.mouseScrolled(mouseX, mouseY, delta);
        int next = Math.max(0, Math.min(maxScrollOffset(), scrollOffset - (int)Math.signum(delta) * 48));
        if (next != scrollOffset) {
            scrollOffset = next;
            init();
        }
        return true;
    }

    private int maxScrollOffset() {
        if (choices.isEmpty()) return 0;
        int total = choices.size() * layoutCardWidth + Math.max(0, choices.size() - 1) * layoutGap;
        return Math.max(0, total - listVisibleWidth);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
