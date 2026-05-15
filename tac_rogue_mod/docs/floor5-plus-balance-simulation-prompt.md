# Floor 5+ Balance Simulation Prompt

## Summary
- 5層以降の敵HP/攻撃力スケーリングを、取得パーク数と武器成長に対して相対比較する。
- 想定は100層以上の長期ラン。
- 実装変更ではなく、現行コードの式を再現したシミュレーションと評価を行う。

## Inputs From Current Implementation
- 通常敵HP:
  - `(1 + 0.10f + 0.0005f^2) * difficultyHp * prefixHp`
- 通常敵攻撃力:
  - `(1 + 0.05f + 0.0005f^2) * difficultyDmg * earlyDamageMult * prefixDmg`
  - 5層以降の `earlyDamageMult` は `1.0`
- 特殊プレフィックス率:
  - `min(0.95, 0.10 + (floor - 1) * 0.01)`
- パーク:
  - フロアクリアごとに1つ取得する前提。
  - 3択から、攻撃寄り/防御寄り/混合の3方針で選択する。
  - パーク効果は `level * 10 * modifierMultiplier`。
- 武器:
  - TacZ実銃データは実行時API依存なので、ここではカテゴリ解禁 + レアリティ倍率で相対火力化する。
  - `COMMON 1.00`, `UNCOMMON 1.10`, `RARE 1.20`, `EPIC 1.35`, `LEGENDARY 1.50`

## Simulation Outputs
- 各チェックポイント階層の敵HP倍率、敵攻撃倍率。
- 期待パーク数と攻撃/防御パークの累積値。
- 武器込みの相対DPS。
- 通常敵の相対TTK指標。
- 被ダメージ圧の相対指標。
- 5層以降でプレイヤー成長が敵成長に追いつくか。

## Assumptions
- プレイヤーは大きなロスなく各階層で1パーク取得する。
- ショップやドロップで、解禁帯に応じた武器カテゴリへ適度に更新できる。
- 実銃ごとのDPS差、アタッチメント差、弾薬不足、プレイヤー命中率は別途調整対象とする。
- 今回はNORMAL難易度基準で評価する。
