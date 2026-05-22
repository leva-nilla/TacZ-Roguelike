package com.levanilla.rogue.client;

import com.levanilla.rogue.networking.NpcMenuActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class NpcMenuScreen extends Screen {
    private static final int DIALOGUE_VARIANTS = 4;

    private final String role;
    private final boolean dungeon;
    private final boolean floorCleared;
    private int dialogueVariant = 0;
    private int actionScrollOffset = 0;

    public NpcMenuScreen(String role, boolean dungeon, boolean floorCleared) {
        super(Component.translatable("gui.tac_rogue.npc_menu.title"));
        this.role = role;
        this.dungeon = dungeon;
        this.floorCleared = floorCleared;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.dialogueVariant = Math.floorMod((int)(System.currentTimeMillis() / 1000L), DIALOGUE_VARIANTS);
        List<MenuAction> actions = buildActions();
        MenuLayout layout = layout(actions.size());
        int maxScroll = Math.max(0, actions.size() - layout.visibleActionRows);
        actionScrollOffset = Math.max(0, Math.min(actionScrollOffset, maxScroll));
        int y = layout.actionY;
        for (int i = actionScrollOffset; i < actions.size() && i < actionScrollOffset + layout.visibleActionRows; i++) {
            MenuAction action = actions.get(i);
            addMenuAction(layout.buttonX, y, layout.buttonW, layout.buttonH, action);
            y += layout.rowStep;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.npc_menu.close"), b -> this.onClose())
            .bounds(layout.buttonX, layout.closeY, layout.buttonW, layout.buttonH).build());
    }

    private List<MenuAction> buildActions() {
        List<MenuAction> actions = new ArrayList<>();
        if ("commander".equals(role)) {
            actions.add(MenuAction.server("gui.tac_rogue.npc_menu.quest", "quest"));
            actions.add(MenuAction.server("gui.tac_rogue.npc_menu.operation_plan", "operation_plan"));
            actions.add(MenuAction.server("gui.tac_rogue.npc_menu.briefing", "talk"));
        } else if ("quartermaster".equals(role)) {
            actions.add(MenuAction.server("gui.tac_rogue.npc_menu.shop", "shop"));
            actions.add(MenuAction.local(Component.translatable("gui.tac_rogue.npc_menu.quartermaster_services"), () -> {
                if (this.minecraft != null) this.minecraft.setScreen(new QuartermasterServicesScreen(this));
            }));
            if (deepUnlocked()) {
                actions.add(MenuAction.server("gui.tac_rogue.npc_menu.deep_operations", "deep_operations"));
            }
            actions.add(MenuAction.server("gui.tac_rogue.npc_menu.supply", "talk"));
        } else if ("intel".equals(role)) {
            if (dungeon) {
                actions.add(MenuAction.server(floorCleared ? "gui.tac_rogue.npc_menu.extract" : "gui.tac_rogue.npc_menu.not_ready", "extract"));
            } else {
                actions.add(MenuAction.server("gui.tac_rogue.npc_menu.intel", "intel"));
                actions.add(MenuAction.server("gui.tac_rogue.npc_menu.floor_select", "floor_select"));
                if (deepUnlocked()) {
                    actions.add(MenuAction.server("gui.tac_rogue.npc_menu.deep_operations", "deep_operations"));
                }
                actions.add(MenuAction.local(Component.translatable("gui.tac_rogue.npc_menu.guide"), () -> {
                    if (this.minecraft != null) this.minecraft.setScreen(new TutorialGuideScreen(this));
                }));
                actions.add(MenuAction.local(Component.translatable("gui.tac_rogue.npc_menu.settings"), () -> {
                    if (this.minecraft != null) this.minecraft.setScreen(new IntelSettingsScreen(this));
                }));
            }
        } else if ("medic".equals(role)) {
            actions.add(MenuAction.server("gui.tac_rogue.npc_menu.heal", "heal"));
            actions.add(MenuAction.local(Component.translatable("gui.tac_rogue.npc_menu.medical_support"), () -> {
                if (this.minecraft != null) this.minecraft.setScreen(new MedicalSupportScreen(this));
            }));
            actions.add(MenuAction.server("gui.tac_rogue.npc_menu.medical", "talk"));
        }
        return actions;
    }

    private boolean deepUnlocked() {
        return com.levanilla.rogue.core.ClientRunState.getHighestEverFloor() >= 100
            || com.levanilla.rogue.core.ClientRunState.getMaxReachedFloor() >= 100
            || com.levanilla.rogue.core.ClientRunState.getDeepCore() > 0;
    }

    private void addMenuAction(int x, int y, int width, int height, MenuAction action) {
        this.addRenderableWidget(Button.builder(action.label, b -> {
            if (action.localAction != null) {
                action.localAction.run();
            } else {
                TacRogueNetworking.CHANNEL.sendToServer(new NpcMenuActionMessage(role, action.serverAction));
                this.onClose();
            }
        }).bounds(x, y, width, height).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        MenuLayout layout = layout(buildActions().size());
        graphics.fill(layout.panelX, layout.panelY, layout.panelX + layout.panelW, layout.panelY + layout.panelH, 0xDD07111F);
        graphics.renderOutline(layout.panelX, layout.panelY, layout.panelW, layout.panelH, 0xFF55CCFF);
        graphics.drawCenteredString(this.font, speakerName(), this.width / 2, layout.panelY + 12, 0xFFAAEEFF);
        drawWrapped(graphics, bodyText(), layout.panelX + 16, layout.panelY + 29, layout.panelW - 32, 0xFFCCCCCC, layout.bodyLines);
        int actionCount = buildActions().size();
        if (actionCount > layout.visibleActionRows) {
            graphics.drawString(this.font, Component.literal("▲"), layout.panelX + layout.panelW - 18, layout.actionY - 10,
                actionScrollOffset > 0 ? 0xFF66F5FF : 0xFF335566, false);
            graphics.drawString(this.font, Component.literal("▼"), layout.panelX + layout.panelW - 18, layout.closeY - 12,
                actionScrollOffset + layout.visibleActionRows < actionCount ? 0xFF66F5FF : 0xFF335566, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component speakerName() {
        return Component.translatable("gui.tac_rogue.npc_menu." + role + ".name");
    }

    private Component bodyText() {
        return Component.translatable("gui.tac_rogue.npc_menu." + role + ".body." + dialogueVariant);
    }

    private MenuLayout layout(int actionCount) {
        int panelW = Math.min(286, Math.max(220, this.width - 24));
        int panelX = (this.width - panelW) / 2;
        int availableH = Math.max(132, this.height - 16);
        int headerH = availableH < 220 ? 56 : 70;
        int targetH = headerH + actionCount * 24 + 34;
        int panelH = Math.min(availableH, Math.max(132, targetH));
        int panelY = Math.max(8, (this.height - panelH) / 2);
        int buttonW = Math.max(150, panelW - 24);
        int buttonX = panelX + (panelW - buttonW) / 2;
        int actionY = panelY + headerH;
        int rowCount = Math.max(1, actionCount + 1);
        int availableRowsH = Math.max(72, panelH - headerH - 14);
        int rowStep = Math.max(16, Math.min(24, availableRowsH / rowCount));
        int buttonH = Math.max(14, Math.min(20, rowStep - 2));
        int closeY = Math.min(panelY + panelH - buttonH - 8, actionY + actionCount * rowStep + 4);
        int visibleActionRows = Math.max(1, Math.min(actionCount, Math.max(1, (closeY - actionY - 4) / rowStep)));
        int bodyLines = Math.max(1, Math.min(3, (actionY - panelY - 31) / 10));
        return new MenuLayout(panelX, panelY, panelW, panelH, buttonX, buttonW, buttonH, actionY, closeY, rowStep, bodyLines, visibleActionRows);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color, int maxLines) {
        int lines = 0;
        for (var line : this.font.split(text, width)) {
            if (lines >= maxLines) break;
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
            lines++;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<MenuAction> actions = buildActions();
        MenuLayout layout = layout(actions.size());
        int maxScroll = Math.max(0, actions.size() - layout.visibleActionRows);
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, delta);
        int next = actionScrollOffset + (delta < 0 ? 1 : -1);
        next = Math.max(0, Math.min(maxScroll, next));
        if (next != actionScrollOffset) {
            actionScrollOffset = next;
            this.init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private record MenuAction(Component label, String serverAction, Runnable localAction) {
        static MenuAction server(String labelKey, String action) {
            return new MenuAction(Component.translatable(labelKey), action, null);
        }

        static MenuAction local(Component label, Runnable action) {
            return new MenuAction(label, null, action);
        }
    }

    private record MenuLayout(int panelX, int panelY, int panelW, int panelH,
                              int buttonX, int buttonW, int buttonH, int actionY, int closeY,
                              int rowStep, int bodyLines, int visibleActionRows) {}
}
