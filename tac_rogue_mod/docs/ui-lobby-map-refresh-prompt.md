# UI / Lobby / Map Refresh Prompt

## 日本語概要
この改修は、ゲーム内容そのものを大きく変える前に、プレイヤーが今の状態を理解しやすくするための見た目と導線の整理を目的にする。

ステータスタブとパークタブは、現在の数値や取得済みパークをただ並べる画面ではなく、ラン中に何が強くなっていて、何が危険なのかをすぐ読める画面にする。ロビーはNPCが立っているだけの場所ではなく、クエスト、ショップ、情報、回復の役割が見た目でも分かる基地として整える。マップ生成は、現在の攻略テンポを壊さずに、部屋の種類や遮蔽物、敵の湧き位置を増やして、TacZの銃撃戦として遊びやすくする。

この段階では、パーク倍率、レアリティ倍率、クエスト報酬、敵の基本スケーリングなどのゲームバランスは変更しない。まずは画面と空間の分かりやすさを上げる。

## 日本語仕様

### 1. ステータスタブ改修
- 体力、防御、移動速度、スタミナ、所持金、現在層、最高到達層、難易度、テーマを整理して表示する。
- 数値をカテゴリごとにまとめ、ラン中に確認したい情報を上から順に読めるようにする。
- 良い状態は緑、注意は黄、危険やペナルティは赤、システム情報は青系で色分けする。
- ボタン類の機能は変えない。
  - 次の層へ
  - ロビーへ戻る
  - 作戦開始

### 2. パークタブ改修
- 取得済みパークをカテゴリごとにまとめて見やすくする。
- 同じパークが複数ある場合は、今のスタック表示を維持する。
- 各パークには次を表示する。
  - パーク名
  - 合計効果量
  - スタック数
  - 代表的な修飾子
  - 危険な修飾子がある場合の警告
- ホバー時のツールチップには、1個あたりの効果、合計効果、修飾子説明を出す。
- 呪縛、汚染、過駆動、不安定などのリスク付き修飾子は見落としにくくする。

### 3. クエスト/ショップ周辺の軽い整理
- 既存のタブ構成や操作は維持する。
- クエスト報酬にレア武器がある場合、一覧上で分かりやすくする。
- ショップやクエスト画面の余白、見出し、状態表示を少し整理する。
- デバッグUIは機能優先で、過度に装飾しない。

### 4. ロビー改修
- ロビーを基地ハブとして読みやすくする。
- NPCの役割は維持する。
  - Commander: クエスト
  - Quartermaster: ショップ
  - Intelligence Officer: 階層情報 / 階層選択
  - Medic: 回復
- 各NPCの周囲に役割が分かる装飾を置く。
  - Commander: 作戦机、ブリーフィング壁
  - Quartermaster: 武器ラック、補給箱
  - Intelligence Officer: コンソール、マップテーブル
  - Medic: 医療ベッド、回復エリア
- NPCが増殖しない既存処理は維持する。
- 重くなるほどブロックを増やさない。

### 5. マップ生成改修
- 部屋の種類を増やして、毎回同じ印象になりにくくする。
- 想定する部屋タイプ:
  - 開けた戦闘部屋
  - 遮蔽物が多い部屋
  - 通路接続部屋
  - 小さな補給/キャッシュ部屋
  - ボス向けアリーナ
  - 暗い、または視界が悪いバリエーション
- 敵の湧き方を調整する。
  - プレイヤー初期位置の近くに湧きすぎない
  - 部屋全体に分散して湧く
  - 1層など序盤は軽めにする
- TacZの銃撃戦向けに、遮蔽物と射線を意識した部屋にする。
- ランシードから決まる生成の再現性は維持する。

### 6. 確認項目
- ステータスタブがロビーとラン中の両方で崩れないこと。
- パークなし、パーク1個、スタック済みパークでパークタブが崩れないこと。
- Commanderからクエストタブが開くこと。
- Quartermasterからショップが開くこと。
- Medic / Intel のポップアップが引き続き動くこと。
- ロビー生成でNPCが重複しないこと。
- フロア生成が失敗せず、敵の密度が極端にならないこと。
- `compileJava` と `build` が成功すること。

## Goal
- Improve the look and readability of the rogue inventory tabs, especially Status and Perks.
- Make the lobby feel more like a usable base instead of a simple command room.
- Improve map generation variety while keeping the current gameplay loop and balance stable.

## Non-Goals
- Do not change core progression rules in this pass.
- Do not rebalance perks, rarity, quest rewards, or enemy scaling unless a UI/map issue requires a small supporting change.
- Do not replace the existing quest/shop/debug systems.
- Do not add new external dependencies.

## Phase 1: Status Tab Visual Refresh
- Rework the Status tab into a compact dashboard.
- Show key combat values in grouped sections:
  - Health / armor / movement
  - Stamina / regen / sprint status
  - Current floor / max floor / difficulty / theme
  - Gold and run state
- Add clearer color coding:
  - green for healthy / improved stats
  - yellow for warning or mid state
  - red for danger or penalties
  - blue/cyan for system/run data
- Keep text short and readable.
- Avoid oversized hero UI; this is a repeated-use operational screen.
- Preserve existing button behavior:
  - next floor
  - return lobby
  - mission start

## Phase 2: Perk Tab Visual Refresh
- Make owned perks easier to scan.
- Group perks by category, keeping the current stack grouping behavior.
- Show each group with:
  - category name
  - total effect
  - stack count
  - strongest or most dangerous modifier tag
  - concise modifier warning/description in tooltip
- Improve hover tooltips:
  - show per-unit effect
  - show total effect
  - show each modifier explanation
  - show cursed or risky modifiers clearly
- Make the perk list visually distinct from the status tab without becoming decorative clutter.

## Phase 3: Quest / Shop Adjacent Polish
- Keep current tabs and workflows.
- Improve spacing, headings, and state labels where the current UI looks cramped.
- Make rare weapon reward quests more visible in the quest tab.
- Keep debug/admin UI functional and plain.

## Phase 4: Lobby Layout Improvements
- Make the lobby read as a base hub with clear stations.
- Keep existing NPC roles:
  - Commander: quests
  - Quartermaster: shop
  - Intelligence Officer: floor information / floor selection
  - Medic: recovery
- Improve station readability with simple environmental cues:
  - commander desk / briefing wall
  - shop racks / supply crates
  - intel console / map table
  - medical bay / recovery area
- Keep the layout navigable and compact.
- Avoid excessive block spam that increases generation cost.
- Ensure NPC count remains stable and does not duplicate.

## Phase 5: Map Generation Improvements
- Increase room variety without changing the core objective.
- Add or improve room archetypes:
  - open combat room
  - cover-heavy room
  - corridor connector
  - small supply/cache corner
  - boss-oriented arena room
  - dark/low-visibility variant where appropriate
- Improve enemy spawn pacing:
  - avoid too many enemies near player spawn
  - spread spawn points across the playable room
  - keep early floors lighter
- Improve readability:
  - clearer exits or extraction spawn area
  - less confusing dead space
  - enough cover for TacZ gunplay
- Keep generation deterministic from the run seed.

## Verification
- Run JSON/resource validation where applicable.
- Run `gradlew compileJava`.
- Run `gradlew build`.
- Manually check:
  - Status tab at lobby and in a run
  - Perk tab with no perks, one perk, and stacked perks
  - Quest tab still opens from Commander
  - Shop still opens from Quartermaster
  - Medic and Intel NPC popups still work
  - New lobby generation does not duplicate NPCs
  - Floor generation completes and enemies spawn at reasonable density
- Regenerate mrpack after successful build if requested.

## Suggested Implementation Order
1. Refactor only rendering helpers inside `RogueInventoryScreen` for Status and Perks.
2. Compile.
3. Improve lobby station decoration while preserving NPC placement.
4. Compile.
5. Improve map room archetypes and spawn placement.
6. Build and regenerate mrpack.
