# Current Status - 2026-05-27

## Branch
- Current branch: `codex/perf-review-low-risk`
- Previous separated commit: `ad7d7c5 Optimize lobby loading warmup`
- This note summarizes the follow-up low-risk optimization work and perk total softcap change.

## Implemented Changes
- Low-risk tick and allocation cleanup:
  - Perk attribute modifiers now skip remove/add when the existing value is unchanged.
  - Stealth attribute updates avoid redundant modifier operations.
  - Combat drop candidate lists and attachment candidate IDs are cached instead of rebuilt per kill.
  - Player-scoped transient maps such as gun context and damage/kill tick tracking are cleaned up.
  - Rogue mob maintenance in `SpawnAndWorldHandler` is staggered over several ticks.
  - Rogue mob vision FOV cosine values are constants instead of recalculated repeatedly.
- Flashlight path cleanup:
  - Old client-side dynamic-light toggle/tick path is no longer driven from input handling.
  - Flashlight state remains server-authoritative through `FlashlightManager`.
- Perk total softcap:
  - Existing per-level scaling remains in place.
  - Added total stacked-effect softcap by perk category, so collecting many perks in the same category has diminishing returns.
  - Strong categories use a harsher curve: first 45 raw effect is full value, then 70%, 45%, and 25% segments.
  - Core categories use a medium curve: first 60 raw effect is full value, then 80%, 55%, and 35% segments.
  - Utility categories use a lighter curve: first 75 raw effect is full value, then 90%, 70%, and 50% segments.
  - Runtime stat snapshots, TacZ magazine/reload/fire-rate/melee-speed hooks, dodge, ammo efficiency, regeneration, gold reward, debug output, and inventory display now use the same total-softcapped effect path where applicable.

## Validation Completed
- Gradle unit tests:
  - `cd C:\Users\user\Documents\TacZ_Roguelike_Workspace\tac_rogue_mod`
  - `.\gradlew.bat test`
  - Result: success
- Build and reobf:
  - `.\gradlew.bat compileJava jar reobfJar`
  - Result: success
- Smoke tests run by user before the final perk total-softcap edit:
  - `/rogue_admin debug smoke monster`: `pass=13 skip=0 fail=0`
  - `/rogue_admin debug smoke objective`: `pass=35 skip=0 fail=0`
  - `/rogue_admin debug smoke encounter`: `pass=226 skip=0 fail=0`
  - `/rogue_admin debug smoke ai_matrix`: `pass=41 skip=0 fail=0`

## Not Revalidated Yet
- Full in-game smoke after the perk total-softcap edit.
- New FPS/perf comparison after this low-risk optimization commit.
- Long-run balance check for high perk counts.

## Deferred Optimization Ideas
- `MapGenerator` `BlockPos` to packed `long` rewrite. This needs ordering and overwrite behavior verification.
- Alert-level NBT read caching. This needs AI smoke coverage first because stale alert state can break stealth behavior.
- Async dungeon layout generation. Larger risk and not part of this low-risk pass.
- Broad sync protocol or ADS keepalive changes. Deferred until separately measured.

## Left Untouched
- Untracked `config/`, `logs/`, `patchouli_data.json`, and `tacz/`.
- Existing untracked docs and optimization reports not part of this commit.
- Shader/Oculus/Embeddium/modpack override settings.
