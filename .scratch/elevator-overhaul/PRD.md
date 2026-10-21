# Elevator overhaul

Status: Implemented — automated checks pass; live server verification remains.

## Intention

Keep elevator floors immediately available while preserving the shared-cab social experience, existing door animations, music, travel timing, and seamless position/rotation changes. Separate concurrent elevator travels into physically isolated transit cabs so proximity conversations do not leak between groups or into the playable world.

This is a clean replacement. Existing elevator configuration and control links do not need migration.

## Agreed behavior

### Independent elevator floors

- An elevator is a named collection of elevator floors with one style tag.
- There is no elevator-wide current elevator floor, occupied cab, or in-transit lock.
- Calling an elevator floor opens its doors regardless of activity at other elevator floors or transit-cab capacity.
- Calling an already open elevator floor refreshes its open duration.
- Preserve the existing door animation, sounds, and automatic closing behavior.
- A call during an uncommitted departure reopens the doors, cancels that departure, and releases its reserved transit cab.
- Once passengers have transferred to a transit cab, calls at the origin elevator floor open it for the next group without affecting that elevator travel.

### Shared departures

- The first valid destination selection initiates departure for the shared physical cab.
- Further selections from that cab cannot start another departure while its doors are closing.
- Check destination validity and reserve a compatible transit cab before closing doors.
- If all compatible transit cabs are occupied or reserved, reject departure with an elevator-at-capacity message. Keep passengers at the origin and leave doors available.
- Once doors are fully closed, determine passengers using the actual cab interior and transfer them to the reserved transit cab.
- Preserve relative position, facing, and pitch on both transfers: origin to transit cab and transit cab to destination.
- Preserve the existing travel music and timing. Transit-cab allocation does not add a queue or wait for another elevator travel.

### Dynamic transit-cab occupancy

- Passengers can teleport out during elevator travel. They remain wherever they teleported and are not brought to the destination.
- Accounts summoned into an occupied transit cab become part of that elevator travel.
- Summoned passengers do not hear elevator music. Music plays for the group transferred from the origin when elevator travel begins.
- At arrival, transfer the accounts currently inside that transit cab's interior. The initial passenger list is not the arrival passenger list.
- Elevator travel keeps its original destination and arrival time when occupants change.
- Each transit cab is exclusively reserved for one elevator travel, including when temporarily empty. It is not reusable until that elevator travel has finished or been safely cancelled.
- Arrival uses physical occupancy only. Do not maintain per-account travel membership or recovery records.

### Arrivals and social interruption

- Arrive immediately even if the destination doors are already open or the destination cab is occupied. Visible spawning is acceptable.
- If an account in the destination cab has a floor-selection GUI open, close it, apply 1 health point of damage (half a heart), and play a sparkle sound effect. Lethal damage is allowed; the server handles death as being knocked prone.
- If the destination cab has an outgoing departure waiting for its doors to close, cancel that departure and release its reserved transit cab.
- Cancel pending departure work and invalidate selection GUIs before transferring arriving passengers. An old callback or stale click must not carry either group away.
- Open or hold open the destination doors so the groups can interact and later choose another destination.
- Arrivals do not merge two active elevator travels in a transit cab; groups only meet at elevator floors.

### Transit-cab styles and management

- Each elevator and each transit cab has one style tag.
- All elevators with the same style share that style's transit-cab pool.
- A transit cab can only serve an elevator with the matching style.
- Different styles identify wall-block designs, not different sizes. All cabs have the same fixed five-by-five interior, four blocks high; dimensions are not configurable.
- Keep setup and management simple: create elevator floors, link controls, assign elevator styles, and register transit cabs with a style.
- Transit-cab setup needs only its center/facing and style tag. The interior volume is fixed.
- Transit cabs must be physically isolated from ordinary play areas and from each other beyond the server's configured proximity voice range. Underground placement alone is not a sufficient acceptance check.
- An elevator with no usable matching transit cab needs a clear setup error, distinct from temporary capacity exhaustion.

### Floor-selection GUI

- Associate each GUI with its physical origin elevator floor; no global current elevator floor is used.
- Start with empty input and the tens digit selected/highlighted.
- Enter tens first, then ones. Evaluate immediately after both digits have been entered.
- For example, `0`, `5` selects elevator floor 5.
- Remove the confirm action and provide a clear/reset action for a mistaken first digit.
- Retain dedicated B and G selections; each evaluates immediately.
- An invalid destination shows an error, then resets to empty tens-first input.
- Selecting the current elevator floor opens or holds open its doors without reserving a transit cab or starting elevator travel.
- Successful departure closes GUIs associated with the originating cab. Other elevator floors remain usable.

## Proposed defaults

These details were not explicitly decided in conversation and should remain visible during review.

- Apply interruption damage once to each interrupted GUI user in the receiving cab. Do not damage other occupants just because someone arrived.
- Only interrupt a selection GUI if its user is physically inside the receiving cab. A GUI opened elsewhere must not act as a remote control; revalidate the user's location when accepting input.
- If departure finishes closing with no occupants, cancel it and release the transit cab without playing an empty elevator travel.
- Preserve the existing automatic door-close delay and normal travel sequence duration.
- A reset or GUI reopening starts a fresh input sequence; displayed current-floor information never counts as entered digits.
- Use one required style tag per elevator/cab. Reject malformed geometry, overlapping cab interiors, and incompatible setup rather than accepting an unsafe cab into the pool.
- Reject deletion or style/geometry edits involving an active elevator travel until it finishes or is safely cancelled.

## Failure handling and cleanup

- Automatic passenger recovery is out of scope. Accounts stuck after a disconnect, failed teleport, shutdown, or restart can use `/spawn`.
- Do not persist active elevator travel or per-account recovery information, resume interrupted elevator travel, redirect reconnecting accounts, or add shutdown evacuation.
- Release runtime reservations when elevator travel finishes, is cancelled, or fails. Reset runtime reservations on startup.
- Allocate only unreserved transit cabs whose interiors are physically empty, so a stranded occupant prevents reuse until they leave, for example via `/spawn`. This is an allocation check, not passenger tracking.
- Ordinary elevator travel still needs loaded destination/transit chunks and should report failures; a failed transfer does not trigger automatic evacuation or retry-based passenger recovery.
- Cancel or invalidate obsolete scheduled door, GUI, and departure work after interruption, deletion, or shutdown.

## Design direction

Keep separate responsibilities for physical cab geometry/position transforms, door animation, GUI input, and elevator-travel coordination. Hide allocation, cancellation, and dynamic occupancy behind a small coordination interface.

Door state belongs to each elevator floor. Departure preparation belongs to its origin elevator floor. Destination, timing, and an exclusive transit-cab reservation belong to each active elevator travel. Style selects the compatible transit-cab pool.

Reuse the proven animation and rotation behavior, while replacing its global-state assumptions and callback lifecycle. Persist setup only; active elevator travel and reservations are runtime state.

CONTEXT.md defines Elevator Travel without a single current elevator floor and includes the transit-cab/style concepts.

## Implementation sequence

1. Define the new configuration and physical cab geometry, with simple setup commands and explicit style matching. Ignore legacy elevator configuration intentionally.
2. Retain the door visuals behind interruptible per-floor animation state; remove elevator-wide current-floor/in-transit restrictions.
3. Add exclusive transit-cab allocation, shared departure, both position-preserving transfers, dynamic arrival occupancy, and reservation cleanup.
4. Implement arrival interruptions and departure cancellation, including invalidation of stale GUI/animation callbacks.
5. Replace keypad input with tens-first automatic evaluation and wire controls to their physical origin elevator floor.
6. Verify concurrency, interruptions, reservation cleanup, visuals, and actual proximity voice isolation on a server.

## Acceptance scenarios

- Calls at two elevator floors open both without waiting; another elevator travel can remain active.
- Two elevator travels with matching styles get separate cabs; another style cannot consume those cabs.
- Exhaustion rejects new departures before doors close but does not block calls.
- Calling while a departure closes cancels it and frees its reservation; its old scheduled work never teleports anyone.
- A full cab travels together; someone outside the actual interior does not travel.
- An account that teleports out stays away. An account summoned in arrives with the remaining occupants.
- An empty active transit cab remains reserved until its scheduled completion, then becomes reusable.
- If no occupants remain at arrival, release the reservation without interrupting selections or departures at the destination.
- Arrival at an open destination visibly places occupants there without waiting.
- Arrival interrupts each eligible selection GUI once with the configured damage and sparkle sound.
- Arrival cancels an outgoing closing sequence before either group can be taken away by it.
- Both transfer stages preserve position and facing for differently rotated elevator floors and transit cabs.
- Numeric selection uses two new digits; B/G use one dedicated selection; invalid input resets; same-floor selection consumes no capacity.
- Multiple selections, repeated calls, and overlapping door callbacks produce one coherent result without duplicate departures or leaked reservations.
- Failed or cancelled elevator travel releases its runtime reservation. Restart does not resume elevator travel or redirect accounts. An unreserved cab with a physical occupant is skipped until empty; stuck accounts can use `/spawn`.
- Concurrent groups cannot hear each other's proximity speech, and neither can hear people at their origin/destination during transit.

## Open questions

No outstanding gameplay questions from this discussion. Proposed defaults above remain visible for review.

## Comments

- Initial planning: user confirmed shared departures, call-to-cancel while closing, dynamic passengers through teleport/summon, arrival interruptions, style-based pools, tens-first automatic selection, and unchanged travel timing. User explicitly requested a clean design without migration.
- Follow-up: user confirmed half-a-heart interruption damage with no nonlethal protection, and no music for summoned passengers. Asked for clarification of the proposed origin-based recovery behavior and its tracking.
- Recovery decision: user rejected automatic recovery and its tracking; stuck accounts can use `/spawn`. Removed recovery records, reconnect redirection, persisted elevator travel, and evacuation requirements.
- Implementation clarification: user explicitly rejected configurable dimensions. Styles refer to wall blocks only; all cabs retain fixed dimensions.
- Implementation includes independent per-floor doors, style-based transit-cab allocation, dynamic occupancy, interruptible travel/GUI work, fixed cab geometry, new setup persistence/commands, and setup/live-verification instructions in docs/elevators.md. Full Gradle checks pass, including 22 elevator tests. No live server/resource-pack/voice verification was available in the workspace.
