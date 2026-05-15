# UI Feedback / Popup Prompt

## Goal
- Make perk modifier effects and weapon rarity scaling understandable in-game.
- Move important quest and NPC feedback out of chat into game-like popup UI.
- Keep the current gameplay balance unchanged unless explicitly requested later.

## Current Problems
- Perk modifier descriptions are too short to understand what changed.
- Some modifier text does not match the actual implementation.
  - `REINFORCED` is implemented as `1.5x`, but the Japanese and English text describe a smaller bonus.
  - `TITANIC` exists in code, but its localized modifier description is missing.
- Weapon rarity effects are applied through NBT, but the player cannot easily see the actual damage, reload, and magazine scaling.
- Quest completion, reward notices, chapter unlocks, extraction notices, and NPC talk are mostly chat messages, so they feel less like game events.

## Implementation Scope

### 1. Perk Modifier Explanation
- Update `ja_jp.json` and `en_us.json` modifier descriptions so every modifier explains:
  - effect multiplier
  - relative strength tier
  - any notable flavor or risk where appropriate
- Fix descriptions to match actual `PerkDefinition.Modifier` values.
- Add missing localization keys, especially:
  - `perk.tac_rogue.mod.none`
  - `perk.tac_rogue.mod.titanic`
- Show modifier description in relevant perk UIs where the player chooses or inspects perks.
  - Perk selection screen
  - Rogue inventory / perk list tooltip if present
  - Debug menu perk preview if practical

### 2. Weapon Rarity Explanation
- Add clear rarity tooltip/lore lines for TacZ rogue weapons created by the mod.
- The tooltip should show:
  - rarity name and star count
  - damage multiplier
  - reload multiplier, with wording that lower reload time is faster
  - magazine multiplier or effective magazine value when available
- Keep rarity mechanics unchanged:
  - Common: damage `1.00x`, reload `1.00x`, magazine `1.00x`
  - Uncommon: damage `1.10x`, reload `0.95x`, magazine `1.05x`
  - Rare: damage `1.20x`, reload `0.90x`, magazine `1.10x`
  - Epic: damage `1.35x`, reload `0.85x`, magazine `1.20x`
  - Legendary: damage `1.50x`, reload `0.80x`, magazine `1.30x`
- Apply the same readable display to:
  - initial weapons
  - shop weapons
  - quest reward weapons
  - dropped rarity weapons
  - debug-spawned weapons

### 3. Popup Notification UI
- Add a lightweight client-side popup notification system.
- Popups should be non-modal by default so they do not block combat.
- Use different popup types for readable styling:
  - `QUEST`
  - `REWARD`
  - `NPC`
  - `SYSTEM`
  - `WARNING`
- Replace selected chat messages with popup notifications:
  - quest completed
  - rare weapon reward gained
  - rare weapon reward failed or inventory full
  - chapter unlock
  - New Game Plus unlock
  - extraction arrival
  - major floor entry notice, if it currently appears as chat
  - NPC conversation and intel messages
- Keep chat messages for:
  - debug/admin command output
  - errors that need exact text
  - low-importance backend notices unless they are part of the player-facing loop

### 4. NPC Conversation Presentation
- Phase 1 uses the same popup system for NPC lines.
- NPC popups should include speaker name and message body.
- Do not add blocking dialogue choices yet unless requested later.
- Keep existing quest logic unchanged; only presentation changes.

## Constraints
- Do not change perk, rarity, quest, shop, or combat balance in this pass.
- Avoid timestamp-based persistent identifiers for these UI additions.
- Keep network payloads compact.
- Avoid creating popups every tick; only send on real event transitions.
- Do not remove debug command chat output.

## Verification
- Confirm localization JSON parses.
- Run Gradle compile/build.
- Confirm no packet registration mismatch.
- Confirm at least these flows compile against the new UI code:
  - quest completion
  - rare quest reward
  - NPC conversation/intel
  - rarity weapon tooltip creation
  - perk modifier tooltip/display
- Regenerate the mrpack after successful build if requested.

