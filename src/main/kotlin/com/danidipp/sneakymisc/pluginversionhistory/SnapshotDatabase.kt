package com.danidipp.sneakymisc.pluginversionhistory

import com.sneakydb.SneakyDBApi
import com.sneakydb.SneakyDBProvider
import java.util.concurrent.CompletableFuture

/** The part of SneakyDB the recorder uses. Keeps SneakyDB types out of classes that load when SneakyDB is absent. */
interface SnapshotDatabase {
    fun whenReady(): CompletableFuture<*>
    fun execute(sql: String, parameters: List<Any?>): CompletableFuture<*>
}

/**
 * The only class that links against SneakyDB. Load it only after checking that the SneakyDB plugin is present:
 * without it, `com.sneakydb` classes are not loadable. SneakyDB's API is compile-only and never bundled.
 */
class SneakyDBSnapshotDatabase private constructor(private val api: SneakyDBApi) : SnapshotDatabase {
    override fun whenReady(): CompletableFuture<*> = api.whenReady()
    override fun execute(sql: String, parameters: List<Any?>): CompletableFuture<*> = api.execute(sql, parameters)

    companion object {
        @Volatile
        private var migrations: CompletableFuture<Int>? = null

        /** Throws `IllegalStateException` when SneakyDB is not loaded. */
        @JvmStatic
        fun connect(): SnapshotDatabase = SneakyDBSnapshotDatabase(SneakyDBProvider.getApi())

        /** Call once per enable while SneakyDB is present; returns the same future on later calls. */
        @JvmStatic
        fun register(): CompletableFuture<*>? = try {
            migrations ?: SneakyDBProvider.getApi()
                .migrate(PluginVersionHistorySql.IDENTIFIER, PluginVersionHistorySql::class.java)
                .also { migrations = it }
        } catch (failure: RuntimeException) {
            CompletableFuture.failedFuture<Int>(failure).also { migrations = it }
        }
    }
}
