package pm.bam.gamedeals.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class SpecificTagsTest {

    private fun tags(vararg tags: String) = GameSignals(gameId = "g", updatedEpochMs = 0, weeklyMaxCut = emptyList(), tags = tags.toList())

    @Test
    fun vague_tags_are_skipped_and_the_order_kept() {
        assertEquals(
            listOf("Action Roguelike", "Hack and Slash", "Mythology"),
            tags("Indie", "Action Roguelike", "Great Soundtrack", "Hack and Slash", "singleplayer", "Mythology", "Isometric").specificTags(),
        )
    }

    @Test
    fun broad_genres_only_fill_in_after_the_specific_ones() {
        assertEquals(listOf("Platformer", "Action", "Adventure"), tags("Action", "Indie", "Adventure", "Platformer", "RPG").specificTags())
    }

    @Test
    fun a_game_with_only_vague_tags_shows_none() {
        assertEquals(emptyList(), tags("Indie", "Singleplayer", "Atmospheric").specificTags())
        assertEquals(emptyList(), tags().specificTags())
    }
}
