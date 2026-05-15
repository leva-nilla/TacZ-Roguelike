# Dungeon Generation / Lobby FPS Optimization

## Goals
- Make dungeon generation complete faster without causing large one-tick stalls.
- Prevent players from seeing partially cleared or partially rebuilt dungeon geometry while a floor is regenerating.
- Reduce lobby-side FPS cost from systems that only matter during dungeon combat.

## Implementation
- Collapse duplicate pending block writes in `MapGenerator.GenerationJob`.
  - Keep only the final `BlockState` per `BlockPos`.
  - Preserve tick-split placement so generation still spreads work over multiple ticks.
- Increase the default block placement budget modestly after duplicate writes are removed.
- Use an opaque retry/deploy staging room in the Rogue dimension for players who start a floor while already inside the Rogue dimension.
  - This includes retry, next-floor deployment from a cleared floor, and manual past-floor entry.
  - Clear the staging room once the generated floor is ready and the player is teleported to the real spawn point.
- Restrict dynamic flashlight lighting to the actual dungeon dimension and add a no-active-lights fast path for the light mixin.

## Expected Result
- Less pending work per generated floor.
- No visible partial generation during retry or in-dungeon floor transitions.
- Lobby rendering avoids dynamic-light lookups unless a flashlight beam is actively present.

## Verification
- `.\gradlew.bat build`
- Retry a floor and confirm the player waits in an enclosed deploy room, then lands in the generated floor.
- Clear a floor, start the next floor from inside the dungeon, and confirm partial generation is not visible.
- In the lobby, compare FPS with flashlight off/on and confirm dynamic lighting does not run there.
