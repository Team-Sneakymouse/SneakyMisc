# Plugin version history

Every time a Paper server or a Velocity proxy starts, this mechanic writes a plugin version snapshot to PostgreSQL through SneakyDB: what the server actually loaded, as opposed to what was uploaded or staged. A snapshot records the server, the startup time, the platform version, and one row for each loaded plugin, Ignite mod, failed JAR, and unapplied update. Snapshots are never pruned, so "what was running on this server at time X" is the server's latest snapshot at or before X.

The same SneakyMisc JAR is a Paper plugin (`paper-plugin.yml`) and a Velocity plugin (`velocity-plugin.json`). On a proxy, only this mechanic runs; none of the Paper mechanics are loaded.

## When a snapshot is taken

- **Paper:** on `ServerLoadEvent` with type `STARTUP`, once every plugin has been enabled. Reloads are not recorded.
- **Velocity:** on `ProxyInitializeEvent`, at normal order, after SneakyDB has registered its provider.

The plugin list is read on the startup thread. Directory scans, Ignite mod lookup, and the database write run off it, so recording never delays or breaks startup.

## Table

One table, `plugin_version_history`, in the `public` schema. SneakyMisc creates it at startup with `create table if not exists`; whichever platform starts first creates it. Paper servers and proxies write to the same table with the same column meanings.

```sql
create table if not exists plugin_version_history (
	server_id text not null,
	server_name text not null,
	started_at timestamptz not null,
	platform text not null,
	platform_version text not null,
	minecraft_version text,
	kind text not null,
	name text not null,
	version text,
	jar text,
	enabled boolean,
	primary key (server_id, started_at, kind, name)
);
```

| Column | Meaning |
| --- | --- |
| `server_id` | Stable server id from SneakyUtils' `ServerUtil.getServerId()`, derived from the `P_SERVER_NAME` environment variable (`Dev Main` → `dev-main`). A renamed server starts a new history under its new id. |
| `server_name` | `P_SERVER_NAME` as set, for display. |
| `started_at` | When the snapshot was taken, in UTC (microsecond precision). Identifies the snapshot together with `server_id`. |
| `platform` | `paper` or `velocity`. |
| `platform_version` | `Bukkit.getVersion()` on Paper; the proxy's name and version on Velocity, e.g. `Velocity 3.4.0-SNAPSHOT (git-…)`. |
| `minecraft_version` | `Bukkit.getMinecraftVersion()` on Paper; null on Velocity. |
| `kind` | Row kind, below. |
| `name` | Plugin name (Paper) or id (Velocity), mod id, or the filename for `failed` and `unapplied`. |
| `version` | Plugin or mod version; null for `failed` and `unapplied`. |
| `jar` | JAR filename only, not a path. |
| `enabled` | Whether Paper reports the plugin as enabled after startup; null on Velocity and for other kinds. This is Paper's state, not proof the plugin works: a plugin that gives up inside its own `onEnable` without disabling itself still counts as enabled. |

Every row of a snapshot repeats its server and startup columns. Later schema changes must be additive (`add column if not exists`) and are made once, since both platforms share this code.

### Row kinds

| `kind` | Paper | Velocity | What it is |
| --- | --- | --- | --- |
| `plugin` | yes | yes | Each plugin in the plugin manager. On Velocity this includes the proxy's own `velocity` entry, which has no JAR. |
| `mod` | yes | yes | Each Ignite mod loaded from the mods directory. Servers not running under Ignite have no mod rows, even if a `mods/` directory exists. |
| `failed` | yes | yes | A `*.jar` directly in the plugins directory that did not become a loaded plugin, for example because of a broken descriptor or a missing dependency. Without these rows a broken plugin would look removed. Matched by filename only, never by path. |
| `unapplied` | yes | no | A file still in Paper's update folder after startup. The folder is the one set by `settings.update-folder` in `bukkit.yml`: Mother-Mouse-Discord stages into `plugins/.update/`, while Paper's stock default is `plugins/update/`. Paper ignores staged JARs that have no installed copy, so anything left there was not applied. Not recorded when the setting is blank, which makes the update folder the plugins directory itself. Velocity has no update folder. |

Ignite mods come from Ignite's runtime API (`Ignite.mods().containers()`, without the launcher and game entries). If Ignite is running but that API cannot be reached, the mechanic reads `id` and `version` from `ignite.mod.json` in each JAR of the mods directory (`-Dignite.mods`, default `./mods`) instead.

## Writing

A snapshot is one multi-row `INSERT`, so it is written completely or not at all; no transactions are needed. The sequence follows SneakyDB's readiness contract: `whenReady()`, then `create table if not exists`, then the insert.

If a snapshot is not recorded, the mechanic logs exactly one line and carries on. There is no retry and no local spool: the server's own log (`latest.log` on Paper, the proxy log on Velocity) lists every plugin and version it loaded and is the backup for a missed snapshot.

| Situation | Log line |
| --- | --- |
| `P_SERVER_NAME` missing, blank, or without any letter or digit | warning: `…the server identity is missing…` |
| SneakyDB not installed | info: `…SneakyDB is not installed` |
| SneakyDB installed but disabled or unreachable | error: `…SneakyDB is unavailable (…)` |
| SneakyDB not ready (initialization failed) | error: `…SneakyDB is not ready (…)` |
| Table creation fails | error: `…creating the table failed (…)` |
| Insert fails | error: `…the insert failed (…)` |
| Success | info: `Recorded plugin version snapshot for <server_id>: N plugins, N mods, N failed JARs, N unapplied files` |

SneakyDB is optional on both platforms: a joined-classpath, non-required dependency in `paper-plugin.yml`, and `{"id": "sneakydb", "optional": true}` in `velocity-plugin.json`. All servers' SneakyDB configurations point at the same database.

## Limits

- Versions are compared as strings only. A rebuilt JAR that keeps its version string does not show as a change.
- Snapshots are taken at startup only. Plugins loaded, unloaded, or replaced while the server runs are not seen until the next startup.
- A snapshot that failed to write is gone; check that startup's log instead.
- While the table does not exist yet, servers that start at the same moment can collide on `create table if not exists`; PostgreSQL then fails all but one, and those lose that one snapshot. Start a single server first when rolling this out, or create the table by hand.
- SneakyMisc only writes. It has no commands, queries, or reports over this data.

## Reading the data

Not implemented in SneakyMisc. These queries are hints for consumers, such as Mother-Mouse-Discord, and show that the table supports the questions it was built for. All times are UTC.

**Which plugins and mods changed on one server between two times, with their versions at the start and end.** This compares the two ends of the window only, so a plugin that went A → B → A inside the window does not appear. The version at the start comes from the server's last snapshot at or before `:from`; the version at the end from its last snapshot before `:to`. For 2026-11-01 to 2026-11-07 inclusive, `:from = '2026-11-01 00:00Z'` and `:to = '2026-11-08 00:00Z'`.

```sql
with
s  as (select max(started_at) as t from plugin_version_history
       where server_id = :server_id and started_at <= :from),
e  as (select max(started_at) as t from plugin_version_history
       where server_id = :server_id and started_at <  :to),
sp as (select kind, name, version, jar from plugin_version_history, s
       where server_id = :server_id and started_at = s.t and kind in ('plugin', 'mod')),
ep as (select kind, name, version from plugin_version_history, e
       where server_id = :server_id and started_at = e.t and kind in ('plugin', 'mod')),
ef as (select name as jar from plugin_version_history, e
       where server_id = :server_id and started_at = e.t and kind = 'failed')
select kind, name, sp.version as version_at_start, ep.version as version_at_end,
       case when sp.name is null then 'added'
            when ep.name is not null then 'updated'
            when sp.jar in (select jar from ef) then 'failed to load'
            else 'removed' end as change
from sp full join ep using (kind, name)
where sp.name is null or ep.name is null or sp.version is distinct from ep.version
order by kind, name;
```

Read `change`, not the version columns, to tell what happened: a version can be null for a plugin or mod that declares none, so a null version does not mean added or removed.

- `failed to load` means the JAR the plugin ran from at the start is a failed JAR at the end, for example after one of its dependencies was removed. A replacement JAR that fails under a new filename shows as `removed`, so also look at the `failed` rows of the end snapshot.
- If the server has no snapshot at or before `:from`, everything shows as `added`; report that as "no baseline" instead.
- If the server has no snapshot before `:to` at all, the result is empty, which is "no data", not "no changes".

**What each server is running now** (its latest snapshot):

```sql
select h.server_name, h.kind, h.name, h.version, h.started_at
from plugin_version_history h
join (select server_id, max(started_at) as t from plugin_version_history group by server_id) l
  on l.server_id = h.server_id and l.t = h.started_at
where h.kind in ('plugin', 'mod')
order by h.server_name, h.kind, h.name;
```

**When each version of a plugin first ran on each server.** Compare names in lower case: the same plugin is recorded under its name on Paper (`SneakyDB`) and under its id on Velocity (`sneakydb`).

```sql
select server_id, server_name, version, min(started_at) as first_running
from plugin_version_history
where kind = 'plugin' and lower(name) = 'sneakyexample'
group by server_id, server_name, version
order by first_running;
```

**What changed on a server at its last restart:** the window query with `:from` and `:to` set around that restart.

The primary key also serves "latest snapshot for a server at or before time X" lookups, so no extra index is needed.
