# TacZ Roguelike current implementation map

This document summarizes the current implementation in `tac_rogue_mod`.
It is meant as a working map before refactoring, not as player-facing documentation.

## Project shape

- Minecraft / Forge: `1.20.1`, Forge `47.4.18`
- Java: `17`
- Mod id: `tac_rogue`
- Current mod version: `0.6.0`
- Main dependencies:
  - `timeless-and-classics-zero`
  - `leawind-third-person`
  - compile-only `lr-tactical`
  - runtime `architectury-api`
- Main entrypoint:
  - `src/main/java/com/levanilla/rogue/TacRogue.java`

## High-level feature areas

### Run and floor flow

Core files:

- `core/RunManager.java`
- `core/ClientRunState.java`
- `core/ClientSyncHandler.java`
- `core/PlayerRunData.java`
- `core/service/FloorService.java`
- `world/MapGenerator.java`
- `world/ThemeManager.java`
- `world/RoomManager.java`
- `world/LobbyGenerator.java`

Responsibilities:

- Track per-player run state in memory through `RunManager`.
- Save/load run and quest data into player persistent NBT.
- Generate and clear dungeon floors in the rogue dimension.
- Teleport players between lobby and rogue dimension.
- Select themes and boss-floor behavior based on floor/world seed.

Current notes:

- `RunManager` still owns server-side run lifecycle, teleport logic, and floor generation calls.
- Client mirror fields now live in `ClientRunState`.
- String-based `SyncDataMessage` parsing now lives in `ClientSyncHandler`.
- It still has deprecated global methods for legacy compatibility.
- Multiplayer isolation is handled by per-player dungeon origins using UUID-derived offsets.

### Player tick rules

Core file:

- `core/event/PlayerTickHandler.java`
- `core/service/InventoryRuleService.java`
- `core/service/PlayerRegenService.java`
- `core/service/PlayerPerkTickService.java`

Responsibilities:

- Stamina ticking.
- Stealth range modifier.
- Adventure-mode enforcement.
- Custom natural regeneration replacement.
- Void return.
- Starter gear prompt in lobby.
- Inventory, slot, ammo, armor, offhand, and stack-size enforcement.
- Periodic perk stat application.

Current notes:

- `PlayerTickHandler` is now mostly an orchestration point for per-player tick rules.
- Inventory and dedicated-slot enforcement has been split into `InventoryRuleService`.
- Custom natural regeneration has been split into `PlayerRegenService`.
- Periodic perk stat, reload, and auto-loader logic has been split into `PlayerPerkTickService`.

### Combat and TacZ integration

Core files:

- `core/event/TacZEventHandler.java`
- `core/event/CombatEventHandler.java`
- `core/service/RoguePickupService.java`
- `core/ScalingEngine.java`
- `core/TacZRegistryHelper.java`
- `core/registry/TacZGunRegistry.java`
- `core/registry/AmmoDatabase.java`
- `core/registry/AttachmentDatabase.java`
- `core/registry/LrTacticalRegistry.java`

Responsibilities:

- Detect TacZ bullet damage.
- Apply damage, crit, headshot, shotgun, explosive, melee, and perk modifiers.
- Spawn damage indicators.
- Handle kill rewards, drops, quest progress, and enemy scaling.
- Read TacZ guns, ammo, and attachments for shop/drop catalog support.
- Create TacZ/LR Tactical item stacks through NBT/API fallback paths.

Current notes:

- TacZ bullet context is bridged between `TacZEventHandler` and `CombatEventHandler`.
- Rogue-dimension pickup routing has been split into `RoguePickupService`.
- Registry helpers and shop item creation are tightly coupled.
- There is one TODO in `TacZEventHandler` for guaranteed weapon drops.

### Economy, shop, stash, and gear

Core files:

- `core/CurrencyManager.java`
- `core/PriceManager.java`
- `core/StashSavedData.java`
- `core/service/ShopService.java`
- `core/service/GearService.java`
- `core/service/RogueItemFactory.java`
- `core/service/ShopPlacementService.java`
- `core/service/ShopUpgradeService.java`
- `core/registry/ShopCatalog.java`
- `world/StashBlock.java`

Client/UI files:

- `client/RogueInventoryScreen.java`
- `client/StashScreen.java`
- `client/StarterGearScreen.java`

Responsibilities:

- Gold storage and spending.
- Buy/sell handling.
- Upgrade handling for stash, inventory, ammo capacity, melee, and random perk purchases.
- Starter loadout generation.
- Player stash persistence.
- Inventory/shop/stash UI.

Current notes:

- `ShopService` handles shop action routing, selling, stash sync, and compatibility wrappers.
- `RogueItemFactory` creates TacZ, LR Tactical, ammo, attachment, and recovery item stacks.
- `ShopPlacementService` places purchased items into dedicated slots, unlocked inventory slots, or stash, and handles refunds.
- `ShopUpgradeService` handles stash, inventory, ammo capacity, melee, and random perk purchases.
- `RogueInventoryScreen` is the largest file in the project and likely combines multiple tabs and behaviors.
- Stash line limits are enforced mostly at service/UI boundaries; internal saved-data access should be reviewed before trusting it.

### Perks and quests

Core files:

- `core/PerkDefinition.java`
- `core/PerkGenerator.java`
- `core/QuestManager.java`
- `core/QuestSavedData.java`

Client files:

- `client/PerkManager.java`
- `client/PerkScreen.java`
- `client/FloorClearScreen.java`

Networking:

- `networking/PerkActionMessage.java`
- `networking/OpenPerkChoiceMessage.java`
- `networking/OpenPerkScreenMessage.java`
- `networking/OpenFloorClearScreenMessage.java`

Responsibilities:

- Generate perk choices.
- Store selected perks as player tags.
- Apply perk effects from tags in combat/tick systems.
- Track quest progress and chapter data.
- Open perk/floor-clear screens from server messages.

Current notes:

- Perks are represented mainly as player tags.
- This keeps storage simple, but makes validation and deduplication important.
- Any client-to-server perk action needs strict validation against server-generated choices.

### Networking

Core file:

- `networking/TacRogueNetworking.java`

Registered messages:

- `SyncRunMessage`
- `OpenPerkScreenMessage`
- `OpenFloorClearScreenMessage`
- `RogueActionMessage`
- `SyncStashMessage`
- `SyncDataMessage`
- `OpenStarterGearMessage`
- `OpenShopMessage`
- `PerkActionMessage`
- `SyncGoldMessage`
- `SyncPerksMessage`
- `DamageIndicatorMessage`
- `DropIndicatorMessage`
- `OpenPerkChoiceMessage`

Current notes:

- There are newer typed packets like `SyncGoldMessage`, `SyncPerksMessage`, `DamageIndicatorMessage`, and `DropIndicatorMessage`.
- There is also a broad string-based `SyncDataMessage` protocol used for many unrelated payloads.
- Recommended direction: gradually move `SyncDataMessage` cases into typed packets.

### Client and HUD

Core files:

- `client/ClientEventHandler.java`
- `client/ClientKeyBinds.java`
- `client/ClientReloadListener.java`
- `client/DynamicLightManager.java`
- `client/hud/HudRenderer.java`
- `client/hud/HotbarRenderer.java`
- `client/hud/BossBarRenderer.java`
- `client/hud/DamageIndicatorRenderer.java`
- `client/hud/NotificationManager.java`
- `client/hud/WelcomeScreen.java`

Responsibilities:

- Client events and GUI opening.
- Custom HUD rendering.
- Damage/drop indicators.
- Keybinds.
- Dynamic light/flashlight support.
- Welcome/title/inventory screen behavior.

Current notes:

- Client run mirror state now lives in `ClientRunState`.
- Some UI/HUD paths still depend on legacy `RunManager` accessors that delegate to `ClientRunState`.

### Mixins

Config:

- `src/main/resources/tac_rogue.mixins.json`

Mixin classes:

- Common:
  - `MixinSlot`
  - `MixinWitherBoss`
  - `MixinItemStack`
  - `MixinAmmoItem`
  - `MixinInventory`
- Client:
  - `MixinClientLevel`
  - `MixinCreateWorldScreen`
  - `MixinInventoryScreen`
  - `MixinAbstractContainerScreen`
  - `MixinTickAnimationEvent`
  - `MixinLocalPlayerDraw`
  - `MixinTitleScreen`

Current notes:

- Mixins are used for inventory restrictions, title/world creation flow, rendering/animation hooks, and TacZ/ammo behavior.
- These should be treated as high-risk integration points during refactoring.

## Largest files

- `client/RogueInventoryScreen.java`: 1204 lines
- `core/event/CombatEventHandler.java`: 647 lines
- `world/MapGenerator.java`: 629 lines
- `core/event/SpawnAndWorldHandler.java`: 430 lines
- `core/registry/AttachmentDatabase.java`: 420 lines
- `core/event/TacZEventHandler.java`: 406 lines
- `client/hud/WelcomeScreen.java`: 378 lines
- `core/registry/TacZGunRegistry.java`: 357 lines
- `core/RunManager.java`: 338 lines
- `client/ClientEventHandler.java`: 335 lines

These are the main refactor pressure points.

## Current organization problems

1. Mojibake in Japanese comments and existing docs
   - Many comments are unreadable.
   - This makes later refactoring riskier because intent is hidden.

2. Mixed server/client responsibility
   - `RunManager` contains server run state, client mirror state, sync parsing, teleporting, and floor generation calls.

3. Broad string sync protocol
   - `SyncDataMessage` routes many unrelated commands by string prefixes.
   - This is quick to extend but weakly typed and easy to break silently.

4. Large event handlers
   - `PlayerTickHandler`, `CombatEventHandler`, and `TacZEventHandler` each own multiple gameplay systems.
   - Bugs in one subsystem are harder to isolate.

5. Shop service has too many reasons to change
   - It owns purchase validation, upgrades, item construction, placement, stash overflow, and refunds.

6. Perk state relies on player tags
   - Simple and portable, but needs stricter server-side validation and a single parsing/ownership boundary.

## Suggested refactor order

### Phase 1: Document and stabilize

- Keep behavior unchanged.
- Fix or replace unreadable comments in files being touched.
- Add a small `docs/` map for each major system as it is refactored.
- Do not rewrite mixins first.

### Phase 2: Split low-risk services

Targets:

- Extract inventory enforcement from `PlayerTickHandler`.
- Extract health/stamina/regen logic from `PlayerTickHandler`.
- Extract item construction from `ShopService` into something like `RogueItemFactory`.
- Extract stash overflow/refund placement from `ShopService` into something like `InventoryPlacementService`.

### Phase 3: Untangle run sync

Targets:

- Move client-only run mirror state out of `RunManager`.
- Replace major `SyncDataMessage` prefixes with typed packets.
- Keep temporary adapters until all callers are migrated.

### Phase 4: Harden gameplay authority

Targets:

- Validate perk choices server-side.
- Validate all shop action IDs against a catalog, not arbitrary client strings.
- Clamp ammo and stack changes at one boundary.
- Review stash saved-data methods so internal storage cannot exceed unlocked capacity.

### Phase 5: UI split

Targets:

- Split `RogueInventoryScreen` by tab/component.
- Keep rendering helpers pure where possible.
- Keep network actions out of rendering code.

## Immediate next candidates

Best first cleanup with low gameplay risk:

1. `PlayerTickHandler`
   - Inventory enforcement has been extracted into `InventoryRuleService`.
   - Health regeneration has been extracted into `PlayerRegenService`.
   - Perk stat application has been extracted into `PlayerPerkTickService`.
   - Next: consider a small `PlayerEnvironmentTickService` for void return, adventure mode, and starter prompt.

2. `ShopService`
   - Item creation has been extracted into `RogueItemFactory`.
   - Purchased-item placement has been extracted into `ShopPlacementService`.
   - Upgrade purchases have been extracted into `ShopUpgradeService`.
   - Next: tighten sell validation and reduce compatibility wrappers once callers are migrated.

3. `RunManager`
   - Client-side static mirror fields have been moved to `ClientRunState`.
   - String sync parsing has been moved to `ClientSyncHandler`.
   - Next: gradually replace `SyncDataMessage` prefixes with typed packets.

4. `CombatEventHandler`
   - Pickup routing has been extracted into `RoguePickupService`.
   - Next: extract drop generation and reward calculation, but keep this as a separate compile-tested phase.

5. `RogueInventoryScreen`
   - Split only after data flow is clearer.
   - UI refactors tend to create accidental behavior changes if done too early.

