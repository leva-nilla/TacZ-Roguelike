# Debug GUI Prompt

## Goal
Turn `/rogue_admin debug` into an admin-only in-game debug menu with creative-style controls for spawning and inspecting roguelike items.

## Entry Point
- `/rogue_admin debug`
  - Opens the debug GUI for the command sender.
- Existing subcommands remain available:
  - `/rogue_admin debug state`
  - `/rogue_admin debug weapon`
  - `/rogue_admin debug quests`
  - `/rogue_admin debug shop <floor>`
  - `/rogue_admin debug givegun <itemId> <rarity>`
  - `/rogue_admin debug advancequest <type> <amount>`
  - `/rogue_admin debug sync`

## Permission Model
- Keep the existing `/rogue_admin` permission gate.
- Server-side debug actions must re-check the same permission.
- Client GUI visibility is not enough authorization by itself.

## First GUI Scope
Build a focused debug menu rather than a full creative inventory clone.

### Tabs
- `Weapons`
  - Browse all known shop/gun entries from `TacZRegistryHelper.getAllShopItems()`.
  - Filter by weapon category.
  - Search by item id or display name.
  - Select rarity with buttons: Common, Uncommon, Rare, Epic, Legendary.
  - Click `Give` to spawn the selected gun through `RogueItemFactory.createGunStack(...)` and `ShopPlacementService.placeRewardItem(...)`.
- `State`
  - Show gold, current floor, max floor, run active, floor cleared, shop floor, difficulty.
  - Buttons:
    - `+1000 Gold`
    - `Sync`
- `Quest`
  - Select quest type.
  - Step amount selector: 1, 5, 10, 100.
  - Button: `Advance Quest`
- `Perks`
  - Browse all `PerkDefinition.Category` values.
  - Select `PerkDefinition.Modifier`.
  - Select level 1-10.
  - Click `Give` to add a unique player tag using `PerkDefinition.toTag() + ":#<short serial>"`.
  - Sync player data after adding the perk.

## Data Flow
- Server command opens GUI by sending a new client packet.
- Client GUI sends debug action packets to server.
- Server packet handler validates admin permission, parses action, executes existing service methods, and syncs player data.

## New Files
- `client/DebugMenuScreen.java`
- `networking/OpenDebugMenuMessage.java`
- `networking/DebugActionMessage.java`

## Changes
- Register new network messages in `TacRogueNetworking`.
- Add an executable action to `/rogue_admin debug` without removing existing child commands.
- Reuse existing item creation and placement services.

## Constraints
- Do not add reset/delete/wipe buttons in this pass.
- Do not expose debug actions to non-admin players.
- Do not duplicate large creative inventory logic.
- Do not change normal gameplay item acquisition.
- Keep UI compact and readable at common resolutions.
- Avoid timestamp suffixes for debug perk duplication; use a short persistent serial instead.

## Verification
- `./gradlew.bat compileJava`
- `./gradlew.bat build`
- Regenerate `.mrpack`
- Manual checks:
  - `/rogue_admin debug` opens GUI.
  - Selecting any weapon + rarity gives the expected rarity weapon.
  - Selecting any perk category + modifier + level gives the expected perk tag.
  - Non-selected subcommands still work.
  - Server-side handler rejects non-admin debug action packets.
