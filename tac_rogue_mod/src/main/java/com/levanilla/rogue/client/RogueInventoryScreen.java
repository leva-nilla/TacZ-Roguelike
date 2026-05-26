package com.levanilla.rogue.client;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.ShopStockManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.ClientRunState;
import com.levanilla.rogue.core.registry.ShopCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.StaminaManager;
import com.levanilla.rogue.core.WeaponRarity;

/**
 * 繝励Ξ繧､繝､繝ｼ縺ｮ繧ｹ繝・・繧ｿ繧ｹ縲√ヱ繝ｼ繧ｯ縲√す繝ｧ繝・・縲√Α繝・す繝ｧ繝ｳ諠・ｱ繧定｡ｨ遉ｺ縺吶ｋ螟壽ｩ溯・繧､繝ｳ繝吶Φ繝医Μ逕ｻ髱｢
 * E繧ｭ繝ｼ縺ｧ髢九°繧後√ち繝門・繧頑崛縺医↓繧医▲縺ｦ讒倥・↑諠・ｱ縺ｫ繧｢繧ｯ繧ｻ繧ｹ縺ｧ縺阪∪縺吶・ */
public class RogueInventoryScreen extends AbstractContainerScreen<AbstractContainerMenu> {
    // 画面に表示するタブの種類
    public enum Tab {
        INVENTORY, STATUS, PERKS, FLOOR_INFO, SHOP, QUEST
    }

    private Tab activeTab = Tab.INVENTORY;
    private int tabX, tabY;
    private int shopPage = 0;
    private int perkScrollOffset = 0;
    private int questScrollOffset = 0;
    private int statusScrollOffset = 0;
    private boolean floorEntrySoloMode = true;
    private boolean floorLowHealthMode = false;
    private static final int ITEMS_PER_PAGE = 6;

    public void setActiveTab(Tab tab) {
        this.activeTab = tab;
        this.shopPage = 0;
    }

    Tab activeTab() { return activeTab; }
    int tabX() { return tabX; }
    int tabY() { return tabY; }
    int leftPos() { return leftPos; }
    int topPos() { return topPos; }
    int rogueImageWidth() { return imageWidth; }
    int rogueImageHeight() { return imageHeight; }
    net.minecraft.client.gui.Font rogueFont() { return font; }
    AbstractContainerMenu rogueMenu() { return menu; }

    public RogueInventoryScreen(AbstractContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 236;
        this.imageHeight = 172;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int x = this.leftPos;
        int y = this.topPos;
        int sidePad = sidePad();
        int topPad = topPad();

        // --- 繝上う繝・け縺ｪ繧ｵ繧､繝舌・繝代Φ繧ｯ鬚ｨ閭梧勹縺ｮ謠冗判 ---
        graphics.fill(x - sidePad, y - topPad, x + imageWidth + sidePad, y + imageHeight + topPad, 0xAA001122);
        graphics.renderOutline(x - sidePad, y - topPad, imageWidth + sidePad * 2, imageHeight + topPad * 2, 0xAA00AAFF);
        for (int i = -sidePad + 10; i < imageWidth + sidePad - 10; i += 20) graphics.fill(x + i, y - topPad + 5, x + i + 1, y + imageHeight + topPad - 5, 0x2200AAFF);
        for (int i = -topPad + 10; i < imageHeight + topPad - 10; i += 20) graphics.fill(x - sidePad + 5, y + i, x + imageWidth + sidePad - 5, y + i + 1, 0x2200AAFF);

        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;

        // 繧ｷ繝ｧ繝・・繝ｻ繧､繝ｳ繝吶Φ繝医Μ縺ｮ繝帙ヰ繝ｼ繧｢繧､繝・Β繧呈峩譁ｰ
        hoveredShopItem = -1;
        hoveredInvSlot = -1;
        hoveredPerkIndex = -1;
        hoveredQuestIndex = -1;
        if (activeTab == Tab.SHOP) {
            int catPanelW = 65;
            int listX = x - 110 + catPanelW + 4;
            int panelRight = x + imageWidth + 100;
            List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> items = getVisibleShopItems();
            int startIndex = shopPage * ITEMS_PER_PAGE;
            int listY = y + 10;
            for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < items.size(); i++) {
                int iy = listY + (i * 18);
                if (mouseX >= listX && mouseX <= panelRight - 5 && mouseY >= iy - 1 && mouseY <= iy + 15) {
                    hoveredShopItem = i;
                }
            }
        } else if (activeTab == Tab.PERKS && player != null) {
            int perkY = y + 32;
            int rightX = x + 10;
            List<PerkGroupEntry> groups = buildPerkGroups(player);
            int visible = Math.min(groups.size() - perkScrollOffset, 6);
            for (int i = 0; i < visible; i++) {
                int py = perkY + i * 25;
                if (mouseX >= rightX && mouseX <= rightX + 280 && mouseY >= py && mouseY <= py + 23) {
                    hoveredPerkIndex = i + perkScrollOffset;
                }
            }
        }

        if (player != null) {
            switch (activeTab) {
                case STATUS -> renderStatusTab(graphics, player, x, y);
                case PERKS -> renderPerksTab(graphics, player, x, y);
                case SHOP -> renderShopTab(graphics, player, x, y);
                case FLOOR_INFO -> renderFloorInfoTab(graphics, x, y);
                case QUEST -> renderQuestTab(graphics, player, x, y, mouseX, mouseY);
                case INVENTORY -> {} 
            }
        }

        if (activeTab == Tab.INVENTORY) {
            super.render(graphics, mouseX, mouseY, partialTick);

            // --- キャラクターモデル表示パネル ---
            if (!isCompactLayout()) {
                int panelX = x - 95;
                int panelY = y + 5;
                int panelW = 80;
                int panelH = Math.min(130, Math.max(82, this.height - panelY - 34));
                graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0x88001133);
                graphics.renderOutline(panelX, panelY, panelW, panelH, 0xAA00CCFF);
                graphics.drawString(this.font, "\u00A7b\u2605 OPERATOR", panelX + 4, panelY + 3, 0xFF00AAFF, false);

                if (player != null) {
                    int entityX = panelX + panelW / 2;
                    int entityY = panelY + panelH - 10;
                    float scale = Math.min(38.0f, Math.max(24.0f, panelH / 3.4f));
                    float lookX = entityX - mouseX;
                    float lookY = (entityY - 60) - mouseY;
                    net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
                        graphics, entityX, entityY, (int) scale, lookX, lookY, player);
                }
            }
        }

        Component statusText = RunManager.isRunActive() ? 
            Component.translatable("gui.tac_rogue.inventory.operator_status", RunManager.getCurrentFloor()) : 
            Component.translatable("gui.tac_rogue.inventory.base_command");
        graphics.drawString(this.font, statusText, x - sidePad + 10, Math.max(4, y - topPad - 14), 0xFF00FFFF, false);

        renderTabHighlight(graphics);

        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }

        // 繝・・繝ｫ繝√ャ繝励ｒ蜈ｨ縺ｦ縺ｮ莉冶ｦ∫ｴ縺ｮ蠕鯉ｼ域怙蜑埼擇・峨↓謠冗判
        if (activeTab == Tab.INVENTORY && this.hoveredSlot != null && !this.hoveredSlot.getItem().isEmpty()) {
            graphics.renderTooltip(this.font, this.hoveredSlot.getItem(), mouseX, mouseY);
        }
        if (activeTab == Tab.SHOP && hoveredShopItem >= 0 && player != null) {
            if (!sellMode) {
                renderShopTooltip(graphics, player, mouseX, mouseY);
            }
        }
        if (activeTab == Tab.PERKS && hoveredPerkIndex >= 0 && player != null) {
            renderPerkTooltip(graphics, player, mouseX, mouseY);
        }
        if (activeTab == Tab.QUEST && hoveredQuestIndex >= 0) {
            renderQuestTooltip(graphics, mouseX, mouseY);
        }
    }

    /** ショップアイテムのツールチップ描画 */
    private List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> getVisibleShopItems() {
        List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> baseItems = shopCategoryFilter == null ?
            TacZRegistryHelper.getAllShopItems() : TacZRegistryHelper.getItemsByCategory(shopCategoryFilter);
        int shopFloor = ShopStockManager.getShopFloor(RunManager.getCurrentFloor(), RunManager.isFloorCleared());
        int blackMarket = com.levanilla.rogue.core.ClientRunState.getPrestigeBlackMarket();
        if (cachedVisibleShopSource == baseItems
            && cachedVisibleShopCategory == shopCategoryFilter
            && cachedVisibleShopFloor == shopFloor
            && cachedVisibleShopBlackMarket == blackMarket) {
            return cachedVisibleShopItems;
        }

        if (cachedVisibleShopSource != baseItems) {
            shopCompatibilityCache.clear();
        }
        cachedVisibleShopItems = ShopStockManager.filterAvailable(baseItems, shopFloor, blackMarket);
        cachedVisibleShopSource = baseItems;
        cachedVisibleShopCategory = shopCategoryFilter;
        cachedVisibleShopFloor = shopFloor;
        cachedVisibleShopBlackMarket = blackMarket;
        return cachedVisibleShopItems;
    }

    private void renderShopTooltip(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int mouseX, int mouseY) {
        List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> items = getVisibleShopItems();
        int idx = shopPage * ITEMS_PER_PAGE + hoveredShopItem;
        if (idx < 0 || idx >= items.size()) return;
        com.levanilla.rogue.core.registry.ShopCatalog.ShopItem item = items.get(idx);
        int actualPrice = item.price;
        if (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SPECIAL) {
            int currentLevel = 0;
            if (item.id.equals("rogue:ammo_capacity_upgrade")) currentLevel = com.levanilla.rogue.core.RunManager.getClientAmmoCapacityLevel();
            else if (item.id.equals("rogue:inv_upgrade")) currentLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
            else if (item.id.equals("rogue:stash_upgrade")) currentLevel = Math.max(0, com.levanilla.rogue.core.RunManager.getClientStashLines() - 2);
            else if (item.id.equals("rogue:melee_upgrade")) currentLevel = player.getPersistentData().getInt("TacRogueMeleeLevel");
            else if (item.id.equals("rogue:flashlight_upgrade")) currentLevel = com.levanilla.rogue.core.RunManager.getClientFlashlightLevel();
            else if (item.id.equals("rogue:random_perk")) currentLevel = player.getPersistentData().getInt("RandomPerkBuys");
            actualPrice = com.levanilla.rogue.core.PriceManager.getUpgradePrice(item.id, currentLevel);
        }

        int gold = com.levanilla.rogue.core.RunManager.getClientGold();
        boolean canAfford = gold >= actualPrice;

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal("\u00A7e").append(shopItemName(item)));
        tooltip.add(Component.translatable("gui.tac_rogue.shop_screen.tooltip.category",
            Component.translatable("gui.tac_rogue.shop_screen.category." + item.category.name().toLowerCase(java.util.Locale.ROOT))));
        tooltip.add(Component.translatable("gui.tac_rogue.shop_screen.tooltip.price",
            Component.literal((canAfford ? "\u00A76" : "\u00A7c") + "$" + actualPrice)));
        tooltip.add(Component.translatable("gui.tac_rogue.shop_screen.tooltip.held", gold));
        
        String desc = switch (item.category) {
            case PISTOL -> trText("gui.tac_rogue.shop_screen.desc.pistol");
            case RIFLE -> trText("gui.tac_rogue.shop_screen.desc.rifle");
            case SMG -> trText("gui.tac_rogue.shop_screen.desc.smg");
            case SHOTGUN -> trText("gui.tac_rogue.shop_screen.desc.shotgun");
            case SNIPER -> trText("gui.tac_rogue.shop_screen.desc.sniper");
            case LMG -> trText("gui.tac_rogue.shop_screen.desc.lmg");
            case EXPLOSIVE -> trText("gui.tac_rogue.shop_screen.desc.explosive");
            case MELEE -> trText("gui.tac_rogue.shop_screen.desc.melee");
            case TACTICAL -> trText("gui.tac_rogue.shop_screen.desc.tactical");
            case ATTACHMENT -> trText("gui.tac_rogue.shop_screen.desc.attachment");
            case AMMO -> trText("gui.tac_rogue.shop_screen.desc.ammo");
            case SPECIAL -> {
                if (item.id.equals("rogue:ammo_capacity_upgrade")) {
                    int ammoCapLevel = RunManager.getClientAmmoCapacityLevel();
                    int nextLevel = ammoCapLevel + 1;
                    int currentMultiplier = (int)((capLevelToMultiplier(ammoCapLevel) - 1.0f) * 100);
                    int nextMultiplier = (int)((capLevelToMultiplier(nextLevel) - 1.0f) * 100);
                    if (ammoCapLevel >= 5) { // GameConstants.AMMO_CAPACITY_MAX_LEVEL
                        yield trText("gui.tac_rogue.shop_screen.desc.ammo_capacity_max", currentMultiplier);
                    } else {
                        yield trText("gui.tac_rogue.shop_screen.desc.ammo_capacity", currentMultiplier, nextMultiplier);
                    }
                } else if (item.id.equals("rogue:inv_upgrade")) {
                    yield trText("gui.tac_rogue.shop_screen.desc.inv_upgrade");
                } else if (item.id.equals("rogue:stash_upgrade")) {
                    yield trText("gui.tac_rogue.shop_screen.desc.stash_upgrade");
                } else if (item.id.equals("rogue:melee_upgrade")) {
                    yield trText("gui.tac_rogue.shop_screen.desc.melee_upgrade");
                } else if (item.id.equals("rogue:flashlight_upgrade")) {
                    int flashlightLevel = com.levanilla.rogue.core.RunManager.getClientFlashlightLevel();
                    yield trText("gui.tac_rogue.shop_screen.desc.flashlight_upgrade", flashlightLevel, flashlightLevel + 1);
                } else if (item.id.equals("rogue:random_perk")) {
                    yield trText("gui.tac_rogue.shop_screen.desc.random_perk");
                }
                yield trText("gui.tac_rogue.shop_screen.desc.special");
            }
        };
        for (String line : desc.split("\n")) {
            tooltip.add(Component.literal("\u00A78" + line));
        }
        if (!canAfford) tooltip.add(Component.translatable("gui.tac_rogue.shop_screen.tooltip.not_enough_gold"));
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private float capLevelToMultiplier(int level) {
        return 1.0f + (level * 0.5f);
    }

    private Component shopItemName(com.levanilla.rogue.core.registry.ShopCatalog.ShopItem item) {
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

    private String trText(String key, Object... args) {
        return net.minecraft.client.resources.language.I18n.get(key, args);
    }

    // ===== クエストタブ描画 =====

    /** クライアント側のクエストキャッシュ（SyncDataMessage で更新） */
    private static int cachedChapter = 1;
    private static java.util.List<String[]> cachedQuests = new java.util.ArrayList<>();

    /** サーバーからのクエストデータ同期 */
    public static void syncQuestData(int chapter, java.util.List<String[]> quests) {
        cachedChapter = chapter;
        cachedQuests = quests;
    }

    private void renderQuestTab(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int x, int y, int mouseX, int mouseY) {
        int panelLeft = x - 110;
        int panelRight = x + imageWidth + 110;
        int panelW = panelRight - panelLeft;
        int panelTop = y + 5;

        // 背景パネル
        graphics.fill(panelLeft, panelTop, panelRight, y + imageHeight + 40, 0xDD1A1A2E);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF00E5FF);
        graphics.fill(panelLeft, y + imageHeight + 39, panelRight, y + imageHeight + 40, 0xFF00E5FF);

        // チャプタータイトル
        String chapterTitle = "CHAPTER " + cachedChapter;
        graphics.drawString(this.font, chapterTitle, panelLeft + panelW / 2 - this.font.width(chapterTitle) / 2,
            panelTop + 6, 0xFF00E5FF, true);

        // チャプターストーリー
        if (cachedChapter >= 1 && cachedChapter <= com.levanilla.rogue.core.QuestManager.MAX_CHAPTERS) {
            net.minecraft.network.chat.Component storyTitle = net.minecraft.network.chat.Component.translatable(
                com.levanilla.rogue.core.QuestManager.getChapterTitleKey(cachedChapter));
            graphics.drawString(this.font, storyTitle, panelLeft + panelW / 2 - this.font.width(storyTitle) / 2,
                panelTop + 18, 0xFFFFD700, true);
        }

        // クエストリスト（スクロール対応）
        int questY = panelTop + 34;
        int lineH = 22;
        int maxQuestVisible = Math.min(5, cachedQuests.size());
        int maxQuestScroll = Math.max(0, cachedQuests.size() - maxQuestVisible);
        if (questScrollOffset > maxQuestScroll) questScrollOffset = maxQuestScroll;
        if (questScrollOffset < 0) questScrollOffset = 0;

        if (cachedQuests.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.no_data"),
                panelLeft + 10, questY, 0xFF888888, false);
            // クエスト同期リクエスト
            if (this.minecraft != null) {
                com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                    new com.levanilla.rogue.networking.RogueActionMessage(
                        com.levanilla.rogue.networking.RogueActionMessage.ActionType.SYNC_DATA));
            }
        }

        // スクロールインジケーター
        if (questScrollOffset > 0) {
            graphics.drawCenteredString(this.font, "\u00A78\u25B2 scroll up",
                panelLeft + panelW / 2, questY - 10, 0xFF555555);
        }

        for (int i = 0; i < maxQuestVisible; i++) {
            int qi = i + questScrollOffset;
            if (qi >= cachedQuests.size()) break;
            String[] quest = cachedQuests.get(qi);
            String questId = quest[0];
            String typeName = quest[1];
            String roleName = quest.length > 6 ? quest[6] : "quest.tac_rogue.role.contract";
            boolean rareWeaponReward = quest.length > 7 && "true".equals(quest[7]);
            int target = 0, progress = 0, goldReward = 0;
            boolean completed = false;
            try {
                target = Integer.parseInt(quest[2]);
                progress = Integer.parseInt(quest[3]);
                goldReward = Integer.parseInt(quest[4]);
                completed = "true".equals(quest[5]);
            } catch (Exception ignored) {}

            int qy = questY + i * lineH;

            // 背景（交互色）
            int bgColor = (i % 2 == 0) ? 0x30FFFFFF : 0x15FFFFFF;
            if (completed) bgColor = 0x3000FF00;
            if (mouseX >= panelLeft + 4 && mouseX <= panelRight - 4 && mouseY >= qy && mouseY < qy + lineH - 2) {
                bgColor = 0x55FFFFFF;
                hoveredQuestIndex = qi;
            }
            graphics.fill(panelLeft + 4, qy, panelRight - 4, qy + lineH - 2, bgColor);

            // ステータスアイコン
            String statusIcon = completed ? "\u00A7a\u2713" : "\u00A7e\u25B6";
            graphics.drawString(this.font, statusIcon, panelLeft + 8, qy + 2, 0xFFFFFFFF, true);

            // クエストタイプ名
            net.minecraft.network.chat.Component typeComp = net.minecraft.network.chat.Component.translatable(typeName);
            graphics.drawString(this.font, typeComp, panelLeft + 22, qy + 2, completed ? 0xFF00FF00 : 0xFFDDDDDD, false);

            net.minecraft.network.chat.Component roleComp = net.minecraft.network.chat.Component.translatable(roleName);
            int roleColor = rareWeaponReward ? 0xFFFF66DD : 0xFF88AAFF;
            graphics.drawString(this.font, roleComp, panelLeft + 22, qy + 11, roleColor, false);
            if (rareWeaponReward) {
                graphics.drawString(this.font, "\u00A7d\u2726", panelLeft + 82, qy + 11, 0xFFFF66DD, true);
            }

            // 進捗バー
            int barX = panelLeft + 150;
            int barW = 100;
            float ratio = target > 0 ? Math.min(1.0f, (float) progress / target) : 0;
            graphics.fill(barX, qy + 3, barX + barW, qy + 11, 0xFF333333);
            if (ratio > 0) {
                int filledW = (int)(barW * ratio);
                int barColor = completed ? 0xFF00FF00 : 0xFF00E5FF;
                graphics.fill(barX, qy + 3, barX + filledW, qy + 11, barColor);
            }
            graphics.fill(barX, qy + 3, barX + barW, qy + 4, 0x60FFFFFF);

            // 進捗テキスト
            String progText = progress + "/" + target;
            graphics.drawString(this.font, progText, barX + barW + 4, qy + 2, 0xFFCCCCCC, false);

            // 報酬
            String rewardText = "\u00A7e" + goldReward + " G";
            graphics.drawString(this.font, rewardText, panelRight - 50, qy + 2, 0xFFFFD700, true);

            // ストーリークエストマーク
            if (questId.startsWith("story_")) {
                graphics.drawString(this.font, "\u00A76\u2605", panelRight - 14, qy + 2, 0xFFFFD700, true);
            }
        }

        // スクロールダウンインジケーター
        if (questScrollOffset + maxQuestVisible < cachedQuests.size()) {
            graphics.drawCenteredString(this.font, "\u00A78\u25BC scroll down (" + (cachedQuests.size() - questScrollOffset - maxQuestVisible) + " more)",
                panelLeft + panelW / 2, questY + maxQuestVisible * lineH + 2, 0xFF555555);
        }

        // 完了カウント
        long completedCount = cachedQuests.stream().filter(q -> q.length > 5 && "true".equals(q[5])).count();
        String summary = "\u00A77" + completedCount + "/" + cachedQuests.size() + " completed";
        graphics.drawString(this.font, summary, panelLeft + 10, y + imageHeight + 25, 0xFF888888, false);
    }

    private void renderTabHighlight(GuiGraphics graphics) {
        int[] offsets = {-6, 61, 113, 165, 217, 284};
        int[] widths = {65, 50, 50, 50, 65, 55};
        int index = activeTab.ordinal();
        if (index >= 0 && index < offsets.length) {
            graphics.fill(tabX + offsets[index], tabY, tabX + offsets[index] + widths[index], tabY + 15, 0x4400FFFF);
            graphics.renderOutline(tabX + offsets[index], tabY, widths[index], 15, 0xFF00FFFF);
        }
    }

    private void renderStatusTab(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int x, int y) {
        boolean compact = isCompactLayout();
        int lx = compact ? x - sidePad() + 8 : x - 96;
        int viewportTop = y + 5;
        int viewportBottom = Math.min(this.height - 28, y + imageHeight + (compact ? 8 : 35));
        int viewportHeight = viewportBottom - viewportTop;
        int top = y + 8;
        int totalW = compact ? Math.max(236, imageWidth + sidePad() * 2 - 16) : 406;
        int gap = compact ? 6 : 10;
        int leftW = compact ? Math.max(112, (totalW - gap) / 2) : 178;
        int rightX = lx + leftW + gap;
        int rightW = compact ? Math.max(112, totalW - leftW - gap) : 218;

        float hp = player.getHealth();
        float maxHp = player.getMaxHealth();
        float hpRatio = maxHp > 0.0f ? Math.max(0.0f, Math.min(1.0f, hp / maxHp)) : 0.0f;
        int hpPercent = Math.round(hpRatio * 100.0f);
        int hpColor = hpRatio > 0.5f ? 0xFF00FF88 : hpRatio > 0.25f ? 0xFFFFFF00 : 0xFFFF4444;
        float maxSta = StaminaManager.getClientMaxStamina();
        float sta = StaminaManager.getClientStamina();
        int armor = (int) player.getAttributeValue(Attributes.ARMOR);
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);

        float dmgBonus = sumClientPerkEffect(player, "perk:DAMAGE");
        float hsBonus = sumClientPerkEffect(player, "perk:FORTUNE");
        float fireRateBonus = WeaponRarity.getFireRatePerkBonusPercent(player);
        float reloadBonus = sumClientPerkEffect(player, "perk:RELOAD_SPEED");
        float autoloaderEffect = sumClientPerkEffect(player, "perk:AUTOLOADER");
        float resistBonus = sumClientPerkEffect(player, "perk:RESISTANCE");
        float vampBonus = sumClientPerkEffect(player, "perk:VAMPIRE");
        float goldBonus = sumClientPerkEffect(player, "perk:GOLD_RUSH");
        float staminaBonus = sumClientPerkEffect(player, "perk:STAMINA");
        float magBonus = sumClientPerkEffect(player, "perk:MAG_SIZE");
        List<String> details = buildStatusDetailLines(player, armor, resistBonus, fireRateBonus, reloadBonus, autoloaderEffect, staminaBonus);
        int tacticalH = Math.max(54, 43 + details.size() * 10 + (isCompactLayout() ? 11 : 0));
        boolean showDeepPanel = com.levanilla.rogue.core.ClientRunState.getDeepCore() > 0
            || com.levanilla.rogue.core.ClientRunState.getHighestEverFloor() >= 100;
        int combatBottomY = top + 188;
        int deepPanelH = showDeepPanel ? 74 : 0;
        int tacticalY = combatBottomY + (showDeepPanel ? deepPanelH + 8 : 0);
        int contentHeight = (tacticalY + tacticalH + 8) - viewportTop;
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        statusScrollOffset = Math.max(0, Math.min(statusScrollOffset, maxScroll));

        graphics.enableScissor(lx - 4, viewportTop, rightX + rightW + 4, viewportBottom);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -statusScrollOffset, 0);

        drawPanel(graphics, lx, top, leftW, 70, tr("gui.tac_rogue.status.panel.vitals"), 0xFF00DDFF);
        drawProgressBar(graphics, lx + 10, top + 20, leftW - 20, 8, hp, maxHp, hpColor, 0xFF3B1D28);
        graphics.drawString(this.font, String.format(java.util.Locale.ROOT, "HP %d%%  %.1f/%.1f", hpPercent, hp, maxHp), lx + 10, top + 32, hpColor, false);
        drawProgressBar(graphics, lx + 10, top + 45, leftW - 20, 8, sta, maxSta, 0xFF00D9FF, 0xFF172B38);
        graphics.drawString(this.font, String.format(java.util.Locale.ROOT, "STA %.0f/%.0f", sta, maxSta), lx + 10, top + 57, 0xFF9BEAFF, false);

        drawPanel(graphics, lx, top + 78, leftW, 78, tr("gui.tac_rogue.status.panel.operator"), 0xFF88CCFF);
        drawMetric(graphics, lx + 10, top + 98, tr("gui.tac_rogue.status.metric.armor"), String.valueOf(armor), 0xFFAABBFF);
        drawMetric(graphics, lx + 92, top + 98, tr("gui.tac_rogue.status.metric.speed"), String.format(java.util.Locale.ROOT, "%.3f", speed), 0xFFFFFFFF);
        drawMetric(graphics, lx + 10, top + 122, tr("gui.tac_rogue.status.metric.gold"), "$" + RunManager.getClientGold(), 0xFFFFD45C);
        drawMetric(graphics, lx + 92, top + 122, tr("gui.tac_rogue.status.metric.perks"), String.valueOf(countClientPerks(player)), 0xFFAAFFCC);

        drawPanel(graphics, rightX, top, rightW, 70, tr("gui.tac_rogue.status.panel.run_state"), 0xFFFFDD66);
        drawMetric(graphics, rightX + 10, top + 20, tr("gui.tac_rogue.status.metric.floor"), String.valueOf(RunManager.getCurrentFloor()), 0xFF88FF88);
        drawMetric(graphics, rightX + 76, top + 20, tr("gui.tac_rogue.status.metric.max"), String.valueOf(RunManager.getMaxReachedFloor()), 0xFF88CCFF);
        String runState = RunManager.isFloorCleared()
            ? tr("gui.tac_rogue.status.state.extract")
            : RunManager.isRunActive() ? tr("gui.tac_rogue.status.state.active") : tr("gui.tac_rogue.status.state.lobby");
        int runStateColor = RunManager.isFloorCleared() ? 0xFF55DDAA : RunManager.isRunActive() ? 0xFF00FF88 : 0xFFFFDD66;
        drawMetric(graphics, rightX + 142, top + 20, tr("gui.tac_rogue.status.metric.state"), runState, runStateColor);
        String theme = RunManager.getCurrentThemeName();
        graphics.drawString(this.font, "\u00A77" + tr("gui.tac_rogue.status.metric.theme"), rightX + 10, top + 45, 0xFF888888, false);
        graphics.drawString(this.font, trim(theme, 30), rightX + 10, top + 56, 0xFFFFFFFF, false);

        drawPanel(graphics, rightX, top + 78, rightW, 102, tr("gui.tac_rogue.status.panel.combat_modifiers"), 0xFFFF6666);
        drawMetric(graphics, rightX + 10, top + 98, tr("gui.tac_rogue.status.metric.damage"), fmtMult(1.0f + dmgBonus / 100.0f), 0xFFFF7777);
        drawMetric(graphics, rightX + 76, top + 98, tr("gui.tac_rogue.status.metric.crit"), fmtChance(hsBonus), 0xFFFFFF66);
        drawMetric(graphics, rightX + 142, top + 98, tr("gui.tac_rogue.status.metric.reload"), fmtPercent(reloadBonus), 0xFF66EEFF);
        drawMetric(graphics, rightX + 10, top + 122, tr("gui.tac_rogue.status.metric.special_resist"), fmtMult(specialResistanceTaken(resistBonus)), 0xFFCCAAFF);
        drawMetric(graphics, rightX + 76, top + 122, tr("gui.tac_rogue.status.metric.vamp"), String.format(java.util.Locale.ROOT, "+%.1f", vampBonus / 10f), 0xFFFF8888);
        drawMetric(graphics, rightX + 142, top + 122, tr("gui.tac_rogue.status.metric.gold"), fmtPercent(goldBonus), 0xFFFFD700);
        drawMetric(graphics, rightX + 10, top + 146, tr("gui.tac_rogue.status.metric.fire_rate"), fmtMult(1.0f + fireRateBonus / 100.0f), 0xFFFF9966);

        if (showDeepPanel) {
            drawDeepOperationsPanel(graphics, lx, combatBottomY, leftW + rightW + 10, deepPanelH);
        }

        int bottomY = tacticalY;
        drawPanel(graphics, lx, bottomY, leftW + rightW + 10, tacticalH, tr("gui.tac_rogue.status.panel.tactical_state"), 0xFFAAFFCC);
        boolean prone = com.levanilla.rogue.core.CombatPostureHelper.isProne(player);
        boolean sneaking = player.hasPose(net.minecraft.world.entity.Pose.CROUCHING)
            || player.isShiftKeyDown()
            || ClientEventHandler.isRogueSneakToggled();
        String posture = prone
            ? "\u00A72" + tr("gui.tac_rogue.status.stance.prone")
            : sneaking ? "\u00A7e" + tr("gui.tac_rogue.status.stance.sneak") : "\u00A7f" + tr("gui.tac_rogue.status.stance.standing");
        float postureDmg = prone ? GameConstants.CRAWL_DAMAGE_MULT : sneaking ? GameConstants.SNEAK_DAMAGE_MULT : 1.0f;
        graphics.drawString(this.font, "\u00A77" + tr("gui.tac_rogue.status.stance", posture), lx + 10, bottomY + 17, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "\u00A77" + tr("gui.tac_rogue.status.posture_damage", String.format(java.util.Locale.ROOT, "%.2f", postureDmg)), lx + 116, bottomY + 17, 0xFFFFFFFF, false);
        int staminaX = compact ? lx + 10 : lx + 236;
        int magazineX = compact ? lx + 150 : lx + 322;
        int secondLineY = compact ? bottomY + 27 : bottomY + 17;
        graphics.drawString(this.font, "\u00A77" + tr("gui.tac_rogue.status.stamina_mult", fmtMult(1.0f + staminaBonus / 100.0f)), staminaX, secondLineY, 0xFFAAFFCC, false);
        graphics.drawString(this.font, "\u00A77" + tr("gui.tac_rogue.status.magazine_mult", fmtMult(1.0f + magBonus / 100.0f)), magazineX, secondLineY, 0xFFAAFFCC, false);

        int detailY = bottomY + (compact ? 42 : 31);
        for (int i = 0; i < details.size(); i++) {
            graphics.drawString(this.font, "\u00A78- \u00A7f" + details.get(i),
                lx + 10, detailY + i * 10, 0xFFE0E0E0, false);
        }
        graphics.pose().popPose();
        graphics.disableScissor();

        if (maxScroll > 0) {
            int barX = rightX + rightW - 4;
            int barH = Math.max(20, viewportHeight * viewportHeight / contentHeight);
            int barY = viewportTop + (viewportHeight - barH) * statusScrollOffset / maxScroll;
            graphics.fill(barX, viewportTop, barX + 2, viewportBottom, 0x44111111);
            graphics.fill(barX, barY, barX + 2, barY + barH, 0xAA00DDFF);
        }
    }

    private void drawDeepOperationsPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        drawPanel(graphics, x, y, w, h, tr("gui.tac_rogue.status.panel.deep_operations"), 0xFF66F5FF);
        int core = com.levanilla.rogue.core.ClientRunState.getDeepCore();
        int prestige = com.levanilla.rogue.core.ClientRunState.getPrestigeLevel();
        int highest = com.levanilla.rogue.core.ClientRunState.getHighestEverFloor();
        int colW = Math.max(68, (w - 20) / 3);
        drawMetric(graphics, x + 10, y + 20, tr("gui.tac_rogue.status.metric.deep_core"), String.valueOf(core), 0xFF66F5FF);
        drawMetric(graphics, x + 10 + colW, y + 20, tr("gui.tac_rogue.status.metric.prestige"), String.valueOf(prestige), 0xFFFFD166);
        drawMetric(graphics, x + 10 + colW * 2, y + 20, tr("gui.tac_rogue.status.metric.highest_floor"), String.valueOf(highest), 0xFFAAFFCC);
        String taskType = com.levanilla.rogue.core.ClientRunState.getCurrentDeepTaskType();
        String taskName = taskType == null || taskType.isBlank()
            ? tr("gui.tac_rogue.status.deep_task.none")
            : Component.translatable("deep_task.tac_rogue." + taskType.toLowerCase(java.util.Locale.ROOT)).getString();
        int progress = com.levanilla.rogue.core.ClientRunState.getDeepTaskProgress();
        int target = com.levanilla.rogue.core.ClientRunState.getDeepTaskTarget();
        String line = tr("gui.tac_rogue.status.deep_task.progress", taskName, progress, target);
        graphics.drawString(this.font, "\u00A77" + tr("gui.tac_rogue.status.metric.deep_task"), x + 10, y + 49, 0xFF888888, false);
        graphics.drawString(this.font, trim(line, Math.max(18, (w - 100) / 6)), x + 82, y + 49, 0xFFE8FFF8, false);
    }

    private List<String> buildStatusDetailLines(net.minecraft.client.player.LocalPlayer player, int armor,
                                                float resistBonus, float fireRateBonus, float reloadBonus,
                                                float autoloaderEffect, float staminaBonus) {
        List<String> lines = new ArrayList<>();
        float armorReduction = GameConstants.getArmorMitigationEstimate(armor);
        float volatileTaken = 1.0f + countModifier(player, PerkDefinition.Modifier.VOLATILE) * 0.20f;
        lines.add(tr("gui.tac_rogue.status.armor_mitigation", fmtOnePercent(armorReduction * 100.0f)));
        lines.add(tr("gui.tac_rogue.status.resistance_damage_taken", fmtMult(specialResistanceTaken(resistBonus))));
        lines.add(tr("gui.tac_rogue.status.volatile_damage_taken", fmtMult(volatileTaken)));
        lines.add(tr("gui.tac_rogue.status.dodge_effective",
            fmtOnePercent(Math.min(GameConstants.DODGE_MAX_CHANCE * 100.0f,
                effectiveStackedChance(player, PerkDefinition.Category.DODGE, 1.0f)))));
        lines.add(tr("gui.tac_rogue.status.ammo_save_effective", fmtOnePercent(effectiveAmmoSaveChance(player))));
        lines.add(formatEffectivePerkValue(player, PerkDefinition.Category.SCAVENGER, sumClientPerkEffect(player, "perk:SCAVENGER")));
        lines.add(tr("gui.tac_rogue.status.fire_rate_effective", fmtMult(1.0f + fireRateBonus / 100.0f)));
        lines.add(tr("gui.tac_rogue.status.reload_effective", fmtMult(1.0f + reloadBonus / 100.0f)));
        lines.add(tr("gui.tac_rogue.status.autoloader_rate", fmtAutoloaderRate(autoloaderEffect)));
        lines.add(tr("gui.tac_rogue.status.regen_delay",
            String.format(java.util.Locale.ROOT, "%.1fs", GameConstants.REGEN_DAMAGE_COOLDOWN_TICKS / 20.0f)));
        lines.add(tr("gui.tac_rogue.status.ads_stamina_drain", fmtMult(Math.max(0.1f, 1.0f - staminaBonus / 200.0f))));
        int medicalBuffSeconds = com.levanilla.rogue.core.ClientRunState.getMedicalBuffRemainingSeconds();
        if (medicalBuffSeconds > 0) {
            lines.add(tr("gui.tac_rogue.status.medical_buff_remaining", fmtDuration(medicalBuffSeconds)));
        }
        addCursedPenaltyDetailLines(player, lines);
        lines.add(tr("gui.tac_rogue.status.flashlight_level", com.levanilla.rogue.core.ClientRunState.getFlashlightLevel()));
        return lines;
    }

    private static void addCursedPenaltyDetailLines(net.minecraft.client.player.LocalPlayer player, List<String> lines) {
        int[] counts = new int[PerkDefinition.CursedPenaltyTarget.values().length];
        for (String tag : getClientPerkTags(player)) {
            if (!tag.startsWith("perk:")) continue;
            PerkDefinition perk = PerkDefinition.fromTag(tag);
            if (perk.modifier != PerkDefinition.Modifier.CURSED) continue;
            PerkDefinition.CursedPenaltyTarget target = PerkDefinition.resolveCursedPenaltyTarget(player.getUUID(), tag);
            counts[target.ordinal()]++;
        }
        for (PerkDefinition.CursedPenaltyTarget target : PerkDefinition.CursedPenaltyTarget.values()) {
            int count = counts[target.ordinal()];
            if (count <= 0) continue;
            String desc = Component.translatable(target.descriptionKey).getString();
            lines.add(tr("gui.tac_rogue.status.cursed_penalty", count == 1 ? desc : desc + " x" + count));
        }
    }

    private static int countModifier(net.minecraft.client.player.LocalPlayer player, PerkDefinition.Modifier modifier) {
        int count = 0;
        for (String tag : getClientPerkTags(player)) {
            if (tag.startsWith("perk:") && PerkDefinition.fromTag(tag).modifier == modifier) {
                count++;
            }
        }
        return count;
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h, String title, int color) {
        graphics.fill(x, y, x + w, y + h, 0x6607111F);
        graphics.fill(x, y, x + 3, y + h, color);
        graphics.renderOutline(x, y, w, h, 0x6600AAFF);
        graphics.drawString(this.font, title, x + 8, y + 6, color, false);
    }

    private void drawProgressBar(GuiGraphics graphics, int x, int y, int w, int h, float value, float max, int fillColor, int bgColor) {
        graphics.fill(x, y, x + w, y + h, bgColor);
        int fillW = max > 0 ? Math.max(0, Math.min(w, (int)(value / max * w))) : 0;
        graphics.fill(x, y, x + fillW, y + h, fillColor);
        graphics.renderOutline(x, y, w, h, 0x55FFFFFF);
    }

    private void drawMetric(GuiGraphics graphics, int x, int y, String label, String value, int color) {
        graphics.drawString(this.font, "\u00A77" + label, x, y, 0xFF888888, false);
        graphics.drawString(this.font, value, x, y + 10, color, false);
    }

    private static String fmtPercent(float value) {
        return String.format(java.util.Locale.ROOT, "+%.0f%%", value);
    }

    private static String fmtChance(float chancePercent) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", Math.max(0.0f, Math.min(100.0f, chancePercent)));
    }

    private static String fmtOnePercent(float chancePercent) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", Math.max(0.0f, chancePercent));
    }

    private static String fmtMult(float value) {
        return String.format(java.util.Locale.ROOT, "x%.2f", value);
    }

    private static String fmtAutoloaderRate(float effect) {
        if (effect <= 0.0f) return "0.0/s";
        return String.format(java.util.Locale.ROOT, "%.1f/s", PerkDefinition.getAutoloaderRoundsPerSecond(effect));
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static int countClientPerks(net.minecraft.client.player.LocalPlayer player) {
        int total = 0;
        for (String tag : getClientPerkTags(player)) {
            if (tag.startsWith("perk:")) total++;
        }
        return total;
    }

    private static List<String> getClientPerkTags(net.minecraft.client.player.LocalPlayer player) {
        java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>(ClientRunState.getPerkTags());
        if (player != null) {
            for (String tag : player.getTags()) {
                if (tag.startsWith("perk:")) tags.add(tag);
            }
        }
        return new ArrayList<>(tags);
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String fmtDuration(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format(java.util.Locale.ROOT, "%d:%02d", safe / 60, safe % 60);
    }

    private static String categoryInitial(com.levanilla.rogue.core.registry.ShopCatalog.Category category) {
        String label = tr("gui.tac_rogue.shop_screen.category." + category.name().toLowerCase(java.util.Locale.ROOT));
        return label.isEmpty() ? category.name().substring(0, 1) : label.substring(0, 1);
    }

    // === Perk grouping data class ===
    private record PerkGroupEntry(String categoryKey, PerkDefinition samplePerk, int count, float totalEffect,
                                  List<PerkDefinition> perks, List<String> tags) {}

    private List<PerkGroupEntry> buildPerkGroups(net.minecraft.client.player.LocalPlayer player) {
        List<String> perkTags = new ArrayList<>();
        for (String tag : getClientPerkTags(player)) {
            if (tag.startsWith("perk:")) {
                perkTags.add(tag);
            }
        }
        perkTags.sort(String::compareTo);
        String signature = String.join("|", perkTags);
        if (signature.equals(cachedPerkTagSignature)) {
            return cachedPerkGroups;
        }

        Map<String, List<PerkDefinition>> grouped = new LinkedHashMap<>();
        Map<String, List<String>> groupedTags = new LinkedHashMap<>();
        for (String tag : perkTags) {
            PerkDefinition perk = PerkDefinition.fromTag(tag);
            String key = perk.category.name(); // modifierの違いを無視してカテゴリでまとめる
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(perk);
            groupedTags.computeIfAbsent(key, k -> new ArrayList<>()).add(tag);
        }
        List<PerkGroupEntry> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<PerkDefinition> perks = entry.getValue();
            float totalEffect = 0;
            for (PerkDefinition p : perks) totalEffect += p.calculateEffect();
            result.add(new PerkGroupEntry(entry.getKey(), perks.get(0), perks.size(), totalEffect, perks, groupedTags.get(entry.getKey())));
        }
        result.sort(java.util.Comparator.comparing(g -> g.samplePerk().category.displayName.toLowerCase(java.util.Locale.ROOT)));
        cachedPerkTagSignature = signature;
        cachedPerkGroups = result;
        return cachedPerkGroups;
    }

    private void renderPerksTab(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int x, int y) {
        int rightX = x + 10;
        List<PerkGroupEntry> groups = buildPerkGroups(player);

        // Header with count
        int totalPerks = 0;
        for (PerkGroupEntry g : groups) totalPerks += g.count();
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.adaptations"), rightX, y + 8, 0xFF00AAFF, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.perk_count", totalPerks, groups.size()), rightX + 88, y + 8, 0xFF888888, false);

        int perkY = y + 32;
        int maxVisible = 6;
        int maxScroll = Math.max(0, groups.size() - maxVisible);
        if (perkScrollOffset > maxScroll) perkScrollOffset = maxScroll;

        // Scroll indicator
        if (perkScrollOffset > 0) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.inventory.scroll_up"), rightX + 140, perkY - 10, 0xFF555555);
        }

        int rendered = 0;
        for (int i = perkScrollOffset; i < groups.size() && rendered < maxVisible; i++) {
            PerkGroupEntry group = groups.get(i);
            PerkDefinition perk = group.samplePerk();
            int py = perkY + rendered * 25;
            int rarityColor = perk.getRarityColor();
            boolean hovered = (hoveredPerkIndex == i);

            // Card background
            int cardW = 280;
            int bgColor = hovered ? 0x66446655 : 0x3307111F;
            graphics.fill(rightX, py, rightX + cardW, py + 23, bgColor);
            graphics.fill(rightX, py, rightX + 3, py + 23, rarityColor | 0xFF000000);
            graphics.renderOutline(rightX, py, cardW, 23, hovered ? 0xAAFFFFFF : 0x4400AAFF);

            // Perk name
            String displayName = trim(perk.category.displayName, 19);
            graphics.drawString(this.font, displayName, rightX + 8, py + 3, rarityColor | 0xFF000000, false);

            // Count badge
            if (group.count() > 1) {
                String countBadge = "x" + group.count();
                graphics.drawString(this.font, "\u00A7e" + countBadge, rightX + 112, py + 3, 0xFFFFCC00, false);
            }

            String effectStr = formatEffectivePerkValue(player, perk.category, group.totalEffect());
            graphics.drawString(this.font, "\u00A7a" + effectStr, rightX + 8, py + 13, 0xFF88FF88, false);

            // Modifier tag
            PerkDefinition.Modifier strongest = getStrongestModifier(group.perks());
            boolean risky = group.perks().stream().anyMatch(this::isRiskyModifier);
            if (strongest != PerkDefinition.Modifier.NONE) {
                String modText = (risky ? "\u00A7c! " : "\u00A77") + strongest.prefix.toUpperCase(java.util.Locale.ROOT);
                int modW = this.font.width(modText);
                graphics.drawString(this.font, modText, rightX + cardW - modW - 8, py + 4, strongest.color, false);
                if (risky) {
                    graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.risk_modifier"), rightX + cardW - 76, py + 14, 0xFFFF7777, false);
                }
            }
            rendered++;
        }

        if (groups.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.no_perks"), rightX + 5, perkY, 0xFF555555, false);
        }

        // Scroll down indicator
        if (perkScrollOffset + maxVisible < groups.size()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.inventory.scroll_down", groups.size() - perkScrollOffset - maxVisible),
                rightX + 140, perkY + maxVisible * 25 + 2, 0xFF555555);
        }

        // === Cursed penalty summary ===
        int cursedCount = 0;
        List<String> cursedTags = new ArrayList<>();
        for (String tag : getClientPerkTags(player)) {
            if (tag.startsWith("perk:") && tag.contains(":CURSED:")) {
                cursedTags.add(tag);
                cursedCount++;
            }
        }
        if (cursedCount > 0) {
            int cursedStartOffset = Math.max(0, perkScrollOffset - groups.size());
            int visibleCursed = Math.max(0, cursedCount - cursedStartOffset);
            int maxSummaryLines = Math.min(visibleCursed, 7 - rendered);
            if (maxSummaryLines > 0) {
                int summaryY = perkY + rendered * 25 + 4;
                int panelBottom = y + imageHeight + 25; // パネル下限（余白考慮）
                // パネルからはみ出さないよう表示行数を制限
                int availableHeight = panelBottom - summaryY - 14;
                int effectiveLines = Math.min(maxSummaryLines, Math.max(0, availableHeight / 10));
                if (effectiveLines > 0 && summaryY < panelBottom) {
                    graphics.fill(rightX, summaryY - 2, rightX + 280, summaryY + 12 + effectiveLines * 10, 0x44990033);
                    graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.cursed_count", cursedCount),
                        rightX + 4, summaryY, 0xFFFF4444, false);
                    int lineY = summaryY + 12;
                    int shown = 0;

                    for (int i = cursedStartOffset; i < cursedTags.size() && shown < effectiveLines; i++) {
                        if (lineY + 10 > panelBottom) break; // パネル外なら描画しない
                        String penalty = getCursedPenaltyText(player, cursedTags.get(i));
                        graphics.pose().pushPose();
                        graphics.pose().translate(rightX + 8, lineY, 0);
                        graphics.pose().scale(0.75f, 0.75f, 1.0f); // 少し小さくしてはみ出し防止
                        graphics.drawString(this.font, penalty, 0, 0, 0xFFCC6666, false);
                        graphics.pose().popPose();
                        lineY += 10;
                        shown++;
                    }
                }
            }
        }
    }

    private PerkDefinition.Modifier getStrongestModifier(List<PerkDefinition> perks) {
        PerkDefinition.Modifier strongest = PerkDefinition.Modifier.NONE;
        for (PerkDefinition perk : perks) {
            if (perk.modifier.tier > strongest.tier || perk.modifier.multiplier > strongest.multiplier) {
                strongest = perk.modifier;
            }
        }
        return strongest;
    }

    private boolean isRiskyModifier(PerkDefinition perk) {
        return perk.modifier == PerkDefinition.Modifier.CURSED
            || perk.modifier == PerkDefinition.Modifier.VOLATILE
            || perk.modifier == PerkDefinition.Modifier.CORRUPTED
            || perk.modifier == PerkDefinition.Modifier.OVERCLOCKED
            || perk.modifier == PerkDefinition.Modifier.FRACTURED
            || perk.modifier == PerkDefinition.Modifier.TITANIC;
    }

    /** Cursed パークのペナルティテキストを返す */
    private String getCursedPenaltyText(net.minecraft.client.player.LocalPlayer player, String perkTag) {
        return "\u00A7c" + PerkDefinition.getCursedPenaltyDescription(player.getUUID(), perkTag).getString();
    }

    /** パークホバー時のツールチップ (グループ化対応) */
    private void renderPerkTooltip(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int mouseX, int mouseY) {
        List<PerkGroupEntry> groups = buildPerkGroups(player);
        if (hoveredPerkIndex < 0 || hoveredPerkIndex >= groups.size()) return;
        PerkGroupEntry group = groups.get(hoveredPerkIndex);
        PerkDefinition perk = group.samplePerk();
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal("\u00A7e" + perk.getDisplayName()));
        tooltip.add(perk.getDescriptionComponent());
        if (group.count() > 1) {
            tooltip.add(Component.literal("\u00A7b\u00D7 " + group.count() + " stacked"));
        }
        // Per-unit and total effect
        float unitEffect = perk.calculateEffect();
        String unitStr;
        if (perk.category == PerkDefinition.Category.REGENERATION || perk.category == PerkDefinition.Category.VAMPIRE) {
            unitStr = String.format("+%.1f", unitEffect / 10f);
        } else if (perk.category == PerkDefinition.Category.AUTOLOADER) {
            unitStr = fmtAutoloaderRate(unitEffect);
        } else {
            unitStr = String.format("+%.0f%%", unitEffect);
        }

        String totalStr = formatEffectivePerkValue(player, perk.category, group.totalEffect());
        tooltip.add(Component.translatable("gui.tac_rogue.perk.tooltip.unit_total", unitStr, totalStr));

        boolean hasModifiers = false;
        for (int i = 0; i < group.perks().size(); i++) {
            PerkDefinition pk = group.perks().get(i);
            if (pk.modifier != PerkDefinition.Modifier.NONE) {
                if (!hasModifiers) {
                    tooltip.add(Component.translatable("gui.tac_rogue.perk.tooltip.modifiers"));
                    hasModifiers = true;
                }
                net.minecraft.network.chat.MutableComponent modLine = Component.literal("  " + pk.modifier.prefix).withStyle(s -> s.withColor(pk.modifier.color));
                if (!pk.modifier.tradeoff.isEmpty()) {
                    modLine.append(Component.literal(" \u00A77- ").append(pk.getModifierDescriptionComponent()));
                }
                tooltip.add(modLine);
                if (pk.modifier == PerkDefinition.Modifier.CURSED) {
                    String perkTag = i < group.tags().size() ? group.tags().get(i) : pk.toTag();
                    tooltip.add(Component.literal("   " + getCursedPenaltyText(player, perkTag)));
                }
            }
        }

        tooltip.add(Component.translatable("gui.tac_rogue.perk.tooltip.level", perk.level));
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    /** クライアント側パーク効果合算 */
    private static float sumClientPerkEffect(net.minecraft.client.player.LocalPlayer player, String perkPrefix) {
        float total = 0;
        PerkDefinition.Category category = categoryFromPerkPrefix(perkPrefix);
        for (String tag : getClientPerkTags(player)) {
            if (tag.startsWith(perkPrefix)) {
                PerkDefinition perk = PerkDefinition.fromTag(tag);
                total += perk.calculateEffect();
                if (category == null) {
                    category = perk.category;
                }
            }
        }
        return category == null ? total : PerkDefinition.softcapTotalEffect(category, total);
    }

    private static PerkDefinition.Category categoryFromPerkPrefix(String perkPrefix) {
        if (perkPrefix == null || !perkPrefix.startsWith("perk:")) return null;
        String rest = perkPrefix.substring("perk:".length());
        int colon = rest.indexOf(':');
        String categoryName = colon >= 0 ? rest.substring(0, colon) : rest;
        try {
            return PerkDefinition.Category.valueOf(categoryName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String formatEffectivePerkValue(net.minecraft.client.player.LocalPlayer player, PerkDefinition.Category category, float totalEffect) {
        return switch (category) {
            case AMMO_EFFICIENCY -> tr("gui.tac_rogue.status.summary.ammo_save", fmtOnePercent(effectiveAmmoSaveChance(player)));
            case DODGE -> tr("gui.tac_rogue.status.summary.dodge",
                fmtOnePercent(Math.min(GameConstants.DODGE_MAX_CHANCE * 100.0f,
                    effectiveStackedChance(player, category, 1.0f))));
            case SCAVENGER -> {
                float drop = com.levanilla.rogue.core.GameConstants.DROP_BASE_CHANCE
                    * com.levanilla.rogue.core.DifficultyManager.getDropMultiplier()
                    * (1.0f + totalEffect / 100.0f);
                yield tr("gui.tac_rogue.status.summary.scavenger", fmtOnePercent(Math.min(100.0f, drop * 100.0f)));
            }
            case REGENERATION, VAMPIRE, QUICK_FIX -> tr("gui.tac_rogue.status.summary.effect",
                String.format(java.util.Locale.ROOT, "+%.1f", totalEffect / 10.0f));
            case GUN_PROFICIENCY -> String.format(java.util.Locale.ROOT,
                "body x%.2f / head +%.0f%%",
                1.0f + totalEffect * 0.18f / 100.0f,
                Math.min(35.0f, totalEffect * 0.35f));
            case FIRE_RATE -> tr("gui.tac_rogue.status.summary.fire_rate",
                fmtMult(1.0f + WeaponRarity.getFireRatePerkBonusPercent(player) / 100.0f));
            case MELEE_SPEED -> tr("gui.tac_rogue.status.summary.melee_speed",
                fmtMult(1.0f + WeaponRarity.getMeleeSpeedPerkBonusPercent(player) / 100.0f));
            case STAMINA, DAMAGE, GOLD_RUSH, HANDLING, FORTUNE -> fmtMult(1.0f + totalEffect / 100.0f);
            case RESISTANCE -> fmtMult(specialResistanceTaken(totalEffect));
            case RELOAD_SPEED -> tr("gui.tac_rogue.status.summary.reload", fmtPercent(totalEffect));
            case AUTOLOADER -> tr("gui.tac_rogue.status.summary.autoloader", fmtAutoloaderRate(totalEffect));
            default -> tr("gui.tac_rogue.status.summary.total", "+" + (int) totalEffect + "%");
        };
    }

    private static float effectiveAmmoSaveChance(net.minecraft.client.player.LocalPlayer player) {
        return Math.min(GameConstants.AMMO_SAVE_MAX_CHANCE * 100.0f,
            effectiveStackedChance(player, PerkDefinition.Category.AMMO_EFFICIENCY, 1.0f));
    }

    private static float specialResistanceTaken(float totalEffect) {
        float resist = Math.min(totalEffect / 100.0f, GameConstants.SPECIAL_RESISTANCE_MAX);
        return Math.max(0.0f, 1.0f - Math.max(0.0f, resist));
    }

    private static float effectiveStackedChance(net.minecraft.client.player.LocalPlayer player, PerkDefinition.Category category, float scale) {
        float total = sumClientPerkEffect(player, "perk:" + category.name());
        return Math.max(0.0f, total * scale);
    }

    private com.levanilla.rogue.core.registry.ShopCatalog.Category shopCategoryFilter = null; // null = ALL
    private boolean sellMode = false;
    private int hoveredShopItem = -1;
    private int hoveredInvSlot = -1;
    private int hoveredPerkIndex = -1;
    private int hoveredQuestIndex = -1;
    private List<ShopCatalog.ShopItem> cachedVisibleShopItems = List.of();
    private List<ShopCatalog.ShopItem> cachedVisibleShopSource = null;
    private ShopCatalog.Category cachedVisibleShopCategory = null;
    private int cachedVisibleShopFloor = Integer.MIN_VALUE;
    private int cachedVisibleShopBlackMarket = Integer.MIN_VALUE;
    private List<String> cachedShopHeldGunIds = List.of();
    private String cachedShopHeldGunSignature = "";
    private final Map<String, ShopCompatibility> shopCompatibilityCache = new HashMap<>();
    private List<PerkGroupEntry> cachedPerkGroups = List.of();
    private String cachedPerkTagSignature = "";

    private void renderQuestTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredQuestIndex < 0 || hoveredQuestIndex >= cachedQuests.size()) return;
        String[] quest = cachedQuests.get(hoveredQuestIndex);
        String questId = quest[0];
        String typeName = quest[1];
        String target = quest.length > 2 ? quest[2] : "0";
        String progress = quest.length > 3 ? quest[3] : "0";
        String gold = quest.length > 4 ? quest[4] : "0";
        String roleName = quest.length > 6 ? quest[6] : "quest.tac_rogue.role.contract";
        boolean rareWeaponReward = quest.length > 7 && "true".equals(quest[7]);

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(typeName).withStyle(s -> s.withColor(0xFFE5FF)));
        tooltip.add(Component.translatable(roleName).withStyle(s -> s.withColor(0x88AAFF)));

        String langKey = "quest.tac_rogue." + questId;
        String typeDescKey = typeName + ".desc";
        
        if (net.minecraft.client.resources.language.I18n.exists(langKey)) {
            // Story Quest descriptions
            tooltip.add(Component.translatable(langKey).withStyle(s -> s.withColor(0xAAAAAA)));
        } else if (net.minecraft.client.resources.language.I18n.exists(typeDescKey)) {
            // Type-based dynamic descriptions (e.g., "Clear floor in %s seconds")
            tooltip.add(Component.translatable(typeDescKey, target).withStyle(s -> s.withColor(0xAAAAAA)));
            tooltip.add(Component.translatable("gui.tac_rogue.quest_screen.reward_gold", gold).withStyle(s -> s.withColor(0xFFD700)));
        } else {
            // Ultimate fallback
            tooltip.add(Component.translatable("gui.tac_rogue.quest_screen.target", target).withStyle(s -> s.withColor(0xAAAAAA)));
            tooltip.add(Component.translatable("gui.tac_rogue.quest_screen.reward_gold", gold).withStyle(s -> s.withColor(0xFFD700)));
        }

        // Add Progress Information
        tooltip.add(Component.translatable("gui.tac_rogue.quest_screen.progress", progress, target).withStyle(s -> s.withColor(0x00E5FF)));
        if (rareWeaponReward) {
            tooltip.add(Component.translatable("quest.tac_rogue.reward.rare_weapon").withStyle(s -> s.withColor(0xFF66DD)));
        }
        
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private void refreshShopHeldGunCache(net.minecraft.client.player.LocalPlayer player) {
        List<String> heldGunIds = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        if (player != null) {
            for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
                net.minecraft.world.item.ItemStack invStack = player.getInventory().getItem(s);
                if (invStack.isEmpty() || !invStack.hasTag()) continue;
                net.minecraft.nbt.CompoundTag tag = invStack.getTag();
                if (!tag.contains("GunId")) continue;
                String gunId = tag.getString("GunId");
                heldGunIds.add(gunId);
                signature.append(s).append(':').append(gunId).append(';');
            }
        }

        String nextSignature = signature.toString();
        if (!nextSignature.equals(cachedShopHeldGunSignature)) {
            cachedShopHeldGunSignature = nextSignature;
            cachedShopHeldGunIds = List.copyOf(heldGunIds);
            shopCompatibilityCache.clear();
        }
    }

    private ShopCompatibility shopCompatibilityFor(ShopCatalog.ShopItem item) {
        if (item.category != ShopCatalog.Category.AMMO && item.category != ShopCatalog.Category.ATTACHMENT) {
            return ShopCompatibility.NONE;
        }
        if (cachedShopHeldGunIds.isEmpty()) return ShopCompatibility.NONE;

        String key = item.category.name() + "|" + item.id;
        ShopCompatibility cached = shopCompatibilityCache.get(key);
        if (cached != null) return cached;

        for (String gunId : cachedShopHeldGunIds) {
            boolean compatible = item.category == ShopCatalog.Category.AMMO
                ? item.id.equals(TacZRegistryHelper.getAmmoForGun(gunId))
                : com.levanilla.rogue.core.registry.AttachmentDatabase.isCompatible(gunId, item.id);
            if (compatible) {
                ShopCompatibility result = new ShopCompatibility(true, compactGunName(gunId));
                shopCompatibilityCache.put(key, result);
                return result;
            }
        }

        shopCompatibilityCache.put(key, ShopCompatibility.NONE);
        return ShopCompatibility.NONE;
    }

    private static String compactGunName(String gunId) {
        String name = gunId.contains(":") ? gunId.substring(gunId.indexOf(':') + 1) : gunId;
        return name.toUpperCase(java.util.Locale.ROOT);
    }

    private record ShopCompatibility(boolean compatible, String gunName) {
        private static final ShopCompatibility NONE = new ShopCompatibility(false, "");
    }

    private void renderShopTab(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int x, int y) {
        int panelLeft = x - 110;
        int panelRight = x + imageWidth + 110;
        int catPanelW = 65;
        int catPanelX = panelLeft;
        int listX = catPanelX + catPanelW + 4;
        int gold = RunManager.getClientGold();

        // Header bar — タブバーの下に配置 (被り回避)
        Component modeLabel = Component.translatable(sellMode ? "gui.tac_rogue.shop_screen.mode_sell" : "gui.tac_rogue.inventory.shop");
        graphics.drawString(this.font, modeLabel, listX, y - 4, sellMode ? 0xFFFF4444 : 0xFFFFCC00, false);
        graphics.drawString(this.font, "\u00A76$ " + gold, listX + 50, y - 4, 0xFFFFD700, false);

        if (sellMode) {
            // === Sell mode ===
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.shop_screen.select_item"), listX, y + 5, 0xFFAAAAAA, false);
            int listY = y + 18;
            int slotIdx = 0;
            int maxSellItems = 10;
            for (int i = 0; i < player.getInventory().getContainerSize() && slotIdx < maxSellItems; i++) {
                net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                if (stack.is(net.minecraft.world.item.Items.BARRIER) && stack.hasTag() && stack.getOrCreateTag().getBoolean("rogue_item_locked")) continue;

                int sellPrice = com.levanilla.rogue.core.PriceManager.getSellPrice(stack);
                if (sellPrice <= 0) continue;

                int iy = listY + (slotIdx * 14);
                boolean hovered = (hoveredShopItem == slotIdx);
                int itemWidth = panelRight - listX - 55;

                if (hovered) {
                    graphics.fill(listX, iy - 1, listX + itemWidth, iy + 12, 0x33FF4444);
                }

                String name = stack.getHoverName().getString();
                if (name.length() > 22) name = name.substring(0, 22) + "..";
                String label = "\u00A7c\u25CF \u00A7f" + name + " \u00A77[\u00A76$" + sellPrice + "\u00A77]";

                graphics.pose().pushPose();
                graphics.pose().translate(listX + 3, iy + 1, 0);
                graphics.pose().scale(0.75f, 0.75f, 1.0f);
                graphics.drawString(this.font, label, 0, 0, hovered ? 0xFFFF6666 : 0xFFCCCCCC, false);
                graphics.pose().popPose();
                slotIdx++;
            }
            if (slotIdx == 0) {
                graphics.drawString(this.font, Component.translatable("gui.tac_rogue.shop_screen.no_sellable_items"), listX, listY, 0xFF555555, false);
            }
        } else {
            // === Purchase mode ===
            // Category panel background only (no text - TacticalButtons handle rendering)
            com.levanilla.rogue.core.registry.ShopCatalog.Category[] cats = com.levanilla.rogue.core.registry.ShopCatalog.Category.values();
            int tabH = 14;
            int catTabY = y + 5;
            graphics.fill(catPanelX, catTabY - 2, catPanelX + catPanelW, catTabY + (cats.length + 1) * tabH + 2, 0x88001122);
            graphics.renderOutline(catPanelX, catTabY - 2, catPanelW, (cats.length + 1) * tabH + 4, 0xAA00AAFF);

            // Item list
            List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> items = getVisibleShopItems();
            int maxPages = items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;
            if (shopPage > maxPages) shopPage = maxPages;
            // Page indicator — ヘッダー右寄りに配置（金額と被らない）
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.shop_screen.page", shopPage + 1, maxPages + 1),
                panelRight - 90, y - 10, 0xFFAAAAAA, false);

            refreshShopHeldGunCache(player);

            int startIndex = shopPage * ITEMS_PER_PAGE;
            int listY = y + 8;
            int itemWidth = panelRight - listX - 55;
            for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < items.size(); i++) {
                com.levanilla.rogue.core.registry.ShopCatalog.ShopItem item = items.get(startIndex + i);
                boolean hovered = (hoveredShopItem == i);
                int iy = listY + (i * 18);

                ShopCompatibility compatibility = shopCompatibilityFor(item);
                boolean compatible = compatibility.compatible();
                String compatGunName = compatibility.gunName();

                if (hovered) {
                    graphics.fill(listX, iy - 1, listX + itemWidth, iy + 15, 0x33FFFF00);
                } else if (compatible) {
                    graphics.fill(listX, iy - 1, listX + itemWidth, iy + 15, 0x2200FF00);
                }

                int actualPrice = item.price;
                if (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SPECIAL) {
                    int currentLevel = 0;
                    if (item.id.equals("rogue:ammo_capacity_upgrade")) currentLevel = com.levanilla.rogue.core.RunManager.getClientAmmoCapacityLevel();
                    else if (item.id.equals("rogue:inv_upgrade")) {
                        if (player != null) currentLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
                    }
                    else if (item.id.equals("rogue:stash_upgrade")) currentLevel = Math.max(0, com.levanilla.rogue.core.RunManager.getClientStashLines() - 2);
                    else if (item.id.equals("rogue:melee_upgrade")) {
                        if (player != null) currentLevel = player.getPersistentData().getInt("TacRogueMeleeLevel");
                    }
                    else if (item.id.equals("rogue:flashlight_upgrade")) currentLevel = com.levanilla.rogue.core.RunManager.getClientFlashlightLevel();
                    else if (item.id.equals("rogue:random_perk")) {
                        if (player != null) currentLevel = player.getPersistentData().getInt("RandomPerkBuys");
                    }
                    actualPrice = com.levanilla.rogue.core.PriceManager.getUpgradePrice(item.id, currentLevel);
                }

                boolean canAfford = gold >= actualPrice;
                String catTag = "\u00A77[" + categoryInitial(item.category) + "] ";
                String nameColor = compatible ? "\u00A7a" : (canAfford ? "\u00A7f" : "\u00A78");
                String priceColor = canAfford ? "\u00A7e" : "\u00A7c";
                String label = catTag + nameColor + shopItemName(item).getString() + " " + priceColor + "$" + actualPrice;
                if (compatible) label = "\u00A7a\u2714 " + label + " \u00A72[" + compatGunName + "]";

                if (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.AMMO) {
                    int stackSize = TacZRegistryHelper.getAmmoStackSize(item.id);
                    label += " \u00A77x" + stackSize;
                }
                if (item.category.isWeapon()) {
                    int magSize = TacZRegistryHelper.getMagazineSize(item.id);
                    String ammoType = TacZRegistryHelper.getAmmoForGun(item.id);
                    String ammoShort = ammoType.contains(":") ? ammoType.substring(ammoType.indexOf(':') + 1) : ammoType;
                    label += " \u00A77[" + tr("gui.tac_rogue.shop_screen.mag_rounds", magSize) + "/" + ammoShort + "]";
                }

                graphics.pose().pushPose();
                graphics.pose().translate(listX + 3, iy + 2, 0);
                graphics.pose().scale(0.80f, 0.80f, 1.0f);
                graphics.drawString(this.font, label, 0, 0, hovered ? 0xFFFFFF00 : item.category.color, false);
                graphics.pose().popPose();
            }
        }
    }

    private void renderFloorInfoTab(GuiGraphics graphics, int x, int y) {
        int rightX = x + 10;
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.mission_intel"), rightX, y + 10, 0xFF00AAFF, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.theme", RunManager.getCurrentThemeName()), rightX + 5, y + 26, 0xFFFFFFFF, false);
        ClientRunState.ObjectiveState objective = ClientRunState.getObjectiveState();
        if (objective == null) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.objective_unknown"), rightX + 5, y + 42, 0xFF888888, false);
            return;
        }

        String objectiveName = objectiveTitle(objective).getString();
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.objective_line", objectiveName), rightX + 5, y + 42, 0xFFFFFFFF, false);
        graphics.drawString(this.font, objectiveStatus(objective), rightX + 5, y + 55, 0xFFB8C7D0, false);
        graphics.drawString(this.font, objectiveProgressLine(objective), rightX + 5, y + 68, 0xFFFFD166, false);
        if (!"ELIMINATE".equals(objective.type()) && objective.hasDirection()) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.objective_target",
                objectiveDirectionLabel(objective)), rightX + 5, y + 81, 0xFF9BEAFF, false);
        }
    }

    private static Component objectiveTitle(ClientRunState.ObjectiveState objective) {
        String type = objective.type() == null || objective.type().isBlank() ? "ELIMINATE" : objective.type();
        return Component.translatable("objective.tac_rogue." + type.toLowerCase(java.util.Locale.ROOT) + ".title");
    }

    private static Component objectiveStatus(ClientRunState.ObjectiveState objective) {
        String key = objective.statusKey();
        if (key != null && !key.isBlank()) {
            return Component.translatable(key);
        }
        String type = objective.type() == null || objective.type().isBlank() ? "ELIMINATE" : objective.type();
        return Component.translatable("objective.tac_rogue." + type.toLowerCase(java.util.Locale.ROOT) + ".description");
    }

    private static Component objectiveProgressLine(ClientRunState.ObjectiveState objective) {
        String type = objective.type();
        if ("SECURE_TERMINAL".equals(type) || "HOLD_POSITION".equals(type)) {
            return Component.translatable("gui.tac_rogue.inventory.objective_seconds",
                Math.max(0, objective.progress() / 20),
                Math.max(1, objective.target() / 20));
        }
        if ("ELIMINATE".equals(type)) {
            int remaining = Math.max(0, objective.target() - objective.progress());
            return Component.translatable("gui.tac_rogue.inventory.objective_remaining", remaining);
        }
        return Component.translatable("gui.tac_rogue.inventory.objective_progress",
            objective.progress(), objective.target());
    }

    private static Component objectiveDirectionLabel(ClientRunState.ObjectiveState objective) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || !objective.hasDirection()) {
            return Component.translatable("gui.tac_rogue.direction.front");
        }
        double length = Math.sqrt(objective.dx() * objective.dx() + objective.dz() * objective.dz());
        if (length < 0.0001D) return Component.translatable("gui.tac_rogue.direction.front");
        double dirX = objective.dx() / length;
        double dirZ = objective.dz() / length;
        double yawRad = Math.toRadians(mc.player.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        double forward = dirX * forwardX + dirZ * forwardZ;
        double right = dirX * rightX + dirZ * rightZ;
        if (Math.abs(forward) >= Math.abs(right)) {
            return forward >= 0.0D
                ? Component.translatable("gui.tac_rogue.direction.front")
                : Component.translatable("gui.tac_rogue.direction.back");
        }
        return right >= 0.0D
            ? Component.translatable("gui.tac_rogue.direction.right")
            : Component.translatable("gui.tac_rogue.direction.left");
    }

    @Override
    protected void init() {
        super.init();
        fitScreenOrigin();
        layoutRogueInventorySlots();
        int x = this.leftPos;
        int y = this.topPos;
        int sidePad = sidePad();
        this.tabX = x - sidePad + 10;
        this.tabY = Math.max(4, y - 20);

        addTacticalButton(tabX - 6, tabY, 65, 15, Component.translatable("gui.tac_rogue.inventory.inventory"), b -> { activeTab = Tab.INVENTORY; this.init(this.minecraft, this.width, this.height); });
        addTacticalButton(tabX + 61, tabY, 50, 15, Component.translatable("gui.tac_rogue.inventory.status"), b -> { activeTab = Tab.STATUS; this.init(this.minecraft, this.width, this.height); });
        addTacticalButton(tabX + 113, tabY, 50, 15, Component.translatable("gui.tac_rogue.inventory.perks"), b -> { activeTab = Tab.PERKS; this.init(this.minecraft, this.width, this.height); });
        addTacticalButton(tabX + 165, tabY, 50, 15, Component.translatable("gui.tac_rogue.inventory.floor_info"), b -> { activeTab = Tab.FLOOR_INFO; this.init(this.minecraft, this.width, this.height); });

        if (activeTab == Tab.SHOP) {
            int panelLeft = x - 110;
            int panelRight = x + imageWidth + 110;
            int catPanelW = 65;
            int catPanelX = panelLeft;
            int listX = catPanelX + catPanelW + 4;

            // BUY/SELL toggle button — ヘッダー右端に配置
            Component modeText = Component.translatable(sellMode ? "gui.tac_rogue.shop_screen.mode_buy_arrow" : "gui.tac_rogue.shop_screen.mode_sell_arrow");
            addTacticalButton(panelRight - 55, y - 12, 48, 12, modeText, b -> {
                sellMode = !sellMode;
                shopPage = 0;
                this.init(this.minecraft, this.width, this.height);
            });

            if (sellMode) {
                // === Sell mode ===
                net.minecraft.client.player.LocalPlayer p = this.minecraft.player;
                if (p != null) {
                    int slotIdx = 0;
                    int maxSellItems = 10;
                    for (int i = 0; i < p.getInventory().getContainerSize() && slotIdx < maxSellItems; i++) {
                        net.minecraft.world.item.ItemStack stack = p.getInventory().getItem(i);
                        if (stack.isEmpty()) continue;
                        if (stack.is(net.minecraft.world.item.Items.BARRIER) && stack.hasTag() && stack.getOrCreateTag().getBoolean("rogue_item_locked")) continue;
                        int sellPrice = com.levanilla.rogue.core.PriceManager.getSellPrice(stack);
                        if (sellPrice <= 0) continue;

                        final int slot = i;
                        addTacticalButton(panelRight - 60, y + 18 + (slotIdx * 14), 48, 12, Component.translatable("gui.tac_rogue.shop_screen.sell_short"), b -> {
                            com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                                new com.levanilla.rogue.networking.RogueActionMessage(
                                    com.levanilla.rogue.networking.RogueActionMessage.ActionType.SELL_ITEM, String.valueOf(slot)));
                            this.init(this.minecraft, this.width, this.height);
                        });
                        slotIdx++;
                    }
                }
            } else {
                // === Purchase mode ===
                // Category buttons on left side (below main tab bar)
                com.levanilla.rogue.core.registry.ShopCatalog.Category[] cats = com.levanilla.rogue.core.registry.ShopCatalog.Category.values();
                int tabH = 14;
                int catTabY = y + 5;
                // ALL button
                addTacticalButton(catPanelX + 2, catTabY, catPanelW - 4, tabH - 1, Component.translatable("gui.tac_rogue.shop_screen.all"), b -> {
                    shopCategoryFilter = null; shopPage = 0;
                    this.init(this.minecraft, this.width, this.height);
                });
                // Category buttons
                for (int ci = 0; ci < cats.length; ci++) {
                    final com.levanilla.rogue.core.registry.ShopCatalog.Category cat = cats[ci];
                    addTacticalButton(catPanelX + 2, catTabY + (ci + 1) * tabH, catPanelW - 4, tabH - 1,
                        Component.translatable("gui.tac_rogue.shop_screen.category." + cat.name().toLowerCase(java.util.Locale.ROOT)), b -> {
                        shopCategoryFilter = cat; shopPage = 0;
                        this.init(this.minecraft, this.width, this.height);
                    });
                }

                // BUY buttons for item list
                List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> items = getVisibleShopItems();

                int startIndex = shopPage * ITEMS_PER_PAGE;
                int listY = y + 8;  // renderShopTabと同一のlistY
                for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < items.size(); i++) {
                    com.levanilla.rogue.core.registry.ShopCatalog.ShopItem item = items.get(startIndex + i);
                    String buyId = item.id;
                    addTacticalButton(panelRight - 48, listY + (i * 18), 40, 14, Component.translatable("gui.tac_rogue.shop_screen.buy_short"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.BUY_ITEM, buyId));
                    });
                }

                // Pagination buttons
                int maxPages = items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;
                if (shopPage > 0) addTacticalButton(listX, y + imageHeight + 10, 50, 15, Component.translatable("gui.tac_rogue.common.prev"), b -> { shopPage--; this.init(this.minecraft, this.width, this.height); });
                if (shopPage < maxPages) addTacticalButton(listX + 55, y + imageHeight + 10, 50, 15, Component.translatable("gui.tac_rogue.common.next"), b -> { shopPage++; this.init(this.minecraft, this.width, this.height); });
            }
        }

        // Lobby check
        boolean inLobby = false;
        if (this.minecraft != null && this.minecraft.level != null) {
            inLobby = this.minecraft.level.dimension().location().toString().equals("tac_rogue:lobby_dimension");
        }

        // ショップ画面/クエスト画面以外の時のみ Floor ボタンを表示
        if (activeTab != Tab.SHOP && activeTab != Tab.QUEST) {
            if (inLobby) {
                // MISSION START button (INVENTORYタブ等でのみ表示)
                int actionY = bottomActionY(y);
                int actionX = x - sidePad + 10;
                int gap = 5;
                int actionW = Math.max(52, Math.min(88, (imageWidth + sidePad * 2 - 20 - gap * 3) / 4));
                addTacticalButton(actionX, actionY, actionW, 20,
                    Component.translatable("gui.tac_rogue.inventory.join_floor"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.START_NEXT_FLOOR,
                                floorEntryMode()));
                        this.onClose();
                    });
                addTacticalButton(actionX + (actionW + gap), actionY, actionW, 20,
                    Component.translatable(floorEntrySoloMode
                        ? "gui.tac_rogue.floor_select.entry_solo"
                        : "gui.tac_rogue.floor_select.entry_public"), b -> {
                        floorEntrySoloMode = !floorEntrySoloMode;
                        this.init(this.minecraft, this.width, this.height);
                    });
                addTacticalButton(actionX + (actionW + gap) * 2, actionY, actionW, 20,
                    Component.translatable("gui.tac_rogue.floor_select.start_waiting"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.START_WAITING_FLOOR));
                        this.onClose();
                    });
                addTacticalButton(actionX + (actionW + gap) * 3, actionY, actionW, 20,
                    lowHealthButtonText(), b -> {
                        floorLowHealthMode = !floorLowHealthMode;
                        this.init(this.minecraft, this.width, this.height);
                    });
            } else if (RunManager.isFloorCleared()) {
                // Floor cleared -> NEXT FLOOR
                int actionY = bottomActionY(y);
                int actionX = x - sidePad + 10;
                int gap = 6;
                int actionW = dungeonActionButtonWidth(sidePad, gap);
                addTacticalButton(actionX, actionY, actionW, 20,
                    Component.translatable("gui.tac_rogue.inventory.next_floor"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.START_NEXT_FLOOR,
                                floorEntryMode()));
                        this.onClose();
                    });
                addTacticalButton(actionX + actionW + gap, actionY, actionW, 20,
                    lowHealthButtonText(), b -> {
                        floorLowHealthMode = !floorLowHealthMode;
                        this.init(this.minecraft, this.width, this.height);
                    });
                addTacticalButton(actionX + (actionW + gap) * 2, actionY, actionW, 20,
                    Component.translatable("gui.tac_rogue.inventory.return_lobby"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.RETURN_TO_LOBBY));
                        this.onClose();
                    });
            } else if (RunManager.isRunActive()) {
                // Dungeon & not cleared -> RETRY FLOOR
                int actionY = bottomActionY(y);
                int actionX = x - sidePad + 10;
                int gap = 6;
                int actionW = dungeonActionButtonWidth(sidePad, gap);
                addTacticalButton(actionX, actionY, actionW, 20,
                    Component.translatable("gui.tac_rogue.inventory.retry_floor"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.RETRY_FLOOR,
                                floorEntryMode()));
                        this.onClose();
                    });
                addTacticalButton(actionX + actionW + gap, actionY, actionW, 20,
                    lowHealthButtonText(), b -> {
                        floorLowHealthMode = !floorLowHealthMode;
                        this.init(this.minecraft, this.width, this.height);
                    });
                addTacticalButton(actionX + (actionW + gap) * 2, actionY, actionW, 20,
                    Component.translatable("gui.tac_rogue.inventory.return_lobby"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.RETURN_TO_LOBBY));
                        this.onClose();
                    });
            } else if (!inLobby) {
                addTacticalButton(x - sidePad + 10, bottomActionY(y), 96, 20,
                    Component.translatable("gui.tac_rogue.inventory.return_lobby"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.RETURN_TO_LOBBY));
                        this.onClose();
                    });
            }
        }
    }

    private void layoutRogueInventorySlots() {
        if (this.menu == null || this.menu.slots.size() < 46) return;

        // Hide crafting, armor, and offhand slots. The rogue inventory has its own combat layout.
        for (int i = 0; i <= 8; i++) placeSlot(i, -1000, -1000);
        placeSlot(45, -1000, -1000);

        // Combat strip.
        placeSlot(36, 14, 34); // player slot 0: gun 1
        placeSlot(37, 36, 34); // player slot 1: gun 2
        placeSlot(38, 64, 34); // player slot 2: melee

        // Active items: hotbar 3-8 plus extended quick slots 9-11.
        int[] itemMenuSlots = {39, 40, 41, 42, 43, 44, 9, 10, 11};
        for (int i = 0; i < itemMenuSlots.length; i++) {
            placeSlot(itemMenuSlots[i], 100 + (i % 3) * 20, 24 + (i / 3) * 20);
        }

        // Ammo slots: gun 1 reserve then gun 2 reserve.
        for (int i = 0; i < 4; i++) {
            placeSlot(12 + i, 174 + (i % 2) * 20, 24 + (i / 2) * 20);
        }

        // Backpack / expandable inventory: player slots 16-35.
        for (int i = 16; i <= 35; i++) {
            int offset = i - 16;
            placeSlot(i, 14 + (offset % 10) * 20, 116 + (offset / 10) * 20);
        }
    }

    private void fitScreenOrigin() {
        int side = sidePad();
        int minX = Math.min(side, Math.max(0, (this.width - this.imageWidth) / 2));
        int maxX = Math.max(minX, this.width - this.imageWidth - side);
        this.leftPos = clamp(this.leftPos, minX, maxX);
        int minY = Math.min(topPad(), Math.max(0, (this.height - this.imageHeight) / 2));
        int maxY = Math.max(minY, this.height - this.imageHeight - 28);
        this.topPos = clamp(this.topPos, minY, maxY);
    }

    private int sidePad() {
        return Math.max(8, Math.min(110, (this.width - this.imageWidth - 16) / 2));
    }

    private int topPad() {
        return Math.max(8, Math.min(40, (this.height - this.imageHeight - 28) / 2));
    }

    private boolean isCompactLayout() {
        return this.width < 520 || this.height < 300 || sidePad() < 82;
    }

    private int bottomActionY(int y) {
        return Math.min(y + imageHeight + 10, this.height - 24);
    }

    private int dungeonActionButtonWidth(int sidePad, int gap) {
        int available = imageWidth + sidePad * 2 - 20;
        return Math.max(76, Math.min(100, (available - gap * 2) / 3));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String floorEntryMode() {
        String mode = floorEntrySoloMode ? "solo" : "public";
        return floorLowHealthMode ? mode + ":lowhp" : mode;
    }

    private Component lowHealthButtonText() {
        return Component.translatable(floorLowHealthMode
            ? "gui.tac_rogue.floor_select.low_health_on"
            : "gui.tac_rogue.floor_select.low_health_off");
    }

    private void placeSlot(int menuIndex, int x, int y) {
        if (menuIndex < 0 || menuIndex >= this.menu.slots.size()) return;
        net.minecraft.world.inventory.Slot slot = this.menu.slots.get(menuIndex);
        com.levanilla.rogue.mixin.SlotAccessor accessor = (com.levanilla.rogue.mixin.SlotAccessor) slot;
        accessor.tacRogue$setX(x);
        accessor.tacRogue$setY(y);
    }

    private void addTacticalButton(int x, int y, int w, int h, Component text, net.minecraft.client.gui.components.Button.OnPress press) {
        this.addRenderableWidget(new TacticalButton(x, y, w, h, text, press));
    }

    private static class TacticalButton extends net.minecraft.client.gui.components.Button {
        public TacticalButton(int x, int y, int w, int h, Component text, OnPress press) {
            super(x, y, w, h, text, press, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int bgColor = this.isHoveredOrFocused() ? 0xBB00FFFF : 0x8800AAFF;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            graphics.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFFFFFFFF);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String label = TacticalScreenStyle.fitLabel(font, this.getMessage().getString(), Math.max(12, this.width - 8));
            graphics.drawCenteredString(font, Component.literal(label), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, 0xFFFFFFFF);
        }
    }

    /** マウスホイールでスクロール（SHOP/PERKS/QUEST統合） */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (activeTab == Tab.SHOP) {
            List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> items = getVisibleShopItems();
            int maxPages = items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;
            if (delta < 0 && shopPage < maxPages) { shopPage++; this.init(this.minecraft, this.width, this.height); return true; }
            if (delta > 0 && shopPage > 0) { shopPage--; this.init(this.minecraft, this.width, this.height); return true; }
        }
        if (activeTab == Tab.PERKS) {
            if (delta < 0) { perkScrollOffset++; return true; }
            if (delta > 0 && perkScrollOffset > 0) { perkScrollOffset--; return true; }
        }
        if (activeTab == Tab.QUEST) {
            if (delta < 0) { questScrollOffset++; return true; }
            if (delta > 0 && questScrollOffset > 0) { questScrollOffset--; return true; }
        }
        if (activeTab == Tab.STATUS) {
            int step = 18;
            statusScrollOffset += delta < 0 ? step : -step;
            if (statusScrollOffset < 0) statusScrollOffset = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (activeTab != Tab.INVENTORY) return;
        super.slotClicked(slot, slotId, mouseButton, clickType);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (activeTab != Tab.INVENTORY) return;
        int bx = this.leftPos;
        int by = this.topPos;

        // Main inventory area background
        g.fill(bx, by, bx + imageWidth, by + imageHeight, 0x88001122);
        g.fill(bx + 8, by + 18, bx + 88, by + 62, 0x33100000);
        g.renderOutline(bx + 8, by + 18, 80, 44, 0x66AA5533);
        g.fill(bx + 94, by + 18, bx + 164, by + 88, 0x33102010);
        g.renderOutline(bx + 94, by + 18, 70, 70, 0x6644AA55);
        g.fill(bx + 168, by + 18, bx + 218, by + 68, 0x33303010);
        g.renderOutline(bx + 168, by + 18, 50, 50, 0x66AAAA44);
        g.fill(bx + 8, by + 104, bx + 220, by + 160, 0x33202028);
        g.renderOutline(bx + 8, by + 104, 212, 56, 0x66556688);

        // Draw slot backgrounds by type
        for (int i = 0; i < this.menu.slots.size(); i++) {
            if (i <= 8 || i == 45) continue;
            net.minecraft.world.inventory.Slot slot = this.menu.slots.get(i);
            int sx = bx + slot.x - 1;
            int sy = by + slot.y - 1;
            int bgColor;
            String label = null;

            if (i >= 36 && i <= 37) {
                // GUN slots (hotbar 0-1)
                bgColor = 0x55FF4422;
                if (i == 36) label = "\u00A7cG1";
                else label = "\u00A7cG2";
            } else if (i == 38) {
                // MELEE slot (hotbar 2)
                bgColor = 0x552244FF;
                label = "\u00A79M";
            } else if ((i >= 39 && i <= 44) || (i >= 9 && i <= 11)) {
                // ITEM slots (hotbar 3-8 + extended quick slots 9-11)
                bgColor = i <= 11 ? 0x6633AA66 : 0x5522FF22;
                if (i >= 9 && i <= 11) label = "\u00A7aE" + (i - 8);
            } else if (i >= 12 && i <= 15) {
                // AMMO slots (inv 12-15)
                boolean isGun2 = (i >= 14);
                bgColor = isGun2 ? 0x5500AAFF : 0x55FFFF00;
                if (i == 12) label = "\u00A7eA1";
                else if (i == 14) label = "\u00A7bA2";
            } else if (i >= 16 && i <= 35) {
                // Expandable inventory slots
                net.minecraft.world.item.ItemStack stack = slot.getItem();
                boolean isLocked = stack.is(net.minecraft.world.item.Items.BARRIER)
                    && stack.hasTag() && stack.getOrCreateTag().getBoolean("rogue_item_locked");
                bgColor = isLocked ? 0x44330000 : 0x33FFFFFF;
            } else {
                bgColor = 0x22FFFFFF;
            }

            g.fill(sx, sy, sx + 18, sy + 18, bgColor);
            if (i >= 12 && i <= 15) {
                drawFlatOutline(g, sx, sy, 18, 18, (bgColor & 0x00FFFFFF) | 0x66000000);
            }

            // Draw slot label in top-left corner
            if (label != null) {
                g.pose().pushPose();
                g.pose().translate(sx + 1, sy + 1, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                g.drawString(this.font, label, 0, 0, 0x88FFFFFF, false);
                g.pose().popPose();
            }
        }

        // Section labels
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.pose().scale(0.6f, 0.6f, 1.0f);
        float invScale = 1.0f / 0.6f;
        g.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.section.gun_melee"), (int)((bx + 12) * invScale), (int)((by + 22) * invScale), 0xAAFF7755, false);
        g.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.section.items"), (int)((bx + 98) * invScale), (int)((by + 10) * invScale), 0xAA55DD77, false);
        g.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.section.ammo"), (int)((bx + 172) * invScale), (int)((by + 10) * invScale), 0xAAAAA800, false);
        g.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.section.backpack"), (int)((bx + 12) * invScale), (int)((by + 98) * invScale), 0xAAAAAAAA, false);
        g.pose().popPose();
    }

    private static void drawFlatOutline(GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + 1, color);
        g.fill(x, y + height - 1, x + width, y + height, color);
        g.fill(x, y + 1, x + 1, y + height - 1, color);
        g.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}

    /**
     * アタッチメントIDからTacZのスロット種別キーを解決する。
     * 銃NBTの Attachments タグのキー（scope, muzzle, grip, stock, laser, extended_mag 等）と照合用。
     */
    private static String resolveAttachmentSlotType(String attachId) {
        String lower = attachId.toLowerCase();
        if (lower.contains("sight") || lower.contains("scope"))  return "scope";
        if (lower.contains("silencer") || lower.contains("suppressor") ||
            lower.contains("compensator") || lower.contains("brake") ||
            lower.contains("muzzle"))  return "muzzle";
        if (lower.contains("grip"))    return "grip";
        if (lower.contains("stock"))   return "stock";
        if (lower.contains("laser"))   return "laser";
        if (lower.contains("ext") && lower.contains("mag"))  return "extended_mag";
        if (lower.contains("bayonet")) return "muzzle"; // bayonets use muzzle slot in TacZ
        return ""; // unknown — fallback to "any gun" check
    }
}
