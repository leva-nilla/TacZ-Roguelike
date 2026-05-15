# Background Load Indicator / Dungeon Chest Balance

## Goals
- Make lobby background loading visible to players so FPS drops have an obvious cause.
- Show a compact progress gauge with loaded/total work, without blocking gameplay.
- Make dungeon chest frequency and chest loot probability easier to tune.

## Background Loading
- TacZ Startup Helper already performs lobby-side sound prewarming after entering `tac_rogue:lobby_dimension`.
- Expose total/pending/loaded sound prewarm counts from `ClientPrewarmManager`.
- Render a small HUD indicator from TacZ Roguelike while prewarm is pending:
  - Stage label: asset cache / TacZ sound prewarm.
  - Progress bar.
  - Loaded/total count and percent.
- Hide the indicator automatically when prewarm completes.

## Chest Balance
- Keep normal floors at roughly one guaranteed supply cache, with a reduced chance for a second cache.
- Keep boss floors at one cache.
- Reduce early rare weapon pressure:
  - Rare weapon chest roll starts later.
  - Chance scales upward with floor instead of being a flat early 18%.
- Keep ammo/recovery rewards common enough that exploration still feels useful.

## Verification
- `.\gradlew.bat build`
- Enter lobby after a fresh launch and confirm a small loading gauge appears while TacZ sound cache prewarms.
- Confirm the gauge disappears once complete.
- Generate several normal floors and confirm most floors have one supply cache, with occasional second cache.
- Confirm early floors do not frequently produce rare weapons from chests.
