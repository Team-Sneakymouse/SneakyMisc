package com.danidipp.sneakymisc.dvzregistrations

import com.danidipp.sneakymisc.databasesync.AccountRecord
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class RegistrationAccountCache {
    private val accounts = AtomicReference<Map<UUID, AccountRecord>>(emptyMap())

    fun replaceWith(records: Iterable<AccountRecord>) {
        accounts.set(records.associateBy { UUID.fromString(it.recordId) })
    }

    fun upsert(record: AccountRecord) {
        val accountId = UUID.fromString(record.recordId)
        accounts.updateAndGet { current -> current + (accountId to record) }
    }

    fun remove(accountId: UUID) {
        accounts.updateAndGet { current -> current - accountId }
    }

    fun isRegisteredForDvz(accountId: UUID): Boolean =
        accounts.get()[accountId]?.dvz == true
}
