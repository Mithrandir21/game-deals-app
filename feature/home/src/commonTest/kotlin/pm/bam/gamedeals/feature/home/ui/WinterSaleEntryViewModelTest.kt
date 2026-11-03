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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinterSaleEntryViewModelTest {
    @Test
    fun enabled_event_appears_and_remote_disable_removes_home_entry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = FakeEvents()
        val vm = WinterSaleEntryViewModel(source)
        try {
            vm.onResume()
            runCurrent()
            assertEquals("Winter", vm.event.value?.event?.title)
            source.state = WinterSaleEventState.Hidden(WinterSaleHiddenReason.Disabled)
            advanceTimeBy(15 * 60 * 1000L)
            runCurrent()
            assertNull(vm.event.value)
        } finally {
            vm.onPause()
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class FakeEvents : WinterSaleEventRepository {
        var state: WinterSaleEventState = WinterSaleEventState.Available(
            WinterSaleEvent("winter", 0L, 0L, 10_000_000_000L, "Winter", "Before", "During"),
            WinterSalePhase.OnSale, 1_000L, false,
        )
        override suspend fun refresh() = state
    }
}
