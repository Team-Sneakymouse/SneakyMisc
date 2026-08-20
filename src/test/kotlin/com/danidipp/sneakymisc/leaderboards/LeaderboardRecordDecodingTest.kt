package com.danidipp.sneakymisc.leaderboards

import kotlin.test.Test
import kotlin.test.assertEquals

class LeaderboardRecordDecodingTest {
    @Test
    fun `PocketBase response metadata is ignored when decoding a leaderboard record`() {
        val json = """
            {
              "id": "record-1",
              "collectionId": "pbc_1302744708",
              "collectionName": "lom2_leaderboards",
              "created": "2026-08-15 09:19:07.000Z",
              "updated": "2026-08-15 09:19:07.000Z",
              "leaderboard": "bounty",
              "date": "2026-08-15 00:00:00.000Z",
              "account": "acc-1",
              "name": "Dani",
              "value": 42
            }
        """.trimIndent()

        val record = decodeLeaderboardRecord(json)

        assertEquals("record-1", record.recordId)
        assertEquals("bounty", record.leaderboard)
        assertEquals("2026-08-15 00:00:00.000Z", record.date)
        assertEquals("acc-1", record.account)
        assertEquals("Dani", record.name)
        assertEquals(42, record.value)
    }
}
