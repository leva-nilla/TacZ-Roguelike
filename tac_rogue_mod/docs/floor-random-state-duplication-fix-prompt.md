# Floor Randomization / Player State / Duplicate Generation Fix Prompt

## Summary
- 同じフロアを再生成してもレイアウトが固定化されている問題を直す。
- ワールド入り直し後にHP/スタミナがバニラ初期値へ戻る問題を直す。
- ロビー/ダンジョンが重複生成される問題を、生成処理の冪等性と復元順の両方から直す。
- 先に安定化を優先し、独自ボス実装はこの修正後に行う。

## Observed Problems
1. Same-floor regeneration is deterministic.
   - `MapGenerator.generateRoom()` の乱数seedが `runSeed ^ floor` に固定されている。
   - `retryCurrentFloor()` は同じ `runSeed` を使い続けるため、同じフロアは同じ構造になる。

2. HP/stamina reset after re-entering the world.
   - HP上限や移動速度などのパーク効果は `AttributeModifier` で再適用されるが、ログイン直後に反映される前の状態が見えてしまう。
   - `StaminaManager` は static map に現在値/最大値を持つため、サーバー停止やワールド入り直しでキャッシュが消える。
   - スタミナ現在値と最大値の復元処理がログイン時に明示されていない。

3. Lobby/dungeon duplicate generation.
   - ロビーは `isCurrentLobby()` の目印ブロック判定が崩れると毎回 rebuild される。
   - ダンジョンはプレイヤーの `dungeonOrigin` 復元が崩れると、過去生成領域とは別の位置へ再生成される可能性がある。
   - 生成済みフロアの世代情報がないため、どの生成が現在有効かを安全に判定しづらい。

## Implementation Policy
- 通常ワールドで遊ぶ前提は置かず、ログイン/リスポーン時はロビーへ戻す既存方針を維持する。
- ただし、既存ラン進行、ショップ、パーク、インベントリの保持は壊さない。
- ダンジョン生成のランダム性は「同じフロアを再生成した時だけ変わる」ようにする。
- 進行中のフロアを再ログインしただけでは、勝手に新しいレイアウトへ切り替えない。

## Floor Randomization Fix
- `PlayerRunData` に現在フロアの生成世代/seed saltを追加する。
  - 例: `floorGeneration` または `floorSeedSalt`
- `MapGenerator.generateRoom()` のseedを以下のようにする。
  - `runSeed ^ floor ^ floorSeedSalt`
- 新しいフロアへ進む時:
  - floorGenerationを更新する。
- リトライ/再生成時:
  - floorGenerationを更新する。
- ログイン時:
  - 保存済みのfloorGenerationを維持し、勝手に更新しない。
- これにより、同じフロアでも明示的な再生成ではランダム化されるが、入り直しただけで地形が変わらない。

## Player State Restore Fix
- ログイン/リスポーン/クローン後に、以下を明示的に復元する。
  - `RunManager.loadFromPlayerNbt(player)`
  - `PlayerPerkTickService.applyPerkStats(player)`
  - `StaminaManager.restoreFromPersistentData(player)`
  - `RunManager.syncPlayer(player)`
- `StaminaManager` に現在スタミナ/最大スタミナの永続化APIを追加する。
  - `saveToPersistentData(Player)`
  - `restoreFromPersistentData(Player)`
- スタミナ値はtick中またはログアウト時に保存する。
- 最大HP反映後、保存HPがある場合は現在HPを適切に復元する。
  - 上限を超えないよう `min(savedHealth, player.getMaxHealth())`
  - 保存値がない場合は現状値を維持し、勝手な全回復はしない。

## Duplicate Generation Fix
- `PlayerRunData` の `dungeonOrigin` をログイン時に必ず検証する。
  - NBTにoriginがある場合はそれを優先。
  - 古いデータでoriginが欠けている場合だけ `resolveDungeonOrigin()` を使う。
  - `PlayerRunData` と player persistent data のoriginを同期する。
- ダンジョン生成前に、現在origin周辺の既存ブロック/コンテナ/ドロップを完全に消す既存処理は維持する。
- 過去originが別に残っている可能性がある場合、プレイヤー個人の旧originもクリア候補にする。
- ロビー生成は `isCurrentLobby()` の単発ブロック判定だけに頼りすぎない。
  - 専用SavedDataまたは中心マーカーNBTでロビー生成バージョンを保存する。
  - 同じバージョンならrebuildせず、NPC補正だけ行う。
  - バージョンが変わった時だけ明示的に再構築する。

## Safety Notes
- ロビー/ダンジョン領域のブロック消去は専用ディメンション内だけで行う。
- 通常ワールドの地形には触らない。
- フロア再生成時はチェスト中身とItemEntityを先に消して、中身が飛び出す再発を避ける。

## Test Plan
- `.\gradlew.bat build` が成功すること。
- 同じフロアでリトライ/再生成した時、レイアウトが変わること。
- ワールドに入り直しただけでは、進行中フロアが勝手に別レイアウトへ変わらないこと。
- ワールドに入り直してもHP上限、現在HP、最大スタミナ、現在スタミナがバニラ初期値へ戻らないこと。
- ロビーがログインのたびに不要rebuildされないこと。
- ロビーNPCが増殖しないこと。
- ダンジョン再生成時に旧構造、旧チェスト、旧ドロップが残らないこと。

## Assumptions
- 「同じフロアでもランダム」は、明示的なリトライ/再生成時にレイアウトを変えたいという意味。
- ワールド再入場だけでは、進行中のフロアを勝手に作り替えない。
- 通常ワールドはこのmod導入時に使わないため、ログイン時ロビー移動は維持する。
