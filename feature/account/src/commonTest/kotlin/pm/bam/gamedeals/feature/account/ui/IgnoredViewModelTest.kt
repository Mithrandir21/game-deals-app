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
import pm.bam.gamedeals.domain.models.IgnoredEntry
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Ignored-games sub-screen reacts to the auth-gated id set; un-ignore is remote-first and flows back. */
class IgnoredViewModelTest : MainDispatcherTest() {

    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = IgnoredViewModel(ignoredRepository, logger)

    @Test
    fun logged_in_id_set_populates_the_list_from_the_reconcile() = runTest {
        every { ignoredRepository.observeIgnoredIds() } returns flowOf(persistentSetOf("a", "b"))
        everySuspend { ignoredRepository.getIgnored() } returns listOf(IgnoredEntry("a", "A"), IgnoredEntry("b", "B"))

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf("a", "b"), state.items.map { it.gameId })
    }

    @Test
    fun empty_id_set_yields_an_empty_list_without_a_network_fetch() = runTest {
        every { ignoredRepository.observeIgnoredIds() } returns flowOf(persistentSetOf())

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.loading)
        verifySuspend(exactly(0)) { ignoredRepository.getIgnored() }
    }

    @Test
    fun unignore_when_the_id_leaves_the_set_drops_the_row_without_refetching() = runTest {
        // toggleIgnored removes the id from the Room set; the observer re-emits the smaller set and re-filters.
        every { ignoredRepository.observeIgnoredIds() } returns flowOf(persistentSetOf("a", "b"), persistentSetOf("a"))
        everySuspend { ignoredRepository.getIgnored() } returns listOf(IgnoredEntry("a", "A"), IgnoredEntry("b", "B"))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.uiState.value.items.map { it.gameId })
        verifySuspend(exactly(1)) { ignoredRepository.getIgnored() }
    }

    @Test
    fun onUnignore_delegates_to_the_repository() = runTest {
        every { ignoredRepository.observeIgnoredIds() } returns flowOf(persistentSetOf("a"))
        everySuspend { ignoredRepository.getIgnored() } returns listOf(IgnoredEntry("a", "A"))
        everySuspend { ignoredRepository.toggleIgnored("a") } returns RepoUpdateResult.UPDATED

        val vm = viewModel()
        advanceUntilIdle()
        vm.onUnignore("a")
        advanceUntilIdle()

        verifySuspend(exactly(1)) { ignoredRepository.toggleIgnored("a") }
    }
}
