package com.levanilla.rogue.core;

import com.levanilla.rogue.core.registry.AmmoDatabase;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.core.registry.TacZGunRegistry;

import java.util.*;

/**
 * TacZ ショップデータのファサード — v0.4.0: TacZGunRegistry への移行。
 * GunProfile から自動的にショップアイテムを生成し、
 * サードパーティ銃パックも自動的にショップに出現する。
 */
public class TacZRegistryHelper {

    // ===== 型エイリアス (後方互換) =====
    public static final class ShopItem extends ShopCatalog.ShopItem {
        public ShopItem(String id, String displayName, int price, ShopCategory category) {
            super(id, displayName, price, toInternalCategory(category));
        }
        private static ShopCatalog.Category toInternalCategory(ShopCategory cat) {
            return ShopCatalog.Category.valueOf(cat.name());
        }
    }

    /** 後方互換のための ShopCategory — 内部では ShopCatalog.Category に委譲 */
    public enum ShopCategory {
        PISTOL("PISTOL", 0xFFAACC00),
        RIFLE("RIFLE", 0xFF00AAFF),
        SMG("SMG", 0xFF00FFAA),
        SHOTGUN("SHOTGUN", 0xFFFF6600),
        SNIPER("SNIPER", 0xFFFF0066),
        LMG("LMG", 0xFFFFAA00),
        MELEE("MELEE", 0xFFFF3333),
        ATTACHMENT("ATTACHMENT", 0xFF8800FF),
        AMMO("AMMO", 0xFF888888),
        SPECIAL("SPECIAL", 0xFFFFFF00);

        public final String label;
        public final int color;
        ShopCategory(String label, int color) { this.label = label; this.color = color; }

        public boolean isWeapon() {
            return this == PISTOL || this == RIFLE || this == SMG
                || this == SHOTGUN || this == SNIPER || this == LMG;
        }

        public static ShopCategory fromInternal(ShopCatalog.Category cat) {
            return valueOf(cat.name());
        }
    }

    // ===== ショップアイテムキャッシュ =====
    private static List<ShopCatalog.ShopItem> cachedShopItems = null;

    /**
     * 全ショップアイテムを取得。
     * v0.4.0: TacZGunRegistry から GunProfile を取得し、自動的にショップアイテムを生成。
     * サードパーティ銃パックも自動的に含まれる。
     */
    public static List<ShopCatalog.ShopItem> getAllShopItems() {
        if (cachedShopItems != null) return cachedShopItems;

        List<ShopCatalog.ShopItem> items = new ArrayList<>();

        // === 銃: TacZ API から自動取得 ===
        List<TacZGunRegistry.GunProfile> guns = TacZGunRegistry.getAllGuns();
        if (!guns.isEmpty()) {
            for (TacZGunRegistry.GunProfile gun : guns) {
                // SPECIAL カテゴリの銃は別扱い
                ShopCatalog.Category cat = gun.category;
                // ビルトイン価格が存在する場合はそちらを優先（手動バランス調整）
                int price = findBuiltinPrice(gun.id.toString());
                if (price <= 0) {
                    price = gun.autoPrice;
                }
                items.add(new ShopCatalog.ShopItem(gun.id.toString(), gun.displayName, price, cat));
            }
        } else {
            // フォールバック: API 取得失敗時はビルトインデータ
            items.addAll(ShopCatalog.getBuiltinGuns());
        }

        // === アタッチメント: TacZ API から自動取得 ===
        List<String> attachmentIds = TacZGunRegistry.getAllAttachmentIds();
        if (!attachmentIds.isEmpty()) {
            for (String id : attachmentIds) {
                String name = ShopCatalog.extractName(id).toUpperCase();
                
                // 拡張マガジンの名前を "EXTEND MAG (種類)" フォーマットに統一
                String slotType = com.levanilla.rogue.core.registry.AttachmentDatabase.getSlotType(id);
                if (slotType == null) slotType = com.levanilla.rogue.core.registry.AttachmentDatabase.guessSlotType(id);
                
                if ("extended_mag".equals(slotType) || name.contains("EXTENDED MAG") || name.contains("EXT MAG")) {
                    String clean = name.replace("EXTENDED MAG", "").replace("EXT MAG", "").replace("_", " ").trim();
                    if (clean.isEmpty()) {
                        name = "EXTEND MAG";
                    } else {
                        name = "EXTEND MAG (" + clean + ")";
                    }
                }
                
                int price = findBuiltinAttachmentPrice(id);
                if (price <= 0) price = 300; // デフォルト価格
                items.add(new ShopCatalog.ShopItem(id, name, price, ShopCatalog.Category.ATTACHMENT));
            }
        } else {
            items.addAll(ShopCatalog.getBuiltinAttachments());
        }

        // === 弾薬: TacZ API から自動取得 ===
        List<String> ammoIds = TacZGunRegistry.getAllAmmoIds();
        if (!ammoIds.isEmpty()) {
            for (String id : ammoIds) {
                String name = ShopCatalog.extractName(id);
                int price = findBuiltinAmmoPrice(id);
                if (price <= 0) price = 100;
                int stackSize = AmmoDatabase.getAmmoStackSize(id);
                items.add(new ShopCatalog.ShopItem(id, name.toUpperCase() + " x" + stackSize, price, ShopCatalog.Category.AMMO));
            }
        } else {
            items.addAll(ShopCatalog.getBuiltinAmmo());
        }

        // 近接武器 + 特殊 (常にビルトイン) ===
        items.addAll(ShopCatalog.getBuiltinMelee());
        items.addAll(ShopCatalog.getBuiltinSpecial());

        // A-Z順にソートする前に、GameConstantsの倍率を適用する
        for (ShopCatalog.ShopItem item : items) {
            float mult = 1.0f;
            if (item.category.isWeapon() || item.category == ShopCatalog.Category.MELEE) {
                mult = com.levanilla.rogue.core.GameConstants.PRICE_MULT_WEAPON;
            } else if (item.category == ShopCatalog.Category.ATTACHMENT) {
                mult = com.levanilla.rogue.core.GameConstants.PRICE_MULT_ATTACHMENT;
            } else if (item.category == ShopCatalog.Category.AMMO) {
                mult = com.levanilla.rogue.core.GameConstants.PRICE_MULT_AMMO;
            } else if (item.category == ShopCatalog.Category.SPECIAL) {
                mult = com.levanilla.rogue.core.GameConstants.PRICE_MULT_SPECIAL;
            }
            if (mult != 1.0f) {
                try {
                    // Reflection or recreate to modify the final field
                    java.lang.reflect.Field priceField = ShopCatalog.ShopItem.class.getDeclaredField("price");
                    priceField.setAccessible(true);
                    priceField.setInt(item, (int)(item.price * mult));
                } catch (Exception ignored) {}
            }
        }

        // A-Z順にソート (カテゴリ別に抽出された際もアルファベット順になる)
        items.sort(java.util.Comparator.comparing(item -> item.displayName));

        cachedShopItems = items;
        return items;
    }

    /** カテゴリでフィルタリング */
    public static List<ShopCatalog.ShopItem> getItemsByCategory(ShopCatalog.Category category) {
        List<ShopCatalog.ShopItem> result = new ArrayList<>();
        for (ShopCatalog.ShopItem item : getAllShopItems()) {
            if (item.category == category) result.add(item);
        }
        return result;
    }

    /** 後方互換: ShopCategory でフィルタリング */
    public static List<ShopCatalog.ShopItem> getItemsByCategory(ShopCategory category) {
        return getItemsByCategory(ShopCatalog.Category.valueOf(category.name()));
    }

    /** キャッシュリセット */
    public static void clearCache() {
        TacZGunRegistry.clearCache();
        cachedShopItems = null;
    }

    // ===== 委譲メソッド (後方互換 — TacZGunRegistry ベース) =====

    public static List<String> getAllGunIds()        { return TacZGunRegistry.getAllGunIds(); }
    public static List<String> getAllAmmoIds()        { return TacZGunRegistry.getAllAmmoIds(); }
    public static List<String> getAllAttachmentIds()  { return TacZGunRegistry.getAllAttachmentIds(); }
    public static String getAmmoForGun(String gunId) { return TacZGunRegistry.getAmmoForGun(gunId); }
    public static int getMagazineSize(String gunId)  { return TacZGunRegistry.getMagazineSize(gunId); }
    public static int getAmmoStackSize(String ammoId) { return AmmoDatabase.getAmmoStackSize(ammoId); }
    public static String getAttachmentTypeDesc(String id) { return ShopCatalog.getAttachmentTypeDesc(id); }

    // ===== ビルトイン価格検索 (手動バランス調整した価格を優先) =====

    private static int findBuiltinPrice(String gunId) {
        for (ShopCatalog.ShopItem item : ShopCatalog.getBuiltinGuns()) {
            if (item.id.equals(gunId)) return item.price;
        }
        return -1;
    }

    private static int findBuiltinAttachmentPrice(String attachId) {
        for (ShopCatalog.ShopItem item : ShopCatalog.getBuiltinAttachments()) {
            if (item.id.equals(attachId)) return item.price;
        }
        return -1;
    }

    private static int findBuiltinAmmoPrice(String ammoId) {
        for (ShopCatalog.ShopItem item : ShopCatalog.getBuiltinAmmo()) {
            if (item.id.equals(ammoId)) return item.price;
        }
        return -1;
    }
}
