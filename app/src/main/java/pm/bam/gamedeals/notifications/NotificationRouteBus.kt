package pm.bam.gamedeals.notifications

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Where tapping a delivered system notification should land the user (#7 notification revamp). */
sealed interface NotificationRoute {
    /** The bundled waitlist summary opens the (day-grouped) Notifications list. */
    data object Notifications : NotificationRoute
    /** The bundled followed-franchise summary opens the Followed-series screen (current franchise sales). */
    data object FollowedSeries : NotificationRoute
}

/**
 * Process-level hand-off from a tapped notification (parsed in [MainActivity][pm.bam.gamedeals.MainActivity])
 * to the Compose `NavGraph`, mirroring `AuthRedirectBus`. Backed by a buffered [Channel] (not a replaying
 * flow) so a **cold-start** tap is held until `NavGraph` subscribes, yet is consumed exactly once — a
 * config change / re-subscription won't re-navigate.
 */
object NotificationRouteBus {
    private val channel = Channel<NotificationRoute>(Channel.BUFFERED)
    val routes: Flow<NotificationRoute> = channel.receiveAsFlow()

    fun deliver(route: NotificationRoute) {
        channel.trySend(route)
    }
}
