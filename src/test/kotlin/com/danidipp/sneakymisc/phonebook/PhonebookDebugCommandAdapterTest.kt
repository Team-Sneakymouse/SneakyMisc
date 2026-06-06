package com.danidipp.sneakymisc.phonebook

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PhonebookDebugCommandAdapterTest {
    @Test
    fun `debug self stops at the debug permission gate without inspecting`() {
        val inspector = RecordingDebugInspection()
        val adapter = PhonebookDebugCommandAdapter(
            inspector = inspector,
            renderer = PhonebookDebugRenderer(),
            targets = MapPhonebookDebugTargets(emptyMap()),
        )

        val result = adapter.debugSelf(PhonebookDebugCommandActor(accountId = uuid(1), permitted = false))

        assertEquals(PhonebookDebugCommandResult.NoPermission, result)
        assertEquals(emptyList(), inspector.inspectedAccounts)
    }

    @Test
    fun `debug self inspects the sender account and returns rendered components`() {
        val account = uuid(1)
        val inspector = RecordingDebugInspection()
        val renderer = RecordingDebugRenderer()
        val adapter = PhonebookDebugCommandAdapter(
            inspector = inspector,
            renderer = renderer,
            targets = MapPhonebookDebugTargets(emptyMap()),
        )

        val result = adapter.debugSelf(PhonebookDebugCommandActor(accountId = account, permitted = true))

        assertEquals(listOf(account), inspector.inspectedAccounts)
        assertEquals(
            PhonebookDebugCommandResult.Sent(
                model = PhonebookDebugModel(account, activeCharacterId = null, characters = emptyList()),
                components = renderer.renderedComponents,
            ),
            result,
        )
    }

    @Test
    fun `debug without a target is rejected for console`() {
        val inspector = RecordingDebugInspection()
        val adapter = PhonebookDebugCommandAdapter(
            inspector = inspector,
            renderer = PhonebookDebugRenderer(),
            targets = MapPhonebookDebugTargets(emptyMap()),
        )

        val result = adapter.debugSelf(PhonebookDebugCommandActor(accountId = null, permitted = true))

        assertEquals(PhonebookDebugCommandResult.ConsoleRequiresTarget, result)
        assertEquals(emptyList(), inspector.inspectedAccounts)
    }

    @Test
    fun `debug target resolves online Accounts for player or console senders`() {
        val targetAccount = uuid(2)
        val inspector = RecordingDebugInspection()
        val renderer = RecordingDebugRenderer()
        val adapter = PhonebookDebugCommandAdapter(
            inspector = inspector,
            renderer = renderer,
            targets = MapPhonebookDebugTargets(mapOf("Target" to targetAccount)),
        )

        val result = adapter.debugTarget(PhonebookDebugCommandActor(accountId = null, permitted = true), "Target")

        assertEquals(listOf(targetAccount), inspector.inspectedAccounts)
        assertEquals(
            PhonebookDebugCommandResult.Sent(
                model = PhonebookDebugModel(targetAccount, activeCharacterId = null, characters = emptyList()),
                components = renderer.renderedComponents,
            ),
            result,
        )
    }

    @Test
    fun `debug target rejects offline or unknown Accounts without inspecting`() {
        val inspector = RecordingDebugInspection()
        val adapter = PhonebookDebugCommandAdapter(
            inspector = inspector,
            renderer = PhonebookDebugRenderer(),
            targets = MapPhonebookDebugTargets(emptyMap()),
        )

        val result = adapter.debugTarget(PhonebookDebugCommandActor(accountId = uuid(1), permitted = true), "Offline")

        assertEquals(PhonebookDebugCommandResult.TargetNotOnline("Offline"), result)
        assertEquals(emptyList(), inspector.inspectedAccounts)
    }

    private fun uuid(value: Int): UUID =
        UUID.fromString("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")

    private class RecordingDebugInspection : PhonebookDebugInspection {
        val inspectedAccounts = mutableListOf<UUID>()

        override fun inspect(accountId: UUID): PhonebookDebugModel {
            inspectedAccounts += accountId
            return PhonebookDebugModel(accountId, activeCharacterId = null, characters = emptyList())
        }
    }

    private class RecordingDebugRenderer : PhonebookDebugRenderer() {
        val renderedComponents = listOf(net.kyori.adventure.text.Component.text("rendered debug"))

        override fun render(model: PhonebookDebugModel): List<net.kyori.adventure.text.Component> =
            renderedComponents
    }

    private class MapPhonebookDebugTargets(
        private val accountsByName: Map<String, UUID>,
    ) : PhonebookDebugTargetResolver {
        override fun onlineAccount(name: String): UUID? = accountsByName[name]
    }
}
