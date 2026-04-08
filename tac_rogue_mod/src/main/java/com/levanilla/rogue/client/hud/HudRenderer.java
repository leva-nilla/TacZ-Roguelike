package com.levanilla.rogue.client.hud;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.StaminaManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * カスタムHUD描画 — HP/スタミナ/ゴールド/階層表示。
 */
public final class HudRenderer {

    private HudRenderer() {}

    private static int lastFloor = 0;
    private static int victoryTicks = 0;

    public static void tickVictory() {
        if (victoryTicks > 0) victoryTicks--;
        int currentFloor = RunManager.getCurrentFloor();
        if (currentFloor > lastFloor && lastFloor != 0) {
            victoryTicks = 100;
        }
        lastFloor = currentFloor;
    }

    public static void render(GuiGraphics graphics, Minecraft mc, Player player, int screenWidth, int screenHeight) {
        boolean inLobby = mc.level != null && mc.level.dimension().location().getPath().contains("lobby");
        if (!RunManager.isRunActive() && !inLobby) {
            lastFloor = 0;
            return;
        }

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float absorptionAmount = player.getAbsorptionAmount();
        float totalHealth = health + absorptionAmount;
        float healthRatio = Math.max(0.0f, totalHealth / maxHealth);

        int currentFloor = RunManager.getCurrentFloor();
        String theme = RunManager.getCurrentThemeName();
        boolean isBoss = currentFloor > 0 && currentFloor % 5 == 0;

        // --- フロアクリア演出 ---
        if (victoryTicks > 0) {
            float alpha = Math.min(1.0f, victoryTicks / 20.0f);
            int color = ((int)(alpha * 255) << 24) | 0x00FF88;
            graphics.drawCenteredString(mc.font, "FLOOR CLEARED", screenWidth / 2, screenHeight / 4, color);
            graphics.drawCenteredString(mc.font, "PROCEED TO NEXT LEVEL", screenWidth / 2, screenHeight / 4 + 12, color & 0xAAFFFFFF);
        }

        // --- ボス戦の体力バー ---
        if (isBoss) BossBarRenderer.render(graphics, mc, screenWidth);

        // --- 左下のステータスパネル ---
        int x = 20;
        int y = screenHeight - 70;
        int barWidth = 130;
        int barHeight = 10;

        // 背景パネル
        graphics.fill(x - 5, y - 25, x + barWidth + 5, y + 40, 0x99000000);
        graphics.fill(x - 5, y - 25, x - 3, y + 40, isBoss ? 0xFFFF0000 : 0xFF00AAFF);

        // 階層情報
        String floorText = "FLOOR " + String.format("%02d", currentFloor);
        if (isBoss) floorText += " [EXTREME]";
        graphics.drawString(mc.font, floorText, x, y - 20, isBoss ? 0xFFFF5555 : 0xFFFFFFFF);
        graphics.drawString(mc.font, theme, x, y - 10, 0xFF888888);

        // 体力バー
        graphics.fill(x, y + 2, x + barWidth, y + barHeight + 2, 0x33FFFFFF);
        float clampedRatio = Math.min(1.0f, healthRatio);
        int healthColor = healthRatio > 1.0f ? 0xFFFFD700 : (healthRatio > 0.5 ? 0xFF00FF88 : (healthRatio > 0.25 ? 0xFFFFFF00 : 0xFFFF0000));
        int healthFill = (int)(barWidth * clampedRatio);
        graphics.fill(x, y + 2, x + healthFill, y + barHeight + 2, healthColor | 0xFF000000);
        String pctText = (int)(healthRatio * 100) + "%";
        int pctColor = healthRatio > 1.0f ? 0xFFFFD700 : 0xFFFFFFFF;
        graphics.drawCenteredString(mc.font, pctText, x + barWidth / 2, y + 1, pctColor);

        // スタミナバー
        float stamina = StaminaManager.getStamina(player);
        float maxStamina = StaminaManager.getMaxStamina(player);
        float staminaRatio = Math.max(0.0f, Math.min(1.0f, stamina / maxStamina));
        graphics.fill(x, y + barHeight + 6, x + barWidth, y + barHeight + 8, 0x33FFFFFF);
        graphics.fill(x, y + barHeight + 6, x + (int)(barWidth * staminaRatio), y + barHeight + 8, 0xFF00FFFF);

        // Cash display
        int gold = RunManager.getClientGold();
        graphics.drawString(mc.font, "§6$" + gold, x, y + barHeight + 14, 0xFFFFD700);
    }
}
