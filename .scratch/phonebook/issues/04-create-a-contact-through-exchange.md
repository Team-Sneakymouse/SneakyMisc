# Create A Contact Through Exchange

Status: ready-for-agent

## Parent

`.scratch/phonebook/PRD.md`

## What to build

Add the first complete Phonebook Exchange path. A player clicks Add Contact, enters a 30-second Phonebook Seeking state, damages another valid Account, and opens an accept/decline GUI for the target. Accepting creates the bidirectional Phonebook Contact between the snapshotted Characters from the hit moment, lists both snapshotted Characters, persists immediately, and closes the GUI next tick. Declining, closing the GUI, or target quit resolves without creating a contact.

## Acceptance criteria

- [ ] The Add Contact GUI control starts a 30-second Phonebook Seeking state for the initiating Account.
- [ ] Clicking Add Contact while already seeking resets the 30-second timer and sends neutral waiting feedback.
- [ ] Seeking times out after 30 seconds if no valid exchange target is damaged.
- [ ] Phonebook never cancels the damage event used to trigger an exchange.
- [ ] Damage can trigger an exchange when the damaged entity is a Player and the damager resolves to a Player, including simple projectile-shooter resolution if straightforward.
- [ ] Soft invalid hits, including self-target and non-player target, do not consume seeking.
- [ ] Hard invalid hits cancel seeking with feedback: target lacks active Character, initiator no longer has active Character, duplicate Phonebook Contact, or target is busy.
- [ ] Target busy means the target's top open inventory is not the normal crafting inventory or the target is holding an item on their cursor; Phonebook does not replace that UI.
- [ ] The exchange snapshots both active Characters at hit time.
- [ ] The accept/decline GUI cancels all click and drag interactions.
- [ ] The accept/decline GUI is identified by a custom `InventoryHolder`, not by inventory title, and carries exchange identity plus target Account UUID.
- [ ] Clicking accept resolves once, creates the canonical Phonebook Contact, lists both snapshotted Characters, saves immediately, sends accurate feedback naming snapshotted Characters, and schedules close for the next tick.
- [ ] Clicking decline resolves once, creates no contact, changes no listing state, sends decline feedback, and schedules close for the next tick.
- [ ] Exchange feedback uses central MiniMessage translation keys with named Character-name arguments, not hard-coded rendered strings at send sites.
- [ ] Closing the accept/decline GUI without choosing counts as decline.
- [ ] A scheduled close after an accept or decline does not trigger close-as-decline.
- [ ] Target quit cancels that target's open accept/decline exchange.
- [ ] The accept/decline GUI is not closed on Character switch, and accepting still uses the snapshotted Characters.
- [ ] Accepting works even if the initiator disconnected after the GUI opened.
- [ ] Tests cover seeking timeout/reset, soft and hard invalid hits, target busy UI/cursor rejection, duplicate rejection, snapshot behavior, accept, decline, close-as-decline, target quit, Character switch, initiator disconnect, persistence, listing-on-accept, and message keys/arguments without asserting exact rendered text.

## Blocked by

- `.scratch/phonebook/issues/01-call-a-persisted-phonebook-contact.md`
- `.scratch/phonebook/issues/02-toggle-phonebook-listing.md`

## Comments

- Architecture note from 2026-06-05 review: implement Phonebook Exchange and Phonebook Seeking as a testable state-machine module before adding Bukkit damage, inventory, quit, scheduler, and GUI adapters. The state machine should own timeout/reset, snapshot, duplicate, busy-target, accept, decline, close-as-decline, target quit, persistence, listing-on-accept, and message-outcome rules so those policies do not scatter across listeners and scheduled tasks.
