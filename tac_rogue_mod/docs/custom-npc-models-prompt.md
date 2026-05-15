# Custom NPC 3D Models and Textures Prompt

## Goal
TacZ Roguelike のロビーNPCを、ArmorStand/Villager の見た目流用ではなく、ロール別の完全な独自3Dモデルと専用テクスチャで表示する。

対象NPC:
- Commander
- Quartermaster
- Intel Officer
- Medic

## Desired Direction
- 全体テーマ: 近未来軍事基地の作戦スタッフ。
- Minecraft の世界観から浮きすぎない低ポリ寄りモデル。
- TacZ の銃器/軍事感に合う、装備・服装・シルエット差分を持たせる。
- 遠目でも役割がわかる色と装備を使う。

## Implementation Strategy

### Phase 1: 独自表示エンティティの追加
- `TacRogueNpcEntity` を追加する。
- `Mob` または `PathfinderMob` ベースではなく、基本的には動かない表示/対話用の軽量エンティティにする。
- AI・移動・攻撃・村人取引は不要。
- サーバー側の対話処理は既存 `NpcManager` のメニュー処理へ接続する。
- 既存の不可視Villager + ArmorStand方式は削除または移行用フォールバックにする。

### Phase 2: EntityType 登録
- `ModEntities` を新規追加。
- `tac_rogue:npc` のような EntityType を登録。
- 既存 `NpcRole` を NBT / SynchedEntityData / persistent data で保持する。
- 既存セーブで旧NPCが残る可能性があるため、`ensureNpcsSpawned` で旧方式NPC/ArmorStandを整理して新NPCへ置換する。

### Phase 3: Client Renderer / Model
- `TacRogueNpcRenderer` を追加。
- `TacRogueNpcModel` を追加。
- モデルはロール共通の humanoid ベースにし、以下のパーツを切り替える:
  - helmet / headset
  - vest / coat
  - backpack / medic bag
  - shoulder pads
  - role item / arm pose
- ロールごとにテクスチャを切り替える:
  - `textures/entity/npc/commander.png`
  - `textures/entity/npc/quartermaster.png`
  - `textures/entity/npc/intel.png`
  - `textures/entity/npc/medic.png`
- 可能なら発光部分用の emissive layer は後回し。まず通常テクスチャのみ。

### Phase 4: Model Asset Creation
- Javaコード内の `EntityModel` で低ポリモデルを定義する。
- Blockbench 風の構造:
  - head
  - body
  - right_arm / left_arm
  - right_leg / left_leg
  - role accessory parts
- テクスチャは最初は 64x64 PNG を生成/作成する。
- 完全手描き品質までは最初から狙わず、役割識別できるベーステクスチャを作る。

### Phase 5: Interaction
- 新NPCを右クリックしたら既存の `NpcMenuScreen` を開く。
- Commander -> quest/talk
- Quartermaster -> shop/talk
- Intel -> intel/floor_select/extract
- Medic -> heal/talk
- ダンジョン内の Extraction Officer も同じEntityTypeで `INTEL_OFFICER` ロールを使う。

### Phase 6: Migration / Cleanup
- 旧不可視VillagerとArmorStandを `ensureNpcsSpawned` 時に削除。
- 既存の `handleInteraction(ServerPlayer, Villager)` は互換用に残すが、新NPCが優先。
- 旧 `NPC_VISUAL_TAG` ArmorStand は不要になったら掃除する。

## Model Concepts

### Commander
- 色: dark olive / gold accent
- 頭: beret or command helmet
- 胴: officer coat / tactical vest
- 手持ち: compass or map tablet
- 印象: 指揮官、作戦司令

### Quartermaster
- 色: green / black / ammo yellow
- 頭: helmet
- 胴: heavy vest, ammo pouches
- 背中: supply backpack
- 手持ち: compact weapon crate or crossbow-like prop
- 印象: 補給担当、武器庫管理

### Intel Officer
- 色: navy / cyan accent
- 頭: headset / visor
- 胴: light tactical suit
- 手持ち: tablet
- 印象: 情報分析、通信担当

### Medic
- 色: white / gray / red or pink accent
- 頭: medical cap or headset
- 胴: medic vest
- 背中/腰: med bag
- 手持ち: potion/medical kit
- 印象: フィールドメディック

## Risks
- Forge 1.20.1 の EntityType/Renderer 登録が必要になり、現在より変更範囲が広い。
- 既存ロビーに残っている旧NPCとの二重表示が起きる可能性があるため、移行処理が重要。
- PNGテクスチャをコードだけで作る場合、初期品質は簡素になる。
- 本格的なBlockbench製モデルを求める場合、外部 `.bbmodel` 作成が望ましいが、まずはJavaモデルで実装する。

## Validation
- `./gradlew.bat compileJava`
- `./gradlew.bat build`
- ゲーム内確認:
  - ロビーに4種類の独自NPCが表示される。
  - 旧Villager/ArmorStandが残らない。
  - 各NPC右クリックでメニューが開く。
  - Quartermasterから専用ShopScreenが開く。
  - Medicの回復、Commanderのクエスト、Intelの階層選択/回収が動く。
  - ダンジョン回収NPCも独自モデルで表示される。

## Open Questions
- 初期モデルは「Javaコードで作る低ポリモデル」で進めるか、Blockbench用 `.bbmodel` 生成まで含めるか。
- テクスチャは最初は簡易64x64でよいか、最初から描き込み多めの128x128にするか。
- NPCの体格は全員同じでロール装備差分にするか、Quartermasterだけ大柄など体格差も入れるか。
