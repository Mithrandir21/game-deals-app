package pm.bam.gamedeals.notifications

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behavioural coverage for the process-level notification deep-link hand-off (#288). Pins the contract the
 * `NavGraph` collector relies on: a tap delivered before anyone is collecting (cold start) is held until the
 * first subscriber, each route is consumed exactly once (no replay on re-subscription / config change), and
 * deliveries are received in order.
 *
 * [NotificationRouteBus] is a process-global `object`, so its channel is shared across the JVM — each test
 * therefore consumes exactly what it delivers, leaving the bus drained for the next one.
 */
class NotificationRouteBusTest {

    @Test
    fun deliver_before_collect_is_buffered_then_received_on_first_subscribe() = runTest {
        // Cold-start tap: nothing is collecting yet.
        NotificationRouteBus.deliver(NotificationRoute.FollowedSeries)

        assertEquals(NotificationRoute.FollowedSeries, NotificationRouteBus.routes.first())
    }

    @Test
    fun notifications_route_round_trips() = runTest {
        NotificationRouteBus.deliver(NotificationRoute.Notifications)

        assertEquals(NotificationRoute.Notifications, NotificationRouteBus.routes.first())
    }

    @Test
    fun deliveries_are_received_in_order() = runTest {
        NotificationRouteBus.deliver(NotificationRoute.FollowedSeries)
        NotificationRouteBus.deliver(NotificationRoute.Notifications)

        assertEquals(NotificationRoute.FollowedSeries, NotificationRouteBus.routes.first())
        assertEquals(NotificationRoute.Notifications, NotificationRouteBus.routes.first())
    }

    @Test
    fun a_received_route_is_consumed_once_and_not_replayed() = runTest {
        NotificationRouteBus.deliver(NotificationRoute.FollowedSeries)

        assertEquals(NotificationRoute.FollowedSeries, NotificationRouteBus.routes.first())

        // A re-subscription (e.g. after a config change) must not see the already-consumed route again.
        val replayed = withTimeoutOrNull(1_000) { NotificationRouteBus.routes.first() }
        assertNull(replayed)
    }
}
