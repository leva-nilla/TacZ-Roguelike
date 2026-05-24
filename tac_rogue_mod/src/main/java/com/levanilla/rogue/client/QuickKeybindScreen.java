package com.levanilla.rogue.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class QuickKeybindScreen extends Screen {
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private KeyMapping activeMapping;
    private int pendingModifierKey = InputConstants.UNKNOWN.getValue();
    private int pendingModifierScan = 0;
    private final List<InputConstants.Key> pendingChord = new ArrayList<>();
    private double scroll;
    private int contentHeight;

    public QuickKeybindScreen(Screen parent) {
        super(Component.translatable("gui.tac_rogue.quick_keys.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.rows.clear();
        this.activeMapping = null;
        clearPendingModifier();
        this.pendingChord.clear();
        this.scroll = Math.min(this.scroll, maxScroll());

        collectRows();

        int panelW = Math.min(430, this.width - 24);
        int x = this.width / 2 - panelW / 2;
        int y = 44;
        int by = y + 28;
        int rowH = 24;

        addOptionButton(this.minecraft.options.fov().createButton(this.minecraft.options, x + 18, by, panelW / 2 - 28), by);
        addOptionButton(this.minecraft.options.sensitivity().createButton(this.minecraft.options, x + panelW / 2 + 10, by, panelW / 2 - 28), by);
        by += 24;
        addOptionButton(this.minecraft.options.guiScale().createButton(this.minecraft.options, x + 18, by, panelW - 36), by);
        by += 34;

        for (Row row : rows) {
            Button button = Button.builder(bindingLabel(row.mapping), b -> {
                    activeMapping = row.mapping;
                    pendingChord.clear();
                    clearPendingModifier();
                })
                .bounds(x + panelW - 128, by, 112, 20)
                .build();
            row.button = button;
            row.baseY = by;
            this.addRenderableWidget(button);
            by += rowH;
        }
        this.contentHeight = by - y + 70;

        int bottomY = this.height - 54;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.quick_keys.recommended"), b -> {
            applyRecommendedProfile();
            refreshLabels();
        }).bounds(x + 14, bottomY, 128, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.quick_keys.disable_support"), b -> {
            unassignSupportKeys();
            refreshLabels();
        }).bounds(x + 150, bottomY, 132, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.common.back"), b -> this.onClose())
            .bounds(x + panelW - 96, bottomY, 82, 20).build());

        updateWidgetScroll();
    }

    private void collectRows() {
        addRow("gui.tac_rogue.quick_keys.move.sprint", this.minecraft.options.keySprint);
        addRow("gui.tac_rogue.quick_keys.move.sneak", this.minecraft.options.keyShift);
        addRow("gui.tac_rogue.quick_keys.view.perspective", this.minecraft.options.keyTogglePerspective);
        addRow("gui.tac_rogue.quick_keys.tacz.interact", find("key.tacz.interact.desc", "key.tacz.interact"));
        addRow("gui.tac_rogue.quick_keys.tacz.reload", find("key.tacz.reload.desc", "key.tacz.reload"));
        addRow("gui.tac_rogue.quick_keys.tacz.fire_select", find("key.tacz.fire_select.desc", "key.tacz.fire_select"));
        addRow("gui.tac_rogue.quick_keys.tacz.crawl", find("key.tacz.crawl.desc", "key.tacz.crawl"));
        addRow("gui.tac_rogue.quick_keys.tacz.refit", find("key.tacz.refit.desc", "key.tacz.refit"));
        addRow("gui.tac_rogue.quick_keys.tacz.zoom", find("key.tacz.zoom.desc", "key.tacz.zoom"));
        addRow("gui.tac_rogue.quick_keys.tacz.melee", find("key.tacz.melee.desc", "key.tacz.melee"));
        addRow("gui.tac_rogue.quick_keys.tacz.inspect", find("key.tacz.inspect.desc", "key.tacz.inspect"));
        addRow("gui.tac_rogue.quick_keys.rogue.flashlight", ClientKeyBinds.FLASHLIGHT);
        addRow("gui.tac_rogue.quick_keys.rogue.camera", ClientKeyBinds.CAMERA_TOGGLE);
        addRow("gui.tac_rogue.quick_keys.rogue.ai_debug", ClientKeyBinds.DEBUG_AI_OVERLAY);
        addRow("gui.tac_rogue.quick_keys.rogue.tutorial_next", ClientKeyBinds.TUTORIAL_NEXT);
        addRow("gui.tac_rogue.quick_keys.ysm.model", find("key.yes_steve_model.player_model.desc"));
        addRow("gui.tac_rogue.quick_keys.ysm.roulette", find("key.yes_steve_model.animation_roulette.desc"));
        addRow("gui.tac_rogue.quick_keys.ysm.extra", find("key.yes_steve_model.open_extra_player_render.desc"));
        addRow("gui.tac_rogue.quick_keys.leawind.adjust", find("key.leawind_third_person.adjust_position", "leawind_third_person.key.adjust_position"));
        addRow("gui.tac_rogue.quick_keys.leawind.side", find("key.leawind_third_person.toggle_side", "leawind_third_person.key.toggle_side"));
        addRow("gui.tac_rogue.quick_keys.shader.toggle", find("iris.keybind.toggleShaders", "key.iris.keybind.toggleShaders"));
        addRow("gui.tac_rogue.quick_keys.shader.select", find("iris.keybind.shaderPackSelection", "key.iris.keybind.shaderPackSelection"));
        addRow("gui.tac_rogue.quick_keys.shader.reload", find("iris.keybind.reload", "key.iris.keybind.reload"));
    }

    private void addOptionButton(AbstractWidget widget, int baseY) {
        this.addRenderableWidget(widget);
    }

    private void addRow(String labelKey, KeyMapping mapping) {
        if (mapping != null) {
            rows.add(new Row(labelKey, mapping));
        }
    }

    private KeyMapping find(String... names) {
        for (String name : names) {
            for (KeyMapping mapping : this.minecraft.options.keyMappings) {
                if (mapping.getName().equals(name)) {
                    return mapping;
                }
            }
        }
        return null;
    }

    private Component bindingLabel(KeyMapping mapping) {
        if (mapping == activeMapping) {
            return Component.literal("> ")
                .append(pendingChord.isEmpty()
                    ? Component.translatable("gui.tac_rogue.quick_keys.press_key").withStyle(ChatFormatting.YELLOW)
                    : KeybindCaptureHelper.comboLabel(pendingChord).withStyle(ChatFormatting.YELLOW))
                .append(" <");
        }
        if (KeyComboManager.hasCombo(mapping)) return KeyComboManager.label(mapping);
        return mapping.getTranslatedKeyMessage();
    }

    private void applyRecommendedProfile() {
        set(this.minecraft.options.keyDrop, KeyModifier.NONE, InputConstants.UNKNOWN);
        set(this.minecraft.options.keySwapOffhand, KeyModifier.NONE, InputConstants.UNKNOWN);
        set(this.minecraft.options.keySprint, KeyModifier.NONE, key(GLFW.GLFW_KEY_LEFT_SHIFT));
        set(this.minecraft.options.keyShift, KeyModifier.NONE, key(GLFW.GLFW_KEY_LEFT_CONTROL));
        set(this.minecraft.options.keyTogglePerspective, KeyModifier.NONE, key(GLFW.GLFW_KEY_F5));
        set(find("key.tacz.interact.desc", "key.tacz.interact"), KeyModifier.NONE, key(GLFW.GLFW_KEY_F));
        set(find("key.tacz.reload.desc", "key.tacz.reload"), KeyModifier.NONE, key(GLFW.GLFW_KEY_R));
        set(find("key.tacz.fire_select.desc", "key.tacz.fire_select"), KeyModifier.NONE, key(GLFW.GLFW_KEY_G));
        set(find("key.tacz.crawl.desc", "key.tacz.crawl"), KeyModifier.NONE, key(GLFW.GLFW_KEY_C));
        set(find("key.tacz.refit.desc", "key.tacz.refit"), KeyModifier.NONE, key(GLFW.GLFW_KEY_Z));
        set(find("key.tacz.zoom.desc", "key.tacz.zoom"), KeyModifier.NONE, key(GLFW.GLFW_KEY_X));
        set(find("key.tacz.melee.desc", "key.tacz.melee"), KeyModifier.NONE, key(GLFW.GLFW_KEY_V));
        set(find("key.tacz.inspect.desc", "key.tacz.inspect"), KeyModifier.NONE, key(GLFW.GLFW_KEY_H));
        set(ClientKeyBinds.FLASHLIGHT, KeyModifier.NONE, key(GLFW.GLFW_KEY_Q));
        set(ClientKeyBinds.CAMERA_TOGGLE, KeyModifier.NONE, key(GLFW.GLFW_KEY_F8));
        set(find("key.yes_steve_model.player_model.desc"), KeyModifier.ALT, key(GLFW.GLFW_KEY_Y));
        set(find("key.yes_steve_model.animation_roulette.desc"), KeyModifier.ALT, key(GLFW.GLFW_KEY_B));
        set(find("key.yes_steve_model.open_extra_player_render.desc"), KeyModifier.ALT, key(GLFW.GLFW_KEY_P));
        set(find("key.leawind_third_person.adjust_position", "leawind_third_person.key.adjust_position"), KeyModifier.NONE, key(GLFW.GLFW_KEY_LEFT_ALT));
        set(find("key.leawind_third_person.toggle_side", "leawind_third_person.key.toggle_side"), KeyModifier.NONE, key(GLFW.GLFW_KEY_TAB));
        set(find("iris.keybind.toggleShaders", "key.iris.keybind.toggleShaders"), KeyModifier.NONE, key(GLFW.GLFW_KEY_F7));
        set(find("iris.keybind.shaderPackSelection", "key.iris.keybind.shaderPackSelection"), KeyModifier.ALT, key(GLFW.GLFW_KEY_F7));
        set(find("iris.keybind.reload", "key.iris.keybind.reload"), KeyModifier.NONE, InputConstants.UNKNOWN);
        if (this.minecraft.options.guiScale().get() == 0 || this.minecraft.options.guiScale().get() > 2) {
            this.minecraft.options.guiScale().set(2);
        }
        unassignSupportKeys();
        saveKeys();
    }

    private void unassignSupportKeys() {
        for (KeyMapping mapping : this.minecraft.options.keyMappings) {
            String name = mapping.getName();
            if (name.startsWith("key.journeymap.") || name.startsWith("key.jade.")) {
                set(mapping, KeyModifier.NONE, InputConstants.UNKNOWN);
            }
        }
        saveKeys();
    }

    private static InputConstants.Key key(int glfwKey) {
        return InputConstants.Type.KEYSYM.getOrCreate(glfwKey);
    }

    private static void set(KeyMapping mapping, KeyModifier modifier, InputConstants.Key key) {
        if (mapping != null) {
            KeyComboManager.clearCombo(mapping);
            mapping.setKeyModifierAndCode(modifier, key);
        }
    }

    private void saveKeys() {
        KeyMapping.resetMapping();
        this.minecraft.options.save();
    }

    private void refreshLabels() {
        for (Row row : rows) {
            if (row.button != null) {
                row.button.setMessage(bindingLabel(row.mapping));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        refreshLabels();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeMapping != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                set(activeMapping, KeyModifier.NONE, InputConstants.UNKNOWN);
                KeyComboManager.clearCombo(activeMapping);
                finishCapture();
                refreshLabels();
                return true;
            }
            InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
            addPendingKey(key);
            if (KeybindCaptureHelper.isModifierKey(keyCode) && pendingChord.size() == 1) {
                this.pendingModifierKey = keyCode;
                this.pendingModifierScan = scanCode;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (activeMapping != null && !pendingChord.isEmpty()) {
            commitPendingChord();
            refreshLabels();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeMapping != null) {
            addPendingKey(InputConstants.Type.MOUSE.getOrCreate(button));
            commitPendingChord();
            refreshLabels();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - delta * 22));
        updateWidgetScroll();
        return true;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (this.height - 88));
    }

    private void updateWidgetScroll() {
        int clipTop = 116;
        int clipBottom = this.height - 62;
        for (Row row : rows) {
            if (row.button != null) {
                int y = (int)(row.baseY - scroll);
                row.button.setY(y);
                row.button.visible = y >= clipTop - 20 && y <= clipBottom;
            }
        }
    }

    private void finishCapture() {
        activeMapping = null;
        clearPendingModifier();
        pendingChord.clear();
        saveKeys();
    }

    private void clearPendingModifier() {
        pendingModifierKey = InputConstants.UNKNOWN.getValue();
        pendingModifierScan = 0;
    }

    private void addPendingKey(InputConstants.Key key) {
        if (key != null && key != InputConstants.UNKNOWN && !pendingChord.contains(key)) {
            pendingChord.add(key);
        }
    }

    private void commitPendingChord() {
        if (activeMapping == null || pendingChord.isEmpty()) return;
        if (pendingChord.size() == 1) {
            KeyComboManager.clearCombo(activeMapping);
            set(activeMapping, KeyModifier.NONE, pendingChord.get(0));
        } else if (KeybindCaptureHelper.canUseForgeModifier(pendingChord)) {
            KeyComboManager.clearCombo(activeMapping);
            set(activeMapping, KeybindCaptureHelper.forgeModifier(pendingChord), KeybindCaptureHelper.mainKey(pendingChord));
        } else {
            KeyComboManager.setCombo(activeMapping, pendingChord);
        }
        finishCapture();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelW = Math.min(430, this.width - 24);
        int panelH = this.height - 34;
        int x = this.width / 2 - panelW / 2;
        int y = 16;
        graphics.fill(x, y, x + panelW, y + panelH, 0xE607111F);
        graphics.fill(x, y, x + panelW, y + 2, 0xFF55DDAA);
        graphics.renderOutline(x, y, panelW, panelH, 0xAA55DDAA);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.quick_keys.title"), this.width / 2, y + 10, 0xFFAAFFDD);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.quick_keys.subtitle"), this.width / 2, y + 23, 0xFF9BA8B8);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quick_keys.display"), x + 18, y + 46, 0xFF8AFFF0, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quick_keys.gui_scale_recommend"), x + 18, y + 70, 0xFFFFD45C, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quick_keys.bindings"), x + 18, y + 104, 0xFF8AFFF0, false);

        int clipTop = y + 100;
        int clipBottom = this.height - 62;
        graphics.enableScissor(x + 8, clipTop, x + panelW - 8, clipBottom);
        int labelX = x + 18;
        for (Row row : rows) {
            int rowY = (int)(row.baseY - scroll);
            if (rowY >= clipTop - 22 && rowY <= clipBottom) {
                graphics.drawString(this.font, Component.translatable(row.labelKey), labelX, rowY + 6, 0xFFE8FFF8, false);
            }
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quick_keys.hint"), x + 14, this.height - 28, 0xFF8A98A0, false);
    }

    @Override
    public void onClose() {
        saveKeys();
        if (this.minecraft != null && this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class Row {
        private final String labelKey;
        private final KeyMapping mapping;
        private Button button;
        private int baseY;

        private Row(String labelKey, KeyMapping mapping) {
            this.labelKey = labelKey;
            this.mapping = mapping;
        }
    }
}
