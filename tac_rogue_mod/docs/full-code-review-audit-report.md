# Full Code Review Audit Report

## Scope

- Target: `tac_rogue_mod`
- Mode: read-only code audit, no implementation fixes
- Sub-agent effort: `xhigh`
- Main CLI checks:
  - `git status --short`
  - `rg --files`
  - suspicious pattern search via `rg`
  - lang key comparison via JSON parsing
  - `.\gradlew.bat build`

## CLI Summary

- Java files: 122
- Java lines: 20,567
- Resource files: 18
- Docs files: 23
- `ja_jp.json` keys: 258
- `en_us.json` keys: 258
- Missing keys between `ja_jp` and `en_us`: none by direct key comparison
- Build result: success
- Build warning class:
  - Gradle deprecated features
  - Existing Mixin warnings
  - deprecated `ResourceLocation` constructors

## P0

### Multiplayer same-floor join can teleport players to an ungenerated origin

References:
- `src/main/java/com/levanilla/rogue/core/RunManager.java:175`
- `src/main/java/com/levanilla/rogue/core/RunManager.java:180`
- `src/main/java/com/levanilla/rogue/core/service/FloorService.java:84`

When another player is already on the same floor, the second player is teleported to their own `dungeonOrigin.above(2)` instead of the generated floor's actual origin/spawn. If their personal origin has not been generated, this can drop them into empty space.

Fix direction:
- Either make every player generate their own floor instance, or persist a shared floor instance/spawn and teleport joiners to that actual generated location.
- Do not use the joining player's ungenerated `dungeonOrigin` for same-floor join/retry.

## P1

### Vanilla HUD is canceled outside rogue context

References:
- `src/main/java/com/levanilla/rogue/client/ClientEventHandler.java:201`
- `src/main/java/com/levanilla/rogue/client/ClientEventHandler.java:218`

Health, food, armor, XP, and air overlays are canceled globally, but the custom HUD only renders in rogue context. Normal worlds can lose the vanilla HUD.

Fix direction:
- Check rogue context before canceling vanilla overlays.

### RogueInventory non-inventory tabs may still accept invisible slot clicks

References:
- `src/main/java/com/levanilla/rogue/client/RogueInventoryScreen.java:92`
- `src/main/java/com/levanilla/rogue/client/RogueInventoryScreen.java:1120`
- `src/main/java/com/levanilla/rogue/client/RogueInventoryScreen.java:1198`

Non-inventory tabs do not render the normal slots, but the container slots still exist. Invisible slot interaction can happen unless explicitly blocked.

Fix direction:
- Block slot click/drag/release outside the inventory tab, or move slots offscreen while non-inventory tabs are active.

### NPC menu action packets trust role/action without server-side NPC validation

References:
- `src/main/java/com/levanilla/rogue/networking/NpcMenuActionMessage.java:28`
- `src/main/java/com/levanilla/rogue/world/NpcManager.java:205`

The server accepts `role` and `action` strings from the client without validating that the player is still near the NPC that opened the menu. This bypasses the intended NPC-only shop/quest flow.

Fix direction:
- Store a short-lived server-side NPC menu session with entity id, role, dimension, and expiry.
- On action, validate distance, dimension, role, and allowed action.

### Loadout and difficulty stat baselines are reset by perk tick logic

References:
- `src/main/java/com/levanilla/rogue/core/service/PlayerPerkTickService.java:29`
- `src/main/java/com/levanilla/rogue/core/service/GearService.java:57`

The perk stat recalculation resets base max health, armor, speed, and stamina, which can erase loadout/difficulty intent.

Fix direction:
- Preserve baseline attributes.
- Remove/reapply only perk-owned modifiers by UUID.

### Quest partial progress can be overwritten by stale player NBT

References:
- `src/main/java/com/levanilla/rogue/core/RunManager.java:284`
- `src/main/java/com/levanilla/rogue/core/QuestManager.java:345`

Quest progress appears split between player persistent NBT and `QuestSavedData`. Partial progress can be saved to one source, then overwritten from the other on login.

Fix direction:
- Make `QuestSavedData` the source of truth.
- Use player NBT only for one-time migration, or version/timestamp conflict resolution.

### TacZ gun boss kills do not share the same boss reward path

References:
- `src/main/java/com/levanilla/rogue/core/event/TacZEventHandler.java:269`
- `src/main/java/com/levanilla/rogue/core/event/CombatEventHandler.java:318`

Boss rewards differ between TacZ gun kills and non-TacZ kills. The TacZ path gives gold and has a TODO for weapon rewards; the non-TacZ path handles weapon drops.

Fix direction:
- Extract boss reward handling to one shared method and call it from both paths.

### Ammo capacity upgrade is not calculated consistently

References:
- `src/main/java/com/levanilla/rogue/core/service/RogueItemFactory.java:119`
- `src/main/java/com/levanilla/rogue/core/service/InventoryRuleService.java:177`
- `src/main/java/com/levanilla/rogue/core/RunManager.java:114`

Ammo stack creation, inventory enforcement, and global/client synced level calculation disagree. In multiplayer, another player's larger ammo level can leak into global limits.

Fix direction:
- Introduce one player-scoped ammo capacity function and use it everywhere.

### TacZ dependency range is broader than the implementation supports

References:
- `build.gradle:76`
- `src/main/resources/META-INF/mods.toml:26`
- `src/main/java/com/levanilla/rogue/mixin/MixinAmmoItem.java:13`

Build is pinned to TacZ `1.1.7-hotfix2`, but `mods.toml` accepts `[1.0.3,)`. Direct API/Mixin use makes future/older versions risky.

Fix direction:
- Narrow `mods.toml` to verified TacZ versions, or add Mixin target compatibility checks.

### Modrinth pack has risky server env metadata

References:
- `tac_rogue_modpack/modrinth.index.json:39`
- `tac_rogue_modpack/modrinth.index.json:294`

Client/visual mods such as `leawind_third_person` and `rubidium-extra` are marked `server: "required"`.

Fix direction:
- Mark client-only mods as `server: "unsupported"` or separate client/server pack metadata.

## P2

### Perk/FloorClear/Shop screens are too fixed-width for small GUI scales

References:
- `src/main/java/com/levanilla/rogue/client/PerkScreen.java:74`
- `src/main/java/com/levanilla/rogue/client/FloorClearScreen.java:46`
- `src/main/java/com/levanilla/rogue/client/ShopScreen.java:97`

Several screens assume wide layouts and can overflow or create negative detail width at small scaled resolutions.

Fix direction:
- Add responsive layouts: fewer columns, vertical cards, scroll panels, or hide detail panes on narrow screens.

### Popup stack can exceed screen height

References:
- `src/main/java/com/levanilla/rogue/client/hud/NotificationManager.java:167`
- `src/main/java/com/levanilla/rogue/client/hud/NotificationManager.java:241`

Popup total height is computed but not clamped to available screen height.

Fix direction:
- Dynamically cap visible popups based on available vertical space.

### Screen openings can overwrite each other

References:
- `src/main/java/com/levanilla/rogue/client/ClientEventHandler.java:424`
- `src/main/java/com/levanilla/rogue/networking/OpenStarterGearMessage.java:17`

Welcome, starter gear, NPC, shop, and other screens can call `setScreen` directly without a queue or priority.

Fix direction:
- Add a simple client screen queue or priority guard.

### Common/networking classes contain client-only references

References:
- `src/main/java/com/levanilla/rogue/networking/OpenNpcMenuMessage.java:3`
- `src/main/java/com/levanilla/rogue/core/ClientSyncHandler.java:3`

Some common-side packet classes directly reference client classes.

Fix direction:
- Move client packet handling to a client-only handler package and invoke via DistExecutor.

### RogueInventory quest tab can spam sync requests

References:
- `src/main/java/com/levanilla/rogue/client/RogueInventoryScreen.java:277`
- `src/main/java/com/levanilla/rogue/client/RogueInventoryScreen.java:285`

If quest data is empty, render can send sync requests repeatedly.

Fix direction:
- Request once on tab open, or add a cooldown.

### Reroll from FloorClear opens normal perk flow

References:
- `src/main/java/com/levanilla/rogue/client/FloorClearScreen.java:73`
- `src/main/java/com/levanilla/rogue/networking/RogueActionMessage.java:150`

The client passes `"floor_clear"` to reroll, but server handling ignores context and opens a normal perk screen.

Fix direction:
- Track reroll context and reopen `FloorClearScreen` with new candidates.

### Boss entities may miss the `rogue:boss` tag

References:
- `src/main/java/com/levanilla/rogue/world/RoomManager.java:141`
- `src/main/java/com/levanilla/rogue/core/event/TacZEventHandler.java:270`

If boss tag assignment is inconsistent, reward and quest detection diverge.

Fix direction:
- Ensure every boss spawn path applies a shared boss tag.

### Spawn placement lacks collision and clearance validation

References:
- `src/main/java/com/levanilla/rogue/world/MapGenerator.java:545`
- `src/main/java/com/levanilla/rogue/world/RoomManager.java:69`

Mob/boss placement does not consistently validate collision, floor, and headroom.

Fix direction:
- Add shared safe spawn candidate selection.

### Block generation sends many immediate updates

References:
- `src/main/java/com/levanilla/rogue/world/MapGenerator.java:808`
- `src/main/java/com/levanilla/rogue/world/LobbyGenerator.java:57`

Dungeon/lobby regeneration can touch many blocks synchronously.

Fix direction:
- Use lower notification flags where safe, chunked work, version markers, and avoid full rebuild unless needed.

### Rarity reload multiplier is stored but not applied to normal reload

References:
- `src/main/java/com/levanilla/rogue/core/WeaponRarity.java:98`
- `src/main/java/com/levanilla/rogue/core/service/PlayerPerkTickService.java:153`

`RogueReloadMult` exists, but normal TacZ reload is not clearly affected.

Fix direction:
- Implement reload animation/action multiplier through TacZ hook/Mixin, or remove the displayed promise.

### Non-gun kills do not advance major kill quests

References:
- `src/main/java/com/levanilla/rogue/core/event/CombatEventHandler.java:317`
- `src/main/java/com/levanilla/rogue/core/event/TacZEventHandler.java:305`

TacZ kill path advances many quest types; non-gun kill path does not.

Fix direction:
- Share kill progression logic. Keep only weapon-specific quests gun-limited.

### Mixin policy can hide missing critical behavior

References:
- `src/main/resources/tac_rogue.mixins.json:2`
- `src/main/resources/tac_rogue.mixins.json:25`

`required: true` with `defaultRequire: 0` makes some failures quiet and others hard to reason about.

Fix direction:
- Separate required internal Mixins from optional external-mod Mixins.

## P3

### FloorSelection is fixed-width

References:
- `src/main/java/com/levanilla/rogue/client/FloorSelectionScreen.java:36`

Can overflow on narrow scaled resolutions.

### Boss bars overlap for multiple bosses

References:
- `src/main/java/com/levanilla/rogue/client/hud/BossBarRenderer.java:16`

Multiple boss bars draw at the same coordinates.

### Supply chest has maximum count but no minimum guarantee

References:
- `src/main/java/com/levanilla/rogue/world/MapGenerator.java:175`
- `src/main/java/com/levanilla/rogue/world/MapGenerator.java:233`

Some floors can generate zero supply caches.

### Extraction NPC can spawn at the player's exact position

References:
- `src/main/java/com/levanilla/rogue/core/event/SpawnAndWorldHandler.java:322`
- `src/main/java/com/levanilla/rogue/world/NpcManager.java:136`

Can create overlap/clicking problems in tight rooms.

### Missing or mojibake lang entries remain

References:
- `src/main/java/com/levanilla/rogue/core/event/CombatEventHandler.java:323`
- `src/main/java/com/levanilla/rogue/core/event/TacZEventHandler.java:274`
- `src/main/resources/assets/tac_rogue/lang/en_us.json:274`

Direct key count matches, but referenced translatable keys can still be absent, and one English string contains mojibake.

## Cross-Agent Notes

- Agent A and the main CLI both flagged HUD boundary issues and common/client side leakage.
- Agent C and the main CLI both flagged NPC action trust as a high-priority server validation issue.
- Agent B flagged several gameplay correctness issues that do not show in compile/build checks.
- Agent D's Modrinth findings are outside `tac_rogue_mod` Java code but affect the playable pack.
- Severity differs by deployment mode:
  - Single-player only: multiplayer same-floor P0 becomes lower practical priority.
  - Dedicated server/export: client-only references and Modrinth server env become higher priority.

## Recommended Fix Order

1. Fix P0 multiplayer same-floor teleport behavior, or explicitly disable shared-floor multiplayer logic.
2. Fix vanilla HUD global cancel and invisible inventory slot interaction.
3. Add server-side NPC menu session validation.
4. Fix loadout/stat baseline reset.
5. Fix quest persistence source of truth.
6. Unify boss kill reward handling.
7. Unify ammo capacity calculation.
8. Fix FloorClear reroll context.
9. Responsive pass for Perk/FloorClear/Shop/Popup.
10. Clean client-only packet references and Modrinth server env metadata.

## Verification Needed

- Normal vanilla overworld HUD visibility.
- RogueInventory STATUS/PERKS/QUEST tabs: invisible slot clicking.
- GUI scale 4 on small window: Perk/FloorClear/Shop/Popup.
- Two-player same-floor join/retry.
- Boss kill with TacZ gun and non-TacZ damage.
- Quest progress after partial progress, logout, relog.
- Ammo stack limit after capacity upgrades.
- Dedicated server classloading check.
- Modrinth client export and server export metadata check.
