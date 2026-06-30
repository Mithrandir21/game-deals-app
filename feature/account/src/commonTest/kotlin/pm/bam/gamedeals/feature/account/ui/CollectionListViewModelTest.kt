@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Collection list renders the cached snapshot, filters by the live id set, and sorts in-memory. */
class CollectionListViewModelTest : MainDispatcherTest() {

    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val storesRepository: StoresRepository = mock(MockMode.autoUnit)
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit)
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit)
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = CollectionListViewModel(
        collectionRepository, gamesRepository, storesRepository,
        waitlistRepository, ignoredRepository, dealShareTextBuilder, logger,
    )

    private fun stubs(snapshot: List<CollectionEntry>?, ids: Set<String>) {
        every { collectionRepository.observeCollectionDisplay() } returns flowOf(snapshot)
        every { collectionRepository.observeCollectionIds() } returns flowOf(ids.toPersistentSet())
        // The peek delegate observes these id sets at construction (for the sheet's icon states).
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf())
        every { ignoredRepository.observeIgnoredIds() } returns flowOf(persistentSetOf())
    }

    private val snapshot = listOf(
        CollectionEntry("a", "Alpha", type = "game", addedEpochMs = 2L),
        CollectionEntry("b", "Beta", type = "dlc", addedEpochMs = 1L),
    )

    @Test
    fun cached_snapshot_renders_sorted_by_recently_added() = runTest {
        stubs(snapshot, setOf("a", "b"))

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(CollectionSort.RECENTLY_ADDED, state.sort)
        assertEquals(listOf("a", "b"), state.rows.map { it.gameId }) // addedEpochMs desc
    }

    @Test
    fun empty_id_set_yields_empty_rows() = runTest {
        stubs(snapshot, emptySet())

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.rows.isEmpty())
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun removed_id_is_filtered_out_of_the_snapshot() = runTest {
        stubs(snapshot, setOf("a"))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.uiState.value.rows.map { it.gameId })
    }

    @Test
    fun set_sort_orders_by_title() = runTest {
        stubs(
            listOf(
                CollectionEntry("a", "Zelda", addedEpochMs = 2L),
                CollectionEntry("b", "Alpha", addedEpochMs = 1L),
            ),
            setOf("a", "b"),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSort(CollectionSort.TITLE_AZ)
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), vm.uiState.value.rows.map { it.gameId }) // Alpha before Zelda
    }
}
