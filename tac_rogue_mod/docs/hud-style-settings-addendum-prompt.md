# HUD Style Settings Addendum Prompt

## Goal

HUDとホットバーの重なり解消に加えて、HUD表示のスタイル・サイズ・位置をゲーム内設定から変更できるようにする。プレイヤーのGUIスケールやミニマップ環境に合わせて、HUDを邪魔にならない位置へ調整できる状態を目指す。

## Scope

### 1. HUD Settings Storage

- 新しい設定ファイルを追加する。
  - `config/tac_rogue_hud.properties`
- 保存する項目:
  - `scale`
  - `style`
  - `position`
  - `opacity`
- 起動時または描画時に遅延ロードする。
- 設定変更時に即保存する。
- 既存ワールド・既存設定がなくてもデフォルト値で動作する。

### 2. HUD Scale

- HUD全体のサイズ倍率を変更できるようにする。
- 範囲:
  - `70%` から `120%`
- 設定UI:
  - スライダー
- デフォルト:
  - `90%`
- 小さい画面ではHUDが画面外へ出ないようにクランプする。

### 3. HUD Style

- HUDの見た目スタイルを切り替え可能にする。
- 最初は以下の3種類にする。
  - `Compact`: 小さめ、情報密度高め
  - `Tactical`: 現在の軍用HUDに近い標準
  - `Minimal`: 背景を薄くし、バーと数値中心
- 設定UI:
  - ボタンで順送り
- デフォルト:
  - `Tactical`
- 各スタイルで表示情報自体は変えない。
  - HP
  - スタミナ
  - フロア
  - テーマ
  - ゴールド

### 4. HUD Position

- HUDの配置を変更できるようにする。
- 選択肢:
  - `LEFT_BOTTOM`: 左下、ホットバーより上
  - `LEFT_MID`: 左中央寄り
  - `RIGHT_BOTTOM`: 右下、ホットバーより上
- 設定UI:
  - ボタンで順送り
- デフォルト:
  - `LEFT_BOTTOM`
- ホットバーと重ならないよう、下端位置を安全マージンで制限する。

### 5. HUD Opacity

- HUD背景の透明度を変更できるようにする。
- 範囲:
  - `35%` から `100%`
- 設定UI:
  - スライダー
- デフォルト:
  - `75%`

### 6. Settings Screen

- 既存の `PopupSettingsScreen` とは分けて、新しい `HudSettingsScreen` を追加する。
- Intel NPCメニューに `HUD設定` ボタンを追加する。
- 画面内には以下を配置する。
  - サイズスライダー
  - 透明度スライダー
  - スタイル切替ボタン
  - 位置切替ボタン
  - プレビュー表示
  - 戻るボタン
- プレビューは画面内に簡易HUDを描画する。
- 実ゲームHUDにも即時反映される。

## Integration With Existing Prompt

- `dungeon-chest-hud-popup-layout-prompt.md` のHUD重なり解消と同時に実装する。
- HUD設定のデフォルト状態でも、ホットバーとHUDが重ならないようにする。
- ポップアップ位置設定とは別管理にする。
  - ポップアップ: `tac_rogue_popup.properties`
  - HUD: `tac_rogue_hud.properties`

## Non Goals

- HUD表示項目の追加・削除はしない。
- ミニマップMODの位置を自動検出しない。
- ホットバーのサイズ設定は今回は追加しない。
- ダメージインジケーターやボスバーの設定は今回は対象外。

## Validation

- `.\gradlew.bat build` が成功すること。
- Intel NPCメニューからHUD設定画面を開けること。
- HUDサイズ、透明度、スタイル、位置がゲーム中に反映されること。
- 設定変更後、再起動しても設定が残ること。
- デフォルト設定でもHUDとホットバーが重ならないこと。
- jar を Modrinth プロファイルへ配置し、ハッシュ一致を確認すること。
