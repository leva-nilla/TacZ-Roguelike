# Remaining UI Localization Prompt

## Summary
- Continue localization after the first pass.
- Target the remaining visible UI strings that are still hard-coded in English or Japanese.

## Scope
- Localize dedicated shop UI labels.
- Localize floor clear and floor selection screens.
- Localize the old inventory shop tooltip/list labels that can still be reached.
- Localize NPC menu local action labels for HUD and popup settings.
- Localize major debug menu labels without changing debug behavior.
- Add matching keys to `ja_jp.json` and `en_us.json`.

## Non-Goals
- Do not change layout or gameplay logic.
- Do not change item names supplied by TacZ or registry data.
- Do not translate internal log messages or comments.
- Do not remove existing keys unless they are proven invalid.

## Acceptance Criteria
- `ja_jp.json` and `en_us.json` still have identical key sets.
- Placeholder usage remains identical between languages.
- `.\gradlew.bat build` succeeds.
- The generated jar is copied into the Modrinth profile.
