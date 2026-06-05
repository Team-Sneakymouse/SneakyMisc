# Toggle Phonebook Listing

Status: ready-for-agent

## Parent

`.scratch/phonebook/PRD.md`

## What to build

Add the player command for changing the active Character's Phonebook Listing. Players can use `/phonebook toggle` to flip the current listing state, `/phonebook toggle listed` to explicitly list, and `/phonebook toggle unlisted` to explicitly unlist. Listing changes persist immediately and affect future Phonebook visibility.

## Acceptance criteria

- [ ] `/phonebook toggle` requires `sneakymisc.phonebook`, is player-only, and requires an active Character.
- [ ] `/phonebook toggle` flips the active Character between listed and unlisted.
- [ ] `/phonebook toggle listed` explicitly lists the active Character.
- [ ] `/phonebook toggle unlisted` explicitly unlists the active Character.
- [ ] Toggle feedback uses central MiniMessage translation keys with named arguments where needed, not hard-coded rendered strings at send sites.
- [ ] Listing changes save immediately to `phonebooks.yml`.
- [ ] Listed Characters are persisted in the sorted `listings` set; unlisted Characters are omitted.
- [ ] Opening `/phonebook` does not automatically list the active Character.
- [ ] Future visible-contact resolution reflects listing changes.
- [ ] Tests cover toggle, explicit listed/unlisted modes, permission checks, missing active Character behavior, persistence, visibility impact, and message keys/arguments without asserting exact rendered text.

## Blocked by

- `.scratch/phonebook/issues/01-call-a-persisted-phonebook-contact.md`
