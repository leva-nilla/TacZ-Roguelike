package com.levanilla.rogue.core;

import java.util.UUID;

/**
 * ゲーム全体で共有する主要なバランス定数を管理するクラス。
 * 生成、報酬、ショップ在庫などの局所的なテーブルは各サービス/レジストリ側にも持つ。
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
    /** 射撃音: TacZ消音距離が取れない時のサプレッサー最小アラート範囲 */
    public static final double SUPPRESSED_ALERT_RADIUS = 14.0;

    /** 自然回復: ダメージ後のクールダウン (tick) — 5秒 */
    public static final int REGEN_DAMAGE_COOLDOWN_TICKS = 100;

    /** ダメージ: 伏せ時のボーナス倍率 */
    public static final float CRAWL_DAMAGE_MULT = 1.25f;
    /** ダメージ: スニーク時のボーナス倍率 */
    public static final float SNEAK_DAMAGE_MULT = 1.12f;
    /** クリティカルダメージ閾値 */
    public static final float CRITICAL_DAMAGE_THRESHOLD = 20.0f;
    /** パーク「Fortune」によるクリティカルヒット時のダメージ倍率 */
    public static final float CRITICAL_DAMAGE_MULT = 1.5f;

    /** Dagger系のダメージ倍率。リーチ/速度と引き換えに単発火力を落とす */
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

    /** ベースキル報酬 (v0.5: 15→20 序盤の金欠緩和) */
    public static final int KILL_REWARD_BASE = 20;
    /** フロア進行ごとの増加量 (v0.5: 2→3 中盤スケーリング改善) */
    public static final int KILL_REWARD_PER_FLOOR = 3;
    /** キル報酬の最大キャップ (v0.5: 100→150 高フロア報酬改善) */
    public static final int KILL_REWARD_MAX_CAP = 150;
    /** 51-100層のキル報酬キャップ */
    public static final int KILL_REWARD_HIGH_CAP = 220;
    /** 101層以降のキル報酬キャップ */
    public static final int KILL_REWARD_ENDLESS_CAP = 300;

    /** デスペナルティの割合 */
    public static final float DEATH_PENALTY_RATE = 0.05f;

    /** 基本ドロップ率 (v0.5: 0.05→0.08 ドロップ頻度向上) */
    public static final float DROP_BASE_CHANCE = 0.08f;

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
    public static final int STASH_UPGRADE_COST_BASE = 2000;
    /** フラッシュライト最大強化レベル */
    public static final int FLASHLIGHT_MAX_LEVEL = 5;
    /** フラッシュライト強化の基礎費用 */
    public static final int FLASHLIGHT_UPGRADE_COST_BASE = 900;

    /** インベントリ最大拡張レベル (2スロット×12レベル=24→実質23スロット全開放) */
    public static final int INV_MAX_LEVEL = 12;
    /** 弾薬容量最大アップグレードレベル (+50% x 5) */
    public static final int AMMO_CAPACITY_MAX_LEVEL = 5;
    /** 弾薬容量拡張の基礎費用 */
    public static final int AMMO_CAP_UPGRADE_COST_BASE = 2000;
    public static final int INV_UPGRADE_COST_BASE = 2000;

    /** 近接武器最大アップグレードレベル (+50% x 5) */
    public static final int MELEE_MAX_LEVEL = 5;
    /** 近接武器拡張の基礎費用 */
    public static final int MELEE_UPGRADE_COST_BASE = 1500;

    /** @deprecated ペナルティ修飾子は取得上限ではなく実効ペナルティのソフトキャップで制御する */
    @Deprecated
    public static final int MAX_OVERCLOCKED_PERKS = 2;
    /** @deprecated ペナルティ修飾子は取得上限ではなく実効ペナルティのソフトキャップで制御する */
    @Deprecated
    public static final int MAX_CURSED_PERKS = 8;
    /** パークリロールのコスト */
    public static final int PERK_REROLL_COST = 500;
    /** ランダムパークの初期価格 */
    public static final int RANDOM_PERK_BASE_PRICE = 2000;
    /** ランダムパーク購入ごとの価格上昇量 */
    public static final int RANDOM_PERK_PRICE_STEP = 400;

    /** パークLvごとの基礎効果量。全体の高Lvインフレを抑えるため10%から下げる */
    public static final float PERK_EFFECT_PER_LEVEL = 7.5f;
    /** パークLvソフトキャップ開始Lv */
    public static final int PERK_LEVEL_SOFTCAP_START = 3;
    /** ソフトキャップ以降のLv伸び率 */
    public static final float PERK_LEVEL_POST_SOFTCAP_SCALE = 0.65f;
    /** 強カテゴリの追加係数 */
    public static final float PERK_STRONG_CATEGORY_SCALE = 0.85f;
    /** 回復・自動化カテゴリの追加係数 */
    public static final float PERK_RECOVERY_CATEGORY_SCALE = 0.90f;

    /** ローグライク内で許可するアーマー属性の上限。高スタックでも無駄になりにくいよう高めに取る */
    public static final double ROGUE_ARMOR_ATTRIBUTE_MAX = 1000.0D;
    /** バニラ軽減上限を超えたアーマーが追加軽減に変換され始める値 */
    public static final double ARMOR_OVERCAP_START = 20.0D;
    /** 追加アーマー軽減の伸びを抑える係数 */
    public static final double ARMOR_OVERCAP_SCALE = 80.0D;
    /** 追加アーマー軽減の上限。バニラ軽減後に乗算される */
    public static final float ARMOR_OVERCAP_MAX_EXTRA_REDUCTION = 0.45f;
    /** 弾薬節約率の最大値。100%化は戦闘リスクが消えるため禁止 */
    public static final float AMMO_SAVE_MAX_CHANCE = 0.75f;
    /** Ammo Saver の表示/判定に使う効果量係数。上限到達を遅らせる */
    public static final float AMMO_SAVE_EFFECT_SCALE = 0.55f;
    /** 特殊耐性の最大軽減率。完全耐性化を避ける */
    public static final float SPECIAL_RESISTANCE_MAX = 0.65f;
    /** DODGE の最大回避率。無敵化を避けるため60%で止める */
    public static final float DODGE_MAX_CHANCE = 0.60f;
    /** DODGE の表示/判定に使う効果量係数。Lv上昇の価値を残しつつ序盤の過剰回避を抑える */
    public static final float DODGE_EFFECT_SCALE = 0.50f;
    /** Fortune のクリティカル率上限。超過分はクリティカルダメージへ変換する */
    public static final float CRITICAL_CHANCE_MAX = 1.00f;
    /** クリティカル率超過分からクリティカルダメージ倍率へ変換する係数 */
    public static final float CRITICAL_OVERFLOW_DAMAGE_SCALE = 0.35f;
    /** クリティカル率超過による追加クリティカルダメージ倍率上限 */
    public static final float CRITICAL_OVERFLOW_DAMAGE_MAX = 0.55f;
    /** パーク保存に使うscoreboard tagの安全上限。バニラ上限1024に対して他タグ分を残す */
    public static final int MAX_PERK_TAG_COUNT = 800;
    /** AUTOLOADER の効果値から発/秒へ変換する除数 */
    public static final float AUTOLOADER_EFFECT_DIVISOR = 20.0f;
    /** AUTOLOADER が有効な場合の最低装填速度 */
    public static final float AUTOLOADER_MIN_ROUNDS_PER_SECOND = 0.5f;
    /** Vampire / Bloodlust / Quick Fix の効果値からHP回復量へ変換する除数 */
    public static final float PERK_RECOVERY_HEAL_DIVISOR = 20.0f;
    /** レアリティ/パーク合算後のリロード時間倍率下限 */
    public static final float MIN_EFFECTIVE_RELOAD_MULT = 0.35f;
    /** レアリティ/パーク合算後の実効連射速度上限 */
    public static final int MAX_EFFECTIVE_FIRE_RATE_RPM = 1440;
    /** FIRE_RATE パークのソフトキャップ開始値 */
    public static final float FIRE_RATE_SOFTCAP_START = 80.0f;
    /** FIRE_RATE パークのソフトキャップ後の伸び率 */
    public static final float FIRE_RATE_POST_SOFTCAP_SCALE = 0.20f;
    /** FIRE_RATE パークの最大ボーナス */
    public static final float FIRE_RATE_HARD_CAP = 125.0f;

    public static float getArmorOvercapExtraReduction(double armor) {
        double extraArmor = Math.max(0.0D, armor - ARMOR_OVERCAP_START);
        if (extraArmor <= 0.0D) {
            return 0.0f;
        }
        return (float) (ARMOR_OVERCAP_MAX_EXTRA_REDUCTION
            * (extraArmor / (extraArmor + ARMOR_OVERCAP_SCALE)));
    }

    public static float getArmorMitigationEstimate(double armor) {
        double baseReduction = Math.min(0.80D, Math.max(0.0D, armor) / 25.0D);
        float extraReduction = getArmorOvercapExtraReduction(armor);
        return (float) (1.0D - (1.0D - baseReduction) * (1.0D - extraReduction));
    }

    /** フロアクリア判定の間隔（ティック） — 0.5秒 */
    public static final int FLOOR_CLEAR_CHECK_INTERVAL = 10;

    // =====================================================================
    //  4. スタミナ
    // =====================================================================

    /** デフォルト最大スタミナ (10秒 = 200 ticks) */
    public static final float DEFAULT_MAX_STAMINA = 200.0f;
    /** スタミナ消費レート (tick あたり) */
    public static final float STAMINA_CONSUME_RATE = 1.0f;
    /** ADS中のスタミナ消費レート (tick あたり) */
    public static final float STAMINA_ADS_CONSUME_RATE = 0.32f;
    /** ジャンプ1回あたりのスタミナ消費 */
    public static final float STAMINA_JUMP_COST = 12.0f;
    /** スタミナ切れ後に再使用できるようになる回復量 */
    public static final float STAMINA_EXHAUST_RECOVERY = 35.0f;
    /** スタミナ切れADSを許容する猶予時間 */
    public static final int STAMINA_ADS_EXHAUST_GRACE_TICKS = 60;
    /** スタミナ切れADS継続時の移動速度ペナルティ */
    public static final double STAMINA_ADS_EXHAUST_SPEED_PENALTY = -0.35D;
    /** スタミナ切れADS継続時の継続自傷ダメージ（最大HPに対する割合/秒） */
    public static final float STAMINA_ADS_EXHAUST_DAMAGE_PER_SECOND = 0.01f;
    /** スニーク中のADSスタミナ消費倍率 */
    public static final float STAMINA_ADS_SNEAK_MULT = 0.80f;
    /** 伏せ中のADSスタミナ消費倍率 */
    public static final float STAMINA_ADS_PRONE_MULT = 0.65f;
    /** スタミナ回復レート (tick あたり) */
    public static final float STAMINA_REGEN_RATE = 0.5f;

    /** カスタム自然回復: Regeneration 1スタックあたりの最大HP割合/秒 */
    public static final float REGEN_PERK_HEAL_RATIO = 0.01f;
    /** カスタム自然回復: Regeneration 1スタックあたりの自然回復上限解除割合 */
    public static final float REGEN_PERK_CAP_UNLOCK_RATIO = 0.05f;
    /** カスタム自然回復: Regeneration の効果値からHP/秒へ変換する除数 */
    public static final float REGEN_EFFECT_HEAL_DIVISOR = 100.0f;
    /** カスタム自然回復: Regeneration の効果値1あたりの回復上限解除割合 */
    public static final float REGEN_CAP_UNLOCK_PER_EFFECT = 0.005f;
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
    /** Item slots: 3-11 */
    public static final int SLOT_ITEM_START = 3;
    public static final int SLOT_ITEM_END = 11;
    /** Ammo slots: 12-15 (共通弾薬枠 — gun1/gun2の区別なし) */
    public static final int SLOT_AMMO_START = 12;
    public static final int SLOT_AMMO_END = 15;
    // 後方互換エイリアス
    public static final int SLOT_AMMO_GUN1_START = SLOT_AMMO_START;
    public static final int SLOT_AMMO_GUN1_END = 13;
    public static final int SLOT_AMMO_GUN2_START = 14;
    public static final int SLOT_AMMO_GUN2_END = SLOT_AMMO_END;

    // =====================================================================
    //  6. プリセット (装備選択)
    // =====================================================================

    /** 速度修正用 UUID */
    public static final UUID SPEED_MODIFIER_UUID = UUID.fromString("732292f7-2856-4240-a15d-5fb90d96d934");

    // Balanced
    public static final float BALANCED_HP = 30.0f;
    public static final float BALANCED_ARMOR = 4.0f;
    public static final float BALANCED_STAMINA = 120.0f;
    public static final double BALANCED_SPEED = 0.0;
    public static final String BALANCED_GUN = "tacz:glock_17";
    public static final String BALANCED_AMMO = "tacz:9mm";
    public static final int BALANCED_MAG = 17;
    public static final int BALANCED_AMMO_COUNT = 192;

    // Power
    public static final float POWER_HP = 24.0f;
    public static final float POWER_ARMOR = 2.0f;
    public static final float POWER_STAMINA = 100.0f;
    public static final double POWER_SPEED = -0.05;
    public static final String POWER_GUN = "tacz:deagle";
    public static final String POWER_AMMO = "tacz:50ae";
    public static final int POWER_MAG = 7;
    public static final int POWER_AMMO_COUNT = 64;

    // Classic
    public static final float CLASSIC_HP = 34.0f;
    public static final float CLASSIC_ARMOR = 3.0f;
    public static final float CLASSIC_STAMINA = 140.0f;
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
    /** ドロップテーブル: 武器 (v0.5: 2→4 武器ドロップ倍増) */
    public static final int DROP_WEAPON_BASE = 4;
    /** ドロップテーブル: アタッチメント (v0.5: 5→8) */
    public static final int DROP_ATTACHMENT_BASE = 8;

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

    /** 通常敵HPスケーリングの線形項 */
    public static final double ENEMY_HP_SCALE_LINEAR = 0.10D;
    /** 通常敵HPスケーリングの二次項。100層で約12.9倍に抑える */
    public static final double ENEMY_HP_SCALE_QUADRATIC = 0.0002D;
    /** 通常敵ダメージスケーリングの線形項 */
    public static final double ENEMY_DMG_SCALE_LINEAR = 0.05D;
    /** 通常敵ダメージスケーリングの二次項 */
    public static final double ENEMY_DMG_SCALE_QUADRATIC = 0.0005D;
    /** ボスHPスケーリングの基礎値 */
    public static final double BOSS_HP_SCALE_BASE = 3.4D;
    /** ボスHPスケーリングの線形項。100層で約50倍 */
    public static final double BOSS_HP_SCALE_LINEAR = 0.20D;
    /** ボスHPスケーリングの二次項 */
    public static final double BOSS_HP_SCALE_QUADRATIC = 0.0028D;
    /** ボス攻撃力スケーリングの基礎値 */
    public static final double BOSS_DMG_SCALE_BASE = 2.8D;
    /** ボス攻撃力スケーリングの線形項 */
    public static final double BOSS_DMG_SCALE_LINEAR = 0.13D;
    /** ボス攻撃力スケーリングの二次項。即死寄りを少し抑える */
    public static final double BOSS_DMG_SCALE_QUADRATIC = 0.0015D;

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
    public static final double BOSS_SPEED_MULT = 0.5;
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
