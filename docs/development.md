# 開発・検証手順

## 環境と配置

Minecraft **1.20.1** / Forge **47.4.18** / Mojang official mappings **1.20.1** / **JDK 17** / Gradle Wrapper **8.8**。開発時の TaCZ は **1.1.8**（MOD メタデータの必須範囲は `[1.1.8,1.2.0)`）。Leawind Three Person、LR Tactical 関連の座標・スコープは [build.gradle](../tac_rogue_mod/build.gradle)、パックに入る実ファイルは [manifest](../tac_rogue_modpack/modrinth.index.json) を参照する。開発ランと配布パックの MOD 構成は同一ではない。

| パス | 役割 |
| --- | --- |
| [`tac_rogue_mod/`](../tac_rogue_mod) | `tac_rogue` 本体。Java は `src/main/java`、翻訳・モデル・ディメンションなどは `src/main/resources` |
| [`tac_rogue_mod/src/test/`](../tac_rogue_mod/src/test) | JUnit の単体テスト。価格、スケーリング、パーク、テーマ別の生成計画など |
| [`tac_rogue_mod/tacz_startup_helper/`](../tac_rogue_mod/tacz_startup_helper) | TaCZ 起動補助の別Gradleモジュール。下の `:jar` コマンドは本体のみ |
| [`tac_rogue_modpack/`](../tac_rogue_modpack) | Modrinth の配布用manifestと `overrides/`。公開済み `.mrpack` や本体JARはリポジトリに含まれない |

## ビルドと起動（Windows）

新規取得する場合は `git clone -b 0.9.7 https://github.com/leva-nilla/TacZ-Roguelike.git`。取得済みの作業ツリーは、切り替え前に `git status` で未コミット変更を確認する。以下はリポジトリ直下から実行する。

```powershell
& "$env:JAVA_HOME\bin\java.exe" -version  # JAVA_HOME を JDK 17 に設定してから進む
Set-Location tac_rogue_mod
.\gradlew.bat :test
.\gradlew.bat :jar :reobfJar
.\gradlew.bat :runClient
```

- テスト結果は `tac_rogue_mod/build/reports/tests/test/index.html`。本体のビルド出力は `tac_rogue_mod/build/libs/tac_rogue-0.9.7.jar`。
- 開発クライアントは `tac_rogue_mod/run/` を使う。Modrinth で検証する場合は既存プロファイルを上書きせず、使い捨てプロファイル・新規ワールドを使う。
- `:runServer` の定義はあるが、専用サーバーでパックをそのまま導入できることの確認にはならない。TaCZ は本体の両側で必須だが、現行manifestではサーバー配布対象外。クライアントとサーバーの本体JARと依存関係を揃えてから Co-op を確認する。

## ゲーム内スモーク

通常プレイで、ロビー → 出撃 → 任務達成 → 回収 → 次階または帰還を通す。デバッグスモークは本番ワールドでは使わない。現行の [コマンド登録](../tac_rogue_mod/src/main/java/com/levanilla/rogue/core/command/RogueAdminCommand.java#L355-L358) は特定プレイヤー名に限定され、OPやサーバーコンソールでは代用できない。

| 変更箇所 | `/rogue_admin debug smoke` のサブコマンド | 追加で見ること |
| --- | --- | --- |
| フロア生成・ボス | `generation`, `boss_generation` | 同階の再挑戦での変化、入口からアリーナまでの到達 |
| 任務・敵AI | `objective`, `encounter`, `monster`, `ai_matrix` | 端末や防衛地点の周囲に敵が到達するか、進行が止まらないか |
| 装備・経済・パーク | `combat`, `economy`, `perk_storage` | 弾薬の拾得・移動、ショップとパークの保存・再ログイン |
| 公開Co-op | 手動（同じビルドの2クライアント） | 同じフロアへの参加、任務共有、補給箱の分離、個別帰還 |

結果はゲームディレクトリ下の `logs/tac_rogue_smoke/smoke-latest.log` と `smoke-latest.jsonl`。次の実行で上書きされるので、必要なログは先に保存し、`PASS / SKIP / FAIL` を分けて記録する。サーバーコマンドの成功だけで、クライアントUIや実際のパック導入が通ったとは扱わない。

### 補給箱の実機チェック

1. 同じ公開フロアで A が通常補給箱から一部だけ取り、B が同じ箱を開く。A の残りが変わらず、B には自分の品だけが見えること。
2. A が閉じて開き直すと残りだけが見え、空にした後は再抽選されないこと。通常クリック・ドラッグ・ホットバー交換・Shiftクリックで手持ちから預けられないこと。弾薬の取り出しは従来どおり予備弾薬枠を優先すること。
3. 同じ階を再生成すると未回収品は残らず、使用済みの取得回数も戻らないこと。上限2で1回取得後に再挑戦した場合は、残り1回を使えること。
4. 任務 `RECOVER_CACHE` のチェックでは任務進行と報酬が正常に機能すること。通常補給箱とは別経路。

## クライアントパックの組み立てと確認

1. 本体に加え、`tac_rogue_mod` で `.\gradlew.bat :tacz_startup_helper:jar :tacz_startup_helper:reobfJar` を実行する。
2. ZIP の直下に `modrinth.index.json` と `overrides/` を置く。同梱する `overrides/mods/` には本体と起動補助の **0.9.7 JAR を各1本**入れ、拡張子を `.mrpack` にする。ソースフォルダを直接上書きせず、別の組み立て用ディレクトリを使う。
3. `gradle.properties` のMOD版、manifestの `versionId`、`.mrpack` 内のmanifest・JAR名を照合する。外部ファイルは実データから `fileSize`、`sha1`、`sha512` を照合する。
4. クリーンなModrinthプロファイルで導入・起動する。マルチプレイは同版でショップを開けることと、旧チャンネル版が接続時に拒否されることを確認する。専用サーバーへのパック導入は現時点の配布対象外。
