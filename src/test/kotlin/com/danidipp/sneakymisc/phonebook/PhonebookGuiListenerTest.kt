package com.danidipp.sneakymisc.phonebook

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.logging.Logger
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertTrue
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import sun.misc.Unsafe

class PhonebookGuiListenerTest {
    @Test
    fun `browsing inventory clicks are cancelled before routing`() {
        val view = browsingView()
        val event = InventoryClickEvent(
            view,
            InventoryType.SlotType.CONTAINER,
            0,
            ClickType.SWAP_OFFHAND,
            InventoryAction.NOTHING,
        )

        listener().onInventoryClick(event)

        assertTrue(event.isCancelled)
    }

    @Test
    fun `browsing inventory drags are cancelled`() {
        val view = browsingView()
        val event = InventoryDragEvent(
            view,
            blankItemStack(),
            blankItemStack(),
            false,
            emptyMap(),
        )

        listener().onInventoryDrag(event)

        assertTrue(event.isCancelled)
    }

    private fun listener(): PhonebookGuiListener {
        val storage = PhonebookStorage(createTempFile(prefix = "phonebook-listener", suffix = ".yml"), Logger.getAnonymousLogger())
        val directory = FakePhonebookDirectory()
        val browser = PhonebookBrowser(
            resolver = PhonebookResolver(directory),
            renderer = PhonebookBrowserRenderer(),
            renderTokenProvider = { 1L },
        )
        val inventoryFactory = PhonebookInventoryFactory(plugin())
        return PhonebookGuiListener(
            guiActions = PhonebookGuiActions(
                phonebooks = storage,
                activeCharacters = directory,
                directory = directory,
                callRouter = PhonebookCallRouter(directory, directory, NoOpPhonebookCaller),
                browser = browser,
            ),
            inventoryFactory = inventoryFactory,
        )
    }

    private fun browsingView(): InventoryView {
        val holder = PhonebookBrowsingHolder(
            PhonebookBrowserState(
                viewerAccountId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ownerCharacterId = UUID.fromString("10000000-0000-0000-0000-000000000000"),
                page = 0,
                renderToken = 1,
            )
        )
        val topInventory = inventory(holder)
        val bottomInventory = inventory(null)

        return proxy { method, args ->
            when (method.name) {
                "getTopInventory" -> topInventory
                "getBottomInventory" -> bottomInventory
                "getInventory" -> if ((args?.firstOrNull() as? Int ?: 0) < 54) topInventory else bottomInventory
                "getType" -> InventoryType.CHEST
                "getSlotType" -> InventoryType.SlotType.CONTAINER
                "countSlots" -> 90
                else -> defaultReturn(method)
            }
        }
    }

    private fun inventory(holder: PhonebookBrowsingHolder?): Inventory =
        proxy { method, _ ->
            when (method.name) {
                "getSize" -> 54
                "getType" -> InventoryType.CHEST
                "getHolder" -> holder
                "getItem" -> null
                else -> defaultReturn(method)
            }
        }

    private fun plugin(): Plugin =
        proxy { method, _ ->
            when (method.name) {
                "getName" -> "SneakyMisc"
                else -> defaultReturn(method)
            }
        }

    private fun blankItemStack(): ItemStack {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return (field.get(null) as Unsafe).allocateInstance(ItemStack::class.java) as ItemStack
    }

    private inline fun <reified T> proxy(noinline answer: (Method, Array<Any?>?) -> Any?): T =
        Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, args -> answer(method, args) } as T

    private fun defaultReturn(method: Method): Any? =
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Void.TYPE -> null
            String::class.java -> ""
            else -> null
        }

    private object NoOpPhonebookCaller : PhonebookCaller {
        override fun startOrReuseCall(callerAccountId: UUID, targetAccountId: UUID, targetDisplayName: String) = Unit
    }

    private class FakePhonebookDirectory : PhonebookDirectory, PhonebookActiveCharacters {
        override fun character(accountId: UUID, characterId: UUID): PhonebookCharacter? = null

        override fun isOnline(accountId: UUID): Boolean = false

        override fun activeCharacter(accountId: UUID): UUID? = null
    }
}
