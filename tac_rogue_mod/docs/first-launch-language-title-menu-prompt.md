# 初回言語選択・タイトル画面リデザイン実装仕様

## Summary
- Superseded note: メインメニュー描画方針は `main-menu-rebuild-and-startup-audit-prompt.md` の再構成版を正とする。この文書の `title_logo.png` 前提は旧案。
- 初回起動時、TacZ Roguelike専用の言語選択画面を表示する。
- 選択肢は「日本語」「English」「Other」の3つ。
- 「日本語」「English」はMinecraftクライアント言語をそれぞれ `ja_jp` / `en_us` に切り替え、選択済みフラグを保存する。
- 「Other」はMinecraft標準の言語設定画面を開かせる導線にし、選択済みフラグだけ保存する。
- メインメニューは既存の薄いHUD装飾を置き換え、軍事基地/作戦端末寄りの専用タイトルUIにする。

## Design Direction
- 全体は「軍事基地の作戦端末」を優先する。
- パノラマ背景は暗く使い、タイトルロゴ、作戦ステータス、短いメニュー補助情報、角フレーム、スキャンラインで構成する。
- 既存のMinecraftメニュー操作は壊さない。ボタン位置やクリック処理はバニラのままにし、上に描画する装飾だけを変える。
- 旧案では画像ロゴを使っていたが、現在は重なり防止のためタイトル画面専用画像を使わない。
- UI文言は `ja_jp.json` / `en_us.json` に追加する。

## First Launch Language Screen
- 新規クライアント画面 `FirstLaunchLanguageScreen` を追加する。
- 表示タイミングはタイトル画面表示後、かつまだ選択済みフラグが無い場合。
- フラグ保存先は `config/tac_rogue-client.properties` のような軽量クライアント設定ファイルにする。
- 画面の選択肢:
  - `日本語`: `options.languageCode = "ja_jp"` 相当へ切り替え、リソース再読み込み後にタイトルへ戻る。
  - `English`: `options.languageCode = "en_us"` 相当へ切り替え、リソース再読み込み後にタイトルへ戻る。
  - `Other`: Minecraft標準の言語設定画面を開けるようにする。言語選択済み扱いにして、以後は専用言語選択を出さない。
- 1回選択した後は再表示しない。
- 設定ファイルを削除すれば再表示できる。
- 言語切り替えAPIがバージョン差で直接呼べない場合は、反射で `Options.languageCode` と `LanguageManager#setSelected` 系を安全に呼び、失敗時は通常の言語設定画面へ誘導する。

## Title Menu Redesign
- `MixinTitleScreen` の描画を刷新する。
- 現在の `TACZ ROGUELIKE SYSTEM // INITIALIZED` 表示は廃止。
- 旧案の `title_logo.png` は廃止し、文字とラインだけで崩れにくいヘッダーを描画する。
- 方向性は「軍事基地の作戦端末」「TacZ銃撃ローグライク」「暗い金属 + シアン/アンバーアクセント」を維持する。
- 追加する描画:
  - 画面左上から中央寄りにロゴ/タイトルブロック。
  - 左下に作戦ステータス: build version, protocol, operator status。
  - 右側に縦のターミナル風情報パネル。
  - 細いフレーム線、スキャンライン、控えめなアクセントライン。
- 画面サイズが小さい場合でもロゴや情報がバニラボタンに大きく被らないよう、描画位置をレスポンシブに調整する。
- 装飾だけでクリック判定は持たない。

## Visibility Fix
- バニラのメインメニューボタン、Forge情報、Mojang著作権表示、新バージョン通知と重ならない配置にする。
- 画面中央上のMinecraftロゴ/スプラッシュとは競合しないよう、TacZ Roguelikeロゴは左上の専用コンパクトパネルに移動する。
- 左下のForge情報領域には独自ステータスパネルを置かない。
- 右側の情報パネルは十分に横幅がある場合のみ表示し、ボタン列に近い場合は省略する。
- コーナーブラケットは画面端のみに留め、中央UIへ伸ばさない。
- 全画面オーバーレイは暗くしすぎず、背景とボタンの視認性を優先する。

## Localization
- 新規キー:
  - `gui.tac_rogue.language.title`
  - `gui.tac_rogue.language.subtitle`
  - `gui.tac_rogue.language.japanese`
  - `gui.tac_rogue.language.english`
  - `gui.tac_rogue.language.other`
  - `gui.tac_rogue.language.other_hint`
  - `gui.tac_rogue.title.status`
  - `gui.tac_rogue.title.protocol`
  - `gui.tac_rogue.title.operator`
  - `gui.tac_rogue.title.briefing_1`
  - `gui.tac_rogue.title.briefing_2`
  - `gui.tac_rogue.title.briefing_3`

## Test Plan
- `.\gradlew.bat build` が成功すること。
- `config/tac_rogue-client.properties` が無い状態で起動すると言語選択画面が出ること。
- 日本語選択後、UIが日本語になり、次回起動で言語選択画面が再表示されないこと。
- English選択後、UIが英語になり、次回起動で言語選択画面が再表示されないこと。
- Other選択後、標準言語設定へ誘導され、次回起動で専用言語選択画面が再表示されないこと。
- メインメニューの通常ボタンが押せること。
- 小さめ解像度でもロゴ/装飾がボタンを過度に隠さないこと。
- Modrinthプロファイルへjarを配置して起動確認すること。

## Assumptions
- 言語選択はクライアントローカル設定で十分。ワールドデータやサーバー同期は不要。
- 「その他」は任意言語をこちらで全列挙せず、Minecraft標準の言語画面へ任せる。
- メインメニューの改修は描画中心に留め、バニラのボタン配置やクリック処理は保持する。
