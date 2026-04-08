package com.levanilla.rogue.core;

import java.util.UUID;

/**
 * ゲーム全体のバランス定数を一元管理するクラス。
 * バランス調整はこのファイルのみを編集すれば完了する設計。
 *
 * セクション:
 *  1. 座標・境界
 *  2. 戦闘バランス
 *  3. 経済バランス
 *  4. スタミナ
 *  5. インベントリ
 *  6. プリセット (装備選択)
 *  7. ドロップテーブル
 *  8. HUD / UI
 */
public final class GameConstants {

    private GameConstants() {} // インスタンス化禁止

    // =====================================================================
    //  1. 座標・境界
    // =====================================================================

    /** ロビーのスポーン座標 */
    public static final double LOBBY_X = 0.5;
    public static final double LOBBY_Y = 201.0;
    public static final double LOBBY_Z = 0.5;

    /** 奈落ラインY座標（これより下でロビー帰還） */
    public static final double VOID_Y_THRESHOLD = -10.0;

    /** テレポート境界（エンダーマン等） */
    public static final double TELEPORT_BOUNDARY = 120.0;

    /** モブの場外引き戻し距離（GRID_SIZE=240のため±120ブロック＋余裕） */
    public static final double MOB_BOUNDARY = 200.0;

    /** フロアクリア判定の検索範囲 */
    public static final double FLOOR_CLEAR_RADIUS = 250.0;

    /** スポーン安全範囲 (敵排除範囲) */
    public static final double SPAWN_SAFE_RADIUS = 28.0;

    /** ダンジョン生成の基準Y座標 */
    public static final int DUNGEON_BASE_Y = 100;

    // =====================================================================
    //  2. 戦闘バランス
    // =====================================================================

    /** ステルス: スニーク時の検知範囲倍率 */
    public static final double STEALTH_SNEAK_RANGE = 0.5;
    /** ステルス: 伏せ時の検知範囲倍率 */
    public static final double STEALTH_CRAWL_RANGE = 0.3;
    /** ステルス検知チェック範囲 */
    public static final double STEALTH_CHECK_RADIUS = 32.0;
    /** ステルス修正のUUID */
    public static final UUID STEALTH_MODIFIER_UUID = UUID.fromString("d5a2e4c8-1234-4567-8901-abcdef123456");

    /** 射撃音: サプレッサーなし時のアラート範囲 (ブロック) */
    public static final double GUNSHOT_ALERT_RADIUS = 40.0;
    /** 射撃音: サプレッサー装着時のアラート範囲 */
    public static final double SUPPRESSED_ALERT_RADIUS = 8.0;

    /** 自然回復: ダメージ後のクールダウン (tick) — 5秒 */
    public static final int REGEN_DAMAGE_COOLDOWN_TICKS = 100;

    /** ダメージ: 伏せ時のボーナス倍率 */
    public static final float CRAWL_DAMAGE_MULT = 1.40f;
    /** ダメージ: スニーク時のボーナス倍率 */
    public static final float SNEAK_DAMAGE_MULT = 1.20f;
    /** クリティカルダメージ閾値 */
    public static final float CRITICAL_DAMAGE_THRESHOLD = 20.0f;
    /** パーク「Fortune」によるクリティカルヒット時のダメージ倍率 */
    public static final float CRITICAL_DAMAGE_MULT = 1.5f;

    /** ダガーのダメージ倍率 */
    public static final float DAGGER_DAMAGE_MULT = 0.5f;

    /** ステーキ使用時の回復量 */
    public static final float STEAK_HEAL_AMOUNT = 6.0f;

    /** MEDKIT 回復率 (最大HPの割合) */
    public static final float MEDKIT_HEAL_RATIO = 0.3f;

    /** ステルスチェック間隔（チック） */
    public static final int STEALTH_CHECK_INTERVAL = 20;

    // =====================================================================
    //  3. 経済バランス
    // =====================================================================

    /** 初期ゴールド */
    public static final int INITIAL_GOLD = 100;

    /** ベースキル報酬 */
    public static final int KILL_REWARD_BASE = 15;
    /** フロア進行ごとの増加量 */
    public static final int KILL_REWARD_PER_FLOOR = 2;
    /** キル報酬の最大キャップ (ハイパーインフレ対策) */
    public static final int KILL_REWARD_MAX_CAP = 100;

    /** デスペナルティの割合 */
    public static final float DEATH_PENALTY_RATE = 0.05f;

    /** 基本ドロップ率 */
    public static final float DROP_BASE_CHANCE = 0.05f;

    /** SCRAP METAL の売却額 */
    public static final int SCRAP_SELL_VALUE = 50;
    /** GOLD CACHE の売却額 */
    public static final int GOLD_CACHE_VALUE = 250;

    /** 銃の売却レート (購入価格の割合) */
    public static final float GUN_SELL_RATE = 0.4f;
    /** 弾薬の1発あたりの売却額 */
    public static final int AMMO_SELL_PRICE_PER = 1;
    /** アタッチメントの一律売却額 */
    public static final int ATTACHMENT_SELL_PRICE = 50;

    /** スタッシュ最大行数 */
    public static final int STASH_MAX_LINES = 6;
    /** スタッシュ拡張の基礎費用係数 */
    public static final int STASH_UPGRADE_COST_BASE = 500;

    /** インベントリ最大拡張レベル (2スロット×12レベル=24→実質23スロット全開放) */
    public static final int INV_MAX_LEVEL = 12;

    /** OVERCLOCKED パークの最大取得数 */
    public static final int MAX_OVERCLOCKED_PERKS = 2;
    /** CURSED パークの最大取得数 */
    public static final int MAX_CURSED_PERKS = 8;

    /** フロアクリア判定の間隔（ティック） — 0.5秒 */
    public static final int FLOOR_CLEAR_CHECK_INTERVAL = 10;

    // =====================================================================
    //  4. スタミナ
    // =====================================================================

    /** デフォルト最大スタミナ (10秒 = 200 ticks) */
    public static final float DEFAULT_MAX_STAMINA = 200.0f;
    /** スタミナ消費レート (tick あたり) */
    public static final float STAMINA_CONSUME_RATE = 1.0f;
    /** スタミナ回復レート (tick あたり) */
    public static final float STAMINA_REGEN_RATE = 0.5f;

    /** カスタム自然回復: パーク付き回復量 */
    public static final float REGEN_PERK_HEAL = 0.15f;
    /** カスタム自然回復: パークなし回復量 */
    public static final float REGEN_BASE_HEAL = 0.1f;
    /** カスタム自然回復: パークなし時のHP上限割合 */
    public static final float REGEN_BASE_CAP_RATIO = 0.3f;
    /** 自然回復に必要なスタミナ割合 */
    public static final float REGEN_STAMINA_THRESHOLD = 0.5f;

    /** ダッシュ許可の最低食料レベル */
    public static final int SPRINT_MIN_FOOD_LEVEL = 0;
    /** ダッシュ時に設定する食料レベル */
    public static final int SPRINT_RESTORE_FOOD = 7;
    /** ダッシュ許可の上限食料レベル */
    public static final int SPRINT_MAX_CHECK_FOOD = 6;

    // =====================================================================
    //  5. インベントリ・専用スロット
    // =====================================================================

    /** インベントリチェック間隔（チック） */
    public static final int INV_CHECK_INTERVAL = 5;
    /** 装備未選択時の初期装備画面表示間隔（チック） */
    public static final int GEAR_CHECK_INTERVAL = 40;

    // --- Dedicated Slot Ranges ---
    /** Gun slots: 0-1 */
    public static final int SLOT_GUN_START = 0;
    public static final int SLOT_GUN_END = 1;
    /** Melee slot: 2 */
    public static final int SLOT_MELEE = 2;
    /** Item slots: 3-8 */
    public static final int SLOT_ITEM_START = 3;
    public static final int SLOT_ITEM_END = 8;
    /** Ammo slots for gun 1: 9-10 */
    public static final int SLOT_AMMO_GUN1_START = 9;
    public static final int SLOT_AMMO_GUN1_END = 10;
    /** Ammo slots for gun 2: 11-12 */
    public static final int SLOT_AMMO_GUN2_START = 11;
    public static final int SLOT_AMMO_GUN2_END = 12;

    // =====================================================================
    //  6. プリセット (装備選択)
    // =====================================================================

    /** 速度修正用 UUID */
    public static final UUID SPEED_MODIFIER_UUID = UUID.fromString("732292f7-2856-4240-a15d-5fb90d96d934");

    // Balanced
    public static final float BALANCED_HP = 30.0f;
    public static final float BALANCED_ARMOR = 4.0f;
    public static final float BALANCED_STAMINA = 100.0f;
    public static final double BALANCED_SPEED = 0.0;
    public static final String BALANCED_GUN = "tacz:glock_17";
    public static final String BALANCED_AMMO = "tacz:9mm";
    public static final int BALANCED_MAG = 17;
    public static final int BALANCED_AMMO_COUNT = 192;

    // Power
    public static final float POWER_HP = 24.0f;
    public static final float POWER_ARMOR = 2.0f;
    public static final float POWER_STAMINA = 80.0f;
    public static final double POWER_SPEED = -0.05;
    public static final String POWER_GUN = "tacz:deagle";
    public static final String POWER_AMMO = "tacz:50ae";
    public static final int POWER_MAG = 7;
    public static final int POWER_AMMO_COUNT = 64;

    // Classic
    public static final float CLASSIC_HP = 34.0f;
    public static final float CLASSIC_ARMOR = 3.0f;
    public static final float CLASSIC_STAMINA = 120.0f;
    public static final double CLASSIC_SPEED = 0.05;
    public static final String CLASSIC_GUN = "tacz:m1911";
    public static final String CLASSIC_AMMO = "tacz:45acp";
    public static final int CLASSIC_MAG = 7;
    public static final int CLASSIC_AMMO_COUNT = 128;

    // =====================================================================
    //  7. ドロップテーブルの重み
    // =====================================================================

    /** ドロップテーブル: EMERGENCY RATION (金リンゴ) */
    public static final int DROP_RARE_BASE = 5;
    /** ドロップテーブル: 武器 (超レア) */
    public static final int DROP_WEAPON_BASE = 2;
    /** ドロップテーブル: アタッチメント */
    public static final int DROP_ATTACHMENT_BASE = 5;

    // === Shop Price Multipliers ===
    public static final float PRICE_MULT_WEAPON = 3.0f;
    public static final float PRICE_MULT_ATTACHMENT = 3.0f;
    public static final float PRICE_MULT_AMMO = 1.5f;
    public static final float PRICE_MULT_SPECIAL = 1.5f;

    /** ドロップテーブル: MEDKIT */
    public static final int DROP_UNCOMMON_BASE = 15;
    /** ドロップテーブル: FIELD RATION */
    public static final int DROP_RATION_BASE = 35;
    /** ドロップテーブル: GOLD CACHE */
    public static final int DROP_GOLD_CACHE_BASE = 55;
    /** ドロップテーブル: STAMINA BOOST */
    public static final int DROP_STAMINA_BASE = 70;

    // =====================================================================
    //  8. HUD / UI 定数
    // =====================================================================

    /** ダメージ表記の寿命（チック） */
    public static final int DAMAGE_INDICATOR_LIFETIME = 40;
    /** ドロップ表記の寿命（チック） */
    public static final int DROP_INDICATOR_LIFETIME = 60;
    /** HUD通知のデフォルト表示時間（チック） */
    public static final int HUD_NOTIFICATION_DURATION = 80;
    /** HUD通知の最大表示数 */
    public static final int HUD_NOTIFICATION_MAX = 5;
    /** フロアクリア演出のチック数 */
    public static final int VICTORY_DISPLAY_TICKS = 100;

    // =====================================================================
    //  9. スケーリング
    // =====================================================================

    /** 階層HPスケーリング: 1階層あたりの増加率 */
    public static final double FLOOR_HP_SCALE_PER_LEVEL = 0.15;
    /** 階層攻撃力スケーリング: 1階層あたりの増加率 */
    public static final double FLOOR_DMG_SCALE_PER_LEVEL = 0.08;

    /** 発光開始フロア */
    public static final int GLOW_FLOOR_THRESHOLD = 10;

    /** ボスHPベース倍率 */
    public static final double BOSS_HP_BASE = 5.0;
    /** ボスHP 1フロアあたり増加 */
    public static final double BOSS_HP_PER_FLOOR = 0.5;
    /** ボス攻撃力ベース倍率 */
    public static final double BOSS_DMG_BASE = 3.0;
    /** ボス攻撃力 1フロアあたり増加 */
    public static final double BOSS_DMG_PER_FLOOR = 0.2;
    /** ボス速度倍率 */
    public static final double BOSS_SPEED_MULT = 0.9;
    /** ボス防御力ベース */
    public static final double BOSS_ARMOR_BASE = 10.0;
    /** ボス防御力 1フロアあたり増加 */
    public static final double BOSS_ARMOR_PER_FLOOR = 0.5;

    // =====================================================================
    //  10. フラッシュライト
    // =====================================================================

    /** フラッシュライトの光レベル (0-15) */
    public static final int FLASHLIGHT_LIGHT_LEVEL = 14;

    /** MEDKIT の初期支給数 */
    public static final int STARTER_MEDKIT_COUNT = 3;
    /** ステーキの初期支給数 */
    public static final int STARTER_STEAK_COUNT = 4;
}
