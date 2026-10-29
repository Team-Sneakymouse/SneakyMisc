## Agent skills

### Issue tracker

Issues and PRDs are tracked as local markdown files under `.scratch/<feature-slug>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the default five triage status strings: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context repo: root `CONTEXT.md` plus root `docs/adr/`. See `docs/agents/domain.md`.

## Repo-specific rules

- **New mechanics are modules.** Add a mechanic as a package under com.danidipp.sneakymisc.<name> with a <Name>Module : SneakyModule (commands: SneakyMiscCommand, listeners) and a `companion object { val deps = listOf(...) }`, then register it in SneakyMisc.onEnable gated with dependenciesAvailable(). Don't create a separate plugin for small mechanics.
- **Source layout quirk.** Kotlin sources are under src/main/kotlin, the Java bootstrapper under src/main/java, but plugin descriptor and bundled resources are under src/resources (not src/main/resources). Put paper-plugin.yml, messages/ and world-templates/ there.
- **Pocketbase and IO go async, state stays on main.** Pocketbase and file IO calls in modules (leaderboards, dclock, dvzregistrations, databasesync) use runTaskAsynchronously; capture plain data first and re-enter the main thread (runTask) before touching players, blocks or inventories. Record parsing has unit tests (LeaderboardRecordDecodingTest, DClockSettingRecordDecodingTest, RegistrationAccountCacheTest); add one per new record type.
- **Configured worlds are applied at bootstrap.** worlds.yml is read by SneakyMiscBootstrap.java during datapack discovery and written to generated-worlds.zip; changes need a server restart and must not use Bukkit API (bootstrap phase). Follow docs/custom-worlds.md (dimension fields are required, existing Multiverse worlds must be left out) and update its table when adding fields.
- **PDC key namespaces are persisted.** Keep the NamespacedKey strings (sneakymisc:elevator_control_floor, elevator_transit_keypad, elevator_control_mode, elevator door key, crate_decoration, phonebook contact character key; cmilib:cmibackpack and magicspells:magicitem which belong to other plugins) unchanged; migrate with code, not by renaming.
- **Respect module independence and dependencies.** Modules should not call each other (CONTEXT.md); the one exception is Kobold, which wraps the chat renderer so it must be registered after Chat. When a module needs an optional plugin, list it in its deps and in paper-plugin.yml with required: false; do not import optional-plugin classes from always-registered modules (Attributes, Crates, Paintings).
