package com.levanilla.rogue.client;

import com.levanilla.rogue.networking.NpcMenuActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class NpcMenuScreen extends Screen {
    private final String role;
    private final boolean dungeon;
    private final boolean floorCleared;

    public NpcMenuScreen(String role, boolean dungeon, boolean floorCleared) {
        super(Component.translatable("gui.tac_rogue.npc_menu.title"));
        this.role = role;
        this.dungeon = dungeon;
        this.floorCleared = floorCleared;
    }

    @Override
    protected void init() {
        int panelW = 260;
        int x = this.width / 2 - panelW / 2;
        int y = this.height / 2 - 56;
        int by = y + 52;

        if ("commander".equals(role)) {
            addAction(x, by, "gui.tac_rogue.npc_menu.quest", "quest"); by += 24;
            addAction(x, by, "gui.tac_rogue.npc_menu.briefing", "talk"); by += 24;
        } else if ("quartermaster".equals(role)) {
            addAction(x, by, "gui.tac_rogue.npc_menu.shop", "shop"); by += 24;
            addAction(x, by, "gui.tac_rogue.npc_menu.supply", "talk"); by += 24;
        } else if ("intel".equals(role)) {
            if (dungeon) {
                addAction(x, by, floorCleared ? "gui.tac_rogue.npc_menu.extract" : "gui.tac_rogue.npc_menu.not_ready", "extract"); by += 24;
            } else {
                addAction(x, by, "gui.tac_rogue.npc_menu.intel", "intel"); by += 24;
                addAction(x, by, "gui.tac_rogue.npc_menu.floor_select", "floor_select"); by += 24;
                addLocalAction(x, by, Component.translatable("gui.tac_rogue.npc_menu.hud_settings"), () -> {
                    if (this.minecraft != null) this.minecraft.setScreen(new HudSettingsScreen(this));
                }); by += 24;
                addLocalAction(x, by, Component.translatable("gui.tac_rogue.npc_menu.popup_settings"), () -> {
                    if (this.minecraft != null) this.minecraft.setScreen(new PopupSettingsScreen(this));
                }); by += 24;
                addLocalAction(x, by, Component.translatable("gui.tac_rogue.npc_menu.quick_keys"), () -> {
                    if (this.minecraft != null) this.minecraft.setScreen(new QuickKeybindScreen(this));
                }); by += 24;
            }
        } else if ("medic".equals(role)) {
            addAction(x, by, "gui.tac_rogue.npc_menu.heal", "heal"); by += 24;
            addAction(x, by, "gui.tac_rogue.npc_menu.medical", "talk"); by += 24;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.npc_menu.close"), b -> this.onClose())
            .bounds(x, by + 4, panelW, 20).build());
    }

    private void addAction(int x, int y, String labelKey, String action) {
        this.addRenderableWidget(Button.builder(Component.translatable(labelKey), b -> {
            TacRogueNetworking.CHANNEL.sendToServer(new NpcMenuActionMessage(role, action));
            this.onClose();
        }).bounds(x, y, 260, 20).build());
    }

    private void addLocalAction(int x, int y, Component label, Runnable action) {
        this.addRenderableWidget(Button.builder(label, b -> action.run())
            .bounds(x, y, 260, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelW = 286;
        int panelH = 224;
        int x = this.width / 2 - panelW / 2;
        int y = this.height / 2 - 98;
        graphics.fill(x, y, x + panelW, y + panelH, 0xDD07111F);
        graphics.renderOutline(x, y, panelW, panelH, 0xFF55CCFF);
        graphics.drawCenteredString(this.font, speakerName(), this.width / 2, y + 12, 0xFFAAEEFF);
        graphics.drawCenteredString(this.font, bodyText(), this.width / 2, y + 28, 0xFFCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component speakerName() {
        return Component.translatable("gui.tac_rogue.npc_menu." + role + ".name");
    }

    private Component bodyText() {
        return Component.translatable("gui.tac_rogue.npc_menu." + role + ".body");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
