# 現行仕様 — 0.9.7

基準: `0.9.7` / `adfeeaf`。以下はコードから確認した仕様で、Modrinth 公開版 0.9.5 の挙動や、すべての実機確認を保証するものではない。[版の一覧](README.md) · [レビュー](reviews/0.9.7.md)

## 出撃から帰還まで

1. **準備** — 専用タイトル・ワールド選択から入り、ロビーでチュートリアル、初期装備、NPC サービスを利用する。装備は Balanced / Power / Classic の3種から選ぶ。
2. **出撃** — 解放済みフロアを選ぶ。`SOLO` は個別の生成領域に即時出撃処理を開始し、`PUBLIC` は同フロアの共有インスタンスを使う。公開待機は標準15秒。進行中の途中参加もできるが、ボスの残りHPが70%以下なら参加できない。
3. **攻略** — 通常フロアは敵の排除、端末確保、地点防衛、補給回収、エリート討伐、脱出経路到達のいずれか。1〜3階は通常の抽選では敵排除。5階ごとにボス戦。敵のHP・攻撃力などはフロアと難易度で変わる。
4. **回収・帰還** — クリアで次のフロアが解放され、回収担当NPCが出現する。NPCの回収メニューから、条件に応じてパークを選び、次のフロアに進むかロビーに戻る。解放済みフロアの再挑戦も可能。
5. **失敗** — 攻略中の死亡は原則として死亡イベントを打ち消してロビーへ戻す。難易度に応じて所持ゴールドを失い、Hard はパーク1個、Extreme は2個を失う。Ironman はパーク全喪失と進行リセットの処理がある。

実装: [フロア遷移](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/FloorService.java) · [参加ルール](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/FloorEntryModeHandler.java) · [インスタンス管理](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/FloorInstanceManager.java) · [任務](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/FloorObjectiveService.java) · [死亡処理](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/event/CombatEventHandler.java)

## フロア生成と進行

- ダンジョンとロビーは別ディメンション。`FloorGenerationContext` にランのシード、フロア、試行情報などを渡し、`DungeonPlanGenerator` が部屋・通路を計画、`MapGenerator` がブロック配置・スポーンなどを行う。同じフロアでも再挑戦時はシードを更新する。最近の配置との重複は候補の再生成で避けるが、フォールバックでは重複しない保証はない。
- ボスフロアには開始地点からアリーナまでの進入ルートがある。通常フロアの部屋構成や任務候補はテーマによって変わる。
- 難易度はワールド単位で保存する。フロア進行と所持パークはプレイヤーの NBT、クエストとスタッシュは `SavedData` を使う。旧 scoreboard tags のパークは移行処理がある。
- `highestEverFloor` が100以上で Deep Operations を解放。101階以降の深層タスクや、装備の再抽選・固定・移植、プレステージがある。

実装: [生成計画](../tac_rogue_mod/src/main/java/com/levanilla/rogue/world/generation/DungeonPlanGenerator.java) · [ブロック配置](../tac_rogue_mod/src/main/java/com/levanilla/rogue/world/MapGenerator.java) · [進行保存](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/RunManager.java) · [パーク保存](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/PerkStorageService.java) · [深層進行](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/DeepProgressService.java)

## 装備・経済

- 敵撃破などでゴールドを得て、武器・弾薬・アタッチメントを売買する。ロビーではスタッシュ、補給、ロードアウトの保存・復元、装備枠などのアップグレードを利用できる。
- 補給箱は生成された箱ごとのシードを使って戦利品を抽選し、取得回数はプレイヤー・フロア単位で管理する。公開Co-opでの中身の分離は[レビューの確認事項](reviews/0.9.7.md#p2--public-co-op-の補給箱が同じコンテナを使う)を参照。
- 0.9.7 では携行中に効果を得るパッシブと、右クリックで使う消耗品がある。パッシブはインベントリ順に異なるIDを最大3種適用。0.9.7 の連射速度処理では上限1,200 RPMを使い、超過分の一部をダメージへ変換する。

| 内部スロット | 用途 |
| --- | --- |
| `0–1` | 銃 |
| `2` | 近接武器 |
| `3–11` | 戦闘アイテム。`9–11` は `E1–E3` で、余剰弾薬も置ける |
| `12–15` | 専用の予備弾薬枠 |
| `16–35` | アップグレードで解放するバックパック |

実装: [ショップ](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/ShopService.java) · [NPC](../tac_rogue_mod/src/main/java/com/levanilla/rogue/world/NpcManager.java) · [スロット制限](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/InventoryRuleService.java) · [ユーティリティ](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/RogueUtilityItemService.java) · [連射速度](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/TacZFireRateService.java)

## クライアントとサーバー

クライアントは専用HUD、画面、チュートリアル、カメラや入力を担当する。フロア・報酬・所持金などの状態更新はサーバー側で行う。Co-op の検証では同じビルドを両側に入れる。現行のチャンネル版 `5` は、旧版との互換性を保証しない。

実装: [クライアント](../tac_rogue_mod/src/main/java/com/levanilla/rogue/client) · [通信登録](../tac_rogue_mod/src/main/java/com/levanilla/rogue/networking/TacRogueNetworking.java)
