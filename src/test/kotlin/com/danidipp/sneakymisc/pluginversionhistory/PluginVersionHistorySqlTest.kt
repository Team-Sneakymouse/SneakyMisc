package com.danidipp.sneakymisc.pluginversionhistory

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PluginVersionHistorySqlTest {
    private val startedAt = OffsetDateTime.of(2026, 11, 11, 3, 4, 5, 123_456_789, ZoneOffset.UTC)

    @Test
    fun `the schema matches the table shared by Paper and Velocity`() {
        val normalized = PluginVersionHistorySql.CREATE_TABLE.replace(Regex("\\s+"), " ")

        assertEquals(
            "create table if not exists plugin_version_history ( server_id text not null, server_name text not null, " +
                "started_at timestamptz not null, platform text not null, platform_version text not null, " +
                "minecraft_version text, kind text not null, name text not null, version text, jar text, " +
                "enabled boolean, primary key (server_id, started_at, kind, name) )",
            normalized,
        )
    }

    @Test
    fun `a snapshot is one multi-row insert with eleven parameters per row`() {
        val snapshot = PluginVersionSnapshot(
            ServerIdentity("dev-main", "Dev Main"), startedAt, "paper", "26.2-116-37dc545 (MC: 26.2)", "26.2",
            listOf(SnapshotRow.plugin("SneakyMisc", "1.0", "SneakyMisc.jar", true), SnapshotRow.failed("Broken.jar")),
        )

        val insert = PluginVersionHistorySql.insert(snapshot)

        assertEquals(
            "insert into plugin_version_history (server_id, server_name, started_at, platform, platform_version, " +
                "minecraft_version, kind, name, version, jar, enabled) values " +
                "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            insert.sql,
        )
        val micros = OffsetDateTime.of(2026, 11, 11, 3, 4, 5, 123_456_000, ZoneOffset.UTC)
        val shared = listOf("dev-main", "Dev Main", micros, "paper", "26.2-116-37dc545 (MC: 26.2)", "26.2")
        assertEquals(
            shared + listOf("plugin", "SneakyMisc", "1.0", "SneakyMisc.jar", true) +
                shared + listOf("failed", "Broken.jar", null, "Broken.jar", null),
            insert.parameters,
        )
    }

    @Test
    fun `startedAt is bound as an OffsetDateTime and Velocity's minecraft version as null`() {
        val snapshot = PluginVersionSnapshot(
            ServerIdentity("proxy", "Proxy"), startedAt, "velocity", "Velocity 3.4.0", null,
            listOf(SnapshotRow.plugin("sneakymisc", "1.0", "SneakyMisc.jar", null)),
        )

        val parameters = PluginVersionHistorySql.insert(snapshot).parameters

        assertTrue(parameters[2] is OffsetDateTime)
        assertEquals(null, parameters[5])
        assertEquals(null, parameters[10])
    }

    @Test
    fun `an empty or oversized snapshot is not written`() {
        val empty = PluginVersionSnapshot(ServerIdentity("a", "A"), startedAt, "paper", "v", "26.2", emptyList())

        assertFailsWith<IllegalArgumentException> { PluginVersionHistorySql.insert(empty) }
        val rows = (0..PluginVersionHistorySql.MAX_ROWS).map { SnapshotRow.failed("$it.jar") }
        assertFailsWith<IllegalArgumentException> { PluginVersionHistorySql.insert(empty.copy(rows = rows)) }
        assertEquals(PluginVersionHistorySql.MAX_ROWS * 11, PluginVersionHistorySql.insert(empty.copy(rows = rows.drop(1))).parameters.size)
    }
}
