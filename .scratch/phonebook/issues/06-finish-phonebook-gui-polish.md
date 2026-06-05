# Finish Phonebook GUI Polish

Status: ready-for-agent

## Parent

`.scratch/phonebook/PRD.md`

## What to build

Finish the Phonebook browsing GUI beyond the MVP. Add full pagination, reserved empty edge columns, previous and next controls, proactive close on Character switch, and asynchronous Character head updates that are safe against late futures after pagination, refresh, or close.

## Acceptance criteria

- [ ] The Phonebook browsing GUI uses 54 slots.
- [ ] Contact slots are columns 1 through 7 across all 6 rows, for 42 contacts per page.
- [ ] Slot 8 is Add Contact.
- [ ] Slot 45 is previous page only when applicable.
- [ ] Slot 53 is next page only when applicable.
- [ ] Unused contact slots and unused reserved edge-column slots remain empty.
- [ ] Visible contacts sort by current Character display name case-insensitively, then Character UUID.
- [ ] Previous and next controls navigate pages without allowing item movement.
- [ ] Previous, next, and refresh actions rebuild the current open top inventory contents in place instead of opening a new inventory view.
- [ ] Phonebook browsing GUIs close when the Account changes Character.
- [ ] Call and remove actions still validate the viewer is embodying the GUI owner Character after any GUI refresh or page navigation.
- [ ] Contact items render as default player heads first, then update through asynchronous SneakyCharacterManager skin resolution.
- [ ] Each full browsing GUI render has a render token stored in the holder.
- [ ] A completed skin update only modifies the slot if the viewer still has the same Phonebook page open and the holder token, page, slot, and contact Character UUID still match.
- [ ] Skin resolution failures leave the default head silently.
- [ ] Tests cover pagination slot layout, empty slots, in-place page navigation/refresh, sorting, Character-switch close behavior, stale owner validation, render token changes, and late skin-result slot validation.

## Blocked by

- `.scratch/phonebook/issues/01-call-a-persisted-phonebook-contact.md`
- `.scratch/phonebook/issues/03-remove-a-phonebook-contact.md`
