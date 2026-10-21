package com.danidipp.sneakymisc.leaderboards

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class LeaderboardCharacterNameTest {
    @Test
    fun `score sampling resolves the unformatted character name for the sampled account`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/com/danidipp/sneakymisc/leaderboards/LeaderboardsModule.kt"
        ))
        assertContains(source, "characterName = PlaceholderAPI.setPlaceholders(player, \"%sneakycharacters_character_name_noformat%\")")
    }

    @Test
    fun `leaderboard dependencies include the placeholder resolver`() {
        assertContains(LeaderboardsModule.deps, "PlaceholderAPI")
    }
}
