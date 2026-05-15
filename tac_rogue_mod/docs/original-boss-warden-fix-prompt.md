# Original Boss / Warden Disappear Fix Prompt

## Summary
- 現在のボスに使っている Warden は、バニラAIの地中潜行/消失挙動がローグライクのボス戦と相性が悪い。
- ボスフロアの安定性を優先し、Warden を直接ボスとして使う構成を廃止する。
- 代わりに TacZ Roguelike 専用の独自ボスEntityを追加し、階層/テーマごとに能力を持たせる。

## Current Problem
- `RoomManager` のボス候補に `EntityType.WARDEN` が含まれている。
- `SpawnAndWorldHandler` では Warden の潜行を抑制しているが、20tickごとの処理なので完全ではない。
- Warden が消えると `rogue:boss` タグ持ちEntityが消失し、ボス撃破ではないのにボスフロア進行/報酬処理が崩れる可能性がある。

## Implementation Policy
- TacZ本体やバニラEntity本体は直接改変しない。
- Warden を無理に改造して固定するのではなく、ローグライク専用ボスEntityへ置換する。
- 既存のボスバー、報酬、階層進行、デバッグ機能との互換性を維持する。
- 既存の `rogue:boss` / `tac_rogue_spawned` タグ、`TacRogueSpawnFloor` などの永続データは維持する。

## New Boss Entity
- 新規Entity: `tac_rogue:boss`
- クラス案: `TacRogueBossEntity`
- `PathfinderMob` または `Monster` ベースで実装する。
- despawn防止:
  - `setPersistenceRequired()`
  - `removeWhenFarAway` を無効化
  - `checkDespawn` を実質無効化
- 基本AI:
  - プレイヤーを優先ターゲット
  - 近接攻撃
  - 視線/索敵は既存の `RogueMobVisionGoal` に寄せる
  - HurtByTargetGoal を維持

## Boss Roles
階層テーマに応じて、同じEntityの内部ロールを切り替える。

### BREACHER
- 近接圧力型。
- 能力:
  - ショックウェーブ: 周囲のプレイヤーへダメージ、ノックバック、短い鈍足。
  - 突進: 一定距離内のターゲットへ加速接近。

### COMMANDER
- 召喚/制圧型。
- 能力:
  - 増援召喚: 雑魚を少数召喚。上限数を設け、増えすぎを防ぐ。
  - 制圧指令: 周囲のプレイヤーへ短時間の弱体化/鈍足。

### VOID_WARDEN
- Wardenの代替枠。
- バニラWardenは使わず、潜行/消失しない独自ボスとして実装する。
- 能力:
  - ソニックパルス風の範囲攻撃。
  - 視線が通る相手への短時間プレッシャー効果。

### PYRO
- Nether/高階層向け。
- 能力:
  - 炎上床または短時間の炎上付与。
  - HP低下時に攻撃頻度が少し上がる。

### LEVIATHAN
- Ocean/重装型テーマ向け。
- 能力:
  - 引き寄せ/鈍足。
  - 高い耐久、低めの移動速度。

## Boss Scaling
- 既存の `ScalingEngine.applyBossScaling` を維持する。
- ロール別の追加補正は最小限にする。
- HP、攻撃力、防御、移動速度は既存バランスから大きく逸脱させない。
- 序盤ボスは能力を1-2種類に抑え、高階層ほど能力頻度を上げる。

## Spawn Changes
- `RoomManager.spawnBoss` はバニラボス候補配列ではなく、`ModEntities.TAC_ROGUE_BOSS` を生成する。
- biomeIndex/floor からロールを決定する。
- 生成後に以下を設定する:
  - `rogue:boss`
  - `tac_rogue_spawned`
  - `TacRogueSpawnFloor`
  - `TacRogueSpawnTick`
  - `setPersistenceRequired`
- 旧Wardenボスが残っている場合の保険として、Warden潜行抑制は既存処理を残す。

## Visuals
- 初回実装では、独自ボス用Renderer/Modelを追加する。
- 既存NPCの完全流用ではなく、ボス用の大柄なシルエットにする。
- テクスチャは初期版としてロール別に色/装備感が分かるものを用意する。
- 後続で精密な独自3Dモデル/高品質テクスチャへ差し替えられる構造にする。

## Rewards / Floor Clear
- ボス撃破時の既存報酬処理は維持する。
- ボスが自然消失してクリア扱いになる挙動を避ける。
- 召喚された増援は、ボス死亡時またはフロア遷移時に残留しないようにする。
- ボスフロアのクリア判定は、独自ボスの死亡を基準にする。

## Debug Support
- 既存のデバッグコマンド/GUIから、任意ロールのボスをスポーンできるようにする余地を残す。
- 今回の必須実装は通常ボスフロアの置換を優先する。

## Test Plan
- `.\gradlew.bat build` が成功すること。
- 5層/10層/15層/20層でボスが生成されること。
- Wardenがボスとして出現しないこと。
- ボスが時間経過や距離で消えないこと。
- ボスバーが表示されること。
- ボス撃破時に既存の報酬と階層進行が動作すること。
- 召喚系ボスの増援が過剰に増えないこと。
- ボス死亡後に増援が残り続けないこと。

## Assumptions
- ユーザーの「うおーでん」は Warden ボスを指す。
- ボスはバニラMob強化ではなく、TacZ Roguelike専用の独自Entityとして実装する。
- 最初の実装では能力と安定性を優先し、モデル/テクスチャは後続でさらに精密化できる構成にする。
