package com.levanilla.rogue.client.hud;

import com.levanilla.rogue.client.ClientKeyBinds;
import com.levanilla.rogue.networking.RogueActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;

public class WelcomeScreen extends Screen {

    private Button closeButton;

    // キー設定用のボタン
    private Button sprintKeyBtn;
    private Button sneakKeyBtn;
    private Button crawlKeyBtn;
    private Button flashLightKeyBtn;
    private Button cameraKeyBtn;
    private Button perspectiveKeyBtn;
    private Button l3pAdjustKeyBtn;
    private Button attachKeyBtn;
    private Button taczInteractKeyBtn;
    private KeyMapping taczInteractKeyBtnMapping;
    private Button inspectKeyBtn;

    private AbstractWidget fovBtn;
    private AbstractWidget sensitivityBtn;

    private KeyMapping activeKeybind = null; // 現在設定状態にあるキー

    private double scrollY = 0;
    private double targetScrollY = 0;
    private int contentHeight = 500; // init() で動的に計算

    private final List<WidgetPos> widgetPositions = new ArrayList<>();

    private static class WidgetPos {
        AbstractWidget widget;
        int baseY;
        WidgetPos(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }
    }

    public WelcomeScreen() {
        super(Component.translatable("gui.tac_rogue.welcome.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.widgetPositions.clear();

        // --- Auto-bind F key to TacZ Interact if it's currently used for offhand ---
        KeyMapping interactMap = findExactKey("key.tacz.interact.desc", "key.tacz.interact");
        if (this.minecraft != null && this.minecraft.options != null && interactMap != null) {
            KeyMapping swapMap = this.minecraft.options.keySwapOffhand;
            if ("key.keyboard.f".equals(swapMap.saveString()) && !"key.keyboard.f".equals(interactMap.saveString())) {
                swapMap.setKeyModifierAndCode(net.minecraftforge.client.settings.KeyModifier.NONE, com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
                interactMap.setKeyModifierAndCode(net.minecraftforge.client.settings.KeyModifier.NONE, com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.f"));
                KeyMapping.resetMapping();
                this.minecraft.options.save();
            }
        }

        int centerX = this.width / 2;

        // 計算上の Y座標
        int currentY = 50;

        // テキストの高さをざっくり計算する (幅制限: max 360 or width-40)
        int textWidth = Math.min(360, this.width - 40);
        int lineHeight = 12;

        // タイトル
        currentY += lineHeight * 2; // tut1
        currentY += this.font.split(Component.translatable("gui.tac_rogue.welcome.tut2"), textWidth).size() * lineHeight;
        currentY += this.font.split(Component.translatable("gui.tac_rogue.welcome.tut3"), textWidth).size() * lineHeight;
        currentY += lineHeight;
        
        currentY += lineHeight * 2; // tut4
        currentY += this.font.split(Component.translatable("gui.tac_rogue.welcome.tut5"), textWidth).size() * lineHeight;
        currentY += lineHeight;
        
        currentY += lineHeight * 2; // tut6
        currentY += this.font.split(Component.translatable("gui.tac_rogue.welcome.tut7"), textWidth).size() * lineHeight;
        currentY += lineHeight; // scroll_hint
        currentY += lineHeight * 2;

        // ===== キー設定UI領域 (1カラム構成) =====
        int btnWidth = 150;
        int colX = centerX; // ボタンは中心から右へ
        int spacing = 24;

        this.fovBtn = this.minecraft.options.fov().createButton(this.minecraft.options, colX, currentY, btnWidth);
        this.addRenderableWidget(this.fovBtn);
        this.widgetPositions.add(new WidgetPos(this.fovBtn, currentY));
        
        currentY += spacing;

        this.sensitivityBtn = this.minecraft.options.sensitivity().createButton(this.minecraft.options, colX, currentY, btnWidth);
        this.addRenderableWidget(this.sensitivityBtn);
        this.widgetPositions.add(new WidgetPos(this.sensitivityBtn, currentY));

        currentY += spacing;

        KeyMapping sprintMap = this.minecraft.options.keySprint;
        this.sprintKeyBtn = this.addRenderableWidget(Button.builder(getBindingName(sprintMap), b -> this.activeKeybind = sprintMap)
            .bounds(colX, currentY, btnWidth, 20).build());
        this.widgetPositions.add(new WidgetPos(this.sprintKeyBtn, currentY));
        
        currentY += spacing;

        KeyMapping sneakMap = this.minecraft.options.keyShift;
        this.sneakKeyBtn = this.addRenderableWidget(Button.builder(getBindingName(sneakMap), b -> this.activeKeybind = sneakMap)
            .bounds(colX, currentY, btnWidth, 20).build());
        this.widgetPositions.add(new WidgetPos(this.sneakKeyBtn, currentY));

        currentY += spacing;

        KeyMapping crawlMap = findExactKey("key.tacz.crawl.desc", "key.tacz.crawl");
        this.crawlKeyBtn = this.addRenderableWidget(Button.builder(
            crawlMap != null ? getBindingName(crawlMap) : Component.literal("N/A"), 
            b -> { if (crawlMap != null) this.activeKeybind = crawlMap; })
            .bounds(colX, currentY, btnWidth, 20).build());
        this.widgetPositions.add(new WidgetPos(this.crawlKeyBtn, currentY));
        if (crawlMap == null) this.crawlKeyBtn.active = false;

        currentY += spacing;

        KeyMapping flashMap = ClientKeyBinds.FLASHLIGHT;
        this.flashLightKeyBtn = this.addRenderableWidget(Button.builder(getBindingName(flashMap), b -> this.activeKeybind = flashMap)
            .bounds(colX, currentY, btnWidth, 20).build());
        this.widgetPositions.add(new WidgetPos(this.flashLightKeyBtn, currentY));

        currentY += spacing;

        KeyMapping cameraMap = ClientKeyBinds.CAMERA_TOGGLE;
        this.cameraKeyBtn = this.addRenderableWidget(Button.builder(getBindingName(cameraMap), b -> this.activeKeybind = cameraMap)
            .bounds(colX, currentY, btnWidth, 20).build());
        this.widgetPositions.add(new WidgetPos(this.cameraKeyBtn, currentY));

        currentY += spacing;

        KeyMapping perspMap = this.minecraft.options.keyTogglePerspective;
        this.perspectiveKeyBtn = this.addRenderableWidget(Button.builder(getBindingName(perspMap), b -> this.activeKeybind = perspMap)
            .bounds(colX, currentY, btnWidth, 20).build());
        this.widgetPositions.add(new WidgetPos(this.perspectiveKeyBtn, currentY));

        currentY += spacing;

        KeyMapping l3pMap = findExactKey("key.leawind_third_person.adjust_position", "leawind_third_person.key.adjust_position");
        this.l3pAdjustKeyBtn = this.addRenderableWidget(Button.builder(
            l3pMap != null ? getBindingName(l3pMap) : Component.literal("N/A"), 
            b -> { if (l3pMap != null) this.activeKeybind = l3pMap; })
            .bounds(colX, currentY, btnWidth, 20).build());
        this.widgetPositions.add(new WidgetPos(this.l3pAdjustKeyBtn, currentY));
        if (l3pMap == null) this.l3pAdjustKeyBtn.active = false;

        currentY += spacing;

        KeyMapping attachMap = findExactKey("key.tacz.refit.desc", "key.tacz.refit");
        this.attachKeyBtn = this.addRenderableWidget(Button.builder(
            attachMap != null ? getBindingName(attachMap) : Component.literal("N/A"), 
            b -> { if (attachMap != null) this.activeKeybind = attachMap; })
            .bounds(colX, currentY, btnWidth, 20).build());
        this.widgetPositions.add(new WidgetPos(this.attachKeyBtn, currentY));
        if (attachMap == null) this.attachKeyBtn.active = false;

        currentY += spacing;

        this.taczInteractKeyBtnMapping = interactMap;
        this.taczInteractKeyBtn = this.addRenderableWidget(Button.builder(
            interactMap != null ? getBindingName(interactMap) : Component.literal("N/A"), 
            b -> { if (interactMap != null) this.activeKeybind = interactMap; })
            .bounds(colX, currentY, btnWidth, 20).build());
        this.widgetPositions.add(new WidgetPos(this.taczInteractKeyBtn, currentY));
        if (interactMap == null) this.taczInteractKeyBtn.active = false;

        currentY += spacing;

        KeyMapping inspectMap = findExactKey("key.tacz.inspect.desc", "key.tacz.inspect");
        this.inspectKeyBtn = this.addRenderableWidget(Button.builder(
            inspectMap != null ? getBindingName(inspectMap) : Component.literal("N/A"), 
            b -> { if (inspectMap != null) this.activeKeybind = inspectMap; })
            .bounds(colX, currentY, btnWidth, 20).build());
        this.widgetPositions.add(new WidgetPos(this.inspectKeyBtn, currentY));
        if (inspectMap == null) this.inspectKeyBtn.active = false;

        currentY += spacing + 20;

        // 閉じる＆チュートリアル完了ボタン
        this.closeButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.welcome.start"), b -> {
            TacRogueNetworking.CHANNEL.sendToServer(new RogueActionMessage(RogueActionMessage.ActionType.TUTORIAL_DONE));
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.addTag("rogue:tutorial_seen");
            }
            this.minecraft.options.save();
            this.minecraft.setScreen(null);
        }).bounds(centerX - 100, currentY, 200, 20).build());
        this.widgetPositions.add(new WidgetPos(this.closeButton, currentY));

        currentY += 40;

        this.contentHeight = currentY;
    }

    private KeyMapping findExactKey(String... keyNames) {
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
        if (this.activeKeybind == mapping) {
            return Component.literal("> ").append(Component.translatable("gui.tac_rogue.welcome.press_key").withStyle(net.minecraft.ChatFormatting.YELLOW)).append(" <");
        }
        return mapping.getTranslatedKeyMessage();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double maxScroll = Math.max(0, this.contentHeight - this.height);
        this.targetScrollY = Math.max(0, Math.min(maxScroll, this.targetScrollY - delta * 25));
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        // スクロール適用
        this.scrollY += (this.targetScrollY - this.scrollY) * 0.5f;
        for (WidgetPos wp : this.widgetPositions) {
            wp.widget.setY((int) (wp.baseY - this.scrollY));
        }

        // ラベル更新
        if (this.sprintKeyBtn != null) this.sprintKeyBtn.setMessage(getBindingName(this.minecraft.options.keySprint));
        if (this.sneakKeyBtn != null) this.sneakKeyBtn.setMessage(getBindingName(this.minecraft.options.keyShift));
        
        KeyMapping crawlMap = findExactKey("key.tacz.crawl.desc", "key.tacz.crawl");
        if (this.crawlKeyBtn != null && crawlMap != null) this.crawlKeyBtn.setMessage(getBindingName(crawlMap));
        
        if (this.flashLightKeyBtn != null) this.flashLightKeyBtn.setMessage(getBindingName(ClientKeyBinds.FLASHLIGHT));
        if (this.cameraKeyBtn != null) this.cameraKeyBtn.setMessage(getBindingName(ClientKeyBinds.CAMERA_TOGGLE));
        if (this.perspectiveKeyBtn != null) this.perspectiveKeyBtn.setMessage(getBindingName(this.minecraft.options.keyTogglePerspective));
        
        KeyMapping l3pMap = findExactKey("key.leawind_third_person.adjust_position", "leawind_third_person.key.adjust_position");
        if (this.l3pAdjustKeyBtn != null && l3pMap != null) this.l3pAdjustKeyBtn.setMessage(getBindingName(l3pMap));
        
        KeyMapping attachMap = findExactKey("key.tacz.refit.desc", "key.tacz.refit");
        if (this.attachKeyBtn != null && attachMap != null) this.attachKeyBtn.setMessage(getBindingName(attachMap));

        if (this.taczInteractKeyBtn != null && this.taczInteractKeyBtnMapping != null) this.taczInteractKeyBtn.setMessage(getBindingName(this.taczInteractKeyBtnMapping));
        
        KeyMapping inspectMap = findExactKey("key.tacz.inspect.desc", "key.tacz.inspect");
        if (this.inspectKeyBtn != null && inspectMap != null) this.inspectKeyBtn.setMessage(getBindingName(inspectMap));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.activeKeybind != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                this.minecraft.options.setKey(this.activeKeybind, com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            } else {
                this.minecraft.options.setKey(this.activeKeybind, com.mojang.blaze3d.platform.InputConstants.getKey(keyCode, scanCode));
            }
            KeyMapping.resetMapping();
            this.activeKeybind = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.activeKeybind != null) {
            this.minecraft.options.setKey(this.activeKeybind, com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(button));
            KeyMapping.resetMapping();
            this.activeKeybind = null;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 背景を暗くする
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -this.scrollY, 0);

        // タイトル
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 20, 0xFFFFFF);

        // ===== チュートリアル説明 =====
        int textWidth = Math.min(360, this.width - 40);
        int textX = centerX - textWidth / 2;
        int textY = 50;
        int lineHeight = 12;

        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.tut1").withStyle(net.minecraft.ChatFormatting.GOLD), textX, textY, 0xFFFFFF, false);
        textY += lineHeight * 2;
        textY = drawMultilineWrapped(guiGraphics, "gui.tac_rogue.welcome.tut2", textX, textY, textWidth, lineHeight, 0xDDDDDD);
        textY = drawMultilineWrapped(guiGraphics, "gui.tac_rogue.welcome.tut3", textX, textY, textWidth, lineHeight, 0xDDDDDD);
        textY += lineHeight;
        
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.tut4").withStyle(net.minecraft.ChatFormatting.AQUA), textX, textY, 0xFFFFFF, false);
        textY += lineHeight * 2;
        textY = drawMultilineWrapped(guiGraphics, "gui.tac_rogue.welcome.tut5", textX, textY, textWidth, lineHeight, 0xDDDDDD);
        textY += lineHeight;
        
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.tut6").withStyle(net.minecraft.ChatFormatting.YELLOW), textX, textY, 0xFFFFFF, false);
        textY += lineHeight * 2;
        textY = drawMultilineWrapped(guiGraphics, "gui.tac_rogue.welcome.tut7", textX, textY, textWidth, lineHeight, 0xDDDDDD);

        textY += lineHeight;
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.welcome.scroll_hint").withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD), centerX, textY, 0xFFFFFF);

        // ===== キー設定のラベル (1カラム) =====
        int colLabelX = centerX - 10;
        int currentY = textY + lineHeight * 2;
        int spacing = 24;
        int txtOffsetY = 6; // バニラのボタンテキストとの高さを合わせる

        guiGraphics.drawString(this.font, Component.translatable("options.fov"), colLabelX - this.font.width(Component.translatable("options.fov").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("options.sensitivity"), colLabelX - this.font.width(Component.translatable("options.sensitivity").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.key_sprint"), colLabelX - this.font.width(Component.translatable("gui.tac_rogue.welcome.key_sprint").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.key_sneak"), colLabelX - this.font.width(Component.translatable("gui.tac_rogue.welcome.key_sneak").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.key_crawl"), colLabelX - this.font.width(Component.translatable("gui.tac_rogue.welcome.key_crawl").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.key_flashlight"), colLabelX - this.font.width(Component.translatable("gui.tac_rogue.welcome.key_flashlight").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.key_camera"), colLabelX - this.font.width(Component.translatable("gui.tac_rogue.welcome.key_camera").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.key_perspective"), colLabelX - this.font.width(Component.translatable("gui.tac_rogue.welcome.key_perspective").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.key_l3p_adjust"), colLabelX - this.font.width(Component.translatable("gui.tac_rogue.welcome.key_l3p_adjust").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.key_attach"), colLabelX - this.font.width(Component.translatable("gui.tac_rogue.welcome.key_attach").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.key_tacz_interact"), colLabelX - this.font.width(Component.translatable("gui.tac_rogue.welcome.key_tacz_interact").getString()), currentY + txtOffsetY, 0xFFFFFF, false);
        currentY += spacing;
        guiGraphics.drawString(this.font, Component.translatable("gui.tac_rogue.welcome.key_inspect"), colLabelX - this.font.width(Component.translatable("gui.tac_rogue.welcome.key_inspect").getString()), currentY + txtOffsetY, 0xFFFFFF, false);

        guiGraphics.pose().popPose();

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private int drawMultilineWrapped(GuiGraphics guiGraphics, String key, int x, int y, int wrapWidth, int lineHeight, int color) {
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(Component.translatable(key), wrapWidth);
        int currentY = y;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            guiGraphics.drawString(this.font, line, x, currentY, color, false);
            currentY += lineHeight;
        }
        return currentY;
    }
}

