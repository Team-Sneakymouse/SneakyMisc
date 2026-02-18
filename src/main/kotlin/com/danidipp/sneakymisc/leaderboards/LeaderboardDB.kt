@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")
package com.danidipp.sneakymisc.leaderboards

import com.danidipp.sneakypocketbase.AsyncPocketbaseEvent
import com.danidipp.sneakypocketbase.BaseRecord
import com.danidipp.sneakypocketbase.SneakyPocketbase
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import io.github.agrevster.pocketbaseKotlin.dsl.query.SortFields
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.util.logging.Logger
import java.time.LocalDate

@Serializable
data class LeaderboardRecord(
    @Transient override val recordId: String? = null,
    val leaderboard: String,
    val date: String,
    val account: String,
    val name: String,
    val value: Int
): BaseRecord(recordId)

class LeaderboardDB(private val logger: Logger) {
    val LEADERBOARDS_COLLECTION = "lom2_leaderboards"

    suspend fun fetchRecords(leaderboardName: String, date: LocalDate): List<LeaderboardRecord> {
        val pb = SneakyPocketbase.getInstance().pb()
        val dateStr = date.toString()
        return try {
            pb.records.getFullList<LeaderboardRecord>(
                LEADERBOARDS_COLLECTION,
                200,
                SortFields(),
                Filter("leaderboard = '$leaderboardName' && date ~ '$dateStr'")
            )
        } catch (e: Exception) {
            logger.warning("Failed to fetch records for $leaderboardName: ${e.message}")
            emptyList()
        }
    }

    suspend fun upsertRecord(
        leaderboardName: String,
        date: String,
        account: String,
        characterName: String,
        value: Int,
        knownRecordId: String? = null
    ) {
        val pb = SneakyPocketbase.getInstance().pb()

        if (knownRecordId != null) {
            // Update known record
            try {
                val record = LeaderboardRecord(
                    recordId = knownRecordId,
                    leaderboard = leaderboardName,
                    date = date,
                    account = account,
                    name = characterName,
                    value = value
                )
                pb.records.update<LeaderboardRecord>(
                    LEADERBOARDS_COLLECTION,
                    knownRecordId,
                    record.toJson(LeaderboardRecord.serializer())
                )
            } catch (e: Exception) {
                logger.warning("Failed to update leaderboard record for $leaderboardName: ${e.message}")
            }
        } else {
            // Create or Find & Update
            try {
                // Try to find existing record first to avoid duplicates/errors
                // Use ~ for date to be resilient to formatting differences (T vs space, etc.)
                val existing = try {
                    val datePart = if (date.length >= 10) date.substring(0, 10) else date
                    pb.records.getFullList<LeaderboardRecord>(
                        LEADERBOARDS_COLLECTION,
                        1,
                        SortFields(),
                        Filter("leaderboard = '$leaderboardName' && date ~ '$datePart' && account = '$account'")
                    ).firstOrNull()
                } catch (e: Exception) {
                    logger.warning("Error searching for existing leaderboard record: ${e.message}")
                    null
                }

                if (existing != null) {
                    val record = existing.copy(value = value, name = characterName)
                    pb.records.update<LeaderboardRecord>(
                        LEADERBOARDS_COLLECTION,
                        existing.id!!,
                        record.toJson(LeaderboardRecord.serializer())
                    )
                } else {
                    val record = LeaderboardRecord(
                        leaderboard = leaderboardName,
                        date = date,
                        account = account,
                        name = characterName,
                        value = value
                    )
                    pb.records.create<LeaderboardRecord>(
                        LEADERBOARDS_COLLECTION,
                        record.toJson(LeaderboardRecord.serializer())
                    )
                }
            } catch (e: Exception) {
                logger.warning("Failed to upsert leaderboard record for $leaderboardName: ${e.message}")
            }
        }
    }

    fun parseEvent(event: AsyncPocketbaseEvent): LeaderboardRecord? {
        if (event.collectionName != LEADERBOARDS_COLLECTION) return null
        return try {
            event.data.parseRecord<LeaderboardRecord>(Json { ignoreUnknownKeys = true })
        } catch (e: Exception) {
            logger.warning("Error parsing leaderboard event: ${e.message}")
            null
        }
    }
}
