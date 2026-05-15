package com.levanilla.rogue.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

public class StashScreen extends AbstractContainerScreen<ChestMenu> {
    private static final int VISIBLE_ROWS = 3;
    private static final int COLUMNS = 2;
    private static final int PLAYER_AREA_Y = 112;
    private int storageScroll = 0;

    public StashScreen(ChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 380;
        this.imageHeight = 258;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        layoutSlots();
    }

    private void layoutSlots() {
        int storageSlots = RogueContainerLayout.containerSlotCount(this.menu);
        int entriesPerPage = VISIBLE_ROWS * COLUMNS;
        int maxScroll = Math.max(0, (storageSlots + COLUMNS - 1) / COLUMNS - VISIBLE_ROWS);
        storageScroll = Math.max(0, Math.min(storageScroll, maxScroll));

        for (int i = 0; i < storageSlots; i++) {
            int visibleIndex = i - storageScroll * COLUMNS;
            if (visibleIndex < 0 || visibleIndex >= entriesPerPage) {
                RogueContainerLayout.hideSlot(this.menu, i);
                continue;
            }
            int col = visibleIndex % COLUMNS;
            int row = visibleIndex / COLUMNS;
            RogueContainerLayout.placeSlot(this.menu, i, 20 + col * 168, 38 + row * 24);
        }
        RogueContainerLayout.applyRoguePlayerSlots(this.menu, PLAYER_AREA_Y);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        layoutSlots();
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;

        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xFF001122);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF01303B);
        g.fill(x, y, x + w, y + h, 0xDD000A14);
        g.fill(x, y, x + w, y + 2, 0xFF00FFFF);
        g.fill(x, y, x + 2, y + h, 0xFF00AAFF);
        g.fill(x, y + h - 2, x + w, y + h, 0xFF00AAFF);

        g.drawString(this.font, "STASH TERMINAL", x + 12, y + 8, 0xFF66FFE6, false);
        g.drawString(this.font, "ITEM ARCHIVE // " + storageUsed() + "/" + RogueContainerLayout.containerSlotCount(this.menu),
            x + 12, y + 19, 0xFF7F98A0, false);

        int listX = x + 12;
        int listY = y + 32;
        int entryW = 160;
        for (int i = 0; i < VISIBLE_ROWS * COLUMNS; i++) {
            int slotIndex = storageScroll * COLUMNS + i;
            if (slotIndex >= RogueContainerLayout.containerSlotCount(this.menu)) break;
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int ex = listX + col * 168;
            int ey = listY + row * 24;
            ItemStack stack = this.menu.slots.get(slotIndex).getItem();
            int bg = stack.isEmpty() ? 0x66111B22 : 0x99203A42;
            g.fill(ex, ey, ex + entryW, ey + 22, bg);
            g.renderOutline(ex, ey, entryW, 22, stack.isEmpty() ? 0x664A5B66 : 0xAA66FFE6);
            RogueContainerLayout.drawSlot(g, ex + 8, ey + 2, 0xFF0E1B22, null, this.font);
            String name = stack.isEmpty() ? "EMPTY SLOT" : stack.getHoverName().getString();
            int color = stack.isEmpty() ? 0xFF54616A : rarityColor(stack);
            g.drawString(this.font, trimToWidth(name, 116), ex + 32, ey + 3, color, false);
            String meta = stack.isEmpty() ? classifyEmpty(slotIndex) : classify(stack) + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
            g.drawString(this.font, trimToWidth(meta, 116), ex + 32, ey + 13, 0xFF91A0A8, false);
        }

        int maxScroll = Math.max(0, (RogueContainerLayout.containerSlotCount(this.menu) + COLUMNS - 1) / COLUMNS - VISIBLE_ROWS);
        if (maxScroll > 0) {
            int barX = x + w - 18;
            int barY = listY;
            int barH = VISIBLE_ROWS * 24 - 2;
            int thumbH = Math.max(12, barH * VISIBLE_ROWS / (VISIBLE_ROWS + maxScroll));
            int thumbY = barY + (barH - thumbH) * storageScroll / maxScroll;
            g.fill(barX, barY, barX + 4, barY + barH, 0x552C3A40);
            g.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xFF66FFE6);
        }

        g.fill(x + 12, y + PLAYER_AREA_Y - 8, x + w - 12, y + PLAYER_AREA_Y - 7, 0xAA00FFFF);
        RogueContainerLayout.drawRoguePlayerBackgrounds(g, this.font, x, y, PLAYER_AREA_Y);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, (RogueContainerLayout.containerSlotCount(this.menu) + COLUMNS - 1) / COLUMNS - VISIBLE_ROWS);
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, delta);
        storageScroll += delta < 0 ? 1 : -1;
        storageScroll = Math.max(0, Math.min(storageScroll, maxScroll));
        layoutSlots();
        return true;
    }

    private int storageUsed() {
        int used = 0;
        int count = RogueContainerLayout.containerSlotCount(this.menu);
        for (int i = 0; i < count; i++) {
            if (!this.menu.slots.get(i).getItem().isEmpty()) used++;
        }
        return used;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        return this.font.plainSubstrByWidth(text, Math.max(8, maxWidth - this.font.width("..."))) + "...";
    }

    private static int rarityColor(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("RogueRarity")) {
            return switch (stack.getTag().getString("RogueRarity")) {
                case "RARE" -> 0xFF55AAFF;
                case "EPIC" -> 0xFFC060FF;
                case "LEGENDARY" -> 0xFFFFD45A;
                case "UNCOMMON" -> 0xFF66FF99;
                default -> 0xFFE8F4F8;
            };
        }
        return 0xFFE8F4F8;
    }

    private static String classifyEmpty(int slotIndex) {
        return "#" + String.format(java.util.Locale.ROOT, "%02d", slotIndex + 1);
    }

    private static String classify(ItemStack stack) {
        if (stack.hasTag()) {
            if (stack.getTag().contains("GunId")) return "WEAPON";
            if (stack.getTag().contains("MeleeWeaponId")) return "MELEE";
        }
        String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().toLowerCase(java.util.Locale.ROOT);
        if (id.contains("ammo")) return "AMMO";
        if (id.contains("attachment") || id.contains("scope") || id.contains("muzzle") || id.contains("grip") || id.contains("stock")) return "ATTACHMENT";
        if (stack.isEdible()) return "SUPPLY";
        return "ITEM";
    }
}
