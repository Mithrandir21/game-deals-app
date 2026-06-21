package pm.bam.gamedeals.domain.repositories.notifications

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.models.NotificationDetail
import pm.bam.gamedeals.domain.models.NotificationGame
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.source.ItadAccountSource
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationsRepositoryTest {

    private val accountSource: ItadAccountSource = mock(MockMode.autoUnit)
    private val authTokenStore: AuthTokenStore = mock(MockMode.autoUnit)

    // Fixed "now" one day after the sample notifications, so they sit inside the 7-day retention window.
    private val now = Instant.parse("2026-06-13T00:00:00Z").toEpochMilliseconds()
    private val clock = Clock { now }

    private fun repo() = NotificationsRepositoryImpl(accountSource, authTokenStore, clock)

    private fun notification(id: String, read: Boolean) =
        ItadNotification(id = id, type = "waitlist", title = id, timestamp = "2026-06-12T00:00:00+00:00", read = read)

    private fun loggedIn(loggedIn: Boolean) {
        every { authTokenStore.observeAuthState() } returns
            flowOf(if (loggedIn) AuthState.LoggedIn("user") else AuthState.LoggedOut)
        everySuspend { authTokenStore.getAccessToken() } returns if (loggedIn) "token" else null
    }

    @Test
    fun unread_count_is_zero_when_logged_out() = runTest {
        loggedIn(false)

        val repo = repo()
        repo.getNotifications() // no-op when logged out

        assertEquals(0, repo.observeUnreadCount().first())
    }

    @Test
    fun unread_count_reflects_loaded_unread() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotifications() } returns
            listOf(notification("n1", read = false), notification("n2", read = false), notification("n3", read = true))

        val repo = repo()
        repo.getNotifications()

        assertEquals(2, repo.observeUnreadCount().first())
    }

    @Test
    fun getNotifications_drops_anything_older_than_seven_days_even_if_unread() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotifications() } returns listOf(
            // now = 2026-06-13: 1 day old (kept) and 8 days old (dropped, despite being unread).
            ItadNotification("recent", "waitlist", "recent", "2026-06-12T00:00:00+00:00", read = false),
            ItadNotification("stale", "waitlist", "stale", "2026-06-05T00:00:00+00:00", read = false),
        )

        val repo = repo()
        val loaded = repo.getNotifications()

        assertEquals(listOf("recent"), loaded.map { it.id })
        assertEquals(emptyList(), repo.observeNotifications().first().filter { it.id == "stale" })
        assertEquals(1, repo.observeUnreadCount().first()) // the stale unread one no longer counts
    }

    @Test
    fun markRead_marks_one_and_calls_remote() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotifications() } returns
            listOf(notification("n1", read = false), notification("n2", read = false))

        val repo = repo()
        repo.getNotifications()
        repo.markRead("n1")

        assertEquals(1, repo.observeUnreadCount().first())
        verifySuspend(exactly(1)) { accountSource.markNotificationRead("n1") }
    }

    @Test
    fun markAllRead_zeroes_unread_and_calls_remote() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotifications() } returns
            listOf(notification("n1", read = false), notification("n2", read = false))

        val repo = repo()
        repo.getNotifications()
        repo.markAllRead()

        assertEquals(0, repo.observeUnreadCount().first())
        verifySuspend(exactly(1)) { accountSource.markAllNotificationsRead() }
    }

    @Test
    fun writes_are_no_ops_when_logged_out() = runTest {
        loggedIn(false)

        val repo = repo()
        repo.markRead("n1")
        repo.markAllRead()

        verifySuspend(exactly(0)) { accountSource.markNotificationRead("n1") }
        verifySuspend(exactly(0)) { accountSource.markAllNotificationsRead() }
    }

    @Test
    fun getWaitlistGames_returns_source_games_when_logged_in() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getWaitlistNotificationGames("n1") } returns
            listOf(NotificationGame("g1", "Halo"), NotificationGame("g2", "Hades"))

        assertEquals(
            listOf(NotificationGame("g1", "Halo"), NotificationGame("g2", "Hades")),
            repo().getWaitlistGames("n1"),
        )
    }

    @Test
    fun getWaitlistGames_is_empty_when_logged_out_without_calling_remote() = runTest {
        loggedIn(false)

        assertEquals(emptyList(), repo().getWaitlistGames("n1"))

        verifySuspend(exactly(0)) { accountSource.getWaitlistNotificationGames(any()) }
    }

    @Test
    fun getNotificationDetail_joins_waitlist_art_and_caches() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getWaitlistNotificationDetail("n1") } returns
            NotificationDetail("n1", listOf(NotificationDealGame(gameId = "g1", title = "Halo")))
        everySuspend { accountSource.getWaitlist() } returns
            listOf(WaitlistEntry(gameId = "g1", title = "Halo", artwork = GameArtwork(banner400 = "art-g1")))

        val repo = repo()
        val first = repo.getNotificationDetail("n1")
        val second = repo.getNotificationDetail("n1")

        assertEquals("art-g1", first.games.single().artwork.banner400) // art joined by game id
        assertEquals(first, second)                                    // served from cache
        verifySuspend(exactly(1)) { accountSource.getWaitlistNotificationDetail("n1") }
        verifySuspend(exactly(1)) { accountSource.getWaitlist() }
    }

    @Test
    fun getNotificationDetail_is_empty_when_logged_out_without_calling_remote() = runTest {
        loggedIn(false)

        assertEquals(NotificationDetail("n1", emptyList()), repo().getNotificationDetail("n1"))

        verifySuspend(exactly(0)) { accountSource.getWaitlistNotificationDetail(any()) }
    }
}
