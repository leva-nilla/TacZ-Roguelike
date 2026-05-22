package com.levanilla.rogue.client.hud;

import com.levanilla.rogue.core.ClientRunState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * ボス体力バー描画。
 * サーバー同期された状態だけを読む。描画中にワールド内エンティティを走査しない。
 */
public final class BossBarRenderer {

    private BossBarRenderer() {}

    public static void render(GuiGraphics graphics, Minecraft mc, int screenWidth) {
        if (mc.level == null || mc.player == null) return;

        ClientRunState.BossBarState boss = ClientRunState.getBossBarState();
        if (boss == null) return;

        String name = boss.name();
        float ratio = boss.ratio();

        int bw = 200;
        int bh = 12;
        int bx = (screenWidth - bw) / 2;
        int by = 20;

        graphics.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xAA000000);
        graphics.fill(bx, by, bx + bw, by + bh, 0x44FF0000);
        graphics.fill(bx, by, bx + (int)(bw * ratio), by + bh, 0xFFFF0000);
        graphics.drawCenteredString(mc.font, name, screenWidth / 2, by - 10, 0xFFFF5555);
    }
}
