# ロビー再生成不発修正 実装プロンプト

## 問題

既存ワールドでプレイヤーがすでにロビーディメンション内にいる場合、ログイン時の `LobbyGenerator.buildLobby` が呼ばれない。
そのため、jarを更新しても旧ロビーのまま残り、新しい前線基地ロビーに置き換わらない。

## 原因

`ItemAndLifecycleHandler.onPlayerLogin` は、プレイヤーがロビー外にいる場合だけロビー生成を実行している。
ロビー内でログインした場合、生成処理を通らない。

## 修正方針

1. `LobbyGenerator` に現在のロビー構造かどうかを判定する `isCurrentLobby` を追加する。
2. 新ロビー固有のブロック配置をマーカーとして使う。
3. `ensureLobbyBuilt` を追加し、マーカーが欠けている場合だけ `buildLobby` を実行する。
4. ログイン時、ロビー内外に関係なく `ensureLobbyBuilt` を呼ぶ。
5. リスポーン時も `ensureLobbyBuilt` を呼ぶ。
6. `/rogue_admin debug rebuild_lobby` を追加し、必要な時に手動でロビーを再生成できるようにする。

## 受け入れ条件

- 既存ロビーでもログイン時に新ロビーへ再生成される。
- 新ロビーがすでに生成済みなら、毎回重い再生成はしない。
- 手動コマンドで再生成できる。
- `gradlew.bat compileJava` と `gradlew.bat build` が成功する。
- Modrinthプロファイルへjarを反映する。

