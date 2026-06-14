package pm.bam.gamedeals.common.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Process-level request that the Deals tab reveal its title-search field (Search was merged into the
 * Deals tab — epic #291 follow-up). Producers are the app-shell search icon (no title) and the Game
 * Page's "no deals → search by title" deep-link (carries the [title] to prefill).
 *
 * Backed by a buffered [Channel] (mirroring [NotificationRouteBus][pm.bam.gamedeals.notifications]) so a
 * request raised while navigating to the tab is held until the Deals screen subscribes, yet is consumed
 * exactly once — a config change / re-subscription won't re-reveal.
 */
object SearchRequestBus {
    private val channel = Channel<String?>(Channel.BUFFERED)
    val requests: Flow<String?> = channel.receiveAsFlow()

    /** Request the Deals tab reveal its search field, optionally prefilled with [title]. */
    fun request(title: String? = null) {
        channel.trySend(title)
    }
}
