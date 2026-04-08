package com.levanilla.rogue.core.registry;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TacZ 公式 API ({@link TimelessAPI}) を使った正規アクセスレイヤー。
 * リフレクション/ハードコードを排除し、銃/弾薬/アタッチメントの全データを
 * 公式 API 経由で取得する。
 *
 * <p>主な機能:
 * <ul>
 *   <li>全登録銃の GunProfile 生成（ダメージ/RPM/マガジン/弾種/許可スロット/自動カテゴリ/自動価格）</li>
 *   <li>弾薬マッピングの自動取得（AmmoDatabase のハードコード置き換え）</li>
 *   <li>アタッチメント互換性の API 取得（AttachmentDatabase のハードコード置き換え）</li>
 * </ul>
 */
public final class TacZGunRegistry {

    private TacZGunRegistry() {}

    // ========== キャッシュ ==========
    private static List<GunProfile> cachedGuns = null;
    private static Map<String, GunProfile> cachedById = null;
    private static List<String> cachedGunIds = null;
    private static List<String> cachedAmmoIds = null;
    private static List<String> cachedAttachmentIds = null;

    /** キャッシュクリア（/tacz reload 後に呼ぶ） */
    public static void clearCache() {
        cachedGuns = null;
        cachedById = null;
        cachedGunIds = null;
        cachedAmmoIds = null;
        cachedAttachmentIds = null;
    }

    // ========== GunProfile ==========

    /**
     * 銃の全パラメータを保持するデータクラス。
     * TacZ の GunData + BulletData + CommonGunIndex から自動生成。
     */
    public static class GunProfile {
        public final ResourceLocation id;
        public final String displayName;
        public final ShopCatalog.Category category;
        public final float damage;
        public final int rpm;
        public final int magSize;
        public final int[] extMagSizes;
        public final ResourceLocation ammoId;
        public final List<AttachmentType> allowedAttachments;
        public final String gunType;
        public final int autoPrice;
        public final float dps;

        public GunProfile(ResourceLocation id, String displayName,
                          ShopCatalog.Category category, float damage, int rpm,
                          int magSize, int[] extMagSizes, ResourceLocation ammoId,
                          List<AttachmentType> allowedAttachments,
                          String gunType, int autoPrice, float dps) {
            this.id = id;
            this.displayName = displayName;
            this.category = category;
            this.damage = damage;
            this.rpm = rpm;
            this.magSize = magSize;
            this.extMagSizes = extMagSizes;
            this.ammoId = ammoId;
            this.allowedAttachments = allowedAttachments;
            this.gunType = gunType;
            this.autoPrice = autoPrice;
            this.dps = dps;
        }
    }

    /** 全登録銃の GunProfile をキャッシュ付きで取得 */
    public static List<GunProfile> getAllGuns() {
        if (cachedGuns != null) return cachedGuns;
        List<GunProfile> profiles = new ArrayList<>();
        try {
            for (var entry : TimelessAPI.getAllCommonGunIndex()) {
                ResourceLocation gunId = entry.getKey();
                CommonGunIndex index = entry.getValue();
                GunProfile profile = buildProfile(gunId, index);
                if (profile != null) profiles.add(profile);
            }
        } catch (Exception e) {
            // TacZ 未ロード時はフォールバック
        }
        cachedGuns = profiles;
        // ID → Profile マップも構築
        cachedById = new ConcurrentHashMap<>();
        for (GunProfile p : profiles) {
            cachedById.put(p.id.toString(), p);
        }
        return profiles;
    }

    /** ID から GunProfile を取得 */
    public static GunProfile getProfile(String gunId) {
        if (cachedById == null) getAllGuns();
        if (cachedById == null) return null;
        GunProfile p = cachedById.get(gunId);
        if (p != null) return p;
        // tacz: プレフィックスなしで再試行
        if (!gunId.contains(":")) {
            return cachedById.get("tacz:" + gunId);
        }
        return null;
    }

    /** ID から GunProfile を取得 (ResourceLocation) */
    public static GunProfile getProfile(ResourceLocation gunId) {
        return getProfile(gunId.toString());
    }

    // ========== ID リスト取得 (レガシー互換) ==========

    /** 全銃 ID リスト */
    public static List<String> getAllGunIds() {
        if (cachedGunIds != null) return cachedGunIds;
        cachedGunIds = new ArrayList<>();
        try {
            for (var entry : TimelessAPI.getAllCommonGunIndex()) {
                cachedGunIds.add(entry.getKey().toString());
            }
        } catch (Exception ignored) {}
        return cachedGunIds;
    }

    /** 全弾薬 ID リスト */
    public static List<String> getAllAmmoIds() {
        if (cachedAmmoIds != null) return cachedAmmoIds;
        cachedAmmoIds = new ArrayList<>();
        try {
            for (var entry : TimelessAPI.getAllCommonAmmoIndex()) {
                cachedAmmoIds.add(entry.getKey().toString());
            }
        } catch (Exception ignored) {}
        return cachedAmmoIds;
    }

    /** 全アタッチメント ID リスト */
    public static List<String> getAllAttachmentIds() {
        if (cachedAttachmentIds != null) return cachedAttachmentIds;
        cachedAttachmentIds = new ArrayList<>();
        try {
            for (var entry : TimelessAPI.getAllCommonAttachmentIndex()) {
                cachedAttachmentIds.add(entry.getKey().toString());
            }
        } catch (Exception ignored) {}
        return cachedAttachmentIds;
    }

    // ========== 弾薬マッピング (AmmoDatabase 置き換え) ==========

    /** 銃 ID → 弾薬 ID (API ベース) */
    public static String getAmmoForGun(String gunId) {
        GunProfile p = getProfile(gunId);
        if (p != null && p.ammoId != null) return p.ammoId.toString();
        // レガシーフォールバック
        return AmmoDatabase.getAmmoForGun(gunId);
    }

    /** 銃 ID → マガジンサイズ (API ベース) */
    public static int getMagazineSize(String gunId) {
        GunProfile p = getProfile(gunId);
        if (p != null) return p.magSize;
        return AmmoDatabase.getMagazineSize(gunId);
    }

    // ========== アタッチメント互換性 (AttachmentDatabase 置き換え) ==========

    /** 銃が特定のアタッチメントタイプを許可しているか (API ベース) */
    public static boolean isAttachmentAllowed(String gunId, AttachmentType type) {
        GunProfile p = getProfile(gunId);
        if (p != null) return p.allowedAttachments.contains(type);
        return false;
    }

    /** 銃の許可アタッチメントタイプ一覧 (API ベース) */
    public static List<AttachmentType> getAllowedAttachments(String gunId) {
        GunProfile p = getProfile(gunId);
        if (p != null) return p.allowedAttachments;
        return Collections.emptyList();
    }

    /** ItemStack から銃かどうかを判定 */
    public static boolean isGun(ItemStack stack) {
        return IGun.getIGunOrNull(stack) != null;
    }

    /** ItemStack から銃の ID を取得 */
    public static ResourceLocation getGunId(ItemStack stack) {
        IGun igun = IGun.getIGunOrNull(stack);
        return igun != null ? igun.getGunId(stack) : null;
    }

    /** ItemStack から弾薬かどうかを判定 */
    public static boolean isAmmo(ItemStack stack) {
        return IAmmo.getIAmmoOrNull(stack) != null;
    }

    /** ItemStack からアタッチメントかどうかを判定 */
    public static boolean isAttachment(ItemStack stack) {
        return IAttachment.getIAttachmentOrNull(stack) != null;
    }

    /** プレイヤーが銃を持っているか */
    public static boolean isHoldingGun(net.minecraft.world.entity.LivingEntity entity) {
        return IGun.mainHandHoldGun(entity);
    }

    /**
     * 銃にサプレッサーが装着されているか。
     * 
     * <p>検知方法 (優先順):
     * <ol>
     *   <li>TacZ API: {@code AttachmentData.getModifier()} に {@code SilenceModifier.ID} キーが存在するか</li>
     *   <li>フォールバック: アタッチメント ID の名前パターンマッチ</li>
     * </ol>
     */
    public static boolean hasSuppressor(ItemStack gunStack) {
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return false;
        ItemStack muzzle = igun.getAttachment(gunStack, AttachmentType.MUZZLE);
        if (muzzle == null || muzzle.isEmpty()) return false;

        IAttachment att = IAttachment.getIAttachmentOrNull(muzzle);
        if (att == null) return false;
        ResourceLocation attId = att.getAttachmentId(muzzle);

        // === Check 1: TacZ API — SilenceModifier が登録されているか ===
        try {
            if (attId != null) {
                var indexOpt = TimelessAPI.getCommonAttachmentIndex(attId);
                if (indexOpt.isPresent()) {
                    var data = indexOpt.get().getData();
                    if (data != null) {
                        var modifiers = data.getModifier();
                        if (modifiers != null && modifiers.containsKey(
                            com.tacz.guns.resource.modifier.custom.SilenceModifier.ID)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // TacZ API 未ロード時はフォールバック
        }

        // === Check 2: フォールバック — アタッチメント ID の名前パターン ===
        if (attId != null) {
            String name = attId.getPath().toLowerCase();
            return name.contains("silencer") || name.contains("suppressor");
        }
        return false;
    }

    /** 銃にスコープが装着されているか (API ベース) */
    public static boolean hasScope(ItemStack gunStack) {
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return false;
        ItemStack scope = igun.getAttachment(gunStack, AttachmentType.SCOPE);
        return scope != null && !scope.isEmpty();
    }

    // ========== 価格計算 ==========

    /**
     * GunData のパラメータから自動価格を算出。
     * DPS + マガジン容量 をベースに $200-$18,000 の範囲に正規化。
     */
    public static int calculatePrice(float damage, int rpm, int magSize, ShopCatalog.Category category) {
        float dps = damage * rpm / 60.0f;
        int base;
        switch (category) {
            case PISTOL:  base = (int)(dps * 40) + magSize * 5 + 200; break;
            case SMG:     base = (int)(dps * 35) + magSize * 8 + 500; break;
            case SHOTGUN: base = (int)(dps * 30) + magSize * 15 + 400; break;
            case RIFLE:   base = (int)(dps * 45) + magSize * 10 + 800; break;
            case SNIPER:  base = (int)(dps * 60) + magSize * 20 + 1500; break;
            case LMG:     base = (int)(dps * 50) + magSize * 5 + 1500; break;
            case EXPLOSIVE: base = (int)(damage * 120) + 8000; break; // $16000〜$32000 を目指した極めて高額な設定
            case SPECIAL: base = (int)(dps * 80) + 2000; break;
            default:      base = (int)(dps * 40) + 500; break;
        }
        int clamped = Math.max(200, Math.min(40000, base));
        // 値段を分かりやすく50単位で丸める
        return (clamped / 50) * 50;
    }

    // ========== カテゴリ自動分類 ==========

    /** CommonGunIndex.getType() からショップカテゴリを自動分類 */
    public static ShopCatalog.Category categorizeFromType(String type) {
        if (type == null) return ShopCatalog.Category.RIFLE;
        String lower = type.toLowerCase();
        if (lower.contains("pistol") || lower.contains("handgun")) return ShopCatalog.Category.PISTOL;
        if (lower.contains("smg") || lower.contains("submachine")) return ShopCatalog.Category.SMG;
        if (lower.contains("shotgun")) return ShopCatalog.Category.SHOTGUN;
        if (lower.contains("sniper") || lower.contains("bolt") || lower.contains("marksman") || lower.contains("dmr"))
            return ShopCatalog.Category.SNIPER;
        if (lower.contains("lmg") || lower.contains("machine_gun") || lower.contains("mg"))
            return ShopCatalog.Category.LMG;
        if (lower.contains("rpg") || lower.contains("launcher") || lower.contains("m320"))
            return ShopCatalog.Category.EXPLOSIVE;
        if (lower.contains("rifle") || lower.contains("ar") || lower.contains("carbine"))
            return ShopCatalog.Category.RIFLE;
        // フォールバック: 名前ベース推測
        return ShopCatalog.Category.RIFLE;
    }

    // ========== 内部: GunProfile 構築 ==========

    private static GunProfile buildProfile(ResourceLocation gunId, CommonGunIndex index) {
        try {
            GunData gunData = index.getGunData();
            BulletData bulletData = index.getBulletData();
            if (gunData == null || bulletData == null) return null;

            float damage = bulletData.getDamageAmount();
            int rpm = gunData.getRoundsPerMinute();
            int magSize = gunData.getAmmoAmount();
            int[] extMagSizes = gunData.getExtendedMagAmmoAmount();
            ResourceLocation ammoId = gunData.getAmmoId();
            List<AttachmentType> allowedAtt = gunData.getAllowAttachments();
            if (allowedAtt == null) allowedAtt = Collections.emptyList();

            String gunType = index.getType();
            ShopCatalog.Category category = categorizeFromType(gunType);
            // フォールバック: type が汎用的すぎる場合は名前ベース推測
            if (category == ShopCatalog.Category.RIFLE) {
                ShopCatalog.Category guessed = ShopCatalog.guessCategory(gunId.getPath());
                if (guessed != ShopCatalog.Category.RIFLE) {
                    category = guessed;
                }
            }

            float dps = damage * rpm / 60.0f;
            int autoPrice = calculatePrice(damage, rpm, magSize, category);

            String namePath = gunId.getPath();
            if (namePath.contains("/")) {
                namePath = namePath.substring(namePath.lastIndexOf('/') + 1);
            }
            String displayName = namePath.replace("_", " ").toUpperCase();

            return new GunProfile(gunId, displayName, category, damage, rpm,
                magSize, extMagSizes, ammoId, allowedAtt, gunType, autoPrice, dps);
        } catch (Exception e) {
            return null;
        }
    }
}
