# ロビー・ポップアップ設定・スタミナ・ショップ修正 実装プロンプト

## 対象の問題

今回の修正対象は以下。

1. ロビーで一部ブロックが浮いて見える。
2. ポップアップ表示設定をIntel NPCのGUIから変更できるようにしたい。
3. ポップアップの速度、サイズなどを変更し、プレビューポップアップを出せるようにしたい。
4. NPCの顔がパッと見で見えない。UV展開が適切か確認し、修正したい。
5. ADS中にもスタミナを消費するようにしたい。
6. しゃがみ、伏せ中はスタミナ消費を軽減したい。
7. スニークをデフォルトでトグル式にしたい。
8. ステータスのstanceがstandingのままになる。
9. `TACTICAL STATE` が `JOIN FLOOR` ボタンと被っている。
10. ショップのAmmo/Specialタブが消えている。
11. ショップの互換性ハイライトを維持したい。
12. Sell/Buyを切り替えると表示が複数生成される。

## 方針

機能面の変更が多いため、以下の4領域に分けて修正する。

## 1. ロビー浮きブロック修正

対象:

- `LobbyGenerator.java`

修正方針:

- 現在のロビーは開放感を出すために、区画上部の鉄格子キャノピーや照明が支柱と繋がらず浮いて見える箇所がある。
- 各区画のキャノピーは、角と中央に支柱を追加して接続感を出す。
- 背面/側面壁上の照明は、壁または梁と繋がる位置に限定する。
- 空中単独のSea Lantern/Iron Barsを避ける。
- 外周防壁と落下防止機能は維持する。

## 2. NPC顔UV修正

対象:

- `TacRogueNpcModel.java`
- `assets/tac_rogue/textures/entity/npc/*.png`

現状の問題:

- 128x128テクスチャに顔らしいドットは描いているが、キューブUVの正面領域と完全には合っていない。
- そのため、ゲーム内では顔が横面/上面へ逃げたり、ヘルメット/装備に隠れて見える可能性がある。

修正方針:

- 頭部正面に薄い `face_plate` パーツを追加する。
- `face_plate` はUVを明示的に専用領域へ割り当て、顔パーツが必ず正面に表示されるようにする。
- 役職ごとに顔専用領域を描き分ける。
  - Commander: 太い眉、傷、階級感。
  - Quartermaster: ゴーグル、無精ひげ。
  - Intel: 青いアイウェア、通信装備。
  - Medic: 医療マスク、赤十字。
- 既存の体格差/装備差は維持する。

## 3. ポップアップ設定GUI

対象:

- `NpcMenuScreen.java`
- `NotificationManager.java`
- 必要に応じて小さなクライアント設定クラスを追加

修正方針:

- Intel NPCのロビーメニューに `Popup Settings` を追加する。
- クリックするとクライアント専用の設定画面を開く。
- 設定項目:
  - Size: 0.75x / 0.90x / 1.00x / 1.15x
  - Speed: Slow / Normal / Fast
  - Duration multiplier: Short / Normal / Long
  - Max visible popups: 1 / 2 / 3
  - Preview: 現在設定でテストポップアップを表示
- 設定はクライアント側のみで完結し、サーバーデータは汚さない。
- 永続化はまず `options.txt` ではなく、ゲームディレクトリ配下の簡易ローカル設定ファイルに保存する。
- プレビューは `NotificationManager.addPopup` を直接呼ぶ。

## 4. スタミナ/姿勢/スニーク

対象:

- `StaminaManager.java`
- `PlayerTickHandler.java`
- `ClientEventHandler.java`
- `RogueInventoryScreen.java`

修正方針:

- ADS中もスタミナを消費する。
- ADS判定はサーバーで可能な範囲を優先し、難しければTacZのサーバーAPI/リフレクションを薄く包む。
- 消費量:
  - Sprint: 既存通り 1.0/tick
  - ADS standing: 0.35/tick
  - ADS sneaking: 0.20/tick
  - ADS prone/crawling: 0.12/tick
- SprintとADSが重なった場合は高い方、または加算しすぎない範囲で最大値を使う。
- スタミナ0時はスプリント解除、ADSについては強制解除が難しければ回復停止/移動ペナルティ側に留める。
- スニークはクライアント側でデフォルトトグル式にする。
  - Shift押下でトグルON/OFF。
  - GUIを開いている時は触らない。
  - バニラ/他MODの設定と干渉しすぎる場合は、Tac Rogueディメンション限定で動かす。
- stance表示は `isShiftKeyDown` だけに頼らず、Pose/CROUCHING/SWIMMING とクライアント側トグル状態を反映する。

## 5. ステータスUIの被り修正

対象:

- `RogueInventoryScreen.java`

修正方針:

- `TACTICAL STATE` パネルの高さ/位置を見直し、下部の `JOIN FLOOR` ボタンと被らないようにする。
- 画面高さが低い場合は、Tactical Stateの表示を2行に詰めるか、パネル位置を上へ逃がす。
- ボタン表示位置も固定ではなく、パネルと最低4px以上空ける。

## 6. ショップGUI修正

対象:

- `ShopScreen.java`

修正方針:

- Ammo/Specialカテゴリが表示されない原因は、左カテゴリボタンが縦に収まらず途中でbreakしていること。
- カテゴリを縦リストではなく、上部または左側の2列/スクロール式にする。
- Ammo/Specialを必ず表示する。
- 互換性ハイライトを独立ショップ画面にも復元する。
  - Ammo: 現在装備中の銃で使う弾薬なら緑枠/チェック表示。
  - Attachment: 現在装備中の銃に対応するなら緑枠/チェック表示。
- Sell/Buy切替時にボタンが複数生成される問題は、`init()`内でウィジェットを再生成する前に `clearWidgets()` 相当の処理を入れる、または `rebuildWidgets()` を整理して重複を防ぐ。
- ページ/選択状態は切替時にリセットする。

## 受け入れ条件

- `gradlew.bat compileJava` が成功する。
- `gradlew.bat build` が成功する。
- Modrinthプロファイルへjarを反映する。
- ロビーの空中単独ブロックが目立たない。
- NPCの顔が正面から判別できる。
- Intel NPCからポップアップ設定画面を開ける。
- 設定画面からプレビューポップアップを出せる。
- ADS中にスタミナが減る。
- しゃがみ/伏せADSの消費は立ちADSより軽い。
- スニークがTac Rogue内でトグル式として使える。
- statusのstanceが実姿勢に追従する。
- tactical stateとjoin floorボタンが被らない。
- ShopScreenでAmmo/Specialが選べる。
- ShopScreenで互換性ハイライトが出る。
- Sell/Buy切替で表示やボタンが重複しない。

