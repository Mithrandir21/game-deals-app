@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.home.ui

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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealPage
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealTotals
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEvent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHub
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubStore
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShelf
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinterSaleHubViewModelTest {

    @Test
    fun before_the_sale_the_entry_stays_on_the_guide_and_loads_no_shelves() = withFixture { fixture ->
        fixture.events.state = available(WinterSalePhase.BeforeSale)
        fixture.vm.onResume()
        runCurrent()

        val state = fixture.vm.state.value
        assertTrue(state.eventChecked)
        assertFalse(state.onSale)
        assertEquals(0, fixture.hubs.loads.size)
        assertTrue(fixture.analytics.events.isEmpty())
    }

    @Test
    fun during_the_sale_the_hub_loads_and_its_opening_is_counted_once() = withFixture { fixture ->
        fixture.vm.onResume()
        runCurrent()

        val state = fixture.vm.state.value
        assertTrue(state.onSale)
        assertEquals("GB", state.hub?.country)
        assertFalse(state.loading)
        assertEquals(listOf("GB" to false), fixture.hubs.loads.map { it.first to it.second })

        fixture.vm.onPause()
        fixture.vm.onResume()
        runCurrent()
        assertEquals(1, fixture.analytics.events.count { it.first == AnalyticsEvents.WINTER_SALE_HUB_OPENED })
    }

    @Test
    fun watching_as_the_sale_starts_waits_a_moment_before_switching_to_the_hub() = withFixture(jitterMillis = 90_000L) { fixture ->
        fixture.events.state = available(WinterSalePhase.BeforeSale, serverNow = SALE_START - 60_000L)
        fixture.vm.onResume()
        runCurrent()
        assertFalse(fixture.vm.state.value.onSale)

        fixture.events.state = available(WinterSalePhase.OnSale, serverNow = SALE_START)
        advanceTimeBy(60_000L + 1)
        runCurrent()
        assertFalse(fixture.vm.state.value.onSale, "still on the guide during the jitter")
        assertEquals(0, fixture.hubs.loads.size)

        advanceTimeBy(90_000L)
        runCurrent()
        assertTrue(fixture.vm.state.value.onSale)
        assertEquals(1, fixture.hubs.loads.size)
    }

    @Test
    fun the_end_of_the_sale_drops_the_hub() = withFixture { fixture ->
        fixture.vm.onResume()
        runCurrent()
        assertTrue(fixture.vm.state.value.onSale)

        fixture.events.state = WinterSaleEventState.Hidden(WinterSaleHiddenReason.Ended)
        fixture.time += 31_000L
        fixture.vm.onPullToRefresh()
        runCurrent()

        assertFalse(fixture.vm.state.value.onSale)
        assertNull(fixture.vm.state.value.hub)
    }

    @Test
    fun a_hub_that_cannot_load_offers_a_retry() = withFixture { fixture ->
        fixture.hubs.fail = true
        fixture.vm.onResume()
        runCurrent()
        assertTrue(fixture.vm.state.value.failed)
        assertNull(fixture.vm.state.value.hub)

        fixture.hubs.fail = false
        fixture.vm.onRetry()
        runCurrent()
        assertFalse(fixture.vm.state.value.failed)
        assertEquals(true, fixture.hubs.loads.last().third, "a retry skips the cache")
    }

    @Test
    fun a_failed_refresh_keeps_the_shelves_already_shown() = withFixture { fixture ->
        fixture.vm.onResume()
        runCurrent()
        val shown = fixture.vm.state.value.hub

        fixture.hubs.fail = true
        fixture.time += 31_000L
        fixture.vm.onPullToRefresh()
        runCurrent()

        assertEquals(shown, fixture.vm.state.value.hub)
        assertFalse(fixture.vm.state.value.failed)
    }

    @Test
    fun a_pull_right_after_a_load_is_ignored() = withFixture { fixture ->
        fixture.vm.onResume()
        runCurrent()
        fixture.time += 10_000L
        fixture.vm.onPullToRefresh()
        runCurrent()
        assertEquals(1, fixture.hubs.loads.size)

        fixture.time += 25_000L
        fixture.vm.onPullToRefresh()
        runCurrent()
        assertEquals(2, fixture.hubs.loads.size)
        assertEquals(true, fixture.hubs.loads.last().third)
    }

    @Test
    fun a_new_region_or_adult_setting_reloads_for_it() = withFixture { fixture ->
        fixture.vm.onResume()
        runCurrent()

        fixture.country.value = Country("US", "United States", "USD")
        runCurrent()
        assertEquals("US", fixture.vm.state.value.hub?.country)

        fixture.adult.value = true
        runCurrent()
        assertEquals(true, fixture.vm.state.value.hub?.includeAdult)
        assertEquals(listOf("GB", "US", "US"), fixture.hubs.loads.map { it.first })
    }

    @Test
    fun a_region_that_arrives_after_the_screen_opens_still_loads_the_hub() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val country = kotlinx.coroutines.flow.MutableSharedFlow<Country>(replay = 1)
        val region = mock<RegionRepository>(MockMode.autofill)
        val settings = mock<SettingsRepository>(MockMode.autofill)
        every { region.observeSelectedCountry() } returns country
        every { settings.observeMatureOptIn() } returns MutableStateFlow(false)
        val hubs = FakeHubs()
        val vm = WinterSaleHubViewModel(FakeEvents(), hubs, region, settings, mock<WinterSaleReminder>(MockMode.autoUnit),
            RecordingAnalytics(), Clock { testScheduler.currentTime }, TestingLoggingListener()) { 0L }
        try {
            vm.onResume()
            runCurrent()
            assertTrue(vm.state.value.eventChecked)
            assertTrue(vm.state.value.loading)
            assertEquals(0, hubs.loads.size)

            country.emit(Country("GB", "United Kingdom", "GBP"))
            runCurrent()
            assertEquals("GB", vm.state.value.hub?.country)
            assertFalse(vm.state.value.loading)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun shelf_and_game_taps_are_counted_with_their_shelf() = withFixture { fixture ->
        fixture.vm.onShelfOpened(WinterSaleShelf.Store(61).key)
        fixture.vm.onGamePeeked(WinterSaleShelf.AllTimeLows.key)

        assertEquals(
            listOf(
                AnalyticsEvents.WINTER_SALE_SHELF_OPENED to mapOf<String, Any>("shelf" to "store:61"),
                AnalyticsEvents.WINTER_SALE_GAME_PEEKED to mapOf<String, Any>("shelf" to "lows", "from" to "hub"),
            ),
            fixture.analytics.events,
        )
    }

    @Test
    fun the_sale_lists_count_down_by_the_server_clock_even_when_the_phone_clock_is_off() = withFixture { fixture ->
        // The phone's clock reads 0 while the server says the sale has been on for a minute.
        fixture.vm.onResume()
        runCurrent()

        val left = SALE_END - (SALE_START + 60_000L)
        assertEquals(left, fixture.vm.state.value.saleEndOnPhoneEpochMillis)

        fixture.time += 5_000L
        fixture.events.state = available(WinterSalePhase.OnSale, serverNow = SALE_START + 65_000L)
        fixture.time += 31_000L
        fixture.vm.onPullToRefresh()
        runCurrent()
        assertEquals(left + 36_000L - 5_000L, fixture.vm.state.value.saleEndOnPhoneEpochMillis)
    }

    @Test
    fun a_failed_event_check_shows_the_guide_as_unavailable() = withFixture { fixture ->
        fixture.events.fail = true
        fixture.vm.onResume()
        runCurrent()

        val state = fixture.vm.state.value
        assertTrue(state.eventChecked)
        assertFalse(state.onSale)
        assertEquals(WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable), state.event)
        assertEquals(0, fixture.hubs.loads.size)
    }

    @Test
    fun a_region_change_during_a_load_shows_only_the_new_region() = withFixture { fixture ->
        val gate = CompletableDeferred<Unit>()
        fixture.hubs.gate = gate
        fixture.vm.onResume()
        runCurrent()
        assertTrue(fixture.vm.state.value.loading)

        fixture.country.value = Country("US", "United States", "USD")
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("GB", "US"), fixture.hubs.loads.map { it.first })
        assertEquals("US", fixture.vm.state.value.hub?.country)
        assertFalse(fixture.vm.state.value.loading)
    }

    @Test
    fun a_region_change_while_away_clears_the_hub_and_loads_on_return() = withFixture { fixture ->
        fixture.vm.onResume()
        runCurrent()
        fixture.vm.onPause()

        fixture.country.value = Country("US", "United States", "USD")
        runCurrent()
        assertNull(fixture.vm.state.value.hub)
        assertEquals(1, fixture.hubs.loads.size, "nothing loads while the screen is away")

        fixture.vm.onResume()
        runCurrent()
        assertEquals("US", fixture.vm.state.value.hub?.country)
    }

    @Test
    fun the_hub_rechecks_every_fifteen_minutes_and_right_at_the_end() = withFixture { fixture ->
        fixture.vm.onResume()
        fixture.vm.onResume()
        runCurrent()
        assertEquals(1, fixture.events.refreshes, "a second resume doesn't start a second check")

        advanceTimeBy(15 * 60 * 1000L + 1)
        runCurrent()
        assertEquals(2, fixture.events.refreshes)

        fixture.events.state = available(WinterSalePhase.OnSale, serverNow = SALE_END - 60_000L)
        advanceTimeBy(15 * 60 * 1000L + 1)
        runCurrent()
        assertEquals(3, fixture.events.refreshes)
        advanceTimeBy(60_000L + 1)
        runCurrent()
        assertEquals(4, fixture.events.refreshes, "the next check lands on the sale's end")
    }

    @Test
    fun an_empty_hub_that_also_failed_offers_a_retry_but_an_empty_one_that_loaded_does_not() = withFixture { fixture ->
        fixture.hubs.empty = true
        fixture.hubs.partial = true
        fixture.vm.onResume()
        runCurrent()
        assertTrue(fixture.vm.state.value.failed)

        fixture.hubs.partial = false
        fixture.vm.onRetry()
        runCurrent()
        assertFalse(fixture.vm.state.value.failed)
        assertEquals(true, fixture.vm.state.value.hub?.isEmpty)
    }

    @Test
    fun a_pull_after_the_clock_moved_back_still_reloads() = withFixture { fixture ->
        fixture.vm.onResume()
        runCurrent()
        fixture.time -= 1_000L
        fixture.vm.onPullToRefresh()
        runCurrent()
        assertEquals(2, fixture.hubs.loads.size)
    }

    @Test
    fun countdown_reads_days_then_hours_under_two_days_then_soon() {
        val hour = 60 * 60 * 1000L
        assertEquals(SaleCountdown.Days(12), saleCountdown(12 * 24 * hour + 5 * hour))
        assertEquals(SaleCountdown.Days(2), saleCountdown(48 * hour))
        assertEquals(SaleCountdown.Hours(47), saleCountdown(48 * hour - 1), "not '1 day' with 47 hours left")
        assertEquals(SaleCountdown.Hours(24), saleCountdown(24 * hour))
        assertEquals(SaleCountdown.Hours(23), saleCountdown(24 * hour - 1))
        assertEquals(SaleCountdown.Hours(1), saleCountdown(hour))
        assertEquals(SaleCountdown.Soon, saleCountdown(hour - 1))
        assertEquals(SaleCountdown.Soon, saleCountdown(-5))
    }

    @Test
    fun the_countdown_pill_names_the_day_in_the_phone_time_zone() {
        val evening = 1_797_285_600_000L // 14 Dec 2026, 22:00 UTC
        assertEquals("14 Dec", winterSaleDay(evening, TimeZone.UTC))
        assertEquals("15 Dec", winterSaleDay(evening, TimeZone.of("Asia/Tokyo")), "already the 15th in Tokyo")
    }

    @Test
    fun deal_totals_round_down_to_the_thousand() {
        assertEquals("64,000+", roundedDealTotal(64_127))
        assertEquals("1,000+", roundedDealTotal(1_000))
        assertEquals("999", roundedDealTotal(999))
    }

    private class Fixture(
        val vm: WinterSaleHubViewModel,
        val events: FakeEvents,
        val hubs: FakeHubs,
        val analytics: RecordingAnalytics,
        val country: MutableStateFlow<Country>,
        val adult: MutableStateFlow<Boolean>,
    ) {
        var time = 0L
    }

    private fun withFixture(jitterMillis: Long = 0L, block: suspend TestScope.(Fixture) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val country = MutableStateFlow(Country("GB", "United Kingdom", "GBP"))
        val adult = MutableStateFlow(false)
        val region = mock<RegionRepository>(MockMode.autofill)
        val settings = mock<SettingsRepository>(MockMode.autofill)
        every { region.observeSelectedCountry() } returns country
        every { settings.observeMatureOptIn() } returns adult
        val events = FakeEvents()
        val hubs = FakeHubs()
        val analytics = RecordingAnalytics()
        lateinit var fixture: Fixture
        val vm = WinterSaleHubViewModel(
            events, hubs, region, settings, mock<WinterSaleReminder>(MockMode.autoUnit), analytics,
            Clock { fixture.time + testScheduler.currentTime }, TestingLoggingListener(),
        ) { jitterMillis }
        fixture = Fixture(vm, events, hubs, analytics, country, adult)
        try {
            runCurrent()
            block(fixture)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}

private const val SALE_START = 1_797_530_400_000L
private const val SALE_END = SALE_START + 14 * 24 * 60 * 60 * 1000L

private fun available(phase: WinterSalePhase, serverNow: Long = SALE_START + 60_000L) = WinterSaleEventState.Available(
    WinterSaleEvent("winter", SALE_START - 86_400_000L, SALE_START, SALE_END, "Winter", "Soon", "On"), phase, serverNow, false,
)

private class FakeEvents : WinterSaleEventRepository {
    var state: WinterSaleEventState = available(WinterSalePhase.OnSale)
    var fail = false
    var refreshes = 0
    override suspend fun refresh(): WinterSaleEventState {
        refreshes++
        if (fail) error("event file unreachable")
        return state
    }
}

private class FakeHubs : WinterSaleHubRepository {
    val loads = mutableListOf<Triple<String, Boolean, Boolean>>()
    var fail = false
    var empty = false
    var partial = false
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun load(country: String, includeAdult: Boolean, force: Boolean): WinterSaleHub {
        loads += Triple(country, includeAdult, force)
        gate?.await()
        if (fail) error("ITAD down")
        val steam = WinterSaleHubStore(61, "Steam", 30_000, emptyList()).takeUnless { empty }
        return WinterSaleHub(country, includeAdult, steam, emptyList(), emptyList(), emptyList(),
            WinterSaleDealTotals(30_000, 1), partial = partial, loadedAtEpochMillis = 0L)
    }

    override suspend fun page(shelf: WinterSaleShelf, country: String, includeAdult: Boolean, offset: Int) = WinterSaleDealPage(emptyList(), null)

    override suspend fun dealTotals(country: String) = null

    override fun cachedStore(shopId: Int): WinterSaleHubStore? = null
}

internal class RecordingAnalytics : Analytics {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun screen(name: String, properties: Map<String, Any>) = Unit
    override fun capture(event: String, properties: Map<String, Any>) { events += event to properties }
    override fun identify(distinctId: String) = Unit
    override fun reset() = Unit
    override fun setConsent(granted: Boolean) = Unit
}
