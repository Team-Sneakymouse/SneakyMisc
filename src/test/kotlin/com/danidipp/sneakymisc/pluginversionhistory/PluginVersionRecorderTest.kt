package com.danidipp.sneakymisc.pluginversionhistory

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginVersionRecorderTest {
    private val log = RecordingLog()
    private val database = RecordingDatabase()
    private val identity = ServerIdentity("dev-main", "Dev Main")
    private var collected = 0

    private fun record(
        identity: ServerIdentity? = this.identity,
        database: () -> SnapshotDatabase? = { this.database },
        executor: Executor = Executor(Runnable::run),
        collect: (ServerIdentity) -> PluginVersionSnapshot = ::snapshot,
    ): Boolean = PluginVersionRecorder(log, executor).record(identity, database, collect).join()

    private fun snapshot(server: ServerIdentity): PluginVersionSnapshot {
        collected++
        return PluginVersionSnapshot(
            server, OffsetDateTime.of(2026, 11, 11, 0, 0, 0, 0, ZoneOffset.UTC), "paper", "26.2", "26.2",
            listOf(
                SnapshotRow.plugin("SneakyMisc", "1.0", "SneakyMisc.jar", true),
                SnapshotRow.mod("sneakypatches", "0.2.0", "SneakyPatches-0.2.0.jar"),
                SnapshotRow.failed("Broken.jar"),
                SnapshotRow.unapplied("New.jar"),
            ),
        )
    }

    @Test
    fun `records readiness then schema then one insert`() {
        assertTrue(record())

        assertEquals(listOf("whenReady", "execute:create", "execute:insert"), database.calls)
        assertEquals(44, database.insertParameters!!.size)
        assertEquals(
            listOf("INFO Recorded plugin version snapshot for dev-main: 1 plugins, 1 mods, 1 failed JARs, 1 unapplied files"),
            log.lines,
        )
    }

    @Test
    fun `scanning and the write run on the given executor`() {
        val tasks = mutableListOf<Runnable>()
        val future = PluginVersionRecorder(log, tasks::add).record(identity, { database }, ::snapshot)

        assertEquals(0, collected)
        assertEquals(emptyList(), database.calls)
        tasks.single().run()
        assertTrue(future.join())
    }

    @Test
    fun `missing server identity logs one warning and records nothing`() {
        assertFalse(record(identity = null, database = { error("must not be touched") }))

        assertEquals(listOf("WARNING Plugin version snapshot not recorded: the server identity is missing (P_SERVER_NAME is not set or unusable)"), log.lines)
        assertEquals(0, collected)
    }

    @Test
    fun `SneakyDB not installed logs one line and records nothing`() {
        assertFalse(record(database = { null }))

        assertEquals(listOf("INFO Plugin version snapshot not recorded: SneakyDB is not installed"), log.lines)
        assertEquals(0, collected)
    }

    @Test
    fun `SneakyDB that cannot be reached logs one error and records nothing`() {
        assertFalse(record(database = { throw IllegalStateException("SneakyDB is not loaded") }))
        assertFalse(record(database = { throw NoClassDefFoundError("com/sneakydb/SneakyDBProvider") }))

        assertEquals(
            listOf(
                "ERROR Plugin version snapshot not recorded: SneakyDB is unavailable (IllegalStateException: SneakyDB is not loaded)",
                "ERROR Plugin version snapshot not recorded: SneakyDB is unavailable (NoClassDefFoundError: com/sneakydb/SneakyDBProvider)",
            ),
            log.lines,
        )
        assertEquals(0, collected)
    }

    @Test
    fun `the real SneakyDB provider without a running SneakyDB counts as unavailable`() {
        // Off-server the api artifact alone is present, so the provider fails to link rather than throwing
        // "SneakyDB is not loaded"; either way it is one error line.
        assertFalse(record(database = SneakyDBSnapshotDatabase::connect))

        assertEquals(1, log.lines.size)
        assertTrue(log.lines.single().startsWith("ERROR Plugin version snapshot not recorded: SneakyDB is unavailable ("), log.lines.single())
        assertEquals(0, collected)
    }

    @Test
    fun `readiness failure logs one error and runs no SQL`() {
        database.ready = CompletableFuture.failedFuture<Void>(IllegalStateException("SneakyDB initialization failed"))

        assertFalse(record())

        assertEquals(listOf("whenReady"), database.calls)
        assertEquals(
            listOf("ERROR Plugin version snapshot not recorded: SneakyDB is not ready (IllegalStateException: SneakyDB initialization failed)"),
            log.lines,
        )
    }

    @Test
    fun `table creation failure logs one error and skips the insert`() {
        database.fail["create"] = RuntimeException("permission denied for schema public")

        assertFalse(record())

        assertEquals(listOf("whenReady", "execute:create"), database.calls)
        assertEquals(
            listOf("ERROR Plugin version snapshot not recorded: creating the table failed (RuntimeException: permission denied for schema public)"),
            log.lines,
        )
    }

    @Test
    fun `insert failure logs one error and is not retried`() {
        database.fail["insert"] = RuntimeException("connection reset")

        assertFalse(record())

        assertEquals(listOf("whenReady", "execute:create", "execute:insert"), database.calls)
        assertEquals(listOf("ERROR Plugin version snapshot not recorded: the insert failed (RuntimeException: connection reset)"), log.lines)
    }

    @Test
    fun `a multi-line error message is still one log line`() {
        database.fail["insert"] = RuntimeException("ERROR: duplicate key value\r\n  Detail: Key (server_id)=(dev-main) already exists.\n\n")

        assertFalse(record())

        assertEquals(
            listOf("ERROR Plugin version snapshot not recorded: the insert failed (RuntimeException: ERROR: duplicate key value Detail: Key (server_id)=(dev-main) already exists.)"),
            log.lines,
        )
    }

    @Test
    fun `a database that throws instead of failing its future is handled the same way`() {
        database.throwOnExecute = IllegalStateException("worker rejected")

        assertFalse(record())

        assertEquals(listOf("ERROR Plugin version snapshot not recorded: creating the table failed (IllegalStateException: worker rejected)"), log.lines)
    }

    @Test
    fun `collection failure logs one error and touches no database`() {
        assertFalse(record(collect = { throw IllegalStateException("boom") }))

        assertEquals(emptyList(), database.calls)
        assertEquals(listOf("ERROR Plugin version snapshot not recorded: collecting the snapshot failed (IllegalStateException: boom)"), log.lines)
    }

    @Test
    fun `a scheduler that refuses the task logs one error`() {
        assertFalse(record(executor = { throw RejectedExecutionException("shutting down") }))

        assertEquals(listOf("ERROR Plugin version snapshot not recorded: could not schedule the snapshot (RejectedExecutionException: shutting down)"), log.lines)
    }

    private class RecordingLog : SnapshotLog {
        val lines = mutableListOf<String>()
        override fun info(message: String) { lines += "INFO $message" }
        override fun warning(message: String) { lines += "WARNING $message" }
        override fun error(message: String) { lines += "ERROR $message" }
    }

    private class RecordingDatabase : SnapshotDatabase {
        val calls = mutableListOf<String>()
        var ready: CompletableFuture<*> = CompletableFuture.completedFuture(null)
        val fail = mutableMapOf<String, Throwable>()
        var throwOnExecute: RuntimeException? = null
        var insertParameters: List<Any?>? = null

        override fun whenReady(): CompletableFuture<*> {
            calls += "whenReady"
            return ready
        }

        override fun execute(sql: String, parameters: List<Any?>): CompletableFuture<*> {
            val statement = if (sql.startsWith("create table")) "create" else "insert"
            calls += "execute:$statement"
            throwOnExecute?.let { throw it }
            if (statement == "insert") insertParameters = parameters
            return fail[statement]?.let { CompletableFuture.failedFuture<Int>(it) } ?: CompletableFuture.completedFuture(1)
        }
    }
}
