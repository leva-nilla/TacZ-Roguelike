# TacZ Roguelike ゲームシステム仕様書

作成日: 2026-05-12
対象: `tac_rogue_mod` 現在実装

この文書は、現状のゲームシステムを再検討するための仕様整理です。プレイヤー向け説明ではなく、実装とゲームデザインを見直すための作業用仕様として扱います。

## 1. コンセプト

TacZ の銃撃戦を中心にした、階層進行型ローグライクModです。

プレイヤーはロビーで装備・ショップ・クエストを整え、ローグライク専用ディメンションのフロアを攻略します。敵を倒してゴールドとドロップを得て、フロアクリア時やショップ購入でパークを増やしながら、5層ごとのボス階層を突破していきます。

## 2. 基本ループ

1. ロビーに入る
2. 初期ロードアウトを選ぶ
3. NPCからクエスト、ショップ、フロア情報を確認する
4. フロアへ突入する
5. 生成された部屋を探索し、敵を殲滅する
6. フロアクリア判定後、脱出NPCまたはクリア画面から次の行動を選ぶ
7. パーク、ゴールド、ドロップ、ショップ強化で戦力を伸ばす
8. 5層ごとのボスを攻略する
9. 死亡時は難易度に応じたペナルティを受け、ロビーへ戻る

## 3. ロビー

ロビーは専用ディメンションに生成される拠点です。

主な役割:
- 初期装備選択
- NPCとの会話
- ショップ利用
- クエスト確認
- フロア選択
- 回復
- スタッシュ利用

NPC:
- Commander: 現在チャプターのクエスト一覧を開く
- Quartermaster: ショップを開く
- Intelligence Officer: ロビーではフロア情報・フロア選択を開く。ダンジョン内ではクリア済みフロアからの脱出処理を行う
- Field Medic: HPとスタミナを全回復する

ロビー内ではTacZの発砲が禁止されます。NPCは無敵、AI停止、常時表示名ありです。

## 4. ランとフロア進行

ラン状態はプレイヤーごとに管理されます。

管理データ:
- 現在階層
- 到達済み最大階層
- ラン中かどうか
- 現在フロアがクリア済みか
- ランシード
- 現在テーマ名
- 弾薬容量強化レベル
- プレイヤーごとのダンジョン原点
- フロア開始tick

フロア開始:
- 初回開始時は1層から始まる
- 初回開始時に初期ゴールド100Gを得る
- 次のフロアへ進むと現在階層が+1される
- 到達済み最大階層が更新される

リトライ:
- 未クリアの現在フロアを再生成して再挑戦できる
- 同じフロアに他プレイヤーがいる場合は再生成せず、スポーン地点へのテレポートに留める

過去フロア:
- 到達済み最大階層以下のフロアへ移動可能
- 過去フロアはファーミング扱い

マルチプレイ:
- プレイヤーごとにダンジョン原点をずらして管理する
- 同じ階層に複数人がいる場合は、既存フロアへの合流を優先する

## 5. フロア生成

フロアは `MapGenerator` により毎回生成されます。

生成仕様:
- グリッドベースの部屋生成
- レイアウトパターンは `SCATTER`, `LINEAR`, `RING`, `GRID`, `BRANCH`, `HYBRID`
- ボス階層は `RING` レイアウト固定
- 部屋、通路、壁、天井、装飾、照明をテーマに応じて配置
- 一部の部屋は暗所化される
- スポーン部屋には安全化処理が入る
- 生成範囲外周に壁を配置する

テーマ:
- 9系統のバイオームテーマ
- 各バイオームに5種類のバリエーション
- 合計45テーマ

バイオーム系統:
- RUINS
- LAB
- UNDERGROUND
- MILITARY
- NETHER
- OCEAN
- URBAN
- TEMPLE
- VOID

バイオームは5階層単位で切り替わります。ワールドシードによりバイオーム順がシャッフルされ、各階層内のバリエーションもシードと階層から決まります。

## 6. 敵システム

通常フロアでは、部屋ごとに敵がスポーンします。

敵数:
- 基本数は `1 + sqrt(floor)` を上限10で計算
- プレイヤー人数を掛ける

通常敵:
- バイオームごとのMobプールから選ばれる
- ゾンビ、ピリジャー、スケルトン、蜘蛛、ヴィンディケーター、ブレイズ、エンダーマンなどを使用
- 遠距離Mobには弓やクロスボウを補助装備させる
- 蜘蛛系は壁登りを制限し、ダンジョン構造に引っかかりにくくする
- スライム系は小〜中サイズに制限する

Tac Rogue専用バリアント:
- 現在は独自Entity登録ではなく、バニラEntityをベースにした専用敵として実装
- 出現率は `20% + (floor - 1) * 1.5%`、最大55%
- 通常敵に混ざって出現する
- 専用名、タグ、能力補正、装備、状態異常を持つ

実装済みバリアント:
- Ash Stalker: 1層から。Huskベース。高速・火炎耐性・石剣
- Rift Marauder: 3層から。Zombieベース。やや硬い近接兵・鉄剣・革防具
- Plague Crawler: 5層から。Cave Spiderベース。高速の毒系接近役
- Iron Bruiser: 8層から。Vindicatorベース。高HP・防御・鉄斧・鉄防具
- Rift Gunner: 10層から。Pillagerベース。遠距離火力役・クロスボウ
- Void Wraith: 15層から。Wither Skeletonベース。透明・耐性・高火力

敵AI:
- 通常のターゲットゴールを削除し、専用の視界ベースターゲットゴールを追加する
- 被弾時は周囲へ警戒を共有する
- 銃声で周囲の敵が警戒する
- サプレッサー付き銃は警戒範囲が縮小される
- スノーボールはデコイとして機能し、未発見状態の敵を誘導できる

## 7. ボス階層

5層ごとにボス階層になります。

仕様:
- ボス階層はリング型レイアウト
- 中央にボス、外周側にプレイヤーがスポーンする
- 周辺部屋には雑魚敵が少数配置される
- ボスは常時発光する
- ボス撃破はクエスト進行対象
- 到達済み最大階層での初回ボス撃破時、1000Gボーナスと武器ドロップが発生する

バイオーム別ボスベース:
- RUINS: Ravager
- LAB: Iron Golem
- UNDERGROUND: Warden
- MILITARY: Ravager
- NETHER: Wither
- OCEAN: Drowned
- URBAN: Ravager
- TEMPLE: Evoker
- VOID: Warden

ボス表示名:
- RUINED OVERLORD
- BIO HAZARD ALPHA
- DEEP CORE GUARDIAN
- COMMANDER IRON
- NETHER LORD
- LEVIATHAN
- URBAN PREDATOR
- ELDER SENTINEL
- VOID HARBINGER

## 8. スケーリング

通常敵:
- 階層に応じてHPと攻撃力が上昇する
- 難易度補正がHPと攻撃力に乗る
- プレフィックスにより追加補正が入る

通常敵HP倍率:
- `1.0 + (floor - 1) * 0.10 + (floor - 1)^2 * 0.0005`

通常敵攻撃倍率:
- `1.0 + (floor - 1) * 0.05 + (floor - 1)^2 * 0.0005`

プレフィックス:
- Normal
- Swift
- Tanky
- Lethal
- Armored
- Fiery
- Toxic
- Ghostly
- Elite
- Ancient
- Berserk

特殊プレフィックス出現率:
- `10% + (floor - 1) * 1%`
- 最大95%

ボス:
- HP倍率は `5.0 + (floor - 1) * 0.3 + (floor - 1)^2 * 0.005`
- 攻撃倍率は `3.0 + (floor - 1) * 0.15 + (floor - 1)^2 * 0.002`
- 移動速度は抑制される
- 防御力は `10.0 + floor * 0.5`
- Witherは別途速度を低めに調整
- LEVIATHANは水辺以外を想定してHPと速度が補強される

## 9. 難易度

難易度はワールド単位で保存されます。

難易度:
- EASY
- NORMAL
- HARD
- EXTREME

補正:
- EASY: 敵HP 0.7倍、敵攻撃 0.8倍、ゴールド 1.5倍、ドロップ 1.3倍、死亡ペナルティなし
- NORMAL: 敵HP 1.0倍、敵攻撃 1.0倍、ゴールド 1.0倍、ドロップ 1.0倍、死亡ペナルティ5%
- HARD: 敵HP 1.5倍、敵攻撃 1.2倍、ゴールド 0.8倍、ドロップ 0.8倍、死亡ペナルティ15%
- EXTREME: 敵HP 2.0倍、敵攻撃 1.5倍、ゴールド 0.7倍、ドロップ 0.7倍、死亡ペナルティ20%

死亡時追加ペナルティ:
- HARD: ランダムにパーク1個喪失
- EXTREME: ランダムにパーク3個喪失、武器・弾薬など保護スロット以外の一部アイテム喪失

## 10. 初期ロードアウト

ロビーで初期ロードアウトを選びます。選択後、初期パーク選択画面が開きます。

Balanced:
- HP 30
- 防御 4
- スタミナ 100 * 2
- 速度補正なし
- Glock 17
- 9mm弾 192
- マガジン17

Power:
- HP 24
- 防御 2
- スタミナ 80 * 2
- 速度 -5%
- Deagle
- 50AE弾 64
- マガジン7

Classic:
- HP 34
- 防御 3
- スタミナ 120 * 2
- 速度 +5%
- M1911
- 45ACP弾 128
- マガジン7

共通初期支給:
- 近接武器
- Medkit x3
- Field ration相当のステーキ x4
- Snowball x16
- 選択銃に対応する弾薬

難易度によるHP補正:
- EASY: 初期HP 1.5倍
- NORMAL: 等倍
- HARD: 0.9倍
- EXTREME: 0.5倍

## 11. パーク

パークはプレイヤータグとして保存されます。

獲得タイミング:
- 初期ロードアウト選択後
- フロアクリア時
- ボスフロアクリア時
- ショップのランダムパーク購入
- 隠し部屋用の生成処理

選択肢:
- 通常は3択
- 初期パークはLv1〜2、修飾子なし
- 通常フロアは階層に応じて最大Lvが上がる
- ボスフロアは高品質修飾子が出やすい

カテゴリ:
- Vitality
- Regeneration
- Armor
- Velocity
- Stamina
- Damage
- Gun Proficiency
- Autoloader
- Ammo Saver
- Scavenger
- Gold Rush
- Blood Harvest
- Explosive Rounds
- Resistance
- Fortune
- Handling
- Sharpshooter
- Executioner
- Dodge
- Adrenaline
- Bloodlust
- Medic
- Head Hunter
- Stealth Extend
- Quick Fix

修飾子:
- None
- Radiant
- Blessed
- Primal
- Reinforced
- Fractured
- Cursed
- Overclocked
- Volatile
- Corrupted
- Titanic

効果値:
- `level * 10 * modifierMultiplier`
- 一部の回復系は表示・実効果上で10分の1換算される

制限:
- Overclockedは最大2個
- Cursedは最大8個

主な効果適用:
- 最大HP、防御、移動速度、スタミナ
- 銃ダメージ、近接ダメージ、爆発ダメージ
- クリティカル、ヘッドショット、距離ダメージ、処刑ダメージ
- 弾薬消費軽減
- 自動装填、リロード支援
- 回復強化、吸血、キル時回復
- ドロップ率、ゴールド獲得量
- 回避、耐性、ステルス範囲

## 12. クエスト

クエストはチャプター制です。

仕様:
- 全20チャプター
- 各チャプターに10クエスト
- うち1つは固定ストーリークエスト
- 残り9つはチャプターごとの通常クエスト
- チャプター内の全クエスト完了で次チャプターへ進む
- 20チャプター完了後はNG+へ移行し、チャプター1から再開する

クエスト種別:
- Kill Count
- Floor Clear
- Headshot
- Stealth Kill
- Boss Kill
- Gold Earn
- Survive
- No Damage
- Speedrun
- Weapon Mastery

報酬:
- クエスト完了時にゴールドを獲得
- ストーリークエストはチャプターが進むほど高報酬
- 通常クエストもチャプターとクエスト番号で報酬が増加

現在の進行更新箇所:
- 敵撃破
- ヘッドショット撃破
- ステルスキル
- ボス撃破
- フロアクリア
- ゴールド獲得
- 条件付きクリア系

## 13. 経済・報酬

通貨はゴールドです。

初期ゴールド:
- 100G

キル報酬:
- `20 + floor * 3`
- 最大150G
- Gold Rushの効果で増加
- 難易度のゴールド倍率が乗る

ヘッドショット撃破:
- キル報酬の30%分が追加される

ボス初回撃破:
- 最大到達階層のボス初回撃破時に1000G

ドロップ:
- 基本ドロップ率8%
- 難易度倍率とScavengerで補正

ドロップ種別:
- 武器
- アタッチメント
- Emergency Ration
- Medkit
- Field Ration
- Gold Cache
- Stamina Boost
- Scrap Metal

武器ドロップ:
- 階層に応じて出現カテゴリが広がる
- 1〜5層: Pistol, SMG, Melee
- 6〜15層: Shotgun追加
- 16〜30層: Rifle追加
- 31〜50層: LMG追加
- 51層以降: 全カテゴリ
- レアリティが付与される
- 階層に応じてアタッチメント付きで落ちる可能性がある

売却:
- Scrap Metal: 50G
- Gold Cache: 250G
- 銃: 購入価格の40%
- 弾薬: 1発1G
- アタッチメント: 50G

## 14. ショップ

ショップはQuartermasterから開きます。

カテゴリ:
- Pistol
- Rifle
- SMG
- Shotgun
- Sniper
- LMG
- Explosive
- Melee
- Tactical
- Attachment
- Ammo
- Special

購入可能:
- TacZ銃
- TacZ弾薬
- TacZアタッチメント
- LR Tactical近接武器
- LR Tactical投擲・戦術アイテム
- 回復アイテム
- スタッシュ拡張
- インベントリ拡張
- 弾薬容量拡張
- 近接武器強化
- ランダムパーク

Special商品:
- Inventory +2 Slots
- Stash +1 Row
- Ammo Pouch Level Up
- Melee Weapon Level Up
- Medkit
- Field Ration x3
- Stamina Shot
- Snowball x16
- Bandage
- Armor Plate
- Adrenaline Syringe
- EMP Device
- Random Perk

購入配置:
- 専用スロットに配置可能なものは専用スロットへ
- 空きがなければ拡張インベントリへ
- さらに空きがなければスタッシュへ
- 失敗時は返金処理

## 15. インベントリ・スタッシュ

専用スロット:
- 銃: 0〜1
- 近接: 2
- アイテム: 3〜8
- 弾薬: 9〜12

拡張インベントリ:
- インベントリ拡張1レベルごとに+2スロット
- 最大レベル12
- 実質的に通常インベントリ後半を段階解放する

弾薬容量:
- Ammo Pouch強化で弾薬容量を拡張
- 最大レベル5

近接強化:
- Melee強化で近接ダメージ倍率を伸ばす
- 最大レベル5
- 1レベルごとに+50%相当

スタッシュ:
- プレイヤーごとに保存される
- 初期2行想定
- 最大6行
- 拡張購入で1行ずつ解放

## 16. 戦闘・ステルス

プレイヤーは常時Adventureモードに固定されます。

銃撃:
- TacZイベントを利用して銃ダメージを補正する
- Damage, Fortune, Gun Proficiency, Handling, Sharpshooter, Executionerなどが影響
- Head Hunterはヘッドショット倍率に影響
- Shotgun系は連続ヒットの無敵時間を抑制する
- Witherの弾無効化を貫通する特殊処理がある

近接:
- LR Tacticalの近接武器に対応
- Daggerは基礎倍率0.5
- Melee強化でダメージが上がる
- 背後から未発見の敵を近接攻撃するとステルステイクダウンになる

ステルス:
- スニーク中は敵の検知範囲が0.5倍
- クロール中は敵の検知範囲が0.3倍
- Stealth Extendでさらに検知範囲を下げる
- 敵の正面視界外からのターゲット取得はキャンセルされる
- 銃声、被弾、デコイで敵は警戒状態になる

回復:
- バニラ自然回復は無効化
- 独自自然回復を使用
- ダメージ後100tickは自然回復停止
- スタミナが一定以上あると回復する
- RegenerationとQuick Fixが回復に関与
- Medicは回復量を増加させる

スタミナ:
- 基本最大200
- ダッシュなどで消費
- tickごとに自然回復
- Adrenalineでスタミナ処理に補正

## 17. フロアクリア・脱出

フロアクリア条件:
- ローグディメンション内で敵がいなくなることを基準に判定
- 判定範囲はフロア中心から半径250
- 判定間隔は10tick

クリア後:
- フロアクリア画面を開く
- 次のフロアへ進む
- ロビーへ戻る
- 過去フロアの場合はファーミング扱いになる

ダンジョン内のIntelligence Officer:
- クリア済みフロアで話しかけるとクリア画面を開く
- その時点でラン状態を一時的に非アクティブへ移す

## 18. 死亡・失敗

ローグディメンションで死亡した場合:
- 死亡をキャンセルしてHPを最大まで戻す
- 難易度に応じてゴールドを失う
- HARD以上ではパークを失う
- EXTREMEでは一部アイテムを失う
- ロビーへテレポートする
- ラン状態は非アクティブになる

奈落落下:
- Y座標が -10 未満になるとロビーへ戻る
- HPは最大まで回復する

## 19. UI・HUD

主な画面:
- Welcome画面
- 初期ロードアウト選択
- パーク選択
- フロアクリア画面
- ローグインベントリ
- ショップ
- スタッシュ
- フロア選択

HUD:
- 現在階層
- テーマ
- ゴールド
- スタミナ
- パーク
- 通知
- ダメージ表示
- ドロップ表示
- ボスバー

クライアント同期:
- `SyncDataMessage` による文字列プロトコルが多く残っている
- 一部は型付きパケットも存在する
- 今後は型付きパケットへ移行する余地がある

## 20. 現在の設計上の注意点

現状の強み:
- ローグライクの基本ループは一通り成立している
- パーク、クエスト、ショップ、ドロップ、ボス、ステルスが接続済み
- TacZ連携は銃撃、ヘッドショット、弾薬、アタッチメントまで対応している
- マルチプレイ向けのフロア分離処理がある

見直し候補:
- `SyncDataMessage` の用途が広いため、型付きパケットへ段階移行したい
- パークがプレイヤータグ管理なので、検証・重複制御・保存境界を強化したい
- クエスト条件の一部は進行更新箇所をさらに明確化したい
- コメントの文字化けがまだ多く、重要ファイルから順に整理したい
- オリジナルmobは現状バニラEntityベースなので、見た目の固有化や独自Entity化は次の検討事項
- ボスの個性は名前とベースEntity中心なので、フェーズ制・特殊行動・専用報酬を追加する余地がある
- ショップとドロップの武器カテゴリ解放は、実プレイの弾薬供給量と合わせて再調整したい

## 21. 次に決めたいゲームデザイン項目

優先して再検討したい項目:
- 1ランの想定到達階層
- 1フロアの平均攻略時間
- 通常敵と専用バリアントの比率
- 5層ごとのボスの難易度上昇幅
- パーク獲得頻度
- ショップ価格とキル報酬の釣り合い
- クエストを必須進行にするか、任意報酬にするか
- ステルスを主軸にするか、銃撃戦の補助に留めるか
- 独自mobを見た目だけ固有化するか、完全な独自Entityにするか
- エンド目標を20チャプター、特定階層、NG+周回のどれに寄せるか

## 22. Stage 2 implementation notes

Act-aware shop stock has been added.

Current shop stock rules:
- Ammo and Special items are always available to avoid soft-locking recovery, upgrades, and ammo supply.
- Weapons and attachments are filtered by the current shop floor.
- The shop floor is the current floor, or the next floor when the current floor is already cleared.
- Early shop access favors pistols, SMGs, melee, tactical items, and attachments.
- Shotguns unlock from floor 4.
- Rifles unlock from floor 8.
- Snipers unlock from floor 10.
- LMGs unlock from floor 15.
- Explosive weapons unlock from floor 20.
- Act phases bias stock availability:
  - Scout favors pistol, SMG, sniper, melee, and tactical items.
  - Supply favors attachments, ammo, special items, and tactical items.
  - Elite favors rifle, shotgun, LMG, and explosive items.
  - Danger favors attachments, special items, shotgun, and melee.
  - Boss favors rifle, sniper, LMG, explosive items, and attachments.

Implementation notes:
- `ShopStockManager` owns deterministic stock filtering.
- `RogueInventoryScreen` uses the same stock filter for visible shop items.
- `ShopService` validates purchases against the same stock filter server-side.
- This keeps client display and server authority aligned without adding a new shop sync packet.

## 23. Stage 3 implementation notes

The quest system has been reworked into role-based quest lanes.

Current quest roles:
- Story: required chapter progression quest.
- Contract: Act-aware tactical objective.
- Bounty: combat-focused optional objective.
- Supply: economy, recovery, survival, or preparation objective.

Chapter progression now requires:
- The Story quest for the current chapter is completed.
- At least 2 non-story quests in the current chapter are completed.

This means players no longer need to complete every generated quest in a chapter.
Side quests still matter, but the player can choose which objectives fit their run.

Quest generation is now shaped by the 5-floor Act rhythm:
- Scout favors floor clear, speed, stealth, and headshot goals.
- Supply favors gold, survival, floor clear, and preparation goals.
- Elite favors kill count, headshot, weapon mastery, and direct combat.
- Danger favors no-damage, survival, stealth, and careful play.
- Boss favors boss kills, weapon mastery, survival, and high-value combat rewards.

Rare weapon reward rules:
- One generated quest per chapter is marked as a rare weapon reward quest.
- Completing it grants gold and attempts to grant a rare weapon.
- The reward uses the same item creation path as shop items.
- If weapon creation fails, quest completion and gold still apply.
- If weapon slots are full, the reward goes to stash; if stash is full, it drops nearby.

Implementation notes:
- `QuestManager` owns quest roles, Act-aware quest generation, progression checks, and rare weapon selection.
- `NpcManager` sends quest role and rare weapon metadata through the existing quest payload.
- `RogueInventoryScreen` remains the quest UI and now displays role labels plus rare weapon reward hints.
- Existing 6-field quest payloads remain tolerated client-side.

## 24. Stage 4 implementation notes

Weapon rarity application has been unified across weapon acquisition paths.

Current rarity rules:
- Starter loadout guns are always Common.
- Shop-purchased guns receive deterministic shop rarity based on shop floor and item id.
- Dropped guns keep floor-random rarity.
- Rare weapon quest rewards force at least Rare.

Rarity display:
- Weapon names now use ASCII-safe rarity labels:
  - `[C] Common`
  - `[U] Uncommon`
  - `[R] Rare`
  - `[E] Epic`
  - `[L] Legendary`
- This avoids mojibake-prone star characters while keeping rarity color styling.

Implemented effects:
- Damage multiplier is applied in TacZ gun damage handling.
- Magazine multiplier is applied to created gun ammo count and effective reload/autoloader caps.
- Ammo-efficiency refill caps also use rarity-adjusted magazine size.

Known limitation:
- `RogueReloadMult` is still stored in weapon NBT, but no stable TacZ reload-duration hook is currently wired.
- Reload rarity behavior is reserved for a later pass when a safe TacZ reload API/event path is confirmed.

Implementation notes:
- `WeaponRarity` owns rarity roll, shop rarity, minimum rarity, display, and effective magazine helpers.
- `RogueItemFactory` creates rarity-aware guns for starter, shop, drop, and quest reward paths.
- `GearService` now uses `RogueItemFactory` for starter guns.
- `ShopService` now uses shop-floor-aware item creation for purchases.
- `QuestManager` uses reward weapon creation with a minimum Rare rarity.
