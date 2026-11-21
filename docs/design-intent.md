# ゲームデザインの意図

2026-11-21 合意。実装の参照点は `0.9.7` の修正ブランチ `b9d4b2b`。**このページは意図の記録であり、実装済みの機能一覧ではない。** [現行仕様](current-spec.md) · [実装との差分・プレイテスト計画](design/validation.md)

**核は「装備やパークを育てながら、少しずつ深く潜る」こと。** 銃撃が主役だが、近接や隠密にも意味がある。補給に帰る、今の装備で続ける、過去階で立て直す、いずれも選べる。失敗や任意リセットで装備を失うことを主な面白さにはしない。

## 1. 入口・難易度・フロア

| 項目 | 合意した意図 | 現状・差分 |
| --- | --- | --- |
| ゲームの範囲 | 専用のロビーとダンジョンで完結。バニラの採掘・建築・生活は攻略の前提にしない | 専用タイトル、ワールド選択、ロビー導線、バニラ要素の制限がある |
| 初回 | 初回のみ、準備・操作・出撃から回収まで案内。スキップでき、後で見直せる | 行動や時間で進み、手動でも送れるガイドと、一覧で見直す画面がある。初見で必要な手順が伝わるかは未検証 |
| 難易度 | Easy は入門、Normal は標準、Hard／Extreme は敵強化とパーク損失、Ironman は進行リセット付きの任意挑戦 | 難易度はワールド共通。Normal は所持ゴールド5%減、銃・パークは保持。Hard／Extreme の死亡時はパーク1個／2個減。Ironman の装備保全には差分あり |
| ダンジョン | 5階ごとのボスと章内の緩急は固定。任務・部屋・遭遇はテーマに合わせて変え、同じ階の再挑戦でも違う判断が出る | 5階サイクル、テーマ別計画、試行ごとの生成がある。見た目だけでなく攻略が変わるかは未検証 |
| 通常階の任務 | 排除、防衛、回収などで最適な行動を変える。全任務を敵全滅にはしない | 排除・端末確保・地点防衛・補給回収・エリート討伐・脱出経路。1〜3階は通常の抽選では排除。初出順・頻度は調整対象 |
| ボス | 体力が多い敵で終わらせず、予兆・対処と初回報酬で章の達成感を出す | 進入ルート、専用アリーナ、役割別のボス、初回ボーナスがある。予兆の伝わり方と役割差は未検証 |

根拠: [初回ガイド](../tac_rogue_mod/src/main/java/com/levanilla/rogue/client/hud/TutorialGuideManager.java) · [難易度](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/DifficultyManager.java) · [5階サイクル](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/RogueActManager.java) · [生成計画](../tac_rogue_mod/src/main/java/com/levanilla/rogue/world/generation/DungeonPlanGenerator.java) · [任務](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/FloorObjectiveService.java) · [ボス](../tac_rogue_mod/src/main/java/com/levanilla/rogue/world/TacRogueBossEntity.java)

## 2. 戦闘と損耗

| 項目 | 合意した意図 | 現状・差分 |
| --- | --- | --- |
| 銃・近接・隠密 | 銃撃を軸に、正面戦闘でも進める。近接・隠密・陽動は初動、節弾、危険回避で有効。全ボスを近接や隠密だけで倒せることは必須にしない | 姿勢補正、視界・銃声への反応、陽動などがある。実際の有効性は難易度・部屋・任務によって確認する |
| スタミナと照準 | スタミナがない状態で構え続けると**実際にHPが減る**。疲労単独では倒れず、敵の攻撃とは区別できる専用の警告・演出を出す | 猶予後の移動低下・HP減少（下限HP1）と通常の被ダメージ通知がある。専用の警告・演出は未実装 |
| 回復と防御 | 自然回復は応急。継戦には回復用品、回復パーク、医療・補給の選択が効く | 自然回復の基礎上限は最大HPの30%。回復パーク、装甲板、消耗品とロビー医療がある。補給と自然回復の適切な比率は実測する |

根拠: [敵の警戒・聴覚](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/RogueMobAlertService.java) · [スタミナ](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/StaminaManager.java) · [自然回復](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/PlayerRegenService.java) · [装甲板](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/ArmorPlateService.java)

## 3. 出撃・帰還・連戦

| 場面 | 合意した意図 | 現状・差分 |
| --- | --- | --- |
| クリア後 | 次階直行とロビー帰還の両方を成立させる。補給へ戻った人の長期育成が大幅に遅れないこと | クリア後に進むか帰るか選べる。連戦補給は未実装 |
| 途中撤退 | 達成前でも自分で撤退できる。死亡ペナルティなしで、取得済みの品とゴールドは持ち帰る。クリア報酬・連戦補給は付かず、補給箱の消費済み回数も戻らない | クリア後の帰還導線とロビーに戻す処理はある。プレイヤー向けの明示的な途中撤退導線は未実装 |
| 通常の失敗 | Normal は階をやり直し、ゴールド5%減のみ。武器やパークは没収しない | ロビーに戻す失敗処理はある |
| 過去階への再訪 | ゴールドと通常ドロップで立て直す。主要なパーク報酬と解放は先へ進む動機にし、過去階は連戦の稼ぎに使えない | 解放済み階に再訪できる。クリアパークの再取得を抑止し、通常補給箱の回数を引き継ぐ |

### 連戦補給の試作仕様（未実装）

1. **本人にとって未クリア側の3階を連続クリア**し、ロビーに戻らず次階に到着した時点で支給。3階は試作の値で、所要時間・補給残量を見て調整する。
2. 報酬は対応弾・回復など、その場で継戦に使える補給。独占装備・永続パークは報酬にしない。
3. 公開Co-op でも本人の参加資格・クリア・直行で計上。仲間の参加や離脱だけで自分の連戦を失わない。本人の帰還・途中撤退・失敗・旧階の周回で連戦をつなげない。
4. ボスを数えるか、支給間隔、数量、満杯時の処理、階層移動中の切断、表示の細部は未決。クリア確定とパーク選択を別の出来事として扱い、同じ階や再接続で二重支給しない。

根拠: [フロア遷移](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/FloorService.java) · [回収NPC](../tac_rogue_mod/src/main/java/com/levanilla/rogue/world/NpcManager.java) · [クリア画面](../tac_rogue_mod/src/main/java/com/levanilla/rogue/client/FloorClearScreen.java) · [クリアパーク配布](../tac_rogue_mod/src/main/java/com/levanilla/rogue/networking/OpenFloorClearScreenMessage.java) · [失敗・Ironman](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/event/CombatEventHandler.java)

## 4. 装備・成長・経済

| 項目 | 合意した意図 | 現状・差分 |
| --- | --- | --- |
| 初期装備と武器 | 3種類で序盤の個性を出すだけで、後の装備選択をクラスに縛らない。古い銃も強化次第で使いたい | 各プリセットの銃と基本能力値が違い、武器は交換できる。初期能力差が後半までどう効くか、古い銃の実用性は未検証 |
| レア度・付属品・外部銃パック | 装備による選択肢を増やす。特定パックの武器・付属品を唯一の正解や必須装備にしない | レア度、改造、複数銃パック、過剰な補正に対する上限がある。銃種ごとの役割・上位互換・依存パック別の格差は実測する |
| パーク | 積み上げる喜びを残しつつ、特化先や副作用を選ぶ。極端な効果は上限などで調整 | 選択・再抽選、修飾子、系統ごとのソフト上限がある。選択が形だけになっていないかは未検証 |
| 所持枠・パッシブ | 現場への持ち込みには選択を残す。保管はロビー、銃・弾・消耗品・パッシブは戦い方に合わせて選ぶ | 銃・近接・弾薬の専用枠、バッグ拡張、スタッシュ、携行パッシブ最大3種。適用順・拾えない理由の理解しやすさは未検証 |
| 経済・NPC | 対応弾・基本回復は安定させ、武器・付属品・アップグレードの価格や在庫に選択を残す | 弾薬等の常設カテゴリと変動在庫、売買、医療、弾のまとめ買い、ロードアウト保存・復元がある。資金が尽きたときにも立て直せるかは未検証 |
| 通常補給箱 | プレイヤー別・取り出し専用。同じ生成中は残りを保持し、再生成で消すが取得回数は戻さない | 0.9.7 の修正ブランチに実装。`RECOVER_CACHE` の任務用コンテナは別扱い。2クライアントでの確認は未実施 |

根拠: [初期装備](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/GearService.java) · [武器レア度](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/WeaponRarity.java) · [パーク](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/PerkDefinition.java) · [携行アイテム](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/RogueUtilityItemService.java) · [店の在庫](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/ShopStockManager.java) · [通常補給箱](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/SupplyCacheService.java)

## 5. 公開Co-op・画面・情報

| 項目 | 合意した意図 | 現状・差分 |
| --- | --- | --- |
| ソロと協力 | 全階層・主要報酬はソロで完結。公開Co-op は別の遊び方で、人数に合わせて調整し、必須にはしない | 個別／共有インスタンスと、参加人数・途中合流による調整がある |
| 途中参加と報酬 | 途中参加は残す。初回報酬や連戦は、参加者が**任務の節目前**から居た場合に対象とする。ダメージ量で採点せず、防衛・支援役を不利にしない | ボスHP70%以下では新規合流不可。討伐報酬は参加者に配られる。報酬ごとの節目と対象の分離は未実装 |
| 妨害と離脱 | 味方への実ダメージなしを公開Co-op の標準とする。誰かが帰還・切断しても他の参加者の攻略を止めない | 離脱しても他の参加者がいればインスタンスが残る。攻撃種別すべてで味方ダメージが止まることは未確認 |
| 再接続 | 短時間で同じ攻略がまだ進行中なら、元の参加・連戦を保持して復帰できるようにする。閉じた階や任務は止めて待たない | 現行はログアウトで離脱し、ログインでロビーへ。同一攻略への復帰導線と許容時間は未実装・未決 |
| HUD・視点 | 一人称・三人称ともHP・弾・疲労・任務が読める。ボスや敵の重要な予兆は複数の伝え方を持つ。細かい通知やHUDは好みで調整できる | 専用HUD・任務マーカー・カメラ切替・表示設定はある。両視点、音を聞けない場合、色・解像度を変えた場合の読みやすさは未検証 |

根拠: [参加ルール](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/FloorEntryModeHandler.java) · [インスタンスと進行](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/FloorInstanceManager.java) · [ボス報酬](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/BossRewardService.java) · [ログイン・離脱](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/event/ItemAndLifecycleHandler.java) · [HUD](../tac_rogue_mod/src/main/java/com/levanilla/rogue/client/hud/HudRenderer.java) · [表示設定](../tac_rogue_mod/src/main/java/com/levanilla/rogue/client/IntelSettingsScreen.java)

## 6. クエスト・終盤・リセット

| 項目 | 合意した意図 | 現状・差分 |
| --- | --- | --- |
| クエスト | 階層進行とは別軸の任意目標。別の戦い方や立て直しに意味を持たせ、未達成で本編を止めない | クエストはストーリーと選択式サイドの達成で自分の章を進める。フロアの5階サイクルとは別 |
| 任意チャレンジ | 低HP縛り、無傷、速攻などは熟練者向け。通常階の解放や必須装備の条件にしない | 低HPモードの上限は最大HPの35%。クエストと深層タスクに関連条件がある |
| 100階と深層 | **100階ボス撃破が通常攻略の一区切り**。101階以降の深層は任意の長期育成。メニューが達成前に見えること自体は可 | 最高到達記録100で Deep Operations が解放され、撃破とは別判定。専用の達成演出と、深層サービスを操作可能にする時点は要確認 |
| プレステージ | 任意リセットと小さな永続恩恵。所持金・階層・パーク等をリセットしても、**取得済みの装備とスタッシュは失わせない**。深層継続も成立させる | 所持金・階層・パーク・ラン内報酬記録をリセットし、初回報酬・別枠の恩恵あり。次の装備選択で手持ちを消す経路があり、保全の意図と不一致 |
| Ironman の死亡 | 厳しさは階層・ラン内報酬記録・パークのリセットと所持金減少で出す。**装備とスタッシュは保全**する | リセット後に初期装備を選び直す経路に同じ手持ち消去のリスクがある。プレステージとは別の条件で検証する |

根拠: [クエスト](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/QuestManager.java) · [低HPモード](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/LowHealthChallengeService.java) · [深層・プレステージ](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/DeepProgressService.java) · [Ironman リセット](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/event/CombatEventHandler.java#L542-L555) · [初期装備再配布](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/GearService.java#L94-L105)
