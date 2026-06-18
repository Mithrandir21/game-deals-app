@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.models.NotificationDetail
import pm.bam.gamedeals.domain.models.NotificationShopDeal
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.feature.account.ui.NotificationDetailViewModel.NotificationDetailEvent
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationDetailViewModelTest : MainDispatcherTest() {

    private val notificationsRepository: NotificationsRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel(notificationId: String? = "n1") =
        NotificationDetailViewModel(
            savedStateHandle = SavedStateHandle(buildMap { notificationId?.let { put("notificationId", it) } }),
            notificationsRepository = notificationsRepository,
            logger = logger,
        )

    private fun deal(shop: String, price: Double) =
        NotificationShopDeal(shopName = shop, salePriceValue = price, salePriceDenominated = "$price", regularPriceDenominated = null, cutPercent = 50, url = "u")

    @Test
    fun open_loads_detail_and_marks_read() = runTest {
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns
            NotificationDetail("n1", listOf(NotificationDealGame(gameId = "g1", title = "Halo", deals = listOf(deal("GOG", 9.99)))))

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertEquals(1, vm.uiState.value.games.size)
        assertEquals("GOG", vm.uiState.value.games.single().bestDeal?.shopName)
        verifySuspend(exactly(1)) { notificationsRepository.markRead("n1") } // opening the detail is the read action
    }

    @Test
    fun expired_game_is_kept_in_state() = runTest {
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns
            NotificationDetail("n1", listOf(NotificationDealGame(gameId = "g1", title = "Halo", deals = emptyList())))

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.games.single().isExpired)
    }

    @Test
    fun onToggleExpanded_toggles_the_game_id() = runTest {
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns NotificationDetail("n1", emptyList())

        val vm = viewModel()
        advanceUntilIdle()

        vm.onToggleExpanded("g1")
        assertTrue("g1" in vm.uiState.value.expandedGameIds)
        vm.onToggleExpanded("g1")
        assertFalse("g1" in vm.uiState.value.expandedGameIds)
    }

    @Test
    fun onOpenGame_emits_OpenGame() = runTest {
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns NotificationDetail("n1", emptyList())

        val vm = viewModel()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)
        advanceUntilIdle()

        vm.onOpenGame("g7")
        advanceUntilIdle()

        assertEquals(NotificationDetailEvent.OpenGame("g7"), events.last())
    }

    @Test
    fun missing_id_finishes_loading_without_fetching() = runTest {
        val vm = viewModel(notificationId = null)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        verifySuspend(exactly(0)) { notificationsRepository.getNotificationDetail(any()) }
    }
}
