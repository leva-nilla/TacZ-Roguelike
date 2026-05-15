# Full Code Review Audit Prompt

## Goal

TacZ Roguelike mod の現状コードを、実装変更なしで一度全体精査する。目的は「今すぐ直すべき不具合」「将来的に重くなる設計」「UI/ゲーム体験上の破綻」「Modrinth環境での動作リスク」を洗い出し、次の改修順を決められる状態にすること。

## Review Policy

- 原則としてコード変更は行わない。
- 例外的に、レビュー中にビルド不能などの致命的問題が見つかっても、その場では修正せずレポートにまとめる。
- 既存の未コミット変更はユーザー作業として扱い、勝手に戻さない。
- 評価対象は `tac_rogue_mod` 配下の実装、リソース、Mixin、ビルド設定、docs とする。
- 外部依存そのものは評価対象外。ただし TacZ / Forge / Modrinth 連携の使い方にリスクがある場合は指摘する。

## Sub-Agent Split

### Agent A: Client / UI / HUD

対象:
- `src/main/java/com/levanilla/rogue/client`
- `src/main/java/com/levanilla/rogue/client/hud`
- GUI、HUD、ホットバー、ポップアップ、NPCメニュー、ショップ/クエスト/インベントリ画面

評価観点:
- 画面同士の競合
- GUIスケール・小画面でのはみ出し
- HUD/ホットバー/ポップアップの重なり
- イベントフックの過剰キャンセル
- クライアント専用処理がサーバー側に漏れていないか

### Agent B: Core Gameplay / Progression

対象:
- `src/main/java/com/levanilla/rogue/core`
- `src/main/java/com/levanilla/rogue/core/event`
- `src/main/java/com/levanilla/rogue/core/service`
- パーク、クエスト、報酬、レアリティ、スタッシュ、進行管理

評価観点:
- 進行不能・報酬消失・同期漏れ
- レアリティ効果の実効反映
- パーク効果の積算と表示のズレ
- サーバー/クライアント同期
- NBT保存・ロードの互換性

### Agent C: World / NPC / Map Generation

対象:
- `src/main/java/com/levanilla/rogue/world`
- ダンジョン生成、ロビー生成、NPC、モブ、チェスト、フロア生成

評価観点:
- 生成物の浮き・奈落・閉じ込め・スポーン不能
- モブ数・チェスト数・部屋配置の破綻
- NPCインタラクトと画面導線
- 生成負荷とワールド保存への影響

### Agent D: Build / Resources / Mixin / Packaging

対象:
- `build.gradle`
- `gradle.properties`
- `src/main/resources`
- `src/main/java/com/levanilla/rogue/mixin`
- Modrinth pack 関連ファイル

評価観点:
- Mixin target / remap の危険
- リソースパス・langキー欠落
- jar / mrpack 化の再現性
- 依存関係とバージョン固定
- クライアント/サーバー環境でのクラッシュリスク

## Main CLI Checks

メイン側では以下を実行する。

- `git status --short`
- `rg --files`
- `.\gradlew.bat build`
- 主要警告の整理
- `rg` による TODO/FIXME/例外握りつぶし/`System.currentTimeMillis`/`System.nanoTime`/client-only import の探索
- ファイル数・行数の概算

## Cross Review

各サブエージェントの評価を受け取った後、メイン側で以下を行う。

- 同じ問題を重複統合する。
- 重大度を `P0 / P1 / P2 / P3` で分類する。
- 「すぐ直す」「次の段階で直す」「保留でよい」に分ける。
- 指摘にはファイルパスと根拠を付ける。
- 不確実なものは推測として明記する。

## Output

最終出力は日本語でまとめる。

- 総評
- P0/P1/P2/P3 の指摘一覧
- サブエージェント間で評価が割れた点
- 次に直すべき順番
- ビルド結果
- 追加で実機確認すべき項目

## Non Goals

- このレビューではコード修正をしない。
- ゲームバランスの大規模再設計はしない。
- 新機能提案は、発見した問題に紐づくものだけに絞る。
