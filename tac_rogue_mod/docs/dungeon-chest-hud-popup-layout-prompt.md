# Dungeon Chest / HUD / Popup Layout Prompt

## Goal

ダンジョン探索中の画面体験を整理する。ダンジョン内の物資チェストを専用GUIに差し替え、HUDとホットバーの重なりを解消し、ポップアップ通知の表示位置をプレイヤーが選べるようにする。

## Scope

### 1. Dungeon Supply Chest GUI

- `TacRogueLootChest` が付いたダンジョン内チェストだけを対象にする。
- バニラ `ChestScreen` を専用の `RogueSupplyChestScreen` に差し替える。
- スタッシュ用 `StashScreen` の差し替え処理とは干渉させない。
- 専用GUIの見た目:
  - タイトル: `SUPPLY CACHE`
  - 上部: チェスト内容
  - 下部: プレイヤーインベントリ
  - 左または右に小さな情報欄
    - `TACTICAL SUPPLY`
    - `Claiming this cache advances supply recovery.`
  - 暗い軍用端末風の背景、過剰に大きくしない
- 実際のアイテム移動・クリック挙動は既存の `ChestMenu` をそのまま使う。
- チェストを開いた時点で既存の `CHEST_RECOVERY` クエスト進行処理は維持する。

### 2. HUD / Hotbar Overlap Fix

- 現状:
  - `HotbarRenderer` は画面下 `screenHeight - 25` 付近に表示。
  - `HudRenderer` は左下 `screenHeight - 70` 付近に表示。
  - 解像度やGUIスケールによってHUDパネルとホットバーが近すぎる。
- 修正方針:
  - ホットバーは画面下中央のまま維持。
  - HUDはホットバーより上に逃がす。
  - 目安としてHUDパネル下端を `screenHeight - 52` より上に収める。
  - 左下パネルの高さは必要最小限にする。
  - 小さい画面でもホットバーとHUDの描画領域が重ならないようにする。
- HUD内容は変えない。

### 3. Popup Position Setting

- `NotificationManager` にポップアップ位置設定を追加する。
- 選択肢:
  - `TOP_RIGHT`: 現状に近い右上
  - `MINIMAP_UNDER`: ミニマップ下想定の右上低め
  - `ABOVE_HUD`: HUDの上、左下寄り
- `PopupSettingsScreen` に位置選択ボタンを追加する。
  - スライダーではなく、3択のボタンでよい。
  - 表示名は日本語:
    - `右上`
    - `ミニマップ下`
    - `HUD上`
- `config/tac_rogue_popup.properties` に `position` を保存する。
- プレビューポップアップは選択中の位置に出る。
- 既存のサイズ、速度、表示時間、最大表示数の設定は維持する。

## Non Goals

- チェストの中身や生成頻度は変更しない。
- チェストの報酬バランスは変更しない。
- ミニマップMODの座標を実際に検出する処理は追加しない。
  - `MINIMAP_UNDER` は「右上から少し下げた固定位置」として扱う。
- HUD情報の項目追加・削除はしない。

## Validation

- `.\gradlew.bat build` が成功すること。
- ダンジョン内物資チェストを開くと専用GUIが表示されること。
- スタッシュは引き続き `StashScreen` で表示されること。
- HUDとホットバーが重ならないこと。
- Intel端末のポップアップ設定から位置を変更できること。
- プレビューが選択した位置に表示されること。
- jar を Modrinth プロファイルへ配置し、ハッシュ一致を確認すること。
