# Support Group Exchange Flow

Status: ready-for-agent

## Parent

`.scratch/phonebook/PRD.md`

## What to build

Extend Phonebook Exchange so a player can quickly send contact requests to multiple Accounts. An initiator may have multiple outgoing accept/decline requests open at once by repeatedly clicking Add Contact and damaging different valid targets. Initiator quit cancels only the currently active seeking state, not already opened exchange GUIs. If a target is also seeking when they receive an exchange GUI, their seeking state is cancelled.

## Acceptance criteria

- [ ] After one valid hit creates an outgoing exchange GUI, the initiator can click Add Contact again and seek another target while the first request remains unresolved.
- [ ] One Add Contact click still creates at most one exchange GUI or times out.
- [ ] Multiple outgoing requests from the same initiator resolve independently.
- [ ] Initiator quit cancels only active seeking.
- [ ] Initiator quit does not cancel already opened accept/decline exchanges.
- [ ] If a target is in Phonebook Seeking when they receive an accept/decline GUI, their seeking state is cancelled.
- [ ] A target who is seeking but has no inventory UI open is still a valid exchange target.
- [ ] Online initiators receive decline/close feedback for each independently declined request through central MiniMessage translation keys.
- [ ] Tests cover multiple outgoing requests, independent resolution, initiator quit, target seeking cancellation, one-dialog-per-seeking-click behavior, and message keys/arguments without asserting exact rendered text.

## Blocked by

- `.scratch/phonebook/issues/04-create-a-contact-through-exchange.md`

## Comments

- Architecture note from 2026-06-05 review: extend the Exchange/Seeking state-machine module from issue 04 for group exchange rather than adding group-specific listener state. Multiple outgoing requests, independent resolution, initiator quit, and target-seeking cancellation should be state-machine transitions with thin Bukkit adapters.
