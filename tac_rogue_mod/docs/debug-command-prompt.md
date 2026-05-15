# Debug Command Prompt

## Goal
Add admin-only debug commands for the current TacZ Roguelike systems so rarity weapons, quests, shop stock, and run state can be checked in-game without changing normal gameplay behavior.

## Command Scope
All commands are registered under the existing `/rogue_admin` root and keep the current admin permission gate.

## Commands
- `/rogue_admin debug state`
  - Shows core player run state: gold, floor, max reached floor, run active flag, floor clear flag, difficulty, and relevant counters.
- `/rogue_admin debug weapon`
  - Shows held weapon and configured gun slots.
  - Reports item id, gun id, rarity, damage/reload/magazine multipliers, base magazine size, effective magazine size, current ammo, and ammo id when available.
- `/rogue_admin debug quests`
  - Shows current quest chapter and active quest progress.
  - Includes quest id, role, type, progress, target, completion state, and whether a rare weapon reward exists.
- `/rogue_admin debug shop <floor>`
  - Shows shop stock data for the requested floor.
  - Reports filtered item count, category summary, and a short sample list with rarity for weapon entries.
- `/rogue_admin debug givegun <itemId> <rarity>`
  - Creates a TacZ gun stack with the requested rarity and gives it through the existing reward placement path.
  - Supported rarity values are the existing `WeaponRarity.Rarity` enum names.
- `/rogue_admin debug advancequest <type> <amount>`
  - Advances quest progress for the requested quest type by the requested amount.
  - This is an explicit mutating command for testing quest completion and reward delivery.
- `/rogue_admin debug sync`
  - Forces current player rogue data sync to the client.

## Constraints
- Do not change normal gameplay behavior.
- Do not expose commands outside the existing admin-only `/rogue_admin` permission check.
- Do not add data-wiping or reset commands in this pass.
- Mutating commands must be visibly named and require explicit arguments.
- Output should be compact enough to fit in chat, using multiple lines only where useful.

## Verification
- `./gradlew.bat compileJava`
- `./gradlew.bat build`
- Manual in-game command checks after packaging:
  - Inspect state, weapon, quests, and shop.
  - Spawn a rarity weapon and confirm debug output shows the rarity multipliers.
  - Advance a quest and confirm quest progress/reward behavior still works.
