# World paste

Pastes a preset of schematics over an area with FastAsyncWorldEdit, for one-off map changes that are never rolled
back (so it doesn't go through SneakyUtils' altered-block manager). Only registered when FAWE is enabled.

## Commands

Permission: `sneakymisc.command.worldpaste` (operators by default). Every command works from the console.

| Command | |
|--|--|
| `/worldpaste list` | List the presets in `world-pastes.yml` |
| `/worldpaste info <preset>` | Read the schematics and show where each lands, how many chunks the area covers and who is in it now. Changes nothing |
| `/worldpaste run <preset> confirm` | Paste. Without `confirm` it only warns |

## What `run` does

1. Reads every schematic (off the main thread) and checks every box fits the world's build height.
2. Loads every chunk the boxes touch, holding each with a plugin ticket, and waits up to 2 minutes for them and
   their entities to load.
3. Kills every player whose x/z is inside any schematic's box, at any height, and removes every other entity inside
   the boxes. Entities riding a player (cosmetics) are left to that player.
4. Pastes each schematic's blocks, block entities and air at the preset's origin. The schematics' own entities and
   biomes are left out.
5. Runs the preset's `after-commands` from the console, then tells LodServerSupport which chunks changed.
6. Tells LSS again after 30 seconds (in case FAWE was still relighting) and keeps the chunks loaded for 90 seconds
   in all, so LSS serves the new LODs from memory, then lets them unload.

Nothing in the world changes before step 3: a bad file, a box outside the build height or chunks that don't load
in time stop the job harmlessly. If the paste fails part-way, the job says which schematics were pasted, skips the
after-commands and still tells LSS about the area. Only one paste runs at a time.

LSS sends its change notices every `dirty_broadcast_interval_ticks` (200 by default). Keep it well under 30 seconds
(600 ticks) so the notices go out while the chunks are still loaded, and above 0 (0 turns the notices off).

## Configuration

`plugins/SneakyMisc/world-pastes.yml`, read fresh by every command (no reload needed).

Schematic files are read from FAWE's schematic folder: `saving.dir` in WorldEdit's config, by default
`plugins/FastAsyncWorldEdit/schematics/`, the same folder `//schem load` uses. Paths are relative to it and may
include subfolders (`darkvale/darkvalescar1.schem`), but can't leave it.

```yaml
darkvalescar:
  world: world
  origin: [4991, 60, 4850]   # where each schematic's own origin goes
  schematics:
    - darkvalescar1.schem
    - darkvalescar2.schem
    - darkvalescar3.schem
    - darkvalescar4.schem
    - darkvalescar5.schem
  after-commands:            # console commands, optional
    - darkvale crater off      # off + on: clears the crater and builds it fresh on the new ground,
    - darkvale crater on       # and leaves it on even where it was off
```

`darkvale crater rebuild` alone does nothing where the crater is turned off. Since SneakyDarkvale's 2026-11-20
build, `darkvale crater on` alone also rebuilds cleanly; `off` + `on` works with older builds too.

To put a build back exactly where it was copied, use the origin it was copied from (FAWE stores it in the
schematic as `Metadata.WorldEdit.Origin`). Check the boxes with `/worldpaste info` before running.

## LodServerSupport

LSS only notices block changes through Bukkit events, and FAWE fires none. After a paste, SneakyMisc calls LSS's
public `getRequestService().getDirtyTracker().markDirty(dimension, chunkX, chunkZ)` by reflection, the same call LSS's
own event listener makes: LSS drops its cached LODs for those chunks and tells nearby clients to re-request them.
Checked against LSS 0.22.0. If LSS is missing or a later version renames those methods, the paste still works, a
warning is logged, and players see the new terrain in their LODs after reconnecting.
