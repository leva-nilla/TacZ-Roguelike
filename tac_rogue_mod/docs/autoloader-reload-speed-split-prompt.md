# Autoloader / Reload Speed Split Prompt

## Summary
- 現在 `RELOAD_SPEED` が「リロード速度短縮」と「自動装填」の両方を担当しているため、2つの独立パークに分割する。
- `Reload Speed` はリロード時間と TacZ リロードアニメーション短縮だけを担当する。
- `Autoloader` は時間経過による自動装填だけを担当し、表示はパーセントではなく「毎秒何発」の実効値にする。

## Key Changes
- パークカテゴリ:
  - 既存 `RELOAD_SPEED` は `Reload Speed` として扱い、リロード短縮専用にする。
  - 新規 `AUTOLOADER` を追加し、自動装填専用にする。
- 互換性:
  - 既存セーブにある `perk:RELOAD_SPEED:*` はリロード短縮パークとして維持する。
  - 既存 `RELOAD_SPEED` から自動装填効果は外れるため、過去取得済みパークの挙動は変わる。
  - 自動装填が必要な場合は新規 `AUTOLOADER` を取得する設計にする。
- 効果計算:
  - `Reload Speed`: `Lv x 10 x 修飾子倍率 %` をリロード速度ボーナスとして扱う。
    - 実効リロード倍率は `rarityReloadMult / (1 + reloadBonus / 100)`。
    - 下限は既存通り `GameConstants.MIN_EFFECTIVE_RELOAD_MULT`。
  - `Autoloader`: `Lv x 10 x 修飾子倍率` を元に `shotsPerSecond = max(1, effect / 10)` とする。
    - 例: 効果 10 = 1発/秒、30 = 3発/秒、100 = 10発/秒。
    - レアリティのリロード倍率は自動装填の弾数には反映しない。Reload Speed と Autoloader の責務を分離する。
- 表示:
  - パーク説明の `Reload Speed` は「リロード時間短縮」。
  - `Autoloader` は「毎秒 %s 発を自動装填」。
  - パーク一覧/ステータスタブでは `Autoloader` の合計を `%` ではなく `x.x 発/秒` 表示にする。
- 生成:
  - パーク候補に `AUTOLOADER` を含める。
  - Supply / Assault 系の推奨カテゴリでは `RELOAD_SPEED` と `AUTOLOADER` の両方を候補にする。
- デバッグ:
  - debug の reload info はレアリティ倍率、Reload Speed込み実効倍率、Autoloader発/秒を表示する。

## Test Plan
- `.\gradlew.bat build` が成功すること。
- パーク候補に `Reload Speed` と `Autoloader` が別々に出ること。
- `Reload Speed` のみ取得:
  - リロード実時間とアニメーションが短縮される。
  - 自動装填は発生しない。
- `Autoloader` のみ取得:
  - 手動リロード速度は変わらない。
  - 専用スロット内の銃へ時間経過で弾が装填される。
  - 表示が `+xx%` ではなく `x.x 発/秒` になる。
- 両方取得:
  - リロード短縮と自動装填が独立して同時に働く。
- 日本語/英語 lang JSON が壊れていないこと。

## Assumptions
- 既存 `RELOAD_SPEED` パークを自動的に `AUTOLOADER` へ変換しない。
- `Autoloader` はローグライクの強めの便利パークとして残すが、説明と効果単位を明確にする。
- その後のレビューは、実装完了後に以下5領域へ各3エージェントずつ割り当てる。
  - マップ生成
  - ロビー
  - パークシステム
  - ボスフロア
  - ゲームバランス
