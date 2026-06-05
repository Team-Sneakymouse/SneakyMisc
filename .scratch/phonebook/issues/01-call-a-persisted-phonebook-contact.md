# Call A Persisted Phonebook Contact

Status: ready-for-human

## Parent

`.scratch/phonebook/PRD.md`

## What to build

Build the minimum viable Phonebook path from persisted data to a delivered call. A server operator can seed `phonebooks.yml`, a player can open `/phonebook`, the mechanic resolves reachable persisted Phonebook Contacts for the active Character, renders a simple first-page GUI with default heads, cancels custom GUI item movement, and left-clicking a contact starts or reuses a SneakyCellPhones call to that contact's controlling Account.

This slice should include the durable storage behavior needed by the MVP: canonical unordered contact keys, listing set semantics, sorted save output, immediate saves for mutations introduced in this slice, malformed-entry warnings, duplicate prevention helpers, and no cached Character names or skins.

## Acceptance criteria

- [ ] `phonebooks.yml` loads contacts keyed as `<lower-character-uuid>_<higher-character-uuid>` with `accountA` and `accountB`, using lowercase lexicographic Character UUID string ordering rather than `UUID.compareTo()`.
- [ ] `phonebooks.yml` loads `listings` as a semantic set where omitted Characters are unlisted.
- [ ] Malformed contact entries and malformed listing entries are skipped with warnings and are not preserved on the next save.
- [ ] Saved output has no version field and sorts listings and contact keys lexicographically.
- [ ] `/phonebook` is player-only, requires `sneakymisc.phonebook`, and fails cleanly when the Account has no active Character.
- [ ] Normal player-facing messages use central MiniMessage translation keys under `sneakymisc.phonebook.*` with named Adventure translation arguments, rather than hard-coded rendered strings at send sites.
- [ ] The opened Phonebook belongs to the active Character and shows only listed contacts whose controlling Accounts are online.
- [ ] The Phonebook browsing GUI is identified by a custom `InventoryHolder`, not by inventory title, and carries viewer Account UUID, owner Character UUID, page number, and render token.
- [ ] Contact item metadata stores only the selected contact Character UUID; click handling re-reads the relationship from the GUI owner Character plus contact Character.
- [ ] Visible contact display data is resolved from SneakyCharacterManager using the stored Account UUID and Character UUID, not from cached Phonebook names or skins.
- [ ] The initial GUI is enough to display a first page of contacts using default player heads and empty unused slots.
- [ ] All click and drag interactions in the Phonebook GUI are cancelled so GUI items cannot be moved.
- [ ] Left-clicking a visible contact calls SneakyCellPhones with the selected contact Character display name as the call override.
- [ ] Phonebook calls require only `sneakymisc.phonebook`, not raw SneakyCellPhones command permissions.
- [ ] If a visible contact becomes unlisted after render but the target Account remains online, the call still goes through.
- [ ] If the target Account is offline at click time, the call fails cleanly and the GUI refreshes.
- [ ] Tests cover storage round trips, malformed data skipping, visible contact resolution, command permission/active-Character gates, click cancellation, call routing behavior, and message keys/arguments without asserting exact rendered text.

## Blocked by

None - can start immediately

## Implementation notes

Use these notes to keep the MVP path integrated end to end:

- Register default Phonebook MiniMessage translations with Adventure during Phonebook mechanic startup. Use translation keys under `sneakymisc.phonebook.*` and a real Adventure translation source such as `MiniMessageTranslationStore`, `MiniMessageTranslator`, `TranslationStore`, and/or `GlobalTranslator.addSource(...)`.
- Keep translation keys stable and testable. Tests should assert emitted keys and named argument payloads, and should also verify that the registered Adventure translation source can resolve the default keys to components. Do not assert final rendered English text.
- Dynamic player-facing values should be passed as named Adventure translation arguments. For example, a target-offline message that names a Character should carry a `character` argument.
- The real GUI click seam has only the browsing holder state and item metadata. Contact item metadata stores only the selected contact Character UUID, so click handling must re-read the Phonebook Contact and current display data from the GUI owner Character plus selected contact Character.
- Drive `/phonebook` opening through one owner path: command validation, active Character lookup, visible-contact resolution, GUI model creation, and inventory opening should not perform duplicate resolution/rendering for a single open action.
- Test the Bukkit-facing seams with fakes where possible: command send behavior, GUI click metadata extraction, translated message delivery, drag/click cancellation, and first-page inventory contents.
- Prefer fewer, deeper modules whose interfaces match the acceptance criteria. Storage, reachable-contact resolution, message translation, and GUI browsing are the main seams in this slice.

## Comments

- 2026-06-05: Refined implementation notes to emphasize end-to-end translation registration, GUI click seam coverage, and a single `/phonebook` open path.
- 2026-06-05: Implemented the MVP persisted-contact-to-call path with TDD coverage for storage canonicalization/round trips/malformed skipping/duplicate prevention, reachable contact resolution, active-Character open gating, and click-time call routing. Full Gradle test suite passes; remaining validation should be a human Minecraft-client smoke test for actual inventory rendering and SneakyCellPhones invite behavior.
- 2026-06-05: Architecture review pass for issue-01 only. Fully implemented deeper locality for the Reachable Character/browser render seam by sharing one PhonebookBrowser module across open and refresh paths; fully implemented storage save canonicalization before sorting/deduping; partially implemented named message arguments by preserving named PhonebookMessage payloads and central key argument ordering before Adventure's positional translatable adapter. Scoped Phonebook tests pass with `.\gradlew.bat test --tests "com.danidipp.sneakymisc.phonebook.*"`.
