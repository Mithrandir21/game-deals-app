@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.giveaways.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayPlatformSelection
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayTypeSelection
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.giveaway
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GiveawaysViewModelTest : MainDispatcherTest() {

    private val giveawaysRepository: GiveawaysRepository = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun initially_loading() = runTest {
        every { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.last().status)
    }

    @Test
    fun initially_error() = runTest {
        every { giveawaysRepository.observeGiveaways() } throws Exception()
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.last().status)
    }

    @Test
    fun reload_giveaways() = runTest {
        every { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        val refreshGate = CompletableDeferred<Unit>()
        everySuspend { giveawaysRepository.refreshGiveaways() } calls { refreshGate.await() }

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        // Subscribe before triggering reload so LOADING is observable under
        // SharingStarted.WhileSubscribed semantics.
        val emissions = observeStates(viewModel)
        viewModel.reloadGiveaways()

        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.LOADING, emissions.last().status)

        refreshGate.complete(Unit)
    }

    @Test
    fun reload_giveaways_emits_LOADING_before_refresh_completes() = runTest {
        every { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        val refreshGate = CompletableDeferred<Unit>()
        everySuspend { giveawaysRepository.refreshGiveaways() } calls { refreshGate.await() }

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        // Subscribe before triggering reload so LOADING is observable under
        // SharingStarted.WhileSubscribed semantics.
        val emissions = observeStates(viewModel)
        viewModel.reloadGiveaways()

        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.LOADING, emissions.last().status)

        refreshGate.complete(Unit)
    }

    @Test
    fun load_giveaways_with_search_parameters() = runTest {
        val resultOne = giveaway(id = 1)
        val resultTwo = giveaway(id = 2)
        val resultThree = giveaway(id = 3)

        val para = GiveawaySearchParameters(
            types = persistentListOf(GiveawayTypeSelection(GiveawayType.GAME, true), GiveawayTypeSelection(GiveawayType.BETA, true)),
            platforms = persistentListOf(GiveawayPlatformSelection(GiveawayPlatform.PC, true), GiveawayPlatformSelection(GiveawayPlatform.NINTENDO_SWITCH, true)),
            sortBy = GiveawaySortBy.DATE
        )

        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf(resultOne, resultTwo, resultThree))
        every { giveawaysRepository.observeGiveaways(any()) } returns flowOf(listOf(resultThree, resultOne))
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        viewModel.loadGiveaway(para)

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.last().status)
        assertEquals(2, emissions.last().giveaways.size)
        assertEquals(resultThree, emissions.last().giveaways.first())
        assertEquals(resultOne, emissions.last().giveaways.second())
    }

    @Test
    fun failed_reload_sets_ERROR() = runTest {
        val unfilteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        every { giveawaysRepository.observeGiveaways() } returns unfilteredRoom
        everySuspend { giveawaysRepository.refreshGiveaways() } throws Exception()

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        unfilteredRoom.emit(emptyList())

        viewModel.reloadGiveaways()

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.last().status)
    }

    @Test
    fun cancelled_reload_does_not_set_ERROR() = runTest {
        val unfilteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        every { giveawaysRepository.observeGiveaways() } returns unfilteredRoom
        // Simulate an in-flight reload being cancelled (e.g. viewModelScope cleared while
        // refreshGiveaways() is suspended). The CancellationException must propagate, not be
        // swallowed into a RefreshOutcome.Error.
        everySuspend { giveawaysRepository.refreshGiveaways() } throws CancellationException("scope cleared")

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        unfilteredRoom.emit(emptyList())

        viewModel.reloadGiveaways()

        val emissions = observeStates(viewModel)
        // Pre-fix this would be ERROR (the bare catch swallowed the CE and wrote
        // RefreshOutcome.Error). After the fix the CE is rethrown and refreshOutcomeFlow
        // stays Idle, so the surviving status must not be ERROR.
        assertNotEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.last().status)
    }

    @Test
    fun ERROR_from_failed_reload_survives_subsequent_Room_invalidation() = runTest {
        val giveaway = giveaway(id = 1)
        val unfilteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        every { giveawaysRepository.observeGiveaways() } returns unfilteredRoom
        everySuspend { giveawaysRepository.refreshGiveaways() } throws Exception()

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        unfilteredRoom.emit(emptyList())

        viewModel.reloadGiveaways()

        // Simulate a Room invalidation arriving after the refresh has already failed.
        unfilteredRoom.emit(listOf(giveaway))

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.last().status)
    }

    @Test
    fun successful_reload_after_a_failure_flips_ERROR_back_to_SUCCESS() = runTest {
        val giveaway = giveaway(id = 1)
        val unfilteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        every { giveawaysRepository.observeGiveaways() } returns unfilteredRoom
        var refreshCallCount = 0
        everySuspend { giveawaysRepository.refreshGiveaways() } calls {
            refreshCallCount++
            if (refreshCallCount == 1) throw Exception()
        }

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        unfilteredRoom.emit(emptyList())

        viewModel.reloadGiveaways()
        // Pre-condition: first refresh failed → ERROR.
        assertEquals(
            GiveawaysViewModel.GiveawaysScreenStatus.ERROR,
            observeStates(viewModel).last().status
        )

        viewModel.reloadGiveaways()
        // The successful refresh write triggers a Room invalidation in production; model it here.
        unfilteredRoom.emit(listOf(giveaway))

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.last().status)
        assertEquals(1, emissions.last().giveaways.size)
    }

    @Test
    fun upstream_collector_survives_an_observeGiveaways_failure_and_processes_later_emissions() = runTest {
        val giveaway = giveaway(id = 1)
        val filteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        every { giveawaysRepository.observeGiveaways() } throws Exception()
        every { giveawaysRepository.observeGiveaways(any()) } returns filteredRoom

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.last().status)

        val para = GiveawaySearchParameters(
            types = persistentListOf(GiveawayTypeSelection(GiveawayType.GAME, true)),
            platforms = persistentListOf(GiveawayPlatformSelection(GiveawayPlatform.PC, true)),
            sortBy = GiveawaySortBy.DATE
        )
        viewModel.loadGiveaway(para)
        filteredRoom.emit(listOf(giveaway))

        assertEquals(1, emissions.last().giveaways.size)
        assertEquals(giveaway, emissions.last().giveaways.first())
    }

    @Test
    fun load_giveaways_cancels_prior_unfiltered_collector_when_filter_applied() = runTest {
        val unfilteredOne = giveaway(id = 1)
        val unfilteredTwo = giveaway(id = 2)
        val filteredOne = giveaway(id = 3)

        val para = GiveawaySearchParameters(
            types = persistentListOf(GiveawayTypeSelection(GiveawayType.GAME, true)),
            platforms = persistentListOf(GiveawayPlatformSelection(GiveawayPlatform.PC, true)),
            sortBy = GiveawaySortBy.DATE
        )

        // Model the hot Room Flows: each invalidation re-emits, the flow never completes.
        val unfilteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        val filteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        every { giveawaysRepository.observeGiveaways() } returns unfilteredRoom
        every { giveawaysRepository.observeGiveaways(any()) } returns filteredRoom

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        unfilteredRoom.emit(listOf(unfilteredOne, unfilteredTwo))

        viewModel.loadGiveaway(para)
        filteredRoom.emit(listOf(filteredOne))

        unfilteredRoom.emit(listOf(unfilteredOne, unfilteredTwo))

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.last().status)
        assertEquals(1, emissions.last().giveaways.size)
        assertEquals(filteredOne, emissions.last().giveaways.first())
    }


    private fun TestScope.observeStates(viewModel: GiveawaysViewModel) =
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
}
