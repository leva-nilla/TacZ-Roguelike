# Armor / Resistance Role Separation

## Summary
- `Armor` and `Resistance` currently overlap too much because both effectively make the player harder to kill against broad incoming damage.
- Keep `Armor` as the normal armor-attribute defense used for physical combat.
- Rework `Resistance` into special resistance for hazards, boss abilities, and debuffs.

## Goals
- `Armor` remains the main defense against normal mob attacks, melee pressure, and any damage path that respects Minecraft armor.
- `Resistance` no longer reduces every incoming hit.
- `Resistance` reduces:
  - explosion-like damage
  - fire/burn/lava/hot floor damage
  - poison, wither, magic, sonic, freeze-style damage
  - Tac Rogue boss ability damage
  - Tac Rogue boss debuff durations
- Cap special resistance at 65% so it cannot become full immunity.

## Implementation
- Add a shared resistance cap constant to `GameConstants`.
- In `CombatEventHandler`, expose helper methods:
  - total resistance effect
  - special damage multiplier
  - negative effect duration reduction
- In `LivingHurtEvent`, apply `Resistance` only when the damage source looks like a special/hazard source.
- In `TacRogueBossEntity`, apply the same special resistance multiplier to shockwave damage and boss-applied debuff durations.
- Update status UI so Resistance is displayed as special damage taken, not generic damage taken.
- Update Japanese and English perk descriptions to clarify the difference.

## Non-Goals
- Do not change `Armor` scaling or player armor attribute application.
- Do not modify TacZ directly.
- Do not add a new perk category; reuse the existing `RESISTANCE` category.

## Test Plan
- `.\gradlew.bat build` succeeds.
- Confirm code paths compile for:
  - player damage handling
  - boss shockwave/pressure abilities
  - status tab rendering
- In game, verify:
  - normal hits are no longer globally reduced by Resistance
  - fire/explosion/magic-like sources are reduced by Resistance
  - boss shockwave damage and debuffs are reduced by Resistance
  - Armor and Resistance descriptions no longer read as the same effect
