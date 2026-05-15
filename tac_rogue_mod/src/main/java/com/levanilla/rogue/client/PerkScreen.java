package com.levanilla.rogue.client;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PerkGenerator;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.networking.PerkActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.Minecraft;
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
    private int layoutCardWidth = 168;
    private int layoutCardHeight = 210;
    private int layoutGap = 8;

    public PerkScreen(boolean isBoss) {
        super(Component.translatable("gui.tac_rogue.perk_screen.title.select"));
        this.isBoss = isBoss;

        // 既存のパークタグを収集
        Set<String> existing = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer localPlayer = mc.player;
        if (localPlayer != null) {
            for (String tag : localPlayer.getTags()) {
                if (tag.startsWith("perk:")) {
                    existing.add(PerkDefinition.fromTag(tag).toTag());
                }
            }
        }

        // パーク候補を生成
        int floor = RunManager.getCurrentFloor();
        int overclockedCount = 0;
        if (localPlayer != null) {
            for (String tag : localPlayer.getTags()) {
                if (tag.contains(":OVERCLOCKED:")) {
                    overclockedCount++;
                }
            }
        }
        this.choices = PerkGenerator.generateChoices(floor, isBoss, existing, overclockedCount);
    }

    /** 初期パーク選択用のコンストラクタ */
    public PerkScreen() {
        super(Component.translatable("gui.tac_rogue.perk_screen.title.initial"));
        this.isBoss = false;
        this.choices = PerkGenerator.generateInitialChoices();
    }

    /**
     * サーバーから受け取ったパーク候補で画面を構築するコンストラクタ（推奨ルート）。
     * SEC-1対策: 候補はサーバー側で生成・セッション登録済み。
     */
    public PerkScreen(List<PerkDefinition> serverChoices, boolean isBoss) {
        super(Component.translatable(isBoss ? "gui.tac_rogue.perk_screen.title.boss" : "gui.tac_rogue.perk_screen.title.mission"));
        this.isBoss = isBoss;
        this.choices = serverChoices;
    }

    @Override
    protected void init() {
        int availableW = Math.max(300, this.width - 36);
        int cardWidth = Math.min(178, Math.max(132, (availableW - layoutGap * 2) / 3));
        int cardHeight = Math.min(240, Math.max(200, this.height - 92));
        layoutCardWidth = cardWidth;
        layoutCardHeight = cardHeight;
        int totalWidth = cardWidth * 3 + layoutGap * 2;
        int startX = (this.width - totalWidth) / 2;
        int startY = Math.max(42, (this.height - cardHeight) / 2 - 4);

        for (int i = 0; i < choices.size() && i < 3; i++) {
            PerkDefinition perk = choices.get(i);
            int x = startX + i * (cardWidth + layoutGap);
            int y = startY;

            this.addRenderableWidget(new PerkCardButton(x, y, cardWidth, cardHeight, perk, b -> {
                // サーバーに選択を送信
                TacRogueNetworking.CHANNEL.sendToServer(new PerkActionMessage(perk.toTag()));
                this.onClose();
            }));
        }

        // REROLL ボタン
        int btnW = 100;
        int btnH = 20;
        int bx = this.width / 2 - btnW / 2;
        int by = Math.min(this.height - 24, startY + cardHeight + 12);
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
            Component.translatable("gui.tac_rogue.perk_screen.reroll", com.levanilla.rogue.core.GameConstants.PERK_REROLL_COST),
            b -> {
                TacRogueNetworking.CHANNEL.sendToServer(new com.levanilla.rogue.networking.RogueActionMessage(com.levanilla.rogue.networking.RogueActionMessage.ActionType.REROLL_PERK));
            }
        ).bounds(bx, by, btnW, btnH).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // 背景パネル
        int panelW = Math.min(this.width - 20, layoutCardWidth * 3 + layoutGap * 2 + 36);
        int panelH = Math.min(this.height - 18, layoutCardHeight + 72);
        int px = (this.width - panelW) / 2;
        int py = Math.max(8, (this.height - panelH) / 2 - 10);
        graphics.fill(px - 10, py - 30, px + panelW + 10, py + panelH + 30, 0xBB001122);
        graphics.renderOutline(px - 10, py - 30, panelW + 20, panelH + 60, 0xAA00AAFF);

        // タイトル
        Component title = Component.translatable(isBoss ? "gui.tac_rogue.perk_screen.title.boss" : "gui.tac_rogue.perk_screen.title.mission");
        graphics.drawCenteredString(this.font, title, this.width / 2, py - 20, 0xFFFFFFFF);

        Component subtitle = Component.translatable("gui.tac_rogue.perk_screen.subtitle");
        graphics.drawCenteredString(this.font, subtitle, this.width / 2, py - 8, 0xFF888888);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===== パークカードボタン =====
    private static class PerkCardButton extends net.minecraft.client.gui.components.Button {
        private final PerkDefinition perk;

        public PerkCardButton(int x, int y, int w, int h, PerkDefinition perk, OnPress press) {
            super(x, y, w, h, net.minecraft.network.chat.Component.empty(), press, DEFAULT_NARRATION);
            this.perk = perk;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int rarityColor = perk.getRarityColor();
            int bgColor = this.isHoveredOrFocused() ? 0xCC002244 : 0xAA001122;

            // カードの背景
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            // レア度に応じた枠線
            graphics.renderOutline(this.getX(), this.getY(), this.width, this.height, rarityColor | 0xFF000000);
            // 上部のアクセント
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 2, rarityColor | 0xFF000000);

            Minecraft mc = Minecraft.getInstance();
            int textX = this.getX() + 5;
            int textY = this.getY() + 8;

            // パーク名
            graphics.drawString(mc.font, perk.getDisplayName(), textX, textY, rarityColor | 0xFF000000, false);
            textY += 14;

            // 効果説明（tradeoffを除いた純粋な効果文のみ）
            net.minecraft.network.chat.Component pureDesc = perk.getDescriptionComponent();
            java.util.List<net.minecraft.util.FormattedCharSequence> wrappedDesc =
                mc.font.split(pureDesc, this.width - 10);
            int descLines = 0;
            int maxDescLines = perk.modifier == PerkDefinition.Modifier.NONE ? 7 : 4;
            for (net.minecraft.util.FormattedCharSequence line : wrappedDesc) {
                if (descLines >= maxDescLines || textY + 10 >= this.getY() + this.height - 78) break;
                graphics.drawString(mc.font, line, textX, textY, 0xFFCCCCCC, false);
                textY += 10;
                descLines++;
            }
            textY += 6;

            // 修飾子名（独立した行）
            if (perk.modifier != PerkDefinition.Modifier.NONE) {
                graphics.drawString(mc.font, "§7[" + perk.modifier.prefix + "]", textX, textY, perk.modifier.color, false);
                textY += 12;
                graphics.drawString(mc.font, Component.translatable("gui.tac_rogue.perk_screen.power", String.format(java.util.Locale.ROOT, "%.1f", perk.modifier.multiplier)),
                    textX, textY, perk.modifier.color, false);
                textY += 12;
                if (!perk.modifier.tradeoff.isEmpty()) {
                    net.minecraft.network.chat.Component tradeoffComp = perk.getModifierDescriptionComponent();
                    java.util.List<net.minecraft.util.FormattedCharSequence> wrapped =
                        splitJapaneseFriendly(mc, tradeoffComp, this.width - 34);
                    int lines = 0;
                    int maxLines = Math.max(1, (this.getY() + this.height - 22 - textY) / 10);
                    for (net.minecraft.util.FormattedCharSequence line : wrapped) {
                        if (textY + 10 < this.getY() + this.height - 18) {
                            graphics.drawString(mc.font, line, textX, textY, perk.modifier.color, false);
                            textY += 10;
                            lines++;
                            if (lines >= maxLines) break;
                        }
                    }
                    if (this.isHoveredOrFocused() && lines < wrapped.size()) {
                        graphics.renderComponentTooltip(mc.font,
                            java.util.List.of(perk.getDescriptionComponent(), tradeoffComp), mouseX, mouseY);
                    }
                }
            }

            // ホバー時の選択指示
            if (this.isHoveredOrFocused()) {
                graphics.drawCenteredString(mc.font, Component.translatable("gui.tac_rogue.perk_screen.click_select"),
                    this.getX() + this.width / 2, this.getY() + this.height - 14, 0xFF00FF00);
            }
        }

        private java.util.List<net.minecraft.util.FormattedCharSequence> splitJapaneseFriendly(
                Minecraft mc, Component component, int width) {
            java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
            String text = component.getString()
                .replace("。", "。\n")
                .replace(". ", ".\n")
                .replace("; ", ";\n");
            for (String paragraph : text.split("\\n")) {
                if (paragraph.isBlank()) continue;
                lines.addAll(mc.font.split(Component.literal(paragraph), width));
            }
            return lines;
        }
    }
}
