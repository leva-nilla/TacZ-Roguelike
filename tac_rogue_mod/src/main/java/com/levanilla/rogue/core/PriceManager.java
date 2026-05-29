package com.levanilla.rogue.core;

import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.core.registry.TacZGunRegistry;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraft.resources.ResourceLocation;

/**
 * Dynamic price manager for shop items (v0.4.0 balanced)
 *
 * Economy design (70 mobs/floor):
 * - Initial cash: $100
 * - Kill reward: 15 + floor × 8 $/kill
 *
 * v0.4.0: TacZ API ベースの正確な価格算出に移行。
 * GunProfile から DPS / マガジン / カテゴリを取得し、パラメータベースで計算。
 */
public class PriceManager {

    public static int getKillReward(int floor) {
        int reward = GameConstants.KILL_REWARD_BASE + floor * GameConstants.KILL_REWARD_PER_FLOOR;
        return Math.min(reward, getKillRewardCap(floor));
    }

    public static int getKillRewardCap(int floor) {
        if (floor >= 101) return GameConstants.KILL_REWARD_ENDLESS_CAP;
        if (floor >= 51) return GameConstants.KILL_REWARD_HIGH_CAP;
        return GameConstants.KILL_REWARD_MAX_CAP;
    }

    /** 初期ゴールド */
    public static int getInitialGold() {
        return GameConstants.INITIAL_GOLD;
    }

    /** デスペナルティ（所持ゴールドの割合） */
    public static int getDeathPenalty(int currentGold) {
        return Math.max(0, (int)(currentGold * DifficultyManager.getDeathPenaltyRate()));
    }

    /**
     * 各種アップグレードの価格算出ロジック (v0.4.0)
     */
    public static int getUpgradePrice(String upgradeId, int currentLevel) {
        int base = 2000;
        if (upgradeId.equals("rogue:stash_upgrade")) base = GameConstants.STASH_UPGRADE_COST_BASE;
        else if (upgradeId.equals("rogue:inv_upgrade")) base = GameConstants.INV_UPGRADE_COST_BASE; 
        else if (upgradeId.equals("rogue:ammo_capacity_upgrade")) base = GameConstants.AMMO_CAP_UPGRADE_COST_BASE;
        else if (upgradeId.equals("rogue:melee_upgrade")) base = GameConstants.MELEE_UPGRADE_COST_BASE;
        else if (upgradeId.equals("rogue:flashlight_upgrade")) base = GameConstants.FLASHLIGHT_UPGRADE_COST_BASE;
        else if (upgradeId.equals("rogue:random_perk")) {
            return GameConstants.RANDOM_PERK_BASE_PRICE + (currentLevel * GameConstants.RANDOM_PERK_PRICE_STEP);
        }
        
        // 2000をベースに、購入ごとに価格が倍々(レベル比例)になるスケーリング
        return base * (currentLevel + 1);
    }

    /**
     * 武器IDから販売価格を算出する。
     * v0.4.0: TacZ API (TimelessAPI → GunData) から正確なパラメータを取得して計算。
     * フォールバック時は ID のハッシュ値ベースで武器カテゴリを推定。
     */
    public static int getPrice(String gunId) {
        // TacZGunRegistry (API ベース) から GunProfile を取得
        TacZGunRegistry.GunProfile profile = TacZGunRegistry.getProfile(gunId);
        if (profile != null) {
            return profile.autoPrice;
        }
        // フォールバック: ID ベースのカテゴリ推定
        return getHashBasedPrice(gunId);
    }

    public static int getShopBuyPrice(ShopCatalog.ShopItem item, int shopFloor) {
        if (item == null) return 0;
        int basePrice = item.price;
        if (isRarityPricedWeapon(item.category)) {
            if (isGunCategory(item.category)) {
                basePrice = getPrice(item.id);
            }
            WeaponRarity.Rarity rarity = WeaponRarity.rollShopRarity(Math.max(1, shopFloor), item.id);
            return roundPrice(basePrice * getRarityPriceMultiplier(rarity));
        }
        return basePrice;
    }

    public static double getRarityPriceMultiplier(WeaponRarity.Rarity rarity) {
        if (rarity == null) return 1.0D;
        return switch (rarity) {
            case COMMON -> 1.00D;
            case UNCOMMON -> 1.35D;
            case RARE -> 1.85D;
            case EPIC -> 2.60D;
            case LEGENDARY -> 3.60D;
        };
    }

    private static boolean isGunCategory(ShopCatalog.Category category) {
        return category == ShopCatalog.Category.PISTOL
            || category == ShopCatalog.Category.RIFLE
            || category == ShopCatalog.Category.SMG
            || category == ShopCatalog.Category.SHOTGUN
            || category == ShopCatalog.Category.SNIPER
            || category == ShopCatalog.Category.LMG
            || category == ShopCatalog.Category.EXPLOSIVE;
    }

    private static boolean isRarityPricedWeapon(ShopCatalog.Category category) {
        return isGunCategory(category) || category == ShopCatalog.Category.MELEE;
    }

    private static int roundPrice(double price) {
        int rounded = (int) (Math.round(price / 50.0D) * 50);
        return Math.max(400, Math.min(60000, rounded));
    }

    /** Hash-based price estimation (fallback when reflection fails) */
    private static int getHashBasedPrice(String gunId) {
        String lower = gunId.toLowerCase();
        if (lower.contains("rpg") || lower.contains("m320") || lower.contains("grenade")) {
            return 10000 + (Math.abs(gunId.hashCode()) % 5000);
        }
        if (lower.contains("sniper") || lower.contains("awp") || lower.contains("svd") || lower.contains("m107")) {
            return 8000 + (Math.abs(gunId.hashCode()) % 7000);
        }
        if (lower.contains("m249") || lower.contains("rpk") || lower.contains("minigun") || lower.contains("lmg")) {
            return 10000 + (Math.abs(gunId.hashCode()) % 8000);
        }
        if (lower.contains("ak") || lower.contains("m4") || lower.contains("scar") || lower.contains("m16") || lower.contains("rifle")) {
            return 4000 + (Math.abs(gunId.hashCode()) % 4000);
        }
        if (lower.contains("smg") || lower.contains("mp") || lower.contains("ump") || lower.contains("p90") || lower.contains("vector")) {
            return 2000 + (Math.abs(gunId.hashCode()) % 2500);
        }
        if (lower.contains("shotgun") || lower.contains("870") || lower.contains("spas")) {
            return 2000 + (Math.abs(gunId.hashCode()) % 5000);
        }
        // Pistol / other
        return 800 + (Math.abs(gunId.hashCode()) % 1700);
    }

    public static int getAttachmentBuyPrice(String attachmentId) {
        if (attachmentId == null || attachmentId.isBlank()) return 0;
        try {
            ResourceLocation id = new ResourceLocation(attachmentId);
            var index = TimelessAPI.getCommonAttachmentIndex(id).orElse(null);
            if (index != null) {
                String slot = index.getType() == null
                    ? null
                    : index.getType().name().toLowerCase(java.util.Locale.ROOT);
                return getAttachmentBuyPrice(attachmentId, slot, index.getData());
            }
        } catch (Throwable ignored) {
        }
        return getAttachmentFallbackPrice(attachmentId);
    }

    public static int getAttachmentBuyPrice(String attachmentId, String slot, AttachmentData data) {
        int price = getAttachmentSlotBasePrice(slot);
        if (data != null) {
            int extLevel = Math.max(0, data.getExtendedMagLevel());
            if (extLevel > 0) price += 350 + extLevel * 450;

            float weight = data.getWeight();
            if (weight < 0.0F) {
                price += Math.min(900, Math.round(Math.abs(weight) * 250.0F));
            } else if (weight > 0.0F) {
                price -= Math.min(250, Math.round(weight * 50.0F));
            }

            int modifierCount = data.getModifier() == null ? 0 : data.getModifier().size();
            price += getAttachmentModifierPrice(modifierCount);
        }
        return roundAttachmentPrice(price);
    }

    private static int getAttachmentModifierPrice(int modifierCount) {
        if (modifierCount <= 0) return 0;
        int primary = Math.min(modifierCount, 3) * 250;
        int extra = Math.max(0, modifierCount - 3) * 100;
        return Math.min(1400, primary + extra);
    }

    private static int getAttachmentSlotBasePrice(String slot) {
        String normalized = slot == null ? "unknown" : slot.toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "scope" -> 1000;
            case "muzzle" -> 850;
            case "grip" -> 700;
            case "laser" -> 550;
            case "extended_mag", "extendedmag" -> 900;
            case "stock" -> 650;
            default -> 500;
        };
    }

    private static int getAttachmentFallbackPrice(String attachmentId) {
        String slot = com.levanilla.rogue.core.registry.AttachmentDatabase.getSlotType(attachmentId);
        if (slot == null) slot = com.levanilla.rogue.core.registry.AttachmentDatabase.guessSlotType(attachmentId);
        String name = com.levanilla.rogue.core.registry.ShopCatalog.extractName(attachmentId).toUpperCase(java.util.Locale.ROOT);
        int price = getAttachmentSlotBasePrice(slot);
        if ("extended_mag".equals(slot)) {
            if (name.contains("3") || name.contains("III")) price += 1400;
            else if (name.contains("2") || name.contains("II")) price += 900;
            else price += 600;
        } else if (name.contains("4X") || name.contains("6X") || name.contains("8X") || name.contains("SR") || name.contains("SNIPER")) {
            price += 700;
        } else if (name.contains("SILENCER") || name.contains("SUPPRESSOR")) {
            price += 700;
        }
        return roundAttachmentPrice(price);
    }

    private static int roundAttachmentPrice(double price) {
        int rounded = (int) (Math.round(price / 50.0D) * 50);
        return Math.max(450, Math.min(8500, rounded));
    }

    public static int getAmmoBuyPrice(String ammoId) {
        String name = com.levanilla.rogue.core.registry.ShopCatalog.extractName(ammoId).toUpperCase();
        int basePrice = 150;
        if (name.contains("50 BMG") || name.contains("338")) basePrice = 300;
        else if (name.contains("RPG") || name.contains("40MM")) basePrice = 500;
        return basePrice;
    }

    /**
     * アイテムの売却価格を算出する。
     * 銃: 購入価格の 40% (GUN_SELL_RATE)
     * アタッチメント: 購入価格の 40% (GUN_SELL_RATE)
     * 弾薬: 1発あたり (箱の価格 / 32) の購入価格の 40% (最低$1)
     */
    public static int getSellPrice(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return 0;
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null) return 0;

        // TacZ 銃
        if (tag.contains("GunId")) {
            String gunId = tag.getString("GunId");
            int buyPrice = getWeaponStackBuyPrice(gunId, stack);
            int gunSell = Math.round(buyPrice * GameConstants.GUN_SELL_RATE);
            // アタッチメント価格を加算
            if (tag.contains("Attachments")) {
                net.minecraft.nbt.CompoundTag attachments = tag.getCompound("Attachments");
                for (String key : attachments.getAllKeys()) {
                    String attId = readAttachmentId(attachments, key);
                    if (attId != null && !attId.isEmpty()) {
                        gunSell += Math.round(getAttachmentStackBuyPrice(attId) * GameConstants.GUN_SELL_RATE);
                    }
                }
            }
            return gunSell;
        }

        // LrTactical 近接武器
        if (tag.contains("MeleeWeaponId")) {
            String meleeId = tag.getString("MeleeWeaponId");
            int buyPrice = getMeleeStackBuyPrice(meleeId, stack);
            return Math.round(buyPrice * GameConstants.GUN_SELL_RATE);
        }

        // TacZ ammo
        if (tag.contains("AmmoId")) {
            String ammoId = tag.getString("AmmoId");
            int buyPrice = getAmmoBuyPrice(ammoId);
            int sellPerRound = Math.max(1, (int)((buyPrice / 32.0f) * GameConstants.GUN_SELL_RATE));
            return sellPerRound * stack.getCount();
        }

        // TacZ アタッチメント
        if (tag.contains("AttachmentId")) {
            String attId = tag.getString("AttachmentId");
            int buyPrice = getAttachmentStackBuyPrice(attId);
            return Math.round(buyPrice * GameConstants.GUN_SELL_RATE) * stack.getCount();
        }

        // ローグアイテム（消耗品）
        if (tag.getBoolean("rogue_item")) {
            String utilityId = tag.getString(com.levanilla.rogue.core.service.RogueUtilityItemService.UTILITY_ID_KEY);
            if (!utilityId.isEmpty()) {
                ShopCatalog.ShopItem utilityItem = findCatalogItem(utilityId);
                int buyPrice = utilityItem != null
                    ? utilityItem.price
                    : com.levanilla.rogue.core.service.RogueUtilityItemService.getReferencePrice(utilityId);
                return Math.round(buyPrice * GameConstants.GUN_SELL_RATE) * stack.getCount();
            }
            int unitPrice;
            if (stack.is(net.minecraft.world.item.Items.RAW_IRON)) unitPrice = GameConstants.SCRAP_SELL_VALUE;
            else if (stack.is(net.minecraft.world.item.Items.RAW_GOLD)) unitPrice = GameConstants.GOLD_CACHE_VALUE;
            else if (stack.is(net.minecraft.world.item.Items.COOKED_BEEF)) unitPrice = 20; // FIELD RATION
            else if (stack.is(net.minecraft.world.item.Items.HONEY_BOTTLE)) unitPrice = 80; // STAMINA BOOST
            else if (stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE)) unitPrice = 120; // EMERGENCY RATION
            else unitPrice = 10;
            return unitPrice * stack.getCount();
        }

        return 0; // 売却不可
    }

    private static int getWeaponStackBuyPrice(String gunId, net.minecraft.world.item.ItemStack stack) {
        int basePrice = getPrice(gunId);
        return roundPrice(basePrice * getRarityPriceMultiplier(WeaponRarity.getRarity(stack)));
    }

    private static int getMeleeStackBuyPrice(String meleeId, net.minecraft.world.item.ItemStack stack) {
        ShopCatalog.ShopItem item = findCatalogItem(meleeId);
        int basePrice = item != null ? item.price : 1000;
        return roundPrice(basePrice * getRarityPriceMultiplier(WeaponRarity.getRarity(stack)));
    }

    private static int getAttachmentStackBuyPrice(String attachmentId) {
        ShopCatalog.ShopItem item = findCatalogItem(attachmentId);
        if (item != null && item.category == ShopCatalog.Category.ATTACHMENT) {
            return item.price;
        }
        return getAttachmentBuyPrice(attachmentId);
    }

    private static ShopCatalog.ShopItem findCatalogItem(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            for (ShopCatalog.ShopItem item : TacZRegistryHelper.getAllShopItems()) {
                if (id.equals(item.id)) return item;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String readAttachmentId(net.minecraft.nbt.CompoundTag attachments, String key) {
        net.minecraft.nbt.Tag raw = attachments.get(key);
        if (raw instanceof net.minecraft.nbt.CompoundTag compound) {
            return compound.getString("AttachmentId");
        }
        if (raw instanceof net.minecraft.nbt.StringTag stringTag) {
            return stringTag.getAsString();
        }
        return "";
    }
}
