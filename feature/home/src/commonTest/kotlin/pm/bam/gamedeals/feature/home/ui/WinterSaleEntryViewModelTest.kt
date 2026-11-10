@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.home.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealPage
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealTotals
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEvent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHub
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubStore
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShelf
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminderDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinterSaleEntryViewModelTest {
    @Test
    fun enabled_event_appears_and_remote_disable_removes_home_entry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeEvents()
        val reminder = RecordingReminder()
        val vm = WinterSaleEntryViewModel(source, reminder, FakeTotals(), region())
        try {
            vm.onResume()
            runCurrent()
            assertEquals("Winter", vm.event.value?.event?.title)
            source.state = WinterSaleEventState.Hidden(WinterSaleHiddenReason.Disabled)
            advanceTimeBy(15 * 60 * 1000L)
            runCurrent()
            assertNull(vm.event.value)
            assertEquals(listOf<WinterSaleEventState>(available, source.state), reminder.reconciled)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun the_home_card_switches_at_sale_start_then_rechecks_every_fifteen_minutes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val event = WinterSaleEvent("winter", 0L, 5 * MINUTE, 60 * MINUTE, "Winter", "Before", "During")
        val source = FakeEvents()
        source.state = WinterSaleEventState.Available(event, WinterSalePhase.BeforeSale, 0L, false)
        val vm = WinterSaleEntryViewModel(source, RecordingReminder(), FakeTotals(), region())
        try {
            vm.onResume()
            runCurrent()
            assertEquals(WinterSalePhase.BeforeSale, vm.event.value?.phase)
            assertEquals(1, source.refreshes)

            source.state = WinterSaleEventState.Available(event, WinterSalePhase.OnSale, 5 * MINUTE, false)
            advanceTimeBy(5 * MINUTE - 1)
            runCurrent()
            assertEquals(1, source.refreshes)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(WinterSalePhase.OnSale, vm.event.value?.phase)
            assertEquals(2, source.refreshes)

            // During the sale the next check is the 15-minute one, not the end of the sale.
            advanceTimeBy(15 * MINUTE)
            runCurrent()
            assertEquals(3, source.refreshes)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun leaving_home_stops_the_checks_and_returning_checks_at_once() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeEvents()
        val vm = WinterSaleEntryViewModel(source, RecordingReminder(), FakeTotals(), region())
        try {
            vm.onResume()
            runCurrent()
            vm.onPause()
            advanceTimeBy(60 * MINUTE)
            runCurrent()
            assertEquals(1, source.refreshes)

            vm.onResume()
            runCurrent()
            assertEquals(2, source.refreshes)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun during_the_sale_the_card_gets_the_deal_total_for_the_chosen_region() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val totals = FakeTotals()
        val vm = WinterSaleEntryViewModel(FakeEvents(), RecordingReminder(), totals, region())
        try {
            vm.onResume()
            runCurrent()
            assertEquals(WinterSaleDealTotals(64_127, 8), vm.totals.value)
            assertEquals(listOf("GB"), totals.asked)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun before_the_sale_no_total_is_asked_for() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeEvents()
        source.state = WinterSaleEventState.Available(available.event, WinterSalePhase.BeforeSale, 0L, false)
        val totals = FakeTotals()
        val vm = WinterSaleEntryViewModel(source, RecordingReminder(), totals, region())
        try {
            vm.onResume()
            runCurrent()
            assertNull(vm.totals.value)
            assertEquals(emptyList(), totals.asked)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun a_failed_total_keeps_the_last_one_and_never_hides_the_card() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val totals = FakeTotals()
        val vm = WinterSaleEntryViewModel(FakeEvents(), RecordingReminder(), totals, region())
        try {
            vm.onResume()
            runCurrent()
            totals.fail = true
            advanceTimeBy(15 * MINUTE)
            runCurrent()
            assertEquals(WinterSaleDealTotals(64_127, 8), vm.totals.value)
            assertEquals(available, vm.event.value)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun a_failed_event_check_hides_the_card() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeEvents()
        val vm = WinterSaleEntryViewModel(source, RecordingReminder(), FakeTotals(), region())
        try {
            vm.onResume()
            runCurrent()
            source.fail = true
            advanceTimeBy(15 * MINUTE)
            runCurrent()
            assertNull(vm.event.value)
            assertNull(vm.totals.value)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun a_second_resume_keeps_one_check_running() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeEvents()
        val vm = WinterSaleEntryViewModel(source, RecordingReminder(), FakeTotals(), region())
        try {
            vm.onResume()
            vm.onResume()
            runCurrent()
            advanceTimeBy(15 * MINUTE)
            runCurrent()
            assertEquals(2, source.refreshes)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun region(): RegionRepository = mock<RegionRepository>(MockMode.autofill).also {
        every { it.observeSelectedCountry() } returns MutableStateFlow(Country("GB", "United Kingdom", "GBP"))
    }

    private class FakeTotals : WinterSaleHubRepository {
        val asked = mutableListOf<String>()
        var fail = false
        override suspend fun dealTotals(country: String): WinterSaleDealTotals? {
            asked += country
            if (fail) error("ITAD down")
            return WinterSaleDealTotals(64_127, 8)
        }
        override suspend fun load(country: String, includeAdult: Boolean, force: Boolean): WinterSaleHub = error("not used")
        override suspend fun page(shelf: WinterSaleShelf, country: String, includeAdult: Boolean, offset: Int): WinterSaleDealPage = error("not used")
        override fun cachedStore(shopId: Int): WinterSaleHubStore? = null
    }

    private val available = WinterSaleEventState.Available(
        WinterSaleEvent("winter", 0L, 0L, 10_000_000_000L, "Winter", "Before", "During"),
        WinterSalePhase.OnSale, 1_000L, false,
    )

    private inner class FakeEvents : WinterSaleEventRepository {
        var state: WinterSaleEventState = available
        var refreshes = 0
        var fail = false
        override suspend fun refresh(): WinterSaleEventState {
            refreshes++
            if (fail) error("event file unreachable")
            return state
        }
    }

    private companion object { const val MINUTE = 60 * 1000L }

    private class RecordingReminder : WinterSaleReminder {
        val reconciled = mutableListOf<WinterSaleEventState>()
        override suspend fun sync() = Unit
        override suspend fun reconcile(state: WinterSaleEventState) { reconciled += state }
        override suspend fun cancel() = Unit
        override suspend fun onSummaryPosted() = Unit
        override suspend fun claimDelivery() = WinterSaleReminderDecision.Skip
    }
}
