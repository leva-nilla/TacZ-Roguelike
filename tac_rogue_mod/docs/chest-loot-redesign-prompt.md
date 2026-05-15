# Chest Loot Redesign Prompt

## Summary
- ダンジョンチェストの抽選テーブルを一から再構成する。
- 現在の「生成時に中身を入れるテーブル」と「開封時に個人用へ詰め直すテーブル」の二重構造を廃止し、実プレイで使うチェスト抽選を単一のサービスに集約する。
- チェストは探索報酬として、回復・弾薬・防御・素材・低確率の武器/アタッチメントを提供する。
- 序盤に強すぎる武器が出ないよう、武器カテゴリとレアリティは階層で制限する。
- アーマープレート、スタミナショット、アドレナリン、EMPなどの補給品が自然に出るようにする。

## Goals
- チェストを開けたときに「ほぼ空」「食料だけ」「武器だけ強すぎる」の偏りを減らす。
- 1フロア1-2個のチェストでも探索する価値を出す。
- マルチプレイでは同じチェストを各プレイヤーが一度だけ個別に取得できる状態を維持する。
- 100層以上でも報酬が完全に陳腐化しないように、武器カテゴリ・補給品・素材量だけ段階的に伸ばす。
- 調整値を `GameConstants` または専用サービスに集約し、今後のバランス調整をしやすくする。

## Non-Goals
- 敵ドロップテーブルはこの変更では直接変えない。
- ボス報酬テーブルはこの変更では直接変えない。
- ショップ価格、武器レアリティそのものの仕様は変えない。

## Architecture
- 新規サービス `ChestLootService` を追加する。
- `MapGenerator` はチェストにメタデータだけを設定し、中身は入れない。
- `ItemAndLifecycleHandler.refillPersonalSupplyChest` は `ChestLootService.generatePersonalChestLoot(...)` を呼ぶだけにする。
- 個人別抽選は既存通り `TacRogueLootSeed + player UUID` を使う。
- チェスト開封済み判定は既存の `TacRogueChestClaimed_<uuid>` を維持する。

## Chest Count
- 通常フロア:
  - 1個は保証。
  - 2個目は 25% で許可。
- ボスフロア:
  - 1個保証。
  - 2個目は出さない。
- 将来的に部屋タイプごとの特別チェストを足せるように、配置数と中身抽選は分離する。

## Loot Structure
チェストは以下のスロット群を順に抽選する。

### 1. Guaranteed Recovery Slot
必ず1つ出る。

| Floor | Bandage | Field Ration | Medkit | Emergency Ration |
| --- | ---: | ---: | ---: | ---: |
| 1-4 | 45% | 45% | 10% | 0% |
| 5-14 | 30% | 40% | 25% | 5% |
| 15-34 | 20% | 35% | 35% | 10% |
| 35+ | 15% | 30% | 35% | 20% |

### 2. Guaranteed Ammo Slot
必ず1スタック出る。
- 階層に合った武器候補からランダムに1つ選ぶ。
- その武器に対応する弾薬を生成する。
- 候補がない場合のみスキップする。

### 3. Optional Defense Slot
防御・耐久系の補給品。

| Floor | Roll Chance | Result |
| --- | ---: | --- |
| 1-4 | 25% | Armor Plate |
| 5-14 | 35% | Armor Plate 80% / Medkit 20% |
| 15-34 | 45% | Armor Plate 75% / Medkit 15% / Emergency Ration 10% |
| 35+ | 55% | Armor Plate 70% / Medkit 15% / Emergency Ration 15% |

### 4. Optional Tactical/Stamina Slot
スタミナ・戦術系の補給品。

| Floor | Roll Chance | Result |
| --- | ---: | --- |
| 1-4 | 25% | Stamina Shot |
| 5-9 | 35% | Stamina Shot 85% / Adrenaline 15% |
| 10-24 | 45% | Stamina Shot 65% / Adrenaline 25% / EMP Device 10% |
| 25+ | 55% | Stamina Shot 55% / Adrenaline 30% / EMP Device 15% |

### 5. Optional Material/Currency Slot
素材または換金用アイテム。

| Floor | Roll Chance | Result |
| --- | ---: | --- |
| 1-9 | 45% | Scrap Metal 1-3 |
| 10-29 | 50% | Scrap Metal 2-4 75% / Gold Cache 25% |
| 30+ | 55% | Scrap Metal 3-5 60% / Gold Cache 40% |

### 6. Optional Attachment Slot
アタッチメント。序盤は控えめ、中盤以降に増やす。

```text
chance = clamp(0.10 + floor * 0.004, 0.10, 0.35)
```

- floor 1: 10.4%
- floor 10: 14%
- floor 30: 22%
- floor 63+: 35% cap

### 7. Optional Weapon Slot
武器は低確率。序盤の強武器事故を避ける。

```text
if floor < 6:
  weapon chance = 0%
else:
  weapon chance = clamp(0.06 + (floor - 6) * 0.006, 0.06, 0.28)
```

- floor 6: 6%
- floor 10: 8.4%
- floor 20: 14.4%
- floor 30: 20.4%
- floor 43+: 28% cap

レアリティ抽選は `max(1, floor - 5)` を使う。  
チェスト武器は敵ドロップやボス報酬より少し弱めにする。

## Chest Weapon Candidate Progression

| Floor | Candidate Categories |
| --- | --- |
| 1-5 | PISTOL / SMG only for ammo source, weapon drop disabled |
| 6-14 | PISTOL / SMG |
| 15-29 | PISTOL / SMG / SHOTGUN / RIFLE |
| 30-49 | PISTOL / SMG / SHOTGUN / RIFLE / LMG / SNIPER |
| 50+ | PISTOL / SMG / SHOTGUN / RIFLE / LMG / SNIPER, plus low-weight EXPLOSIVE |

- MELEE はチェスト武器候補から除外する。
- TACTICAL は武器枠ではなく、戦術補給品側で扱う。
- EXPLOSIVE は floor 50+ でも通常武器より低重みで選ぶ。
- 価格上限は `700 + floor * 120` を基準にしつつ、候補が空なら PISTOL/SMG にフォールバックする。

## Slot Placement
- チェスト内の配置はランダムでよい。
- ただし同じスロットに上書きしない。
- 空きスロットがない場合は追加しない。
- 生成時チェストに仮中身を入れず、開封時にだけ中身を確定する。

## Balancing Notes
- アーマープレートは敵ドロップだけでは薄いので、チェストで補う。
- 弾薬は探索報酬として必ず出すが、武器は低確率に抑える。
- 武器レアリティは `floor - 5` 扱いにして、序盤の高レア暴発を抑える。
- 35層以降は回復より防御・戦術・素材が価値を持つようにする。
- 100層以降でも確率は上限で止め、インフレは素材量と武器候補で表現する。

## Test Plan
- `.\gradlew.bat build` が成功すること。
- 通常フロアでチェストが最低1個生成されること。
- 通常フロアで2個目が出る場合があること。
- ボスフロアでチェストが1個に収まること。
- 同じチェストを同じプレイヤーが2回開いても再取得できないこと。
- マルチプレイで別プレイヤーは同じチェストから個別報酬を取得できること。
- floor 1-5 で武器が出ないこと。
- floor 6+ で低確率に武器が出ること。
- floor 15+ で RIFLE / SHOTGUN が候補に入ること。
- floor 30+ で LMG / SNIPER が候補に入ること。
- floor 50+ で EXPLOSIVE が低重み候補に入ること。
- アーマープレートがチェストから出ること。
- 弾薬がチェストから安定して出ること。
- チェスト開封時に空チェストや重複上書きが発生しないこと。
