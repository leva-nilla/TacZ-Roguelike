# Full Audit Fix Implementation Prompt

## Goal

`full-code-review-audit-report.md` で検出した問題を、現行ゲーム内容を大きく変えずに段階的に修正する。追加要望として、ダンジョン再生成時のチェスト中身飛び出し、レア武器報酬抽選、debugコマンドからのパーク削除、パーク選択画面の修飾子説明途切れも同時に扱う。

## Assumptions

- このmodpackでは通常ワールドを基本的に使わない。
- そのため「通常ワールドでバニラHUDが消える」問題は最優先からは外す。
- ただし実装コストが低く副作用が少ない場合は、ローグ文脈外ではバニラHUDを消さない安全策を入れる。
- 既存のゲーム内容、報酬量、クエスト構造、ショップ品揃えは原則変更しない。
- 既存の未コミット変更は維持し、不要なrevertはしない。

## Fix Phases

### Phase 1: Progression / Softlock Fixes

#### 1. Multiplayer same-floor join

Problem:
- 同フロアに他プレイヤーがいる場合、未生成の自分の `dungeonOrigin.above(2)` に飛ぶ可能性がある。

Fix:
- 当面は「プレイヤーごとに個別生成」を優先する。
- `findPlayerOnFloor` による同フロア合流を使わず、各プレイヤーの `dungeonOrigin` に必ず生成済みフロアを用意する。
- 既存のマルチ合流仕様は保留。将来的にパーティ制を作るなら専用の共有ラン管理へ分離する。

Validation:
- `startNextFloor`
- `retryCurrentFloor`
- `gotoFloor`
の各ルートで、未生成originへテレポートしない。

#### 2. Floor progression after replay

Problem:
- 過去フロア再訪後に次フロア解放がズレる可能性がある。

Fix:
- 既存修正を維持しつつ、`floorCleared` と `maxReachedFloor` の扱いを再確認する。

### Phase 2: NPC Menu Security / Flow

Problem:
- `NpcMenuActionMessage` が role/action を信用し、近接NPCやセッションを再検証していない。

Fix:
- `NpcInteractMessage` でNPCに触れた時点で、サーバー側に短時間のNPCメニューセッションを保存する。
- 保存内容:
  - player UUID
  - npc entity id
  - role
  - dimension
  - expire tick
- `NpcMenuActionMessage` 処理時に以下を検証する。
  - セッションが存在する
  - 有効期限内
  - role一致
  - NPCが同じ次元にいる
  - プレイヤーがNPCから一定距離以内
  - actionがroleに許可されている
- 検証失敗時は処理しない。

Validation:
- 遠隔でshop/quest/heal/extractを呼べない。
- TacZアイテム保持中のNPCインタラクトは維持。

### Phase 3: Inventory / HUD / GUI Safety

#### 1. RogueInventory invisible slot interaction

Problem:
- 非INVENTORYタブで不可視スロットを操作できる可能性。

Fix:
- `activeTab != INVENTORY` のとき、slot click/drag/releaseをブロックする。
- 実アイテムスロット操作はINVENTORYタブのみ許可する。

#### 2. Vanilla HUD cancel

Problem:
- 通常ワールドを使わない前提なので優先度は低い。

Fix:
- 副作用が少ないため、ローグ文脈外ではバニラHUDをキャンセルしない。

#### 3. Small screen GUI

Problem:
- Perk/FloorClear/Shop/Popupが小さいGUIで崩れる可能性。

Fix:
- 今回は進行不能に関わるPerk/FloorClearを優先する。
- Shop/Popupは最低限のclampを入れる。

### Phase 4: Core Gameplay Correctness

#### 1. Loadout / difficulty baseline reset

Problem:
- パーク再計算がbase attributeを固定値へ戻し、Loadout/難易度補正を消す可能性がある。

Fix:
- パーク専用UUID modifierだけを除去/再付与する。
- base attributeを固定値へ戻さない。
- スタミナ最大値も基準値を保持し、パーク分のみ加算する。

#### 2. Quest persistence source of truth

Problem:
- QuestSavedData と Player persistent NBT が競合し、部分進捗が巻き戻る可能性。

Fix:
- QuestSavedData を正とする。
- Player NBTの `TacRogueQuestData` は初回移行/後方互換用に限定する。
- 通常同期時に古いPlayer NBTでSavedDataを上書きしない。

#### 3. Boss reward common path

Problem:
- TacZ銃でボスを倒すと、非TacZ側と同じ報酬処理に入らない。

Fix:
- ボス撃破報酬処理を共通サービスに分離する。
- TacZ kill path と vanilla/combat path の両方から呼ぶ。
- `rogue:boss` タグ付与もすべてのboss spawn pathで統一する。

#### 4. Ammo capacity calculation

Problem:
- 弾薬容量アップグレードの計算がfactory/inventory enforcement/global getterでズレる。

Fix:
- player単位の弾薬容量計算関数を1つに統一する。
- ammo生成、インベントリ整理、表示に同じ計算を使う。

#### 5. Rarity reload multiplier

Problem:
- レアリティのreload倍率が通常リロードへ反映されていない可能性。

Fix:
- 既存TacZ hook/mixinで安全に適用できるか確認。
- 実装困難な場合は、説明文から実効しない表現を外すのではなく、まずhook候補を調査してから判断する。

### Phase 5: Additional User Requests

#### 1. Dungeon regeneration drops chest contents

Problem:
- ダンジョン再生成時、既存チェストが破壊されて中身が飛び出す。

Fix:
- `clearPreviousDungeon` または再生成前処理で、チェスト/コンテナ系BlockEntityを先に空にしてからブロック消去する。
- 対象はローグダンジョン生成範囲内のみ。
- ロビーやスタッシュには影響させない。

Validation:
- リトライ/次フロア/再生成時に旧チェスト中身がアイテム化しない。

#### 2. Rare weapon quest reward roll not happening

Problem:
- クエストクリア時、レア武器報酬の抽選または付与が行われていない可能性。

Fix:
- `rareWeaponReward` のクエスト完了時に、適切なレアリティ武器を抽選して付与する。
- 序盤に強すぎる武器が出ないよう、現在floor/chapter基準のtier制限を維持する。
- 満杯なら既存方針どおりスタッシュへ送る。

Validation:
- rare quest completionで武器報酬が発生する。
- 通常questでは従来どおりgold/進捗のみ。

#### 3. Debug command perk removal

Problem:
- debug GUI/commandでパーク付与はできるが削除できない。

Fix:
- `/rogue_admin debug` GUIにパーク削除機能を追加する。
- 可能なら以下を用意する。
  - 個別パーク削除
  - 修飾子/カテゴリ指定削除
  - 全パーク削除
- サーバー側は既存debug権限チェックを維持する。

Validation:
- パーク削除後、同期されHUD/ステータス/パーク一覧から消える。

#### 4. Perk modifier text is over-wrapped and clipped

Problem:
- パーク選択画面で修飾子説明が改行されすぎ、末尾が途切れる可能性。

Fix:
- 日本語向け改行は維持するが、過剰に句読点ごとで分割しすぎない。
- カード下部に収まらない場合は、説明行数を優先順位づけする。
  - 効果説明
  - 修飾子名
  - 修飾子効果
  - tradeoff
- 収まらない場合はツールチップ/詳細表示へ逃がす。

Validation:
- 日本語表示で修飾子説明が途中で消えない。

### Phase 6: Packaging / Resources

Fix:
- Missing lang keyを追加する。
- `en_us.json` のmojibakeを修正する。
- Modrinth pack server envのclient-only mod指定を修正する。
- TacZ依存範囲を実検証済み範囲へ寄せる。

Non-blocking:
- mrpack完全再現性は別途CI化が必要なため、今回は可能な範囲でスクリプトとhash確認を改善する。

## Validation Commands

- `.\gradlew.bat build`
- 主要diff確認
- jarをModrinth profileへ配置
- SHA256一致確認

## Output

実装完了時に以下を報告する。

- 修正した問題一覧
- 変更ファイル
- ビルド結果
- Modrinth profileへのjar配置結果
- 実機で確認してほしい項目
