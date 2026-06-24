@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Waitlist sub-screen reacts to the auth-gated id set (login/logout + in-place removals). */
class WaitlistListViewModelTest : MainDispatcherTest() {

    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = WaitlistListViewModel(waitlistRepository, logger)

    @Test
    fun logged_in_id_set_populates_the_list_from_the_reconcile() = runTest {
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf("a", "b"))
        everySuspend { waitlistRepository.getWaitlist() } returns listOf(WaitlistEntry("a", "A"), WaitlistEntry("b", "B"))

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf("a", "b"), state.items.map { it.gameId })
    }

    @Test
    fun empty_id_set_yields_an_empty_list_without_a_network_fetch() = runTest {
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf())

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.loading)
        verifySuspend(exactly(0)) { waitlistRepository.getWaitlist() }
    }

    @Test
    fun a_removal_refilters_the_cache_without_refetching() = runTest {
        // The set shrinks (e.g. un-waitlisted from the peek sheet): the cache already covers it, so no refetch.
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf("a", "b"), persistentSetOf("a"))
        everySuspend { waitlistRepository.getWaitlist() } returns listOf(WaitlistEntry("a", "A"), WaitlistEntry("b", "B"))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.uiState.value.items.map { it.gameId })
        verifySuspend(exactly(1)) { waitlistRepository.getWaitlist() }
    }
}
