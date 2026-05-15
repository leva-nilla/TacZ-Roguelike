# P2/P3修正 + TacZ連携強化 実装仕様書

## 目的

監査で残っているP2/P3項目と、追加で報告されたTacZ連携・UI・デバッグ機能の不足を修正する。
既存のゲーム内容は変えず、進行・報酬・操作・表示の信頼性を上げる。

## 前提

- TacZ本体jarを直接書き換えるのは避ける。
- TacZの挙動変更は、このmod側のMixin / accessor / イベントフックで実装する。
- どうしてもMixinで不可能な場合のみ、TacZ fork jarの作成を別作業として検討する。
- 通常ワールド利用は優先度低め。ただし副作用で通常HUDや通常操作を壊さない。

## 実装範囲

### 1. 残P2/P3の優先修正

- ShopScreenの狭幅GUI対策
  - detail幅が負にならないよう、列数・detail表示・panel幅を動的調整する。
  - 最低幅を下回る場合はdetailを簡略表示に切り替える。

- ポップアップ表示数の高さクランプ
  - 設定上の最大表示数だけでなく、画面高さ・HUD位置・scaleを見て実表示数を制限する。
  - 表示できない通知はキューに残す。

- FloorClear / FloorSelection系の小画面対策
  - 画面外にはみ出す固定幅を減らす。
  - 必要に応じてカード幅縮小、縦詰め、スクロールを導入する。

- BossBar複数重なり対策
  - 最寄り、または現在ターゲットに近いbossを優先して1体表示。
  - 複数表示する場合は縦スタック。

- Mob/Bossスポーン位置の安全性
  - 床あり、頭上空間あり、衝突なしを検証してからスポーンする。
  - 失敗時は周辺候補を再探索する。

### 2. TacZリロード短縮の実効化

- 現状の`GunReloadEvent`後のcountdown変更では効いていないため、TacZ内部のreload開始処理を調査する。
- 候補:
  - `LivingEntityReload`のreload countdown設定箇所へMixin。
  - `ReloadState#setCountDown`の呼び出し元へMixin。
  - reload time計算に使う`GunReloadData` / `GunReloadTime`参照箇所へMixin。
- `RogueReloadMult`を持つ銃だけ短縮する。
- パークのAutoloaderと重複して暴発しないよう、通常リロード短縮と自動装填は別処理として扱う。
- 反映確認用にdebug出力/コマンドを用意する。

### 3. ステータスタブのスクロール詳細化

- STATUSタブにスクロール可能な詳細リストを追加。
- 表示対象:
  - HP、最大HP、アーマー値
  - 推定ダメージ軽減率
  - スタミナ最大値、回復量、消費量
  - ADS消費、スニーク/伏せ時軽減
  - 自然回復量、被弾後回復停止時間
  - Dodge発動率
  - Scavenger実効ドロップ率
  - Volatile自傷/被ダメージデメリット
  - Cursedの現在ペナルティ
  - Gold倍率、Fortune、Ammo Saverなどの実効値
- パーク合算値は「内部%」ではなく、実際に起きる確率・倍率に寄せて表示する。

### 4. 自然回復の遅延修正

- 被ダメージ時刻を必ず記録する。
- 自然回復は「最後にダメージを受けてから5秒後」までは発動しない。
- TacZ銃撃、mob攻撃、環境ダメージなど、LivingHurtEvent側で共通記録する。
- 既存のRegeneration perk処理とPlayerRegenServiceを確認し、重複回復を整理する。

### 5. フラッシュライト強化をショップ販売

- SPECIALカテゴリにフラッシュライト強化を追加。
- 強化内容案:
  - Lv1: 照射距離/明るさ + small
  - Lv2: 距離/明るさ + medium
  - Lv3: 距離/明るさ + high
- 所持レベルはplayer persistent dataに保存。
- HUD/ステータスタブにも現在レベルを表示。
- 既存Dynamic Flashlight実装に反映する。

### 6. TacZインタラクトでスタッシュブロックを開く

- TacZ interact keyでNPCだけでなく、視線先の`StashBlock`も検出する。
- 通常右クリックと同じサーバー処理を通す。
- 射程はブロック操作として自然な距離に制限する。
- NPCとブロックが両方候補の場合は、より近い/視線に近いものを優先する。
- TacZ本来の「インタラクト可能」画面表示を消さない。
  - NPC/スタッシュにTacZ interactを対応させても、TacZ側の表示overlayや判定を横取りしない。
  - 必要ならTacZ interact表示に近い独自表示を補完するが、既存TacZ表示を潰す修正は避ける。

### 7. デバッグコマンド/GUI拡張

- `/rogue_admin debug ammo <ammoId> <count>`
- `/rogue_admin debug clearfloor`
- `/rogue_admin debug unclearfloor`
- `/rogue_admin debug setfloor <floor>`
- `/rogue_admin debug setmaxfloor <floor>`
- `/rogue_admin debug spawnboss`
- `/rogue_admin debug spawncache`
- `/rogue_admin debug flashlight <level>`
- `/rogue_admin debug reloadinfo`
- GUIにも以下を追加:
  - 弾薬付与
  - 現在フロアをクリア済みにする
  - 現在フロアを未クリアに戻す
  - フロア/最大到達フロア変更
  - フラッシュライトレベル変更

### 8. 三人称クロスヘア精度向上

- 現在の画面中央固定/簡易補正から、TacZの射線に近い位置へ寄せる。
- 優先実装:
  - プレイヤー視線ベクトルからray trace。
  - 銃口/カメラずれを補正できる場合はTacZ内部値を参照。
  - ヒット位置をスクリーン座標に投影し、そこにクロスヘアを描画。
- TacZ内部値が取れない場合:
  - camera entityのlook vector + projectile ray相当で近似。
  - ADS中/非ADS中で補正係数を分ける。

## 検証

- `.\gradlew.bat build --no-daemon`
- Modrinth profileへjar配置
- 確認項目:
  - レアリティreload倍率が実際に短縮される
  - 被弾直後5秒は自然回復しない
  - TacZ interact keyでNPCとスタッシュを操作できる
  - ステータスタブがスクロールできる
  - デバッグGUI/コマンドで弾薬付与、フロアクリア、フロア変更ができる
  - 小さいGUI scaleでもShop/Popup/FloorClearが破綻しない
  - 三人称クロスヘアが弾道方向に近づく

## 非対応・保留

- TacZ本体jarを直接改変して再配布する方式は今回は採用しない。
- Mixinでリロード短縮が不可能だった場合、TacZ fork jar方式を別途仕様化する。
