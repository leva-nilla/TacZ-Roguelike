# Debug Permission Fixed User Prompt

## Summary
- デバッグコマンドとデバッグGUI操作の権限を、OP権限ではなく固定ユーザー名のみへ戻す。

## Scope
- `RogueAdminCommand.isAllowed` と `DebugActionMessage.isAllowed` を固定ユーザー名チェックのみにする。
- それ以外のデバッグ機能、P0-P2修正、バランス調整は変更しない。

## Test Plan
- `.\gradlew.bat build` が成功する。
- Modrinthプロファイルへjarを再配置する。
