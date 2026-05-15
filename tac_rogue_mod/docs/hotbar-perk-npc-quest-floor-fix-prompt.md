# Hotbar / Perk / NPC / Quest / Floor Progression Fix Prompt

## Goal

前回追加した専用UIとフロア再訪機能の不具合を修正する。プレイヤー操作としては、拡張ホットバーの E1-E3 をスクロールで選択でき、TacZ のインタラクトキーでも NPC メニューが開き、クエストGUIは画面外にはみ出さず、過去フロア再訪後も最高到達フロアの次へ進める状態にする。

## Scope

### 1. Hotbar E1-E3 Scroll Selection

- 現在の表示仕様は維持する。
  - slot 0-1: Gun
  - slot 2: Melee
  - slot 3-8: normal item
  - slot 9-11: E1-E3
- ローグディメンション中、またはラン進行中はマウスホイール選択を `0-11` の範囲に拡張する。
- バニラの `0-8` 循環に任せず、ローグ中だけ専用のスクロール処理で `player.getInventory().selected` を `0-11` に変更する。
- GUI画面表示中はこの処理を走らせない。
- クリエイティブやローグ外の通常操作は壊さない。

### 2. Perk Modifier Description Wrapping

- `PerkScreen` と `FloorClearScreen` の両方を対象にする。
- 日本語の修飾子説明は、句読点で改行候補を作ってから `font.split` に通す。
- `FloorClearScreen` は現在 `drawString("⚠ " + tradeoffText)` で1行固定になっているため、ここを複数行描画に変更する。
- `PerkScreen` 側もカード幅に対してやや強めに折り返す。
- 文字がカード外へ出る場合は最大行数で切り、下部の選択ボタンと重ならないようにする。

### 3. TacZ Interact Key for NPC

- 既存の通常右クリックNPCインタラクトは維持する。
- TacZ の `key.tacz.interact.desc` / `key.tacz.interact` に割り当てられたキーが押されたとき、クライアント側で視線先の `TacRogueNpcEntity` を検出する。
- NPCを検出した場合、新しいC2Sパケットで対象 entity id をサーバーへ送り、サーバー側で `NpcManager.handleCustomNpcInteraction` を呼ぶ。
- サーバー側では以下を検証する。
  - entity id が `TacRogueNpcEntity`
  - プレイヤーとの距離が近い
  - プレイヤーとNPCが同じディメンション
- 検証に失敗した場合は何もしない。
- TacZ の銃やアイテムを持っていても NPC メニューを開けるようにする。

### 4. Quest GUI Overflow

- `QuestScreen` の最小サイズ固定をやめ、画面サイズに応じて縮むレイアウトにする。
- 低解像度時は以下の優先順位で崩れを防ぐ。
  - 左サマリーを狭くする、または非表示相当に短くする
  - クエスト一覧を優先して表示
  - 右詳細パネルを最小幅まで縮める
- `panelW = Math.min(...); panelW = Math.max(...)` のような、画面幅より大きく戻す処理は使わない。
- 報酬欄も右パネル内に収め、はみ出す場合は文言を短くする。

### 5. Floor Progression After Revisiting Old Floors

- 現状の問題:
  - 2層クリア後にロビーへ戻る
  - 1層へ再訪する
  - 1層をクリアすると `currentFloor` が1のため、次フロア処理が2層へ戻ってしまい、3層が解放されない
- 修正方針:
  - 再訪フロアのクリア後に「次へ進む」場合、`maxReachedFloor` の次のフロアへ進む。
  - `currentFloor < maxReachedFloor` かつ `floorCleared == true` の場合は、`currentFloor + 1` ではなく `maxReachedFloor + 1` を開始する。
  - 新規到達時だけ `maxReachedFloor` を更新する。
- 過去フロア再訪自体は維持する。
- 金策・周回用途として、過去フロアクリアが破綻しないよう `isFarming` 判定も維持する。

## Non Goals

- ホットバーの枠数やスロット番号の再設計はしない。
- TacZ のキー設定自体は変更しない。
- クエスト内容、報酬、難易度バランスは変えない。
- フロア選択GUIの大規模な見た目改修はしない。

## Validation

- `.\gradlew.bat build` が成功すること。
- ローグ中にマウスホイールで slot 0-11 を循環でき、E1-E3 を選択できること。
- 日本語表示の `PerkScreen` / `FloorClearScreen` で修飾子説明がカード外へはみ出さないこと。
- TacZ インタラクトキーで NPC メニューを開けること。
- 小さめの解像度でも `QuestScreen` が画面外にはみ出さないこと。
- 2層クリア後に1層再訪、1層クリア後に「次へ」を押すと3層へ進めること。
- jar を Modrinth プロファイルへ配置し、ハッシュ一致を確認すること。
