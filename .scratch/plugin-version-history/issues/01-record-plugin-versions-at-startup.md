# Record installed plugin versions at each server startup

Status: Implemented — automated checks and a throwaway-server run pass; live server verification remains.

## Context

We want a record of which plugin versions run on which server, and since when. This matters most for live servers, where releases go out by hand with `/plugins update` during planned maintenance.

Mother-Mouse-Discord uploads the JARs, but its upload history cannot provide that record:

- An upload is not a running version. Paper applies JARs staged in `plugins/.update/` only on the next restart, and Velocity loads new JARs only on restart. The bot does not restart servers.
- JARs also reach servers in other ways: copied through the panel, removed by hand, or skipped by Paper because no installed copy existed.

Only the server knows what it actually loaded. A SneakyMisc mechanic should record a snapshot of the loaded plugins and Ignite mods every time a Paper server starts. Velocity proxies write to the same table from a Velocity counterpart, tracked in [02](02-record-velocity-plugin-versions.md).

## Scope

SneakyMisc owns the table and writes to it, nothing more. It ships no queries, commands or reports over this data. Reading it is the job of consumers outside SneakyMisc (see [Reading the data](#reading-the-data-not-part-of-sneakymisc)).

## Proposed behaviour

On each startup, once every plugin has been enabled (`ServerLoadEvent` with type `STARTUP`), write one snapshot to PostgreSQL through SneakyDB:

- **Server:** `ServerUtil.getServerId()` and `getServerName()` from SneakyUtils (`P_SERVER_NAME`). If the server identity is missing, log a warning and record nothing.
- **Startup:** the time, the Paper version and build (`Bukkit.getVersion()`), and the Minecraft version.
- **Each plugin** in `Bukkit.getPluginManager().plugins`: name, version (`pluginMeta.version`), JAR filename from the plugin class's code source, and whether it ended up enabled.
- **Each Ignite mod** in `mods/`: mod id and version, and its JAR filename. Use Ignite's runtime API if it exposes the loaded mods; otherwise read `ignite.mod.json` from each JAR in `mods/`. Servers without Ignite have no mod rows.
- **Failed JARs:** filenames of `*.jar` files directly in `plugins/` that did not become a loaded plugin, for example because of a broken `plugin.yml` or a missing dependency. These never appear in the plugin manager, so without them a broken plugin would look removed. Match by filename, not full path: Paper loads remapped Spigot plugins from `plugins/.paper-remapped/` under the same filename.
- **Leftovers in `plugins/.update/`:** filenames still there after startup. Paper ignores staged JARs that have no installed copy, so anything left here was not applied and is worth surfacing.

Recording must never delay or break startup. Scan files and write the snapshot off the main thread. If the write fails, log an error and carry on: no retry, no local spool. Paper's `latest.log` already lists every plugin and version it loads, so it serves as the backup for a missed snapshot. SneakyDB should be a soft dependency, so the mechanic stays inactive (with one log line) when SneakyDB is missing or not ready.

Store a full snapshot on every startup and keep them all; there is no pruning. At about 150 plugins times a few restarts a day the volume is small, and full snapshots make "what was running at time X" a single lookup. A pruned history could also lose the snapshot that says what a long-running server was on at the start of a queried window.

### Server identity

`server_id` is stable and unique per server, and servers are not renamed. If one ever is, its history ends under the old id and starts fresh under the new one; that is intended.

### Time

All times are UTC. `started_at` is a `timestamptz` and consumers give their window bounds in UTC.

### Table

One table in the `public` schema. A dedicated schema is not needed: SneakyDB manages the role, other SneakyDB consumers (SneakyLeveling, SneakyRoamin) already keep prefixed tables in `public`, and it avoids a one-off `create schema` by an administrator.

One table rather than a `startups` table plus a `startup_plugins` table, because:

- A snapshot is one multi-row `INSERT`, which is atomic without needing transactions. A failed write leaves nothing behind, rather than a partial snapshot that would read as "plugins were removed". About 170 rows × 11 columns stays well under PostgreSQL's 32767-parameter limit. (SneakyDB could add transactions, but this design doesn't need them; a two-table design wouldn't either, using `insert … returning` in a single statement.)
- Two plugins (SneakyMisc on Paper, its Velocity counterpart) write the same data. One `create table if not exists` is simple to keep identical in both.
- Each snapshot repeats its server and startup columns on every row. At this volume that costs nothing.

```sql
create table if not exists plugin_version_history (
	server_id text not null,
	server_name text not null,
	started_at timestamptz not null,
	platform text not null,           -- 'paper' or 'velocity'
	platform_version text not null,   -- Bukkit.getVersion() on Paper
	minecraft_version text,           -- null on Velocity
	kind text not null,               -- 'plugin', 'mod', 'failed', 'unapplied'
	name text not null,               -- plugin or mod id; the filename for 'failed' and 'unapplied'
	version text,                     -- null for 'failed' and 'unapplied'
	jar text,                         -- JAR filename
	enabled boolean,                  -- 'plugin' rows only
	primary key (server_id, started_at, kind, name)
);
```

The primary key also serves "latest snapshot for a server at or before time X" lookups, so no extra index is needed. Later schema changes must be additive (`add column if not exists`) and made in both writers.

## Reading the data (not part of SneakyMisc)

These are the questions the table must be able to answer. The queries show that the schema supports them; they are not implemented in SneakyMisc.

**Which plugins and mods changed version on one server between two times, with their versions at the start and end.** This compares the two ends of the window only, so a plugin that went A → B → A inside the window does not appear. The version at the start comes from the server's last snapshot at or before `:from`; the version at the end comes from its last snapshot before `:to`. A null start version means the plugin was added; a null end version means it was removed. For 2026-11-01 to 2026-11-07 inclusive, `:from = '2026-11-01 00:00Z'` and `:to = '2026-11-08 00:00Z'`.

```sql
with
s  as (select max(started_at) as t from plugin_version_history
       where server_id = :server_id and started_at <= :from),
e  as (select max(started_at) as t from plugin_version_history
       where server_id = :server_id and started_at <  :to),
sp as (select kind, name, version from plugin_version_history, s
       where server_id = :server_id and started_at = s.t and kind in ('plugin', 'mod')),
ep as (select kind, name, version from plugin_version_history, e
       where server_id = :server_id and started_at = e.t and kind in ('plugin', 'mod'))
select kind, name, sp.version as version_at_start, ep.version as version_at_end
from sp full join ep using (kind, name)
where sp.version is distinct from ep.version
order by kind, name;
```

If the server has no snapshot at or before `:from`, everything shows as added; a consumer should report that as "no baseline" instead.

**What each server is running now** (its latest snapshot):

```sql
select h.server_name, h.kind, h.name, h.version, h.started_at
from plugin_version_history h
join (select server_id, max(started_at) as t from plugin_version_history group by server_id) l
  on l.server_id = h.server_id and l.t = h.started_at
where h.kind in ('plugin', 'mod')
order by h.server_name, h.kind, h.name;
```

**When each version of a plugin first ran on each server:**

```sql
select server_name, version, min(started_at) as first_running
from plugin_version_history
where kind = 'plugin' and name = 'SneakyExample'
group by server_name, version
order by first_running;
```

**What changed on a server at its last restart:** the window query with `:from` and `:to` set around that restart.

## Acceptance criteria

- Every Paper server startup with SneakyDB available writes exactly one snapshot, in a single `INSERT`, covering every loaded plugin and Ignite mod.
- Snapshots record the server identity, startup time (UTC), Paper and Minecraft versions, each plugin's name, version, JAR filename and enabled state, each Ignite mod's id, version and JAR filename, JARs in `plugins/` that failed to load, and any files left in `plugins/.update/`.
- Startup is never blocked or failed by this mechanic, including when the database is unavailable or the server identity is missing. A failed write logs an error and is not retried.
- The mechanic creates its table at startup with `create table if not exists`, following SneakyDB's readiness → schema → write sequence.
- SneakyMisc contains no code that reads the snapshots back.
- Documentation (`docs/plugin-version-history.md`) describes the table, its columns and row kinds, and that the Velocity counterpart writes to the same table.

## Open questions

- **Datapacks:** should the snapshot also cover datapacks?
- **Consumers:** where should the read side live? Mother-Mouse-Discord could use it, for example to show a release as "running" versus "staged" in `/plugins update`, to post what changed after a maintenance restart, or to answer the window question above.

## Related

- [02 – Record Velocity plugin versions at startup](02-record-velocity-plugin-versions.md) writes proxy snapshots to the same table.
- Mother-Mouse-Discord now pins `/plugins update` deployments to the version shown in the release menu, and lists that version in the deploy message (uncommitted on 2026-11-11). Uploads are recorded only in Discord. This ticket covers what actually runs.
- SneakyPipeMonitor already reads Mother-Mouse-Discord's Postgres data through SneakyDB and watches `plugins/.update/`, so it can serve as a reference for the SneakyDB integration.

## Comments

### 2026-11-11

Decisions from review:

- Keep all snapshots; no pruning.
- A failed write only logs an error. Paper's `latest.log` is the backup.
- Record JARs that failed to load (now `kind = 'failed'` rows).
- Compare version strings only; drop the SHA-256 column and JAR hashing.
- The window question compares start and end versions only (absolute change), for one server at a time, in UTC.
- `server_id` is stable and unique; a renamed server counts as a new one.
- SneakyMisc manages the schema and writes snapshots only; queries belong to consumers.

### 2026-11-11 (second round)

- The database role is handled by SneakyDB.
- Ignite mods are in scope for this ticket. Velocity proxies are in scope too, split into ticket 02, probably as a SneakyMisc-Velocity plugin.
- The schema question became: dedicated schema or one table in `public`? Proposed: one table in `public`, for the reasons under [Table](#table).
- SneakyDB can add transactions if needed. It isn't needed here: both one-table and two-table designs can write a snapshot in one atomic statement, so the choice rests on simplicity.

### 2026-11-11 (third round)

- Datapacks are out of scope.
- The read side is out of scope. The documentation carries the example queries as hints; no consumer is built in this work.
- All servers' SneakyDB configurations point at the same database.
- If SneakyDB is missing, or the database is unavailable in any way (SneakyDB not ready, schema or insert fails), log one line and record nothing.
- Server identity comes from `ServerUtil` in `sneakyutils-common` (SneakyUtils branch `feat/sneakyutils-common`, to be merged and published).
- Velocity: one combined JAR and a Velocity entry point in SneakyDB; see the comments in [02](02-record-velocity-plugin-versions.md).

### 2026-11-11 (after implementation review)

- The window query above misses two cases. A plugin or mod without a version that is added or removed drops out, because null is not distinct from null. A plugin whose JAR failed to load at the end shows as removed. `docs/plugin-version-history.md` carries the corrected query, which adds a `change` column (`added`, `updated`, `removed`, `failed to load`); use that one.
- Paper 26.2 no longer remaps Spigot plugins into `plugins/.paper-remapped/`. Matching failed JARs by filename stays.
- Paper's stock update folder is `plugins/update/`; `plugins/.update/` is what Mother-Mouse-Discord stages into, so servers need `settings.update-folder: .update` in `bukkit.yml`.

### 2026-11-11, dependencies released

`sneakyutils-common` (SneakyUtils #17) and SneakyDB's Velocity entry point (SneakyDB #7) are merged and published. The build now pins `sneakyutils` and `sneakyutils-common` at `2026.11.11-eca9fb017f37` and `sneakydb-api` at `2026.11.11-6a969d531546`, and resolves from the team Maven repository alone.

### 2026-11-11, second review

- An error message with line breaks is now joined into one log line, so "exactly one line" also holds for PostgreSQL errors that carry a `Detail:` part.
- The window query in `docs/plugin-version-history.md` now reads `enabled`: a Paper plugin that is still loaded but stopped enabling shows as `disabled`, and every row carries `enabled_at_end`.
