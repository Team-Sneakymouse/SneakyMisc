package com.danidipp.sneakymisc.dvzregistrations

import com.danidipp.sneakymisc.SneakyMisc
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakymisc.databasesync.AccountRecord
import com.danidipp.sneakypocketbase.AsyncPocketbaseEvent
import com.danidipp.sneakypocketbase.MSVariableSync
import com.danidipp.sneakypocketbase.PBRunnable
import com.danidipp.sneakypocketbase.SneakyPocketbase
import io.github.agrevster.pocketbaseKotlin.dsl.query.Filter
import io.github.agrevster.pocketbaseKotlin.dsl.query.SortFields
import io.github.agrevster.pocketbaseKotlin.services.RealtimeService
import kotlinx.serialization.json.Json
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import java.util.UUID
import java.util.logging.Logger

class RegistrationModule(logger: Logger): SneakyModule {
    override val commands = listOf<Command>()
    override val listeners = mutableListOf<Listener>()

    init {
        if (Bukkit.getServer().worlds.first().name == "dvz") {
            logger.warning("DvZ server detected, registering pre-login listener for registration checks")
            listeners.add(makePreLoginListener(logger))
        }
    }

    private fun makePreLoginListener(logger: Logger) : Listener{
        return object : Listener {
            val accounts: MutableMap<UUID, AccountRecord> = mutableMapOf()
            val sneakyPB = SneakyPocketbase.getInstance()
            init {
                sneakyPB.onPocketbaseLoaded {
                    logger.info("Pocketbase loaded, subscribing to record")
                    sneakyPB.subscribeAsync("lom2_accounts")
                    Bukkit.getScheduler().runTaskAsynchronously(SneakyMisc.getInstance(), PBRunnable {
                        val accountRecords = sneakyPB.pb().records.getFullList<AccountRecord>("lom2_accounts", 100,
                            SortFields(),
                            Filter("dvz == true")
                        )
                        for (record in accountRecords) {
                            accounts[UUID.fromString(record.id)] = record
                        }
                    })
                }
            }
            @EventHandler
            fun onPocketbaseEvent(event: AsyncPocketbaseEvent){
                if (event.collectionName != "lom2_accounts") return
                val accountRecord = event.data.parseRecord<AccountRecord>(Json { ignoreUnknownKeys = true })
                if (event.action == RealtimeService.RealtimeActionType.DELETE) {
                    accounts.remove(UUID.fromString(accountRecord.id))
                } else {
                    accounts[UUID.fromString(accountRecord.id)] = accountRecord
                }
            }

            @EventHandler
            fun onPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
                val playerUUID = event.uniqueId
                if (Bukkit.getServer().whitelistedPlayers.contains(Bukkit.getOfflinePlayer(playerUUID))) {
                    return // Player is whitelisted, allow login
                }
                val account = accounts[playerUUID]
                if (account != null && account.dvz) {
                    return // Player is registered for dvz, allow login
                }
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    Component.join(JoinConfiguration.newlines(),
                        Component.text("You are not registered for DvZ", NamedTextColor.RED),
                        Component.empty(),
                        Component.text("Please register on Discord or LoM with the command"),
                        Component.text("/register", NamedTextColor.AQUA)
                    )
                )
            }
        }
    }
    private fun makeRegisterCommand(logger: Logger): Command {
        return object : Command("register") {
            init {
                name = "register"
                description = "Register for DvZ"
                usageMessage = "/register"
            }

            override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
                if (args.isNotEmpty() && sender.hasPermission("sneakymisc.dvz.registerothers")) {
                    val playerName = args[0]
                    val player = Bukkit.getPlayer(playerName) ?: run {
                        sender.sendMessage(Component.text("Player $playerName not found", NamedTextColor.RED))
                        return true
                    }
                    register(player)
                    return true
                }
                if (sender !is Player) {
                    sender.sendMessage(Component.text("This command can only be used by players", NamedTextColor.RED))
                    return true
                }
                register(sender)
                return true
            }
        }
    }

    private fun register(player: Player) {

    }
}