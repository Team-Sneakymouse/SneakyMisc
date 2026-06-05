# Remove A Phonebook Contact

Status: ready-for-human

## Parent

`.scratch/phonebook/PRD.md`

## What to build

Let a player remove a visible Phonebook Contact from the Phonebook GUI by pressing swap-offhand on the contact item. Removal deletes the bidirectional Phonebook Contact from persistence, sends only the removing player a local confirmation, does not notify the counterparty, and does not change either Character's Phonebook Listing.

## Acceptance criteria

- [ ] Swap-offhand on a contact item in the Phonebook GUI removes the canonical bidirectional Phonebook Contact.
- [ ] Removal saves `phonebooks.yml` immediately with sorted remaining contacts.
- [ ] Removal does not change either Character's listing state.
- [ ] The removing player receives a local confirmation naming the removed contact.
- [ ] Remove feedback uses central MiniMessage translation keys with named Character-name arguments, not hard-coded rendered strings at send sites.
- [ ] The counterparty is not notified.
- [ ] If state changes between validation and confirmation, the defensive confirmation message names the affected Phonebook owner Character.
- [ ] Remove actions validate that the viewer is still embodying the GUI owner Character.
- [ ] Remove actions remain covered by GUI click cancellation so no GUI item movement is possible.
- [ ] The GUI refreshes after successful removal.
- [ ] Tests cover bidirectional deletion, no listing mutation, local-only confirmation, stale owner rejection, persistence save, click cancellation, and message keys/arguments without asserting exact rendered text.

## Blocked by

- `.scratch/phonebook/issues/01-call-a-persisted-phonebook-contact.md`

## Comments

- 2026-06-05: Implemented swap-offhand Phonebook Contact removal through a small `PhonebookGuiActions` module so removal policy stays out of `PhonebookGuiListener`. Removal validates the viewer is still embodying the GUI owner Character via `PhonebookActiveCharacters`, delegates load/mutate/save to storage-owned `removeContact`, preserves Phonebook Listing state, sends local keyed feedback with named Character arguments, refreshes the GUI, and leaves the counterparty uninvolved. Full Gradle test suite passes; remaining validation should be a human Minecraft-client smoke test for actual swap-offhand behavior in the inventory UI.
