@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.models.NotificationDetail
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsViewModelTest : MainDispatcherTest() {

    private val notificationsRepository: NotificationsRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest
    fun setUp() {
        installMainDispatcher()
        every { notificationsRepository.observeNotifications() } returns flowOf(emptyList())
        everySuspend { notificationsRepository.getNotifications() } returns emptyList()
        // autoUnit only auto-returns Unit; the detail (NotificationDetail) needs an explicit default.
        everySuspend { notificationsRepository.getNotificationDetail(any()) } returns NotificationDetail("", emptyList())
    }

    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = NotificationsViewModel(notificationsRepository, logger)

    private fun notification(id: String, day: String = "2026-06-13", read: Boolean = false) =
        ItadNotification(id = id, type = "waitlist", title = id, timestamp = "${day}T00:00:00+00:00", read = read)

    private fun detail(id: String, vararg games: Pair<String, String>) =
        NotificationDetail(
            id,
            games.map { (gameId, title) ->
                // Carry art so the resolved thumbnail can be asserted (banner300 is what GameArtwork.thumbnail prefers).
                NotificationDealGame(gameId = gameId, title = title, artwork = GameArtwork(banner300 = "art-$gameId"))
            },
        )

    @Test
    fun init_groups_by_day_and_counts_distinct_games_named_in_the_title() = runTest {
        val list = listOf(
            notification("n1", day = "2026-06-13", read = false),
            notification("n2", day = "2026-06-13", read = true),
            notification("n3", day = "2026-06-12", read = true),
        )
        every { notificationsRepository.observeNotifications() } returns flowOf(list)
        everySuspend { notificationsRepository.getNotifications() } returns list
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns detail("n1", "gA" to "A")
        everySuspend { notificationsRepository.getNotificationDetail("n2") } returns detail("n2", "gA" to "A", "gB" to "B")
        everySuspend { notificationsRepository.getNotificationDetail("n3") } returns detail("n3", "gC" to "C")

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertEquals(listOf("2026-06-13", "2026-06-12"), vm.uiState.value.days.map { it.date })
        // The 13th's two entries reference gA twice + gB once → 2 distinct games, named in title order.
        assertEquals(2, vm.uiState.value.days.first().count)
        assertEquals(listOf("A", "B"), vm.uiState.value.days.first().games.map { it.title })
        // The joined waitlist art rides through to the row (no longer discarded).
        assertEquals(listOf("art-gA", "art-gB"), vm.uiState.value.days.first().games.map { it.thumbnailUrl })
        assertEquals(1, vm.uiState.value.days.last().count)
        assertEquals(listOf("C"), vm.uiState.value.days.last().games.map { it.title })
        assertTrue(vm.uiState.value.days.first().hasUnread)
        assertFalse(vm.uiState.value.days.last().hasUnread)
        assertTrue(vm.uiState.value.hasUnread)
        verifySuspend(exactly(1)) { notificationsRepository.getNotifications() }
    }

    @Test
    fun a_single_notification_referencing_two_games_counts_as_two() = runTest {
        // The original bug: one notification (one "entry") whose digest covers two games.
        val list = listOf(notification("n1"))
        every { notificationsRepository.observeNotifications() } returns flowOf(list)
        everySuspend { notificationsRepository.getNotifications() } returns list
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns detail("n1", "gA" to "A", "gB" to "B")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.days.single().count)
        assertEquals(listOf("A", "B"), vm.uiState.value.days.single().games.map { it.title })
    }

    @Test
    fun onMarkAllRead_calls_remote() = runTest {
        val vm = viewModel()

        vm.onMarkAllRead()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { notificationsRepository.markAllRead() }
    }
}
