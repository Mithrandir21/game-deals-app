package pm.bam.gamedeals.widget

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.fixtures.giveaway
import java.io.IOException

/** What the Free Games widget loads from the giveaways cache ([FreeGamesWidget.load]). */
class FreeGamesWidgetLoadTest {

    // Only used to download thumbnails, and every fixture has none.
    private val context: Context = mockk()
    private val giveawaysRepository: GiveawaysRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single { giveawaysRepository }
                single<Logger> { mockk(relaxed = true) }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private suspend fun load() = FreeGamesWidget().load(context)

    // The fixtures all pass a blank thumbnail, which skips the image download.
    private fun cached(vararg giveaways: Giveaway) {
        every { giveawaysRepository.observeGiveaways(any()) } returns flowOf(giveaways.toList())
    }

    private fun refreshFails() {
        coEvery { giveawaysRepository.refreshGiveawaysIfOlderThan(any()) } throws IOException("offline")
    }

    private fun FreeGamesState.games() = (this as FreeGamesState.Loaded).games

    @Test
    fun it_asks_for_pc_games_newest_first_like_the_giveaways_tab() = runTest {
        cached()

        load()

        verify {
            giveawaysRepository.observeGiveaways(match {
                it.sortBy == GiveawaySortBy.DATE &&
                    it.platforms.filter { p -> p.selected }.map { p -> p.platform } == listOf(GiveawayPlatform.PC) &&
                    it.types.filter { t -> t.selected }.map { t -> t.type } == listOf(GiveawayType.GAME)
            })
        }
    }

    @Test
    fun it_refetches_giveaways_older_than_its_refresh_period() = runTest {
        cached()

        load()

        coVerify { giveawaysRepository.refreshGiveawaysIfOlderThan(FreeGamesWidget.FRESH_FOR_MILLIS) }
    }

    @Test
    fun titles_drop_gamerpowers_giveaway_suffix() = runTest {
        cached(giveaway(title = "Hades Giveaway", thumbnail = ""))

        assertEquals("Hades", load().games().single().title)
    }

    @Test
    fun a_missing_usual_price_is_left_out() = runTest {
        cached(
            giveaway(id = 1, worthDenominated = "\$19.99", thumbnail = ""),
            giveaway(id = 2, worthDenominated = "N/A", thumbnail = ""),
            giveaway(id = 3, worthDenominated = " ", thumbnail = ""),
            giveaway(id = 4, worthDenominated = null, thumbnail = ""),
        )

        assertEquals(listOf("\$19.99", null, null, null), load().games().map { it.worth })
    }

    @Test
    fun a_past_missing_or_unknown_end_date_gets_no_ends_label() = runTest {
        cached(
            giveaway(id = 1, endDate = "2000-01-01 00:00:00", thumbnail = ""),
            giveaway(id = 2, endDate = "N/A", thumbnail = ""),
            giveaway(id = 3, endDate = null, thumbnail = ""),
        )

        assertEquals(listOf(null, null, null), load().games().map { it.endsLabel })
    }

    @Test
    fun at_most_the_widget_row_limit_is_loaded() = runTest {
        cached(*(1..10).map { giveaway(id = it, thumbnail = "") }.toTypedArray())

        assertEquals(WIDGET_MAX_ROWS, load().games().size)
    }

    @Test
    fun nothing_free_is_an_empty_list_not_an_error() = runTest {
        cached()

        assertEquals(FreeGamesState.Loaded(emptyList()), load())
    }

    @Test
    fun a_failed_refresh_keeps_showing_the_cache() = runTest {
        cached(giveaway(id = 1, thumbnail = ""))
        refreshFails()

        assertEquals(listOf(1), load().games().map { it.id })
    }

    @Test
    fun a_failed_refresh_with_nothing_cached_shows_the_error_state() = runTest {
        cached()
        refreshFails()

        assertEquals(FreeGamesState.Error, load())
    }
}
