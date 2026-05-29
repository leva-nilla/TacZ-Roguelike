package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.networking.PopupNotificationMessage;
import com.levanilla.rogue.networking.SyncDataMessage;
import com.levanilla.rogue.networking.SyncStashMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

/**
 * ショップの購入・売却・スタッシュ管理のビジネスロジック。
 * RogueActionMessage から抽出し、単一責務で管理する。
 */
public final class ShopService {

    public static final int STASH_SELL_SLOT_OFFSET = 1000;

    private ShopService() {}

    // ===== 購入 =====

    public static void handleBuyItem(ServerPlayer player, String itemId) {
        int shopFloor = getShopFloor(player);
        int blackMarket = DeepProgressService.getBlackMarketLevel(player);
        ShopCatalog.ShopItem catalogItem = findCatalogItem(itemId);
        int price = findPrice(itemId, shopFloor);
        if (catalogItem != null && !ShopStockManager.isAvailable(catalogItem, shopFloor, blackMarket)) {
            notifyShop(player, PopupNotificationMessage.PopupType.WARNING,
                Component.literal("SHOP"),
                Component.translatable("message.tac_rogue.shop_not_in_stock", catalogItem.displayName));
            return;
        }

        // SEC-4: 未登録IDで0以下の価格が返った場合は拒否
        if (price <= 0 && !itemId.startsWith("rogue:")) {
            notifyShop(player, PopupNotificationMessage.PopupType.WARNING,
                Component.literal("SHOP"),
                Component.translatable("message.tac_rogue.shop_unknown_item", itemId));
            return;
        }
        // Upgrade handling
        if (itemId.equals("rogue:stash_upgrade")) {
            ShopUpgradeService.handleStashUpgrade(player);
            return;
        }
        if (itemId.equals("rogue:inv_upgrade")) {
            ShopUpgradeService.handleInventoryUpgrade(player);
            return;
        }
        if (itemId.equals("rogue:ammo_capacity_upgrade")) {
            ShopUpgradeService.handleAmmoCapacityUpgrade(player);
            return;
        }
        if (itemId.equals("rogue:melee_upgrade")) {
            ShopUpgradeService.handleMeleeUpgrade(player);
            return;
        }
        if (itemId.equals("rogue:flashlight_upgrade")) {
            ShopUpgradeService.handleFlashlightUpgrade(player);
            return;
        }
        if (itemId.equals("rogue:random_perk")) {
            ShopUpgradeService.handleRandomPerkPurchase(player);
            return;
        }
        // Recovery items
        if (itemId.startsWith("rogue:medkit") || itemId.startsWith("rogue:field_ration")
                || itemId.startsWith("rogue:stamina_shot") || itemId.startsWith("rogue:bandage")
                || itemId.startsWith("rogue:armor_plate") || itemId.startsWith("rogue:adrenaline")
                || itemId.startsWith("rogue:emp_device")) {
            if (!CurrencyManager.consumeGold(player, price)) {
                notifyShop(player, PopupNotificationMessage.PopupType.WARNING,
                    Component.literal("SHOP"),
                    Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
                return;
            }
            ItemStack recItem = RogueItemFactory.createRecoveryItem(itemId);
            if (!recItem.isEmpty()) {
                ShopPlacementService.placePurchasedItem(player, recItem, itemId, 0);
            }
            syncGold(player);
            return;
        }

        // 騾壼ｸｸ繧｢繧､繝・Β雉ｼ蜈･
        if (!CurrencyManager.consumeGold(player, price)) {
            notifyShop(player, PopupNotificationMessage.PopupType.WARNING,
                Component.literal("SHOP"),
                Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
            return;
        }

        try {
            ItemStack resultStack = RogueItemFactory.createShopItemStack(player, itemId, shopFloor);
            if (!resultStack.isEmpty()) {
                ShopPlacementService.placePurchasedItem(player, resultStack, itemId, price);
            } else {
                notifyShop(player, PopupNotificationMessage.PopupType.WARNING,
                    Component.literal("SHOP ERROR"),
                    Component.literal("Item not found: " + itemId));
                CurrencyManager.addGoldNoQuest(player, price);
            }
        } catch (Exception e) {
            notifyShop(player, PopupNotificationMessage.PopupType.WARNING,
                Component.literal("SHOP ERROR"),
                Component.literal("Creation failed: " + e.getMessage()));
            CurrencyManager.addGoldNoQuest(player, price);
        }

        // Sync gold after purchase
        syncGold(player);
    }

    // ===== 螢ｲ蜊ｴ =====

    public static void handleSellItem(ServerPlayer player, String slotData) {
        try {
            int slot = Integer.parseInt(slotData);
            if (slot >= STASH_SELL_SLOT_OFFSET) {
                handleSellStashItem(player, slot - STASH_SELL_SLOT_OFFSET);
                return;
            }
            // 武器スロットや拡張インベントリ等もすべて売却可能にする
            if (slot < 0 || slot >= 36) {
                notifyShop(player, PopupNotificationMessage.PopupType.WARNING,
                    Component.literal("SHOP"),
                    Component.literal("Invalid slot for selling."));
                return;
            }
            if (slot >= player.getInventory().getContainerSize()) return;
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) return;

            int sellPrice = PriceManager.getSellPrice(stack);
            if (sellPrice <= 0) {
                notifyShop(player, PopupNotificationMessage.PopupType.WARNING,
                    Component.literal("SHOP"),
                    Component.literal("This item cannot be sold."));
                return;
            }

            GoldGainService.award(player, sellPrice);
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            notifyShop(player, PopupNotificationMessage.PopupType.REWARD,
                Component.literal("SOLD"),
                Component.literal("+$" + sellPrice + " " + stack.getHoverName().getString()));
            syncGold(player);
            TacRogueNetworking.openShop(player);
        } catch (NumberFormatException ignored) {}
    }

    private static void handleSellStashItem(ServerPlayer player, int stashSlot) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
        int maxSlots = Math.min(stash.unlockedLines * 9, stash.getContainerSize());
        if (stashSlot < 0 || stashSlot >= maxSlots) {
            notifyShop(player, PopupNotificationMessage.PopupType.WARNING,
                Component.literal("SHOP"),
                Component.literal("Invalid stash slot for selling."));
            return;
        }

        ItemStack stack = stash.getItem(stashSlot);
        if (stack.isEmpty()) return;

        int sellPrice = PriceManager.getSellPrice(stack);
        if (sellPrice <= 0) {
            notifyShop(player, PopupNotificationMessage.PopupType.WARNING,
                Component.literal("SHOP"),
                Component.literal("This stash item cannot be sold."));
            return;
        }

        GoldGainService.award(player, sellPrice);
        stash.setItem(stashSlot, ItemStack.EMPTY);
        data.setDirty();
        notifyShop(player, PopupNotificationMessage.PopupType.REWARD,
            Component.literal("SOLD"),
            Component.literal("+$" + sellPrice + " " + stack.getHoverName().getString()));
        syncGold(player);
        TacRogueNetworking.openShop(player);
    }

    // ===== 繧ｹ繧ｿ繝・す繝･ =====

    public static void handleUpgradeStash(ServerPlayer player) {
        ShopUpgradeService.handleLegacyStashUpgrade(player);
    }

    public static void handleSyncStash(ServerPlayer player) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
        TacRogueNetworking.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncStashMessage(stash.unlockedLines));
    }

    // ===== 繝倥Ν繝代・ =====

    private static int findPrice(String itemId, int shopFloor) {
        ShopCatalog.ShopItem catalogItem = findCatalogItem(itemId);
        if (catalogItem != null) return PriceManager.getShopBuyPrice(catalogItem, shopFloor);
        return PriceManager.getPrice(itemId);
    }

    private static ShopCatalog.ShopItem findCatalogItem(String itemId) {
        for (ShopCatalog.ShopItem shopItem : TacZRegistryHelper.getAllShopItems()) {
            if (shopItem.id.equals(itemId)) return shopItem;
        }
        return null;
    }

    private static int getShopFloor(ServerPlayer player) {
        PlayerRunData data = RunManager.getData(player);
        return ShopStockManager.getShopFloor(data.getCurrentFloor(), data.isFloorCleared());
    }

    public static ItemStack createItemStack(ServerPlayer player, String itemId) {
        return RogueItemFactory.createItemStack(player, itemId);
    }

    public static ItemStack createMeleeStack(String fullId) {
        return RogueItemFactory.createMeleeStack(fullId);
    }

    public static ItemStack createAttachmentStack(String fullId) {
        return RogueItemFactory.createAttachmentStack(fullId);
    }

    public static ItemStack createAmmoStack(ServerPlayer player, String fullId) {
        return RogueItemFactory.createAmmoStack(player, fullId);
    }

    public static ItemStack createGunStack(String fullId) {
        return RogueItemFactory.createGunStack(fullId);
    }

    public static boolean sendToStash(ServerPlayer player, ItemStack stack) {
        return ShopPlacementService.sendToStash(player, stack);
    }

    private static void syncGold(ServerPlayer player) {
        int currentGold = CurrencyManager.getGold(player);
        TacRogueNetworking.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncDataMessage("gold:" + currentGold));
    }

    private static void notifyShop(ServerPlayer player, PopupNotificationMessage.PopupType type,
                                   Component title, Component body) {
        PopupNotificationMessage.send(player, type, title, body, 120);
    }
}
