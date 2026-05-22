package com.levanilla.rogue.client;

import com.levanilla.rogue.networking.NpcMenuActionMessage;
import com.levanilla.rogue.networking.ServiceLoadoutSwapMessage;
import com.levanilla.rogue.networking.SyncServiceStashMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QuartermasterServicesScreen extends Screen {
    private static final ServiceEntry[] ENTRIES = {
        new ServiceEntry("gui.tac_rogue.quartermaster_services.save_loadout", "gui.tac_rogue.quartermaster_services.save_loadout.desc", "save_loadout"),
        new ServiceEntry("gui.tac_rogue.quartermaster_services.restore_loadout", "gui.tac_rogue.quartermaster_services.restore_loadout.desc", "restore_loadout"),
        new ServiceEntry("gui.tac_rogue.quartermaster_services.bulk_ammo", "gui.tac_rogue.quartermaster_services.bulk_ammo.desc", "bulk_ammo"),
        new ServiceEntry("gui.tac_rogue.quartermaster_services.attachment_check", "gui.tac_rogue.quartermaster_services.attachment_check.desc", "attachment_check"),
        new ServiceEntry("gui.tac_rogue.quartermaster_services.sell_loose_attachments", "gui.tac_rogue.quartermaster_services.sell_loose_attachments.desc", "sell_loose_attachments")
    };
    private static final int MAX_INTERNAL_POPUPS = 1;

    private static List<SyncServiceStashMessage.Entry> hotbarEntries = List.of();
    private static List<SyncServiceStashMessage.Entry> stashEntries = List.of();

    private final Screen parent;
    private final List<InternalPopup> internalPopups = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int serviceX;
    private int serviceW;
    private int sideX;
    private int sideW;
    private int listY;
    private int rowH;
    private int stashScroll;
    private Source selectedSource;
    private int selectedSlot = -1;
    private Source dragSource;
    private int dragSlot = -1;
    private ItemStack dragStack = ItemStack.EMPTY;
    private double dragStartX;
    private double dragStartY;
    private boolean dragging;

    public QuartermasterServicesScreen(Screen parent) {
        super(Component.translatable("gui.tac_rogue.quartermaster_services.title"));
        this.parent = parent;
    }

    public static void syncLoadoutPanel(List<SyncServiceStashMessage.Entry> hotbar, List<SyncServiceStashMessage.Entry> stash) {
        hotbarEntries = hotbar == null ? List.of() : List.copyOf(hotbar);
        stashEntries = stash == null ? List.of() : List.copyOf(stash);
    }

    public static boolean offerServicePopup(String type, Component title, Component body, int color, int durationTicks) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof QuartermasterServicesScreen screen)) return false;
        screen.addInternalPopup(title, body, color, durationTicks);
        return true;
    }

    @Override
    protected void init() {
        clearWidgets();
        computeLayout();
        for (int i = 0; i < ENTRIES.length; i++) {
            ServiceEntry entry = ENTRIES[i];
            addAction(serviceX + 14, listY + i * rowH, serviceW - 28, entry.labelKey(), entry.action());
        }
        this.addRenderableWidget(new TacticalButton(serviceX + 14, panelY + panelH - 28, serviceW - 28, 20,
            Component.translatable("gui.tac_rogue.common.back"), b -> onClose()));
        requestLoadoutPanelSync();
    }

    @Override
    public void tick() {
        super.tick();
        internalPopups.removeIf(InternalPopup::tickExpired);
    }

    private void computeLayout() {
        panelW = Math.min(this.width - 16, 760);
        panelH = Math.min(this.height - 16, 336);
        panelX = (this.width - panelW) / 2;
        panelY = Math.max(8, (this.height - panelH) / 2);
        sideW = panelW >= 620 ? 318 : 0;
        serviceW = sideW > 0 ? panelW - sideW - 14 : panelW;
        serviceX = panelX;
        sideX = serviceX + serviceW + 14;
        listY = panelY + 50;
        rowH = Math.max(34, Math.min(40, (panelY + panelH - 136 - listY) / ENTRIES.length));
    }

    private void addAction(int x, int y, int width, String labelKey, String action) {
        this.addRenderableWidget(new TacticalButton(x, y, width, 20, Component.translatable(labelKey), b -> {
            TacRogueNetworking.CHANNEL.sendToServer(new NpcMenuActionMessage("quartermaster", action));
            addInternalPopup(Component.translatable("gui.tac_rogue.quartermaster_services.processing"),
                Component.translatable(labelKey), 0xFF55CCFF, 70);
        }));
    }

    private void requestLoadoutPanelSync() {
        TacRogueNetworking.CHANNEL.sendToServer(new NpcMenuActionMessage("quartermaster", "sync_service_stash"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        computeLayout();
        drawPanel(graphics, serviceX, panelY, serviceW, panelH, 0xFF55CCFF);
        graphics.drawCenteredString(this.font, this.title, serviceX + serviceW / 2, panelY + 12, 0xFFAAEEFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.quartermaster_services.subtitle"),
            serviceX + serviceW / 2, panelY + 25, 0xFFB8C6D9);
        for (int i = 0; i < ENTRIES.length; i++) {
            int y = listY + i * rowH + 22;
            graphics.drawCenteredString(this.font, Component.translatable(ENTRIES[i].descKey()),
                serviceX + serviceW / 2, y, 0xFF9AA8B8);
        }
        if (sideW > 0) {
            renderLoadoutPanel(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderInternalPopups(graphics);
        renderLoadoutTooltip(graphics, mouseX, mouseY);
        renderDraggedStack(graphics, mouseX, mouseY);
    }

    private void renderLoadoutPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        drawPanel(graphics, sideX, panelY, sideW, panelH, 0xFF88CCFF);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quartermaster_services.panel.loadout"),
            sideX + 10, panelY + 10, 0xFFAAEEFF, false);

        int hotbarY = panelY + 28;
        int nextY = renderSlotSection(graphics, Component.translatable("gui.tac_rogue.quartermaster_services.panel.guns"),
            hotbarY, 0, 1, mouseX, mouseY);
        nextY = renderSlotSection(graphics, Component.translatable("gui.tac_rogue.quartermaster_services.panel.melee"),
            nextY + 3, 2, 2, mouseX, mouseY);
        nextY = renderSlotSection(graphics, Component.translatable("gui.tac_rogue.quartermaster_services.panel.items"),
            nextY + 3, 3, 11, mouseX, mouseY);
        nextY = renderSlotSection(graphics, Component.translatable("gui.tac_rogue.quartermaster_services.panel.ammo"),
            nextY + 3, 12, 15, mouseX, mouseY);

        int stashY = nextY + 7;
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quartermaster_services.panel.stash", stashEntries.size()),
            sideX + 10, stashY, 0xFFB8C6D9, false);
        int listTop = stashY + 13;
        int listH = panelY + panelH - 44 - listTop;
        int rows = Math.max(2, listH / 23);
        int maxScroll = Math.max(0, stashEntries.size() - rows);
        stashScroll = Math.max(0, Math.min(stashScroll, maxScroll));
        for (int i = 0; i < rows; i++) {
            int index = i + stashScroll;
            if (index >= stashEntries.size()) break;
            SyncServiceStashMessage.Entry entry = stashEntries.get(index);
            int rowY = listTop + i * 23;
            boolean hovered = inside(mouseX, mouseY, sideX + 8, rowY, sideW - 16, 22);
            boolean selected = selectedSource == Source.STASH && entry.slot() == selectedSlot;
            graphics.fill(sideX + 8, rowY, sideX + sideW - 8, rowY + 21, selected ? 0x5522CCFF : hovered ? 0x3322CCFF : 0x3307111F);
            graphics.renderOutline(sideX + 8, rowY, sideW - 16, 21, selected ? 0xFF55CCFF : 0x55334455);
            drawSlot(graphics, sideX + 11, rowY + 2, entry.stack(), false, mouseX, mouseY);
            graphics.drawString(this.font, "#" + (entry.slot() + 1), sideX + 32, rowY + 3, 0xFF7F8A98, false);
            String name = entry.stack().isEmpty()
                ? Component.translatable("gui.tac_rogue.service_item.empty").getString()
                : entry.stack().getHoverName().getString();
            graphics.drawString(this.font, trim(name, 22), sideX + 58, rowY + 3, entry.stack().isEmpty() ? 0xFF7F8A98 : 0xFFE8FFF8, false);
            graphics.drawString(this.font, categoryLabel(entry.category()), sideX + 58, rowY + 13, categoryColor(entry.category()), false);
        }
    }

    private int renderSlotSection(GuiGraphics graphics, Component title, int y, int startSlot, int endSlot, int mouseX, int mouseY) {
        graphics.drawString(this.font, title, sideX + 10, y, 0xFFB8C6D9, false);
        int slotY = y + 12;
        int slotX = sideX + 10;
        int cols = Math.max(1, Math.min(9, (sideW - 20) / 24));
        int count = Math.max(0, endSlot - startSlot + 1);
        for (int i = 0; i < count; i++) {
            int slot = startSlot + i;
            SyncServiceStashMessage.Entry entry = hotbarEntry(slot);
            int sx = slotX + (i % cols) * 24;
            int sy = slotY + (i / cols) * 24;
            boolean selected = selectedSource == Source.INVENTORY && selectedSlot == slot;
            drawSlot(graphics, sx, sy, entry == null ? ItemStack.EMPTY : entry.stack(), selected, mouseX, mouseY);
            graphics.drawString(this.font, slotLabel(slot), sx + 2, sy + 16, 0xFF7F8A98, false);
        }
        return slotY + Math.max(1, (count + cols - 1) / cols) * 24;
    }

    private void drawSlot(GuiGraphics graphics, int x, int y, ItemStack stack, boolean selected, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 20, 20);
        graphics.fill(x, y, x + 20, y + 20, selected ? 0x5533CCFF : hovered ? 0x3333CCFF : 0x55111A24);
        graphics.renderOutline(x, y, 20, 20, selected ? 0xFF55CCFF : 0x66778899);
        if (!stack.isEmpty()) {
            if (!TacZGuiIconRenderer.renderLightweightIcon(graphics, this.font, stack, x + 2, y + 2)) {
                graphics.renderItem(stack, x + 2, y + 2);
                graphics.renderItemDecorations(this.font, stack, x + 2, y + 2);
            }
        }
    }

    private void renderLoadoutTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sideW <= 0) return;
        SyncServiceStashMessage.Entry hovered = hoveredEntry(mouseX, mouseY);
        if (hovered != null && !hovered.stack().isEmpty()) {
            graphics.renderTooltip(this.font, hovered.stack(), mouseX, mouseY);
        }
    }

    private SyncServiceStashMessage.Entry hoveredEntry(int mouseX, int mouseY) {
        HoveredSlot hoveredSlot = hoveredSlot(mouseX, mouseY);
        if (hoveredSlot == null) return null;
        if (hoveredSlot.source() == Source.STASH) {
            return stashEntries.stream().filter(e -> e.slot() == hoveredSlot.slot()).findFirst().orElse(null);
        }
        return hotbarEntry(hoveredSlot.slot());
    }

    private HoveredSlot hoveredSlot(int mouseX, int mouseY) {
        int y = panelY + 28;
        HoveredSlot hovered = hoveredInventorySection(mouseX, mouseY, y, 0, 1);
        if (hovered != null) return hovered;
        y = sectionEndY(y, 0, 1) + 3;
        hovered = hoveredInventorySection(mouseX, mouseY, y, 2, 2);
        if (hovered != null) return hovered;
        y = sectionEndY(y, 2, 2) + 3;
        hovered = hoveredInventorySection(mouseX, mouseY, y, 3, 11);
        if (hovered != null) return hovered;
        y = sectionEndY(y, 3, 11) + 3;
        hovered = hoveredInventorySection(mouseX, mouseY, y, 12, 15);
        if (hovered != null) return hovered;
        y = sectionEndY(y, 12, 15) + 7;
        int stashY = y;
        int listTop = stashY + 13;
        int listH = panelY + panelH - 44 - listTop;
        int rows = Math.max(2, listH / 23);
        for (int i = 0; i < rows; i++) {
            int index = i + stashScroll;
            if (index >= stashEntries.size()) break;
            int rowY = listTop + i * 23;
            if (inside(mouseX, mouseY, sideX + 8, rowY, sideW - 16, 22)) {
                return new HoveredSlot(Source.STASH, stashEntries.get(index).slot());
            }
        }
        return null;
    }

    private HoveredSlot hoveredInventorySection(int mouseX, int mouseY, int y, int startSlot, int endSlot) {
        int cols = Math.max(1, Math.min(9, (sideW - 20) / 24));
        int slotY = y + 12;
        int slotX = sideX + 10;
        int count = Math.max(0, endSlot - startSlot + 1);
        for (int i = 0; i < count; i++) {
            int sx = slotX + (i % cols) * 24;
            int sy = slotY + (i / cols) * 24;
            if (inside(mouseX, mouseY, sx, sy, 20, 20)) {
                return new HoveredSlot(Source.INVENTORY, startSlot + i);
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && sideW > 0) {
            HoveredSlot hovered = hoveredSlot((int)mouseX, (int)mouseY);
            if (hovered != null) {
                dragSource = hovered.source();
                dragSlot = hovered.slot();
                dragStack = stackFor(hovered.source(), hovered.slot());
                dragStartX = mouseX;
                dragStartY = mouseY;
                dragging = false;
                handleLoadoutSelection(hovered.source(), hovered.slot());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragSource != null) {
            if (!dragging && distanceSqr(mouseX, mouseY, dragStartX, dragStartY) > 9.0D) {
                dragging = true;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragSource != null) {
            HoveredSlot target = hoveredSlot((int)mouseX, (int)mouseY);
            if (dragging && target != null && target.source() != dragSource) {
                sendSwap(dragSource, dragSlot, target.source(), target.slot());
                selectedSource = null;
                selectedSlot = -1;
            }
            dragSource = null;
            dragSlot = -1;
            dragStack = ItemStack.EMPTY;
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (sideW > 0 && inside(mouseX, mouseY, sideX, panelY, sideW, panelH)) {
            int y = panelY + 28;
            y = sectionEndY(y, 0, 1) + 3;
            y = sectionEndY(y, 2, 2) + 3;
            y = sectionEndY(y, 3, 11) + 3;
            y = sectionEndY(y, 12, 15) + 7;
            int listTop = y + 13;
            int listH = panelY + panelH - 44 - listTop;
            int rows = Math.max(2, listH / 23);
            int maxScroll = Math.max(0, stashEntries.size() - rows);
            stashScroll = Math.max(0, Math.min(maxScroll, stashScroll + (delta < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void handleLoadoutSelection(Source source, int slot) {
        if (selectedSource != null && selectedSource != source) {
            sendSwap(selectedSource, selectedSlot, source, slot);
            selectedSource = null;
            selectedSlot = -1;
            return;
        }
        if (selectedSource == source && selectedSlot == slot) {
            selectedSource = null;
            selectedSlot = -1;
        } else {
            selectedSource = source;
            selectedSlot = slot;
        }
    }

    private void sendSwap(Source firstSource, int firstSlot, Source secondSource, int secondSlot) {
        int inventorySlot = firstSource == Source.INVENTORY ? firstSlot : secondSlot;
        int stashSlot = firstSource == Source.STASH ? firstSlot : secondSlot;
        TacRogueNetworking.CHANNEL.sendToServer(new ServiceLoadoutSwapMessage(inventorySlot, stashSlot));
        addInternalPopup(Component.translatable("gui.tac_rogue.quartermaster_services.processing"),
            Component.translatable("gui.tac_rogue.quartermaster_services.swap"), 0xFF55CCFF, 70);
    }

    private ItemStack stackFor(Source source, int slot) {
        SyncServiceStashMessage.Entry entry = source == Source.STASH
            ? stashEntries.stream().filter(e -> e.slot() == slot).findFirst().orElse(null)
            : hotbarEntry(slot);
        return entry == null ? ItemStack.EMPTY : entry.stack();
    }

    private void renderDraggedStack(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!dragging || dragStack.isEmpty()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        if (!TacZGuiIconRenderer.renderLightweightIcon(graphics, this.font, dragStack, mouseX - 8, mouseY - 8)) {
            graphics.renderItem(dragStack, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(this.font, dragStack, mouseX - 8, mouseY - 8);
        }
        graphics.pose().popPose();
    }

    private static double distanceSqr(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return dx * dx + dy * dy;
    }

    private SyncServiceStashMessage.Entry hotbarEntry(int slot) {
        for (SyncServiceStashMessage.Entry entry : hotbarEntries) {
            if (entry.slot() == slot) return entry;
        }
        return null;
    }

    private int sectionEndY(int y, int startSlot, int endSlot) {
        int cols = Math.max(1, Math.min(9, (sideW - 20) / 24));
        int count = Math.max(0, endSlot - startSlot + 1);
        return y + 12 + Math.max(1, (count + cols - 1) / cols) * 24;
    }

    private void addInternalPopup(Component title, Component body, int color, int durationTicks) {
        internalPopups.add(0, new InternalPopup(title, body, color, Math.max(60, Math.min(200, durationTicks))));
        while (internalPopups.size() > MAX_INTERNAL_POPUPS) {
            internalPopups.remove(internalPopups.size() - 1);
        }
    }

    private void renderInternalPopups(GuiGraphics graphics) {
        int popupW = Math.min(serviceW - 28, 300);
        int x = serviceX + 14;
        int y = panelY + panelH - 92;
        for (InternalPopup popup : internalPopups) {
            int alpha = popup.alpha();
            int bg = (alpha << 24) | 0x00101824;
            graphics.fill(x, y, x + popupW, y + 42, bg);
            graphics.renderOutline(x, y, popupW, 42, (alpha << 24) | (popup.color & 0x00FFFFFF));
            graphics.drawString(this.font, trim(popup.title.getString(), 32), x + 8, y + 6, popup.color, false);
            graphics.drawString(this.font, trim(popup.body.getString(), 40), x + 8, y + 22, 0xFFE8FFF8, false);
            y -= 46;
        }
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, 0xDD07111F);
        graphics.renderOutline(x, y, w, h, color);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String categoryLabel(String category) {
        return Component.translatable("gui.tac_rogue.service_item." + category.toLowerCase(Locale.ROOT)).getString();
    }

    private static int categoryColor(String category) {
        return switch (category) {
            case "gun" -> 0xFFFFD166;
            case "ammo" -> 0xFF66E0FF;
            case "attachment" -> 0xFFB69CFF;
            case "melee" -> 0xFFFF8888;
            case "item" -> 0xFFAAFFCC;
            default -> 0xFF777777;
        };
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String slotLabel(int slot) {
        return slot < 9 ? String.valueOf(slot + 1) : String.valueOf(slot);
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

    private enum Source { INVENTORY, STASH }

    private record HoveredSlot(Source source, int slot) {}

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
