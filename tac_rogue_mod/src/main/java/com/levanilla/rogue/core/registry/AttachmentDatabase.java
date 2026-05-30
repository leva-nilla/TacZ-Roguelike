package com.levanilla.rogue.core.registry;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.*;

/**
 * TacZ 銃 ↔ アタッチメント互換性データベース v3。
 * TacZ JAR/ガンパック JAR 内の allow_attachments/*.json と
 * tacz_tags/attachments/*.json をランタイムで読み込み、
 * 銃→許可アタッチメントIDマッピングを構築する。
 */
public final class AttachmentDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentDatabase.class);
    private static final Gson GSON = new Gson();

    private AttachmentDatabase() {}

    // 銃ID → 許可アタッチメントID集合 (例: "tacz:ak47" -> {"tacz:muzzle_brake_cyclone_d2", ...})
    private static final Map<String, Set<String>> GUN_ALLOWED_ATTACHMENTS = new HashMap<>();

    // タグ名 → アタッチメントID集合 (例: "scope" -> {"tacz:scope_elcan_4x", ...})
    private static final Map<String, Set<String>> TAG_TO_ATTACHMENTS = new HashMap<>();

    // アタッチメントID → スロットタイプ (逆引き用)
    private static final Map<String, String> ATTACHMENT_SLOT_TYPE = new HashMap<>();

    // (gunId, attachId) → compatibility
    private static final ConcurrentMap<CompatibilityKey, Boolean> COMPATIBILITY_CACHE = new ConcurrentHashMap<>();

    private static boolean loaded = false;
    private static int compatibilityGeneration = 0;
    private static final Path CACHE_FILE = Paths.get("config", "tac_rogue", "attachment_db_cache.json");

    // ===== 初期化 =====

    /**
     * サーバー/クライアント起動時に呼び出し。
     * tacz/ ディレクトリと Gradle キャッシュ内の TacZ JAR を走査。
     */
    public static synchronized void init() {
        if (loaded) return;
        long started = System.nanoTime();
        loaded = true;
        COMPATIBILITY_CACHE.clear();
        compatibilityGeneration++;

        List<Path> jarPaths = new ArrayList<>();

        // 1. ゲームディレクトリの tacz/ フォルダ
        Path taczDir = Paths.get("tacz");
        if (Files.isDirectory(taczDir)) {
            try (var stream = Files.list(taczDir)) {
                stream.filter(p -> {
                    String s = p.toString().toLowerCase();
                    return s.endsWith(".jar") || s.endsWith(".zip");
                }).forEach(jarPaths::add);
            } catch (IOException e) {
                LOGGER.warn("[AttachmentDB] Failed to list tacz/ directory", e);
            }
        }

        // 2. Forge のモジュールパスからTacZコアJARを検索
        try {
            Enumeration<java.net.URL> urls = AttachmentDatabase.class.getClassLoader().getResources("assets/tacz");
            while (urls.hasMoreElements()) {
                java.net.URL url = urls.nextElement();
                String path = url.getPath();
                if (path.contains("!")) {
                    String jarPath = path.substring(0, path.indexOf("!"));
                    if (jarPath.startsWith("file:")) jarPath = jarPath.substring(5);
                    // Windows fix
                    if (jarPath.startsWith("/") && jarPath.contains(":")) jarPath = jarPath.substring(1);
                    Path jp = Paths.get(jarPath);
                    if (Files.exists(jp) && !jarPaths.contains(jp)) {
                        jarPaths.add(jp);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[AttachmentDB] Failed to find TacZ core JAR via classloader", e);
        }

        if (isVerboseLogging()) {
            LOGGER.info("[AttachmentDB] Scanning {} JAR(s) for attachment data", jarPaths.size());
        } else {
            LOGGER.debug("[AttachmentDB] Scanning {} JAR(s) for attachment data", jarPaths.size());
        }

        if (loadCache(jarPaths)) {
            LOGGER.debug("[AttachmentDB] Loaded cached {} gun definitions, {} tag groups, {} attachment slot mappings in {} ms",
                GUN_ALLOWED_ATTACHMENTS.size(), TAG_TO_ATTACHMENTS.size(), ATTACHMENT_SLOT_TYPE.size(), elapsedMs(started));
            return;
        }

        // Phase 1: タグ定義を読み込み (tacz_tags/attachments/*.json excluding allow_attachments)
        for (Path jar : jarPaths) {
            loadTagDefinitions(jar);
        }

        // Phase 2: 銃の allow_attachments を読み込み、タグを展開
        for (Path jar : jarPaths) {
            loadAllowAttachments(jar);
        }

        // Phase 3: 個別アタッチメント定義 (attachments/*.json) の読み込みを行い、正確な slot type (type, slot) を取得
        for (Path jar : jarPaths) {
            loadAttachmentDefinitions(jar);
        }

        // Phase 4: スロットタイプ逆引きの補完 (ハードコードタグ、名前推測など)
        buildSlotTypeMap();

        LOGGER.debug("[AttachmentDB] Loaded {} gun definitions, {} tag groups, {} attachment slot mappings",
            GUN_ALLOWED_ATTACHMENTS.size(), TAG_TO_ATTACHMENTS.size(), ATTACHMENT_SLOT_TYPE.size());
        saveCache(jarPaths);
        LOGGER.debug("[AttachmentDB] Load completed in {} ms", elapsedMs(started));
    }

    private static void loadTagDefinitions(Path jarPath) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                // tacz_tags/attachments/*.json だが allow_attachments/ は除外
                if (!name.endsWith(".json") || !name.contains("tacz_tags/attachments/") || name.contains("allow_attachments")) continue;

                // ネームスペースを推定 (assets/<namespace>/custom/<pack>/data/<ns>/tacz_tags/... or data/<ns>/tacz_tags/...)
                String tagName = name.substring(0, name.length() - 5); // remove .json
                tagName = tagName.substring(tagName.lastIndexOf('/') + 1); // basename

                try (InputStream is = zip.getInputStream(entry)) {
                    JsonArray arr = GSON.fromJson(new InputStreamReader(is), JsonArray.class);
                    if (arr == null) continue;
                    Set<String> set = TAG_TO_ATTACHMENTS.computeIfAbsent(tagName, k -> new HashSet<>());
                    for (JsonElement el : arr) {
                        String val = el.getAsString();
                        if (val.startsWith("#tacz:")) {
                            // ネストタグ参照 — 後で解決
                            // Phase 1 で全タグを読み込んだ後に解決する
                        } else {
                            set.add(val);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("[AttachmentDB] Failed to parse tag {}: {}", name, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[AttachmentDB] Failed to read JAR: {}", jarPath, e);
        }
    }

    private static void loadAllowAttachments(Path jarPath) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".json") || !name.contains("allow_attachments")) continue;

                String gunName = name.substring(0, name.length() - 5);
                gunName = gunName.substring(gunName.lastIndexOf('/') + 1);

                // ネームスペースを推定
                String namespace = "tacz";
                if (name.contains("/data/")) {
                    String afterData = name.substring(name.indexOf("/data/") + 6);
                    if (afterData.contains("/")) {
                        namespace = afterData.substring(0, afterData.indexOf("/"));
                    }
                }
                String gunId = namespace + ":" + gunName;

                try (InputStream is = zip.getInputStream(entry)) {
                    JsonArray arr = GSON.fromJson(new InputStreamReader(is), JsonArray.class);
                    if (arr == null) continue;
                    Set<String> allowed = GUN_ALLOWED_ATTACHMENTS.computeIfAbsent(gunId, k -> new HashSet<>());

                    for (JsonElement el : arr) {
                        String val = el.getAsString();
                        if (val.startsWith("#tacz:")) {
                            // タグ参照を展開
                            String tagKey = val.substring(6); // remove "#tacz:"
                            Set<String> tagAttachments = TAG_TO_ATTACHMENTS.get(tagKey);
                            if (tagAttachments != null) {
                                allowed.addAll(tagAttachments);
                            }
                        } else if (val.startsWith("#")) {
                            // 他のネームスペースのタグ参照
                            String tagRef = val.substring(1);
                            String[] parts = tagRef.split(":");
                            if (parts.length == 2) {
                                Set<String> tagAttachments = TAG_TO_ATTACHMENTS.get(parts[1]);
                                if (tagAttachments != null) {
                                    allowed.addAll(tagAttachments);
                                }
                            }
                        } else {
                            // 直接アタッチメントID指定
                            allowed.add(val);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("[AttachmentDB] Failed to parse allow_attachments {}: {}", name, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[AttachmentDB] Failed to read JAR: {}", jarPath, e);
        }
    }

    private static void loadAttachmentDefinitions(Path jarPath) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".json") || !name.contains("attachments/") || name.contains("tacz_tags") || name.contains("allow_attachments")) continue;

                String attachName = name.substring(0, name.length() - 5);
                attachName = attachName.substring(attachName.lastIndexOf('/') + 1);

                String namespace = "tacz";
                if (name.contains("/data/")) {
                    String afterData = name.substring(name.indexOf("/data/") + 6);
                    if (afterData.contains("/")) {
                        namespace = afterData.substring(0, afterData.indexOf("/"));
                    }
                }
                String attachId = namespace + ":" + attachName;

                try (InputStream is = zip.getInputStream(entry)) {
                    com.google.gson.JsonObject obj = GSON.fromJson(new InputStreamReader(is), com.google.gson.JsonObject.class);
                    if (obj == null) continue;

                    String type = null;
                    if (obj.has("type")) {
                        type = obj.get("type").getAsString();
                    } else if (obj.has("slot")) { // 一部のAddonは "slot" を使うことがある
                        String slotStr = obj.get("slot").getAsString();
                        // e.g. "daffas_arsenal:attachment/slot/uh" -> "uh"
                        if (slotStr.contains("/")) {
                            type = slotStr.substring(slotStr.lastIndexOf('/') + 1);
                        } else if (slotStr.contains(":")) {
                            type = slotStr.substring(slotStr.lastIndexOf(':') + 1);
                        } else {
                            type = slotStr;
                        }
                    }

                    if (type != null && !type.isEmpty()) {
                        ATTACHMENT_SLOT_TYPE.put(attachId, type);
                    }
                } catch (Exception e) {
                    LOGGER.debug("[AttachmentDB] Failed to parse attachment def {}: {}", name, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[AttachmentDB] Failed to read JAR: {}", jarPath, e);
        }
    }

    private static void buildSlotTypeMap() {
        // タグ名からスロットタイプを推定するマッピング
        Map<String, String> tagToSlot = new HashMap<>();
        // スコープ系
        tagToSlot.put("scope", "scope");
        tagToSlot.put("scope_scope", "scope");
        tagToSlot.put("scope_sight", "scope");
        tagToSlot.put("scope_sniper", "scope");
        tagToSlot.put("scope_lowsight", "scope");
        tagToSlot.put("scope_springfield", "scope");
        tagToSlot.put("pistol_sight", "scope");
        tagToSlot.put("deagle_sight", "scope");
        tagToSlot.put("okp_scope", "scope");
        tagToSlot.put("p90_scope", "scope");
        tagToSlot.put("aug_scope", "scope");
        tagToSlot.put("m16a1_scope", "scope");
        // マズル系
        tagToSlot.put("muzzle", "muzzle");
        tagToSlot.put("muzzle_brake", "muzzle");
        tagToSlot.put("muzzle_compensator", "muzzle");
        tagToSlot.put("muzzle_silencer", "muzzle");
        tagToSlot.put("muzzle_shotgun", "muzzle");
        tagToSlot.put("pistol_muzzle", "muzzle");
        tagToSlot.put("pistol_silencer", "muzzle");
        tagToSlot.put("amr_muzzle_silencer", "muzzle");
        tagToSlot.put("timeless50_exclusive", "muzzle");
        tagToSlot.put("deagle_exclusive", "muzzle");
        tagToSlot.put("bayonet_ak", "muzzle");
        tagToSlot.put("bayonet_ar", "muzzle");
        tagToSlot.put("bayonet_other", "muzzle");
        // グリップ系
        tagToSlot.put("grip", "grip");
        tagToSlot.put("grip_afg", "grip");
        tagToSlot.put("grip_vertical", "grip");
        tagToSlot.put("pistol_grip", "grip");
        // ストック系
        tagToSlot.put("stock", "stock");
        tagToSlot.put("oem_stock", "stock");
        tagToSlot.put("db_short_stock", "stock");
        tagToSlot.put("hk416d_stock", "stock");
        tagToSlot.put("m4a1_stock", "stock");
        tagToSlot.put("sks_tactical_stock", "stock");
        tagToSlot.put("spas_12_stock", "stock");
        // レーザー系
        tagToSlot.put("ar_laser", "laser");
        tagToSlot.put("pistol_laser", "laser");
        // マガジン系
        tagToSlot.put("extended_mag", "extended_mag");
        tagToSlot.put("light_extended_mag", "extended_mag");
        tagToSlot.put("shotgun_extended_mag", "extended_mag");
        tagToSlot.put("sniper_extended_mag", "extended_mag");
        tagToSlot.put("smg_extended_mag", "extended_mag");
        tagToSlot.put("pdw_extended_mag", "extended_mag");
        tagToSlot.put("pistol_extended_mag", "extended_mag");
        tagToSlot.put("ar_extended_mag", "extended_mag");
        tagToSlot.put("rifle_extended_mag", "extended_mag");
        // 弾薬mod
        tagToSlot.put("ammo_mod", "ammo_mod");
        tagToSlot.put("ammo_mod_no_he", "ammo_mod");
        tagToSlot.put("ammo_mod_slug", "ammo_mod");
        tagToSlot.put("ammo_mod_shotgun_exclusive", "ammo_mod");
        tagToSlot.put("slug", "ammo_mod");

        // タグごとのアタッチメントにスロットタイプを付与
        for (Map.Entry<String, Set<String>> tagEntry : TAG_TO_ATTACHMENTS.entrySet()) {
            String slotType = tagToSlot.get(tagEntry.getKey());
            if (slotType == null) continue;
            for (String attachId : tagEntry.getValue()) {
                ATTACHMENT_SLOT_TYPE.putIfAbsent(attachId, slotType);
            }
        }
    }

    // ===== Public API =====

    /** 銃とアタッチメントの互換性を判定 (ダイレクトマッチ) */
    public static boolean isCompatible(String gunId, String attachId) {
        ensureLoaded();
        CompatibilityKey key = new CompatibilityKey(gunId, attachId, compatibilityGeneration);
        Boolean cached = COMPATIBILITY_CACHE.get(key);
        if (cached != null) return cached;

        boolean result = computeCompatible(gunId, attachId);
        COMPATIBILITY_CACHE.put(key, result);
        return result;
    }

    private static boolean computeCompatible(String gunId, String attachId) {
        String slotTypeStr = getSlotType(attachId);
        if (slotTypeStr == null) slotTypeStr = guessSlotType(attachId);

        // ammo_mod は通常のアタッチメント（マガジンスロット等）として機能するため、
        // 独自判定は行わず、下記の TacZ ネイティブ判定に完全に委ねる。


        // TacZ ネイティブの完璧な互換性判定 (1.20.1)
        // AllowAttachmentTagMatcher は銃の allow_attachments ツリー (タグやID) を再帰的にすべて走査し、
        // 対象のアタッチメントが本当にその銃へ装着可能か (ショットガンマガジン等でないかも含み) 正確に判定する。
        try {
            net.minecraft.resources.ResourceLocation gLoc = new net.minecraft.resources.ResourceLocation(gunId);
            net.minecraft.resources.ResourceLocation aLoc = new net.minecraft.resources.ResourceLocation(attachId);
            return com.tacz.guns.util.AllowAttachmentTagMatcher.match(gLoc, aLoc);
        } catch (Exception e) {
            // TacZ のバージョン違いなどで例外が出た場合は、従来のフォールバック処理を行う
        }

        Set<String> explicitAllowed = GUN_ALLOWED_ATTACHMENTS.get(gunId);

        // 1. レガシーな明示的タグリストに含まれていれば無条件で許可
        if (explicitAllowed != null && explicitAllowed.contains(attachId)) {
            return true;
        }

        // 2. 最終フォールバック：TacZ ネイティブスロット検証
        boolean hasNativeSlot = false;
        if (slotTypeStr != null) {
            try {
                com.tacz.guns.api.item.attachment.AttachmentType typeEnv =
                    com.tacz.guns.api.item.attachment.AttachmentType.valueOf(slotTypeStr.toUpperCase());
                hasNativeSlot = TacZGunRegistry.isAttachmentAllowed(gunId, typeEnv);
            } catch (IllegalArgumentException e) {
                // TacZ 1.1.8 の拡張マガジンスロット名は EXTENDED_MAG。
                try {
                    if (slotTypeStr.equalsIgnoreCase("extended_mag")) {
                        com.tacz.guns.api.item.attachment.AttachmentType typeEnv =
                            com.tacz.guns.api.item.attachment.AttachmentType.EXTENDED_MAG;
                        hasNativeSlot = TacZGunRegistry.isAttachmentAllowed(gunId, typeEnv);
                    }
                } catch (Exception ex) {}
            }
        }

        return hasNativeSlot;
    }

    /** リソースリロード時にランタイム上の構造DBと互換性結果を破棄する。 */
    public static synchronized void clearRuntimeCache() {
        loaded = false;
        GUN_ALLOWED_ATTACHMENTS.clear();
        TAG_TO_ATTACHMENTS.clear();
        ATTACHMENT_SLOT_TYPE.clear();
        COMPATIBILITY_CACHE.clear();
        compatibilityGeneration++;
    }

    /** 銃の許可アタッチメントID全セットを取得 */
    public static Set<String> getAllowedAttachments(String gunId) {
        ensureLoaded();
        Set<String> s = GUN_ALLOWED_ATTACHMENTS.get(gunId);
        return s != null ? Collections.unmodifiableSet(s) : Collections.emptySet();
    }

    /** アタッチメントのスロットタイプを取得 */
    public static String getSlotType(String attachId) {
        ensureLoaded();
        return ATTACHMENT_SLOT_TYPE.get(attachId);
    }

    /** 名前ベースフォールバック */
    public static String guessSlotType(String attachId) {
        ensureLoaded();
        String fromDb = getSlotType(attachId);
        if (fromDb != null) return fromDb;
        String lower = attachId.toLowerCase();
        if (lower.contains("sight") || lower.contains("scope")) return "scope";
        if (lower.contains("silencer") || lower.contains("suppressor") ||
            lower.contains("compensator") || lower.contains("brake") ||
            lower.contains("muzzle") || lower.contains("bayonet") || lower.contains("choke")) return "muzzle";
        if (lower.contains("grip")) return "grip";
        if (lower.contains("stock")) return "stock";
        if (lower.contains("laser")) return "laser";
        if (lower.contains("ammo_mod")) return "ammo_mod";
        if (lower.contains("extended_mag")) return "extended_mag";
        return null;
    }

    /** 旧APIとの互換性: isSlotAllowed */
    public static boolean isSlotAllowed(String gunId, String slotType) {
        ensureLoaded();
        Set<String> allowed = GUN_ALLOWED_ATTACHMENTS.get(gunId);
        if (allowed == null) return false;
        for (String attachId : allowed) {
            String type = getSlotType(attachId);
            if (type == null) type = guessSlotType(attachId);
            if (slotType.equals(type)) return true;
        }
        return false;
    }

    /** 旧APIとの互換性: getAllowedSlots */
    public static Set<String> getAllowedSlots(String gunId) {
        ensureLoaded();
        Set<String> allowed = GUN_ALLOWED_ATTACHMENTS.get(gunId);
        if (allowed == null) return Collections.emptySet();
        Set<String> slots = new HashSet<>();
        for (String attachId : allowed) {
            String type = getSlotType(attachId);
            if (type == null) type = guessSlotType(attachId);
            if (type != null) slots.add(type);
        }
        return slots;
    }

    /** DB のロード状態を返す */
    public static boolean isLoaded() { return loaded; }

    /** 登録銃数を返す */
    public static int getGunCount() {
        ensureLoaded();
        return GUN_ALLOWED_ATTACHMENTS.size();
    }

    private static void ensureLoaded() {
        if (!loaded) {
            init();
        }
    }

    private static long elapsedMs(long startedNs) {
        return (System.nanoTime() - startedNs) / 1_000_000L;
    }

    private static boolean isVerboseLogging() {
        return com.levanilla.rogue.core.RogueConfig.debugLogs();
    }

    private static boolean loadCache(List<Path> jarPaths) {
        if (!Files.isRegularFile(CACHE_FILE)) {
            return false;
        }
        try (Reader reader = Files.newBufferedReader(CACHE_FILE)) {
            AttachmentDbCache cache = GSON.fromJson(reader, AttachmentDbCache.class);
            if (cache == null || !sourceSignatures(jarPaths).equals(cache.sources)) {
                return false;
            }
            GUN_ALLOWED_ATTACHMENTS.clear();
            TAG_TO_ATTACHMENTS.clear();
            ATTACHMENT_SLOT_TYPE.clear();
            COMPATIBILITY_CACHE.clear();
            if (cache.gunAllowedAttachments != null) GUN_ALLOWED_ATTACHMENTS.putAll(cache.gunAllowedAttachments);
            if (cache.tagToAttachments != null) TAG_TO_ATTACHMENTS.putAll(cache.tagToAttachments);
            if (cache.attachmentSlotType != null) ATTACHMENT_SLOT_TYPE.putAll(cache.attachmentSlotType);
            return true;
        } catch (Exception e) {
            LOGGER.debug("[AttachmentDB] Failed to load cache: {}", e.toString());
            return false;
        }
    }

    private static void saveCache(List<Path> jarPaths) {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            AttachmentDbCache cache = new AttachmentDbCache();
            cache.sources = sourceSignatures(jarPaths);
            cache.gunAllowedAttachments = new HashMap<>(GUN_ALLOWED_ATTACHMENTS);
            cache.tagToAttachments = new HashMap<>(TAG_TO_ATTACHMENTS);
            cache.attachmentSlotType = new HashMap<>(ATTACHMENT_SLOT_TYPE);
            try (Writer writer = Files.newBufferedWriter(CACHE_FILE)) {
                GSON.toJson(cache, writer);
            }
        } catch (Exception e) {
            LOGGER.debug("[AttachmentDB] Failed to save cache: {}", e.toString());
        }
    }

    private static List<SourceSignature> sourceSignatures(List<Path> paths) {
        List<SourceSignature> signatures = new ArrayList<>();
        for (Path path : paths) {
            try {
                Path normalized = path.toAbsolutePath().normalize();
                SourceSignature signature = new SourceSignature();
                signature.path = normalized.toString();
                signature.size = Files.size(normalized);
                signature.modified = Files.getLastModifiedTime(normalized).toMillis();
                signatures.add(signature);
            } catch (IOException e) {
                SourceSignature signature = new SourceSignature();
                signature.path = path.toAbsolutePath().normalize().toString();
                signature.size = -1L;
                signature.modified = -1L;
                signatures.add(signature);
            }
        }
        signatures.sort(Comparator.comparing(s -> s.path));
        return signatures;
    }

    private static class AttachmentDbCache {
        List<SourceSignature> sources;
        Map<String, Set<String>> gunAllowedAttachments;
        Map<String, Set<String>> tagToAttachments;
        Map<String, String> attachmentSlotType;
    }

    private record CompatibilityKey(String gunId, String attachId, int generation) {}

    private static class SourceSignature {
        String path;
        long size;
        long modified;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SourceSignature other)) return false;
            return size == other.size && modified == other.modified && Objects.equals(path, other.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, size, modified);
        }
    }
}
