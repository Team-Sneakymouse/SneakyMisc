# Elevators

Every elevator floor has its own available cab. Calls open that elevator floor's doors immediately, including when other elevator floors are open or occupied transit cabs have exhausted capacity.

Elevator travel uses a separate, physically isolated transit cab. All cabs have a fixed **5 × 5 block interior, 4 blocks high**, with a three-block-wide, four-block-high animated doorway. Style tags identify wall designs only. Build the matching interiors yourself; registering a transit cab does not copy blocks or add doors to it.

## Setup

1. Create a named elevator with a style: `/elevator create tower stone`.
2. Stand at the center of an elevator floor's cab, at feet level, facing its doorway. Run `/elevatorfloor create tower-1`. Repeat for other elevator floors.
3. Build one or more matching transit cabs away from play areas. Stand at each cab's center facing its doorway, then run `/elevatorcab create stone1 stone` (use a unique name for each).
4. Look at an item frame within five blocks and run `/elevatorfloor link tower-1 call` for an outside call control, or `/elevatorfloor link tower-1 select` for the inside keypad. Call frames also work with an adjacent vanilla button. Sneak-clicking that button opens the keypad when inside the cab.

Anchors snap to the block center and the nearest cardinal facing. The door is three blocks in front of the anchor. Interior membership uses account feet positions within the five-by-five footprint and four-block height, excluding the doorway/corridor.

Alternatively, right-click an unlinked frame while holding an item named `tower-1` to link a call control, or `tower-1:select` to link a keypad. Linking needs `sneakymisc.elevatorfloor` permission.

Place transit cabs beyond the configured proximity voice range of **both ordinary play areas and every other transit cab**. Putting them underground alone does not guarantee voice isolation. Verify spacing on the live server with the actual voice configuration.

## Using an elevator

- Call controls open or reopen doors. Reopening while a departure closes cancels that departure.
- Select tens first, then ones: `0`, `5` selects elevator floor 5. The second digit evaluates automatically. B and G select immediately; Clear resets partial input.
- The first valid destination selection starts departure for everyone inside when doors finish closing.
- Elevator travel keeps the existing music and timing. Occupants who teleport away stay away; accounts summoned into the transit cab arrive with its current occupants and hear no music.
- Arrivals into an occupied/open elevator floor happen immediately. They cancel any departure still closing there and interrupt keypad users inside with half a heart of damage and an amethyst sparkle sound. Damage is allowed to be lethal.
- Compatible transit cabs are shared by every elevator with the same style. If all are reserved or physically occupied, selection shows an at-capacity message; calls still work. A missing style pool shows a setup error instead.
- An empty transit cab remains reserved until its original arrival time. With nobody arriving, it does not interrupt the destination.

## Management

| Command | Purpose |
| --- | --- |
| `/elevator list` | List elevators, styles, and elevator floors |
| `/elevator style <name> <style>` | Change an inactive elevator's style |
| `/elevator delete <name>` | Delete an inactive elevator and its animated doors |
| `/elevator call <name-floor>` | Open an elevator floor, cancelling an uncommitted departure |
| `/elevatorfloor open <name-floor>` | Same as call |
| `/elevatorfloor close <name-floor>` | Cancel an uncommitted departure and close doors |
| `/elevatorfloor delete <name-floor>` | Remove an inactive elevator floor and its animated doors |
| `/elevatorcab list` | List transit cabs, styles, and locations |
| `/elevatorcab style <name> <style>` | Change an inactive transit cab's style |
| `/elevatorcab delete <name>` | Remove an inactive transit-cab registration; leave its blocks |

Elevator and transit-cab names accept letters, digits, and underscores. Styles accept lowercase letters, digits, underscores, and hyphens. Elevator floor labels are B, G, or 0–99 without leading zeros. B/G are distinct from numeric floor 0.

Elevator and transit-cab commands need `sneakymisc.elevator`; elevator-floor commands need `sneakymisc.elevatorfloor`. Ordinary control use needs no management permission.

Setup is stored in `plugins/SneakyMisc/elevators.yml`. Legacy `elevator.yml` is ignored; recreate existing elevators. Active elevator travel and passenger information are not persisted. Interrupted or stranded occupants can use `/spawn`. Physically occupied transit cabs are skipped until empty.

## Live verification

After rebuilding the physical elevators, verify the following with multiple accounts:

- Call at different elevator floors during active elevator travel, and exhaust the matching style's transit-cab pool.
- Reopen doors during departure; then arrive at another elevator floor while its doors are closing or someone is selecting a destination.
- Teleport out and summon another account in during elevator travel. Check preserved offsets/facing at rotated elevator floors and transit cabs.
- Check door movement, collision barriers, keypad models, music timing, and actual voice isolation. These require the server and resource pack and cannot be proven by JVM tests.
