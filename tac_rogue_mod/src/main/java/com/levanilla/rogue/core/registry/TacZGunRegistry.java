package com.levanilla.rogue.core.registry;

import com.levanilla.rogue.core.GameConstants;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import it.unimi.dsi.fastutil.Pair;
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

    public record GunSoundProfile(boolean suppressed, double alertRadius) {}

    // ========== キャッシュ ==========
    private static List<GunProfile> cachedGuns = null;
    private static Map<String, GunProfile> cachedById = null;
    private static List<String> cachedGunIds = null;
    private static List<String> cachedAmmoIds = null;
    private static List<String> cachedAttachmentIds = null;
    private static Set<String> cachedBuiltInAttachmentIds = null;
    private static final Set<String> INTERNAL_ATTACHMENT_DENYLIST = Set.of(
        "tacz:scope_aug_default",
        "tacz:sight_p90"
    );

    /** キャッシュクリア（/tacz reload 後に呼ぶ） */
    public static void clearCache() {
        cachedGuns = null;
        cachedById = null;
        cachedGunIds = null;
        cachedAmmoIds = null;
        cachedAttachmentIds = null;
        cachedBuiltInAttachmentIds = null;
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
                if (isStandaloneAttachmentId(entry.getKey())) {
                    cachedAttachmentIds.add(entry.getKey().toString());
                }
            }
        } catch (Exception ignored) {}
        return cachedAttachmentIds;
    }

    /**
     * ショップ/チェストに単体で出してよいアタッチメントだけを通す。
     * TacZの一部銃は内蔵スコープを通常アタッチメントindexにも持つが、
     * それを単体アイテム化すると表示用モデルだけが出て紫黒テクスチャになりやすい。
     */
    public static boolean isStandaloneAttachmentId(String attachmentId) {
        if (attachmentId == null || attachmentId.isBlank()) return false;
        ResourceLocation id = ResourceLocation.tryParse(attachmentId);
        return id != null && isStandaloneAttachmentId(id);
    }

    private static boolean isStandaloneAttachmentId(ResourceLocation attachmentId) {
        if (attachmentId == null) return false;
        String fullId = attachmentId.toString().toLowerCase(Locale.ROOT);
        if (getBuiltInAttachmentIds().contains(fullId)) return false;
        return !INTERNAL_ATTACHMENT_DENYLIST.contains(fullId);
    }

    /**
     * TacZ gunpack横断で銃内蔵アタッチメントを集める。
     * 追加ガンパックのG36/Sassy系でも、GunDataのbuiltin_attachmentsに登録されていれば
     * ショップ/チェストの単体抽選から自動除外される。
     */
    private static Set<String> getBuiltInAttachmentIds() {
        if (cachedBuiltInAttachmentIds != null) return cachedBuiltInAttachmentIds;
        Set<String> ids = new LinkedHashSet<>();
        try {
            for (var entry : TimelessAPI.getAllCommonGunIndex()) {
                GunData data = entry.getValue().getGunData();
                if (data == null || data.getBuiltInAttachments() == null) continue;
                for (ResourceLocation id : data.getBuiltInAttachments().values()) {
                    if (id != null) ids.add(id.toString().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {}
        cachedBuiltInAttachmentIds = ids;
        return cachedBuiltInAttachmentIds;
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
        return getGunSoundProfile(gunStack).suppressed();
    }

    /**
     * TacZの消音modifierを評価し、自modの敵アラート用発砲音距離へ変換する。
     * TacZ側の標準距離はpack/configで変わり得るため、ゲームバランス用に14〜40へ丸める。
     */
    public static GunSoundProfile getGunSoundProfile(ItemStack gunStack) {
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return unsuppressedSoundProfile();
        ItemStack muzzle = igun.getAttachment(gunStack, AttachmentType.MUZZLE);
        if (muzzle == null || muzzle.isEmpty()) return unsuppressedSoundProfile();

        IAttachment att = IAttachment.getIAttachmentOrNull(muzzle);
        if (att == null) return unsuppressedSoundProfile();
        ResourceLocation attId = att.getAttachmentId(muzzle);
        boolean nameLooksSuppressed = isSuppressorName(attId);
        boolean hasSilenceModifier = false;

        // === Check 1: TacZ API — SilenceModifier が登録されているか ===
        try {
            if (attId != null) {
                var indexOpt = TimelessAPI.getCommonAttachmentIndex(attId);
                if (indexOpt.isPresent()) {
                    var data = indexOpt.get().getData();
                    if (data != null) {
                        var modifiers = data.getModifier();
                        if (modifiers != null && modifiers.containsKey(
                            SilenceModifier.ID)) {
                            hasSilenceModifier = true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // TacZ API 未ロード時はフォールバック
        }

        if (!hasSilenceModifier && !nameLooksSuppressed) {
            return unsuppressedSoundProfile();
        }

        if (hasSilenceModifier) {
            try {
                ResourceLocation gunId = igun.getGunId(gunStack);
                var gunIndex = TimelessAPI.getCommonGunIndex(gunId);
                if (gunIndex.isPresent() && gunIndex.get().getGunData() != null) {
                    AttachmentCacheProperty cache = new AttachmentCacheProperty();
                    cache.eval(gunStack, gunIndex.get().getGunData());
                    Pair<Integer, Boolean> silence = cache.getCache(SilenceModifier.ID);
                    if (silence != null && silence.left() != null) {
                        double rawRadius = silence.left();
                        boolean usesSilenceSound = Boolean.TRUE.equals(silence.right());
                        double alertRadius = convertTacZSoundDistanceToAlertRadius(rawRadius, usesSilenceSound);
                        boolean actuallySuppressed = usesSilenceSound
                            || rawRadius < tacZDefaultFireSoundDistance()
                            || nameLooksSuppressed;
                        return actuallySuppressed
                            ? new GunSoundProfile(true, alertRadius)
                            : unsuppressedSoundProfile();
                    }
                }
            } catch (Exception ignored) {
                // TacZ API/pack差異で評価できない場合は下の互換フォールバックへ落とす。
            }
        }

        return new GunSoundProfile(true, GameConstants.SUPPRESSED_ALERT_RADIUS);
    }

    /**
     * アタッチメント単体の静音性能を敵アラート用距離へ変換する。
     * 銃に装着済みの評価は {@link #getGunSoundProfile(ItemStack)} を使う。
     */
    public static Optional<GunSoundProfile> getAttachmentSoundProfile(String attachmentId) {
        if (attachmentId == null || attachmentId.isBlank()) return Optional.empty();
        try {
            return getAttachmentSoundProfile(new ResourceLocation(attachmentId));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * アタッチメント単体の静音性能を敵アラート用距離へ変換する。
     * TacZ の SilenceModifier が取れる場合はその距離を優先し、無い場合は名前フォールバックだけ使う。
     */
    public static Optional<GunSoundProfile> getAttachmentSoundProfile(ResourceLocation attachmentId) {
        if (attachmentId == null) return Optional.empty();
        boolean nameLooksSuppressed = isSuppressorName(attachmentId);
        try {
            var index = TimelessAPI.getCommonAttachmentIndex(attachmentId).orElse(null);
            if (index != null && index.getData() != null && index.getData().getModifier() != null) {
                var property = index.getData().getModifier().get(SilenceModifier.ID);
                if (property != null && property.getValue() instanceof Pair<?, ?> pair) {
                    Object distanceObj = pair.left();
                    Object silenceSoundObj = pair.right();
                    if (distanceObj instanceof Modifier distance) {
                        double rawRadius = AttachmentPropertyManager.eval(distance,
                            GunConfig.DEFAULT_GUN_FIRE_SOUND_DISTANCE.get());
                        boolean usesSilenceSound = Boolean.TRUE.equals(silenceSoundObj);
                        double alertRadius = convertTacZSoundDistanceToAlertRadius(rawRadius, usesSilenceSound);
                        boolean actuallySuppressed = usesSilenceSound
                            || rawRadius < tacZDefaultFireSoundDistance()
                            || nameLooksSuppressed;
                        if (actuallySuppressed) {
                            return Optional.of(new GunSoundProfile(true, alertRadius));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // TacZ API/pack差異で評価できない場合は名前フォールバックへ落とす。
        }

        return nameLooksSuppressed
            ? Optional.of(new GunSoundProfile(true, GameConstants.SUPPRESSED_ALERT_RADIUS))
            : Optional.empty();
    }

    /**
     * 通常銃声距離からどれだけ検知距離を短縮するかを表示用に返す。
     * 実際のAI判定は {@link GunSoundProfile#alertRadius()} を使う。
     */
    public static double getSoundReductionBlocks(GunSoundProfile profile) {
        if (profile == null || !profile.suppressed()) return 0.0D;
        return Math.max(0.0D, GameConstants.GUNSHOT_ALERT_RADIUS - profile.alertRadius());
    }

    private static GunSoundProfile unsuppressedSoundProfile() {
        return new GunSoundProfile(false, GameConstants.GUNSHOT_ALERT_RADIUS);
    }

    private static boolean isSuppressorName(ResourceLocation attId) {
        if (attId == null) return false;
        String name = attId.getPath().toLowerCase(Locale.ROOT);
        return name.contains("silencer") || name.contains("suppressor");
    }

    private static double clampGunshotRadius(double radius) {
        if (!Double.isFinite(radius)) return GameConstants.SUPPRESSED_ALERT_RADIUS;
        return Math.max(
            GameConstants.SUPPRESSED_ALERT_RADIUS,
            Math.min(GameConstants.GUNSHOT_ALERT_RADIUS, radius));
    }

    private static double convertTacZSoundDistanceToAlertRadius(double tacZSoundDistance, boolean usesSilenceSound) {
        if (!Double.isFinite(tacZSoundDistance)) return GameConstants.SUPPRESSED_ALERT_RADIUS;
        double reduction = Math.max(0.0D, tacZDefaultFireSoundDistance() - tacZSoundDistance);
        if (reduction <= 0.0D && usesSilenceSound) return GameConstants.SUPPRESSED_ALERT_RADIUS;
        return clampGunshotRadius(GameConstants.GUNSHOT_ALERT_RADIUS - reduction);
    }

    private static double tacZDefaultFireSoundDistance() {
        try {
            return Math.max(1.0D, GunConfig.DEFAULT_GUN_FIRE_SOUND_DISTANCE.get());
        } catch (Exception ignored) {
            return 64.0D;
        }
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
     * フロアではなく DPS / 単発火力 / マガジン / カテゴリで制限する。
     */
    public static int calculatePrice(float damage, int rpm, int magSize, ShopCatalog.Category category) {
        float dps = damage * rpm / 60.0f;
        double base = 350.0D
            + dps * 85.0D
            + damage * 120.0D
            + Math.min(magSize, 120) * 10.0D;
        double categoryMultiplier = switch (category) {
            case PISTOL -> 0.75D;
            case SMG -> 0.90D;
            case RIFLE -> 1.00D;
            case SHOTGUN -> 1.05D;
            case SNIPER -> 1.15D;
            case LMG -> 1.20D;
            case EXPLOSIVE -> 2.00D;
            default -> 1.00D;
        };
        int rounded = (int) (Math.round((base * categoryMultiplier) / 50.0D) * 50);
        return Math.max(400, Math.min(60000, rounded));
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
            else allowedAtt = allowedAtt.stream()
                .filter(Objects::nonNull)
                .toList();

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
