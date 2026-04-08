package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.networking.SyncDataMessage;
import com.levanilla.rogue.networking.SyncStashMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 繧ｷ繝ｧ繝・・縺ｮ雉ｼ蜈･繝ｻ螢ｲ蜊ｴ繝ｻ繧ｹ繧ｿ繝・す繝･邂｡逅・・繝薙ず繝阪せ繝ｭ繧ｸ繝・け縲・ * RogueActionMessage 縺九ｉ謚ｽ蜃ｺ縺励∝腰荳雋ｬ蜍吶〒邂｡逅・☆繧九・ */
public final class ShopService {

    private ShopService() {}

    // ===== 雉ｼ蜈･ =====

    public static void handleBuyItem(ServerPlayer player, String itemId) {
        int price = findPrice(itemId);

        // Upgrade handling
        if (itemId.equals("rogue:stash_upgrade")) {
            handleStashUpgrade(player, price);
            return;
        }
        if (itemId.equals("rogue:inv_upgrade")) {
            handleInventoryUpgrade(player, price);
            return;
        }
        // Recovery items
        if (itemId.startsWith("rogue:medkit") || itemId.startsWith("rogue:field_ration")
                || itemId.startsWith("rogue:stamina_shot")) {
            if (!CurrencyManager.consumeGold(player, price)) {
                player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
                return;
            }
            ItemStack recItem = createRecoveryItem(itemId);
            if (!recItem.isEmpty()) {
                placeItemIntoInventory(player, recItem, itemId, 0);
            }
            syncGold(player);
            return;
        }

        // 騾壼ｸｸ繧｢繧､繝・Β雉ｼ蜈･
        if (!CurrencyManager.consumeGold(player, price)) {
            player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
            return;
        }

        try {
            ItemStack resultStack = createItemStack(itemId);
            if (!resultStack.isEmpty()) {
                placeItemIntoInventory(player, resultStack, itemId, price);
            } else {
                player.sendSystemMessage(Component.literal("\u00A7c[ERROR] Item not found in registry: " + itemId));
                CurrencyManager.addGold(player, price);
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("\u00A7c[ERROR] Creation failed: " + e.getMessage()));
            CurrencyManager.addGold(player, price);
        }

        // Sync gold after purchase
        syncGold(player);
    }

    // ===== 螢ｲ蜊ｴ =====

    public static void handleSellItem(ServerPlayer player, String slotData) {
        try {
            int slot = Integer.parseInt(slotData);
            // 武器スロットや拡張インベントリ等もすべて売却可能にする
            if (slot < 0 || slot >= 36) {
                player.sendSystemMessage(Component.literal("\u00A7c[SHOP] Invalid slot for selling."));
                return;
            }
            if (slot >= player.getInventory().getContainerSize()) return;
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) return;

            int sellPrice = PriceManager.getSellPrice(stack);
            if (sellPrice <= 0) {
                player.sendSystemMessage(Component.literal("\u00A7cThis item cannot be sold."));
                return;
            }

            CurrencyManager.addGold(player, sellPrice);
            String itemName = stack.getHoverName().getString();
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            player.sendSystemMessage(Component.literal(
                "\u00A7a[SOLD] \u00A7f" + itemName + " \u00A77-> \u00A76+$" + sellPrice));
            RunManager.sync();
        } catch (NumberFormatException ignored) {}
    }

    // ===== 繧ｹ繧ｿ繝・す繝･ =====

    public static void handleUpgradeStash(ServerPlayer player) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());

        if (stash.unlockedLines < GameConstants.STASH_MAX_LINES) {
            int cost = GameConstants.STASH_UPGRADE_COST_BASE * (stash.unlockedLines + 1);
            if (CurrencyManager.consumeGold(player, cost)) {
                stash.unlockedLines++;
                data.setDirty();
                player.sendSystemMessage(Component.translatable("message.tac_rogue.stash_upgraded", stash.unlockedLines * 9));
                handleSyncStash(player);
            } else {
                player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
            }
        } else {
            player.sendSystemMessage(Component.translatable("message.tac_rogue.stash_max"));
        }
    }

    public static void handleSyncStash(ServerPlayer player) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
        TacRogueNetworking.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncStashMessage(stash.unlockedLines));
    }

    // ===== 繝倥Ν繝代・ =====

    private static int findPrice(String itemId) {
        for (com.levanilla.rogue.core.registry.ShopCatalog.ShopItem shopItem : TacZRegistryHelper.getAllShopItems()) {
            if (shopItem.id.equals(itemId)) return shopItem.price;
        }
        return PriceManager.getPrice(itemId);
    }

    private static void handleStashUpgrade(ServerPlayer player, int price) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
        if (stash.unlockedLines >= GameConstants.STASH_MAX_LINES) {
            player.sendSystemMessage(Component.translatable("message.tac_rogue.stash_max"));
            return;
        }
        if (CurrencyManager.consumeGold(player, price)) {
            stash.unlockedLines++;
            data.setDirty();
            player.sendSystemMessage(Component.translatable("message.tac_rogue.stash_upgraded", stash.unlockedLines * 9));
            handleSyncStash(player);
            RunManager.sync();
        } else {
            player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
        }
    }

    private static void handleInventoryUpgrade(ServerPlayer player, int price) {
        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        if (invLevel >= GameConstants.INV_MAX_LEVEL) {
            player.sendSystemMessage(Component.translatable("message.tac_rogue.inv_max"));
            return;
        }
        if (CurrencyManager.consumeGold(player, price)) {
            player.getPersistentData().putInt("TacRogue_InvLevel", invLevel + 1);
            int newSlots = Math.min(GameConstants.SLOT_AMMO_GUN2_END + (invLevel + 1) * 2, 35) - GameConstants.SLOT_AMMO_GUN2_END;
            player.sendSystemMessage(Component.translatable("message.tac_rogue.inv_upgraded", newSlots));
            RunManager.sync();
        } else {
            player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
        }
    }

    public static ItemStack createItemStack(String itemId) {
        if (itemId.equals("minecraft:snowball")) {
            return new ItemStack(Items.SNOWBALL, 16);
        }

        String fullId = itemId.contains(":") ? itemId : ("tacz:" + itemId);

        // 近接武器
        if (itemId.equals("lrtactical:dagger") || itemId.equals("lrtactical:karambit")
            || itemId.equals("lrtactical:baseball_bat")) {
            return createMeleeStack(fullId);
        }
        // Attachment
        if (isKnownAttachment(fullId)) {
            return createAttachmentStack(fullId);
        }
        // 蠑ｾ阮ｬ
        if (isKnownAmmo(fullId)) {
            return createAmmoStack(fullId);
        }
        // Gun
        return createGunStack(fullId);
    }

    public static ItemStack createMeleeStack(String fullId) {
        net.minecraft.world.item.Item meleeBase = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("lrtactical", "melee"));
        if (meleeBase != null && meleeBase != Items.AIR) {
            ItemStack stack = new ItemStack(meleeBase);
            CompoundTag tag = new CompoundTag();
            tag.putString("MeleeWeaponId", fullId);
            stack.setTag(tag);
            return stack;
        }
        // fallback: modern_kinetic_gun
        net.minecraft.world.item.Item gunItem = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("tacz", "modern_kinetic_gun"));
        if (gunItem != null) {
            ItemStack stack = new ItemStack(gunItem);
            CompoundTag tag = new CompoundTag();
            tag.putString("MeleeWeaponId", fullId);
            stack.setTag(tag);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack createAttachmentStack(String fullId) {
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("tacz", "attachment"));
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            CompoundTag tag = new CompoundTag();
            tag.putString("AttachmentId", fullId);
            stack.setTag(tag);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack createAmmoStack(String fullId) {
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("tacz", "ammo"));
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            CompoundTag tag = new CompoundTag();
            tag.putString("AmmoId", fullId);
            stack.setTag(tag);
            stack.setCount(TacZRegistryHelper.getAmmoStackSize(fullId));
            return stack;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack createGunStack(String fullId) {
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("tacz", "modern_kinetic_gun"));
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            CompoundTag tag = new CompoundTag();
            tag.putString("GunId", fullId);
            tag.putInt("GunCurrentAmmoCount", TacZRegistryHelper.getMagazineSize(fullId));
            tag.putString("GunFireMode", "SEMI");
            tag.putBoolean("HasBulletInBarrel", true);
            stack.setTag(tag);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * アイテムをインベントリに配置。スロット満杖時はスタッシュに送り、
     * スタッシュも満杖の場合はゴールドを返金する。
     */
    private static void placeItemIntoInventory(ServerPlayer player, ItemStack stack, String itemId, int price) {
        boolean isGun = stack.hasTag() && stack.getTag().contains("GunId");
        boolean isMelee = stack.hasTag() && stack.getTag().contains("MeleeWeaponId");
        boolean isAmmo = stack.hasTag() && stack.getTag().contains("AmmoId");

        if (isGun) {
            boolean slot0Full = !player.getInventory().items.get(0).isEmpty();
            boolean slot1Full = !player.getInventory().items.get(1).isEmpty();
            if (slot0Full && slot1Full) {
                if (sendToStash(player, stack)) {
                    player.sendSystemMessage(Component.literal(
                        "\u00A7e[SHOP] Gun slots full -> Sent to stash: " + itemId.toUpperCase()));
                } else {
                    refundPurchase(player, price, itemId);
                }
            } else {
                player.getInventory().setItem(slot0Full ? 1 : 0, stack);
                player.sendSystemMessage(Component.translatable("message.tac_rogue.shop_purchased", itemId.toUpperCase()));
            }
        } else if (isMelee) {
            if (!player.getInventory().items.get(2).isEmpty()) {
                if (sendToStash(player, stack)) {
                    player.sendSystemMessage(Component.literal(
                        "\u00A7e[SHOP] Melee slot full -> Sent to stash: " + itemId.toUpperCase()));
                } else {
                    refundPurchase(player, price, itemId);
                }
            } else {
                player.getInventory().setItem(2, stack);
                player.sendSystemMessage(Component.translatable("message.tac_rogue.shop_purchased", itemId.toUpperCase()));
            }
        } else if (isAmmo) {
            String ammoId = stack.getTag().getString("AmmoId");
            int targetSlotStart = findAmmoSlotForAmmo(player, ammoId);
            boolean placed = false;
            if (targetSlotStart >= 0) {
                for (int s = targetSlotStart; s <= targetSlotStart + 1; s++) {
                    if (player.getInventory().items.get(s).isEmpty()) {
                        player.getInventory().setItem(s, stack);
                        placed = true;
                        break;
                    }
                }
            }
            if (!placed) {
                for (int s = GameConstants.SLOT_AMMO_GUN1_START; s <= GameConstants.SLOT_AMMO_GUN2_END; s++) {
                    if (player.getInventory().items.get(s).isEmpty()) {
                        player.getInventory().setItem(s, stack);
                        placed = true;
                        break;
                    }
                }
            }
            if (!placed) {
                for (int s = GameConstants.SLOT_ITEM_START; s <= GameConstants.SLOT_ITEM_END; s++) {
                    if (player.getInventory().items.get(s).isEmpty()) {
                        player.getInventory().setItem(s, stack);
                        placed = true;
                        break;
                    }
                }
            }
            if (!placed) {
                if (sendToStash(player, stack)) {
                    player.sendSystemMessage(Component.literal(
                        "\u00A7e[SHOP] Ammo overflow -> Sent to stash"));
                } else {
                    refundPurchase(player, price, itemId);
                }
            } else {
                player.sendSystemMessage(Component.translatable("message.tac_rogue.shop_purchased", itemId.toUpperCase()));
            }
        } else {
            boolean placed = false;
            for (int s = GameConstants.SLOT_ITEM_START; s <= GameConstants.SLOT_ITEM_END; s++) {
                if (player.getInventory().items.get(s).isEmpty()) {
                    player.getInventory().setItem(s, stack);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                if (sendToStash(player, stack)) {
                    player.sendSystemMessage(Component.literal(
                        "\u00A7e[SHOP] Item slots full -> Sent to stash"));
                } else {
                    refundPurchase(player, price, itemId);
                }
            } else {
                player.sendSystemMessage(Component.translatable("message.tac_rogue.shop_purchased", itemId.toUpperCase()));
            }
        }
    }

    /** スタッシュ満杖時のゴールド返金処理 */
    private static void refundPurchase(ServerPlayer player, int price, String itemId) {
        CurrencyManager.addGold(player, price);
        player.sendSystemMessage(Component.literal(
            "\u00A7c[SHOP] All slots & stash full! Refunded \u00A76$" + price + "\u00A7c for " + itemId.toUpperCase()));
    }

    /** Determine which ammo slot range to use based on gun's ammo type */
    private static int findAmmoSlotForAmmo(ServerPlayer player, String ammoId) {
        // Check gun slot 0
        ItemStack gun0 = player.getInventory().items.get(0);
        if (!gun0.isEmpty() && gun0.hasTag() && gun0.getTag().contains("GunId")) {
            String gunAmmo = TacZRegistryHelper.getAmmoForGun(gun0.getTag().getString("GunId"));
            if (gunAmmo.equals(ammoId)) return GameConstants.SLOT_AMMO_GUN1_START;
        }
        // Check gun slot 1
        ItemStack gun1 = player.getInventory().items.get(1);
        if (!gun1.isEmpty() && gun1.hasTag() && gun1.getTag().contains("GunId")) {
            String gunAmmo = TacZRegistryHelper.getAmmoForGun(gun1.getTag().getString("GunId"));
            if (gunAmmo.equals(ammoId)) return GameConstants.SLOT_AMMO_GUN2_START;
        }
        return -1; // No matching gun found
    }

    /**
     * スタッシュにアイテムを送る。成功時true、満杯時falseを返す。
     * @return スタッシュに配置できた場合true
     */
    public static boolean sendToStash(ServerPlayer player, ItemStack stack) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
        int maxSlots = stash.unlockedLines * 9;
        for (int i = 0; i < maxSlots; i++) {
            if (stash.getItem(i).isEmpty()) {
                stash.setItem(i, stack.copy());
                data.setDirty();
                return true;
            }
        }
        // スタッシュ満杯: アイテムをドロップせず、呼び出し元で返金処理を行う
        return false;
    }

    private static void syncGold(ServerPlayer player) {
        int currentGold = CurrencyManager.getGold(player);
        TacRogueNetworking.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncDataMessage("gold:" + currentGold));
    }

    private static boolean isKnownAttachment(String fullId) {
        for (String id : TacZRegistryHelper.getAllAttachmentIds()) {
            if (id.equals(fullId)) return true;
        }
        for (com.levanilla.rogue.core.registry.ShopCatalog.ShopItem item : TacZRegistryHelper.getAllShopItems()) {
            if (item.id.equals(fullId) && item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.ATTACHMENT) return true;
        }
        return false;
    }

    private static boolean isKnownAmmo(String fullId) {
        for (String id : TacZRegistryHelper.getAllAmmoIds()) {
            if (id.equals(fullId)) return true;
        }
        for (com.levanilla.rogue.core.registry.ShopCatalog.ShopItem item : TacZRegistryHelper.getAllShopItems()) {
            if (item.id.equals(fullId) && item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.AMMO) return true;
        }
        return false;
    }

    // ===== Recovery Items =====

    private static ItemStack createRecoveryItem(String itemId) {
        return switch (itemId) {
            case "rogue:medkit" -> {
                yield GearService.createMedkitStack(1);
            }
            case "rogue:field_ration" -> {
                var stack = new ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 3);
                applyRecoveryLore(stack, "\u00a7f\u2726 FIELD RATION", new String[]{
                    "\u00a77右クリックで即時使用", "\u00a77HP +6 / 満腹度回復"
                });
                stack.getOrCreateTag().putInt("CustomModelData", 39003);
                yield stack;
            }
            case "rogue:stamina_shot" -> {
                var stack = new ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE, 1);
                applyRecoveryLore(stack, "\u00a7b\u2726 STAMINA SHOT", new String[]{
                    "\u00a77右クリックで使用", "\u00a77スタミナが全回復し最大値が上昇"
                });
                stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
                stack.getOrCreateTag().putInt("HideFlags", 1);
                stack.getOrCreateTag().putInt("CustomModelData", 39005);
                yield stack;
            }
            default -> ItemStack.EMPTY;
        };
    }

    private static void applyRecoveryLore(ItemStack stack, String name, String[] lore) {
        stack.setHoverName(Component.literal(name));
        net.minecraft.nbt.CompoundTag display = stack.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag loreList = new net.minecraft.nbt.ListTag();
        for (String line : lore) {
            loreList.add(net.minecraft.nbt.StringTag.valueOf(
                Component.Serializer.toJson(Component.literal(line))));
        }
        display.put("Lore", loreList);
        stack.getOrCreateTag().putBoolean("rogue_item", true);
    }
}
