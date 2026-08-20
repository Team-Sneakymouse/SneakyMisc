package com.danidipp.sneakymisc.dvzregistrations

import com.danidipp.sneakymisc.databasesync.AccountRecord
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationAccountCacheTest {
    @Test
    fun `registration cache reports only accounts registered for DvZ`() {
        val registeredAccountId = UUID.randomUUID()
        val unregisteredAccountId = UUID.randomUUID()
        val cache = RegistrationAccountCache()

        cache.replaceWith(
            listOf(
                accountRecord(registeredAccountId, dvz = true),
                accountRecord(unregisteredAccountId, dvz = false),
            )
        )

        assertTrue(cache.isRegisteredForDvz(registeredAccountId))
        assertFalse(cache.isRegisteredForDvz(unregisteredAccountId))
    }

    @Test
    fun `registration cache applies realtime removals`() {
        val accountId = UUID.randomUUID()
        val cache = RegistrationAccountCache()

        cache.replaceWith(listOf(accountRecord(accountId, dvz = true)))
        cache.remove(accountId)

        assertFalse(cache.isRegisteredForDvz(accountId))
    }

    @Test
    fun `registration cache applies realtime upserts`() {
        val accountId = UUID.randomUUID()
        val cache = RegistrationAccountCache()

        cache.upsert(accountRecord(accountId, dvz = true))
        assertTrue(cache.isRegisteredForDvz(accountId))

        cache.upsert(accountRecord(accountId, dvz = false))
        assertFalse(cache.isRegisteredForDvz(accountId))
    }

    private fun accountRecord(accountId: UUID, dvz: Boolean) = AccountRecord(
        recordId = accountId.toString(),
        name = "Account",
        owner = "",
        main = false,
        dvz = dvz,
    )
}
