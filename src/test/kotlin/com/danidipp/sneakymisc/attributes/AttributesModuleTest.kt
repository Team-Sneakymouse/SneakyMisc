package com.danidipp.sneakymisc.attributes

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.exceptions.CommandSyntaxException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver
import org.bukkit.entity.Player
import org.bukkit.entity.Item
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.logging.Logger
import kotlin.test.assertSame

class AttributesModuleTest {
    @Test
    fun `single entity resolver accepts the player inside its result list`() {
        val source = mock(CommandSourceStack::class.java)
        val resolver = mock(EntitySelectorArgumentResolver::class.java)
        val player = mock(Player::class.java)
        `when`(resolver.resolve(source)).thenReturn(listOf(player))
        assertSame(player, AttributesModule(Logger.getAnonymousLogger()).resolveTarget(resolver, source))
    }

    @Test
    fun `single entity resolver rejects a nonliving target`() {
        val source = mock(CommandSourceStack::class.java)
        val resolver = mock(EntitySelectorArgumentResolver::class.java)
        `when`(resolver.resolve(source)).thenReturn(listOf(mock(Item::class.java)))
        val error = assertFailsWith<CommandSyntaxException> {
            AttributesModule(Logger.getAnonymousLogger()).resolveTarget(resolver, source)
        }
        assertEquals("The target must be a living entity", error.rawMessage.string)
    }

    @Test
    fun `registration accepts Paper shadow nodes without traversing their children`() {
        val dispatcher = CommandDispatcher<Boolean>()
        dispatcher.register(literal<Boolean>("attribute").requires { it }.then(
            argument<Boolean, String>("target", StringArgumentType.word()).then(
                argument<Boolean, String>("attribute", StringArgumentType.word()).then(
                    literal<Boolean>("modifier").then(literal<Boolean>("remove").then(
                        argument<Boolean, String>("id", StringArgumentType.word()).executes { 7 }
                    ))
                )
            )
        ))
        val shadow = object : LiteralCommandNode<Boolean>("attribute", null, { false }, null, null, false) {
            override fun getChild(name: String): CommandNode<Boolean>? =
                throw UnsupportedOperationException("Cannot retrieve children from this node.")
            override fun getChildren(): Collection<CommandNode<Boolean>> =
                throw UnsupportedOperationException("Cannot retrieve children from this node.")
            override fun addChild(node: CommandNode<Boolean>) =
                throw UnsupportedOperationException("Cannot modify children for this node.")
        }
        // Model Paper's API mirror: reads return a shadow; root additions merge
        // into the actual dispatcher. Vanilla child access must never be attempted.
        val mirror = object : RootCommandNode<Boolean>() {
            override fun getChild(name: String): CommandNode<Boolean>? =
                if (name == "attribute") shadow else null
            override fun addChild(node: CommandNode<Boolean>) = dispatcher.root.addChild(node)
        }
        assertTrue(AttributesModule.extendRemove(mirror, "attribute", Command { 3 }))
        assertEquals(3, dispatcher.execute("attribute Alex speed modifier remove *", true))
        assertEquals(7, dispatcher.execute("attribute Alex speed modifier remove bonus", true))
        assertFailsWith<CommandSyntaxException> {
            dispatcher.execute("attribute Alex speed modifier remove *", false)
        }
    }

    @Test
    fun `extension preserves explicit modifier removal and permission checks`() {
        val dispatcher = CommandDispatcher<Boolean>()
        dispatcher.register(literal<Boolean>("attribute").requires { it }.then(
            argument<Boolean, String>("target", StringArgumentType.word()).then(
                argument<Boolean, String>("attribute", StringArgumentType.word()).then(
                    literal<Boolean>("modifier").then(literal<Boolean>("remove").then(
                        argument<Boolean, String>("id", StringArgumentType.word()).executes { 7 }
                    ))
                )
            )
        ))
        repeat(2) {
            assertTrue(AttributesModule.extendRemove(dispatcher.root, "attribute", Command { 3 }))
        }
        assertFailsWith<CommandSyntaxException> {
            dispatcher.execute("attribute Alex speed modifier remove", true)
        }
        assertEquals(3, dispatcher.execute("attribute Alex speed modifier remove *", true))
        assertEquals(7, dispatcher.execute("attribute Alex speed modifier remove bonus", true))
        assertFailsWith<CommandSyntaxException> {
            dispatcher.execute("attribute Alex speed modifier remove *", false)
        }
    }

    @Test
    fun `argument ranges work through execute redirects and preserve selector spaces`() {
        val dispatcher = CommandDispatcher<Boolean>()
        dispatcher.register(literal<Boolean>("attribute").then(
            argument<Boolean, String>("target", StringArgumentType.string()).then(
                argument<Boolean, String>("attribute", StringArgumentType.word()).executes {
                    assertEquals("\"@e[name=Some Entity,limit=1]\"", AttributesModule.argumentText(it, "target"))
                    assertEquals("speed", AttributesModule.argumentText(it, "attribute"))
                    1
                }
            )
        ))
        dispatcher.register(literal<Boolean>("execute").then(literal<Boolean>("run").redirect(dispatcher.root)))
        assertEquals(1, dispatcher.execute("execute run attribute \"@e[name=Some Entity,limit=1]\" speed", true))
    }
}
