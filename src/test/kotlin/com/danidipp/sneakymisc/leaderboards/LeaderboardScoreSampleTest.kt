package com.danidipp.sneakymisc.leaderboards

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class LeaderboardScoreSampleTest {
    @Test
    fun `score sample chooses the leaderboard subject from the leaderboard type`() {
        val accountId = UUID.randomUUID()
        val characterId = UUID.randomUUID()
        val sample = LeaderboardScoreSample(
            accountId = accountId,
            characterId = characterId,
            characterName = "Dani",
            value = 12.4,
            leaderboardDate = "2026-07-04T07:00:00Z",
        )

        assertEquals(accountId, sample.subjectId(LeaderboardType.PLAYER))
        assertEquals(characterId, sample.subjectId(LeaderboardType.CHARACTER))
    }
}
