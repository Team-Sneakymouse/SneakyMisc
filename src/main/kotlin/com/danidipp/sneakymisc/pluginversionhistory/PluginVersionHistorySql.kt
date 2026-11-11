package com.danidipp.sneakymisc.pluginversionhistory

import java.time.temporal.ChronoUnit

/**
 * Schema and write statement for `plugin_version_history`. Paper servers and Velocity proxies both run this exact
 * schema; later changes must be additive (`add column if not exists`). SneakyMisc only writes; it reads nothing back.
 */
object PluginVersionHistorySql {
    const val CREATE_TABLE = """create table if not exists plugin_version_history (
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
)"""

    private val COLUMNS = listOf(
        "server_id", "server_name", "started_at", "platform", "platform_version", "minecraft_version",
        "kind", "name", "version", "jar", "enabled",
    )

    /** PostgreSQL's limit on bind parameters in one statement. */
    const val MAX_PARAMETERS = 32767
    val MAX_ROWS = MAX_PARAMETERS / COLUMNS.size

    data class Statement(val sql: String, val parameters: List<Any?>)

    /**
     * One multi-row `INSERT` for the whole snapshot, so a failed write leaves nothing behind. Nulls are bound untyped,
     * which PostgreSQL resolves from the target column. `started_at` is an `OffsetDateTime` (pgjdbc maps it to
     * `timestamptz`; it rejects `Instant`), truncated to PostgreSQL's microsecond precision.
     */
    fun insert(snapshot: PluginVersionSnapshot): Statement {
        require(snapshot.rows.isNotEmpty()) { "A snapshot needs at least one row" }
        require(snapshot.rows.size <= MAX_ROWS) { "A snapshot of ${snapshot.rows.size} rows exceeds $MAX_ROWS rows" }
        val placeholder = COLUMNS.joinToString(", ", "(", ")") { "?" }
        val sql = "insert into plugin_version_history (${COLUMNS.joinToString(", ")}) values " +
            snapshot.rows.joinToString(", ") { placeholder }
        val startedAt = snapshot.startedAt.truncatedTo(ChronoUnit.MICROS)
        val parameters = snapshot.rows.flatMap { row ->
            listOf(
                snapshot.server.id, snapshot.server.name, startedAt, snapshot.platform, snapshot.platformVersion,
                snapshot.minecraftVersion, row.kind.column, row.name, row.version, row.jar, row.enabled,
            )
        }
        return Statement(sql, parameters)
    }
}
