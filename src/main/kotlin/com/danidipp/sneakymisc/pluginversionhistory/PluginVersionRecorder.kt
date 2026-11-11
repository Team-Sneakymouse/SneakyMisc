package com.danidipp.sneakymisc.pluginversionhistory

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/** Platform logger levels the recorder needs; adapters wrap Paper's JUL logger or Velocity's SLF4J logger. */
interface SnapshotLog {
    fun info(message: String)
    fun warning(message: String)
    fun error(message: String)
}

/**
 * Writes one plugin version snapshot per startup: SneakyDB readiness, then `create table if not exists`, then a
 * single multi-row `INSERT`. Every way of not recording logs exactly one line and records nothing; there is no retry
 * and no spool, because the platform's own startup log already lists what it loaded. Never throws to the caller.
 */
class PluginVersionRecorder(private val log: SnapshotLog, private val async: Executor) {

    /**
     * Call on the platform's startup thread once every plugin is loaded. Only cheap checks run there; [collect]
     * (file scanning) and all database work run on [async] or SneakyDB's workers.
     *
     * @param identity null when the server identity is missing.
     * @param database null when SneakyDB is not installed. It may throw when SneakyDB is installed but unreachable.
     * @return completes with whether the snapshot was recorded; never exceptionally.
     */
    fun record(
        identity: ServerIdentity?,
        database: () -> SnapshotDatabase?,
        collect: (ServerIdentity) -> PluginVersionSnapshot,
    ): CompletableFuture<Boolean> {
        if (identity == null) {
            log.warning("$NOT_RECORDED: the server identity is missing (P_SERVER_NAME is not set or unusable)")
            return CompletableFuture.completedFuture(false)
        }
        val db = try {
            database()
        } catch (failure: Throwable) {
            log.error("$NOT_RECORDED: SneakyDB is unavailable (${describe(failure)})")
            return CompletableFuture.completedFuture(false)
        }
        if (db == null) {
            log.info("$NOT_RECORDED: SneakyDB is not installed")
            return CompletableFuture.completedFuture(false)
        }

        return try {
            CompletableFuture.supplyAsync({ prepare(identity, collect) }, async)
                .thenCompose { (snapshot, insert) ->
                    step("SneakyDB is not ready") { db.whenReady() }
                        .thenCompose { step("creating the table failed") { db.execute(PluginVersionHistorySql.CREATE_TABLE, emptyList()) } }
                        .thenCompose { step("the insert failed") { db.execute(insert.sql, insert.parameters) } }
                        .thenApply { snapshot }
                }
                .handle { snapshot, failure ->
                    if (failure == null) {
                        log.info(
                            "Recorded plugin version snapshot for ${snapshot.server.id}: " +
                                "${snapshot.count(SnapshotRowKind.PLUGIN)} plugins, ${snapshot.count(SnapshotRowKind.MOD)} mods, " +
                                "${snapshot.count(SnapshotRowKind.FAILED)} failed JARs, " +
                                "${snapshot.count(SnapshotRowKind.UNAPPLIED)} unapplied files"
                        )
                        true
                    } else {
                        val cause = unwrap(failure)
                        if (cause is StepFailure) log.error("$NOT_RECORDED: ${cause.stage} (${describe(cause.cause!!)})")
                        else log.error("$NOT_RECORDED: ${describe(cause)}")
                        false
                    }
                }
        } catch (rejected: RuntimeException) {
            // The platform scheduler refused the task, e.g. because the server is already shutting down.
            log.error("$NOT_RECORDED: could not schedule the snapshot (${describe(rejected)})")
            CompletableFuture.completedFuture(false)
        }
    }

    private fun prepare(
        identity: ServerIdentity,
        collect: (ServerIdentity) -> PluginVersionSnapshot,
    ): Pair<PluginVersionSnapshot, PluginVersionHistorySql.Statement> = try {
        val snapshot = collect(identity).let { it.copy(rows = SnapshotRows.distinct(it.rows)) }
        snapshot to PluginVersionHistorySql.insert(snapshot)
    } catch (failure: Throwable) {
        throw StepFailure("collecting the snapshot failed", failure)
    }

    private fun step(stage: String, action: () -> CompletableFuture<*>): CompletableFuture<Unit> {
        val future = try {
            action()
        } catch (failure: Throwable) {
            CompletableFuture.failedFuture<Any?>(failure)
        }
        return future.handle { _, failure -> if (failure != null) throw StepFailure(stage, unwrap(failure)) }
    }

    private class StepFailure(val stage: String, cause: Throwable) : RuntimeException(stage, cause)

    private companion object {
        const val NOT_RECORDED = "Plugin version snapshot not recorded"

        fun unwrap(failure: Throwable): Throwable {
            var current = failure
            while ((current is CompletionException || current is ExecutionException) && current.cause != null) current = current.cause!!
            return current
        }

        /** One line whatever the message holds: PostgreSQL errors carry a `Detail:` part on a line of its own. */
        fun describe(failure: Throwable): String {
            val message = failure.message.orEmpty().lineSequence().map(String::trim).filter(String::isNotEmpty).joinToString(" ")
            return if (message.isEmpty()) failure.javaClass.simpleName else "${failure.javaClass.simpleName}: $message"
        }
    }
}
