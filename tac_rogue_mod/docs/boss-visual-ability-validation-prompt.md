# Boss Visual Creation / Ability Validation Prompt

## Summary
- `tac_rogue:boss` の見た目を、バニラテクスチャ流用から TacZ Roguelike 専用の外見へ変更する。
- ボス能力が実際に発火するか、コード上の条件とゲーム内デバッグ導線の両方で確認できるようにする。
- 今回は「安定して見える独自ボス」と「能力の動作確認」を優先し、後続の精密モデル化へ拡張できる形にする。

## Current Findings
- `TacRogueBossEntity` は独自Entityとして登録済み。
- 現在の `TacRogueBossRenderer` は `HumanoidModel` にバニラMobテクスチャを割り当てている。
- Wardenなどのバニラ専用テクスチャは人型UVと合わないため、見た目が崩れる可能性が高い。
- 能力はサーバーtickで実装済みだが、発火ログ/デバッグ表示がないため、ゲーム内で「発動しているか」が分かりづらい。

## Visual Implementation
- 新規モデル:
  - `TacRogueBossModel`
  - 既存NPCモデルより大柄な軍事系パワードスーツ風シルエット。
  - 頭、胴、腕、脚、肩アーマー、バックパック風パーツを持つ。
  - 役職差が分かるように、ロールごとに微妙にスケール/色/装備感を変える。
- 新規テクスチャ:
  - `assets/tac_rogue/textures/entity/boss/breacher.png`
  - `assets/tac_rogue/textures/entity/boss/commander.png`
  - `assets/tac_rogue/textures/entity/boss/void_warden.png`
  - `assets/tac_rogue/textures/entity/boss/pyro.png`
  - `assets/tac_rogue/textures/entity/boss/leviathan.png`
- テクスチャ作成方針:
  - MinecraftのEntity UVに合わせる必要があるため、最初はAI生成画像ではなく、UV崩れしない手続き生成テクスチャで作る。
  - 色/明度/装甲パターンをロールごとに分ける。
  - 後からAI生成コンセプトや手描きテクスチャに差し替えられるよう、ファイル名とRenderer参照を固定する。

## Role Visual Direction
- BREACHER:
  - 重装アサルト。暗いグレー装甲、赤い警告ライン。
- COMMANDER:
  - 指揮官。オリーブ/黒、肩章風の明るいライン。
- VOID_WARDEN:
  - Warden代替。黒/紫/シアン発光風、顔の視認性を強める。
- PYRO:
  - 火炎担当。黒/焦げ茶装甲、橙の発光ライン。
- LEVIATHAN:
  - 重装水陸型。青緑/黒、厚い胴体装甲。

## Ability Validation
- ボス能力に軽量なデバッグ用persistent counterを追加する。
  - `TacRogueBossShockwaveCount`
  - `TacRogueBossSummonCount`
  - `TacRogueBossPressureCount`
- 発火時にカウンターを加算する。
- デバッグメニューまたは既存debugコマンドから確認できるようにする余地を残す。
- 最低限、`/rogue_admin debug` のボススポーン導線がある場合は、各ロールをテストしやすいように追加/拡張する。

## Ability Issues To Check
- Shockwave:
  - 対象が範囲内かつLine of Sightありの時にダメージ/ノックバック/鈍足が入るか。
  - `Vec3.normalize()` がゼロ距離で異常値にならないよう、距離ゼロ対策を入れる。
- Summon:
  - 増援が上限4体を超えないか。
  - ボス死亡時に増援が消えるか。
  - 召喚地点が壁内になりすぎないか。必要なら足元安全判定を入れる。
- Pressure:
  - PYROの炎上、COMMANDERの弱体化、その他ロールの採掘低下/プレッシャーが発火するか。
  - 発火間隔が短すぎて理不尽にならないか。
- Despawn:
  - `removeWhenFarAway=false` と `checkDespawn` 無効化で、時間経過/距離で消えないこと。

## Test Plan
- `.\gradlew.bat build` が成功すること。
- ボスフロアで `tac_rogue:boss` が生成されること。
- Wardenがボスとして出現しないこと。
- 5種類のロールでテクスチャが切り替わること。
- 顔/胴/背面に空白や極端なUV崩れがないこと。
- Shockwaveが範囲内プレイヤーへダメージ/ノックバック/鈍足を与えること。
- COMMANDER/PYROが増援を召喚し、上限を超えないこと。
- ボス死亡時に召喚増援が残らないこと。
- Pressure効果がロール別に発火すること。
- ビルド後、Modrinthプロファイルへjarを配置すること。

## Assumptions
- まずはゲーム内で破綻しない独自ボス外見を優先する。
- AI生成テクスチャはUV精度が必要なため、今回は採用しない。
- 後続で精密な独自3Dモデル/AI生成コンセプトからの再テクスチャ化は可能。
