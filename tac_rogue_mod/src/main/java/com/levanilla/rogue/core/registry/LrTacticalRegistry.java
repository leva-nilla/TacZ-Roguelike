package com.levanilla.rogue.core.registry;

import com.levanilla.rogue.core.registry.ShopCatalog.Category;
import com.levanilla.rogue.core.registry.ShopCatalog.ShopItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * lrtactical (LesRaisins Tactical Equipements) の API ラッパー。
 * <p>
 * lrtactical が存在する場合のみ動作し、
 * 全登録済み近接武器・投擲武器を動的に取得してショップに反映する。
 * <p>
 * lrtactical が未インストールの環境でもクラスロードエラーを起こさないよう、
 * API 呼び出しは全て内部クラスに隔離している。
 */
public final class LrTacticalRegistry {

    private LrTacticalRegistry() {}

    // ========== キャッシュ ==========
    private static List<ShopItem> cachedMelee = null;
    private static List<ShopItem> cachedThrowable = null;

    /** lrtactical mod が存在するか */
    public static boolean isAvailable() {
        return ModList.get().isLoaded("lrtactical");
    }

    /** キャッシュクリア */
    public static void clearCache() {
        cachedMelee = null;
        cachedThrowable = null;
    }

    // ========== 近接武器 ==========

    /**
     * 全登録済み近接武器を ShopItem として返す。
     * lrtactical 未インストール時は空リスト。
     */
    public static List<ShopItem> getAllMeleeWeapons() {
        if (cachedMelee != null) return cachedMelee;
        if (!isAvailable()) {
            cachedMelee = Collections.emptyList();
            return cachedMelee;
        }
        try {
            cachedMelee = LrTacticalApiCaller.fetchMeleeWeapons();
        } catch (Exception e) {
            cachedMelee = Collections.emptyList();
        }
        return cachedMelee;
    }

    // ========== 投擲武器 ==========

    /**
     * 全登録済み投擲武器を ShopItem として返す。
     * lrtactical 未インストール時は空リスト。
     */
    public static List<ShopItem> getAllThrowables() {
        if (cachedThrowable != null) return cachedThrowable;
        if (!isAvailable()) {
            cachedThrowable = Collections.emptyList();
            return cachedThrowable;
        }
        try {
            cachedThrowable = LrTacticalApiCaller.fetchThrowables();
        } catch (Exception e) {
            cachedThrowable = Collections.emptyList();
        }
        return cachedThrowable;
    }

    // ========== ItemStack 生成 ==========

    /**
     * 近接武器 ID から ItemStack を生成。
     * lrtactical API の MeleeWeaponIndex.createItemStack() を使用。
     */
    public static ItemStack createMeleeStack(ResourceLocation id) {
        if (!isAvailable()) return ItemStack.EMPTY;
        try {
            return LrTacticalApiCaller.createMeleeItemStack(id);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 投擲武器 ID から ItemStack を生成。
     * lrtactical API の ThrowableIndex.createItemStack() を使用。
     */
    public static ItemStack createThrowableStack(ResourceLocation id, int count) {
        if (!isAvailable()) return ItemStack.EMPTY;
        try {
            ItemStack stack = LrTacticalApiCaller.createThrowableItemStack(id);
            if (!stack.isEmpty()) stack.setCount(Math.min(count, stack.getMaxStackSize()));
            return stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * ItemStack が lrtactical の近接武器かどうかを判定。
     */
    public static boolean isMeleeWeapon(ItemStack stack) {
        if (!isAvailable() || stack.isEmpty()) return false;
        try {
            return LrTacticalApiCaller.isMelee(stack);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ItemStack が lrtactical の投擲武器かどうかを判定。
     */
    public static boolean isThrowable(ItemStack stack) {
        if (!isAvailable() || stack.isEmpty()) return false;
        try {
            return LrTacticalApiCaller.isThrowable(stack);
        } catch (Exception e) {
            return false;
        }
    }

    // ========== 内部クラス: API 直接呼び出し (classloading 隔離) ==========

    /**
     * lrtactical のクラスに直接依存するメソッドをここに隔離。
     * isAvailable() が true の場合のみ呼び出される。
     */
    private static final class LrTacticalApiCaller {

        static List<ShopItem> fetchMeleeWeapons() {
            List<ShopItem> items = new ArrayList<>();
            var indexes = me.xjqsh.lrtactical.api.LrTacticalAPI.getMeleeIndexes();
            for (var index : indexes) {
                ResourceLocation id = index.getId();
                String rawName = index.getDescriptionId();
                // descriptionId は "item.lrtactical.dagger" 形式なので最後のパート抽出
                String displayName = extractDisplayName(rawName, id);
                int price = calculateMeleePrice(index);
                items.add(new ShopItem(id.toString(), displayName, price, Category.MELEE));
            }
            return items;
        }

        static List<ShopItem> fetchThrowables() {
            List<ShopItem> items = new ArrayList<>();
            var indexes = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndexes();
            for (var index : indexes) {
                ResourceLocation id = index.getId();
                String rawName = index.getDescriptionId();
                String displayName = extractDisplayName(rawName, id);
                int price = calculateThrowablePrice(index);
                items.add(new ShopItem(id.toString(), displayName, price, Category.TACTICAL));
            }
            return items;
        }

        static ItemStack createMeleeItemStack(ResourceLocation id) {
            var indexes = me.xjqsh.lrtactical.api.LrTacticalAPI.getMeleeIndexes();
            for (var index : indexes) {
                if (index.getId().equals(id)) {
                    return index.createItemStack();
                }
            }
            return ItemStack.EMPTY;
        }

        static ItemStack createThrowableItemStack(ResourceLocation id) {
            var indexes = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndexes();
            for (var index : indexes) {
                if (index.getId().equals(id)) {
                    return index.createItemStack();
                }
            }
            return ItemStack.EMPTY;
        }

        static boolean isMelee(ItemStack stack) {
            return me.xjqsh.lrtactical.api.item.IMeleeWeapon.of(stack) != null;
        }

        static boolean isThrowable(ItemStack stack) {
            return me.xjqsh.lrtactical.api.item.IThrowable.of(stack) != null;
        }

        // ===== 価格計算 =====

        private static int calculateMeleePrice(me.xjqsh.lrtactical.item.index.MeleeWeaponIndex<?> index) {
            try {
                var data = index.getData();
                var attribs = data.getRawAttributes();
                // ダメージはattributes から取得は複雑なので、名前ベースで段階分け
                String name = index.getId().getPath().toLowerCase();
                if (name.contains("katana") || name.contains("axe")) return 2500;
                if (name.contains("karambit") || name.contains("cobra") || name.contains("vanguard")) return 2000;
                if (name.contains("dagger") || name.contains("knife") || name.contains("tac_dagger")) return 1000;
                if (name.contains("bat") || name.contains("wooden")) return 500;
                // 耐久度ベースのフォールバック
                int durability = data.getMaxDurability();
                if (durability <= 0) return 1500; // 無限耐久 = 高級
                return Math.max(500, Math.min(3000, durability * 5));
            } catch (Exception e) {
                return 1000;
            }
        }

        private static int calculateThrowablePrice(me.xjqsh.lrtactical.item.index.ThrowableIndex<?, ?> index) {
            try {
                var data = index.getData();
                int stackSize = data.getStackSize();
                String name = index.getId().getPath().toLowerCase();
                // タイプ別の価格設定
                if (name.contains("m67") || name.contains("frag")) return 400;
                if (name.contains("rgn")) return 350;
                if (name.contains("molotov")) return 300;
                if (name.contains("flash")) return 250;
                if (name.contains("smoke")) return 200;
                // フォールバック: スタック数が少ないほど高価
                return Math.max(150, 500 - stackSize * 50);
            } catch (Exception e) {
                return 300;
            }
        }

        private static String extractDisplayName(String descriptionId, ResourceLocation id) {
            // "item.lrtactical.dagger" → "DAGGER"
            if (descriptionId != null && descriptionId.contains(".")) {
                String[] parts = descriptionId.split("\\.");
                return parts[parts.length - 1].replace("_", " ").toUpperCase();
            }
            return ShopCatalog.extractName(id.toString()).toUpperCase();
        }
    }
}
