package com.levanilla.rogue.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * ボス体力バー描画。
 */
public final class BossBarRenderer {

    private BossBarRenderer() {}

    public static void render(GuiGraphics graphics, Minecraft mc, int screenWidth) {
        if (mc.level == null) return;

        mc.level.entitiesForRendering().forEach(entity -> {
            if (entity instanceof net.minecraft.world.entity.Mob mob && mob.isAlive() && mob.hasCustomName()) {
                String name = mob.getCustomName() != null ? mob.getCustomName().getString() : "";
                if (name.contains("[BOSS]")) {
                    float bossHealth = mob.getHealth();
                    float bossMax = mob.getMaxHealth();
                    float ratio = Math.max(0.0f, Math.min(1.0f, bossHealth / bossMax));

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
        });
    }
}
