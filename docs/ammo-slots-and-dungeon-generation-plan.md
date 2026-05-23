# Ammo Slots and Dungeon Generation Notes

Date: 2026-05-23

## E1-E3 Ammo Placement

### Issue

Ammo could no longer stay in `E1-E3`.

### Cause

`InventoryRuleService` still had legacy migration logic that treated player inventory slots `9-11` as old ammo slots and periodically moved ammo from those slots into the dedicated ammo reserve slots.

Current slot model:

- `0-1`: gun slots
- `2`: melee slot
- `3-11`: combat item slots
- `9-11`: extended combat item slots shown as `E1-E3`
- `12-15`: dedicated ammo reserve slots
- `16-35`: expandable inventory/backpack

Because `E1-E3` are now valid combat item slots, moving ammo out of them is wrong. Ammo is allowed there as overflow/general combat carry.

### Fix Applied

Removed the legacy `migrateLegacyAmmoSlots()` call and method from:

- `tac_rogue_mod/src/main/java/com/levanilla/rogue/core/service/InventoryRuleService.java`

This keeps ammo in `E1-E3` when the player intentionally places it there.

### Verification

- `.\gradlew.bat compileJava`
- `.\gradlew.bat jar reobfJar`
- Synced rebuilt `tac_rogue-0.8.0.jar` and `tacz_startup_helper-0.8.0.jar` to the Modrinth profile.

Manual check still needed:

- Put ammo into `E1-E3`.
- Wait more than the inventory enforcement interval.
- Confirm ammo stays there and stack limits still apply.
- Confirm shift-click/pickup still prioritizes dedicated ammo slots before overflow slots.

## Same-Floor Dungeon Variation Plan

### Current State

The generator already has a variation hook:

- `DeepProgressData.floorSeedSalt`
- `FloorEntryModeHandler` instance seed/salt creation
- `MapGenerator.generateRoom(..., runSeed, floorSeedSalt, instanceId, mode, participantCount, ...)`

So the current implementation is not purely deterministic by floor number. However, the seed path is split between run data and floor instance creation, and the visible layout vocabulary is limited enough that the same floor can still feel familiar.

### Goal

Entering the same floor multiple times should produce noticeably different maps without breaking multiplayer consistency, chest claim logic, clear checks, or boss-floor readability.

### Proposed Implementation

1. Add a single generation context

   Create a small immutable context, for example `FloorGenerationContext`, containing:

   - `runSeed`
   - `floor`
   - `attemptIndex`
   - `floorSeedSalt`
   - `instanceId`
   - `entryMode`
   - `participantCount`

   Use this as the source of truth for map generation seeds instead of scattering ad hoc `System.nanoTime()` usage across entry paths.

2. Track floor generation attempts

   Store and increment a per-run or per-player `floorAttemptIndex`.

   Increment it when:

   - starting a new floor
   - retrying the current floor
   - selecting a previously unlocked floor
   - creating a new public floor instance after the previous one is cleared

   Do not increment it when a player merely joins an already active/preparing public instance.

3. Derive layout seed explicitly

   Derive a layout seed from:

   ```text
   runSeed ^ floor ^ floorSeedSalt ^ attemptIndex ^ instanceId hash
   ```

   Keep generation deterministic inside a single active instance, but different between separate attempts.

4. Add layout hash anti-repeat

   After planning rooms/corridors, compute a lightweight `layoutHash` from:

   - layout pattern
   - room centers/sizes
   - corridor connections
   - special room positions

   Keep the last few hashes per player/run. If the same floor rerolls into a recent hash, reroll a bounded number of times.

5. Expand visible layout variation

   Add more variability in places the player actually notices:

   - room size bands
   - room type presets
   - corridor width/turn style
   - loop connection density
   - cover/debris placement
   - light placement
   - chest alcove positions
   - special room selection

   This is more important than only changing the random seed. A different seed with the same small pattern pool still feels stale.

6. Preserve balance constraints

   Hard constraints:

   - spawn safety radius remains valid
   - all rooms remain connected
   - enemy spawn count stays within floor scaling budget
   - generated chest count matches chest claim limits
   - boss floors keep a readable main route
   - public multiplayer players share the exact same generated instance

7. Add tests/smoke checks

   Add a static generation smoke test that generates the same floor multiple times with different attempts and records:

   - `layoutHash`
   - room count
   - corridor count
   - chest count
   - spawn count
   - connected graph result

   Pass criteria:

   - no disconnected layout
   - chest count and spawn count remain in expected bounds
   - same-floor `layoutHash` has enough variation across attempts

### Suggested Implementation Order

1. Implement `FloorGenerationContext` and attempt index plumbing.
2. Route `FloorEntryModeHandler` and `FloorService` through that context.
3. Add `layoutHash` logging/debug command.
4. Add anti-repeat reroll with a small bounded retry count.
5. Expand room/deco preset pools.
6. Add static/smoke tests.

### Notes

Do not start by only adding more decoration. That may improve screenshots, but it will not guarantee that the same floor stops feeling repeated. The seed and layout vocabulary need to be fixed first.
