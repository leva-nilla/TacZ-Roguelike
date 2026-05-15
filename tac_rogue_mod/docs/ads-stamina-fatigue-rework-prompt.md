# ADS Stamina Fatigue Rework

## Summary
- ADS stamina fatigue must be deterministic and easy to reason about.
- Sprint/jump/ADS can consume stamina down to 0.
- Once stamina reaches 0, sprint and jump remain locked until stamina recovers to `STAMINA_EXHAUST_RECOVERY`.
- ADS is allowed during recovery, but ADS self-damage only happens while stamina is continuously 0.
- If stamina becomes greater than 0 by any recovery source, the ADS zero-stamina timer, speed penalty, and damage cadence are immediately cleared.

## Required Behavior
- Normal stamina:
  - Sprint consumes stamina every tick.
  - ADS consumes stamina every tick.
  - Jump consumes a fixed amount.
  - If stamina reaches 0, set the general exhausted lock.
  - General exhausted lock only controls sprint/jump availability.
  - General exhausted lock is released once stamina reaches `STAMINA_EXHAUST_RECOVERY` or the current max stamina if lower.
- ADS zero-stamina overuse:
  - ADS may remain active even while general exhausted.
  - While ADS is active and stamina is exactly 0, increment an ADS zero timer.
  - After `STAMINA_ADS_EXHAUST_GRACE_TICKS`, apply the ADS speed penalty and deal periodic self-damage.
  - If stamina becomes greater than 0 at any point, reset ADS zero timer and clear the ADS speed penalty immediately.
  - If ADS is released, reset ADS zero timer and clear the ADS speed penalty immediately.
  - Do not deal ADS self-damage when stamina is greater than 0, even if the player is still under the general exhausted lock.
- HUD:
  - The exhausted color still represents the general exhausted lock.
  - The general exhausted color can remain active until recovery reaches the threshold, but this must not imply ADS damage is active.
- Vanilla hunger:
  - Rogue stamina must not use vanilla food level 0 as a lock signal.
  - Food level 0 triggers vanilla starvation damage, which looks like lingering ADS self-damage.
  - In Rogue/lobby dimensions, keep synced food at 1 or higher and cancel vanilla starvation damage defensively.
- Damage routing:
  - ADS fatigue damage uses direct HP reduction instead of `Player#hurt`.
  - Damage amount is `maxHealth * STAMINA_ADS_EXHAUST_DAMAGE_PER_SECOND`; the constant is a ratio and currently equals 1% per second.
  - Direct reduction is non-lethal and leaves the player at 1 HP minimum.
  - The server sends a health sync packet immediately after direct HP reduction.
  - If another system restores HP on the same tick, ADS fatigue keeps a short-lived forced health target and reapplies it for 30 ticks.
  - Creative players are affected for debugging; spectators are excluded.
- ADS input authority:
  - TacZ server-side aiming sync may become false around stamina exhaustion.
  - The client sends a lightweight ADS input keepalive while the use key is held with a TacZ gun.
  - Server stamina logic treats a fresh ADS input keepalive as aiming only while the player is still holding a gun.

## Implementation Direction
- Rebuild `StaminaManager.tick` around explicit per-tick phases:
  1. Read current/max/aiming/sprinting/exhausted.
  2. Calculate drain for allowed actions.
  3. Apply drain or regeneration.
  4. Update general exhausted lock.
  5. Update ADS overuse state from the final stamina value.
  6. Persist/sync final values.
- Avoid calling `getStamina(player)` from ADS damage checks after local stamina has already been calculated for the current tick.
- ADS penalty checks must use the final local stamina value passed into the ADS overuse updater.
- Remove duplicated reset paths that run before the final stamina value is known.

## Test Plan
- Build with `.\gradlew.bat build`.
- In game:
  - Hold ADS until stamina reaches 0 and keep holding ADS.
  - Confirm self-damage starts only after the grace period.
  - Confirm HP decreases by about 1% of max HP per second.
  - Recover stamina above 0 by natural regen or stamina item and keep holding ADS.
  - Confirm self-damage stops immediately while stamina is above 0.
  - Confirm sprint remains locked until stamina reaches 35.
  - Confirm sprint can be used again after 35.
  - Confirm releasing ADS clears the ADS speed penalty and zero timer.
  - Confirm exhaustion/recovery does not trigger vanilla starvation damage.
  - Confirm ADS overuse damage still applies in lobby and dungeon after the grace period.
  - Confirm holding right-click with a TacZ gun at 0 stamina advances the ADS overuse timer even if TacZ aiming progress drops.
