package com.danidipp.sneakymisc.databasesync

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakypocketbase.PBRunnable
import com.danidipp.sneakypocketbase.SneakyPocketbase
import kotlinx.serialization.json.Json
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.logging.Logger

class DBSyncModule(val logger: Logger) : SneakyModule {
    override val commands: List<Command> = listOf()
    override val listeners: List<Listener>
        get() {
            if (!Bukkit.getPluginManager().isPluginEnabled("SneakyPocketbase")) {
                return emptyList()
            }

            val listeners = mutableListOf<Listener>(object : Listener {
                @EventHandler
                fun onPlayerJoin(event: PlayerJoinEvent) {
                    logger.info("Player ${event.player.name} joined, scheduling account sync")
                    Bukkit.getScheduler().runTaskAsynchronously(SneakyMisc.getInstance(), PBRunnable {
                        logger.info("Running account sync for ${event.player.name}")
                        val pb = SneakyPocketbase.getInstance()
                        val player = event.player
                        val uuid = player.uniqueId.toString()
                        val record = runCatching { pb.pb().records.getOne<AccountRecord>("lom2_accounts", uuid) }.getOrNull()
                        if (record == null) {
                            logger.info("Creating new account record for ${player.name}")
                            pb.pb().records.create<AccountRecord>("lom2_accounts", AccountRecord(
                                recordId = uuid,
                                name = player.name,
                                owner = "",
                                main = false,
                                dvz = false,
                            ).toJson(AccountRecord.serializer()))
                            return@PBRunnable
                        }
                        if (record.name != player.name) {
                            logger.info("Updating account record for ${player.name}")
                            record.name = player.name
                        }
                        pb.pb().records.update<AccountRecord>("lom2_accounts", uuid, record.toJson(AccountRecord.serializer()))
                    })
                }
            })

            if (Bukkit.getPluginManager().isPluginEnabled("SneakyCharacterManager")) {
                listeners.add(object : Listener {
                    @EventHandler
                    fun onChangeCharacter(event: LoadCharacterEvent) {
                        logger.info("Character ${event.characterName} loaded, scheduling character sync")
                        Bukkit.getScheduler().runTaskAsynchronously(SneakyMisc.getInstance(), PBRunnable {
                            logger.info("Running character sync for ${event.characterName}")
                            val pb = SneakyPocketbase.getInstance()
                            val player = event.player
                            val tags = Json.decodeFromString<Map<String, String>>(event.tags)
                            val record = try {
                                pb.pb().records.getOne<CharacterRecord>("lom2_characters", event.characterUUID)
                            } catch (e: Exception) {
                                logger.warning("Failed to fetch character record for ${event.characterName} (${event.characterUUID}): ${e.message}")
                                null
                            }
                            if (record == null) {
                                logger.info("Creating new character record for ${event.characterName}")
                                try {
                                    pb.pb().records.create<CharacterRecord>("lom2_characters", CharacterRecord(
                                        recordId = event.characterUUID,
                                        name = event.characterName,
                                        account = player.uniqueId.toString(),
                                        tags = tags
                                    ).toJson(CharacterRecord.serializer()))
                                } catch (e: Exception) {
                                    logger.severe("Failed to create character record for ${event.characterName}: ${e.message}")
                                    return@PBRunnable
                                }
                                return@PBRunnable
                            }
                            if (record.name != event.characterName || record.tags != tags) {
                                logger.info("Updating character record for ${event.characterName}")
                                record.name = event.characterName
                                record.tags = tags
                                pb.pb().records.update<CharacterRecord>("lom2_characters", event.characterUUID, record.toJson(CharacterRecord.serializer()))
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