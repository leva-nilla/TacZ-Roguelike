# リロード同期・ステータスタブ・三人称クロスヘア修正仕様

## Summary
- レアリティによるリロード短縮は「実リロード時間」と「TacZリロードアニメーション進行」を同じ倍率で短縮する。
- ステータスタブは一番下だけの部分スクロールを廃止し、タブ内容全体を縦スクロールにする。
- 三人称クロスヘアはプレイヤー視線ではなく三人称カメラ/LeaWindsの実照準点を基準に描画し、射撃方向同期も1フレーム遅れないようにする。

## Key Changes
- TacZ本体は直接改変しない。
- 既存のサーバー側 `MixinLivingEntityReload` は維持し、弾が装填される実時間は現状通り短縮。
- 新規クライアントMixinで `ObjectAnimationRunner` の `reload_*` 系アニメーションだけ進行デルタを `1 / RogueReloadMult` 倍にする。
- `RogueInventoryScreen` のSTATUS描画を固定座標から「スクロール可能なコンテンツ領域」に変更する。
- クロスヘアの着弾点計算は `LeaWinds CAMERA_AGENT hitResult`、カメラレイキャスト、既存fallback の順にする。
- `LeaWindsCompat.syncThirdPersonGunAim()` はRenderTick START側にも寄せ、横移動中も射撃直前のプレイヤーyaw/pitchがカメラ照準点へ追従するようにする。

## Test Plan
- `.\gradlew.bat build` が成功すること。
- Modrinthプロファイルへjarを配置して起動確認する。
- RARE/EPIC/LEGENDARY武器で、弾の装填完了とリロードアニメーション終端が大きくズレないこと。
- 小さめ解像度でもSTATUSタブの上から下までスクロールで全項目が読めること。
- 三人称の静止、前後移動、左右ストレイフ中にクロスヘア位置と着弾方向が一致すること。

## Assumptions
- リロード同期方針は「アニメーションも短縮」。
- TacZ本体jarの直接改変はしない。
- 三人称クロスヘアは見た目の滑らかさより、弾が飛ぶ方向との一致を優先する。
