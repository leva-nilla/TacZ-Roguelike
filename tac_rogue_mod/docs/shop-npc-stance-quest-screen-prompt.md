# 実装プロンプト: Shop/NPC/Stance/Quest Screen Fix

## 目的
直近のプレイ確認で出たUI/操作上の違和感を修正し、ショップ・NPC・姿勢制御・クエスト確認をそれぞれ専用の導線に整理する。

## 対象
- `ShopScreen`
- `PopupSettingsScreen`
- `NotificationManager`
- `ClientEventHandler`
- `ItemAndLifecycleHandler` / `TacRogueNpcEntity` / `NpcManager`
- `RogueInventoryScreen`
- 新規 `QuestScreen`
- 必要なら新規 `OpenQuestScreenMessage`
- `LobbyGenerator`

## 仕様

### 1. ショップカテゴリ
- ショップ左側カテゴリは2列表示をやめ、1列の縦リストにする。
- カテゴリ数が多いため、カテゴリ領域だけマウスホイールでスクロール可能にする。
- `AMMO` / `SPECIAL` を含む全カテゴリを必ず表示対象にする。
- アイテム一覧側のページ送り、Buy/Sell切替、互換性ハイライトは維持する。
- Buy/Sell切替やカテゴリ変更でボタンが複数生成されないよう、再初期化時のウィジェットクリアを維持する。

### 2. NPCエリア天井の鉄格子
- Commander / Intel / Quartermaster / Medic 各エリアの天井鉄格子が浮いて見えないよう、天井外周と格子ラインの接続を強化する。
- 鉄格子を完全撤去せず、軍事基地の軽量キャノピー表現として残す。
- 既存ロビーでも次回ログイン時に再生成されるよう、ロビー判定マーカーを更新する。

### 3. スニーク/伏せ
- 前回追加した独自スニークトグル処理はやめ、Minecraft本体の「スニーク: 切り替え」設定をデフォルトでONにする。
- Tac Rogue内に入った時点、または初回クライアントTick時に `options` へ保存して、設定画面側にも反映される状態にする。
- 伏せ状態に入ったらスニークを解除する。
- スニークに切り替えたら伏せを解除し、姿勢はスニークへ移行する。
- TacZ APIのクライアントオペレーターで伏せ状態を参照/解除できる場合はそれを使う。使えない場合はキー状態とPoseを併用して安全にフォールバックする。
- ステータス表示の `stance` は `PRONE > SNEAK > STANDING` の優先順位で実効姿勢を表示する。

### 4. TacZアイテム所持中のNPCインタラクト
- TacZ銃/弾薬/アタッチメント等を持っていても、Tac Rogue NPCを右クリックしたらNPCメニューを優先して開く。
- `TacRogueNpcEntity.mobInteract` に加え、ForgeのEntityInteract系イベントで `TacRogueNpcEntity` を高優先度に捕捉してキャンセルする。
- サーバー側でのみメニューを開き、クライアント側は成功扱いにする。

### 5. ポップアップ設定
- 現在のボタン循環式をスライダー式に変更する。
- スライダー項目:
  - サイズ: 70% - 120%
  - スライド速度: 4 - 14 tick
  - 表示時間倍率: 60% - 150%
  - 最大表示数: 1 - 5
- 変更は即時保存し、プレビューで現在値を確認できる。
- 既存の設定ファイル `config/tac_rogue_popup.properties` を継続利用する。

### 6. クエスト専用画面
- Commander NPCの「クエストを確認」から、インベントリ内QUESTタブではなく専用 `QuestScreen` を開く。
- 画面構成:
  - 左: チャプターと進行サマリー
  - 中央: クエスト一覧、スクロール対応
  - 右: 選択クエストの詳細、達成条件、報酬、レア武器報酬の有無
- 既存のクエスト同期データ形式はできるだけ再利用する。
- `RogueInventoryScreen` のQUESTタブは残してもよいが、NPC導線からは使わない。可能ならタブ自体を非表示にして導線を専用画面へ統一する。
- ショップ同様、Commander経由でしかクエスト画面を開けない状態にする。

## 検証
- `./gradlew.bat compileJava`
- `./gradlew.bat build`
- jarを `C:\Users\user\AppData\Roaming\ModrinthApp\profiles\TacZ_Roguelike_v0.5.0-beta\mods` にコピー
- 確認観点:
  - ショップカテゴリが1列スクロールで全カテゴリ表示される
  - 各NPCエリアの鉄格子が浮いて見えない
  - Minecraft設定側のスニークがトグルになっている
  - スニークと伏せが同時に残らない
  - TacZ銃を持っていてもNPCメニューが開く
  - ポップアップ設定がスライダーで調整でき、プレビューに反映される
  - Commanderから専用クエスト画面が開く
