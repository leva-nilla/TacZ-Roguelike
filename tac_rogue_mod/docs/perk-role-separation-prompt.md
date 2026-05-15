# Perk Role Separation Prompt

## Summary
- 一部パークが似た効果に見える問題を整理する。
- 特に `DAMAGE` と `GUN_PROFICIENCY` はどちらも銃ダメージ倍率になっているため、役割を分離する。
- 実装と説明文がズレているパーク説明も修正する。

## Findings
- `DAMAGE`
  - 実装: 銃/近接/非TacZダメージの全体火力を上げる。
  - 役割としては問題ない。
- `GUN_PROFICIENCY`
  - 実装: 銃ダメージ倍率。
  - `DAMAGE` と体感が近い。
  - 説明文は精度/発射速度/命中力だが、実装はほぼダメージ。
- `RESISTANCE`
  - 実装: 被ダメージ軽減。
  - 説明文は状態異常耐性になっていてズレている。
- `EXPLOSIVE`
  - 実装: 爆発ダメージ強化。
  - 説明文は範囲拡大も書いているが、範囲は変えていない。
- `HANDLING`
  - 実装: スニーク/姿勢射撃時のダメージ補助。
  - 説明文の「武器切り替え速度/ADS安定性」は未実装。

## Implementation
- `DAMAGE`
  - 全武器の基本火力として維持。
- `GUN_PROFICIENCY`
  - 銃専用の制御/精密射撃パークへ変更。
  - 胴撃ち時: 小さめの銃ダメージ補正。
  - ヘッドショット時: 追加の銃ダメージ補正 + ヘッドショット倍率補正。
  - ショットガンはペレット多段で伸びすぎるため補正を少し抑える。
- 表示/説明:
  - `GUN_PROFICIENCY` は「銃の扱い/弱点射撃補正」として説明する。
  - `RESISTANCE`, `EXPLOSIVE`, `HANDLING` の説明を実装に合わせる。
  - ステータスタブで `GUN_PROFICIENCY` を単純な `x倍率` 表示にしない。

## Test Plan
- `.\gradlew.bat build` が成功すること。
- `DAMAGE` と `GUN_PROFICIENCY` をそれぞれ付与して、ダメージ表示の伸び方が異なること。
- `GUN_PROFICIENCY` はヘッドショットでより効果が出ること。
- 日本語/英語の説明が実装内容と矛盾しないこと。
