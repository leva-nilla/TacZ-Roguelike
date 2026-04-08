package com.levanilla.rogue.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Stash Terminal の UI — サイバーパンク風デザイン (v0.3.0)
 * AbstractContainerScreen<ChestMenu> を継承し、実際のアイテムを表示。
 */
public class StashScreen extends AbstractContainerScreen<ChestMenu> {

    public StashScreen(ChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        int rows = menu.getRowCount();
        this.imageHeight = 114 + rows * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;

        // === 外枠: ダークパネル + ネオンアクセント ===
        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xFF001122);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF002233);
        g.fill(x, y, x + w, y + h, 0xDD000A14);

        // 上部アクセントライン
        g.fill(x, y, x + w, y + 2, 0xFF00FFFF);
        // 左サイドアクセント
        g.fill(x, y, x + 2, y + h, 0xFF00AAFF);
        // 下部アクセント
        g.fill(x, y + h - 2, x + w, y + h, 0xFF00AAFF);

        // === ヘッダー ===
        g.drawCenteredString(this.font, "\u00A7b\u2588 STASH TERMINAL v2.1",
            x + w / 2, y + 6, 0xFF00FFFF);

        // === スロット背景の描画 ===
        int rows = this.menu.getRowCount();
        int slotStartY = y + 18;

        // コンテナスロット（Stash）
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + 7 + col * 18;
                int sy = slotStartY + row * 18;
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF002233);
                g.renderOutline(sx, sy, 18, 18, 0xFF004455);

                // アニメーション: ランダムにデータブリンク
                if ((sx + sy + (int)(System.currentTimeMillis() / 700)) % 11 == 0) {
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x1100FFFF);
                }
            }
        }

        // プレイヤーインベントリ区切り線
        int invY = slotStartY + rows * 18 + 10;
        g.fill(x + 7, invY - 1, x + w - 7, invY, 0xFF004455);

        // プレイヤーインベントリ背景
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + 7 + col * 18;
                int sy = invY + 3 + row * 18;
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF111122);
                g.renderOutline(sx, sy, 18, 18, 0xFF333344);
            }
        }

        // ホットバー背景
        int hotbarY = invY + 3 + 3 * 18 + 4;
        for (int col = 0; col < 9; col++) {
            int sx = x + 7 + col * 18;
            g.fill(sx, hotbarY, sx + 18, hotbarY + 18, 0xFF111133);
            g.renderOutline(sx, hotbarY, 18, 18, 0xFF444466);
        }

        // === フッター ===
        g.drawCenteredString(this.font, "\u00A78NEXUS CORP. \u2014 SECURE STORAGE PROTOCOL",
            x + w / 2, y + h - 12, 0xFF334444);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // バニラのラベルを非表示（カスタムヘッダーで代替）
    }
}
