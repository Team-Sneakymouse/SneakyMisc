# Human Server Smoke Validation

Status: ready-for-human

## Parent

`.scratch/phonebook/PRD.md`

## What to build

Run the final Minecraft-client validation for behavior that automated tests and an LLM agent cannot reliably observe. This is a human validation slice, not an AFK implementation task.

## Acceptance criteria

- [ ] A human verifies that `/phonebook` opens the expected inventory GUI on a dev Paper server.
- [ ] A human verifies that GUI item movement, dragging, number-key swaps, and other inventory smuggling paths are blocked.
- [ ] A human verifies that swap-offhand on a contact removes the Phonebook Contact and gives only local feedback.
- [ ] A human verifies that asynchronous head updates appear in the correct contact slots and do not corrupt the GUI after page navigation or close/reopen.
- [ ] A human verifies that left-clicking a contact creates or reuses a SneakyCellPhones call and the invite names the called Character correctly.
- [ ] A human verifies that Add Contact, damage-triggered exchange, accept, decline, close-as-decline, and listing-on-accept work in a live client.
- [ ] A human verifies that switching Characters closes Phonebook browsing GUIs but does not close accept/decline exchange GUIs.

## Blocked by

- `.scratch/phonebook/issues/02-toggle-phonebook-listing.md`
- `.scratch/phonebook/issues/03-remove-a-phonebook-contact.md`
- `.scratch/phonebook/issues/04-create-a-contact-through-exchange.md`
- `.scratch/phonebook/issues/05-support-group-exchange-flow.md`
- `.scratch/phonebook/issues/06-finish-phonebook-gui-polish.md`
- `.scratch/phonebook/issues/07-add-phonebook-debug-diagnostics.md`
