# Phonebook Mechanic PRD

Status: ready-for-agent

## Problem Statement

Players need an in-character way to keep and use contacts without relying on raw Account names or manual SneakyCellPhones commands. Contacts need to represent roleplay Characters, while calls still need to be delivered to the online Account that controls the selected Character.

The current codebase only has a dependency-gated Phonebook shell. There is no player-facing phonebook GUI, no persisted Character contact relationships, no Character listing control, and no contact exchange flow.

## Solution

Add a Phonebook mechanic where `/phonebook` opens a player-only GUI for the active Character's Phonebook. The GUI shows reachable Phonebook Contacts: listed Characters whose controlling Accounts are online. Contact relationships are bidirectional between Characters, and calls are delivered through SneakyCellPhones to the controlling Account.

Players add contacts through a short Phonebook Seeking flow: click Add Contact, then damage another Account within 30 seconds. If valid, the target receives an accept/decline GUI. Accepting creates the bidirectional Phonebook Contact between the snapshotted Characters from the hit moment and lists both Characters. Declining, closing the GUI, or target quit resolves without creating a contact.

Players control their own active Character's Phonebook Listing with `/phonebook toggle [{listed|unlisted}]`. Debug diagnostics are available through a separate admin permission.

## User Stories

1. As a player, I want `/phonebook` to open my active Character's Phonebook, so that I can manage contacts in-character.
2. As a player, I want `/phonebook` to fail cleanly when I have no active Character, so that I understand why contacts cannot be loaded.
3. As a player, I want only Characters who are currently reachable to appear in my Phonebook, so that the contacts I see can be called.
4. As a player, I want a reachable contact to appear even when their Account is embodying another Character, so that I can still call the Character I know.
5. As a player, I want a reachable contact to appear when their Account is online without an active Character, so that delivery is based on Account availability.
6. As a player, I want unlisted contacts hidden from my Phonebook, so that other Characters can control whether they appear.
7. As a player, I want hidden contacts to remain persisted, so that they reappear when listed again.
8. As a player, I want contacts sorted predictably by Character display name, so that larger Phonebooks are easy to scan.
9. As a player, I want the Phonebook GUI to paginate contacts, so that large Phonebooks remain usable.
10. As a player, I want Add Contact, previous page, and next page controls, so that I can navigate and start exchanges from the GUI.
11. As a player, I want unused control and contact slots to stay empty, so that the GUI is visually clear.
12. As a player, I want contact items to show Character heads when available, so that contacts are recognizable.
13. As a player, I want contacts to be usable while their head skin is still loading, so that cosmetics do not block actions.
14. As a player, I want failed skin loading to leave a default head, so that the GUI remains stable.
15. As a player, I want left-clicking a contact to start or reuse a SneakyCellPhones call, so that I can call contacts quickly.
16. As a contacted player, I want the call invite to name the Character being called, so that I understand why I received the call.
17. As a player, I want phonebook calls to work even if the contact unlisted after my GUI opened, so long as their Account is still online, so that already visible contacts feel reliable.
18. As a player, I want phonebook calls to fail cleanly if the target Account is offline, so that I understand why delivery is impossible.
19. As a player, I want calls to use the Phonebook permission rather than raw SneakyCellPhones command permissions, so that the Phonebook is its own calling surface.
20. As a player, I want pressing swap-offhand on a contact to remove the contact, so that cleanup is fast.
21. As a player, I want removing a contact to remove the relationship from both Characters' Phonebooks, so that bidirectional contacts stay consistent.
22. As a player, I want removing a contact not to notify the other Account, so that contact cleanup is private.
23. As a player, I want a local confirmation after removing a contact, so that I know the action succeeded.
24. As a player, I want removing a contact not to change either Character's listing state, so that visibility remains a separate preference.
25. As a player, I want custom GUI interactions to be read-only, so that I cannot accidentally move GUI items into my inventory.
26. As a player, I want my Phonebook browsing GUI to close when I switch Characters, so that I do not use another Character's Phonebook.
27. As a player, I want stale Phonebook actions after a Character switch to be rejected, so that Phonebook ownership remains clear.
28. As a player, I want Add Contact to arm one 30-second seeking window, so that the next valid damage starts a contact exchange.
29. As a player, I want one Add Contact click to create only one exchange dialog, so that accidental repeated hits do not spam requests.
30. As a player, I want damaging another Account to start an exchange without cancelling damage, so that the mechanic does not alter combat or interaction outcomes.
31. As a player, I want simple invalid hits like self-targeting or non-player targets not to consume seeking, so that I can still hit a valid target.
32. As a player, I want hard invalid hits to cancel seeking with feedback, so that I can correct missing Character, duplicate contact, or busy-target states.
33. As a player, I want duplicate exchanges rejected even when the contact is currently hidden, so that existing relationships are not recreated.
34. As a player, I want the target's existing inventory UI respected, so that Phonebook does not replace shops, admin menus, or other plugin GUIs.
35. As a player, I want my own seeking mode cancelled if I become the target of an exchange, so that I do not remain unexpectedly armed.
36. As a player, I want to send multiple outgoing exchange requests to different Accounts, so that I can exchange contacts with a group quickly.
37. As a target, I want the accept/decline GUI not to time out, so that the 30-second limit only applies to seeking.
38. As a target, I want closing the accept/decline GUI to count as declining, so that unresolved requests do not linger invisibly.
39. As an initiator, I want to be notified when an online target declines or closes the exchange GUI, so that I know the exchange did not happen.
40. As a target, I want accepting to work even if the initiator disconnected, so that the snapshotted exchange can still complete.
41. As a target, I want accepting to operate on the Characters from the hit moment even if someone switched Characters, so that the exchange identity stays stable.
42. As a target, I want acceptance feedback to name the snapshotted Characters, so that messages remain accurate after Character switches.
43. As a player, I want successful exchange acceptance to list both snapshotted Characters, so that the new contact appears and the exchange does not look broken.
44. As a player, I want failed or declined exchanges not to change listing state, so that seeking alone does not make my Character visible.
45. As a player, I want `/phonebook toggle` to flip my active Character's Phonebook Listing, so that I can quickly opt in or out.
46. As a player, I want `/phonebook toggle listed` to explicitly list my active Character, so that I can opt in without relying on current state.
47. As a player, I want `/phonebook toggle unlisted` to explicitly unlist my active Character, so that I can hide myself from other Phonebooks.
48. As a player, I want `/phonebook toggle` to fail cleanly when I have no active Character, so that I know which Character state would have been changed.
49. As an admin, I want `/phonebook debug` to require a separate debug permission, so that ordinary Phonebook users cannot inspect hidden relationships.
50. As an admin, I want `/phonebook debug` to inspect myself when run without an argument, so that I can diagnose my current Account quickly.
51. As an admin or console user, I want `/phonebook debug <player>` to inspect an online Account, so that I can diagnose another Account.
52. As an admin, I want debug output for all Characters owned by an Account, so that Character-based contacts and listings are visible.
53. As an admin, I want debug output to include display names and raw UUIDs, so that I can read and repair data.
54. As an admin, I want UUIDs to be clickable copy-to-clipboard components, so that diagnostics are practical.
55. As an admin, I want debug output to be bounded, so that large Phonebooks do not flood chat.
56. As a server operator, I want Phonebook Contacts and listings persisted immediately, so that player changes survive crashes.
57. As a server operator, I want malformed persisted data skipped with warnings and omitted from later saves, so that bad manual edits do not poison the mechanic.
58. As a server operator, I want persisted output sorted, so that diffs and manual inspection remain stable.
59. As a server operator, I want no cached Character names or skins in Phonebook persistence, so that SneakyCharacterManager remains the source of truth.

## Implementation Decisions

- The Phonebook mechanic registers only when SneakyCharacterManager and SneakyCellPhones are enabled.
- The Phonebook command requires `sneakymisc.phonebook` for player-facing actions.
- `/phonebook debug` and `/phonebook debug <player>` require `sneakymisc.phonebook.debug`.
- Normal player-facing Phonebook messages use short colored chat messages without a prefix.
- Normal player-facing Phonebook messages go through a central MiniMessage translator lookup table using keys under `sneakymisc.phonebook.*`.
- Message send sites use `Component.translatable(...)` with named Adventure translation arguments for dynamic values.
- Message color conventions are green for successful changes, red for failures, yellow for neutral or waiting states, and gold for Character names. Colors and formatting belong in the central message catalog.
- Debug output may be hard-coded Adventure components, but tests should not ossify exact debug text.
- `/phonebook` is player-only and requires the executing Account to have an active Character.
- `/phonebook toggle [{listed|unlisted}]` applies to the executing Account's currently active Character. No argument toggles; an argument sets the state explicitly.
- Phonebook Contacts are bidirectional relationships between two Characters.
- Phonebook Contacts are stored and compared as canonical unordered Character UUID pairs.
- Contact keys use lowercase Character UUID strings sorted lexicographically. Do not use `UUID.compareTo()` for canonicalization.
- Phonebook persistence is stored in a single `phonebooks.yml` file in the plugin data folder.
- `phonebooks.yml` has no version field.
- Persisted listings are semantically a set of listed Character UUIDs. Omitted listing means unlisted.
- Persisted contacts are keyed by `<lower-character-uuid>_<higher-character-uuid>`.
- Each persisted contact stores `accountA` for the lower Character UUID and `accountB` for the higher Character UUID.
- Save output sorts listings and contact keys lexicographically.
- Save immediately after every contact or listing mutation.
- Malformed persisted contacts and malformed listing entries are skipped with a warning and are not preserved on the next save.
- Character display data and skins are not cached in Phonebook persistence.
- On GUI render, resolve current Character data from SneakyCharacterManager using the stored Account UUIDs and `Character.getPlayerCharacters(accountUuid)`.
- If a persisted contact's Character UUID cannot be found under its stored Account UUID, hide it from normal player GUIs and report it through debug.
- A Character cannot move to another Account, so storing Account UUIDs in contacts is valid.
- A reachable Character is a listed Character whose controlling Account is online; the Account does not need to be embodying that Character.
- Opening `/phonebook` does not list the active Character.
- Clicking Add Contact does not list the active Character.
- Accepting a Phonebook Exchange creates the contact and lists both snapshotted Characters.
- Duplicate exchange attempts do not relist hidden Characters.
- The Phonebook browsing GUI uses 54 slots.
- Phonebook custom GUIs are identified by custom `InventoryHolder` classes, not by inventory title.
- The Phonebook browsing GUI holder carries the viewer Account UUID, owner Character UUID, page number, and a render token for the current inventory contents.
- The accept/decline exchange GUI holder carries an exchange id and target Account UUID.
- Contact item metadata stores the selected contact Character UUID only. Click handlers re-read the Phonebook Contact using the GUI owner Character and contact Character.
- Contact slots are columns 1 through 7 across all rows, for 42 contacts per page.
- Slot 8 is Add Contact.
- Slot 45 is previous page when applicable.
- Slot 53 is next page when applicable.
- Unused control and contact slots remain empty.
- Visible contacts sort by current Character display name case-insensitively, then Character UUID.
- Contact items initially render as default player heads.
- Skin resolution uses SneakyCharacterManager's skin cache asynchronously. A completed skin update is applied only if the viewer still has the same Phonebook page open and the slot still represents the same Character UUID.
- Each full browsing GUI render has a unique render token. Late skin futures are ignored unless the currently open holder token, page, slot, and contact Character UUID still match.
- Skin resolution failures leave the default head silently.
- All click and drag interactions in custom Phonebook inventories are cancelled before action routing.
- Browsing GUI page navigation and refreshes rebuild the current open top inventory contents in place rather than opening a new inventory view.
- Left-clicking a contact starts or reuses a SneakyCellPhones call through the CellPhones API.
- Phonebook passes the selected contact Character's current display name as the CellPhones call override.
- Phonebook does not enforce SneakyCellPhones call permissions or duplicate CellPhones call-state policy.
- A stale visible contact remains callable after unlisting if the target Account is still online.
- A call attempt fails and refreshes if the target Account is offline.
- Pressing swap-offhand on a contact removes the bidirectional Phonebook Contact without confirmation.
- Contact removal sends local confirmation only and does not notify the counterparty.
- Contact removal does not alter either Character's listing state.
- Phonebook browsing GUIs close when the Account changes Character.
- Call and remove actions still validate that the viewer is embodying the GUI owner Character.
- Add Contact starts a 30-second Phonebook Seeking state.
- The 30-second timeout applies only to seeking a target, not to the accept/decline GUI.
- Phonebook never cancels damage for exchange triggering.
- The damage resolver should handle damaged Player targets and Player damagers. It may also handle Projectile damagers whose shooter is a Player when straightforward.
- Soft invalid seeking hits continue seeking: self-target and non-player target.
- Hard invalid seeking hits cancel seeking: target lacks active Character, initiator no longer has active Character, duplicate Phonebook Contact, or target has an inventory UI open.
- Target busy means the target has any inventory UI open or is holding an item on their cursor. Phonebook should not replace that UI.
- A target is not busy only when their open top inventory is the normal crafting inventory and their cursor item is empty.
- A target who is also seeking remains a valid target, but opening the accept/decline GUI cancels their seeking state.
- Clicking Add Contact while already in Phonebook Seeking resets the 30-second timer and sends a neutral waiting message.
- An initiator may have multiple outgoing exchange requests open by repeatedly using Add Contact.
- Initiator quit cancels only active seeking. Already opened accept/decline requests remain valid.
- Target quit cancels that target's open accept/decline exchange.
- Closing the accept/decline GUI without choosing counts as decline.
- Accept/decline click handling cancels the click, resolves once, and schedules inventory close for the next tick.
- The scheduled close must not also trigger close-as-decline.
- The accept/decline exchange snapshots both active Characters at hit time.
- Accepting operates on snapshotted Characters even if either Account switched Characters or the initiator disconnected.
- The accept/decline GUI is not closed on Character switch because exchange identity is snapshotted.
- Debug without `<player>` inspects the sender. Debug with `<player>` resolves online Accounts only in the first version.
- Debug inspects all Characters owned by the selected Account.
- Debug includes clickable copy-to-clipboard UUID components and bounded summaries.

## Testing Decisions

- Tests should focus on externally visible behavior: persisted YAML shape, command/listener outcomes, state transitions, visible contact resolution, and action routing. Avoid testing private helper implementation details directly.
- Tests should not assert exact rendered player-facing message text. For normal messages, assert translation keys, named arguments, and the branch/intent being emitted.
- Tests should not ossify exact debug output text.
- Storage tests should cover load/save round trips, canonical contact key creation, lexicographic sorting, listing set semantics, omitted listing defaults, malformed contact skipping, malformed listing skipping, duplicate prevention, and no version field.
- State-machine tests should cover Phonebook Seeking timeout, soft invalid hits, hard invalid hits, duplicate exchange rejection, target busy UI rejection, target seeking cancellation, multiple outgoing requests, initiator quit, target quit, close-as-decline, accept snapshots, and accept after Character switch or initiator disconnect.
- Resolution tests should cover visible contacts from stored Character/Account UUIDs, listed/unlisted state, online/offline Account state, Accounts embodying another Character, Accounts embodying no Character, missing Character UUIDs from SneakyCharacterManager, and sorting.
- Command and listener tests should cover permission gates, player-only requirements, active Character requirements, `/phonebook toggle` modes, `/phonebook debug` scope, GUI click cancellation, drag cancellation, page control routing, call routing, and remove routing.
- Existing tests are Kotlin/JUnit tests around configuration behavior. New tests should follow that style where possible and add small seams around storage and state logic so Bukkit-heavy behavior can be tested without a full client.
- Human server smoke testing is expected for Minecraft-client behavior that is difficult to observe in automated tests: actual inventory opening, item movement prevention, swap-offhand click behavior, async head updates in slots, SneakyCellPhones invite behavior, and Character-switch GUI closing.

## Out of Scope

- Offline Account lookup for `/phonebook debug <player>`.
- Admin repair commands for malformed or hidden contacts.
- Removing hidden/unlisted contacts through the normal player GUI.
- Queued offline notifications for initiators.
- Live-updating already open Phonebook browsing GUIs on every join, quit, or listing change.
- Caching Character display names or skins inside Phonebook persistence.
- Replacing or changing SneakyCellPhones call-state policy.
- Supporting Character moves between Accounts.

## Further Notes

- `Character.getPlayerCharacters(accountUuid)` is synchronous and backed by SneakyCharacterManager's cache, so no additional Phonebook cache is needed for the first version.
- Manual validation should be done by a human in a Minecraft client because LLM agents cannot reliably observe the actual game client behavior.
- The persistence shape is:

```yaml
listings:
  - <listed-character-uuid>

contacts:
  <lower-character-uuid>_<higher-character-uuid>:
    accountA: <account-for-lower-character>
    accountB: <account-for-higher-character>
```
