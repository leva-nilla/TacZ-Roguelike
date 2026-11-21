# 開発ドキュメント

| 読みたいもの | ドキュメント |
| --- | --- |
| 今のゲームの流れ・仕様・実装の入口 | [現行仕様](current-spec.md) |
| 開発環境、ビルド、ゲーム内の動作確認 | [開発・検証手順](development.md) |
| 確認したゲームデザインと、今後決めること | [ゲームデザインの意図](design-intent.md) |
| 合意と実装の差分、プレイテストで確かめること | [ゲームデザインの差分と検証計画](design/validation.md) |
| 次に直すこと、確認済みの範囲 | [0.9.7 レビュー](reviews/0.9.7.md) |
| ボス・敵の視線・防衛任務の実行結果 | [0.9.7 戦闘・防衛の動作確認](reviews/0.9.7-combat-smoke.md) |
| 過去の設計案・当時の検証記録 | [履歴](archive/README.md) |

## どの版を見るか

2026-11-21 確認。**開発を再開するなら `0.9.7` を起点にする。** ソース、パック定義、公開成果物の番号は別々。

| 対象 | 現状 | 確認先 |
| --- | --- | --- |
| 開発ソース | `0.9.7`、仕様・レビューの基準は `adfeeaf` | [基準コミット](https://github.com/leva-nilla/TacZ-Roguelike/commit/adfeeafcc0f5b73bf050ec566672bac317ccc7a1)、[MOD バージョン](../tac_rogue_mod/gradle.properties) |
| GitHub の既定ブランチ | `main` は 0.8.0 | [main](https://github.com/leva-nilla/TacZ-Roguelike/tree/main) |
| リポジトリ内のパック定義 | `versionId: 0.9.7`、クライアント配布用・未公開 | [modrinth.index.json](../tac_rogue_modpack/modrinth.index.json) |
| Modrinth 公開版 | 0.9.5 beta（`8fD53Tpo`） | [公開ページ](https://modrinth.com/modpack/tacz-roguelike/version/8fD53Tpo)、[インストール報告 #1](https://github.com/leva-nilla/TacZ-Roguelike/issues/1) |

実装された仕様は `current-spec.md`、合意した設計と未決事項は `design-intent.md`、繰り返し使う手順は `development.md` に置く。レビューは `reviews/` に版と基準コミットを明記し、古い計画や過去の検証結果は `archive/` に残す。過去の「成功」は現行版の再検証結果として扱わない。
