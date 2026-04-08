package com.levanilla.rogue.core.registry;

import java.util.HashMap;
import java.util.Map;

/**
 * 銃 → 弾薬マッピング、マガジンサイズ、弾薬スタックサイズのデータベース。
 * 静的マッピングのみ保持し、ロジックは持たない。
 */
public final class AmmoDatabase {

    private AmmoDatabase() {}

    // ========== 銃 → 弾薬マッピング ==========

    private static final Map<String, String> GUN_AMMO_MAP = new HashMap<>();
    static {
        // Pistols
        GUN_AMMO_MAP.put("tacz:glock_17", "tacz:9mm");
        GUN_AMMO_MAP.put("tacz:m1911", "tacz:45acp");
        GUN_AMMO_MAP.put("tacz:p320", "tacz:9mm");
        GUN_AMMO_MAP.put("tacz:deagle", "tacz:50ae");
        GUN_AMMO_MAP.put("tacz:deagle_golden", "tacz:50ae");
        GUN_AMMO_MAP.put("tacz:cz75", "tacz:9mm");
        GUN_AMMO_MAP.put("tacz:b93r", "tacz:9mm");
        GUN_AMMO_MAP.put("tacz:timeless50", "tacz:50ae");
        // SMGs
        GUN_AMMO_MAP.put("tacz:hk_mp5a5", "tacz:9mm");
        GUN_AMMO_MAP.put("tacz:p90", "tacz:57x28");
        GUN_AMMO_MAP.put("tacz:ump45", "tacz:45acp");
        GUN_AMMO_MAP.put("tacz:vector45", "tacz:45acp");
        GUN_AMMO_MAP.put("tacz:uzi", "tacz:9mm");
        // Rifles
        GUN_AMMO_MAP.put("tacz:m4a1", "tacz:556x45");
        GUN_AMMO_MAP.put("tacz:ak47", "tacz:762x39");
        GUN_AMMO_MAP.put("tacz:m16a4", "tacz:556x45");
        GUN_AMMO_MAP.put("tacz:m16a1", "tacz:556x45");
        GUN_AMMO_MAP.put("tacz:scar_h", "tacz:762x39");
        GUN_AMMO_MAP.put("tacz:scar_l", "tacz:556x45");
        GUN_AMMO_MAP.put("tacz:hk416d", "tacz:556x45");
        GUN_AMMO_MAP.put("tacz:aug", "tacz:556x45");
        GUN_AMMO_MAP.put("tacz:fn_fal", "tacz:762x39");
        GUN_AMMO_MAP.put("tacz:qbz_95", "tacz:58x42");
        GUN_AMMO_MAP.put("tacz:qbz_191", "tacz:58x42");
        GUN_AMMO_MAP.put("tacz:g36k", "tacz:556x45");
        GUN_AMMO_MAP.put("tacz:hk_g3", "tacz:762x39");
        GUN_AMMO_MAP.put("tacz:spr15hb", "tacz:556x45");
        GUN_AMMO_MAP.put("tacz:type_81", "tacz:762x39");
        GUN_AMMO_MAP.put("tacz:sks_tactical", "tacz:762x39");
        // Shotguns
        GUN_AMMO_MAP.put("tacz:m870", "tacz:12g");
        GUN_AMMO_MAP.put("tacz:spas_12", "tacz:12g");
        GUN_AMMO_MAP.put("tacz:aa12", "tacz:12g");
        GUN_AMMO_MAP.put("tacz:m1014", "tacz:12g");
        GUN_AMMO_MAP.put("tacz:db_long", "tacz:12g");
        GUN_AMMO_MAP.put("tacz:db_short", "tacz:12g");
        // Snipers
        GUN_AMMO_MAP.put("tacz:ai_awp", "tacz:338");
        GUN_AMMO_MAP.put("tacz:mk14", "tacz:308");
        GUN_AMMO_MAP.put("tacz:m700", "tacz:308");
        GUN_AMMO_MAP.put("tacz:m107", "tacz:50bmg");
        GUN_AMMO_MAP.put("tacz:m95", "tacz:50bmg");
        GUN_AMMO_MAP.put("tacz:springfield1873", "tacz:45_70");
        // LMGs
        GUN_AMMO_MAP.put("tacz:m249", "tacz:556x45");
        GUN_AMMO_MAP.put("tacz:rpk", "tacz:762x39");
        GUN_AMMO_MAP.put("tacz:fn_evolys", "tacz:68x51fury");
        GUN_AMMO_MAP.put("tacz:minigun", "tacz:556x45");
        // Special
        GUN_AMMO_MAP.put("tacz:rpg7", "tacz:rpg_rocket");
        GUN_AMMO_MAP.put("tacz:m320", "tacz:40mm");
    }

    // ========== マガジンサイズ ==========

    private static final Map<String, Integer> MAG_SIZE_MAP = new HashMap<>();
    static {
        // Pistols
        MAG_SIZE_MAP.put("tacz:glock_17", 17); MAG_SIZE_MAP.put("tacz:m1911", 7);
        MAG_SIZE_MAP.put("tacz:p320", 17); MAG_SIZE_MAP.put("tacz:deagle", 7);
        MAG_SIZE_MAP.put("tacz:deagle_golden", 7); MAG_SIZE_MAP.put("tacz:cz75", 16);
        MAG_SIZE_MAP.put("tacz:b93r", 20); MAG_SIZE_MAP.put("tacz:timeless50", 9);
        // SMGs
        MAG_SIZE_MAP.put("tacz:hk_mp5a5", 30); MAG_SIZE_MAP.put("tacz:p90", 50);
        MAG_SIZE_MAP.put("tacz:ump45", 25); MAG_SIZE_MAP.put("tacz:vector45", 25);
        MAG_SIZE_MAP.put("tacz:uzi", 32);
        // Rifles
        MAG_SIZE_MAP.put("tacz:m4a1", 30); MAG_SIZE_MAP.put("tacz:ak47", 30);
        MAG_SIZE_MAP.put("tacz:m16a4", 30); MAG_SIZE_MAP.put("tacz:m16a1", 20);
        MAG_SIZE_MAP.put("tacz:scar_h", 20); MAG_SIZE_MAP.put("tacz:scar_l", 30);
        MAG_SIZE_MAP.put("tacz:hk416d", 30); MAG_SIZE_MAP.put("tacz:aug", 30);
        MAG_SIZE_MAP.put("tacz:fn_fal", 20); MAG_SIZE_MAP.put("tacz:qbz_95", 30);
        MAG_SIZE_MAP.put("tacz:qbz_191", 30); MAG_SIZE_MAP.put("tacz:g36k", 30);
        MAG_SIZE_MAP.put("tacz:hk_g3", 20); MAG_SIZE_MAP.put("tacz:spr15hb", 30);
        MAG_SIZE_MAP.put("tacz:type_81", 20); MAG_SIZE_MAP.put("tacz:sks_tactical", 20);
        // Shotguns
        MAG_SIZE_MAP.put("tacz:m870", 6); MAG_SIZE_MAP.put("tacz:spas_12", 8);
        MAG_SIZE_MAP.put("tacz:aa12", 20); MAG_SIZE_MAP.put("tacz:m1014", 7);
        MAG_SIZE_MAP.put("tacz:db_long", 2); MAG_SIZE_MAP.put("tacz:db_short", 2);
        // Snipers
        MAG_SIZE_MAP.put("tacz:ai_awp", 5); MAG_SIZE_MAP.put("tacz:mk14", 20);
        MAG_SIZE_MAP.put("tacz:m700", 5); MAG_SIZE_MAP.put("tacz:m107", 10);
        MAG_SIZE_MAP.put("tacz:m95", 5); MAG_SIZE_MAP.put("tacz:springfield1873", 1);
        // LMGs
        MAG_SIZE_MAP.put("tacz:m249", 200); MAG_SIZE_MAP.put("tacz:rpk", 75);
        MAG_SIZE_MAP.put("tacz:fn_evolys", 100); MAG_SIZE_MAP.put("tacz:minigun", 200);
        // Special
        MAG_SIZE_MAP.put("tacz:rpg7", 1); MAG_SIZE_MAP.put("tacz:m320", 1);
    }

    // ========== 弾薬スタックサイズ ==========

    private static final Map<String, Integer> AMMO_STACK_MAP = new HashMap<>();
    static {
        AMMO_STACK_MAP.put("tacz:9mm", 120);
        AMMO_STACK_MAP.put("tacz:45acp", 100);
        AMMO_STACK_MAP.put("tacz:50ae", 56);
        AMMO_STACK_MAP.put("tacz:556x45", 120);
        AMMO_STACK_MAP.put("tacz:762x39", 120);
        AMMO_STACK_MAP.put("tacz:762x54", 60);
        AMMO_STACK_MAP.put("tacz:545x39", 120);
        AMMO_STACK_MAP.put("tacz:338", 40);
        AMMO_STACK_MAP.put("tacz:12g", 24);
        AMMO_STACK_MAP.put("tacz:50bmg", 40);
        AMMO_STACK_MAP.put("tacz:46x30", 100);
        AMMO_STACK_MAP.put("tacz:57x28", 100);
        AMMO_STACK_MAP.put("tacz:308", 60);
        AMMO_STACK_MAP.put("tacz:30_06", 40);
        AMMO_STACK_MAP.put("tacz:357mag", 48);
        AMMO_STACK_MAP.put("tacz:45_70", 24);
        AMMO_STACK_MAP.put("tacz:58x42", 120);
        AMMO_STACK_MAP.put("tacz:68x51fury", 80);
        AMMO_STACK_MAP.put("tacz:762x25", 100);
        AMMO_STACK_MAP.put("tacz:rpg_rocket", 8);
        AMMO_STACK_MAP.put("tacz:40mm", 8);
    }

    // ========== アクセサ ==========

    /** 銃IDに対応する弾薬IDを返す */
    public static String getAmmoForGun(String gunId) {
        return GUN_AMMO_MAP.getOrDefault(gunId, "");
    }

    /** 銃のマガジンサイズを返す（不明は10） */
    public static int getMagazineSize(String gunId) {
        return MAG_SIZE_MAP.getOrDefault(gunId, 10);
    }

    /** 弾薬IDに対するスタックサイズを返す */
    public static int getAmmoStackSize(String ammoId) {
        return AMMO_STACK_MAP.getOrDefault(ammoId, 64);
    }
}
