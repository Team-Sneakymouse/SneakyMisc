package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent

data class PhonebookDebugCommandActor(
    val accountId: UUID?,
    val permitted: Boolean,
)

sealed interface PhonebookDebugCommandResult {
    data class Sent(
        val model: PhonebookDebugModel,
        val components: List<Component>,
    ) : PhonebookDebugCommandResult
    data object NoPermission : PhonebookDebugCommandResult
    data object ConsoleRequiresTarget : PhonebookDebugCommandResult
    data class TargetNotOnline(val targetName: String) : PhonebookDebugCommandResult
}

interface PhonebookDebugTargetResolver {
    fun onlineAccount(name: String): UUID?
}

class PhonebookDebugCommandAdapter(
    private val inspector: PhonebookDebugInspection,
    private val renderer: PhonebookDebugRenderer,
    private val targets: PhonebookDebugTargetResolver,
) {
    fun debugSelf(actor: PhonebookDebugCommandActor): PhonebookDebugCommandResult {
        if (!actor.permitted) return PhonebookDebugCommandResult.NoPermission
        val accountId = actor.accountId ?: return PhonebookDebugCommandResult.ConsoleRequiresTarget
        return send(inspector.inspect(accountId))
    }

    fun debugTarget(actor: PhonebookDebugCommandActor, targetName: String): PhonebookDebugCommandResult {
        if (!actor.permitted) return PhonebookDebugCommandResult.NoPermission
        val accountId = targets.onlineAccount(targetName)
            ?: return PhonebookDebugCommandResult.TargetNotOnline(targetName)
        return send(inspector.inspect(accountId))
    }

    private fun send(model: PhonebookDebugModel): PhonebookDebugCommandResult =
        PhonebookDebugCommandResult.Sent(model, renderer.render(model))
}

open class PhonebookDebugRenderer {
    open fun render(model: PhonebookDebugModel): List<Component> =
        buildList {
            add(line(Component.text("Phonebook debug for Account "), PhonebookDebugComponents.uuid(model.accountId)))
            add(
                line(
                    Component.text("Active Character: "),
                    model.activeCharacterId?.let(PhonebookDebugComponents::uuid) ?: Component.text("none"),
                )
            )
            add(Component.text("Characters: ${model.characters.size}"))

            for (summary in model.characters) {
                add(
                    line(
                        Component.text("Character: ${summary.character.displayName} "),
                        PhonebookDebugComponents.uuid(summary.character.characterId),
                    )
                )
                add(line(Component.text("Account: "), PhonebookDebugComponents.uuid(summary.character.accountId)))
                add(
                    Component.text(
                        "active=${summary.active} listed=${summary.listed} " +
                            "stored=${summary.storedContactCount} visible=${summary.visibleContactCount} " +
                            "missing=${summary.missingContactCount}"
                    )
                )

                renderContactExamples(summary.contactExamples).forEach(::add)
                renderMissingContactExamples(summary.missingContactExamples).forEach(::add)
            }

            if (model.malformedPersistedContacts.totalCount > 0) {
                add(Component.text("Malformed persisted contacts: ${model.malformedPersistedContacts.totalCount}"))
                for (entry in model.malformedPersistedContacts.examples) {
                    add(Component.text("${entry.path}: ${entry.message}"))
                }
                addOmittedLine("malformed contact", model.malformedPersistedContacts)?.let(::add)
            }
        }

    private fun renderContactExamples(page: PhonebookDebugExamplePage<PhonebookDebugContactExample>): List<Component> =
        buildList {
            for (contact in page.examples) {
                add(
                    line(
                        Component.text("Contact: ${contact.displayName ?: "(missing Character)"} "),
                        PhonebookDebugComponents.uuid(contact.characterId),
                        Component.text(" Account "),
                        PhonebookDebugComponents.uuid(contact.accountId),
                        Component.text(
                            " listed=${contact.listed} online=${contact.online} " +
                                "visible=${contact.visible} missing=${contact.missingCharacter}"
                        ),
                    )
                )
            }
            addOmittedLine("contact", page)?.let(::add)
        }

    private fun renderMissingContactExamples(page: PhonebookDebugExamplePage<PhonebookDebugMissingContact>): List<Component> =
        buildList {
            for (missing in page.examples) {
                add(
                    line(
                        Component.text("Missing Character: "),
                        PhonebookDebugComponents.uuid(missing.characterId),
                        Component.text(" Account "),
                        PhonebookDebugComponents.uuid(missing.accountId),
                    )
                )
            }
            addOmittedLine("missing contact", page)?.let(::add)
        }

    private fun addOmittedLine(label: String, page: PhonebookDebugExamplePage<*>): Component? =
        if (page.omittedCount > 0) Component.text("${page.omittedCount} more $label examples omitted") else null

    private fun line(vararg parts: Component): Component =
        parts.fold(Component.empty()) { component, part -> component.append(part) }
}

object PhonebookDebugComponents {
    fun uuid(uuid: UUID): Component =
        Component.text(uuid.toString())
            .clickEvent(ClickEvent.copyToClipboard(uuid.toString()))
}
