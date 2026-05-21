package com.levanilla.rogue.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class TacticalButton extends Button {
    TacticalButton(int x, int y, int w, int h, Component text, OnPress press) {
        super(x, y, w, h, text, press, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TacticalScreenStyle.drawButton(graphics, Minecraft.getInstance().font, this);
    }
}
