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
import pm.bam.gamedeals.domain.models.FollowedFranchise
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FollowedSeriesViewModelTest : MainDispatcherTest() {

    private val followedRepository: FollowedFranchiseRepository = mock(MockMode.autoUnit)
    private val igdbRepository: IgdbRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest
    fun setUp() {
        installMainDispatcher()
        every { followedRepository.observeFollowed() } returns flowOf(emptyList())
        everySuspend { igdbRepository.fetchFranchiseGames(any(), any()) } returns emptyList()
    }

    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = FollowedSeriesViewModel(followedRepository, igdbRepository, logger)

    private fun franchise(id: Long, name: String, addedAtMs: Long) =
        FollowedFranchise(franchiseId = id, name = name, addedAtMs = addedAtMs)

    private fun igdbGame(id: Long, name: String) = IgdbGame(id = id, name = name, summary = null)

    @Test
    fun init_loads_followed_franchises_with_their_games() = runTest {
        every { followedRepository.observeFollowed() } returns flowOf(listOf(franchise(1L, "Halo", addedAtMs = 0L)))
        everySuspend { igdbRepository.fetchFranchiseGames(any(), any()) } returns
            listOf(igdbGame(10L, "Halo 5"), igdbGame(11L, "Halo Infinite"))

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertEquals(1, vm.uiState.value.items.size)
        val item = vm.uiState.value.items.first()
        assertEquals("Halo", item.name)
        assertEquals(2, item.games.size)
        assertEquals(10L, item.games.first().igdbGameId)
    }

    @Test
    fun followed_franchises_are_ordered_newest_first() = runTest {
        every { followedRepository.observeFollowed() } returns
            flowOf(listOf(franchise(1L, "Older", addedAtMs = 100L), franchise(2L, "Newer", addedAtMs = 200L)))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Newer", "Older"), vm.uiState.value.items.map { it.name })
    }

    @Test
    fun unfollow_removes_via_the_repository() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.unfollow(7L)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { followedRepository.remove(7L) }
    }
}
