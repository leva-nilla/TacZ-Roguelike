package com.levanilla.rogue.client;

import com.levanilla.rogue.core.PriceManager;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.ShopStockManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.core.registry.TacZGunRegistry;
import com.levanilla.rogue.core.service.RogueItemFactory;
import com.levanilla.rogue.core.service.ShopService;
import com.levanilla.rogue.networking.OpenShopMessage;
import com.levanilla.rogue.networking.RogueActionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class ShopScreen extends Screen {
    private static final int SLOT = 22;
    private static final int COLS = 8;
    private static final int ROWS = 5;
    private static final int PAGE_SIZE = COLS * ROWS;
    private static final int CATEGORY_W = 78;
    private static final int CATEGORY_GAP = 14;

    private ShopCatalog.Category categoryFilter = null;
    private boolean sellMode = false;
    private int page = 0;
    private int selectedIndex = -1;
    private int selectedSellSlot = -1;
    private int hoveredIndex = -1;
    private int categoryScroll = 0;
    private List<ShopCatalog.ShopItem> cachedVisibleItems = List.of();
    private List<ShopCatalog.ShopItem> cachedVisibleSource = null;
    private ShopCatalog.Category cachedVisibleCategory = null;
    private int cachedVisibleShopFloor = Integer.MIN_VALUE;
    private int cachedVisibleBlackMarket = Integer.MIN_VALUE;
    private final Map<String, ItemStack> previewStackCache = new HashMap<>();
    private int previewStackShopFloor = Integer.MIN_VALUE;
    private List<SellEntry> cachedSellEntries = List.of();
    private List<OpenShopMessage.StashEntry> stashSellEntries = List.of();
    private String cachedSellInventorySignature = "";
    private List<String> cachedHeldGunIds = List.of();
    private String cachedHeldGunSignature = "";
    private final Map<String, Compatibility> compatibilityCache = new HashMap<>();
    private long lastGridClickMillis = 0L;
    private int lastGridClickKey = Integer.MIN_VALUE;
    private boolean lastGridClickSellMode = false;

    public ShopScreen() {
        this(List.of());
    }

    public ShopScreen(List<OpenShopMessage.StashEntry> stashEntries) {
        super(Component.translatable("gui.tac_rogue.shop_screen.title"));
        this.stashSellEntries = List.copyOf(stashEntries);
    }

    public void updateStashEntries(List<OpenShopMessage.StashEntry> stashEntries) {
        this.stashSellEntries = List.copyOf(stashEntries);
        this.cachedSellInventorySignature = "";
        this.cachedSellEntries = List.of();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int panelW = Math.min(560, this.width - 24);
        int panelH = Math.min(244, this.height - 24);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable(sellMode ? "gui.tac_rogue.shop_screen.mode_buy" : "gui.tac_rogue.shop_screen.mode_sell"), b -> {
            sellMode = !sellMode;
            page = 0;
            selectedIndex = -1;
            selectedSellSlot = -1;
            clearDoubleClickState();
            this.init();
        }).bounds(x + panelW - 134, y + 10, 48, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.common.close_short"), b -> this.onClose())
            .bounds(x + panelW - 60, y + 10, 24, 18).build());

        if (sellMode) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.shop_screen.sell_selected"), b -> sellSelected())
                .bounds(x + panelW - 132, y + panelH - 30, 120, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.shop_screen.buy_selected"), b -> buySelected())
                .bounds(x + panelW - 132, y + panelH - 30, 120, 20).build());
        }

        ShopCatalog.Category[] cats = ShopCatalog.Category.values();
        int visibleCategories = visibleCategoryRows(panelH);
        int maxCategoryScroll = Math.max(0, cats.length + 1 - visibleCategories);
        categoryScroll = Math.max(0, Math.min(categoryScroll, maxCategoryScroll));
        int catX = x + 10;
        int catY = y + 48;
        for (int row = 0; row < visibleCategories; row++) {
            int idx = categoryScroll + row;
            if (idx > cats.length) break;
            ShopCatalog.Category cat = idx == 0 ? null : cats[idx - 1];
            Component label = categoryLabel(cat);
            this.addRenderableWidget(Button.builder(label, b -> {
                categoryFilter = cat;
                page = 0;
                selectedIndex = -1;
                clearDoubleClickState();
                this.init();
            }).bounds(catX, catY + row * 18, CATEGORY_W - 4, 16).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelW = Math.min(560, this.width - 24);
        int panelH = Math.min(244, this.height - 24);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int gridX = x + 10 + CATEGORY_W + CATEGORY_GAP;
        int gridY = y + 54;
        int detailX = gridX + COLS * SLOT + 14;
        int detailW = x + panelW - detailX - 10;

        graphics.fill(x, y, x + panelW, y + panelH, 0xEA08111C);
        graphics.fill(x, y, x + panelW, y + 2, 0xFF55DDAA);
        graphics.renderOutline(x, y, panelW, panelH, 0xAA55DDAA);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.shop_screen.title"), x + 12, y + 12, 0xFFAAFFDD, false);
        graphics.drawString(this.font, "$" + RunManager.getClientGold(), x + 140, y + 12, 0xFFFFD45C, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.shop_screen.category"), x + 12, y + 30, 0xFF7FDDBB, false);
        renderCategoryPanel(graphics, x, y, panelH);
        graphics.drawString(this.font, sellMode ? Component.translatable("gui.tac_rogue.shop_screen.sell_inventory")
                : categoryFilter == null ? Component.translatable("gui.tac_rogue.shop_screen.all_stock") : categoryLabel(categoryFilter),
            gridX, y + 28, 0xFFCCCCCC, false);

        if (sellMode) {
            refreshSellEntries();
        } else {
            visibleItems();
            refreshHeldGunCache();
        }
        hoveredIndex = -1;
        if (sellMode) {
            renderSellGrid(graphics, mouseX, mouseY, gridX, gridY);
        } else {
            renderBuyGrid(graphics, mouseX, mouseY, gridX, gridY);
        }

        renderDetail(graphics, detailX, gridY, detailW, panelH - 92);
        renderPageControls(graphics, x, y, panelW, panelH);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    private void renderBuyGrid(GuiGraphics graphics, int mouseX, int mouseY, int gridX, int gridY) {
        List<ShopCatalog.ShopItem> items = visibleItems();
        int maxPage = maxPage(items.size());
        if (page > maxPage) page = maxPage;
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            int col = i % COLS;
            int row = i / COLS;
            int sx = gridX + col * SLOT;
            int sy = gridY + row * SLOT;
            boolean hovered = mouseX >= sx && mouseX < sx + 20 && mouseY >= sy && mouseY < sy + 20;
            boolean selected = idx == selectedIndex;
            Compatibility compatibility = idx < items.size() ? compatibilityFor(items.get(idx)) : Compatibility.NONE;
            int rarityFill = idx < items.size() ? rarityBackground(items.get(idx)) : 0x55000000;
            graphics.fill(sx, sy, sx + 20, sy + 20, rarityFill);
            if (selected || hovered || compatibility.compatible) {
                graphics.fill(sx, sy, sx + 20, sy + 20, selected ? 0x7744FFAA : hovered ? 0x5544AAFF : 0x4422AA55);
            }
            graphics.renderOutline(sx, sy, 20, 20, selected ? 0xFF55FFAA : compatibility.compatible ? 0xAA55FFAA : 0x66336655);
            if (idx >= items.size()) continue;
            if (hovered) hoveredIndex = idx;
            ItemStack stack = previewStack(items.get(idx));
            if (!stack.isEmpty()) {
                if (!TacZGuiIconRenderer.renderLightweightIcon(graphics, this.font, stack, sx + 2, sy + 2)) {
                    graphics.renderItem(stack, sx + 2, sy + 2);
                    graphics.renderItemDecorations(this.font, stack, sx + 2, sy + 2);
                }
                if (isRarityShopWeapon(items.get(idx).category)) {
                    int color = WeaponRarity.getRarity(stack).color;
                    graphics.fill(sx + 2, sy + 18, sx + 18, sy + 20, color);
                }
            } else {
                graphics.drawCenteredString(this.font, "?", sx + 10, sy + 6, 0xFF777777);
            }
        }
    }

    private void renderSellGrid(GuiGraphics graphics, int mouseX, int mouseY, int gridX, int gridY) {
        List<SellEntry> entries = sellEntries();
        int maxPage = maxPage(entries.size());
        if (page > maxPage) page = maxPage;
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = start + i;
            int col = i % COLS;
            int row = i / COLS;
            int sx = gridX + col * SLOT;
            int sy = gridY + row * SLOT;
            boolean hovered = mouseX >= sx && mouseX < sx + 20 && mouseY >= sy && mouseY < sy + 20;
            boolean selected = idx < entries.size() && entries.get(idx).slot == selectedSellSlot;
            graphics.fill(sx, sy, sx + 20, sy + 20, selected ? 0x77FF7755 : hovered ? 0x5544AAFF : 0x55000000);
            graphics.renderOutline(sx, sy, 20, 20, selected ? 0xFFFF7755 : 0x66336655);
            if (idx >= entries.size()) continue;
            if (hovered) hoveredIndex = idx;
            ItemStack stack = entries.get(idx).stack;
            if (!TacZGuiIconRenderer.renderLightweightIcon(graphics, this.font, stack, sx + 2, sy + 2)) {
                graphics.renderItem(stack, sx + 2, sy + 2);
                graphics.renderItemDecorations(this.font, stack, sx + 2, sy + 2);
            }
        }
    }

    private void renderDetail(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x55000000);
        graphics.renderOutline(x, y, w, h, 0x66336655);
        if (sellMode) {
            List<SellEntry> entries = sellEntries();
            SellEntry entry = entries.stream().filter(e -> e.slot == selectedSellSlot).findFirst().orElse(null);
            if (entry == null) {
                graphics.drawString(this.font, Component.translatable("gui.tac_rogue.shop_screen.select_item"), x + 8, y + 8, 0xFF777777, false);
                return;
            }
            drawWrapped(graphics, entry.stack.getHoverName().getString(), x + 8, y + 8, w - 16, 0xFFFFFFFF);
            graphics.drawString(this.font, entry.stash()
                ? Component.literal("STASH")
                : Component.literal("INVENTORY"), x + 8, y + 30, entry.stash() ? 0xFF66DDAA : 0xFF99AABB, false);
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.shop_screen.sell_price", entry.price), x + 8, y + 42, 0xFFFFAA66, false);
            return;
        }

        List<ShopCatalog.ShopItem> items = visibleItems();
        if (selectedIndex < 0 || selectedIndex >= items.size()) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.shop_screen.select_stock"), x + 8, y + 8, 0xFF777777, false);
            return;
        }
        ShopCatalog.ShopItem item = items.get(selectedIndex);
        drawWrapped(graphics, shopItemName(item).getString(), x + 8, y + 8, w - 16, 0xFFFFFFFF);
        graphics.drawString(this.font, categoryLabel(item.category), x + 8, y + 42, item.category.color, false);
        graphics.drawString(this.font, "$" + actualPrice(item), x + 8, y + 56,
            RunManager.getClientGold() >= actualPrice(item) ? 0xFFFFD45C : 0xFFFF6666, false);
        int lineY = y + 72;
        for (Component line : statLines(item)) {
            if (lineY > y + h - 10) break;
            graphics.drawString(this.font, line, x + 8, lineY, 0xFFAAFFDD, false);
            lineY += 10;
        }
        Compatibility compatibility = compatibilityFor(item);
        if (compatibility.relevant) {
            int compatY = Math.min(lineY + 2, y + h - 10);
            graphics.drawString(this.font,
                compatibility.compatible
                    ? Component.translatable("gui.tac_rogue.shop_screen.compatible", compatibility.gunName)
                    : Component.translatable("gui.tac_rogue.shop_screen.no_matching_gun"),
                x + 8, compatY, compatibility.compatible ? 0xFF66FFAA : 0xFFFF7777, false);
        }
    }

    private void renderPageControls(GuiGraphics graphics, int x, int y, int panelW, int panelH) {
        int total = sellMode ? sellEntries().size() : visibleItems().size();
        int max = maxPage(total);
        int cy = y + panelH - 28;
        graphics.fill(x + panelW / 2 - 54, cy, x + panelW / 2 - 14, cy + 18, page > 0 ? 0x6644AAFF : 0x33222222);
        graphics.fill(x + panelW / 2 + 14, cy, x + panelW / 2 + 54, cy + 18, page < max ? 0x6644AAFF : 0x33222222);
        graphics.renderOutline(x + panelW / 2 - 54, cy, 40, 18, 0x66336655);
        graphics.renderOutline(x + panelW / 2 + 14, cy, 40, 18, 0x66336655);
        graphics.drawCenteredString(this.font, "<", x + panelW / 2 - 34, cy + 5, 0xFFCCCCCC);
        graphics.drawCenteredString(this.font, ">", x + panelW / 2 + 34, cy + 5, 0xFFCCCCCC);
        graphics.drawCenteredString(this.font, (page + 1) + "/" + (max + 1), x + panelW / 2, y + panelH - 22, 0xFF888888);
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredIndex < 0) return;
        if (sellMode) {
            List<SellEntry> entries = sellEntries();
            if (hoveredIndex < entries.size()) graphics.renderTooltip(this.font, entries.get(hoveredIndex).stack, mouseX, mouseY);
            return;
        }
        List<ShopCatalog.ShopItem> items = visibleItems();
        if (hoveredIndex >= items.size()) return;
        ShopCatalog.ShopItem item = items.get(hoveredIndex);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(shopItemName(item));
        tooltip.add(Component.empty().append(categoryLabel(item.category)).append(Component.literal("  $" + actualPrice(item))));
        tooltip.addAll(statLines(item));
        Compatibility compatibility = compatibilityFor(item);
        if (compatibility.relevant) {
            tooltip.add(compatibility.compatible
                ? Component.translatable("gui.tac_rogue.shop_screen.compatible", compatibility.gunName)
                : Component.translatable("gui.tac_rogue.shop_screen.no_matching_gun_inventory"));
        }
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = Math.min(560, this.width - 24);
        int panelH = Math.min(244, this.height - 24);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int gridX = x + 10 + CATEGORY_W + CATEGORY_GAP;
        int gridY = y + 54;
        int gridW = COLS * SLOT;
        int gridH = ROWS * SLOT;
        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            int col = ((int)mouseX - gridX) / SLOT;
            int row = ((int)mouseY - gridY) / SLOT;
            int idx = page * PAGE_SIZE + row * COLS + col;
            boolean validCell = false;
            int clickKey = idx;
            if (sellMode) {
                List<SellEntry> entries = sellEntries();
                if (idx < entries.size()) {
                    selectedSellSlot = entries.get(idx).slot;
                    clickKey = selectedSellSlot;
                    validCell = true;
                }
            } else if (idx < visibleItems().size()) {
                selectedIndex = idx;
                validCell = true;
            }
            if (validCell && button == 0 && isDoubleGridClick(clickKey)) {
                if (sellMode) {
                    sellSelected();
                } else {
                    buySelected();
                }
                clearDoubleClickState();
            } else if (validCell && button == 0) {
                rememberGridClick(clickKey);
            } else {
                clearDoubleClickState();
            }
            return true;
        }
        if (mouseX >= x + panelW / 2 - 54 && mouseX < x + panelW / 2 - 14 && mouseY >= y + panelH - 28 && mouseY < y + panelH - 10) {
            if (page > 0) {
                page--;
                clearDoubleClickState();
            }
            return true;
        }
        if (mouseX >= x + panelW / 2 + 14 && mouseX < x + panelW / 2 + 54 && mouseY >= y + panelH - 28 && mouseY < y + panelH - 10) {
            int max = maxPage(sellMode ? sellEntries().size() : visibleItems().size());
            if (page < max) {
                page++;
                clearDoubleClickState();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isDoubleGridClick(int clickKey) {
        long now = System.currentTimeMillis();
        return lastGridClickSellMode == sellMode
            && lastGridClickKey == clickKey
            && now - lastGridClickMillis <= 250L;
    }

    private void rememberGridClick(int clickKey) {
        lastGridClickSellMode = sellMode;
        lastGridClickKey = clickKey;
        lastGridClickMillis = System.currentTimeMillis();
    }

    private void clearDoubleClickState() {
        lastGridClickMillis = 0L;
        lastGridClickKey = Integer.MIN_VALUE;
        lastGridClickSellMode = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int panelW = Math.min(560, this.width - 24);
        int panelH = Math.min(244, this.height - 24);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        if (!sellMode && mouseX >= x + 8 && mouseX < x + 10 + CATEGORY_W && mouseY >= y + 46 && mouseY < y + panelH - 42) {
            int maxCategoryScroll = Math.max(0, ShopCatalog.Category.values().length + 1 - visibleCategoryRows(panelH));
            if (delta < 0 && categoryScroll < maxCategoryScroll) {
                categoryScroll++;
                this.init();
                return true;
            }
            if (delta > 0 && categoryScroll > 0) {
                categoryScroll--;
                this.init();
                return true;
            }
        }
        int max = maxPage(sellMode ? sellEntries().size() : visibleItems().size());
        if (delta < 0 && page < max) page++;
        if (delta > 0 && page > 0) page--;
        return true;
    }

    private void buySelected() {
        List<ShopCatalog.ShopItem> items = visibleItems();
        if (selectedIndex < 0 || selectedIndex >= items.size()) return;
        TacRogueNetworking.CHANNEL.sendToServer(new RogueActionMessage(RogueActionMessage.ActionType.BUY_ITEM, items.get(selectedIndex).id));
    }

    private void sellSelected() {
        if (selectedSellSlot < 0) return;
        TacRogueNetworking.CHANNEL.sendToServer(new RogueActionMessage(RogueActionMessage.ActionType.SELL_ITEM, String.valueOf(selectedSellSlot)));
        selectedSellSlot = -1;
    }

    private List<ShopCatalog.ShopItem> visibleItems() {
        int shopFloor = shopFloor();
        List<ShopCatalog.ShopItem> source = TacZRegistryHelper.getAllShopItems();
        if (cachedVisibleSource == source
            && cachedVisibleShopFloor == shopFloor
            && cachedVisibleCategory == categoryFilter
            && cachedVisibleBlackMarket == com.levanilla.rogue.core.ClientRunState.getPrestigeBlackMarket()) {
            return cachedVisibleItems;
        }

        int blackMarket = com.levanilla.rogue.core.ClientRunState.getPrestigeBlackMarket();
        List<ShopCatalog.ShopItem> base = ShopStockManager.filterAvailable(source, shopFloor, blackMarket);
        if (cachedVisibleSource != source) {
            previewStackCache.clear();
            compatibilityCache.clear();
        }
        cachedVisibleItems = categoryFilter == null
            ? base
            : base.stream().filter(item -> item.category == categoryFilter).toList();
        cachedVisibleSource = source;
        cachedVisibleShopFloor = shopFloor;
        cachedVisibleCategory = categoryFilter;
        cachedVisibleBlackMarket = blackMarket;
        return cachedVisibleItems;
    }

    private List<SellEntry> sellEntries() {
        return refreshSellEntries();
    }

    private List<SellEntry> refreshSellEntries() {
        Minecraft mc = Minecraft.getInstance();
        String signature = sellInventorySignature(mc);
        if (signature.equals(cachedSellInventorySignature)) {
            return cachedSellEntries;
        }

        List<SellEntry> entries = new ArrayList<>();
        if (mc.player != null) {
            for (int i = 0; i < Math.min(36, mc.player.getInventory().getContainerSize()); i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                if (stack.is(Items.BARRIER) && stack.hasTag() && stack.getOrCreateTag().getBoolean("rogue_item_locked")) continue;
                int price = PriceManager.getSellPrice(stack);
                if (price > 0) entries.add(new SellEntry(i, stack, price, false));
            }
        }
        for (OpenShopMessage.StashEntry stashEntry : stashSellEntries) {
            ItemStack stack = stashEntry.stack();
            if (stack.isEmpty()) continue;
            int price = PriceManager.getSellPrice(stack);
            if (price > 0) {
                entries.add(new SellEntry(ShopService.STASH_SELL_SLOT_OFFSET + stashEntry.slot(), stack, price, true));
            }
        }
        cachedSellInventorySignature = signature;
        cachedSellEntries = entries;
        return cachedSellEntries;
    }

    private String sellInventorySignature(Minecraft mc) {
        if (mc.player == null) return "";
        StringBuilder signature = new StringBuilder();
        for (int i = 0; i < Math.min(36, mc.player.getInventory().getContainerSize()); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            signature.append(i).append(':')
                .append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())).append(':')
                .append(stack.getCount()).append(':');
            if (stack.hasTag()) signature.append(stack.getTag().hashCode());
            signature.append(';');
        }
        signature.append("|stash:");
        for (OpenShopMessage.StashEntry entry : stashSellEntries) {
            ItemStack stack = entry.stack();
            if (stack.isEmpty()) continue;
            signature.append(entry.slot()).append(':')
                .append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())).append(':')
                .append(stack.getCount()).append(':');
            if (stack.hasTag()) signature.append(stack.getTag().hashCode());
            signature.append(';');
        }
        return signature.toString();
    }

    private ItemStack previewStack(ShopCatalog.ShopItem item) {
        int shopFloor = shopFloor();
        if (previewStackShopFloor != shopFloor) {
            previewStackShopFloor = shopFloor;
            previewStackCache.clear();
        }
        return previewStackCache.computeIfAbsent(item.id,
            id -> RogueItemFactory.createShopItemStack(null, id, shopFloor));
    }

    private int actualPrice(ShopCatalog.ShopItem item) {
        if (item.category != ShopCatalog.Category.SPECIAL) return PriceManager.getShopBuyPrice(item, shopFloor());
        Minecraft mc = Minecraft.getInstance();
        int currentLevel = 0;
        if (mc.player != null) {
            if (item.id.equals("rogue:inv_upgrade")) currentLevel = mc.player.getPersistentData().getInt("TacRogue_InvLevel");
            if (item.id.equals("rogue:melee_upgrade")) currentLevel = mc.player.getPersistentData().getInt("TacRogueMeleeLevel");
            if (item.id.equals("rogue:random_perk")) currentLevel = mc.player.getPersistentData().getInt("RandomPerkBuys");
        }
        if (item.id.equals("rogue:ammo_capacity_upgrade")) currentLevel = RunManager.getClientAmmoCapacityLevel();
        if (item.id.equals("rogue:stash_upgrade")) currentLevel = Math.max(0, RunManager.getClientStashLines() - 2);
        if (item.id.equals("rogue:flashlight_upgrade")) currentLevel = RunManager.getClientFlashlightLevel();
        if (isUpgradeItem(item.id)) {
            return PriceManager.getUpgradePrice(item.id, currentLevel);
        }
        return PriceManager.getShopBuyPrice(item, shopFloor());
    }

    private int shopFloor() {
        return ShopStockManager.getShopFloor(RunManager.getCurrentFloor(), RunManager.isFloorCleared());
    }

    private boolean isUpgradeItem(String id) {
        return id.equals("rogue:inv_upgrade")
            || id.equals("rogue:stash_upgrade")
            || id.equals("rogue:ammo_capacity_upgrade")
            || id.equals("rogue:melee_upgrade")
            || id.equals("rogue:flashlight_upgrade")
            || id.equals("rogue:random_perk");
    }

    private Component shopItemName(ShopCatalog.ShopItem item) {
        String key = shopItemNameKey(item.id);
        return key == null ? Component.literal(item.displayName) : Component.translatable(key);
    }

    private String shopItemNameKey(String id) {
        return switch (id) {
            case "rogue:inv_upgrade" -> "shop_item.tac_rogue.inv_upgrade";
            case "rogue:stash_upgrade" -> "shop_item.tac_rogue.stash_upgrade";
            case "rogue:ammo_capacity_upgrade" -> "shop_item.tac_rogue.ammo_capacity_upgrade";
            case "rogue:melee_upgrade" -> "shop_item.tac_rogue.melee_upgrade";
            case "rogue:flashlight_upgrade" -> "shop_item.tac_rogue.flashlight_upgrade";
            case "rogue:medkit" -> "shop_item.tac_rogue.medkit";
            case "rogue:field_ration" -> "shop_item.tac_rogue.field_ration";
            case "rogue:stamina_shot" -> "shop_item.tac_rogue.stamina_shot";
            case "minecraft:snowball" -> "shop_item.tac_rogue.snowball";
            case "rogue:bandage" -> "shop_item.tac_rogue.bandage";
            case "rogue:armor_plate" -> "shop_item.tac_rogue.armor_plate";
            case "rogue:adrenaline" -> "shop_item.tac_rogue.adrenaline";
            case "rogue:emp_device" -> "shop_item.tac_rogue.emp_device";
            case "rogue:random_perk" -> "shop_item.tac_rogue.random_perk";
            default -> null;
        };
    }

    private List<Component> statLines(ShopCatalog.ShopItem item) {
        List<Component> lines = new ArrayList<>();
        if (isRarityShopWeapon(item.category)) {
            WeaponRarity.Rarity rarity = WeaponRarity.rollShopRarity(shopFloor(), item.id);
            lines.add(Component.translatable("gui.tac_rogue.shop_screen.rarity_price",
                rarity.name(), String.format(Locale.ROOT, "%.2f", PriceManager.getRarityPriceMultiplier(rarity))));
        }
        if (isGunCategory(item.category)) {
            addGunStatLines(item, lines);
        } else if (item.category == ShopCatalog.Category.ATTACHMENT) {
            addAttachmentStatLines(item, lines);
        } else if (item.category == ShopCatalog.Category.AMMO) {
            lines.add(Component.translatable("gui.tac_rogue.shop_screen.ammo", shopItemName(item)));
        }
        return lines;
    }

    private void addGunStatLines(ShopCatalog.ShopItem item, List<Component> lines) {
        TacZGunRegistry.GunProfile profile = TacZGunRegistry.getProfile(item.id);
        if (profile == null) {
            lines.add(Component.translatable("gui.tac_rogue.shop_screen.mag_ammo",
                TacZRegistryHelper.getMagazineSize(item.id), trimId(TacZRegistryHelper.getAmmoForGun(item.id))));
            return;
        }
        lines.add(Component.translatable("gui.tac_rogue.shop_screen.damage", profile.damage));
        lines.add(Component.translatable("gui.tac_rogue.shop_screen.rpm", profile.rpm));
        lines.add(Component.translatable("gui.tac_rogue.shop_screen.dps", profile.dps));
        lines.add(Component.translatable("gui.tac_rogue.shop_screen.mag_ammo", profile.magSize, trimId(profile.ammoId == null ? "" : profile.ammoId.toString())));
        if (profile.allowedAttachments != null && !profile.allowedAttachments.isEmpty()) {
            String attachments = profile.allowedAttachments.stream()
                .filter(type -> type != null)
                .map(type -> type.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
            if (!attachments.isBlank()) {
                lines.add(Component.translatable("gui.tac_rogue.shop_screen.attachments", trimDisplay(attachments, 30)));
            }
        }
    }

    private void addAttachmentStatLines(ShopCatalog.ShopItem item, List<Component> lines) {
        String slot = com.levanilla.rogue.core.registry.AttachmentDatabase.getSlotType(item.id);
        if (slot == null) slot = com.levanilla.rogue.core.registry.AttachmentDatabase.guessSlotType(item.id);
        if (slot != null) lines.add(Component.literal(slot.toUpperCase(Locale.ROOT)));
        try {
            var index = TimelessAPI.getCommonAttachmentIndex(new ResourceLocation(item.id)).orElse(null);
            if (index == null || index.getData() == null) {
                lines.add(Component.literal(ShopCatalog.getAttachmentTypeDesc(item.id).replaceAll("\u00A7.", "")));
                return;
            }
            var data = index.getData();
            lines.add(Component.translatable("gui.tac_rogue.shop_screen.weight", data.getWeight()));
            if (data.getExtendedMagLevel() > 0) {
                lines.add(Component.translatable("gui.tac_rogue.shop_screen.ext_mag_level", data.getExtendedMagLevel()));
            }
            TacZGunRegistry.getAttachmentSoundProfile(item.id)
                .filter(TacZGunRegistry.GunSoundProfile::suppressed)
                .ifPresent(profile -> lines.add(Component.translatable("gui.tac_rogue.shop_screen.suppressor_reduction",
                    String.format(Locale.ROOT, "%.0f", TacZGunRegistry.getSoundReductionBlocks(profile)))));
            int added = 0;
            for (var entry : data.getModifier().entrySet()) {
                if (added >= 4) break;
                var property = entry.getValue();
                if (property == null) continue;
                if (property.getComponents().isEmpty()) {
                    property.initComponents();
                }
                if (!property.getComponents().isEmpty()) {
                    for (Component component : property.getComponents()) {
                        if (added >= 4) break;
                        lines.add(component);
                        added++;
                    }
                } else {
                    lines.add(Component.translatable("gui.tac_rogue.shop_screen.modifier", trimId(entry.getKey())));
                    added++;
                }
            }
        } catch (Throwable ignored) {
            lines.add(Component.literal(ShopCatalog.getAttachmentTypeDesc(item.id).replaceAll("\u00A7.", "")));
        }
    }

    private boolean isGunCategory(ShopCatalog.Category category) {
        return category == ShopCatalog.Category.PISTOL
            || category == ShopCatalog.Category.RIFLE
            || category == ShopCatalog.Category.SMG
            || category == ShopCatalog.Category.SHOTGUN
            || category == ShopCatalog.Category.SNIPER
            || category == ShopCatalog.Category.LMG
            || category == ShopCatalog.Category.EXPLOSIVE;
    }

    private boolean isRarityShopWeapon(ShopCatalog.Category category) {
        return isGunCategory(category) || category == ShopCatalog.Category.MELEE;
    }

    private int rarityBackground(ShopCatalog.ShopItem item) {
        if (!isRarityShopWeapon(item.category)) return 0x55000000;
        WeaponRarity.Rarity rarity = WeaponRarity.rollShopRarity(shopFloor(), item.id);
        return withAlpha(rarity.color, rarity == WeaponRarity.Rarity.COMMON ? 0x38 : 0x52);
    }

    private static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }

    private void drawWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        for (var line : this.font.split(Component.literal(text), width)) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
    }

    private int maxPage(int total) {
        return total <= 0 ? 0 : (total - 1) / PAGE_SIZE;
    }

    private int visibleCategoryRows(int panelH) {
        return Math.max(5, Math.min(9, (panelH - 90) / 18));
    }

    private void renderCategoryPanel(GuiGraphics graphics, int x, int y, int panelH) {
        int visible = visibleCategoryRows(panelH);
        int maxScroll = Math.max(0, ShopCatalog.Category.values().length + 1 - visible);
        int top = y + 46;
        int bottom = top + visible * 18 + 2;
        graphics.fill(x + 8, top - 2, x + 10 + CATEGORY_W, bottom, 0x55000000);
        graphics.renderOutline(x + 8, top - 2, CATEGORY_W + 2, bottom - top + 2, 0x66336655);
        if (categoryScroll > 0) {
            graphics.drawString(this.font, "^", x + CATEGORY_W - 2, top - 12, 0xFF88FFCC, false);
        }
        if (categoryScroll < maxScroll) {
            graphics.drawString(this.font, "v", x + CATEGORY_W - 2, bottom + 2, 0xFF88FFCC, false);
        }
    }

    private String trimId(String id) {
        if (id == null) return "";
        String value = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return value.length() > 16 ? value.substring(0, 14) + ".." : value;
    }

    private String trimDisplay(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(0, Math.max(0, max - 2)) + "..";
    }

    private Compatibility compatibilityFor(ShopCatalog.ShopItem item) {
        if (item.category != ShopCatalog.Category.AMMO && item.category != ShopCatalog.Category.ATTACHMENT) {
            return Compatibility.NONE;
        }
        if (cachedHeldGunIds.isEmpty()) return new Compatibility(false, true, "");

        String key = item.category.name() + "|" + item.id;
        Compatibility cached = compatibilityCache.get(key);
        if (cached != null) return cached;

        for (String gunId : cachedHeldGunIds) {
            boolean compatible = item.category == ShopCatalog.Category.AMMO
                ? item.id.equals(TacZRegistryHelper.getAmmoForGun(gunId))
                : com.levanilla.rogue.core.registry.AttachmentDatabase.isCompatible(gunId, item.id);
            if (compatible) {
                Compatibility result = new Compatibility(true, true, trimId(gunId).toUpperCase(java.util.Locale.ROOT));
                compatibilityCache.put(key, result);
                return result;
            }
        }
        Compatibility result = new Compatibility(false, true, "");
        compatibilityCache.put(key, result);
        return result;
    }

    private void refreshHeldGunCache() {
        Minecraft mc = Minecraft.getInstance();
        List<String> heldGunIds = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        if (mc.player != null) {
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains("GunId")) continue;
                String gunId = stack.getTag().getString("GunId");
                heldGunIds.add(gunId);
                signature.append(i).append(':').append(gunId).append(';');
            }
        }

        String nextSignature = signature.toString();
        if (!nextSignature.equals(cachedHeldGunSignature)) {
            cachedHeldGunSignature = nextSignature;
            cachedHeldGunIds = List.copyOf(heldGunIds);
            compatibilityCache.clear();
        }
    }

    private Component categoryLabel(ShopCatalog.Category category) {
        if (category == null) return Component.translatable("gui.tac_rogue.shop_screen.category.all");
        return Component.translatable("gui.tac_rogue.shop_screen.category." + category.name().toLowerCase(java.util.Locale.ROOT));
    }

    private record Compatibility(boolean compatible, boolean relevant, String gunName) {
        private static final Compatibility NONE = new Compatibility(false, false, "");
    }

    private record SellEntry(int slot, ItemStack stack, int price, boolean stash) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
