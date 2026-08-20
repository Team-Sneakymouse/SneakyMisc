@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")
package com.danidipp.sneakymisc.leaderboards

import com.danidipp.sneakypocketbase.AsyncPocketbaseEvent
import com.danidipp.sneakypocketbase.PocketbaseProvider
import com.danidipp.sneakymisc.PocketbaseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.logging.Logger
import java.time.LocalDate

@Serializable
data class LeaderboardRecord(
    @SerialName("id") val recordId: String? = null,
    val leaderboard: String,
    val date: String,
    val account: String,
    val name: String,
    val value: Int
)

private val leaderboardRecordJson = Json { ignoreUnknownKeys = true }

internal fun decodeLeaderboardRecord(recordJson: String): LeaderboardRecord =
    leaderboardRecordJson.decodeFromString<LeaderboardRecord>(recordJson)

class LeaderboardDB(private val logger: Logger) {
    val LEADERBOARDS_COLLECTION = "lom2_leaderboards"

    fun fetchRecords(leaderboardName: String, date: LocalDate): List<LeaderboardRecord> {
        val pb = PocketbaseProvider.getApi()
        val dateStr = date.toString()
        return try {
            pb.getFullList(
                LEADERBOARDS_COLLECTION,
                200,
                "",
                "leaderboard = '$leaderboardName' && date ~ '$dateStr'"
            ).join().map(::decodeLeaderboardRecord)
        } catch (e: Exception) {
            logger.warning("Failed to fetch records for $leaderboardName: ${e.message}")
            emptyList()
        }
    }

    fun upsertRecord(
        leaderboardName: String,
        date: String,
        account: String,
        characterName: String,
        value: Int,
        knownRecordId: String? = null
    ) {
        val pb = PocketbaseProvider.getApi()

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
                pb.update(
                    LEADERBOARDS_COLLECTION,
                    knownRecordId,
                    PocketbaseJson.encodeUpdate(record)
                ).join()
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
                    pb.getFullList(
                        LEADERBOARDS_COLLECTION,
                        1,
                        "",
                        "leaderboard = '$leaderboardName' && date ~ '$datePart' && account = '$account'"
                    ).join().firstOrNull()?.let(::decodeLeaderboardRecord)
                } catch (e: Exception) {
                    logger.warning("Error searching for existing leaderboard record: ${e.message}")
                    null
                }

                if (existing != null) {
                    val record = existing.copy(value = value, name = characterName)
                    pb.update(
                        LEADERBOARDS_COLLECTION,
                        existing.recordId!!,
                        PocketbaseJson.encodeUpdate(record)
                    ).join()
                } else {
                    val record = LeaderboardRecord(
                        leaderboard = leaderboardName,
                        date = date,
                        account = account,
                        name = characterName,
                        value = value
                    )
                    pb.create(
                        LEADERBOARDS_COLLECTION,
                        PocketbaseJson.encodeCreate(record)
                    ).join()
                }
            } catch (e: Exception) {
                logger.warning("Failed to upsert leaderboard record for $leaderboardName: ${e.message}")
            }
        }
    }

    fun deleteRecord(
        leaderboardName: String,
        date: String,
        account: String,
        knownRecordId: String? = null
    ) {
        val pb = PocketbaseProvider.getApi()

        val recordId = knownRecordId ?: try {
            val datePart = if (date.length >= 10) date.substring(0, 10) else date
            pb.getFullList(
                LEADERBOARDS_COLLECTION,
                1,
                "",
                "leaderboard = '$leaderboardName' && date ~ '$datePart' && account = '$account'"
            ).join().firstOrNull()?.let(::decodeLeaderboardRecord)?.recordId
        } catch (e: Exception) {
            logger.warning("Error searching for leaderboard record to delete: ${e.message}")
            null
        } ?: return

        try {
            pb.delete(LEADERBOARDS_COLLECTION, recordId).join()
        } catch (e: Exception) {
            logger.warning("Failed to delete leaderboard record for $leaderboardName: ${e.message}")
        }
    }

    fun parseEvent(event: AsyncPocketbaseEvent): LeaderboardRecord? {
        if (event.collectionName != LEADERBOARDS_COLLECTION) return null
        return try {
            decodeLeaderboardRecord(event.recordJson)
        } catch (e: Exception) {
            logger.warning("Error parsing leaderboard event: ${e.message}")
            null
        }
    }
}
