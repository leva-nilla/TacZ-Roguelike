package com.levanilla.rogue.client;

import com.levanilla.rogue.networking.RogueActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ゲーム開始時にプレイヤーが初期装備を選択するための画面
 * 3種類のハンドガンキットから1つを選択し、選ぶまで画面を閉じられないように設定されています。
 */
public class StarterGearScreen extends Screen {
    public StarterGearScreen() {
        super(Component.literal("Loadout Selection"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // --- 初期装備の選択ボタン配置 ---
        
        // Balanced Kit (Glock 17): 汎用性の高い標準的なハンドガン
        this.addRenderableWidget(new TacticalButton(centerX - 120, centerY - 20, 70, 40, 
            Component.literal("BALANCED\n(Glock 17)"), b -> selectGear(RogueActionMessage.ActionType.SELECT_GEAR_BALANCED)));
        
        // High Power (Desert Eagle): 装填数は少ないが、一発の威力が高いハンドガン
        this.addRenderableWidget(new TacticalButton(centerX - 35, centerY - 20, 70, 40, 
            Component.literal("POWER\n(DEAGLE)"), b -> selectGear(RogueActionMessage.ActionType.SELECT_GEAR_POWER)));
        
        // Classic (M1911): 伝統的で扱いやすいハンドガン
        this.addRenderableWidget(new TacticalButton(centerX + 50, centerY - 20, 70, 40, 
            Component.literal("CLASSIC\n(M1911)"), b -> selectGear(RogueActionMessage.ActionType.SELECT_GEAR_CLASSIC)));
    }

    /**
     * 選択された装備タイプをサーバーへ送信し、画面を閉じる
     */
    private void selectGear(RogueActionMessage.ActionType type) {
        // パケットを送信して装備をリクエスト
        TacRogueNetworking.CHANNEL.sendToServer(new RogueActionMessage(type));
        // 選択後は画面を閉じる
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.fill(centerX - 140, centerY - 60, centerX + 140, centerY + 60, 0xAA001122);
        graphics.renderOutline(centerX - 140, centerY - 60, 280, 120, 0xAA00FFFF);
        
        graphics.drawCenteredString(this.font, "\u00A7bSELECT YOUR INITIAL ARMANENT", centerX, centerY - 50, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, "\u00A77[!] All loadouts are handguns for standardization", centerX, centerY + 45, 0xFFAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static class TacticalButton extends net.minecraft.client.gui.components.Button {
        public TacticalButton(int x, int y, int w, int h, Component text, OnPress press) {
            super(x, y, w, h, text, press, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int color = this.isHoveredOrFocused() ? 0xBB00FFFF : 0x8800AAFF;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, color);
            graphics.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFFFFFFFF);
            
            String[] lines = this.getMessage().getString().split("\n");
            int textY = this.getY() + (this.height - (lines.length * 9)) / 2;
            for (String line : lines) {
                graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, line, this.getX() + this.width / 2, textY, 0xFFFFFFFF);
                textY += 10;
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Force selection
    }
}
