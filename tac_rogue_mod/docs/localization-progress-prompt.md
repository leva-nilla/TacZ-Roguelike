# Localization Progress Prompt

## Summary
- Continue Japanese/English localization cleanup based on the previous language audit.
- Prioritize visible player-facing screens and confirmed missing keys before broader cosmetic cleanup.

## Scope
- Fix the confirmed missing fallback key in `QuestScreen`.
- Localize the dedicated quest screen text.
- Localize the most visible status tab labels in `RogueInventoryScreen`.
- Localize popup/HUD settings screen labels where they are simple UI text.
- Add matching `ja_jp.json` and `en_us.json` keys with identical placeholder shapes.

## Non-Goals
- Do not redesign UI layout.
- Do not change quest logic, shop logic, perk logic, or gameplay balance.
- Do not remove existing language keys unless they are clearly wrong.
- Do not attempt a full pass over every debug-only literal in this step.

## Acceptance Criteria
- `ja_jp.json` and `en_us.json` have the same key set.
- JSON parses successfully.
- No known reference to `quest.tac_rogue.type.kill_count` remains.
- Newly localized UI compiles.
- `.\gradlew.bat build` succeeds.
