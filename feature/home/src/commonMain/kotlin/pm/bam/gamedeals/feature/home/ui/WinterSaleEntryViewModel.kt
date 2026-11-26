package pm.bam.gamedeals.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealTotals
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder

/**
 * Home's sale card: whether the event is showing, and during the sale the deal total for its headline. The total comes
 * from store counts kept for a day, so Home asks ITAD at most once a day however often it opens.
 */
internal class WinterSaleEntryViewModel(
    private val events: WinterSaleEventRepository,
    private val reminder: WinterSaleReminder,
    private val hubs: WinterSaleHubRepository,
    private val region: RegionRepository,
) : ViewModel() {
    private val mutableEvent = MutableStateFlow<WinterSaleEventState.Available?>(null)
    val event: StateFlow<WinterSaleEventState.Available?> = mutableEvent
    private val mutableTotals = MutableStateFlow<WinterSaleDealTotals?>(null)
    val totals: StateFlow<WinterSaleDealTotals?> = mutableTotals
    private var poll: Job? = null

    fun onResume() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (isActive) {
                val state = try { events.refresh() } catch (error: CancellationException) { throw error } catch (_: Throwable) {
                    WinterSaleEventState.Hidden(pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason.Unavailable)
                }
                val available = state as? WinterSaleEventState.Available
                mutableEvent.value = available
                launch { reminder.reconcile(state) }
                if (available?.phase == WinterSalePhase.OnSale) {
                    launch { loadTotals()?.let { mutableTotals.value = it } }
                } else {
                    mutableTotals.value = null
                }
                val deadline = available?.let {
                    if (it.phase == WinterSalePhase.BeforeSale) it.event.saleStartEpochMillis else it.event.endEpochMillis
                }
                val serverNow = available?.observedServerNowEpochMillis
                delay(if (deadline != null && serverNow != null) minOf(POLL_MS, (deadline - serverNow).coerceAtLeast(1_000L)) else POLL_MS)
            }
        }
    }

    fun onPause() { poll?.cancel(); poll = null }

    /** A failed count leaves the card on its countdown (or its last total); it never hides the card. */
    private suspend fun loadTotals(): WinterSaleDealTotals? = try {
        hubs.dealTotals(region.observeSelectedCountry().first().code)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private companion object { const val POLL_MS = 15 * 60 * 1000L }
}
