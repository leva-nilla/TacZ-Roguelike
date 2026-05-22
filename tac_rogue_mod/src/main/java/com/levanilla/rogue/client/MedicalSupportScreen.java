package com.levanilla.rogue.client;

import com.levanilla.rogue.networking.NpcMenuActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class MedicalSupportScreen extends Screen {
    private static final ServiceEntry[] ENTRIES = {
        new ServiceEntry("gui.tac_rogue.medical_support.field_buff", "gui.tac_rogue.medical_support.field_buff.desc", "field_buff"),
        new ServiceEntry("gui.tac_rogue.medical_support.medical_refill", "gui.tac_rogue.medical_support.medical_refill.desc", "medical_refill"),
        new ServiceEntry("gui.tac_rogue.medical_support.critical_briefing", "gui.tac_rogue.medical_support.critical_briefing.desc", "critical_briefing")
    };
    private static final int MAX_INTERNAL_POPUPS = 1;

    private final Screen parent;
    private final List<InternalPopup> internalPopups = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int buttonX;
    private int buttonW;
    private int listY;
    private int rowH;

    public MedicalSupportScreen(Screen parent) {
        super(Component.translatable("gui.tac_rogue.medical_support.title"));
        this.parent = parent;
    }

    public static boolean offerServicePopup(String type, Component title, Component body, int color, int durationTicks) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof MedicalSupportScreen screen)) return false;
        screen.addInternalPopup(title, body, color, durationTicks);
        return true;
    }

    @Override
    protected void init() {
        clearWidgets();
        computeLayout();
        for (int i = 0; i < ENTRIES.length; i++) {
            ServiceEntry entry = ENTRIES[i];
            addAction(buttonX, listY + i * rowH, buttonW, entry.labelKey(), entry.action());
        }
        this.addRenderableWidget(new TacticalButton(buttonX, panelY + panelH - 28, buttonW, 20,
            Component.translatable("gui.tac_rogue.common.back"), b -> onClose()));
    }

    @Override
    public void tick() {
        super.tick();
        internalPopups.removeIf(InternalPopup::tickExpired);
    }

    private void computeLayout() {
        panelW = Math.min(360, this.width - 16);
        panelH = Math.min(this.height - 16, 260);
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(8, (this.height - panelH) / 2);
        buttonW = Math.min(286, panelW - 28);
        buttonX = panelX + (panelW - buttonW) / 2;
        listY = panelY + 52;
        rowH = Math.max(38, Math.min(46, (panelY + panelH - 122 - listY) / ENTRIES.length));
    }

    private void addAction(int x, int y, int width, String labelKey, String action) {
        this.addRenderableWidget(new TacticalButton(x, y, width, 20, Component.translatable(labelKey), b -> {
            TacRogueNetworking.CHANNEL.sendToServer(new NpcMenuActionMessage("medic", action));
            addInternalPopup(Component.translatable("gui.tac_rogue.medical_support.processing"),
                Component.translatable(labelKey), 0xFFFF77CC, 70);
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        computeLayout();
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xDD07111F);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xFFFF77CC);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 12, 0xFFFFB6E6);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.medical_support.subtitle"),
            this.width / 2, panelY + 25, 0xFFB8C6D9);
        for (int i = 0; i < ENTRIES.length; i++) {
            int y = listY + i * rowH + 22;
            graphics.drawCenteredString(this.font, Component.translatable(ENTRIES[i].descKey()),
                this.width / 2, y, 0xFFB8A2B8);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderInternalPopups(graphics);
    }

    private void addInternalPopup(Component title, Component body, int color, int durationTicks) {
        internalPopups.add(0, new InternalPopup(title, body, color, Math.max(60, Math.min(200, durationTicks))));
        while (internalPopups.size() > MAX_INTERNAL_POPUPS) {
            internalPopups.remove(internalPopups.size() - 1);
        }
    }

    private void renderInternalPopups(GuiGraphics graphics) {
        int popupW = Math.min(panelW - 28, 310);
        int x = panelX + (panelW - popupW) / 2;
        int y = panelY + panelH - 86;
        for (InternalPopup popup : internalPopups) {
            int alpha = popup.alpha();
            graphics.fill(x, y, x + popupW, y + 42, (alpha << 24) | 0x00101824);
            graphics.renderOutline(x, y, popupW, 42, (alpha << 24) | (popup.color & 0x00FFFFFF));
            graphics.drawString(this.font, trim(popup.title.getString(), 34), x + 8, y + 6, popup.color, false);
            graphics.drawString(this.font, trim(popup.body.getString(), 42), x + 8, y + 22, 0xFFE8FFF8, false);
            y -= 46;
        }
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ServiceEntry(String labelKey, String descKey, String action) {}

    private static final class InternalPopup {
        private final Component title;
        private final Component body;
        private final int color;
        private final int durationTicks;
        private int age;

        private InternalPopup(Component title, Component body, int color, int durationTicks) {
            this.title = title == null ? Component.empty() : title;
            this.body = body == null ? Component.empty() : body;
            this.color = color;
            this.durationTicks = durationTicks;
        }

        private boolean tickExpired() {
            age++;
            return age > durationTicks;
        }

        private int alpha() {
            int fade = Math.min(age, Math.max(0, durationTicks - age));
            return Math.max(80, Math.min(220, 80 + fade * 8));
        }
    }
}
