package com.danidipp.sneakymisc.attributes

import com.danidipp.sneakymisc.SneakyModule
import com.mojang.brigadier.Command
import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.CommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import java.util.logging.Logger

class AttributesModule(private val logger: Logger) : SneakyModule() {
    override fun registerBrigadierCommands(commands: Commands) {
        for (label in listOf("attribute", "minecraft:attribute")) {
            if (!extendRemove(commands.dispatcher.root, label, Command(::removeAll))) {
                logger.warning("Cannot extend /$label: vanilla command was not found")
            }
        }
    }

    private fun removeAll(context: CommandContext<CommandSourceStack>): Int {
        // Vanilla arguments contain internal server types. Reparse their exact ranges
        // through Paper's API, retaining selectors and the source supplied by /execute.
        val target = resolveTarget(
            ArgumentTypes.entity().parse(StringReader(argumentText(context, "target"))), context.source
        )
        val key = NamespacedKey.fromString(argumentText(context, "attribute"))
            ?: fail("Invalid attribute")
        val attribute = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).get(key)
            ?: fail("Unknown attribute: $key")
        val instance = target.getAttribute(attribute)
            ?: fail("${target.name} does not have attribute $key")
        val modifiers = instance.modifiers.toList()
        modifiers.forEach(instance::removeModifier)
        context.source.sender.sendMessage("Removed ${modifiers.size} modifiers of $key from ${target.name}")
        return modifiers.size
    }

    internal fun resolveTarget(resolver: EntitySelectorArgumentResolver, source: CommandSourceStack): LivingEntity =
        // ArgumentTypes.entity() resolves to a list containing exactly one entity.
        resolver.resolve(source).single() as? LivingEntity ?: fail("The target must be a living entity")

    private fun fail(message: String): Nothing =
        throw SimpleCommandExceptionType(LiteralMessage(message)).create()

    companion object {
        internal fun <S> extendRemove(root: CommandNode<S>, label: String, command: Command<S>): Boolean {
            if (root.getChild(label) == null) return false
            // Paper exposes vanilla roots as ShadowBrigNode, whose children cannot
            // be read or changed. Adding at the dispatcher root converts this branch
            // and merges it into the real vanilla tree. Matching nodes retain their
            // original argument types and requirements; remove gains a literal * child.
            // The placeholder arguments are never used on the supported vanilla path.
            // Deny new nodes if that path changes, rather than create an unguarded command.
            root.addChild(literal<S>(label).requires { false }.then(
                argument<S, String>("target", StringArgumentType.word()).requires { false }.then(
                    argument<S, String>("attribute", StringArgumentType.word()).requires { false }.then(
                        literal<S>("modifier").requires { false }.then(
                            literal<S>("remove").requires { false }.then(
                                literal<S>("*").executes(command)
                            )
                        )
                    )
                )
            ).build())
            return true
        }

        internal fun <S> argumentText(context: CommandContext<S>, name: String): String =
            context.nodes.last { it.node.name == name }.range.get(context.input)
    }
}
