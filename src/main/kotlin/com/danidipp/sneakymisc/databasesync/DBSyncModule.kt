package com.danidipp.sneakymisc.databasesync

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.PocketbaseJson
import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakypocketbase.PocketbaseProvider
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.logging.Logger

class DBSyncModule(val logger: Logger) : SneakyModule() {
    companion object { val deps = listOf<String>("SneakyPocketbase") }
    override val listeners: List<Listener>
        get() {
            val listeners = mutableListOf<Listener>(object : Listener {
                @EventHandler
                fun onPlayerJoin(event: PlayerJoinEvent) {
                    logger.info("Player ${event.player.name} joined, scheduling account sync")
                    Bukkit.getScheduler().runTaskAsynchronously(SneakyMisc.getInstance(), Runnable {
                        logger.info("Running account sync for ${event.player.name}")
                        val pb = PocketbaseProvider.getApi()
                        val player = event.player
                        val uuid = player.uniqueId.toString()
                        val record = runCatching {
                            Json { ignoreUnknownKeys = true }.decodeFromString<AccountRecord>(
                                pb.getOne("lom2_accounts", uuid).join()
                            )
                        }.getOrNull()
                        if (record == null) {
                            logger.info("Creating new account record for ${player.name}")
                            pb.create("lom2_accounts", PocketbaseJson.encodeCreate(AccountRecord(
                                recordId = uuid,
                                name = player.name,
                                owner = "",
                                main = false,
                                dvz = false,
                            ))).join()
                            return@Runnable
                        }
                        if (record.name != player.name) {
                            logger.info("Updating account record for ${player.name}")
                            record.name = player.name
                        }
                        pb.update("lom2_accounts", uuid, PocketbaseJson.encodeUpdate(record)).join()
                    })
                }
            })

            if (Bukkit.getPluginManager().isPluginEnabled("SneakyCharacterManager")) {
                listeners.add(object : Listener {
                    @EventHandler
                    fun onChangeCharacter(event: LoadCharacterEvent) {
                        logger.info("Character ${event.characterName} loaded, scheduling character sync")
                        Bukkit.getScheduler().runTaskAsynchronously(SneakyMisc.getInstance(), Runnable {
                            logger.info("Running character sync for ${event.characterName}")
                            val pb = PocketbaseProvider.getApi()
                            val player = event.player
                            val tags = Json.decodeFromString<Map<String, String>>(event.tags)
                            val record = try {
                                Json { ignoreUnknownKeys = true }.decodeFromString<CharacterRecord>(
                                    pb.getOne("lom2_characters", event.characterUUID).join()
                                )
                            } catch (e: Exception) {
                                logger.warning("Failed to fetch character record for ${event.characterName} (${event.characterUUID}): ${e.message}")
                                null
                            }
                            if (record == null) {
                                logger.info("Creating new character record for ${event.characterName}")
                                try {
                                    pb.create("lom2_characters", PocketbaseJson.encodeCreate(CharacterRecord(
                                        recordId = event.characterUUID,
                                        name = event.characterName,
                                        account = player.uniqueId.toString(),
                                        tags = tags
                                    ))).join()
                                } catch (e: Exception) {
                                    logger.severe("Failed to create character record for ${event.characterName}: ${e.message}")
                                    return@Runnable
                                }
                                return@Runnable
                            }
                            if (record.name != event.characterName || record.tags != tags) {
                                logger.info("Updating character record for ${event.characterName}")
                                record.name = event.characterName
                                record.tags = tags
                                pb.update("lom2_characters", event.characterUUID, PocketbaseJson.encodeUpdate(record)).join()
                            } else {
                                logger.info("Character record for ${event.characterName} is up to date")
                            }
                        })
                    }
                })
            }
            return listeners
        }
}
