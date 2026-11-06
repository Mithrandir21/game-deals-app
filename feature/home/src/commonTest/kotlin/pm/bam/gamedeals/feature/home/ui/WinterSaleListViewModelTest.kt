@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.home.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDeal
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealPage
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHub
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubStore
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleMoney
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShelf
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinterSaleListViewModelTest {

    @Test
    fun the_first_page_loads_for_the_shelf_and_region() = withList("lows") { fixture ->
        val state = fixture.vm.state.value
        assertEquals(WinterSaleShelf.AllTimeLows, state.shelf)
        assertEquals(SALE_END, state.saleEndEpochMillis)
        assertEquals(30, state.deals.size)
        assertFalse(state.loading)
        assertEquals(listOf(Triple<WinterSaleShelf, String, Int>(WinterSaleShelf.AllTimeLows, "GB", 0)), fixture.hubs.pages)
    }

    @Test
    fun scrolling_on_loads_the_next_pages_until_the_end() = withList("cuts", pages = 2) { fixture ->
        fixture.vm.loadMore()
        runCurrent()
        assertEquals(60, fixture.vm.state.value.deals.size)
        assertTrue(fixture.vm.state.value.endReached)

        fixture.vm.loadMore()
        runCurrent()
        assertEquals(2, fixture.hubs.pages.size, "nothing more is asked for after the last page")
    }

    @Test
    fun overlapping_pages_show_each_game_once() = withList("cuts", overlap = 5) { fixture ->
        fixture.vm.loadMore()
        runCurrent()
        val ids = fixture.vm.state.value.deals.map { it.gameId }
        assertEquals(ids.distinct(), ids)
        assertEquals(55, ids.size)
    }

    @Test
    fun a_second_load_more_while_one_is_running_is_ignored() = withList("cuts") { fixture ->
        val gate = CompletableDeferred<Unit>()
        fixture.hubs.gate = gate
        fixture.vm.loadMore()
        fixture.vm.loadMore()
        runCurrent()
        assertTrue(fixture.vm.state.value.loadingMore)
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, fixture.hubs.pages.size)
    }

    @Test
    fun a_failed_first_page_offers_a_retry_that_starts_over() = withList("lows", failFirst = true) { fixture ->
        assertTrue(fixture.vm.state.value.failed)
        assertTrue(fixture.vm.state.value.deals.isEmpty())

        fixture.vm.retry()
        runCurrent()
        assertFalse(fixture.vm.state.value.failed)
        assertEquals(30, fixture.vm.state.value.deals.size)
        assertEquals(listOf(0, 0), fixture.hubs.pages.map { it.third })
    }

    @Test
    fun a_failed_later_page_keeps_the_rows_and_retries_from_where_it_stopped() = withList("cuts") { fixture ->
        fixture.hubs.fail = true
        fixture.vm.loadMore()
        runCurrent()
        assertTrue(fixture.vm.state.value.moreFailed)
        assertEquals(30, fixture.vm.state.value.deals.size)

        fixture.vm.loadMore()
        runCurrent()
        assertEquals(2, fixture.hubs.pages.size, "no automatic retry while the footer shows the error")

        fixture.hubs.fail = false
        fixture.vm.retry()
        runCurrent()
        assertFalse(fixture.vm.state.value.moreFailed)
        assertEquals(60, fixture.vm.state.value.deals.size)
        assertEquals(30, fixture.hubs.pages.last().third)
    }

    @Test
    fun a_store_list_names_the_store_from_the_hub_before_the_first_page() = withList("store:35", startPaused = true) { fixture ->
        assertEquals("GOG", fixture.vm.state.value.storeName)
        assertEquals(6_127, fixture.vm.state.value.storeDeals)
        assertTrue(fixture.vm.state.value.loading)
    }

    @Test
    fun a_store_the_hub_never_loaded_takes_its_name_from_the_deals() = withList("store:20") { fixture ->
        assertEquals("Shop 20", fixture.vm.state.value.storeName)
        assertNull(fixture.vm.state.value.storeDeals)
    }

    @Test
    fun a_new_region_starts_the_list_again() = withList("lows") { fixture ->
        fixture.vm.loadMore()
        runCurrent()
        fixture.country.value = Country("US", "United States", "USD")
        runCurrent()

        assertEquals(30, fixture.vm.state.value.deals.size)
        assertEquals(Triple<WinterSaleShelf, String, Int>(WinterSaleShelf.AllTimeLows, "US", 0), fixture.hubs.pages.last())
    }

    @Test
    fun a_page_of_games_already_shown_moves_straight_on_to_the_next_page() = withList("cuts", overlap = 30) { fixture ->
        fixture.vm.loadMore()
        runCurrent()

        assertEquals(listOf(0, 30, 60), fixture.hubs.pages.map { it.third })
        assertEquals(60, fixture.vm.state.value.deals.size)
        assertFalse(fixture.vm.state.value.loadingMore)
        assertFalse(fixture.vm.state.value.endReached)
    }

    @Test
    fun pages_emptied_by_the_adult_filter_do_not_end_the_list() = withList("lows", emptyAt = setOf(0, 60)) { fixture ->
        assertEquals(30, fixture.vm.state.value.deals.size, "an empty first page moves on to the second")
        assertFalse(fixture.vm.state.value.loading)

        fixture.vm.loadMore()
        runCurrent()
        assertEquals(60, fixture.vm.state.value.deals.size)
        assertFalse(fixture.vm.state.value.endReached)
        assertEquals(listOf(0, 30, 60, 90), fixture.hubs.pages.map { it.third })
    }

    @Test
    fun three_empty_pages_in_a_row_end_the_list() = withList("cuts", emptyAt = setOf(30, 60, 90)) { fixture ->
        fixture.vm.loadMore()
        runCurrent()

        assertEquals(listOf(0, 30, 60, 90), fixture.hubs.pages.map { it.third })
        assertTrue(fixture.vm.state.value.endReached)
        assertFalse(fixture.vm.state.value.loadingMore)
    }

    @Test
    fun a_region_change_during_a_page_load_shows_only_the_new_region() = withList("lows") { fixture ->
        val gate = CompletableDeferred<Unit>()
        fixture.hubs.gate = gate
        fixture.vm.loadMore()
        runCurrent()

        fixture.country.value = Country("US", "United States", "USD")
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(30, fixture.vm.state.value.deals.size)
        assertEquals(listOf("GB", "GB", "US"), fixture.hubs.pages.map { it.second })
        assertFalse(fixture.vm.state.value.loadingMore)
    }

    @Test
    fun an_unknown_shelf_shows_the_error_without_calling_itad() = withList("for_you") { fixture ->
        assertNull(fixture.vm.state.value.shelf)
        assertTrue(fixture.vm.state.value.failed)
        assertTrue(fixture.hubs.pages.isEmpty())
    }

    @Test
    fun a_tapped_game_is_counted_with_its_list() = withList("store:61") { fixture ->
        fixture.vm.onGamePeeked()

        assertEquals(
            listOf(AnalyticsEvents.WINTER_SALE_GAME_PEEKED to mapOf<String, Any>("shelf" to "store:61", "from" to "list")),
            fixture.analytics.events,
        )
    }

    private class Fixture(
        val vm: WinterSaleListViewModel,
        val hubs: FakeListHubs,
        val analytics: RecordingAnalytics,
        val country: MutableStateFlow<Country>,
    )

    private fun withList(
        shelf: String,
        pages: Int = 10,
        overlap: Int = 0,
        emptyAt: Set<Int> = emptySet(),
        failFirst: Boolean = false,
        startPaused: Boolean = false,
        block: suspend TestScope.(Fixture) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val country = MutableStateFlow(Country("GB", "United Kingdom", "GBP"))
        val region = mock<RegionRepository>(MockMode.autofill)
        val settings = mock<SettingsRepository>(MockMode.autofill)
        every { region.observeSelectedCountry() } returns country
        every { settings.observeMatureOptIn() } returns MutableStateFlow(false)
        val hubs = FakeListHubs(pages, overlap, emptyAt).apply { fail = failFirst }
        val analytics = RecordingAnalytics()
        val vm = WinterSaleListViewModel(
            SavedStateHandle(mapOf("shelf" to shelf, "saleEndEpochMillis" to SALE_END)), hubs, region, settings, analytics, TestingLoggingListener(),
        )
        try {
            if (!startPaused) {
                runCurrent()
                hubs.fail = false
            }
            block(Fixture(vm, hubs, analytics, country))
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}

private const val SALE_END = 1_798_740_000_000L

/** Pages of 30 games; [overlap] repeats that many games from the page before, and pages at [emptyAt] come back empty (as if every game on them were filtered out). */
private class FakeListHubs(private val pageCount: Int, private val overlap: Int, private val emptyAt: Set<Int>) : WinterSaleHubRepository {
    val pages = mutableListOf<Triple<WinterSaleShelf, String, Int>>()
    var fail = false
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun load(country: String, includeAdult: Boolean, force: Boolean): WinterSaleHub = error("not used")

    override suspend fun page(shelf: WinterSaleShelf, country: String, includeAdult: Boolean, offset: Int): WinterSaleDealPage {
        pages += Triple(shelf, country, offset)
        gate?.await()
        if (fail) error("ITAD down")
        val start = (offset - overlap).coerceAtLeast(0)
        val shopId = (shelf as? WinterSaleShelf.Store)?.shopId ?: 61
        val deals = if (offset in emptyAt) emptyList() else (start until start + 30).map { saleDeal("g$it", shopId) }
        val next = offset + 30
        return WinterSaleDealPage(deals, next.takeIf { it < pageCount * 30 })
    }

    override suspend fun dealTotals(country: String) = null

    override fun cachedStore(shopId: Int): WinterSaleHubStore? =
        if (shopId == 35) WinterSaleHubStore(35, "GOG", 6_127, emptyList()) else null
}

private fun saleDeal(id: String, shopId: Int) = WinterSaleDeal(
    gameId = id, title = "Game $id", artwork = GameArtwork(), shopId = shopId, shopName = "Shop $shopId",
    price = WinterSaleMoney(1.99, "GBP"), regular = WinterSaleMoney(9.99, "GBP"), cutPercent = 80, url = "https://itad.link/$id",
    voucher = null, flag = null, mature = false,
)
