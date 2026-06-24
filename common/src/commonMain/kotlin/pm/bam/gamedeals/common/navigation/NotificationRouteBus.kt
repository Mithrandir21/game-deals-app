package pm.bam.gamedeals.common.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Where tapping a delivered system notification should land the user (#7 notification revamp). */
sealed interface NotificationRoute {
    /** Stable wire key carried in the notification payload (Android Intent extra / iOS `userInfo`). */
    val key: String

    /** The bundled waitlist summary opens the (day-grouped) Notifications list. */
    data object Notifications : NotificationRoute {
        override val key = "notifications"
    }

    /** The bundled followed-franchise summary opens the Followed-series screen (current franchise sales). */
    data object FollowedSeries : NotificationRoute {
        override val key = "followed_series"
    }

    companion object {
        /** Inverse of [key] — maps a payload string back to a route, or null when absent/unrecognised. */
        fun fromKey(key: String?): NotificationRoute? = when (key) {
            Notifications.key -> Notifications
            FollowedSeries.key -> FollowedSeries
            else -> null
        }
    }
}

/**
 * Process-level hand-off from a tapped notification to the shared Compose nav host, mirroring `AuthRedirectBus`.
 * The platform layer parses the tap and [delivers][deliver] a route (Android: the launch `Intent`; iOS: the app
 * delegate); the host collects [routes] and navigates. Backed by a buffered [Channel] (not a replaying flow) so a
 * cold-start tap is held until the host subscribes, yet is consumed exactly once — a config change / re-subscription
 * won't re-navigate.
 */
object NotificationRouteBus {
    private val channel = Channel<NotificationRoute>(Channel.BUFFERED)
    val routes: Flow<NotificationRoute> = channel.receiveAsFlow()

    fun deliver(route: NotificationRoute) {
        channel.trySend(route)
    }
}
