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

    public PerkScreen(boolean isBoss) {
        super(Component.literal("SELECT ADAPTATION"));
        this.isBoss = isBoss;

        // 既存のパークタグを収集
        Set<String> existing = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer localPlayer = mc.player;
        if (localPlayer != null) {
            for (String tag : localPlayer.getTags()) {
                if (tag.startsWith("perk:")) existing.add(tag);
            }
        }

        // パーク候補を生成
        int floor = RunManager.getCurrentFloor();
        this.choices = PerkGenerator.generateChoices(floor, isBoss, existing);
    }

    /** 初期パーク選択用のコンストラクタ */
    public PerkScreen() {
        super(Component.literal("INITIAL LOADOUT ADAPTATION"));
        this.isBoss = false;
        this.choices = PerkGenerator.generateInitialChoices();
    }

    @Override
    protected void init() {
        int cardWidth = 140;
        int cardHeight = 180;  // 増加: 説明文+修飾子+トレードオフテキスト分の余裕を確保
        int gap = 8;
        int totalWidth = cardWidth * 3 + gap * 2;
        int startX = (this.width - totalWidth) / 2;
        int startY = (this.height - cardHeight) / 2;

        for (int i = 0; i < choices.size() && i < 3; i++) {
            PerkDefinition perk = choices.get(i);
            int x = startX + i * (cardWidth + gap);
            int y = startY;

            this.addRenderableWidget(new PerkCardButton(x, y, cardWidth, cardHeight, perk, b -> {
                // サーバーに選択を送信
                TacRogueNetworking.CHANNEL.sendToServer(new PerkActionMessage(perk.toTag()));
                this.onClose();
            }));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // 背景パネル
        int panelW = 440;
        int panelH = 220;
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2 - 30;
        graphics.fill(px - 10, py - 30, px + panelW + 10, py + panelH + 30, 0xBB001122);
        graphics.renderOutline(px - 10, py - 30, panelW + 20, panelH + 60, 0xAA00AAFF);

        // タイトル
        String title = isBoss ? "§e§lBOSS REWARD — SELECT ELITE ADAPTATION" : "§bMISSION UPDATE — SELECT ADAPTATION";
        graphics.drawCenteredString(this.font, title, this.width / 2, py - 20, 0xFFFFFFFF);

        String subtitle = "§7Choose one enhancement to apply";
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
            float effect = perk.calculateEffect();
            String valueStr;
            if (perk.category == PerkDefinition.Category.REGENERATION || perk.category == PerkDefinition.Category.VAMPIRE) {
                valueStr = String.format("%.1f", effect / 10.0f);
            } else {
                valueStr = String.valueOf((int) effect);
            }
            net.minecraft.network.chat.Component pureDesc =
                net.minecraft.network.chat.Component.translatable(perk.category.descriptionKey, valueStr);
            java.util.List<net.minecraft.util.FormattedCharSequence> wrappedDesc =
                mc.font.split(pureDesc, this.width - 10);
            for (net.minecraft.util.FormattedCharSequence line : wrappedDesc) {
                graphics.drawString(mc.font, line, textX, textY, 0xFFCCCCCC, false);
                textY += 10;
            }
            textY += 6;

            // 修飾子名（独立した行）
            if (perk.modifier != PerkDefinition.Modifier.NONE) {
                graphics.drawString(mc.font, "§7[" + perk.modifier.prefix + "]", textX, textY, perk.modifier.color, false);
                textY += 12;
                // 警告テキスト（tradeoff）を独立した赤行で表示
                if (!perk.modifier.tradeoff.isEmpty()) {
                    net.minecraft.network.chat.Component tradeoffComp =
                        net.minecraft.network.chat.Component.literal("§c⚠ ").append(
                            net.minecraft.network.chat.Component.translatable(perk.modifier.tradeoff));
                    java.util.List<net.minecraft.util.FormattedCharSequence> wrapped =
                        mc.font.split(tradeoffComp, this.width - 10);
                    for (net.minecraft.util.FormattedCharSequence line : wrapped) {
                        if (textY + 10 < this.getY() + this.height - 18) {
                            graphics.drawString(mc.font, line, textX, textY, 0xFFFF4444, false);
                            textY += 10;
                        }
                    }
                }
            }

            // ホバー時の選択指示
            if (this.isHoveredOrFocused()) {
                graphics.drawCenteredString(mc.font, "§a▶ CLICK TO SELECT",
                    this.getX() + this.width / 2, this.getY() + this.height - 14, 0xFF00FF00);
            }
        }
    }
}
