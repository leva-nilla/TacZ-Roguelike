package com.levanilla.rogue.client;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.StaminaManager;

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
    private static final int ITEMS_PER_PAGE = 6;

    public void setActiveTab(Tab tab) {
        this.activeTab = tab;
        this.shopPage = 0;
    }

    public RogueInventoryScreen(AbstractContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 220;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int x = this.leftPos;
        int y = this.topPos;

        // --- 繝上う繝・け縺ｪ繧ｵ繧､繝舌・繝代Φ繧ｯ鬚ｨ閭梧勹縺ｮ謠冗判 ---
        graphics.fill(x - 110, y - 40, x + imageWidth + 110, y + imageHeight + 40, 0xAA001122);
        graphics.renderOutline(x - 110, y - 40, imageWidth + 220, imageHeight + 80, 0xAA00AAFF);
        for (int i = -100; i < imageWidth + 100; i += 20) graphics.fill(x + i, y - 35, x + i + 1, y + imageHeight + 35, 0x2200AAFF);
        for (int i = -30; i < imageHeight + 30; i += 20) graphics.fill(x - 105, y + i, x + imageWidth + 105, y + i + 1, 0x2200AAFF);

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
            List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> items = shopCategoryFilter == null ?
                TacZRegistryHelper.getAllShopItems() : TacZRegistryHelper.getItemsByCategory(shopCategoryFilter);
            int startIndex = shopPage * ITEMS_PER_PAGE;
            int listY = y + 10;
            for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < items.size(); i++) {
                int iy = listY + (i * 18);
                if (mouseX >= listX && mouseX <= panelRight - 5 && mouseY >= iy - 1 && mouseY <= iy + 15) {
                    hoveredShopItem = i;
                }
            }
        } else if (activeTab == Tab.PERKS && player != null) {
            int perkY = y + 26;
            int rightX = x + 10;
            List<PerkGroupEntry> groups = buildPerkGroups(player);
            int visible = Math.min(groups.size() - perkScrollOffset, 7);
            for (int i = 0; i < visible; i++) {
                int py = perkY + i * 22;
                if (mouseX >= rightX && mouseX <= rightX + 280 && mouseY >= py && mouseY <= py + 20) {
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
            int panelX = x - 95;
            int panelY = y + 5;
            int panelW = 80;
            int panelH = 130;
            graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0x88001133);
            graphics.renderOutline(panelX, panelY, panelW, panelH, 0xAA00CCFF);
            graphics.drawString(this.font, "\u00A7b\u2605 OPERATOR", panelX + 4, panelY + 3, 0xFF00AAFF, false);

            if (player != null) {
                int entityX = panelX + panelW / 2;
                int entityY = panelY + panelH - 10;
                float scale = 38.0f;
                float lookX = entityX - mouseX;
                float lookY = (entityY - 60) - mouseY;
                net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, entityX, entityY, (int) scale, lookX, lookY, player);
            }
        }

        Component statusText = RunManager.isRunActive() ? 
            Component.translatable("gui.tac_rogue.inventory.operator_status", RunManager.getCurrentFloor()) : 
            Component.translatable("gui.tac_rogue.inventory.base_command");
        graphics.drawString(this.font, statusText, x - 100, y - 55, 0xFF00FFFF, false);

        renderTabHighlight(graphics);

        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }

        // 繝・・繝ｫ繝√ャ繝励ｒ蜈ｨ縺ｦ縺ｮ莉冶ｦ∫ｴ縺ｮ蠕鯉ｼ域怙蜑埼擇・峨↓謠冗判
        if (activeTab == Tab.INVENTORY && this.hoveredSlot != null && !this.hoveredSlot.getItem().isEmpty()) {
            graphics.renderTooltip(this.font, this.hoveredSlot.getItem(), mouseX, mouseY);
        }
        if (activeTab == Tab.SHOP && hoveredShopItem >= 0 && player != null) {
            renderShopTooltip(graphics, player, mouseX, mouseY);
        }
        if (activeTab == Tab.PERKS && hoveredPerkIndex >= 0 && player != null) {
            renderPerkTooltip(graphics, player, mouseX, mouseY);
        }
        if (activeTab == Tab.QUEST && hoveredQuestIndex >= 0) {
            renderQuestTooltip(graphics, mouseX, mouseY);
        }
    }

    /** ショップアイテムのツールチップ描画 */
    private void renderShopTooltip(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int mouseX, int mouseY) {
        List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> items = shopCategoryFilter == null ?
            TacZRegistryHelper.getAllShopItems() : TacZRegistryHelper.getItemsByCategory(shopCategoryFilter);
        int idx = shopPage * ITEMS_PER_PAGE + hoveredShopItem;
        if (idx < 0 || idx >= items.size()) return;
        com.levanilla.rogue.core.registry.ShopCatalog.ShopItem item = items.get(idx);
        int gold = RunManager.getClientGold();
        boolean canAfford = gold >= item.price;

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal("\u00A7e" + item.displayName));
        tooltip.add(Component.literal("\u00A77Category: \u00A7f" + item.category.label));
        tooltip.add(Component.literal("\u00A77Price: " + (canAfford ? "\u00A76" : "\u00A7c") + "$" + item.price));
        tooltip.add(Component.literal("\u00A77Held: \u00A76$" + gold));
        
        String desc = switch (item.category) {
            case PISTOL -> "Sidearm. Light and fast draw.";
            case RIFLE -> "Primary assault rifle. High versatility.";
            case SMG -> "Close-range suppression. High fire rate.";
            case SHOTGUN -> "Devastating at close range. Slow reload.";
            case SNIPER -> "Long-range precision. High scope magnification.";
            case LMG -> "Suppressive fire with large ammo capacity.";
            case EXPLOSIVE -> "Heavy explosive weapon. Splash damage.";
            case MELEE -> "Close combat weapon. No ammo needed.";
            case ATTACHMENT -> "Attach to weapons to boost performance.";
            case AMMO -> "Ammo for matching weapons x32.";
            case SPECIAL -> "Upgrade that changes gameplay.";
        };
        tooltip.add(Component.literal("\u00A78" + desc));
        if (!canAfford) tooltip.add(Component.literal("\u00A7cNot enough gold!"));
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
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
            graphics.drawString(this.font, "\u00A77No quest data \u2014 talk to Commander NPC",
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
        long completedCount = cachedQuests.stream().filter(q -> "true".equals(q[5])).count();
        String summary = "\u00A77" + completedCount + "/" + cachedQuests.size() + " completed";
        graphics.drawString(this.font, summary, panelLeft + 10, y + imageHeight + 25, 0xFF888888, false);
    }

    private void renderTabHighlight(GuiGraphics graphics) {
        int[] offsets = {-6, 61, 113, 165, 217};
        int[] widths = {65, 50, 50, 50, 65};
        int index = activeTab.ordinal();
        if (index >= 0 && index < offsets.length) {
            graphics.fill(tabX + offsets[index], tabY, tabX + offsets[index] + widths[index], tabY + 15, 0x4400FFFF);
            graphics.renderOutline(tabX + offsets[index], tabY, widths[index], 15, 0xFF00FFFF);
        }
    }

    private void renderStatusTab(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int x, int y) {
        int lx = x + 10;
        int rx = x + 155;

        // === VITAL SIGNS ===
        graphics.drawString(this.font, "\u00A7b\u2588 VITAL SIGNS", lx, y + 8, 0xFF00AAFF, false);
        int hp = (int) player.getHealth();
        int maxHp = (int) player.getMaxHealth();
        int hpColor = hp > maxHp * 0.5 ? 0xFF00FF88 : hp > maxHp * 0.25 ? 0xFFFFFF00 : 0xFFFF4444;
        // HP bar
        int barW = 130;
        int barY = y + 22;
        graphics.fill(lx, barY, lx + barW, barY + 8, 0x44FFFFFF);
        int fillW = maxHp > 0 ? (int) ((float) hp / maxHp * barW) : 0;
        graphics.fill(lx, barY, lx + fillW, barY + 8, hpColor);
        graphics.drawString(this.font, "HP " + hp + "/" + maxHp, lx + barW + 4, barY, hpColor, false);

        // Stamina bar
        float sta = com.levanilla.rogue.core.StaminaManager.getStamina(player);
        float maxSta = com.levanilla.rogue.core.StaminaManager.getMaxStamina(player);
        int staBarY = barY + 12;
        graphics.fill(lx, staBarY, lx + barW, staBarY + 8, 0x44FFFFFF);
        int staFill = maxSta > 0 ? (int) (sta / maxSta * barW) : 0;
        graphics.fill(lx, staBarY, lx + staFill, staBarY + 8, 0xFF00FFFF);
        graphics.drawString(this.font, "STA " + (int) (sta / 20) + "s", lx + barW + 4, staBarY, 0xFF00DDFF, false);

        // Stats grid
        int gy = staBarY + 16;
        int armor = (int) player.getAttributeValue(Attributes.ARMOR);
        double speed = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        graphics.drawString(this.font, "\u00A77DEF: \u00A7f" + armor, lx, gy, 0xFFAABBFF, false);
        graphics.drawString(this.font, "\u00A77SPD: \u00A7f" + String.format("%.3f", speed), rx, gy, 0xFFFFFFFF, false);
        gy += 12;
        graphics.drawString(this.font, "\u00A76GOLD: \u00A7e$" + RunManager.getClientGold(), lx, gy, 0xFFFFCC00, false);
        graphics.drawString(this.font, "\u00A7aFLOOR: \u00A7f" + RunManager.getCurrentFloor(), rx, gy, 0xFF00FF00, false);

        // === COMBAT STATS ===
        gy += 18;
        graphics.fill(lx - 2, gy - 2, lx + 280, gy, 0x4400AAFF);
        graphics.drawString(this.font, "\u00A7b\u2588 COMBAT STATS", lx, gy + 2, 0xFF00AAFF, false);
        gy += 14;

        float dmgBonus = sumClientPerkEffect(player, "perk:DAMAGE");
        float hsBonus = sumClientPerkEffect(player, "perk:FORTUNE");
        float reloadBonus = sumClientPerkEffect(player, "perk:RELOAD_SPEED");
        float resistBonus = sumClientPerkEffect(player, "perk:RESISTANCE");
        float vampBonus = sumClientPerkEffect(player, "perk:VAMPIRE");
        float goldBonus = sumClientPerkEffect(player, "perk:GOLD_RUSH");

        graphics.drawString(this.font, "\u00A7cDMG+: \u00A7f" + String.format("%.0f%%", dmgBonus), lx, gy, 0xFFFF4444, false);
        graphics.drawString(this.font, "\u00A7eCRIT: \u00A7f" + String.format("%.0f%%", hsBonus), rx, gy, 0xFFFFFF00, false);
        gy += 11;
        graphics.drawString(this.font, "\u00A7bRLD+: \u00A7f" + String.format("%.0f%%", reloadBonus), lx, gy, 0xFF00FFFF, false);
        graphics.drawString(this.font, "\u00A75RES:  \u00A7f" + String.format("%.0f%%", resistBonus), rx, gy, 0xFFAA88FF, false);
        gy += 11;
        graphics.drawString(this.font, "\u00A74VAMP: \u00A7f" + String.format("%.1f", vampBonus / 10f), lx, gy, 0xFFCC0000, false);
        graphics.drawString(this.font, "\u00A76GOLD: \u00A7f" + String.format("+%.0f%%", goldBonus), rx, gy, 0xFFFFD700, false);

        // === POSTURE ===
        gy += 16;
        graphics.fill(lx - 2, gy - 2, lx + 280, gy, 0x4400AAFF);
        graphics.drawString(this.font, "\u00A7b\u2588 POSTURE", lx, gy + 2, 0xFF00AAFF, false);
        gy += 14;
        String posture = player.isSwimming() ? "\u00A72PRONE" : player.isShiftKeyDown() ? "\u00A7eSNEAK" : "\u00A7fSTANDING";
        float postureDmg = player.isSwimming() ? GameConstants.CRAWL_DAMAGE_MULT : player.isShiftKeyDown() ? GameConstants.SNEAK_DAMAGE_MULT : 1.0f;
        graphics.drawString(this.font, "\u00A77Stance: " + posture, lx, gy, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "\u00A77DMG x" + String.format("%.2f", postureDmg), rx, gy, 0xFFFFFFFF, false);

        // Total perk count
        gy += 16;
        int totalPerks = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:")) totalPerks++;
        }
        graphics.drawString(this.font, "\u00A77Perks: \u00A7f" + totalPerks, lx, gy, 0xFFAAAAAA, false);
    }

    // === Perk grouping data class ===
    private record PerkGroupEntry(String categoryKey, PerkDefinition samplePerk, int count, float totalEffect) {}

    private List<PerkGroupEntry> buildPerkGroups(net.minecraft.client.player.LocalPlayer player) {
        Map<String, List<PerkDefinition>> grouped = new LinkedHashMap<>();
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:")) {
                PerkDefinition perk = PerkDefinition.fromTag(tag);
                String key = perk.category.name() + ":" + perk.modifier.name();
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(perk);
            }
        }
        List<PerkGroupEntry> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<PerkDefinition> perks = entry.getValue();
            float totalEffect = 0;
            for (PerkDefinition p : perks) totalEffect += p.calculateEffect();
            result.add(new PerkGroupEntry(entry.getKey(), perks.get(0), perks.size(), totalEffect));
        }
        return result;
    }

    private void renderPerksTab(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int x, int y) {
        int rightX = x + 10;
        List<PerkGroupEntry> groups = buildPerkGroups(player);

        // Header with count
        int totalPerks = 0;
        for (PerkGroupEntry g : groups) totalPerks += g.count();
        graphics.drawString(this.font, "\u00A7bADAPTATIONS \u00A77(" + totalPerks + " total, " + groups.size() + " types)", rightX, y + 8, 0xFF00AAFF, false);

        int perkY = y + 22;
        int maxVisible = 7;
        int maxScroll = Math.max(0, groups.size() - maxVisible);
        if (perkScrollOffset > maxScroll) perkScrollOffset = maxScroll;

        // Scroll indicator
        if (perkScrollOffset > 0) {
            graphics.drawCenteredString(this.font, "\u00A78\u25B2 scroll up", rightX + 140, perkY - 10, 0xFF555555);
        }

        int rendered = 0;
        for (int i = perkScrollOffset; i < groups.size() && rendered < maxVisible; i++) {
            PerkGroupEntry group = groups.get(i);
            PerkDefinition perk = group.samplePerk();
            int py = perkY + rendered * 22;
            int rarityColor = perk.getRarityColor();
            boolean hovered = (hoveredPerkIndex == i);

            // Card background
            int cardW = 280;
            int bgColor = hovered ? 0x44FFFFFF : 0x22FFFFFF;
            graphics.fill(rightX, py, rightX + cardW, py + 20, bgColor);
            graphics.fill(rightX, py, rightX + 2, py + 20, rarityColor | 0xFF000000);

            // Perk name
            String displayName = perk.getDisplayName();
            graphics.drawString(this.font, displayName, rightX + 6, py + 2, rarityColor | 0xFF000000, false);

            // Count badge
            if (group.count() > 1) {
                String countBadge = "x" + group.count();
                int nameW = this.font.width(displayName);
                graphics.drawString(this.font, "\u00A7e" + countBadge, rightX + 8 + nameW, py + 2, 0xFFFFCC00, false);
            }

            // Total effect
            float effect = group.totalEffect();
            String effectStr;
            if (perk.category == PerkDefinition.Category.REGENERATION || perk.category == PerkDefinition.Category.VAMPIRE) {
                effectStr = String.format("Total: +%.1f", effect / 10.0f);
            } else {
                effectStr = "Total: +" + (int) effect + "%";
            }
            graphics.drawString(this.font, "\u00A7a" + effectStr, rightX + 6, py + 11, 0xFF88FF88, false);

            // Modifier tag
            if (perk.modifier != PerkDefinition.Modifier.NONE) {
                String modText = "\u00A77[" + perk.modifier.prefix + "]";
                int modW = this.font.width(modText);
                graphics.drawString(this.font, modText, rightX + cardW - modW - 4, py + 6, perk.modifier.color, false);
            }
            rendered++;
        }

        if (groups.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.inventory.no_perks"), rightX + 5, perkY, 0xFF555555, false);
        }

        // Scroll down indicator
        if (perkScrollOffset + maxVisible < groups.size()) {
            graphics.drawCenteredString(this.font, "\u00A78\u25BC scroll down (" + (groups.size() - perkScrollOffset - maxVisible) + " more)",
                rightX + 140, perkY + maxVisible * 22 + 2, 0xFF555555);
        }

        // === Cursed penalty summary ===
        int cursedCount = 0;
        List<PerkDefinition> cursedPerks = new ArrayList<>();
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:") && tag.contains(":CURSED:")) {
                cursedPerks.add(PerkDefinition.fromTag(tag));
                cursedCount++;
            }
        }
        if (cursedCount > 0) {
            int summaryY = perkY + Math.min(rendered, maxVisible) * 22 + 14;
            graphics.fill(rightX, summaryY - 2, rightX + 280, summaryY + 12 + cursedCount * 10, 0x44990033);
            graphics.drawString(this.font, "\u00A7c\u26A0 CURSED (" + cursedCount + "/" + GameConstants.MAX_CURSED_PERKS + ")",
                rightX + 4, summaryY, 0xFFFF4444, false);
            int lineY = summaryY + 12;
            for (PerkDefinition cp : cursedPerks) {
                String penalty = getCursedPenaltyText(cp);
                graphics.pose().pushPose();
                graphics.pose().translate(rightX + 8, lineY, 0);
                graphics.pose().scale(0.85f, 0.85f, 1.0f);
                graphics.drawString(this.font, penalty, 0, 0, 0xFFCC6666, false);
                graphics.pose().popPose();
                lineY += 10;
            }
        }
    }

    /** Cursed パークのペナルティテキストを返す */
    private String getCursedPenaltyText(PerkDefinition perk) {
        String effect = String.format("%.0f", perk.calculateEffect());
        return switch (perk.category) {
            case VITALITY -> "\u00A7c-" + effect + "% Max HP";
            case ARMOR -> "\u00A7c-" + effect + "% Armor";
            case VELOCITY -> "\u00A7c-" + effect + "% Movement Speed";
            case DAMAGE -> "\u00A7c-" + effect + "% Accuracy (recoil+)";
            case RELOAD_SPEED -> "\u00A7c+" + effect + "% Reload Time";
            case STAMINA -> "\u00A7c-" + effect + "% Stamina Regen";
            case RESISTANCE -> "\u00A7c-" + effect + "% Explosion Resist";
            default -> "\u00A7c\u2022 " + perk.category.displayName + " penalty";
        };
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
        String unitStr = (perk.category == PerkDefinition.Category.REGENERATION || perk.category == PerkDefinition.Category.VAMPIRE)
            ? String.format("%.1f", unitEffect / 10f) : String.format("%.0f%%", unitEffect);
        String totalStr = (perk.category == PerkDefinition.Category.REGENERATION || perk.category == PerkDefinition.Category.VAMPIRE)
            ? String.format("%.1f", group.totalEffect() / 10f) : String.format("%.0f%%", group.totalEffect());
        tooltip.add(Component.literal("\u00A77Per unit: +" + unitStr + "  \u00A7aTotal: +" + totalStr));
        if (perk.modifier != PerkDefinition.Modifier.NONE) {
            tooltip.add(Component.literal("\u00A77Modifier: ").append(
                Component.literal(perk.modifier.prefix).withStyle(s -> s.withColor(perk.modifier.color))));
            if (!perk.modifier.tradeoff.isEmpty()) {
                tooltip.add(Component.literal("\u00A7c\u26A0 ").append(Component.translatable(perk.modifier.tradeoff)));
            }
            if (perk.modifier == PerkDefinition.Modifier.CURSED) {
                tooltip.add(Component.literal(getCursedPenaltyText(perk)));
            }
        }
        tooltip.add(Component.literal("\u00A78Level: " + perk.level));
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    /** クライアント側パーク効果合算 */
    private static float sumClientPerkEffect(net.minecraft.client.player.LocalPlayer player, String perkPrefix) {
        float total = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith(perkPrefix)) {
                total += PerkDefinition.fromTag(tag).calculateEffect();
            }
        }
        return total;
    }

    private com.levanilla.rogue.core.registry.ShopCatalog.Category shopCategoryFilter = null; // null = ALL
    private boolean sellMode = false;
    private int hoveredShopItem = -1;
    private int hoveredInvSlot = -1;
    private int hoveredPerkIndex = -1;
    private int hoveredQuestIndex = -1;

    private void renderQuestTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredQuestIndex < 0 || hoveredQuestIndex >= cachedQuests.size()) return;
        String[] quest = cachedQuests.get(hoveredQuestIndex);
        String questId = quest[0];
        String typeName = quest[1];
        String target = quest.length > 2 ? quest[2] : "0";
        String progress = quest.length > 3 ? quest[3] : "0";
        String gold = quest.length > 4 ? quest[4] : "0";

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(typeName).withStyle(s -> s.withColor(0xFFE5FF)));

        String langKey = "quest.tac_rogue." + questId;
        String typeDescKey = typeName + ".desc";
        
        if (net.minecraft.client.resources.language.I18n.exists(langKey)) {
            // Story Quest descriptions
            tooltip.add(Component.translatable(langKey).withStyle(s -> s.withColor(0xAAAAAA)));
        } else if (net.minecraft.client.resources.language.I18n.exists(typeDescKey)) {
            // Type-based dynamic descriptions (e.g., "Clear floor in %s seconds")
            tooltip.add(Component.translatable(typeDescKey, target).withStyle(s -> s.withColor(0xAAAAAA)));
            tooltip.add(Component.literal("Reward: " + gold + " G").withStyle(s -> s.withColor(0xFFD700)));
        } else {
            // Ultimate fallback
            tooltip.add(Component.literal("Target: " + target).withStyle(s -> s.withColor(0xAAAAAA)));
            tooltip.add(Component.literal("Reward: " + gold + " G").withStyle(s -> s.withColor(0xFFD700)));
        }

        // Add Progress Information
        tooltip.add(Component.literal("Progress: " + progress + " / " + target).withStyle(s -> s.withColor(0x00E5FF)));
        
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private void renderShopTab(GuiGraphics graphics, net.minecraft.client.player.LocalPlayer player, int x, int y) {
        int panelLeft = x - 110;
        int panelRight = x + imageWidth + 110;
        int catPanelW = 65;
        int catPanelX = panelLeft;
        int listX = catPanelX + catPanelW + 4;
        int gold = RunManager.getClientGold();

        // Header bar — タブバーの下に配置 (被り回避)
        String modeLabel = sellMode ? "\u00A7c\u00A7lSELL" : "\u00A7e\u00A7lSHOP";
        graphics.drawString(this.font, modeLabel, listX, y - 4, sellMode ? 0xFFFF4444 : 0xFFFFCC00, false);
        graphics.drawString(this.font, "\u00A76$ " + gold, listX + 50, y - 4, 0xFFFFD700, false);

        if (sellMode) {
            // === Sell mode ===
            graphics.drawString(this.font, "\u00A77Select item to sell", listX, y + 5, 0xFFAAAAAA, false);
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
                graphics.drawString(this.font, "\u00A78No sellable items", listX, listY, 0xFF555555, false);
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
            List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> items = shopCategoryFilter == null ?
                TacZRegistryHelper.getAllShopItems() :
                TacZRegistryHelper.getItemsByCategory(shopCategoryFilter);
            int maxPages = items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;
            if (shopPage > maxPages) shopPage = maxPages;
            // Page indicator — ヘッダー右寄りに配置（金額と被らない）
            graphics.drawString(this.font, String.format("\u00A77PG %d/%d", shopPage + 1, maxPages + 1),
                panelRight - 90, y - 10, 0xFFAAAAAA, false);

            // Held items check
            java.util.Set<String> heldGunIds = new java.util.HashSet<>();
            java.util.Set<String> heldAmmoIds = new java.util.HashSet<>();
            java.util.Set<String> heldAttTypes = new java.util.HashSet<>();
            for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
                net.minecraft.world.item.ItemStack invStack = player.getInventory().getItem(s);
                if (!invStack.isEmpty() && invStack.hasTag()) {
                    net.minecraft.nbt.CompoundTag itag = invStack.getTag();
                    if (itag.contains("GunId")) {
                        String gunId = itag.getString("GunId");
                        heldGunIds.add(gunId);
                        heldAmmoIds.add(gunId);
                        if (itag.contains("Attachments")) {
                            net.minecraft.nbt.CompoundTag att = itag.getCompound("Attachments");
                            for (String key : att.getAllKeys()) {
                                heldAttTypes.add(key);
                            }
                        }
                    }
                    if (itag.contains("AmmoId")) {
                        heldAmmoIds.add(itag.getString("AmmoId"));
                    }
                }
            }

            int startIndex = shopPage * ITEMS_PER_PAGE;
            int listY = y + 8;
            int itemWidth = panelRight - listX - 55;
            for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < items.size(); i++) {
                com.levanilla.rogue.core.registry.ShopCatalog.ShopItem item = items.get(startIndex + i);
                boolean hovered = (hoveredShopItem == i);
                int iy = listY + (i * 18);

                boolean compatible = false;
                String compatGunName = "";
                if (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.AMMO) {
                    for (String gunId : heldGunIds) {
                        String ammoForGun = TacZRegistryHelper.getAmmoForGun(gunId);
                        if (ammoForGun.equals(item.id)) {
                            compatible = true;
                            compatGunName = gunId.contains(":") ? gunId.substring(gunId.indexOf(':') + 1).toUpperCase() : gunId.toUpperCase();
                            break;
                        }
                    }
                } else if (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.ATTACHMENT) {
                    // 正確な互換性チェック: AttachmentDatabase.isCompatible() で銃×アタッチメントを厳密判定
                    for (String gunId : heldGunIds) {
                        if (com.levanilla.rogue.core.registry.AttachmentDatabase.isCompatible(gunId, item.id)) {
                            compatible = true;
                            compatGunName = gunId.contains(":") ? gunId.substring(gunId.indexOf(':') + 1).toUpperCase() : gunId.toUpperCase();
                            break;
                        }
                    }
                }

                if (hovered) {
                    graphics.fill(listX, iy - 1, listX + itemWidth, iy + 15, 0x33FFFF00);
                } else if (compatible) {
                    graphics.fill(listX, iy - 1, listX + itemWidth, iy + 15, 0x2200FF00);
                }

                boolean canAfford = gold >= item.price;
                String catTag = "\u00A77[" + item.category.label.charAt(0) + "] ";
                String nameColor = compatible ? "\u00A7a" : (canAfford ? "\u00A7f" : "\u00A78");
                String priceColor = canAfford ? "\u00A7e" : "\u00A7c";
                String label = catTag + nameColor + item.displayName + " " + priceColor + "$" + item.price;
                if (compatible) label = "\u00A7a\u2714 " + label + " \u00A72[" + compatGunName + "]";

                if (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.AMMO) {
                    int stackSize = TacZRegistryHelper.getAmmoStackSize(item.id);
                    label += " \u00A77x" + stackSize;
                }
                if (item.category.isWeapon()) {
                    int magSize = TacZRegistryHelper.getMagazineSize(item.id);
                    String ammoType = TacZRegistryHelper.getAmmoForGun(item.id);
                    String ammoShort = ammoType.contains(":") ? ammoType.substring(ammoType.indexOf(':') + 1) : ammoType;
                    label += " \u00A77[" + magSize + "rd/" + ammoShort + "]";
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
        graphics.drawString(this.font, "\u00A77THEME: \u00A7f" + RunManager.getCurrentThemeName(), rightX + 5, y + 26, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "\u00A77OBJECTIVE: \u00A7eELIMINATE ALL TARGETS", rightX + 5, y + 38, 0xFFFFFFFF, false);
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        this.tabX = x - 100;
        this.tabY = y - 20;

        boolean isLobbyInit = this.minecraft != null && this.minecraft.level != null
            && this.minecraft.level.dimension().location().toString().equals("tac_rogue:lobby_dimension");

        addTacticalButton(tabX - 6, tabY, 65, 15, Component.literal("INVENTORY"), b -> { activeTab = Tab.INVENTORY; this.init(this.minecraft, this.width, this.height); });
        addTacticalButton(tabX + 61, tabY, 50, 15, Component.translatable("gui.tac_rogue.inventory.status"), b -> { activeTab = Tab.STATUS; this.init(this.minecraft, this.width, this.height); });
        addTacticalButton(tabX + 113, tabY, 50, 15, Component.translatable("gui.tac_rogue.inventory.perks"), b -> { activeTab = Tab.PERKS; this.init(this.minecraft, this.width, this.height); });
        addTacticalButton(tabX + 165, tabY, 50, 15, Component.translatable("gui.tac_rogue.inventory.floor_info"), b -> { activeTab = Tab.FLOOR_INFO; this.init(this.minecraft, this.width, this.height); });
        // ショップはロビーのみ
        if (isLobbyInit) {
            addTacticalButton(tabX + 217, tabY, 65, 15, Component.translatable("gui.tac_rogue.inventory.shop"), b -> {
                activeTab = Tab.SHOP; shopPage = 0;
                // Request gold sync
                com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                    new com.levanilla.rogue.networking.RogueActionMessage(
                        com.levanilla.rogue.networking.RogueActionMessage.ActionType.SYNC_DATA));
                this.init(this.minecraft, this.width, this.height);
            });
            // クエストタブもロビーのみ
            addTacticalButton(tabX + 284, tabY, 55, 15, Component.literal("QUEST"), b -> {
                activeTab = Tab.QUEST;
                this.init(this.minecraft, this.width, this.height);
            });
        }

        if (activeTab == Tab.SHOP) {
            int panelLeft = x - 110;
            int panelRight = x + imageWidth + 110;
            int catPanelW = 65;
            int catPanelX = panelLeft;
            int listX = catPanelX + catPanelW + 4;

            // BUY/SELL toggle button — ヘッダー右端に配置
            String modeText = sellMode ? "\u00A7a\u2190 BUY" : "\u00A7c\u2192 SELL";
            addTacticalButton(panelRight - 55, y - 12, 48, 12, Component.literal(modeText), b -> {
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
                        addTacticalButton(panelRight - 60, y + 18 + (slotIdx * 14), 48, 12, Component.literal("SELL"), b -> {
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
                addTacticalButton(catPanelX + 2, catTabY, catPanelW - 4, tabH - 1, Component.literal("ALL"), b -> {
                    shopCategoryFilter = null; shopPage = 0;
                    this.init(this.minecraft, this.width, this.height);
                });
                // Category buttons
                for (int ci = 0; ci < cats.length; ci++) {
                    final com.levanilla.rogue.core.registry.ShopCatalog.Category cat = cats[ci];
                    addTacticalButton(catPanelX + 2, catTabY + (ci + 1) * tabH, catPanelW - 4, tabH - 1, Component.literal(cat.label), b -> {
                        shopCategoryFilter = cat; shopPage = 0;
                        this.init(this.minecraft, this.width, this.height);
                    });
                }

                // BUY buttons for item list
                List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> items = shopCategoryFilter == null ?
                    TacZRegistryHelper.getAllShopItems() :
                    TacZRegistryHelper.getItemsByCategory(shopCategoryFilter);

                int startIndex = shopPage * ITEMS_PER_PAGE;
                int listY = y + 8;  // renderShopTabと同一のlistY
                for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < items.size(); i++) {
                    com.levanilla.rogue.core.registry.ShopCatalog.ShopItem item = items.get(startIndex + i);
                    String buyId = item.id;
                    addTacticalButton(panelRight - 48, listY + (i * 18), 40, 14, Component.literal("BUY"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.BUY_ITEM, buyId));
                    });
                }

                // Pagination buttons
                int maxPages = items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;
                if (shopPage > 0) addTacticalButton(listX, y + imageHeight + 10, 50, 15, Component.literal("< PREV"), b -> { shopPage--; this.init(this.minecraft, this.width, this.height); });
                if (shopPage < maxPages) addTacticalButton(listX + 55, y + imageHeight + 10, 50, 15, Component.literal("NEXT >"), b -> { shopPage++; this.init(this.minecraft, this.width, this.height); });
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
                addTacticalButton(x - 100, y + imageHeight + 10, 90, 20,
                    Component.literal("§a[JOIN FLOOR]"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.START_NEXT_FLOOR));
                        this.onClose();
                    });
            } else if (RunManager.isFloorCleared()) {
                // Floor cleared -> NEXT FLOOR
                addTacticalButton(x - 100, y + imageHeight + 10, 90, 20,
                    Component.literal("§a[NEXT FLOOR]"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.START_NEXT_FLOOR));
                        this.onClose();
                    });
            } else if (RunManager.isRunActive()) {
                // Dungeon & not cleared -> RETRY FLOOR
                addTacticalButton(x - 100, y + imageHeight + 10, 90, 20,
                    Component.literal("§e[RETRY FLOOR]"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.RETRY_FLOOR));
                        this.onClose();
                    });
            }
            if (!inLobby) {
                addTacticalButton(x + 10, y + imageHeight + 10, 90, 20,
                    Component.translatable("gui.tac_rogue.inventory.return_lobby"), b -> {
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.sendToServer(
                            new com.levanilla.rogue.networking.RogueActionMessage(
                                com.levanilla.rogue.networking.RogueActionMessage.ActionType.RETURN_TO_LOBBY));
                        this.onClose();
                    });
            }
        }
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
            graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, 0xFFFFFFFF);
        }
    }

    /** マウスホイールでスクロール（SHOP/PERKS/QUEST統合） */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (activeTab == Tab.SHOP) {
            List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> items = shopCategoryFilter == null ?
                TacZRegistryHelper.getAllShopItems() : TacZRegistryHelper.getItemsByCategory(shopCategoryFilter);
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
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (activeTab != Tab.INVENTORY) return;
        int bx = this.leftPos;
        int by = this.topPos;

        // Main inventory area background
        g.fill(bx, by, bx + imageWidth, by + imageHeight, 0x88001122);

        // Draw slot backgrounds by type
        for (int i = 0; i < this.menu.slots.size(); i++) {
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
            } else if (i >= 39 && i <= 44) {
                // ITEM slots (hotbar 3-8)
                bgColor = 0x5522FF22;
            } else if (i >= 9 && i <= 12) {
                // AMMO slots (inv 9-12)
                boolean isGun2 = (i >= 11);
                bgColor = isGun2 ? 0x5500AAFF : 0x55FFFF00;
                if (i == 9) label = "\u00A7eA1";
                else if (i == 11) label = "\u00A7bA2";
            } else if (i >= 13 && i <= 35) {
                // Expandable inventory slots
                net.minecraft.world.item.ItemStack stack = slot.getItem();
                boolean isLocked = stack.is(net.minecraft.world.item.Items.BARRIER)
                    && stack.hasTag() && stack.getOrCreateTag().getBoolean("rogue_item_locked");
                bgColor = isLocked ? 0x44330000 : 0x33FFFFFF;
            } else if (i <= 8 || i == 45) {
                // Crafting, armor, offhand → dim/hide
                bgColor = 0x22111111;
                g.fill(sx, sy, sx + 18, sy + 18, bgColor);
                // Draw dark overlay to indicate disabled
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x66000000);
                continue;
            } else {
                bgColor = 0x22FFFFFF;
            }

            g.fill(sx, sy, sx + 18, sy + 18, bgColor);
            g.renderOutline(sx, sy, 18, 18, (bgColor & 0x00FFFFFF) | 0x66000000);

            // Draw slot label in top-left corner
            if (label != null) {
                g.pose().pushPose();
                g.pose().translate(sx + 1, sy + 1, 300);
                g.pose().scale(0.5f, 0.5f, 1.0f);
                g.drawString(this.font, label, 0, 0, 0x88FFFFFF, false);
                g.pose().popPose();
            }
        }

        // Section labels above hotbar row
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.pose().scale(0.6f, 0.6f, 1.0f);
        float invScale = 1.0f / 0.6f;
        g.drawString(this.font, "\u00A7c\u25B6 GUN", (int)((bx + 8) * invScale), (int)((by + 133) * invScale), 0xAAFF4444, false);
        g.drawString(this.font, "\u00A79\u25B6 MELEE", (int)((bx + 44) * invScale), (int)((by + 133) * invScale), 0xAA4488FF, false);
        g.drawString(this.font, "\u00A7a\u25B6 ITEMS", (int)((bx + 62) * invScale), (int)((by + 133) * invScale), 0xAA22AA22, false);
        g.drawString(this.font, "\u00A7e\u25B6 AMMO", (int)((bx + 8) * invScale), (int)((by + 75) * invScale), 0xAAAAA800, false);
        g.drawString(this.font, "\u00A77\u25B6 EXPANDABLE", (int)((bx + 80) * invScale), (int)((by + 75) * invScale), 0xAA888888, false);
        g.pose().popPose();
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
