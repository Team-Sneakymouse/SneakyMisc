# Spec: SneakyDB jar migrations (`plugin_version_history`)

## Goal

Adopt [team database migrations](https://github.com/Team-Sneakymouse/team-development-policy/blob/main/policy/database-migrations.md) for the `plugin_version_history` table used by Paper and Velocity startup snapshots.

## Non-goals

- No column changes. (The table moved from `public` to the `sneakymisc` schema in `V002`.)
- No data backfill (append-only snapshots).

## Plugin id

`migrate` uses `sneakymisc` (`PluginVersionHistorySql.IDENTIFIER`).

## Scripts

| Script | Purpose |
|--------|---------|
| `V001__init.sql` | Current Live shape previously created with runtime `CREATE TABLE IF NOT EXISTS`. |
| `V002__move_to_sneakymisc_schema.sql` | Moves the table into the `sneakymisc` schema. The operator creates the schema and grants SneakyDB's user `USAGE, CREATE` on it first. Breaks jars that only have `V001` (they write to the unqualified name). |

## Runtime

When SneakyDB is installed, Paper registers migrations in `PluginVersionHistoryModule` during `onEnable`, and Velocity registers at the start of `ProxyInitializeEvent`. The snapshot writer waits for `whenReady()` and the migration future before its insert. On migration failure it logs one error line and records nothing (no runtime DDL, no retry).

## Operator adoption

1. The table may already exist on Dev/Staging/Live from runtime DDL.
2. Before the first jar that calls `migrate`, record `V001` as applied for plugin `sneakymisc` on each database where the table already matches (see SneakyDB README, *Existing tables*).
3. Create schema `sneakymisc` and `grant usage, create on schema sneakymisc to <SneakyDB user>` before the first jar with `V002` starts there.
4. Deploy; only newer scripts run on later releases.