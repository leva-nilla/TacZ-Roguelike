# Weapon Rarity Unification Prompt

## Goal

Initial loadout weapons and shop-purchased weapons should receive the same rarity identity as dropped weapons.
The rarity should be visible on the item name and should have gameplay effects where the current code can reliably apply them.

## Current Behavior

- Dropped weapons are generated in `CombatEventHandler.generateWeaponDrop`.
- Dropped weapons call `WeaponRarity.rollRarity` and `WeaponRarity.applyRarity`.
- Initial loadout weapons are created manually in `GearService.giveStarterGear`.
- Shop weapons are created through `RogueItemFactory.createGunStack`.
- Quest rare weapon rewards also use `RogueItemFactory.createItemStack`.
- `WeaponRarity` stores:
  - `RogueRarity`
  - `RogueDamageMult`
  - `RogueReloadMult`
  - `RogueMagMult`

## Problems Found

- Initial loadout weapons currently do not get rarity metadata.
- Shop-purchased weapons currently do not get rarity metadata.
- Rare quest reward weapons currently use the shop generation path, so they also need the same rarity handling.
- Damage multiplier is applied in `TacZEventHandler.onEntityHurtByGun`.
- Magazine multiplier exists in NBT but is not clearly applied to effective magazine capacity.
- Reload multiplier exists in NBT but there is no confirmed TacZ reload-duration hook in the current code.
- `WeaponRarity.getStarsDisplay` currently contains mojibake-style star text and should be normalized.

## Proposed Design

### 1. Centralize Weapon Rarity Application

Add rarity-aware helper methods to `RogueItemFactory`:

- `createGunStack(String fullId, WeaponRarity.Rarity rarity)`
- `createGunStackForFloor(String fullId, int floor, RandomSource random)`
- `applyDefaultRarityIfMissing(ItemStack stack, WeaponRarity.Rarity rarity)`

Existing `createGunStack(String fullId)` should remain available and should apply `COMMON` by default.

### 2. Initial Loadout Rarity

Initial loadout guns should receive `COMMON` rarity.

Reason:
- Initial weapons should show the same rarity format.
- Starting weapons should not randomly spike run power.
- This makes starter gear clear and stable.

### 3. Shop Weapon Rarity

Shop-purchased weapons should receive a deterministic rarity based on:

- shop floor
- item id

Suggested rarity band:

- Floor 1-4: Common mostly, small Uncommon chance.
- Floor 5-9: Common/Uncommon, rare Rare.
- Floor 10-19: Uncommon/Rare becomes more likely.
- Floor 20+: Epic can appear rarely.
- Legendary should remain drop/boss/quest-oriented unless explicitly expanded later.

The first implementation should be conservative:

- Shop weapons should be usually Common or Uncommon.
- Rare and above should be possible later, but not common.

### 4. Quest Rare Weapon Reward Rarity

Rare weapon quest rewards should force at least `RARE`.

Suggested rule:

- Roll normal floor-based rarity.
- If result is below Rare, upgrade it to Rare.

This makes the reward match the promise without making it always Epic/Legendary.

### 5. Dropped Weapon Rarity

Dropped weapon rarity behavior should remain floor-random.
Use the same helper path as shop/quest where possible, but preserve existing drop progression.

### 6. Rarity Display

Normalize displayed stars to ASCII-safe text:

- `[C] Common`
- `[U] Uncommon`
- `[R] Rare`
- `[E] Epic`
- `[L] Legendary`

Or use plain repeated `*` if compact display is preferred.
Avoid mojibake-prone star characters.

The item name should still be colored by rarity.

### 7. Gameplay Effects To Verify

Damage:
- Already applied through `WeaponRarity.getDamageMult` in TacZ damage handling.
- Keep this path and verify compile.

Magazine:
- Apply `RogueMagMult` to effective magazine capacity wherever effective base magazine is calculated.
- Make sure ammo efficiency caps and autoloader caps use the same effective magazine size.

Reload:
- Current code does not expose a reliable TacZ reload-duration hook.
- Do not fake reload-speed behavior unless a stable event/API is found.
- Keep `RogueReloadMult` in NBT for future support.
- Document that reload multiplier is metadata-only for now if no hook exists.

### 8. Debug Scope

Run:

- `gradlew compileJava`
- `gradlew build`
- UTF-8 JSON parse checks for `en_us.json` and `ja_jp.json`
- static search for all weapon creation paths:
  - starter gear
  - shop purchase
  - drop generation
  - quest rare reward

Expected verification:

- Starter gun has `RogueRarity`.
- Shop gun has `RogueRarity`.
- Dropped gun still has `RogueRarity`.
- Quest rare reward gun has at least Rare.
- Damage multiplier is still read in TacZ gun damage.
- Magazine multiplier is used for effective magazine limits.

