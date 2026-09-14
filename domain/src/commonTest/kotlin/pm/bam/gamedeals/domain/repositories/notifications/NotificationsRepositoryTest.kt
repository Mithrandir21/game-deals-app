package pm.bam.gamedeals.domain.repositories.notifications

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
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
import pm.bam.gamedeals.domain.RecordingAnalytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationsRepositoryTest {

    private val accountSource: ItadAccountSource = mock(MockMode.autoUnit)
    private val authTokenStore: AuthTokenStore = mock(MockMode.autoUnit)
    private val analytics = RecordingAnalytics()

    // Fixed "now" one day after the sample notifications, so they sit inside the 7-day retention window.
    private val now = Instant.parse("2026-06-13T00:00:00Z").toEpochMilliseconds()
    private val clock = Clock { now }

    private fun repo() = NotificationsRepositoryImpl(accountSource, authTokenStore, clock, analytics)

    private fun notification(id: String, read: Boolean) =
        ItadNotification(id = id, type = "waitlist", title = id, timestamp = "2026-06-12T00:00:00+00:00", read = read)

    private fun notification(id: String, read: Boolean, day: String) =
        ItadNotification(id = id, type = "waitlist", title = id, timestamp = "${day}T09:00:00+00:00", read = read)

    private fun detail(id: String, vararg gameIds: String) =
        NotificationDetail(id, gameIds.map { NotificationDealGame(gameId = it, title = it) })

    /** The list's unread entry ids — what the old entry-count tally was derived from. */
    private suspend fun NotificationsRepository.unreadIds() =
        observeNotifications().first().filterNot { it.read }.map { it.id }

    private fun loggedIn(loggedIn: Boolean) {
        every { authTokenStore.observeAuthState() } returns
            flowOf(if (loggedIn) AuthState.LoggedIn("user") else AuthState.LoggedOut)
        everySuspend { authTokenStore.getAccessToken() } returns if (loggedIn) "token" else null
    }

    @Test
    fun unread_game_count_is_zero_when_logged_out() = runTest {
        loggedIn(false)

        val repo = repo()
        repo.getNotifications() // no-op when logged out

        assertEquals(0, repo.observeUnreadGameCount().first())
    }

    @Test
    fun loaded_list_keeps_read_state() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotifications() } returns
            listOf(notification("n1", read = false), notification("n2", read = false), notification("n3", read = true))

        val repo = repo()
        repo.getNotifications()

        assertEquals(listOf("n1", "n2"), repo.unreadIds())
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
        assertEquals(listOf("recent"), repo.unreadIds()) // the stale unread one no longer counts
    }

    @Test
    fun markRead_marks_one_and_calls_remote() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotifications() } returns
            listOf(notification("n1", read = false), notification("n2", read = false))

        val repo = repo()
        repo.getNotifications()
        repo.markRead("n1")

        assertEquals(listOf("n2"), repo.unreadIds())
        verifySuspend(exactly(1)) { accountSource.markNotificationRead("n1") }
        assertTrue(analytics.events.contains(AnalyticsEvents.NOTIFICATION_MARKED_READ))
    }

    @Test
    fun markAllRead_zeroes_unread_and_calls_remote() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotifications() } returns
            listOf(notification("n1", read = false), notification("n2", read = false))

        val repo = repo()
        repo.getNotifications()
        repo.markAllRead()

        assertEquals(emptyList(), repo.unreadIds())
        verifySuspend(exactly(1)) { accountSource.markAllNotificationsRead() }
        assertTrue(analytics.events.contains(AnalyticsEvents.NOTIFICATIONS_MARKED_ALL_READ))
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

    // --- Unread-games badge: must equal the Notifications list's "· N games" summed over unread days. ---

    @Test
    fun unread_game_count_sums_distinct_games_per_unread_day() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getWaitlist() } returns emptyList()
        everySuspend { accountSource.getNotifications() } returns listOf(
            // 12 Jun (unread): g1+g2, g2+g3, and the read entry's g4 (the row lists it too) → 4 distinct games.
            notification("a", read = false, day = "2026-06-12"),
            notification("b", read = false, day = "2026-06-12"),
            notification("c", read = true, day = "2026-06-12"),
            // 11 Jun (unread): g1 again — a different row, so it counts again → 1.
            notification("d", read = false, day = "2026-06-11"),
            // 10 Jun (all read): not an unread row → 0.
            notification("e", read = true, day = "2026-06-10"),
        )
        everySuspend { accountSource.getWaitlistNotificationDetail("a") } returns detail("a", "g1", "g2")
        everySuspend { accountSource.getWaitlistNotificationDetail("b") } returns detail("b", "g2", "g3")
        everySuspend { accountSource.getWaitlistNotificationDetail("c") } returns detail("c", "g4")
        everySuspend { accountSource.getWaitlistNotificationDetail("d") } returns detail("d", "g1")

        val repo = repo()
        repo.getNotifications()
        repo.resolveUnreadGames()

        assertEquals(5, repo.observeUnreadGameCount().first()) // 12 Jun: 4 + 11 Jun: 1
        verifySuspend(exactly(0)) { accountSource.getWaitlistNotificationDetail("e") } // read-only day skipped
    }

    @Test
    fun unresolved_entries_contribute_no_games_until_resolved() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getWaitlist() } returns emptyList()
        everySuspend { accountSource.getNotifications() } returns listOf(notification("a", read = false, day = "2026-06-12"))
        everySuspend { accountSource.getWaitlistNotificationDetail("a") } returns detail("a", "g1", "g2")

        val repo = repo()
        repo.getNotifications()
        assertEquals(0, repo.observeUnreadGameCount().first())

        repo.getNotificationDetail("a") // e.g. the list screen resolving it
        assertEquals(2, repo.observeUnreadGameCount().first())
    }

    @Test
    fun marking_a_day_read_drops_its_games_from_the_count() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getWaitlist() } returns emptyList()
        everySuspend { accountSource.getNotifications() } returns listOf(
            notification("a", read = false, day = "2026-06-12"),
            notification("b", read = false, day = "2026-06-11"),
        )
        everySuspend { accountSource.getWaitlistNotificationDetail("a") } returns detail("a", "g1", "g2")
        everySuspend { accountSource.getWaitlistNotificationDetail("b") } returns detail("b", "g3")

        val repo = repo()
        repo.getNotifications()
        repo.resolveUnreadGames()
        assertEquals(3, repo.observeUnreadGameCount().first())

        repo.markRead("a")
        assertEquals(1, repo.observeUnreadGameCount().first())
    }

    @Test
    fun reload_keeps_resolved_games_so_the_badge_does_not_flicker_and_prunes_gone_entries() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getWaitlist() } returns emptyList()
        everySuspend { accountSource.getNotifications() } returns listOf(
            notification("a", read = false, day = "2026-06-12"),
            notification("b", read = false, day = "2026-06-11"),
        )
        everySuspend { accountSource.getWaitlistNotificationDetail("a") } returns detail("a", "g1", "g2")
        everySuspend { accountSource.getWaitlistNotificationDetail("b") } returns detail("b", "g3")

        val repo = repo()
        repo.getNotifications()
        repo.resolveUnreadGames()

        // The list screen's remote-as-truth reload: "b" has aged out server-side.
        everySuspend { accountSource.getNotifications() } returns listOf(notification("a", read = false, day = "2026-06-12"))
        repo.getNotifications()
        assertEquals(2, repo.observeUnreadGameCount().first()) // "a" still counted without re-resolving

        repo.resolveUnreadGames()
        verifySuspend(exactly(1)) { accountSource.getWaitlistNotificationDetail("a") } // already resolved → skipped
    }

    @Test
    fun a_failing_detail_counts_no_games_for_that_entry_only() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getWaitlist() } returns emptyList()
        everySuspend { accountSource.getNotifications() } returns listOf(
            notification("a", read = false, day = "2026-06-12"),
            notification("b", read = false, day = "2026-06-11"),
        )
        everySuspend { accountSource.getWaitlistNotificationDetail("a") } throws IllegalStateException("boom")
        everySuspend { accountSource.getWaitlistNotificationDetail("b") } returns detail("b", "g3")

        val repo = repo()
        repo.getNotifications()
        repo.resolveUnreadGames()

        assertEquals(1, repo.observeUnreadGameCount().first())
    }

    @Test
    fun resolveUnreadGames_is_a_no_op_when_logged_out() = runTest {
        loggedIn(false)

        repo().resolveUnreadGames()

        verifySuspend(exactly(0)) { accountSource.getWaitlistNotificationDetail(any()) }
    }
}
