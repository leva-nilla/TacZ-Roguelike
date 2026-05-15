package com.levanilla.rogue.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * ボス体力バー描画。
 */
public final class BossBarRenderer {

    private BossBarRenderer() {}

    public static void render(GuiGraphics graphics, Minecraft mc, int screenWidth) {
        if (mc.level == null || mc.player == null) return;

        net.minecraft.world.entity.Mob nearestBoss = null;
        double nearestDist = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof net.minecraft.world.entity.Mob mob) || !mob.isAlive()) continue;
            if (!mob.getTags().contains("rogue:boss")) continue;
            double dist = mob.distanceToSqr(mc.player);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestBoss = mob;
            }
        }
        if (nearestBoss == null) return;

        String name = nearestBoss.getCustomName() != null ? nearestBoss.getCustomName().getString() : "[BOSS]";
        float ratio = Math.max(0.0f, Math.min(1.0f, nearestBoss.getHealth() / nearestBoss.getMaxHealth()));

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
