# Add Phonebook Debug Diagnostics

Status: ready-for-human

## Parent

`.scratch/phonebook/PRD.md`

## What to build

Add admin-only diagnostics for Phonebook state. `/phonebook debug` inspects the sender's online Account. `/phonebook debug <player>` inspects another online Account and can be used by admins or console. Output covers all Characters owned by the selected Account, includes display names and clickable copy-to-clipboard UUIDs, and stays bounded for large Phonebooks.

## Acceptance criteria

- [ ] `/phonebook debug` and `/phonebook debug <player>` require `sneakymisc.phonebook.debug`.
- [ ] `/phonebook debug` without an argument inspects the sender and is not available to console without a target.
- [ ] `/phonebook debug <player>` resolves online Accounts only.
- [ ] Console can run `/phonebook debug <player>`.
- [ ] Debug output includes Account UUID, active Character when available, and all Characters owned by the selected Account.
- [ ] Each Character section includes listing state, stored contact count, visible contact count, and missing or malformed contact counts when applicable.
- [ ] Debug output includes display names and raw UUIDs.
- [ ] UUIDs are clickable copy-to-clipboard components.
- [ ] Output is bounded with capped contact examples rather than unbounded chat dumps.
- [ ] Missing Character UUIDs under stored Account UUIDs are hidden from player GUIs but surfaced in debug.
- [ ] Debug output may be hard-coded components, but tests do not assert exact rendered debug text.
- [ ] Tests cover permission checks, self and target forms, console target form, online-only target resolution, all-Character summaries, bounded output behavior, missing contact reporting, and clickable UUID component construction.

## Blocked by

- `.scratch/phonebook/issues/01-call-a-persisted-phonebook-contact.md`
- `.scratch/phonebook/issues/02-toggle-phonebook-listing.md`
- `.scratch/phonebook/issues/04-create-a-contact-through-exchange.md`

## Comments

- 2026-06-06: Implemented Phonebook debug diagnostics with a pure `PhonebookDebugInspector` model and a thin Bukkit command adapter. The inspector reports all Characters for an Account, active Character, listing state, stored/visible contact counts, missing Character contacts, bounded examples, and storage-owned malformed persisted contact diagnostics. `/phonebook debug` and `/phonebook debug <player>` route through `sneakymisc.phonebook.debug`, support console with a target, resolve targets online-only, and render clickable copy-to-clipboard UUID components. Scoped Phonebook tests pass with `.\gradlew.bat test --tests "com.danidipp.sneakymisc.phonebook.*"`.
