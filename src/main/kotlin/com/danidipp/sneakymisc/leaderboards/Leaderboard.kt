package com.danidipp.sneakymisc.leaderboards

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakypocketbase.PBRunnable
import com.danidipp.sneakypocketbase.AsyncPocketbaseEvent
import com.nisovin.magicspells.variables.Variable
import com.nisovin.magicspells.variables.variabletypes.GlobalStringVariable
import io.github.agrevster.pocketbaseKotlin.services.RealtimeService
import io.ktor.util.collections.ConcurrentMap
import net.sneakycharactermanager.paper.handlers.character.Character
import org.bukkit.Bukkit
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

data class LeaderboardEntry(
    val recordId: String,
    val date: String,
    val playerUUID: UUID,
    val characterUUID: UUID,
    val characterName: String,
    val value: Int,
)

class Leaderboard(
    val plugin: SneakyMisc,
    val db: LeaderboardDB,
    val name: String,
    val type: LeaderboardType,
    val valueVariable: Variable,
    val displayVariables: List<GlobalStringVariable>
) {
    val userCache = ConcurrentMap<UUID, LeaderboardEntry>()
    val lastSentValues = mutableMapOf<UUID, Int>()

    fun updateScore(character: Character, value: Double, leaderboardDate: String) {
        val playerUUID = character.player.uniqueId
        val characterUUID = UUID.fromString(character.characterUUID)
        val cacheKey = if (type == LeaderboardType.PLAYER) playerUUID else characterUUID
        val intValue = value.roundToInt()

        // Debounce
        val lastSent = lastSentValues[cacheKey]
        if (lastSent == null) {
             val cachedEntry = userCache[cacheKey]
             if (cachedEntry != null && cachedEntry.value == intValue) {
                 lastSentValues[cacheKey] = intValue
                 return
             }
        } else if (lastSent == intValue) return

        lastSentValues[cacheKey] = intValue

        if (intValue <= 0) return

        Bukkit.getScheduler().runTaskAsynchronously(plugin, PBRunnable {
            val cachedEntry = userCache[cacheKey]
            val recordId = if (cachedEntry?.date?.startsWith(leaderboardDate.substring(0, 10)) == true) {
                cachedEntry.recordId
            } else null

            db.upsertRecord(
                leaderboardName = name,
                date = leaderboardDate,
                account = cacheKey.toString(),
                characterName = character.name,
                value = intValue,
                knownRecordId = recordId
            )
        })
    }

    fun handleRealtimeUpdate(record: LeaderboardRecord, action: RealtimeService.RealtimeActionType) {
        if (record.leaderboard != name) {
            plugin.logger.warning("Received update for leaderboard '${record.leaderboard}' but expected '$name'")
            return
        }

        // only react to events from today's leaderboard (UTC-7)
        val currentServerDate = LocalDate.now(ZoneId.of("UTC-07:00")).toString()
        if (!record.date.startsWith(currentServerDate)) return

        val uuid = runCatching { UUID.fromString(record.account) }.getOrNull() ?: run {
            plugin.logger.severe("Invalid UUID in leaderboard record: ${record.account}")
            return
        }

        if (action == RealtimeService.RealtimeActionType.DELETE) {
            userCache.remove(uuid)
        } else {
            userCache[uuid] = LeaderboardEntry(
                recordId = record.id ?: "",
                playerUUID = uuid, // Placeholder, logically handled by cacheKey concept but entries need structure
                characterUUID = uuid,
                characterName = record.name,
                value = record.value,
                date = record.date
            )
        }
    }

    fun updateDisplays() {
        val sortedEntries = userCache.values.filter { it.value > 0 }.sortedByDescending { it.value }.take(displayVariables.size)
        for (i in displayVariables.indices) {
            val variable = displayVariables[i]
            val entry = sortedEntries.getOrNull(i)
            if (entry == null) {
                variable.parseAndSet("null", "")
                continue
            }
            val formattedValue = String.format("%,d", entry.value.toLong())
            plugin.logger.warning("Updating display variable '$name${i+1}' with value '$formattedValue' for character '${entry.characterName}'")
            variable.parseAndSet("null", "${entry.characterName} $formattedValue")
        }
    }

    fun loadInitialData() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, PBRunnable {
            userCache.clear()
            val date = LocalDate.now(ZoneId.of("UTC-07:00"))
            val records = db.fetchRecords(name, date)
            plugin.logger.info("Loaded ${records.size} records for leaderboard '$name'")
            
            for (record in records) {
                val uuid = runCatching { UUID.fromString(record.account) }.getOrNull() ?: run {
                    plugin.logger.severe("Invalid UUID in leaderboard record: ${record.account}")
                    continue
                }
                userCache[uuid] = LeaderboardEntry(
                    recordId = record.id!!,
                    playerUUID = uuid,
                    characterUUID = uuid,
                    characterName = record.name,
                    value = record.value,
                    date = record.date
                )
            }
        })
    }
}
