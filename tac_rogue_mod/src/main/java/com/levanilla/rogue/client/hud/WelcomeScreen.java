package com.levanilla.rogue.client.hud;

import com.levanilla.rogue.client.ClientKeyBinds;
import com.levanilla.rogue.client.ClientPreferenceManager;
import com.levanilla.rogue.client.ClientStartupAssist;
import com.levanilla.rogue.client.KeyComboManager;
import com.levanilla.rogue.client.KeybindCaptureHelper;
import com.levanilla.rogue.networking.RogueActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.settings.KeyModifier;

import java.util.ArrayList;
import java.util.List;

public class WelcomeScreen extends Screen {
    private final List<Row> rows = new ArrayList<>();

    private Button closeButton;
    private Button shaderStartupBtn;
    private AbstractWidget fovBtn;
    private AbstractWidget sensitivityBtn;
    private AbstractWidget guiScaleBtn;

    private KeyMapping activeKeybind;
    private KeyMapping taczInteractKeyBtnMapping;
    private int pendingModifierKey = InputConstants.UNKNOWN.getValue();
    private int pendingModifierScan = 0;
    private final List<InputConstants.Key> pendingChord = new ArrayList<>();

    private double scrollY;
    private double targetScrollY;
    private int contentHeight;

    private Layout layout = Layout.empty();

    private static final class Row {
        final Component label;
        final AbstractWidget widget;
        final KeyMapping mapping;
        final int baseY;

        Row(Component label, AbstractWidget widget, KeyMapping mapping, int baseY) {
            this.label = label;
            this.widget = widget;
            this.mapping = mapping;
            this.baseY = baseY;
        }
    }

    private record Layout(
        int panelX,
        int panelY,
        int panelW,
        int panelH,
        int contentX,
        int contentW,
        int viewportTop,
        int viewportBottom,
        int buttonX,
        int buttonW,
        int labelW,
        int footerY
    ) {
        static Layout empty() {
            return new Layout(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public WelcomeScreen() {
        super(Component.translatable("gui.tac_rogue.welcome.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.rows.clear();
        this.activeKeybind = null;
        clearPendingModifier();
        this.pendingChord.clear();
        autoBindTacZInteract();

        this.layout = computeLayout();
        int y = 0;
        y = measureTutorial(y, this.layout.contentW());
        y += 16;
        y = addSettingsRows(y);
        y += 20;

        this.closeButton = Button.builder(Component.translatable("gui.tac_rogue.welcome.start"), b -> closeAndStart())
            .bounds(this.layout.contentX(), this.layout.footerY(), this.layout.contentW(), 20)
            .build();
        this.addRenderableWidget(this.closeButton);

        this.contentHeight = y;
        clampScroll();
        updateWidgetPositions();
    }

    private Layout computeLayout() {
        int margin = clampInt(Math.min(this.width, this.height) / 24, 10, 28);
        int panelW = Math.min(720, this.width - margin * 2);
        int panelH = Math.min(this.height - margin * 2, 520);
        panelW = Math.max(260, panelW);
        panelH = Math.max(190, panelH);

        int panelX = (this.width - panelW) / 2;
        int panelY = Math.max(8, (this.height - panelH) / 2);
        int contentX = panelX + clampInt(panelW / 22, 14, 26);
        int contentW = panelW - (contentX - panelX) * 2;
        int footerY = panelY + panelH - 32;
        int viewportTop = panelY + 44;
        int viewportBottom = footerY - 10;

        int buttonW = clampInt(contentW / 3, 132, 210);
        int buttonX = contentX + contentW - buttonW;
        int labelW = Math.max(82, buttonX - contentX - 12);
        return new Layout(panelX, panelY, panelW, panelH, contentX, contentW, viewportTop, viewportBottom,
            buttonX, buttonW, labelW, footerY);
    }

    private int measureTutorial(int y, int width) {
        int textWidth = Math.max(120, width);
        y += 4;
        y += 15;
        y += wrappedHeight("gui.tac_rogue.welcome.tut2", textWidth);
        y += wrappedHeight("gui.tac_rogue.welcome.tut3", textWidth);
        y += 12;
        y += 15;
        y += wrappedHeight("gui.tac_rogue.welcome.tut5", textWidth);
        y += 12;
        y += 15;
        y += wrappedHeight("gui.tac_rogue.welcome.tut7", textWidth);
        y += 12;
        y += 15;
        y += wrappedHeight("gui.tac_rogue.welcome.tut9", textWidth);
        y += wrappedHeight("gui.tac_rogue.welcome.tut10", textWidth);
        y += 20;
        return y;
    }

    private int wrappedHeight(String key, int width) {
        return Math.max(12, this.font.split(Component.translatable(key), width).size() * 12);
    }

    private int addSettingsRows(int y) {
        this.shaderStartupBtn = addButtonRow(Component.translatable("gui.tac_rogue.welcome.shader_startup"),
            Component.literal(ClientStartupAssist.shaderStatusText()), y, b -> {
                ClientStartupAssist.setShaderEnabled(!ClientStartupAssist.isShaderEnabled());
                b.setMessage(Component.literal(ClientStartupAssist.shaderStatusText()));
            });
        y += 38;

        this.fovBtn = this.minecraft.options.fov().createButton(this.minecraft.options, this.layout.buttonX(), 0, this.layout.buttonW());
        addExistingRow(Component.translatable("options.fov"), this.fovBtn, null, y);
        y += 24;

        this.sensitivityBtn = this.minecraft.options.sensitivity().createButton(this.minecraft.options, this.layout.buttonX(), 0, this.layout.buttonW());
        addExistingRow(Component.translatable("options.sensitivity"), this.sensitivityBtn, null, y);
        y += 24;

        this.guiScaleBtn = this.minecraft.options.guiScale().createButton(this.minecraft.options, this.layout.buttonX(), 0, this.layout.buttonW());
        addExistingRow(Component.translatable("gui.tac_rogue.welcome.gui_scale"), this.guiScaleBtn, null, y);
        y += 24;

        y = addKeyRows(y);
        return y;
    }

    private int addKeyRows(int y) {
        KeyMapping sprintMap = this.minecraft.options.keySprint;
        addKeyRow("gui.tac_rogue.welcome.key_sprint", sprintMap, y);
        y += 24;

        KeyMapping sneakMap = this.minecraft.options.keyShift;
        addKeyRow("gui.tac_rogue.welcome.key_sneak", sneakMap, y);
        y += 24;

        y = addOptionalKeyRow("gui.tac_rogue.welcome.key_crawl", y, "key.tacz.crawl.desc", "key.tacz.crawl");
        y = addKeyRowAndAdvance(Component.translatable("gui.tac_rogue.welcome.key_flashlight"), ClientKeyBinds.FLASHLIGHT, y);
        y = addKeyRowAndAdvance(Component.translatable("gui.tac_rogue.welcome.key_camera"), ClientKeyBinds.CAMERA_TOGGLE, y);
        y = addKeyRowAndAdvance(Component.translatable("gui.tac_rogue.welcome.key_perspective"), this.minecraft.options.keyTogglePerspective, y);
        y = addOptionalKeyRow("gui.tac_rogue.welcome.key_l3p_adjust", y,
            "key.leawind_third_person.adjust_position", "leawind_third_person.key.adjust_position");
        y = addOptionalKeyRow("gui.tac_rogue.welcome.key_attach", y, "key.tacz.refit.desc", "key.tacz.refit");

        this.taczInteractKeyBtnMapping = findExactKey("key.tacz.interact.desc", "key.tacz.interact");
        y = addKeyRowAndAdvance(Component.translatable("gui.tac_rogue.welcome.key_tacz_interact"), this.taczInteractKeyBtnMapping, y);

        y = addOptionalKeyRow("gui.tac_rogue.welcome.key_inspect", y, "key.tacz.inspect.desc", "key.tacz.inspect");
        y = addOptionalKeyRow("gui.tac_rogue.welcome.key_ysm_roulette", y, "key.yes_steve_model.animation_roulette.desc");
        y = addOptionalKeyRow("gui.tac_rogue.welcome.key_shader_toggle", y,
            "iris.keybind.toggleShaders", "key.iris.keybind.toggleShaders");
        y = addOptionalKeyRow("gui.tac_rogue.welcome.key_shader_select", y,
            "iris.keybind.shaderPackSelection", "key.iris.keybind.shaderPackSelection");
        return addOptionalKeyRow("gui.tac_rogue.welcome.key_shader_reload", y,
            "iris.keybind.reload", "key.iris.keybind.reload");
    }

    private int addKeyRowAndAdvance(Component label, KeyMapping mapping, int y) {
        addKeyRow(label, mapping, y);
        return y + 24;
    }

    private int addOptionalKeyRow(String labelKey, int y, String... keyNames) {
        addKeyRow(Component.translatable(labelKey), findExactKey(keyNames), y);
        return y + 24;
    }

    private void addKeyRow(String labelKey, KeyMapping mapping, int y) {
        addKeyRow(Component.translatable(labelKey), mapping, y);
    }

    private void addKeyRow(Component label, KeyMapping mapping, int y) {
        Button button = Button.builder(mapping != null ? getBindingName(mapping) : Component.literal("N/A"),
            b -> {
                if (mapping != null) {
                    this.activeKeybind = mapping;
                    this.pendingChord.clear();
                    clearPendingModifier();
                }
            }).bounds(this.layout.buttonX(), 0, this.layout.buttonW(), 20).build();
        if (mapping == null) button.active = false;
        addExistingRow(label, button, mapping, y);
    }

    private Button addButtonRow(Component label, Component message, int y, Button.OnPress onPress) {
        Button button = Button.builder(message, onPress)
            .bounds(this.layout.buttonX(), 0, this.layout.buttonW(), 20)
            .build();
        addExistingRow(label, button, null, y);
        return button;
    }

    private void addExistingRow(Component label, AbstractWidget widget, KeyMapping mapping, int y) {
        this.addRenderableWidget(widget);
        this.rows.add(new Row(label, widget, mapping, y));
    }

    private void closeAndStart() {
        TacRogueNetworking.CHANNEL.sendToServer(new RogueActionMessage(RogueActionMessage.ActionType.TUTORIAL_DONE));
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.addTag("rogue:tutorial_seen");
        }
        ClientPreferenceManager.markWelcomeSeenForCurrentWorld();
        this.minecraft.options.save();
        this.minecraft.setScreen(null);
    }

    private void autoBindTacZInteract() {
        KeyMapping interactMap = findExactKey("key.tacz.interact.desc", "key.tacz.interact");
        if (this.minecraft == null || this.minecraft.options == null || interactMap == null) return;
        KeyMapping swapMap = this.minecraft.options.keySwapOffhand;
        if ("key.keyboard.f".equals(swapMap.saveString()) && !"key.keyboard.f".equals(interactMap.saveString())) {
            swapMap.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
            interactMap.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.getKey("key.keyboard.f"));
            KeyMapping.resetMapping();
            this.minecraft.options.save();
        }
    }

    private KeyMapping findExactKey(String... keyNames) {
        if (this.minecraft == null || this.minecraft.options == null) return null;
        for (String expectedName : keyNames) {
            for (KeyMapping mapping : this.minecraft.options.keyMappings) {
                if (mapping.getName().equals(expectedName)) {
                    return mapping;
                }
            }
        }
        return null;
    }

    private Component getBindingName(KeyMapping mapping) {
        if (mapping == null) return Component.literal("N/A");
        if (this.activeKeybind == mapping) {
            return Component.literal("> ")
                .append(this.pendingChord.isEmpty()
                    ? Component.translatable("gui.tac_rogue.welcome.press_key").withStyle(net.minecraft.ChatFormatting.YELLOW)
                    : KeybindCaptureHelper.comboLabel(this.pendingChord).withStyle(net.minecraft.ChatFormatting.YELLOW))
                .append(" <");
        }
        if (KeyComboManager.hasCombo(mapping)) return KeyComboManager.label(mapping);
        return mapping.getTranslatedKeyMessage();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        clampScroll();
        updateWidgetPositions();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < this.layout.panelX() || mouseX > this.layout.panelX() + this.layout.panelW()
            || mouseY < this.layout.panelY() || mouseY > this.layout.panelY() + this.layout.panelH()) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        this.targetScrollY -= delta * 28.0D;
        clampScroll();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        this.scrollY += (this.targetScrollY - this.scrollY) * 0.45D;
        if (Math.abs(this.targetScrollY - this.scrollY) < 0.35D) this.scrollY = this.targetScrollY;
        updateKeyLabels();
        updateWidgetPositions();
    }

    private void updateKeyLabels() {
        for (Row row : this.rows) {
            if (!(row.widget instanceof Button button)) continue;
            if (row.widget == this.shaderStartupBtn) {
                button.setMessage(Component.literal(ClientStartupAssist.shaderStatusText()));
                continue;
            }
            if (row.mapping != null) {
                button.setMessage(getBindingName(row.mapping));
            }
        }
    }

    private void updateWidgetPositions() {
        for (Row row : this.rows) {
            int y = this.layout.viewportTop() + row.baseY - (int)Math.round(this.scrollY);
            row.widget.setX(this.layout.buttonX());
            row.widget.setY(y);
            row.widget.setWidth(this.layout.buttonW());
            row.widget.visible = y + row.widget.getHeight() >= this.layout.viewportTop()
                && y <= this.layout.viewportBottom();
        }
        if (this.closeButton != null) {
            this.closeButton.setX(this.layout.contentX());
            this.closeButton.setY(this.layout.footerY());
            this.closeButton.setWidth(this.layout.contentW());
            this.closeButton.visible = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.activeKeybind != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                this.activeKeybind.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
                KeyComboManager.clearCombo(this.activeKeybind);
                finishKeyCapture();
                return true;
            }
            InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
            addPendingKey(key);
            if (KeybindCaptureHelper.isModifierKey(keyCode) && this.pendingChord.size() == 1) {
                this.pendingModifierKey = keyCode;
                this.pendingModifierScan = scanCode;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.activeKeybind != null && !this.pendingChord.isEmpty()) {
            commitPendingChord();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.activeKeybind != null) {
            addPendingKey(InputConstants.Type.MOUSE.getOrCreate(button));
            commitPendingChord();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void addPendingKey(InputConstants.Key key) {
        if (key != null && key != InputConstants.UNKNOWN && !this.pendingChord.contains(key)) {
            this.pendingChord.add(key);
        }
    }

    private void commitPendingChord() {
        if (this.activeKeybind == null || this.pendingChord.isEmpty()) return;
        if (this.pendingChord.size() == 1) {
            KeyComboManager.clearCombo(this.activeKeybind);
            KeybindCaptureHelper.assign(this.activeKeybind, KeyModifier.NONE, this.pendingChord.get(0));
        } else if (KeybindCaptureHelper.canUseForgeModifier(this.pendingChord)) {
            KeyComboManager.clearCombo(this.activeKeybind);
            KeybindCaptureHelper.assign(this.activeKeybind,
                KeybindCaptureHelper.forgeModifier(this.pendingChord),
                KeybindCaptureHelper.mainKey(this.pendingChord));
        } else {
            KeyComboManager.setCombo(this.activeKeybind, this.pendingChord);
        }
        finishKeyCapture();
    }

    private void finishKeyCapture() {
        this.minecraft.options.save();
        this.activeKeybind = null;
        clearPendingModifier();
        this.pendingChord.clear();
    }

    private void clearPendingModifier() {
        this.pendingModifierKey = InputConstants.UNKNOWN.getValue();
        this.pendingModifierScan = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, 0xB8000000);
        drawPanel(graphics);

        graphics.enableScissor(this.layout.contentX(), this.layout.viewportTop(),
            this.layout.contentX() + this.layout.contentW(), this.layout.viewportBottom());
        renderContent(graphics);
        graphics.disableScissor();

        super.render(graphics, mouseX, mouseY, partialTick);
        renderScrollBar(graphics);
    }

    private void drawPanel(GuiGraphics graphics) {
        int x = this.layout.panelX();
        int y = this.layout.panelY();
        int w = this.layout.panelW();
        int h = this.layout.panelH();
        graphics.fill(x, y, x + w, y + h, 0xEA071118);
        graphics.fill(x, y, x + w, y + 2, 0xFF55DDAA);
        graphics.fill(x, y + h - 2, x + w, y + h, 0x996F5D26);
        graphics.renderOutline(x, y, w, h, 0xAA55DDAA);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y + 14, 0xFFAAFFDD);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.welcome.scroll_hint"),
            this.width / 2, y + 28, 0xFF6FE8C4);
    }

    private void renderContent(GuiGraphics graphics) {
        int x = this.layout.contentX();
        int w = this.layout.contentW();
        int y = this.layout.viewportTop() - (int)Math.round(this.scrollY);

        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.tut1").withStyle(net.minecraft.ChatFormatting.GOLD), x, y + 4, 0xFFFFFFFF, false);
        y += 19;
        y = drawWrapped(graphics, "gui.tac_rogue.welcome.tut2", x, y, w, 0xFFD8DEE9);
        y = drawWrapped(graphics, "gui.tac_rogue.welcome.tut3", x, y, w, 0xFFD8DEE9);
        y += 12;
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.tut4").withStyle(net.minecraft.ChatFormatting.AQUA), x, y, 0xFFFFFFFF, false);
        y += 15;
        y = drawWrapped(graphics, "gui.tac_rogue.welcome.tut5", x, y, w, 0xFFD8DEE9);
        y += 12;
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.tut6").withStyle(net.minecraft.ChatFormatting.YELLOW), x, y, 0xFFFFFFFF, false);
        y += 15;
        y = drawWrapped(graphics, "gui.tac_rogue.welcome.tut7", x, y, w, 0xFFD8DEE9);
        y += 12;
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.tut8").withStyle(net.minecraft.ChatFormatting.GREEN), x, y, 0xFFFFFFFF, false);
        y += 15;
        y = drawWrapped(graphics, "gui.tac_rogue.welcome.tut9", x, y, w, 0xFFD8DEE9);
        drawWrapped(graphics, "gui.tac_rogue.welcome.tut10", x, y, w, 0xFFD8DEE9);

        for (Row row : this.rows) {
            int rowY = this.layout.viewportTop() + row.baseY - (int)Math.round(this.scrollY);
            if (rowY + 20 < this.layout.viewportTop() || rowY > this.layout.viewportBottom()) continue;
            graphics.drawString(this.font, fit(row.label, this.layout.labelW()), x, rowY + 6, 0xFFE7FFF7, false);
        }

        int memoryY = this.layout.viewportTop() + rowsStartY() + 24 - (int)Math.round(this.scrollY);
        if (memoryY >= this.layout.viewportTop() && memoryY <= this.layout.viewportBottom()) {
            graphics.drawString(this.font, fit(Component.literal(ClientStartupAssist.memoryAdvice()), this.layout.contentW()),
                x, memoryY + 2, 0xFFB8C0CC, false);
        }
    }

    private int rowsStartY() {
        return this.rows.isEmpty() ? this.contentHeight : this.rows.get(0).baseY;
    }

    private int drawWrapped(GuiGraphics graphics, String key, int x, int y, int width, int color) {
        for (net.minecraft.util.FormattedCharSequence line : this.font.split(Component.translatable(key), width)) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += 12;
        }
        return y;
    }

    private void renderScrollBar(GuiGraphics graphics) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;
        int barX = this.layout.panelX() + this.layout.panelW() - 8;
        int trackTop = this.layout.viewportTop();
        int trackBottom = this.layout.viewportBottom();
        int trackH = Math.max(1, trackBottom - trackTop);
        int thumbH = Math.max(18, (int)(trackH * (trackH / (double)Math.max(trackH, this.contentHeight))));
        int thumbY = trackTop + (int)((trackH - thumbH) * (this.scrollY / maxScroll));
        graphics.fill(barX, trackTop, barX + 2, trackBottom, 0x4455DDAA);
        graphics.fill(barX - 1, thumbY, barX + 3, thumbY + thumbH, 0xDD55DDAA);
    }

    private Component fit(Component component, int maxWidth) {
        String text = component.getString();
        if (this.font.width(text) <= maxWidth) return component;
        while (text.length() > 1 && this.font.width(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return Component.literal(text + "...");
    }

    private void clampScroll() {
        int max = maxScroll();
        this.targetScrollY = Math.max(0, Math.min(max, this.targetScrollY));
        this.scrollY = Math.max(0, Math.min(max, this.scrollY));
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - Math.max(1, this.layout.viewportBottom() - this.layout.viewportTop()));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
