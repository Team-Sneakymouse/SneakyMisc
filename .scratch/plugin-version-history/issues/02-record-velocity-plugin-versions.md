# Record Velocity plugin versions at startup

Status: Implemented — automated checks and a throwaway-server run pass; live server verification remains.

## Context

[01](01-record-plugin-versions-at-startup.md) records a snapshot of the loaded plugins and Ignite mods on every Paper server startup, in the `plugin_version_history` table. Velocity proxies need the same record. SneakyMisc is Paper-only, so this needs a Velocity plugin, probably SneakyMisc-Velocity.

## Proposed behaviour

On each proxy startup, once all plugins are loaded (`ProxyInitializeEvent`), write one snapshot to the `plugin_version_history` table defined in 01, using the same columns and rules:

- `platform = 'velocity'`, `platform_version` = the proxy's version, `minecraft_version` null.
- One `kind = 'plugin'` row per plugin in the proxy's plugin manager: id, version, JAR filename. `enabled` stays null, since Velocity has no enabled state.
- One `kind = 'failed'` row per `*.jar` directly in `plugins/` that did not become a loaded plugin.
- No `mod` or `unapplied` rows.

Write the whole snapshot in a single `INSERT`, off the startup path. If the server identity is missing or the write fails, log once and carry on: no retry, the proxy's log is the backup. Create the table with the same `create table if not exists` as 01; whichever platform starts first creates it.

## Acceptance criteria

- Every proxy startup with database access writes exactly one snapshot covering every loaded plugin and every JAR that failed to load.
- Rows use the shared table and column meanings from 01, with `platform = 'velocity'`.
- Proxy startup is never blocked or failed by this, including when the database is unavailable.
- Contains no code that reads the snapshots back.

## Open questions

- **Database access:** SneakyDB is a Paper plugin and has no Velocity side. Options: port SneakyDB's PostgreSQL adapter to Velocity, or give the Velocity plugin its own small JDBC connection and config.
- **Server identity:** where does a proxy get its `server_id` and `server_name`? SneakyUtils' `ServerUtil` is Paper-side. Reading `P_SERVER_NAME` directly may be enough.
- **Packaging:** a separate SneakyMisc-Velocity repository, or one JAR that is both a Paper and a Velocity plugin? SneakyDiagnostics already ships a combined `paper-plugin,velocity-plugin` JAR.

## Related

- [01 – Record installed plugin versions at each server startup](01-record-plugin-versions-at-startup.md) defines the table and the read-side queries.

## Comments

### 2026-11-11

Decisions from review. Where they differ from the text above, these win:

- **Packaging:** no separate plugin. SneakyMisc's JAR gets a Velocity entry point (`velocity-plugin.json`) and is published as `paper-plugin,velocity-plugin`, like SneakyDiagnostics. The Paper mechanics are never loaded on the proxy.
- **Database access:** through SneakyDB, which gets a Velocity entry point of its own (separate PR in the SneakyDB repository). SneakyDB is an optional dependency on the proxy too.
- **Server identity:** SneakyMisc resolves it with `ServerUtil` from `sneakyutils-common`, bundled into the JAR. The proxy is a Pelican server, so `P_SERVER_NAME` is set.
- **Ignite mods:** the proxies run under Ignite (SneakyPatches ships a Velocity patch), so proxy snapshots do have `mod` rows. Still no `unapplied` rows.
- **Failure handling:** if SneakyDB is missing, or the database is unavailable in any way, log one line and record nothing.

### 2026-11-11, implemented

Implemented with ticket 01 in one change. Proxy snapshots need SneakyDB `2026.11.11-6a969d531546` or later installed on the proxy, with database credentials in `plugins/sneakydb/config.yml`.
