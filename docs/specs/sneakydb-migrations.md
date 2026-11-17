# Spec: SneakyDB jar migrations (`plugin_version_history`)

## Goal

Adopt [team database migrations](https://github.com/Team-Sneakymouse/team-development-policy/blob/main/policy/database-migrations.md) for the `plugin_version_history` table used by Paper and Velocity startup snapshots.

## Non-goals

- No new schema; the table stays in `public` with the same columns.
- No data backfill (append-only snapshots).

## Plugin id

`migrate` uses `sneakymisc` (`PluginVersionHistorySql.IDENTIFIER`).

## Scripts

| Script | Purpose |
|--------|---------|
| `V001__init.sql` | Current Live shape previously created with runtime `CREATE TABLE IF NOT EXISTS`. |

## Runtime

When SneakyDB is installed, Paper registers migrations in `PluginVersionHistoryModule` during `onEnable`, and Velocity registers at the start of `ProxyInitializeEvent`. The snapshot writer waits for `whenReady()` and the migration future before its insert. On migration failure it logs one error line and records nothing (no runtime DDL, no retry).

## Operator adoption

1. The table may already exist on Dev/Staging/Live from runtime DDL.
2. Before the first jar that calls `migrate`, record `V001` as applied for plugin `sneakymisc` on each database where the table already matches (see SneakyDB README, *Existing tables*).
3. Deploy; only newer scripts run on later releases.