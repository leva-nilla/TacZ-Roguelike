# TacZ Roguelike 最適化前レポート

作成日: 2026-05-15

## 目的

現在の実装内容を変えず、体感負荷・起動時間・ワールド入場直後の重さ・GUI描画負荷を下げるための事前調査レポート。
この文書は実装前の整理であり、まだ最適化コードは入れていない。

## 調査範囲

- メインmod本体: `src/main/java/com/levanilla/rogue`
- リソース: `src/main/resources`
- 起動補助mod: `tacz_startup_helper`
- modpack構成: `../tac_rogue_modpack/mods_list.txt`

## 全体評価

現状は「プレイ中の通常フレーム負荷」よりも、以下の瞬間負荷が目立ちやすい構造。

1. フロア生成・再生成時の大量ブロック更新
2. サーバーtickでのプレイヤー/インスタンス/モブ走査
3. GUI描画中のショップ・デバッグ・インベントリの再計算
4. 起動時・リソースリロード時のTacZ資産スキャン/キャッシュ破棄
5. HUD/ワールド描画での小さいが常時走る処理

優先度は `P0` から `P3`。

## P0: フロア生成・再生成の瞬間負荷

対象:

- `MapGenerator.generateRoom`
- `MapGenerator.clearPreviousDungeon`
- `FloorInstanceManager.activateInstance`

観測:

- `MapGenerator.clearPreviousDungeon` は中心から半径約79、Y方向約14ブロックを総当たりで確認している。
- 最大で約17万ブロック近い範囲をチェックし、非空気なら `setBlock(AIR)` している。
- その直後に `generateRoom` 側でも `GRID_SIZE = 160` の2Dグリッドを複数回走査し、部屋・通路・壁・装飾・照明を個別 `setBlock` している。
- フロア開始時に必ず `FloorService.clearDungeonEntities` と `MapGenerator.generateRoom` が同期実行されるため、ワールド入場直後にサーバーtickが詰まりやすい。

推奨:

- 生成済みブロック範囲をインスタンス単位で記録し、次回クリア範囲を「実際に置いたブロックだけ」に寄せる。
- `clearPreviousDungeon` は全領域総当たりではなく、前回生成時の bounding box 履歴 + チャンク単位バッチにする。
- 同一tickで全部置かず、生成を数tickに分割するオプションを用意する。
- `setBlock` 前の `clearContainerIfPresent` はコンテナ候補ブロックだけで呼ぶ。現状は通常ブロックにも毎回BlockEntity確認が入る。

期待効果:

- フロア入場直後のカクつき低減が最も大きい。
- マルチで複数インスタンスがある場合のサーバー負荷にも効く。

リスク:

- 生成途中にプレイヤーを入れると穴や未生成部屋が見える可能性がある。
- 段階生成にする場合、入場待機画面・ローディング表示との整合が必要。

## P0: プレイヤーtickのタグ走査・インベントリ検査

対象:

- `PlayerTickHandler.onPlayerTick`
- `PlayerPerkTickService.applyPerkStats`
- `InventoryRuleService.enforce`
- `StaminaManager.tick`

観測:

- 毎tick `StaminaManager.tick` が走る。
- `PlayerPerkTickService.applyPerkStats` は20tickごとだが、複数回 `player.getTags()` を全走査している。
- `InventoryRuleService.enforce` は設定間隔ごとにインベントリ全体を複数パスで走査している。
- 弾薬・専用スロット・ロック枠・スタッシュ送付が同じ enforce に詰まっていて、変更がない時も同じ検査をする。

推奨:

- パークタグを毎回文字列解析せず、プレイヤーごとの `PerkSnapshot` を作り、パーク変更時だけ再構築する。
- インベントリ制約は「拾得・クリック・スロット変更・購入・売却」時に寄せ、tick enforce は保険として低頻度化する。
- スタミナ同期は現状5tickごと差分同期で妥当。追加最適化するなら、ADS/ダッシュ中だけ同期頻度を上げ、静止時は下げる。

期待効果:

- プレイヤー人数が増えたときのスケールが良くなる。
- マルチ対応の土台として重要。

リスク:

- イベント駆動化すると、漏れた操作経路でスロット制約が崩れる可能性がある。
- 最初はtick保険を残して段階的に頻度を下げるのが安全。

## P1: FloorInstanceManager の範囲検索

対象:

- `FloorInstanceManager.tick`
- `FloorInstanceManager.isInstanceCleared`
- `FloorInstanceManager.applyMidRunJoinScaling`
- `FloorInstanceManager.destroyInstance`

観測:

- `tick` でインスタンス一覧を `List.copyOf` して複数回走査している。
- クリア判定は一定間隔ごとに `getEntitiesOfClass(Mob.class, area, predicate)` で広範囲検索。
- 途中参加補正・破棄時も範囲内エンティティ全探索。

推奨:

- インスタンスごとにスポーンしたmob UUIDを記録し、クリア判定はUUIDリストから生存確認にする。
- `destroyInstance` も全Entity検索ではなく、記録済みUUID + チェスト/ドロップUUIDで破棄する。
- `getInstancesSnapshot()` の呼び出し回数を減らし、1tick内ではローカル snapshot を共有する。

期待効果:

- 100層以上・マルチ・複数インスタンスで効く。

リスク:

- UUID追跡漏れがあるとクリア判定や破棄漏れにつながる。
- まずは記録を追加して、既存の範囲検索をフォールバックとして残すのが安全。

## P1: GUI描画中の再計算

対象:

- `RogueInventoryScreen`
- `ShopScreen`
- `DebugMenuScreen`
- `RogueWorldSelectScreen`

観測:

- `RogueInventoryScreen` は1300行規模で、ショップ・クエスト・ステータス・インベントリが混在している。
- ショップ系画面では表示アイテムリストのフィルタ・互換性判定・preview stack生成が描画/入力経路で繰り返されやすい。
- `AttachmentDatabase.isCompatible` はTacZネイティブ判定に委譲しており、GUIで大量に呼ぶと重くなりうる。

推奨:

- GUI表示用に `ShopViewModel` / `InventoryViewModel` を作り、カテゴリ・検索・所持金・装備変更時だけ再構築する。
- 互換性判定結果を `(gunId, attachmentId/ammoId)` でキャッシュする。
- preview ItemStack は毎フレーム作らず、アイテムID + rarity + floor 単位でキャッシュする。
- `RogueInventoryScreen` はタブごとの renderer/service に分割する。これは性能より保守性に効く。

期待効果:

- ショップ/スタッシュ/デバッグGUIを開いた時のFPS低下を抑えられる。
- 今後のUI調整が安全になる。

リスク:

- キャッシュ無効化漏れで価格・在庫・互換性ハイライトが古くなる可能性がある。

## P1: 起動時・リソースリロード時のキャッシュ管理

対象:

- `AttachmentDatabase`
- `TacZGunRegistry`
- `ClientReloadListener`
- `tacz_startup_helper`

観測:

- `AttachmentDatabase` はキャッシュファイルを持っているが、TacZ jar/zipの署名チェックとZip走査は初回に重い。
- `ClientReloadListener` はリソースリロード時に `TacZRegistryHelper.clearCache()` を行うため、次回GUIやショップで再スキャンが発生しやすい。
- 起動補助mod側はTacZ音声の遅延ロード・ロビー先読みの仕組みが既にある。

推奨:

- リソースリロード時に全キャッシュ破棄ではなく、言語/表示名系とTacZ資産DB系を分ける。
- `TacZRegistryHelper` のキャッシュ再構築はGUIを開いた瞬間ではなく、ロビー到着後の低予算prewarmへ寄せる。
- `AttachmentDatabase` のキャッシュファイルをmodpack overridesに含めるか、初回起動後に生成済みで使える導線を作る。
- 起動補助modのログは通常時 `debug` に落とし、計測モードだけ `info` にする。

期待効果:

- 初回またはF3+T後の「最初にショップを開いた瞬間」の待ちを減らせる。

リスク:

- TacZアドオン差し替え時に古いキャッシュを使うと互換性表示がズレる。
- jarサイズ/mtimeに加えて、modpack profile idやTacZ pack listも署名に含めると安全。

## P2: HUD/ワールド描画の小負荷

対象:

- `ClientEventHandler.onRenderHotbar`
- `DamageIndicatorRenderer`
- `DynamicLightManager`
- `NotificationManager`

観測:

- HUD描画はホットバーoverlayでまとめて呼ばれている。
- 三人称クロスヘアでは毎描画 `resolveThirdPersonAimTarget` と行列投影を行う。
- DamageIndicator は `CopyOnWriteArrayList` を使っており、追加・削除が増える戦闘中には余計なコピーが発生する。
- DynamicLight は変化検知で更新抑制されているため、現状は比較的良い。

推奨:

- `DamageIndicatorRenderer` は `ArrayDeque` + 通常ArrayList snapshot に変更する。
- 三人称クロスヘアの照準点は render tick 内で1回だけ計算し、HUD描画と射撃同期で共有する。
- 通知・ポップアップは最大表示数を厳密に制限し、古いものを早めに落とす。

期待効果:

- 銃撃戦中の小さいFPS落ちを減らせる。

リスク:

- DamageIndicatorのスレッド安全性を崩さないよう、クライアントスレッド前提を明確にする必要がある。

## P2: ログ・デバッグ処理

対象:

- `MapGenerator`
- `AttachmentDatabase`
- `RecoilDebugLogger`
- `tacz_startup_helper`

観測:

- 生成・起動補助・TacZ資産スキャンのログは開発中には有用。
- ただし配布版ではinfoログが多いとログI/Oとlatest.log肥大化につながる。

推奨:

- `tac_rogue.debug` / `taczStartupHelper.profile` のような明示フラグでログを切る。
- 通常配布では recoil-debug を完全に無効化。
- Map生成ログは1行のままなら許容。強制クリアログはdebugでも良い。

期待効果:

- 起動・長時間プレイ時のログノイズ削減。

リスク:

- 問題再現時の情報量が減るため、debug commandで一時的に有効化できる形が良い。

## P3: リソース容量

対象:

- `assets/tac_rogue/textures/entity/boss/*.png`
- `assets/tac_rogue/textures/block/stash.png`

観測:

- ボステクスチャは1枚あたり約137KBから174KB。
- 画像容量自体は大きすぎないが、今後ボス数や高解像度化が増えると読み込み・VRAMに効く。

推奨:

- 512px以上のテクスチャを増やす場合は、最終mrpack前にpngquant/oxipng相当で無劣化または軽い減色圧縮。
- 未使用テクスチャ・旧ロゴ・旧resourcepackの残骸をmrpackから除外する。

期待効果:

- 起動時間より配布容量・VRAM面で効く。

リスク:

- 圧縮で見た目が荒れる可能性があるため、ボス顔など視認性が重要な部分は目視確認が必要。

## 推奨実装順

1. P0-A: `MapGenerator` のクリア/生成処理を記録型に変更
2. P0-B: `PlayerPerkTickService` に `PerkSnapshot` を導入
3. P0-C: `InventoryRuleService` をイベント駆動寄りにし、tick保険を低頻度化
4. P1-A: `FloorInstanceManager` にmob/entity UUID追跡を追加
5. P1-B: Shop/Inventory/Debug GUIの表示リストとpreview stackをキャッシュ化
6. P1-C: TacZ資産キャッシュを「表示名」と「構造DB」に分離
7. P2-A: DamageIndicatorのコンテナをCopyOnWriteから通常構造へ変更
8. P2-B: ログを配布向けに抑制

## 実装時の安全条件

- ゲーム内容・数値バランスは変えない。
- 最初はフォールバックを残す。
- 変更ごとに `.\gradlew.bat build` を通す。
- 生成/インベントリ/ショップ/マルチインスタンスは、修正ごとにデバッグGUIで確認する。
- Modrinthプロファイルへ同期する前に、少なくとも以下を手動確認する。
  - フロア入場
  - フロア再生成
  - チェスト取得
  - ショップ売買
  - スタッシュ格納
  - Public/Solo入場
  - ボス撃破

## 結論

一番効果が大きいのはフロア生成・再生成の同期ブロック更新削減。
次にマルチ対応を見据えたプレイヤーtick/インスタンス走査の削減。
GUIと起動資産キャッシュは体感の引っかかりを減らす改善として有効。

実装はP0から順に進めるのが安全。
