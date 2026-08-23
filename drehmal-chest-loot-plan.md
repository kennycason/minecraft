# Drehmal Chest Loot Injection Plan

## Goal

Randomly enrich existing containers in the local Drehmal Apotheosis world so exploration occasionally produces more exciting rewards.

## Current Crash Note

Before editing loot, resolve or work around the current crash source:

- Recent server crashes are caused by `mcdar:golem_kit_golem` from MC Dungeons Artifacts.
- Crash reports show `Description: Ticking entity`.
- The failing code path is `chronosacaria.mcdar.goals.FollowSummonerGoal.shouldContinue`.
- The null target is the golem's summoner/owner after `ChloeDragon` dies or disconnects.
- The current save has multiple saved `mcdar:golem_kit_golem` entities in `saves/Drehmal Apotheosis/entities/r.0.-1.mca`, all with `SummonerUUID=3e30a6b5-baef-4a07-9289-dae484ffaf0e`.

Recommended first step: back up the world, then remove or kill the stale `mcdar:golem_kit_golem` entities before doing loot edits.

## Safety Rules

- Do not edit while Minecraft or the integrated server is running.
- Make a full copy of the world folder before any write.
- Work against the copy first.
- Prefer changing only entity/container NBT, not terrain or player data.
- Only insert into empty inventory slots.
- Preserve existing chest contents.
- Produce a before/after summary with counts.

## Target World

Likely local profile:

```text
~/Library/Application Support/ModrinthApp/profiles/Drehmal Apotheosis_ Primal Journey
```

Likely save:

```text
~/Library/Application Support/ModrinthApp/profiles/Drehmal Apotheosis_ Primal Journey/saves/Drehmal Apotheosis
```

## Container Scope

Initial pass:

- `minecraft:chest`
- `minecraft:trapped_chest`

Optional later pass:

- `minecraft:barrel`
- shulker boxes
- modded containers if they are confirmed safe to edit

## Loot Roll

For every eligible container:

- Roll a 1 in 20 chance.
- If selected, add 1 to 3 reward stacks.
- Add only to empty slots.
- Skip full containers.
- Use deterministic seed logging so the result can be audited or reproduced.

## Item Pool Strategy

Preferred:

1. Inspect local datapacks, loot tables, item tags, and mod jars for rare/special item IDs.
2. Prefer Drehmal-specific relics or unique named items if their IDs and NBT format are clear.
3. Prefer valid item IDs already present in local loot tables over guessed IDs.

Fallback vanilla/modded-safe pool:

```text
minecraft:diamond
minecraft:emerald
minecraft:ancient_debris
minecraft:netherite_scrap
minecraft:golden_apple
minecraft:enchanted_golden_apple
minecraft:totem_of_undying
minecraft:experience_bottle
minecraft:ender_pearl
minecraft:heart_of_the_sea
```

Possible MCDAR artifact candidates to verify from local registry/loot tables:

```text
mcdar:golem_kit
mcdar:totem_of_shielding
mcdar:totem_of_regeneration
mcdar:totem_of_soul_protection
mcdar:harvester
mcdar:light_feather
mcdar:iron_hide_amulet
mcdar:death_cap_mushroom
mcdar:boots_of_swiftness
mcdar:ghost_cloak
mcdar:wind_horn
mcdar:love_medallion
```

Do not inject MCDAR Golem Kit items until the golem crash is addressed.

## Implementation Outline

1. Confirm Minecraft is closed.
2. Create a timestamped backup of the world folder.
3. Scan `region/*.mca` and relevant dimension region folders.
4. Parse NBT using a structured Anvil/NBT parser.
5. Collect eligible containers and current occupied slots.
6. Inspect local loot tables and item registries to finalize the reward pool.
7. Show proposed counts and item pool for confirmation.
8. Apply changes to a copy first.
9. Validate the edited copy loads.
10. Apply to the real save only after explicit confirmation.

## Verification

After edits:

- Count containers scanned.
- Count containers selected by the 1/20 roll.
- Count containers actually modified.
- Count skipped full containers.
- List injected item IDs and total stack counts.
- Launch the world and verify no crash on load.

