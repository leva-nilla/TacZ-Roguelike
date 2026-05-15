# Early Floor Mob Density Prompt

## Goal
Reduce excessive enemy density on floor 1 while keeping later floors and boss floors threatening.

## Current Issue
The current dungeon generation spawns enemies per eligible room.

- `RoomManager.getMobCountForFloor(level, floor)` returns a per-room count.
- `MapGenerator.generateRoom(...)` applies that count to every eligible room.
- Floor 1 usually has many generated rooms, so even a small per-room count can create a high total enemy count.

## Desired Behavior
Use a floor-wide enemy budget for normal floors, then distribute that budget across eligible combat rooms.

## Proposed Balance
- Floor 1 target: about 10-14 enemies total.
- Floor 2 target: about 14-18 enemies total.
- Floor 3 target: about 18-24 enemies total.
- Floor 4 target: about 24-30 enemies total.
- Floor 5 boss floor: keep existing boss behavior, but optionally keep side-room adds modest.
- Later floors scale gradually from the new floor-wide budget instead of multiplying a per-room count by every room.

## Implementation Plan
1. Keep `RoomManager.spawnMobs(...)` as the low-level spawn function.
2. Replace normal-floor spawn logic in `MapGenerator`:
   - collect eligible combat rooms first;
   - calculate a floor-wide total mob budget;
   - distribute enemies across rooms with a small per-room cap.
3. Add helper methods in `RoomManager`:
   - `getTotalMobBudgetForFloor(ServerLevel level, int floor, int eligibleRoomCount)`
   - `getMaxMobsPerRoomForFloor(int floor)`
4. Keep multiplayer scaling, but soften it so adding players does not multiply the full room count explosively.
5. Keep boss floors separate from this pass unless the side-room mobs still feel excessive after testing.

## Constraints
- Do not reduce enemy damage or HP in this pass.
- Do not change room generation.
- Do not change rewards/economy yet, even if lower enemy counts may later need gold tuning.
- Do not change normal mob AI.
- Rebuild the mod and regenerate the `.mrpack` after implementation.

## Verification
- `./gradlew.bat compileJava`
- `./gradlew.bat build`
- Regenerate `TacZ_Roguelike_v0.5.0-beta.mrpack`
- Verify the archive contains the updated `overrides/mods/tac_rogue-0.5.0-beta.jar`
