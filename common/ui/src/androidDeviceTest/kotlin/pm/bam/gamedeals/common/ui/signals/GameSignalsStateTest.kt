package pm.bam.gamedeals.common.ui.signals

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.models.GameSignals
import pm.bam.gamedeals.domain.repositories.signals.GameSignalsRepository
import java.util.concurrent.atomic.AtomicInteger

/** Rows ask once more, about a minute later, for games the Worker was still fetching (matrix 5.4). */
class GameSignalsStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val id = "018d937f-07c2-7155-b6e2-f5f9b3c5b7a5"
    private val signals = GameSignals(gameId = id, updatedEpochMs = 1, weeklyMaxCut = emptyList(), reviewScore = 98)

    /** Answers "still fetching" (nothing) until [readyFrom] asks, then the game's signals. */
    private class FakeRepository(private val signals: GameSignals, private val readyFrom: Int) : GameSignalsRepository {
        val asks = AtomicInteger()
        override suspend fun getSignals(gameIds: Collection<String>): Map<String, GameSignals> =
            if (asks.incrementAndGet() >= readyFrom) mapOf(signals.gameId to signals) else emptyMap()
    }

    private fun show(repository: GameSignalsRepository) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalGameSignals provides repository) {
                val loaded = rememberGameSignals(listOf(id))
                Text(loaded[id]?.reviewScore?.let { "$it% positive" } ?: "no signals yet")
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun a_game_the_worker_was_still_fetching_gains_its_review_line_after_one_recheck() {
        val repository = FakeRepository(signals, readyFrom = 2)
        show(repository)
        composeTestRule.onNodeWithText("no signals yet").assertExists()

        composeTestRule.mainClock.advanceTimeBy(60_000)
        composeTestRule.onNodeWithText("no signals yet").assertExists()
        assertEquals(1, repository.asks.get())

        composeTestRule.mainClock.advanceTimeBy(10_000)
        composeTestRule.onNodeWithText("98% positive").assertExists()
        assertEquals(2, repository.asks.get())

        composeTestRule.mainClock.advanceTimeBy(300_000)
        assertEquals("asked again only once", 2, repository.asks.get())
    }

    @Test
    fun a_game_with_signals_at_once_is_not_asked_about_again() {
        val repository = FakeRepository(signals, readyFrom = 1)
        show(repository)
        composeTestRule.onNodeWithText("98% positive").assertExists()

        composeTestRule.mainClock.advanceTimeBy(300_000)
        assertEquals(1, repository.asks.get())
    }
}
