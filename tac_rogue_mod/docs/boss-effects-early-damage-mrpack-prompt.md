# Boss Effects / Early Damage Balance / Mrpack Prompt

## Summary
- ボス能力に視覚/音のエフェクトを追加して、発動が分かるようにする。
- 序盤の敵攻撃力が強すぎないか確認し、必要な範囲で1-4層の攻撃力を抑える。
- 友人テスト用に最新jar入りの `.mrpack` を作成する。
- 想定プレイ階層は100層以上なので、後半の伸びは崩さず序盤事故だけを抑える。

## Current Findings
### Boss abilities
- 5ロールすべてに能力発火経路はある。
  - `BREACHER`: Shockwave + Pressure
  - `COMMANDER`: Summon + Pressure
  - `VOID_WARDEN`: Shockwave + Pressure
  - `PYRO`: Summon + Pressure
  - `LEVIATHAN`: Shockwave + Pressure
- ただし現状は発動エフェクトが弱く、プレイヤーが「今なにをされたか」を見分けにくい。

### Early enemy damage
- 通常敵攻撃力は `ScalingEngine.applyScaling()` で以下のように増える。
  - Floor 1: `base * prefix * difficulty`
  - Floor 2: `base * 1.05 * prefix * difficulty`
  - Floor 3: `base * 1.10 * prefix * difficulty`
  - Floor 4: `base * 1.15 * prefix * difficulty`
- 1層から特殊個体が約10%出る。
- `LETHAL` は攻撃力2.0倍、`BERSERK` は2.5倍なので、序盤に出ると被弾が重すぎる可能性が高い。
- NORMAL難易度でも、序盤の事故要因として強すぎる。

## Boss Effect Implementation
### Shockwave
- 発動時にボス中心から円形に粒子を出す。
- `BREACHER`: 赤/煙/爆発寄り。
- `VOID_WARDEN`: シアン/紫のソニック風。
- `LEVIATHAN`: 青緑の水圧波風。
- 発動音:
  - 低い爆発音または金属衝撃音。

### Summon
- 召喚地点に小さな粒子柱を出す。
- `COMMANDER`: 金色/煙、指揮ビーコン風。
- `PYRO`: 火花/炎、熱源風。
- 発動音:
  - ビーコン/炎/金属音を短く鳴らす。

### Pressure
- ボス周囲に短いパルス粒子を出す。
- `PYRO`: 炎/煙。
- `COMMANDER`: 金色/白の制圧指令風。
- その他: ロール色の弱いパルス。
- 実効果が入らない壁越しでも、能力発動の予告/結果として視認できる程度にする。

## Effect Constraints
- サーバー側から `ServerLevel.sendParticles` と `playSound` で実装する。
- 発動時だけ出す。毎tick常時演出は避ける。
- 粒子数は控えめにする。
  - Shockwave: 24-40個程度
  - Summon: 8-16個程度
  - Pressure: 12-24個程度
- 低スペック環境や友人テストを考え、負荷が高い大量パーティクルは避ける。

## Early Damage Balance
- 1-4層だけ通常敵の攻撃力に追加の序盤補正を入れる。
- 推奨倍率:
  - Floor 1: 0.65x
  - Floor 2: 0.75x
  - Floor 3: 0.85x
  - Floor 4: 0.95x
  - Floor 5+: 1.00x
- 特殊個体の出現率は維持するが、序盤の `LETHAL` / `BERSERK` の即死感を抑える。
- 100層以上の長期プレイを想定し、5層以降の通常敵スケーリング式は変更しない。
- ボスの攻撃力は今回は直接下げない。
  - 5層ボスは明確な壁として残す。
  - ただしボス能力エフェクト追加後に理不尽さが見えたら別途調整する。

## Mrpack Build
- 最新mod jarをビルドする。
- `tac_rogue_modpack/overrides/mods` に最新 `tac_rogue-*.jar` をコピーする。
- `scripts/create_mrpack.ps1` を使って `.mrpack` を作成する。
- 出力:
  - `releases/TacZ_Roguelike_v<mod_version>.mrpack`
- Modrinthプロファイルにも同じjar/mrpackを配置する。

## Test Plan
- `.\gradlew.bat build` が成功すること。
- `.mrpack` が `releases` に生成されること。
- `.mrpack` のrootに `modrinth.index.json` が入っていること。
- `.mrpack` に `overrides/mods/tac_rogue-*.jar` が入っていること。
- ボス召喚デバッグで5ロールを出し、以下を確認する。
  - Shockwave発動時に円形エフェクトが出る。
  - Summon発動時に召喚地点エフェクトが出る。
  - Pressure発動時にロール色パルスが出る。
  - `Boss Info` のカウンターが増える。
- 序盤1-4層で通常敵の被弾が以前より少し軽くなること。

## Assumptions
- 友人テスト用なので、mrpackは公開アップロードせずローカル配布用に作成する。
- 序盤火力の調整は、敵HPや出現数には触れず攻撃力だけに限定する。
- ボス演出は派手さより視認性と軽さを優先する。
