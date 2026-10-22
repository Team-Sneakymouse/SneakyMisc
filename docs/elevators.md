# Elevators

Every elevator floor has its own available cab. Calls open that elevator floor's doors immediately, including when other elevator floors are open or occupied transit cabs have exhausted capacity.

Elevator travel uses a separate, physically isolated transit cab. All cabs have a fixed **5 × 5 block interior, 4 blocks high**, with a three-block-wide, four-block-high animated doorway. Style tags identify wall designs only. Build the matching interiors yourself; registering a transit cab adds permanently closed doors with the same appearance and collision as elevator-floor doors. Registration does not copy the interior blocks. Existing registered transit cabs receive doors when the plugin loads.

## Setup

1. Create a named elevator with a style: `/elevator create tower stone`.
2. Stand at the center of an elevator floor's cab, at feet level, facing its doorway. Run `/elevatorfloor create tower-1`. Repeat for other elevator floors.
3. Build one or more matching transit cabs away from play areas. Stand at each cab's center facing its doorway, then run `/elevatorcab create stone1 stone` (use a unique name for each).
4. Look at an item frame within five blocks and run `/elevatorfloor link tower-1`. A frame inside `tower-1` becomes a keypad; a frame anywhere outside that cab becomes a call control for `tower-1`. Call frames also work with an adjacent vanilla button. Sneak-clicking that button opens the keypad when inside the cab.

Anchors snap to the block center and the nearest cardinal facing. The door is three blocks in front of the anchor. Interior membership uses account feet positions within the five-by-five footprint and four-block height, excluding the doorway/corridor.

Alternatively, right-click an unlinked frame while holding an item named `tower-1` for either kind of control. The frame's position determines its role when linked, independently of where you stand. Linking needs `sneakymisc.elevatorfloor` permission. The confirmation tells you whether a keypad or call control was linked. If you move a frame or change its role, run the link command again.

Place transit cabs beyond the configured proximity voice range of **both ordinary play areas and every other transit cab**. Putting them underground alone does not guarantee voice isolation. Verify spacing on the live server with the actual voice configuration.

## Using an elevator

- Call controls open or reopen doors. Reopening while a departure closes cancels that departure.
- Select tens first, then ones: `0`, `5` selects elevator floor 5. The second digit evaluates automatically. B and G select immediately; Clear resets partial input.
- The first valid destination selection starts departure for everyone inside when doors finish closing.
- During elevator travel, purple trail particles move between explicit endpoints along the transit cab walls opposite the direction between departure and destination heights. The particle volume extends one block above the cab interior. Trails gradually accelerate to four times their initial speed over the travel duration. Same-height travel uses stationary sparkles. On arrival, purple trails move a short distance through the destination cab, then leave a brief stationary shimmer at their endpoints.
- Elevator music starts immediately, follows each boarding account, and finishes naturally even after arrival or teleporting away. Elevator travel keeps the existing timing. Occupants who teleport away stay away; accounts summoned into the transit cab arrive with its current occupants and hear no music.
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
| `/elevatorcab link <name>` | Link an inside item frame as a decorative, nonfunctional keypad |
| `/elevatorcab list` | List transit cabs, styles, and locations |
| `/elevatorcab style <name> <style>` | Change an inactive transit cab's style |
| `/elevatorcab delete <name>` | Remove an inactive transit cab and its generated doors; leave the built interior |

Elevator and transit-cab names accept letters, digits, and underscores. Styles accept lowercase letters, digits, underscores, and hyphens. Elevator floor labels are B, G, or 0–99 without leading zeros. B/G are distinct from numeric floor 0.

Elevator and transit-cab commands need `sneakymisc.elevator`; elevator-floor commands need `sneakymisc.elevatorfloor`. Ordinary control use needs no management permission.

Setup is stored in `plugins/SneakyMisc/elevators.yml`. Legacy `elevator.yml` is ignored; recreate existing elevators. Active elevator travel and passenger information are not persisted. Interrupted or stranded occupants can use `/spawn`. Physically occupied transit cabs are skipped until empty.

## Decorative transit keypads

Place an item frame inside a registered transit cab, look at it within five blocks, and run `/elevatorcab link stone1`. Alternatively, right-click the frame holding an item named `stone1` with `sneakymisc.elevator` permission. The frame displays the usual keypad model but clicking it does nothing during travel. Position it to match the keypads in the compatible elevator floors.

Deleting a transit-cab registration removes its generated doors and clears the doorway; the rest of the built cab remains.

## Live verification

After rebuilding the physical elevators, verify the following with multiple accounts:

- Call at different elevator floors during active elevator travel, and exhaust the matching style's transit-cab pool.
- Reopen doors during departure; then arrive at another elevator floor while its doors are closing or someone is selecting a destination.
- Teleport out and summon another account in during elevator travel. Check preserved offsets/facing at rotated elevator floors and transit cabs.
- Check upward/downward transit sparkles, the arrival shimmer, door movement, collision barriers, keypad models, music timing, and actual voice isolation. These require the server and resource pack and cannot be proven by JVM tests.
