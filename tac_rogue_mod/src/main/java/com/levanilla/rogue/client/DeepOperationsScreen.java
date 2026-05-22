package com.levanilla.rogue.client;

import com.levanilla.rogue.core.ClientRunState;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.service.DeepProgressService;
import com.levanilla.rogue.networking.DeepOperationMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DeepOperationsScreen extends Screen {
    private static final String[] PRESTIGE_KEYS = {"provision", "prepared", "selection", "supply_line", "black_market"};
    private static CacheResult lastCacheResult = null;
    private static final int MAX_INTERNAL_POPUPS = 2;
    private static final int POPUP_LANE_HEIGHT = 90;

    private Tab tab = Tab.CACHE;
    private ModifierMode modifierMode = ModifierMode.REFORGE;
    private int selectedGunSlot = -1;
    private int selectedTokenSlot = -1;
    private int gunScroll = 0;
    private int tokenScroll = 0;
    private int pageScroll = 0;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;
    private final List<InternalPopup> internalPopups = new ArrayList<>();

    public DeepOperationsScreen() {
        super(Component.translatable("gui.tac_rogue.deep.title"));
    }

    public static void setLastCacheResult(String name, String categoryKey, String meta) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof DeepOperationsScreen screen)) return;
        lastCacheResult = new CacheResult(name, categoryKey, meta);
        String metaText = Component.translatable(categoryKey).getString()
            + (meta == null || meta.isBlank() ? "" : " / " + meta);
        screen.addInternalPopup(Component.translatable("gui.tac_rogue.deep.cache.result.last"),
            Component.literal(name + "  " + metaText), 0xFFFFD166, 120);
    }

    public static boolean offerDeepPopup(String type, Component title, Component body, int color, int durationTicks) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof DeepOperationsScreen screen)) return false;
        String titleText = title == null ? "" : title.getString();
        String deepTitle = Component.translatable("popup.tac_rogue.deep.title").getString();
        String screenTitle = Component.translatable("gui.tac_rogue.deep.title").getString();
        if (!titleText.equals(deepTitle) && !titleText.equals(screenTitle)) return false;
        screen.addInternalPopup(title, body, color, durationTicks);
        return true;
    }

    @Override
    public void removed() {
        lastCacheResult = null;
        internalPopups.clear();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        internalPopups.removeIf(InternalPopup::tickExpired);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.panelW = Math.min(this.width - 16, 760);
        this.panelH = Math.min(this.height - 16, 430);
        this.panelX = (this.width - panelW) / 2;
        this.panelY = (this.height - panelH) / 2;
        this.contentX = panelX + 16;
        this.contentY = panelY + 58;
        this.contentW = panelW - 32;
        this.contentH = Math.max(54, panelH - 92 - POPUP_LANE_HEIGHT);

        int tabW = Math.max(64, Math.min(104, (panelW - 40) / Tab.values().length));
        int x = panelX + 16;
        for (Tab value : Tab.values()) {
            addRenderableWidget(Button.builder(Component.translatable(value.key()), b -> {
                tab = value;
                pageScroll = 0;
                init();
            }).bounds(x, panelY + 28, tabW, 20).build());
            x += tabW + 4;
        }

        if (tab == Tab.CACHE) {
            addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.deep.cache.open"), b ->
                send(DeepProgressService.ACTION_CACHE, -1, -1, "")
            ).bounds(contentX, panelY + panelH - 28, Math.min(180, contentW), 20).build());
        } else if (tab == Tab.MODIFIER) {
            layoutModifierButtons();
        } else if (tab == Tab.PRESTIGE) {
            layoutPrestigeButtons();
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.common.close"), b -> onClose())
            .bounds(panelX + panelW - 92, panelY + panelH - 28, 76, 20).build());
    }

    private void layoutModifierButtons() {
        int modeW = Math.max(70, Math.min(108, (contentW - 16) / ModifierMode.values().length));
        int x = contentX;
        for (ModifierMode mode : ModifierMode.values()) {
            addRenderableWidget(Button.builder(Component.translatable(mode.key()), b -> {
                modifierMode = mode;
                init();
            }).bounds(x, contentY, modeW, 18).build());
            x += modeW + 4;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.deep.modifier.execute"), b ->
            send(modifierMode.action, selectedGunSlot, selectedTokenSlot, "")
        ).bounds(contentX, panelY + panelH - 28, Math.min(180, contentW), 20).build());
    }

    private void layoutPrestigeButtons() {
        int buttonW = Math.max(58, Math.min(92, contentW / 5));
        addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.deep.prestige.execute"), b ->
            send(DeepProgressService.ACTION_PRESTIGE, -1, -1, "")
        ).bounds(contentX, panelY + panelH - 28, Math.max(80, buttonW), 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TacticalScreenStyle.renderBackground(graphics, this.width, this.height);
        TacticalScreenStyle.drawPanel(graphics, panelX, panelY, panelX + panelW, panelY + panelH);
        TacticalScreenStyle.drawFrame(graphics, panelX, panelY, panelX + panelW, panelY + panelH);
        graphics.drawString(this.font, this.title, panelX + 18, panelY + 12, 0xFFE8FFF8, false);
        graphics.drawString(this.font,
            Component.translatable("gui.tac_rogue.deep.summary",
                ClientRunState.getDeepCore(),
                ClientRunState.getPrestigeLevel(),
                ClientRunState.getHighestEverFloor()),
            panelX + 190, panelY + 12, 0xFF74DDBE, false);

        switch (tab) {
            case CACHE -> renderCache(graphics);
            case MODIFIER -> renderModifier(graphics, mouseX, mouseY);
            case PRESTIGE -> renderPrestige(graphics);
            case INFO -> renderInfo(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        for (var child : this.renderables) {
            if (child instanceof Button button) {
                TacticalScreenStyle.drawButton(graphics, this.font, button);
            }
        }
        renderInternalPopups(graphics);
    }

    private void addInternalPopup(Component title, Component body, int color, int durationTicks) {
        internalPopups.add(0, new InternalPopup(title, body, color, Math.max(60, Math.min(200, durationTicks))));
        while (internalPopups.size() > MAX_INTERNAL_POPUPS) {
            internalPopups.remove(internalPopups.size() - 1);
        }
    }

    private void renderInternalPopups(GuiGraphics graphics) {
        if (internalPopups.isEmpty()) return;
        int toastW = Math.min(260, Math.max(180, panelW - 32));
        int x = panelX + panelW - toastW - 16;
        int bottom = panelY + panelH - 36;
        int visible = bottom - 76 >= contentY ? internalPopups.size() : 1;
        for (int i = 0; i < visible; i++) {
            InternalPopup popup = internalPopups.get(i);
            int alpha = popup.alpha();
            int bg = (alpha << 24) | 0x0005090B;
            int border = (Math.min(255, alpha + 40) << 24) | (popup.color() & 0x00FFFFFF);
            int yy = Math.max(contentY, bottom - 36 - i * 40);
            graphics.fill(x, yy, x + toastW, yy + 36, bg);
            graphics.fill(x, yy, x + toastW, yy + 2, border);
            graphics.fill(x, yy, x + 2, yy + 36, border);
            graphics.drawString(this.font, TacticalScreenStyle.fitLabel(this.font, popup.title().getString(), toastW - 14),
                x + 7, yy + 6, (alpha << 24) | 0x00E8FFF8, false);
            graphics.drawString(this.font, TacticalScreenStyle.fitLabel(this.font, popup.body().getString(), toastW - 14),
                x + 7, yy + 19, (alpha << 24) | 0x00AAB7BF, false);
        }
    }

    private void renderCache(GuiGraphics graphics) {
        pageScroll = clamp(pageScroll, 0, maxPageScroll());
        graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        int y = contentY - pageScroll;
        drawWrapped(graphics, Component.translatable("gui.tac_rogue.deep.cache.body"), contentX, y, contentW, 0xFFE8FFF8, 12);
        y += 78;
        drawLine(graphics, y, "gui.tac_rogue.deep.cache.cost", String.valueOf(DeepProgressService.deepCacheCost()), 0xFF66F5FF);
        y += 16;
        drawWrapped(graphics, Component.translatable("gui.tac_rogue.deep.cache.pool"), contentX, y, contentW, 0xFFAAB7BF, 8);
        if (lastCacheResult != null) {
            int resultY = y + 58;
            TacticalScreenStyle.drawPanel(graphics, contentX, resultY, contentX + contentW, resultY + 46);
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.deep.cache.result.last"), contentX + 8, resultY + 7, 0xFFFFD166, false);
            graphics.drawString(this.font, TacticalScreenStyle.fitLabel(this.font, lastCacheResult.name(), contentW - 18), contentX + 8, resultY + 20, 0xFFE8FFF8, false);
            String meta = Component.translatable(lastCacheResult.categoryKey()).getString()
                + (lastCacheResult.meta().isBlank() ? "" : " / " + lastCacheResult.meta());
            graphics.drawString(this.font, TacticalScreenStyle.fitLabel(this.font, meta, contentW - 18), contentX + 8, resultY + 32, 0xFF74DDBE, false);
        }
        graphics.disableScissor();
    }

    private void renderModifier(GuiGraphics graphics, int mouseX, int mouseY) {
        List<InventoryEntry> guns = collectGuns();
        List<InventoryEntry> tokens = collectTokens();
        int listY = contentY + 26;
        boolean compact = compactLayout();
        int detailX;
        int detailY;
        int detailW;

        if (compact) {
            int sectionGap = 14;
            int listH = modifierMode == ModifierMode.INFUSE ? Math.max(44, (contentH - 82) / 3) : Math.max(54, (contentH - 60) / 2);
            int gunRows = rowsFor(listH);
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.deep.modifier.guns"), contentX, listY - 12, 0xFF74DDBE, false);
            drawClippedEntries(graphics, guns, selectedGunSlot, contentX, listY, contentW, gunRows, gunScroll);
            int nextY = listY + gunRows * 22 + sectionGap;
            if (modifierMode == ModifierMode.INFUSE) {
                int tokenRows = rowsFor(listH);
                graphics.drawString(this.font, Component.translatable("gui.tac_rogue.deep.modifier.tokens"), contentX, nextY - 12, 0xFF74DDBE, false);
                drawClippedEntries(graphics, tokens, selectedTokenSlot, contentX, nextY, contentW, tokenRows, tokenScroll);
                nextY += tokenRows * 22 + sectionGap;
            }
            detailX = contentX;
            detailY = nextY;
            detailW = contentW;
        } else {
            int gunW = modifierMode == ModifierMode.INFUSE ? contentW / 3 : contentW / 2 - 8;
            int tokenX = contentX + gunW + 10;
            int tokenW = modifierMode == ModifierMode.INFUSE ? contentW / 3 - 8 : 0;
            detailX = modifierMode == ModifierMode.INFUSE ? tokenX + tokenW + 12 : contentX + gunW + 16;
            detailY = listY + 4;
            detailW = Math.max(120, contentX + contentW - detailX);
            int rows = rowsFor(panelY + panelH - 42 - listY);

            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.deep.modifier.guns"), contentX, listY - 12, 0xFF74DDBE, false);
            drawClippedEntries(graphics, guns, selectedGunSlot, contentX, listY, gunW, rows, gunScroll);
            if (modifierMode == ModifierMode.INFUSE) {
                graphics.drawString(this.font, Component.translatable("gui.tac_rogue.deep.modifier.tokens"), tokenX, listY - 12, 0xFF74DDBE, false);
                drawClippedEntries(graphics, tokens, selectedTokenSlot, tokenX, listY, tokenW, rows, tokenScroll);
            }
        }

        graphics.drawString(this.font, Component.translatable(modifierMode.key()), detailX, detailY - 12, 0xFFFFD166, false);
        ItemStack gun = stackAt(selectedGunSlot);
        int y = detailY + 4;
        drawWrapped(graphics, Component.translatable(modifierMode.descKey()), detailX, y, detailW, 0xFFE8FFF8, compact ? 3 : 5);
        y += compact ? 40 : 62;
        int cost = modifierMode.cost(gun);
        if (cost >= 0) {
            drawLine(graphics, detailX, y, "gui.tac_rogue.deep.modifier.cost", String.valueOf(cost), 0xFF66F5FF);
            y += 16;
        }
        if (!gun.isEmpty()) {
            DeepProgressService.DeepModifier modifier = DeepProgressService.getModifier(gun);
            drawLine(graphics, detailX, y, "gui.tac_rogue.deep.modifier.selected_gun", gun.getHoverName().getString(), 0xFFFFFFFF);
            y += 16;
            drawLine(graphics, detailX, y, "gui.tac_rogue.deep.modifier.current", modifier == null ? "-" : Component.translatable(modifier.langKey()).getString(), 0xFFAAFFCC);
            y += 16;
            drawLine(graphics, detailX, y, "gui.tac_rogue.deep.modifier.rarity", WeaponRarity.getRarity(gun).name(), 0xFFFFD166);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.deep.modifier.no_gun"), detailX, y, 0xFF9BA8B0, false);
        }
        if (modifierMode == ModifierMode.INFUSE) {
            ItemStack token = stackAt(selectedTokenSlot);
            DeepProgressService.DeepModifier tokenMod = DeepProgressService.getTokenModifier(token);
            graphics.drawString(this.font,
                Component.translatable("gui.tac_rogue.deep.modifier.selected_token",
                    tokenMod == null ? "-" : Component.translatable(tokenMod.langKey()).getString()),
                detailX, y + 24, 0xFF66F5FF, false);
        }
    }

    private void renderPrestige(GuiGraphics graphics) {
        pageScroll = clamp(pageScroll, 0, maxPageScroll());
        graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        int y = contentY - pageScroll;
        drawWrapped(graphics, Component.translatable("gui.tac_rogue.deep.prestige.body"), contentX, y, contentW, 0xFFE8FFF8, compactLayout() ? 4 : 5);
        int cardStartY = y + (compactLayout() ? 54 : 65);
        int cardH = prestigeCardHeight();
        int buttonW = prestigeButtonWidth();
        int buttonH = 18;
        int levelX = contentX + Math.max(100, contentW - buttonW - 108);
        int descMaxW = Math.max(72, levelX - contentX - 18);
        for (int i = 0; i < PRESTIGE_KEYS.length; i++) {
            String key = PRESTIGE_KEYS[i];
            int level = prestigeLevel(key);
            int cost = DeepProgressService.getPrestigeUnlockCost(level);
            int x = contentX;
            int yy = cardStartY + i * (cardH + 8);
            TacticalScreenStyle.drawPanel(graphics, x, yy, x + contentW, yy + cardH);
            graphics.drawString(this.font, Component.translatable("prestige.tac_rogue." + key), x + 8, yy + 7, 0xFFE8FFF8, false);
            int descY = yy + 20;
            for (var line : this.font.split(Component.translatable("prestige.tac_rogue." + key + ".desc"), descMaxW)) {
                if (descY + 9 > yy + cardH - 5) break;
                graphics.drawString(this.font, line, x + 8, descY, 0xFF9BA8B0, false);
                descY += 10;
            }
            graphics.drawString(this.font,
                Component.translatable("gui.tac_rogue.deep.prestige.level_cost", level, cost < 0 ? "-" : String.valueOf(cost)),
                levelX, yy + 6, 0xFF74DDBE, false);
            int bx = contentX + contentW - buttonW - 8;
            int by = yy + cardH - buttonH - 6;
            drawInlineButton(graphics, bx, by, buttonW, buttonH, Component.translatable("prestige.tac_rogue." + key), by >= contentY && by + buttonH <= contentY + contentH);
        }
        graphics.disableScissor();
    }

    private void renderInfo(GuiGraphics graphics) {
        pageScroll = clamp(pageScroll, 0, maxPageScroll());
        graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        int y = contentY - pageScroll;
        for (int i = 0; i < 8; i++) {
            y = drawWrapped(graphics, Component.translatable("gui.tac_rogue.deep.info." + i), contentX, y, contentW, 0xFFE8FFF8, 12) + 8;
        }
        graphics.disableScissor();
    }

    private void drawClippedEntries(GuiGraphics graphics, List<InventoryEntry> entries, int selectedSlot,
                                    int x, int y, int w, int visibleRows, int scroll) {
        int h = Math.max(22, visibleRows * 22);
        graphics.enableScissor(x, y, x + w, y + h);
        drawEntries(graphics, entries, selectedSlot, x, y, w, visibleRows, scroll);
        graphics.disableScissor();
    }

    private void drawEntries(GuiGraphics graphics, List<InventoryEntry> entries, int selectedSlot,
                             int x, int y, int w, int visibleRows, int scroll) {
        int rowH = 22;
        int max = Math.min(entries.size(), scroll + visibleRows);
        for (int i = scroll; i < max; i++) {
            InventoryEntry entry = entries.get(i);
            int rowY = y + (i - scroll) * rowH;
            int fill = entry.slot == selectedSlot ? 0x8837E7C4 : 0x6607111F;
            graphics.fill(x, rowY, x + w, rowY + 19, fill);
            graphics.renderOutline(x, rowY, w, 19, entry.slot == selectedSlot ? 0xFFE6C76A : 0x6654E7C4);
            if (!TacZGuiIconRenderer.renderLightweightIcon(graphics, this.font, entry.stack, x + 2, rowY + 2)) {
                graphics.renderItem(entry.stack, x + 2, rowY + 2);
            }
            String name = TacticalScreenStyle.fitLabel(this.font, entry.stack.getHoverName().getString(), w - 24);
            graphics.drawString(this.font, name, x + 22, rowY + 6, 0xFFE8FFF8, false);
        }
        if (entries.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.deep.modifier.empty"), x, y + 8, 0xFF9BA8B0, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.MODIFIER && button == 0) {
            List<InventoryEntry> guns = collectGuns();
            List<InventoryEntry> tokens = collectTokens();
            int listY = contentY + 26;
            if (compactLayout()) {
                int listH = modifierMode == ModifierMode.INFUSE ? Math.max(44, (contentH - 82) / 3) : Math.max(54, (contentH - 60) / 2);
                int gunRows = rowsFor(listH);
                int clickedGun = clickedEntry(mouseX, mouseY, guns, contentX, listY, contentW, gunScroll, gunRows);
                if (clickedGun >= 0) {
                    selectedGunSlot = clickedGun;
                    return true;
                }
                if (modifierMode == ModifierMode.INFUSE) {
                    int tokenY = listY + gunRows * 22 + 14;
                    int tokenRows = rowsFor(listH);
                    int clickedToken = clickedEntry(mouseX, mouseY, tokens, contentX, tokenY, contentW, tokenScroll, tokenRows);
                    if (clickedToken >= 0) {
                        selectedTokenSlot = clickedToken;
                        return true;
                    }
                }
            } else {
                int gunW = modifierMode == ModifierMode.INFUSE ? contentW / 3 : contentW / 2 - 8;
                int tokenX = contentX + gunW + 10;
                int tokenW = modifierMode == ModifierMode.INFUSE ? contentW / 3 - 8 : 0;
                int rows = rowsFor(panelY + panelH - 42 - listY);
                int clickedGun = clickedEntry(mouseX, mouseY, guns, contentX, listY, gunW, gunScroll, rows);
                if (clickedGun >= 0) {
                    selectedGunSlot = clickedGun;
                    return true;
                }
                if (modifierMode == ModifierMode.INFUSE) {
                    int clickedToken = clickedEntry(mouseX, mouseY, tokens, tokenX, listY, tokenW, tokenScroll, rows);
                    if (clickedToken >= 0) {
                        selectedTokenSlot = clickedToken;
                        return true;
                    }
                }
            }
        }
        if (tab == Tab.PRESTIGE && button == 0 && inside(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            String key = clickedPrestigeUpgrade(mouseX, mouseY);
            if (key != null) {
                send(DeepProgressService.ACTION_PRESTIGE_UNLOCK, -1, -1, key);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if ((tab == Tab.CACHE || tab == Tab.INFO || tab == Tab.PRESTIGE) && inside(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            pageScroll = clamp(pageScroll - (int)Math.signum(delta) * 18, 0, maxPageScroll());
            return true;
        }
        if (tab == Tab.MODIFIER) {
            List<InventoryEntry> guns = collectGuns();
            List<InventoryEntry> tokens = collectTokens();
            int listY = contentY + 26;
            if (compactLayout()) {
                int listH = modifierMode == ModifierMode.INFUSE ? Math.max(44, (contentH - 82) / 3) : Math.max(54, (contentH - 60) / 2);
                int gunRows = rowsFor(listH);
                if (inside(mouseX, mouseY, contentX, listY, contentW, gunRows * 22)) {
                    gunScroll = clamp(gunScroll - (int)Math.signum(delta), 0, Math.max(0, guns.size() - gunRows));
                    return true;
                }
                if (modifierMode == ModifierMode.INFUSE) {
                    int tokenY = listY + gunRows * 22 + 14;
                    int tokenRows = rowsFor(listH);
                    if (inside(mouseX, mouseY, contentX, tokenY, contentW, tokenRows * 22)) {
                        tokenScroll = clamp(tokenScroll - (int)Math.signum(delta), 0, Math.max(0, tokens.size() - tokenRows));
                        return true;
                    }
                }
            } else {
                int gunW = modifierMode == ModifierMode.INFUSE ? contentW / 3 : contentW / 2 - 8;
                int tokenX = contentX + gunW + 10;
                int tokenW = modifierMode == ModifierMode.INFUSE ? contentW / 3 - 8 : 0;
                int rows = rowsFor(panelY + panelH - 42 - listY);
                if (inside(mouseX, mouseY, contentX, listY, gunW, rows * 22)) {
                    gunScroll = clamp(gunScroll - (int)Math.signum(delta), 0, Math.max(0, guns.size() - rows));
                    return true;
                }
                if (modifierMode == ModifierMode.INFUSE && inside(mouseX, mouseY, tokenX, listY, tokenW, rows * 22)) {
                    tokenScroll = clamp(tokenScroll - (int)Math.signum(delta), 0, Math.max(0, tokens.size() - rows));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int clickedEntry(double mouseX, double mouseY, List<InventoryEntry> entries, int x, int y, int w, int scroll, int visibleRows) {
        if (!inside(mouseX, mouseY, x, y, w, visibleRows * 22)) return -1;
        int index = scroll + (int)((mouseY - y) / 22);
        return index >= 0 && index < entries.size() ? entries.get(index).slot : -1;
    }

    private List<InventoryEntry> collectGuns() {
        List<InventoryEntry> entries = new ArrayList<>();
        if (minecraft == null || minecraft.player == null) return entries;
        var inv = minecraft.player.getInventory().items;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.get(i);
            if (DeepProgressService.isDeepGun(stack)) entries.add(new InventoryEntry(i, stack));
        }
        return entries;
    }

    private List<InventoryEntry> collectTokens() {
        List<InventoryEntry> entries = new ArrayList<>();
        if (minecraft == null || minecraft.player == null) return entries;
        var inv = minecraft.player.getInventory().items;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.get(i);
            if (DeepProgressService.getTokenModifier(stack) != null) entries.add(new InventoryEntry(i, stack));
        }
        return entries;
    }

    private ItemStack stackAt(int slot) {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        var inv = minecraft.player.getInventory().items;
        if (slot < 0 || slot >= inv.size()) return ItemStack.EMPTY;
        return inv.get(slot);
    }

    private int prestigeLevel(String key) {
        return switch (key) {
            case "provision" -> ClientRunState.getPrestigeProvision();
            case "prepared" -> ClientRunState.getPrestigePrepared();
            case "selection" -> ClientRunState.getPrestigeSelection();
            case "supply_line" -> ClientRunState.getPrestigeSupplyLine();
            case "black_market" -> ClientRunState.getPrestigeBlackMarket();
            default -> 0;
        };
    }

    private void send(String action, int gunSlot, int tokenSlot, String upgradeKey) {
        TacRogueNetworking.CHANNEL.sendToServer(new DeepOperationMessage(action, gunSlot, tokenSlot, upgradeKey));
    }

    private void drawLine(GuiGraphics graphics, int y, String labelKey, String value, int color) {
        drawLine(graphics, contentX, y, labelKey, value, color);
    }

    private void drawLine(GuiGraphics graphics, int x, int y, String labelKey, String value, int color) {
        graphics.drawString(this.font, Component.translatable(labelKey), x, y, 0xFF9BA8B0, false);
        graphics.drawString(this.font, value, x + 92, y, color, false);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color, int maxLines) {
        int lines = 0;
        for (var line : this.font.split(text, width)) {
            if (lines >= maxLines) break;
            graphics.drawString(this.font, line, x, y, color, false);
            y += 11;
            lines++;
        }
        return y;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean compactLayout() {
        return contentW < 520 || contentH < 260;
    }

    private int maxPageScroll() {
        int contentHeight = switch (tab) {
            case CACHE -> lastCacheResult == null ? 170 : 232;
            case INFO -> 8 * 52;
            case PRESTIGE -> 70 + PRESTIGE_KEYS.length * (prestigeCardHeight() + 8);
            default -> 0;
        };
        return Math.max(0, contentHeight - contentH);
    }

    private int prestigeCardHeight() {
        return compactLayout() ? 58 : 66;
    }

    private int prestigeButtonWidth() {
        return Math.max(64, Math.min(104, contentW / 4));
    }

    private String clickedPrestigeUpgrade(double mouseX, double mouseY) {
        int y = contentY - pageScroll;
        int afterIntro = y + (compactLayout() ? 54 : 65);
        int cardH = prestigeCardHeight();
        int buttonW = prestigeButtonWidth();
        int buttonH = 18;
        int bx = contentX + contentW - buttonW - 8;
        for (int i = 0; i < PRESTIGE_KEYS.length; i++) {
            int yy = afterIntro + i * (cardH + 8);
            int by = yy + cardH - buttonH - 6;
            if (inside(mouseX, mouseY, bx, by, buttonW, buttonH)) return PRESTIGE_KEYS[i];
        }
        return null;
    }

    private void drawInlineButton(GuiGraphics graphics, int x, int y, int w, int h, Component label, boolean enabled) {
        if (!enabled) return;
        int bg = 0xAA06141A;
        int border = enabled ? 0xFF54E7C4 : 0x66788888;
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.renderOutline(x, y, w, h, border);
        graphics.drawCenteredString(this.font, TacticalScreenStyle.fitLabel(this.font, label.getString(), w - 8),
            x + w / 2, y + 5, enabled ? 0xFFE8FFF8 : 0xFF777777);
    }

    private static int rowsFor(int availableHeight) {
        return clamp(availableHeight / 22, 2, 9);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Tab {
        CACHE,
        MODIFIER,
        PRESTIGE,
        INFO;

        String key() {
            return "gui.tac_rogue.deep.tab." + name().toLowerCase(Locale.ROOT);
        }
    }

    private enum ModifierMode {
        REFORGE(DeepProgressService.ACTION_REFORGE),
        LOCK(DeepProgressService.ACTION_LOCK),
        EXTRACT(DeepProgressService.ACTION_EXTRACT),
        INFUSE(DeepProgressService.ACTION_INFUSE),
        DISMANTLE(DeepProgressService.ACTION_DISMANTLE);

        final String action;

        ModifierMode(String action) {
            this.action = action;
        }

        String key() {
            return "gui.tac_rogue.deep.modifier." + name().toLowerCase(Locale.ROOT);
        }

        String descKey() {
            return key() + ".desc";
        }

        int cost(ItemStack gun) {
            return switch (this) {
                case REFORGE -> DeepProgressService.getReforgeCost(gun);
                case LOCK -> DeepProgressService.lockCost();
                case EXTRACT -> DeepProgressService.extractCost();
                case INFUSE -> DeepProgressService.infuseCost();
                case DISMANTLE -> -1;
            };
        }
    }

    private record InventoryEntry(int slot, ItemStack stack) {}

    private record CacheResult(String name, String categoryKey, String meta) {}

    private static final class InternalPopup {
        private final Component title;
        private final Component body;
        private final int color;
        private final int durationTicks;
        private int age;

        private InternalPopup(Component title, Component body, int color, int durationTicks) {
            this.title = title;
            this.body = body;
            this.color = color;
            this.durationTicks = durationTicks;
        }

        boolean tickExpired() {
            age++;
            return age >= durationTicks;
        }

        int alpha() {
            int remaining = durationTicks - age;
            int fade = Math.min(Math.min(age + 1, remaining), 20);
            return clamp(80 + fade * 8, 90, 230);
        }

        Component title() { return title; }
        Component body() { return body; }
        int color() { return color; }
    }
}
