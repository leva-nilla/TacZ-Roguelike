# Multiplayer Floor Instances

## Goal

Support multiplayer as personal progression with shared floor instances.

Difficulty remains world-wide. Each player keeps their own maximum reached floor, quests, gold, stash, perks, and upgrades. Entering a floor can either create a private solo instance or join/create a public co-op instance for that floor.

## Entry Modes

- Solo
  - Creates a private instance immediately.
  - Other players cannot join it.
- Public Co-op
  - If a public instance for the selected floor is waiting or active, eligible players join it.
  - If no public instance exists, a waiting instance is created.
  - Waiting lasts 15 seconds unless the host starts early.
  - The first implementation supports Solo and Public only. Party Co-op is reserved for a later pass.

## Eligibility

- A player cannot join a floor above their own maximum reached floor.
- Clearing a floor unlocks the next floor for every participating player in that instance.
- Replaying older floors is allowed, and joins an existing public instance for that floor if one exists.

## Instance Lifecycle

Each floor instance has:

- instanceId
- floorNumber
- mode: SOLO or PUBLIC
- state: WAITING, ACTIVE, CLEARED
- ownerUuid
- participants
- initialParticipantCount
- origin
- createdTick
- activeTick

Rules:

- Last participant leaving, returning to lobby, disconnecting, or dying out of the floor destroys the instance.
- Destroying an instance removes its mobs, boss, extraction NPC, generated chest state, and protected drops.
- Solo instances always use a player-specific origin.
- Public instances use a floor-specific public origin so all eligible players entering the same floor can converge.

## Scaling

- Initial generation scales by initialParticipantCount.
- Mid-run join is allowed.
- Mid-run join increases normal monster health and adds a small reinforcement wave.
- Boss floor mid-run join is allowed only while boss HP ratio is above 70%.
- Boss HP is increased slightly for mid-run join.

## Rewards And Progress

- Kill reward: killer only.
- Kill/headshot/weapon mastery style quest progress: actor only.
- Floor clear quest progress: all active participants.
- Boss quest progress: all active participants.
- Boss reward: all active participants.
- Chests: personal claim per player, even when the chest block is shared.
- Drops: owner protected. Killer owns enemy drops unless later changed to party-shared.

## Ownership Tags

World entities and block entities created for a floor must carry ownership context:

- TacRogueInstanceId
- TacRogueFloor
- TacRogueOwnerUuid, for solo/private ownership or protected drops
- TacRogueMode

Combat, pickup, chest, cleanup, target selection, and floor clear logic should prefer instanceId over nearest-player inference.
