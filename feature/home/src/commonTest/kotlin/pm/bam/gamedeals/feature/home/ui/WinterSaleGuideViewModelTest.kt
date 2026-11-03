@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.home.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.viewModelScope
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleCandidate
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEvent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideCard
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideContent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideSection
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideSectionKind
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleMoney
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleOffer
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinterSaleGuideViewModelTest {
    @Test
    fun returns_within_fifteen_minutes_keep_prices_and_pull_refresh_always_reloads() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = fixture(Clock { 1_000 + testScheduler.currentTime })
        try {
            runCurrent()
            fixture.vm.onResume()
            runCurrent()
            assertEquals(1, fixture.guide.calls.size)
            fixture.vm.onPause()

            advanceTimeBy(10 * 60 * 1000L)
            fixture.vm.onResume()
            runCurrent()
            assertEquals(1, fixture.guide.calls.size)
            fixture.vm.onPause()

            advanceTimeBy(6 * 60 * 1000L)
            fixture.vm.onResume()
            runCurrent()
            assertEquals(2, fixture.guide.calls.size)
            fixture.vm.refresh()
            runCurrent()
            assertEquals(3, fixture.guide.calls.size)
        } finally {
            fixture.vm.onPause()
            fixture.vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun adult_off_filters_immediately_and_region_account_or_disable_clears_old_context() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = fixture(Clock { 1_000 + testScheduler.currentTime })
        try {
            runCurrent()
            fixture.vm.onResume()
            runCurrent()
            assertEquals(2, fixture.vm.state.value.content?.waitlist?.cards?.size)

            fixture.adult.value = true
            runCurrent()
            val callsAfterAdultOn = fixture.guide.calls.size
            fixture.adult.value = false
            runCurrent()
            assertEquals(callsAfterAdultOn, fixture.guide.calls.size)
            assertEquals(1, fixture.vm.state.value.content?.forAdultSetting(false)?.waitlist?.cards?.size)

            fixture.country.value = Country("US", "United States", "USD")
            runCurrent()
            assertEquals("US", fixture.vm.state.value.content?.country)
            assertEquals("US", fixture.guide.calls.last())
            fixture.auth.value = AuthState.LoggedIn("other-account")
            runCurrent()
            assertEquals("US", fixture.guide.calls.last())

            fixture.guide.fail = true
            fixture.vm.refresh()
            runCurrent()
            assertTrue(fixture.vm.state.value.stalePrices)
            fixture.events.state = WinterSaleEventState.Hidden(pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason.Disabled)
            fixture.vm.refresh()
            runCurrent()
            assertNull(fixture.vm.state.value.content)
            assertFalse(fixture.vm.state.value.stalePrices)
        } finally {
            fixture.vm.onPause()
            fixture.vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun the_open_guide_reloads_as_the_sale_starts_and_hides_at_the_end() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = fixture(Clock { 1_000 + testScheduler.currentTime })
        val event = WinterSaleEvent("winter", 0L, 5 * 60 * 1000L, 10 * 60 * 1000L, "Winter", "Before", "During")
        fixture.events.state = WinterSaleEventState.Available(event, WinterSalePhase.BeforeSale, 4 * 60 * 1000L, false)
        try {
            runCurrent()
            fixture.vm.onResume()
            runCurrent()
            assertEquals(listOf(false), fixture.guide.saleActive)

            fixture.events.state = WinterSaleEventState.Available(event, WinterSalePhase.OnSale, 5 * 60 * 1000L, false)
            advanceTimeBy(60 * 1000L + 1)
            runCurrent()
            assertEquals(listOf(false, true), fixture.guide.saleActive)
            assertEquals(WinterSalePhase.OnSale, (fixture.vm.state.value.event as WinterSaleEventState.Available).phase)

            fixture.events.state = WinterSaleEventState.Hidden(pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason.Ended)
            advanceTimeBy(5 * 60 * 1000L + 1)
            runCurrent()
            assertNull(fixture.vm.state.value.content)
            assertTrue(fixture.vm.state.value.event is WinterSaleEventState.Hidden)
        } finally {
            fixture.vm.onPause()
            fixture.vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun the_guide_checks_before_saying_unavailable_and_a_retry_keeps_just_fetched_prices() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = fixture(Clock { 1_000 + testScheduler.currentTime })
        fixture.guide.partial = true
        try {
            runCurrent()
            assertFalse(fixture.vm.state.value.eventChecked)
            fixture.vm.onResume()
            runCurrent()
            assertTrue(fixture.vm.state.value.eventChecked)
            assertEquals(1, fixture.guide.calls.size)
            fixture.vm.onPause()

            advanceTimeBy(60 * 1000L)
            val gate = CompletableDeferred<Unit>()
            fixture.guide.gate = gate
            fixture.vm.onResume()
            runCurrent()
            assertEquals(2, fixture.guide.calls.size)
            assertTrue(fixture.vm.state.value.refreshing)
            assertFalse(fixture.vm.state.value.stalePrices)
            gate.complete(Unit)
            runCurrent()
            assertFalse(fixture.vm.state.value.refreshing)
            assertFalse(fixture.vm.state.value.stalePrices)
        } finally {
            fixture.vm.onPause()
            fixture.vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun fixture(clock: Clock): Fixture {
        val country = MutableStateFlow(Country("GB", "United Kingdom", "GBP"))
        val adult = MutableStateFlow(false)
        val auth = MutableStateFlow<AuthState>(AuthState.LoggedOut)
        val regionRepository = mock<RegionRepository>(MockMode.autofill)
        val settingsRepository = mock<SettingsRepository>(MockMode.autofill)
        val authStore = mock<AuthTokenStore>(MockMode.autofill)
        every { regionRepository.observeSelectedCountry() } returns country
        every { settingsRepository.observeMatureOptIn() } returns adult
        every { authStore.observeAuthState() } returns auth
        val events = FakeEvents()
        val guide = FakeGuide()
        val vm = WinterSaleGuideViewModel(events, guide, regionRepository, settingsRepository, authStore, clock, mock<WinterSaleReminder>(MockMode.autoUnit))
        return Fixture(vm, country, adult, auth, events, guide)
    }

    private data class Fixture(val vm: WinterSaleGuideViewModel, val country: MutableStateFlow<Country>, val adult: MutableStateFlow<Boolean>,
        val auth: MutableStateFlow<AuthState>, val events: FakeEvents, val guide: FakeGuide)

    private class FakeEvents : WinterSaleEventRepository {
        var state: WinterSaleEventState = WinterSaleEventState.Available(
            WinterSaleEvent("winter", 0L, 0L, 10_000_000_000L, "Winter", "Before", "During"),
            WinterSalePhase.OnSale, 1_000L, false,
        )
        override suspend fun refresh() = state
    }

    private class FakeGuide : WinterSaleGuideRepository {
        val calls = mutableListOf<String>()
        val saleActive = mutableListOf<Boolean>()
        var fail = false
        var partial = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(country: String, includeAdult: Boolean, saleStartEpochMs: Long, saleActive: Boolean): WinterSaleGuideContent {
            calls += country
            this.saleActive += saleActive
            gate?.await()
            if (fail) error("offline")
            val safe = card("safe", country, true)
            val adult = card("adult", country, false)
            return WinterSaleGuideContent(country, 1_000L,
                WinterSaleGuideSection(WinterSaleGuideSectionKind.Waitlist, listOf(safe, adult), incomplete = partial),
                WinterSaleGuideSection(WinterSaleGuideSectionKind.Series, emptyList()),
                WinterSaleGuideSection(WinterSaleGuideSectionKind.Popular, emptyList()))
        }

        private fun card(id: String, country: String, allowedOff: Boolean): WinterSaleGuideCard {
            val offer = WinterSaleOffer(id, country, 1, "Shop", WinterSaleMoney(10.0, "GBP"), null, 20,
                "https://example.com/$id", null, emptyList(), null, null, null)
            return WinterSaleGuideCard(WinterSaleCandidate(id, id, GameArtwork(), offer, !allowedOff), listOf("Reason"), allowedOff)
        }
    }
}
