@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.discover.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.domain.models.TagDiscoveryResult
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.repositories.discovery.DISCOVERY_PAGE_SIZE
import pm.bam.gamedeals.domain.repositories.discovery.DiscoveryPage
import pm.bam.gamedeals.domain.repositories.discovery.TagDiscoveryRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.feature.discover.ui.DiscoverResultsViewModel.ResultsScreenData
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoverResultsViewModelTest : MainDispatcherTest() {

    private val tagDiscoveryRepository: TagDiscoveryRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val storesRepository: StoresRepository = mock(MockMode.autoUnit) {
        every { observeStores() } returns flowOf(emptyList())
    }
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        every { observeWaitlistIds() } returns flowOf(persistentSetOf())
    }
    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit) {
        every { observeCollectionIds() } returns flowOf(persistentSetOf())
    }
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit) {
        every { observeIgnoredIds() } returns flowOf(persistentSetOf())
    }
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel(args: Map<String, Any?> = mapOf("genreIds" to "12")) =
        DiscoverResultsViewModel(
            TestingLoggingListener(), tagDiscoveryRepository, gamesRepository, storesRepository,
            waitlistRepository, collectionRepository, ignoredRepository, dealShareTextBuilder, SavedStateHandle(args),
        )

    @Test
    fun first_page_loads_into_data_with_endReached_from_the_page() = runTest {
        everySuspend { tagDiscoveryRepository.discover(any(), any(), any()) } returns
            DiscoveryPage(results = listOf(result(1L), result(2L)), nextOffset = 2, endReached = true)

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ResultsScreenData.Status.DATA, state.status)
        assertEquals(2, state.results.size)
        assertTrue(state.endReached)
    }

    @Test
    fun empty_first_page_surfaces_empty_status() = runTest {
        everySuspend { tagDiscoveryRepository.discover(any(), any(), any()) } returns
            DiscoveryPage(results = emptyList(), nextOffset = 30, endReached = true)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(ResultsScreenData.Status.EMPTY, vm.uiState.value.status)
    }

    @Test
    fun load_more_appends_next_page_resuming_from_the_pages_igdb_cursor() = runTest {
        val full = List(DISCOVERY_PAGE_SIZE) { result(it.toLong()) }
        // Page 1 reports nextOffset = 30 (not endReached); the VM must resume the next page from 30.
        everySuspend { tagDiscoveryRepository.discover(any(), any(), any()) } calls { (_: IgdbTagFilter, offset: Int, _: Int) ->
            if (offset == 0) DiscoveryPage(full, nextOffset = 30, endReached = false)
            else DiscoveryPage(listOf(result(999L)), nextOffset = 31, endReached = true)
        }

        val vm = createViewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.endReached)

        vm.loadNextPage()
        advanceUntilIdle()

        assertEquals(DISCOVERY_PAGE_SIZE + 1, vm.uiState.value.results.size)
        assertTrue(vm.uiState.value.endReached)
        verifySuspend(exactly(1)) {
            tagDiscoveryRepository.discover(IgdbTagFilter(genreIds = persistentListOf(12L)), 30, DISCOVERY_PAGE_SIZE)
        }
    }

    @Test
    fun decodes_filter_from_route_args_and_queries_with_it() = runTest {
        everySuspend { tagDiscoveryRepository.discover(any(), any(), any()) } returns
            DiscoveryPage(results = emptyList(), nextOffset = 0, endReached = true)

        createViewModel(mapOf("genreIds" to "12,5", "keywordIds" to "270"))
        advanceUntilIdle()

        verifySuspend(exactly(1)) {
            tagDiscoveryRepository.discover(
                IgdbTagFilter(genreIds = persistentListOf(12L, 5L), keywordIds = persistentListOf(270L)),
                0,
                DISCOVERY_PAGE_SIZE,
            )
        }
    }

    @Test
    fun first_page_failure_surfaces_error_status() = runTest {
        everySuspend { tagDiscoveryRepository.discover(any(), any(), any()) } throws RuntimeException("down")

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(ResultsScreenData.Status.ERROR, vm.uiState.value.status)
    }

    private fun result(id: Long): TagDiscoveryResult =
        TagDiscoveryResult(
            igdbId = id,
            gameId = "itad-$id",
            title = "Game $id",
            coverImageUrl = null,
            price = null,
        )
}
