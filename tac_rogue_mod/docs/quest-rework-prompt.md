# Quest System Rework Prompt

## Goal

Make the quest system less monotonous without changing the core roguelike loop.
The player should still progress through chapters, clear floors, collect perks, shop in the lobby, and defeat bosses every 5 floors.

## Current Behavior

- The Commander NPC opens the quest tab.
- Each chapter has one story quest and nine generated side quests.
- Every quest in the current chapter progresses automatically when matching events occur.
- A quest completes immediately when its counter reaches the target.
- The player receives gold immediately on completion.
- The chapter advances only when all quests in the current chapter are completed.

## Problems To Solve

- The player does not choose a mission focus.
- Most quests feel like passive counters.
- Quest goals are weakly connected to the current floor, Act phase, boss rhythm, or shop planning.
- Rewards are mostly gold, so quest completion does not strongly change the next run decision.
- The UI shows a list, but does not communicate priority or mission identity.

## Proposed Design

### 1. Quest Lanes

Each chapter should expose a smaller, clearer set of active quests:

- Story: required chapter progression quest.
- Contract: Act-aware tactical objective for the next few floors.
- Bounty: combat-focused optional objective.
- Supply: economy, shop, ammo, or survival objective.

The existing quest storage can remain compatible by still using quest ids and progress counters.

### 2. Quest Roles

Add a quest role field to each quest:

- STORY: required progression.
- CONTRACT: run-shaping tactical quest.
- BOUNTY: kill, headshot, elite, boss, or weapon quest.
- SUPPLY: gold, survival, no-damage, ammo, or shop-related quest.

The UI should display the role label so the list does not read like repeated counters.

### 3. Act-Aware Generation

Generated side quests should depend on the chapter and the current Act rhythm:

- Scout floors prefer exploration, floor clear, speed, stealth, and headshot goals.
- Supply floors prefer gold, survival, ammo, and shop preparation goals.
- Elite floors prefer kill count, elite/boss-adjacent combat, weapon mastery, and headshots.
- Danger floors prefer no-damage, survival, stealth, and tactical play.
- Boss floors prefer boss kill, weapon mastery, survival, and high-value rewards.

The first implementation should use the chapter number as the deterministic source for Act phase.
This avoids adding new networking or save format complexity.

### 4. Progression Rule

Chapter progression should require:

- The Story quest is completed.
- At least 2 non-story quests in the current chapter are completed.

This removes the need to clear every generated quest, while still making side quests matter.

### 5. Rewards

Keep gold rewards, but add reward identity through labels:

- STORY: high gold, chapter unlock message.
- CONTRACT: medium gold, should feel like tactical preparation.
- BOUNTY: higher gold for combat risk.
- SUPPLY: lower to medium gold, useful for recovery and shop planning.

Add one rare weapon reward quest per chapter.
This should be a special BOUNTY or CONTRACT quest marked as a rare weapon quest.
It should grant gold and one rare weapon on completion.

Rare weapon reward rules:

- Only one quest per chapter can grant a weapon.
- The reward weapon should be generated through the existing rogue item/TacZ helper path, not through raw item ids in UI code.
- Early chapters should reward reliable low-to-mid tier weapons.
- Later chapters can reward stronger categories such as rifle, shotgun, sniper, LMG, or explosive weapons.
- Boss-phase chapters should prefer high-value primary weapons.
- If weapon generation fails, the quest should still complete and grant its gold reward.
  The player should receive a clear failure-safe message rather than losing quest completion.

This adds one meaningful chase reward without turning the whole quest system into an item reward table.

### 6. Compatibility

Do not wipe existing quest progress.

Existing saved quest ids should continue to load.
New generated quest ids can keep the same `story_XX` and `q_XXX` style.
If a saved player has progress on an old quest id that still exists, it should continue to display and complete normally.

### 7. UI Scope

Keep the existing quest tab.
Add small role labels to each quest row and tooltip.
Do not add a new screen or new packet type in this pass.

### 8. Implementation Scope

Expected code changes:

- `QuestManager`
  - Add `QuestRole`.
  - Add rare weapon reward metadata for one quest per chapter.
  - Store role on `Quest`.
  - Replace side quest generation with role-based deterministic generation.
  - Change chapter advancement to require story + two side quests.
  - Add helper methods for role labels and completion checks.
  - Grant rare weapon rewards when the marked quest completes.

- `NpcManager`
  - Include quest role in the existing `quest_data` payload.

- `ClientSyncHandler`
  - Continue accepting old 6-field payloads.
  - Accept new 7-field payloads with role.

- `RogueInventoryScreen`
  - Render role labels on quest rows.
  - Show role in quest tooltip.
  - Show rare weapon reward hints for marked quests.

- `en_us.json` and `ja_jp.json`
  - Add quest role labels.
  - Add or adjust messages if needed.

- `docs/game-system-spec.md`
  - Add the finalized quest rework notes after implementation.

## Acceptance Criteria

- `gradlew compileJava` succeeds.
- `gradlew build` succeeds.
- Commander NPC still opens the quest tab.
- Existing 6-field quest sync does not crash the client.
- New quest rows show role labels.
- Completing the story quest plus at least two side quests advances the chapter.
- Completing every side quest is no longer required.
- Quest completion still grants gold.
- One quest per chapter can grant a rare weapon.
- Rare weapon quest completion does not fail if weapon creation fails; gold and quest completion still apply.
