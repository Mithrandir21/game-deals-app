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
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEvent
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
        val vm = WinterSaleEntryViewModel(source, reminder)
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
        val vm = WinterSaleEntryViewModel(source, RecordingReminder())
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
        val vm = WinterSaleEntryViewModel(source, RecordingReminder())
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

    private val available = WinterSaleEventState.Available(
        WinterSaleEvent("winter", 0L, 0L, 10_000_000_000L, "Winter", "Before", "During"),
        WinterSalePhase.OnSale, 1_000L, false,
    )

    private inner class FakeEvents : WinterSaleEventRepository {
        var state: WinterSaleEventState = available
        var refreshes = 0
        override suspend fun refresh(): WinterSaleEventState { refreshes++; return state }
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
