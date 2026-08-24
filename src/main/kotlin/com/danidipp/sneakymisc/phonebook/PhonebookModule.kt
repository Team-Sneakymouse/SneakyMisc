package com.danidipp.sneakymisc.phonebook

import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakymisc.SneakyMiscCommand
import org.bukkit.plugin.java.JavaPlugin

class PhonebookModule(private val plugin: JavaPlugin) : SneakyModule() {
    companion object {
        val deps = listOf("SneakyCharacterManager", "SneakyCellPhones")
    }
    private val storage = PhonebookStorage(
        configPath = plugin.dataPath.resolve("phonebooks.yml"),
        logger = plugin.logger,
    )
    private val directory = BukkitPhonebookDirectory()
    private val resolver = PhonebookResolver(directory)
    private val browser = PhonebookBrowser(
        resolver = resolver,
        renderer = PhonebookBrowserRenderer(),
    )
    private val inventoryFactory = PhonebookInventoryFactory(plugin)
    private val exchangeInventoryFactory = PhonebookExchangeInventoryFactory(plugin)
    private val exchangeActions = PhonebookExchangeActions(
        phonebooks = storage,
        activeCharacters = directory,
        directory = directory,
    )
    private val exchangeController = BukkitPhonebookExchangeController(
        plugin = plugin,
        actions = exchangeActions,
        inventoryFactory = exchangeInventoryFactory,
    )
    private val guiActions = PhonebookGuiActions(
        phonebooks = storage,
        activeCharacters = directory,
        directory = directory,
        caller = SneakyCellPhonesCaller(),
        browser = browser,
    )
    private val accountActions = PhonebookAccountActions(
        phonebooks = storage,
        activeCharacters = directory,
        browser = browser,
    )
    private val debugInspector = PhonebookDebugInspector(
        phonebooks = storage,
        activeCharacters = directory,
        directory = directory,
    )
    private val debugCommand = PhonebookDebugCommandAdapter(
        inspector = debugInspector,
        renderer = PhonebookDebugRenderer(),
        targets = BukkitPhonebookDebugTargetResolver(),
    )

    override val commands = listOf(
        SneakyMiscCommand(PhonebookCommand(accountActions, inventoryFactory, debugCommand).build(), "Open the active Character's Phonebook")
    )

    override val listeners = listOf(
        PhonebookGuiListener(guiActions, inventoryFactory, exchangeController),
        PhonebookExchangeListener(exchangeController, exchangeInventoryFactory),
    )

    init {
        plugin.saveResource("messages/phonebook.txt", false)
        PhonebookTranslations.registerDefaults(plugin.dataPath.resolve("messages/phonebook.txt"))
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            PhonebookPlaceholders(storage, directory).register()
        }
    }
}
