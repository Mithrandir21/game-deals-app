package pm.bam.gamedeals.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder

internal class WinterSaleEntryViewModel(
    private val events: WinterSaleEventRepository,
    private val reminder: WinterSaleReminder,
) : ViewModel() {
    private val mutableEvent = MutableStateFlow<WinterSaleEventState.Available?>(null)
    val event: StateFlow<WinterSaleEventState.Available?> = mutableEvent
    private var poll: Job? = null

    fun onResume() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (isActive) {
                val state = try { events.refresh() } catch (error: CancellationException) { throw error } catch (_: Throwable) {
                    WinterSaleEventState.Hidden(pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason.Unavailable)
                }
                mutableEvent.value = state as? WinterSaleEventState.Available
                launch { reminder.reconcile(state) }
                val deadline = (state as? WinterSaleEventState.Available)?.let {
                    if (it.phase == WinterSalePhase.BeforeSale) it.event.saleStartEpochMillis else it.event.endEpochMillis
                }
                val serverNow = (state as? WinterSaleEventState.Available)?.observedServerNowEpochMillis
                delay(if (deadline != null && serverNow != null) minOf(POLL_MS, (deadline - serverNow).coerceAtLeast(1_000L)) else POLL_MS)
            }
        }
    }

    fun onPause() { poll?.cancel(); poll = null }

    private companion object { const val POLL_MS = 15 * 60 * 1000L }
}
