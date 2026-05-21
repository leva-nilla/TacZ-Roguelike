package com.levanilla.rogue.client;

import net.minecraft.client.gui.GuiGraphics;

interface RogueInventoryTabRenderer {
    void render(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int x, int y, int mouseX, int mouseY);
}
