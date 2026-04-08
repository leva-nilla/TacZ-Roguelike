package com.levanilla.rogue.core;

import com.levanilla.rogue.core.registry.TacZGunRegistry;

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
        return Math.min(reward, GameConstants.KILL_REWARD_MAX_CAP);
    }

    /** 初期ゴールド */
    public static int getInitialGold() {
        return GameConstants.INITIAL_GOLD;
    }

    /** デスペナルティ（所持ゴールドの割合） */
    public static int getDeathPenalty(int currentGold) {
        return Math.max(0, (int)(currentGold * DifficultyManager.getDeathPenaltyRate()));
    }

    /** スタッシュ拡張の費用 */
    public static int getStashUpgradeCost(int currentLines) {
        return GameConstants.STASH_UPGRADE_COST_BASE * currentLines;
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
        String name = com.levanilla.rogue.core.registry.ShopCatalog.extractName(attachmentId).toUpperCase();
        String slot = com.levanilla.rogue.core.registry.AttachmentDatabase.getSlotType(attachmentId);
        if (slot == null) slot = com.levanilla.rogue.core.registry.AttachmentDatabase.guessSlotType(attachmentId);
        if (slot == null) slot = "unknown";
        
        int price = 300;
        if (slot.equals("scope")) {
            if (name.contains("4X") || name.contains("6X") || name.contains("8X") || name.contains("SR") || name.contains("SNIPER")) price = 1000;
            else price = 300;
        } else if (slot.equals("muzzle")) {
            if (name.contains("SILENCER") || name.contains("SUPPRESSOR")) price = 500;
            else price = 350;
        } else if (slot.equals("grip")) {
            price = 300;
        } else if (slot.equals("laser")) {
            price = 250;
        } else if (slot.equals("extended_mag")) {
            if (name.contains("3") || name.contains("III")) price = 800;
            else if (name.contains("2") || name.contains("II")) price = 600;
            else price = 400;
        } else if (slot.equals("stock")) {
            price = 350;
        } else if (slot.equals("ammo_mod")) {
            price = 450;
        }
        return price;
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
            int buyPrice = getPrice(gunId);
            int gunSell = (int)(buyPrice * GameConstants.GUN_SELL_RATE);
            // アタッチメント価格を加算
            if (tag.contains("Attachments")) {
                net.minecraft.nbt.CompoundTag attachments = tag.getCompound("Attachments");
                for (String key : attachments.getAllKeys()) {
                    String attId = attachments.getCompound(key).getString("AttachmentId");
                    if (attId != null && !attId.isEmpty()) {
                        gunSell += (int)(getAttachmentBuyPrice(attId) * GameConstants.GUN_SELL_RATE);
                    }
                }
            }
            return gunSell;
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
            int buyPrice = getAttachmentBuyPrice(attId);
            return (int)(buyPrice * GameConstants.GUN_SELL_RATE);
        }

        // ローグアイテム（消耗品）
        if (tag.getBoolean("rogue_item")) {
            if (stack.is(net.minecraft.world.item.Items.RAW_IRON)) return 30;  // SCRAP METAL
            if (stack.is(net.minecraft.world.item.Items.RAW_GOLD)) return 150; // GOLD CACHE
            if (stack.is(net.minecraft.world.item.Items.COOKED_BEEF)) return 20; // FIELD RATION
            if (stack.is(net.minecraft.world.item.Items.HONEY_BOTTLE)) return 80; // STAMINA BOOST
            if (stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE)) return 120; // EMERGENCY RATION
            return 10;
        }

        return 0; // 売却不可
    }
}
