package pm.bam.gamedeals.domain.repositories.notifications

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.NotificationGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationSyncTest {

    private val repository: NotificationsRepository = mock(MockMode.autoUnit) {
        everySuspend { getWaitlistGames(any()) } returns emptyList()
    }
    private val authTokenStore: AuthTokenStore = mock(MockMode.autoUnit)
    private val surfacedStore: SurfacedNotificationStore = mock(MockMode.autoUnit) {
        everySuspend { get() } returns emptySet()
    }

    private fun sync() = NotificationSyncImpl(repository, authTokenStore, surfacedStore)

    private fun notif(id: String, read: Boolean, type: String = "waitlist") =
        ItadNotification(id = id, type = type, title = "title-$id", timestamp = "2026-06-13T00:00:00+00:00", read = read)

    private fun loggedIn(loggedIn: Boolean) {
        everySuspend { authTokenStore.getAccessToken() } returns if (loggedIn) "token" else null
    }

    @Test
    fun logged_out_returns_empty_and_does_not_fetch() = runTest {
        loggedIn(false)

        assertTrue(sync().syncAndCollectNew().isEmpty())

        verifySuspend(exactly(0)) { repository.getNotifications() }
        verifySuspend(exactly(0)) { surfacedStore.replace(any()) }
    }

    @Test
    fun read_notifications_are_filtered_out() = runTest {
        loggedIn(true)
        everySuspend { repository.getNotifications() } returns listOf(notif("n1", read = false), notif("n2", read = true))

        val alerts = sync().syncAndCollectNew()

        assertEquals(listOf("n1"), alerts.map { it.notificationId })
    }

    @Test
    fun already_surfaced_notifications_are_filtered_out() = runTest {
        loggedIn(true)
        everySuspend { repository.getNotifications() } returns listOf(notif("n1", read = false), notif("n2", read = false))
        everySuspend { surfacedStore.get() } returns setOf("n1")

        val alerts = sync().syncAndCollectNew()

        assertEquals(listOf("n2"), alerts.map { it.notificationId })
    }

    @Test
    fun first_run_surfaces_all_unread_with_their_games() = runTest {
        loggedIn(true)
        everySuspend { repository.getNotifications() } returns listOf(notif("n1", read = false), notif("n2", read = false))
        everySuspend { repository.getWaitlistGames("n1") } returns listOf(NotificationGame("g1", "Halo"))
        everySuspend { repository.getWaitlistGames("n2") } returns listOf(NotificationGame("g2", "Hades"), NotificationGame("g3", "Celeste"))

        val alerts = sync().syncAndCollectNew()

        assertEquals(2, alerts.size)
        assertEquals(listOf(NotificationGame("g1", "Halo")), alerts.first { it.notificationId == "n1" }.games)
        assertEquals(2, alerts.first { it.notificationId == "n2" }.games.size)
    }

    @Test
    fun games_are_only_fetched_for_waitlist_type() = runTest {
        loggedIn(true)
        everySuspend { repository.getNotifications() } returns listOf(notif("n1", read = false, type = "other"))

        val alerts = sync().syncAndCollectNew()

        assertEquals(emptyList(), alerts.single().games)
        verifySuspend(exactly(0)) { repository.getWaitlistGames(any()) }
    }

    @Test
    fun surfaced_set_is_replaced_with_the_current_server_set() = runTest {
        loggedIn(true)
        everySuspend { repository.getNotifications() } returns listOf(notif("n1", read = false), notif("n2", read = true))

        sync().syncAndCollectNew()

        // Pruned to exactly the current server ids (drops anything no longer returned).
        verifySuspend(exactly(1)) { surfacedStore.replace(setOf("n1", "n2")) }
    }
}
