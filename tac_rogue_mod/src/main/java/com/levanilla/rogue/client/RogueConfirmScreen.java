package com.levanilla.rogue.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class RogueConfirmScreen extends Screen {
    private final Screen parent;
    private final Consumer<Boolean> callback;
    private final Component message;
    private final Component yesLabel;
    private final Component noLabel;

    public RogueConfirmScreen(Screen parent, Consumer<Boolean> callback, Component title, Component message,
                              Component yesLabel, Component noLabel) {
        super(title);
        this.parent = parent;
        this.callback = callback;
        this.message = message;
        this.yesLabel = yesLabel;
        this.noLabel = noLabel;
    }

    @Override
    protected void init() {
        int buttonW = Math.min(180, Math.max(112, this.width / 5));
        int buttonH = this.height < 340 ? 18 : 22;
        int gap = 14;
        int y = this.height / 2 + 54;
        int left = this.width / 2 - buttonW - gap / 2;
        int right = this.width / 2 + gap / 2;
        addRenderableWidget(Button.builder(this.yesLabel, button -> this.callback.accept(true))
            .bounds(left, y, buttonW, buttonH).build());
        addRenderableWidget(Button.builder(this.noLabel, button -> this.callback.accept(false))
            .bounds(right, y, buttonW, buttonH).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TacticalScreenStyle.renderBackground(graphics, this.width, this.height);
        TacticalScreenStyle.drawCenteredStatus(graphics, this.font, this.width, this.height, this.title, this.message, -1);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderables.forEach(renderable -> {
            if (renderable instanceof Button button && button.visible) {
                TacticalScreenStyle.drawButton(graphics, this.font, button);
            }
        });
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.callback.accept(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
