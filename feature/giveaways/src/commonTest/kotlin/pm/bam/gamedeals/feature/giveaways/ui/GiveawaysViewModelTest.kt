@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.giveaways.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayPlatformSelection
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayTypeSelection
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.common.time.Clock
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
import kotlin.test.assertTrue

class GiveawaysViewModelTest : MainDispatcherTest() {

    private val giveawaysRepository: GiveawaysRepository = mock(MockMode.autoUnit)

    // "Now" for the Live/Expired partition; tests that exercise end-date expiry set this before constructing the VM.
    private var now = 0L
    private val testClock = Clock { now }

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun initially_loading() = runTest {
        every { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.last().status)
    }

    @Test
    fun initially_error() = runTest {
        every { giveawaysRepository.observeGiveaways() } returns flow { throw Exception() }
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.last().status)
    }

    @Test
    fun reload_giveaways() = runTest {
        every { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        val refreshGate = CompletableDeferred<Unit>()
        everySuspend { giveawaysRepository.refreshGiveaways() } calls { refreshGate.await() }

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)

        // Subscribe before triggering reload so LOADING is observable under SharingStarted.WhileSubscribed semantics.
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

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)

        // Subscribe before triggering reload so LOADING is observable under SharingStarted.WhileSubscribed semantics.
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
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)
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
        everySuspend { giveawaysRepository.refreshGiveaways() } calls { throw Exception() }

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)
        unfilteredRoom.emit(emptyList())

        viewModel.reloadGiveaways()

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.last().status)
    }

    @Test
    fun cancelled_reload_does_not_set_ERROR() = runTest {
        val unfilteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        every { giveawaysRepository.observeGiveaways() } returns unfilteredRoom
        // Simulate an in-flight reload being cancelled (e.g. viewModelScope cleared while refreshGiveaways() is suspended). The CancellationException must
        // propagate, not be swallowed into a RefreshOutcome.Error.
        everySuspend { giveawaysRepository.refreshGiveaways() } calls { throw CancellationException("scope cleared") }

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)
        unfilteredRoom.emit(emptyList())

        viewModel.reloadGiveaways()

        val emissions = observeStates(viewModel)
        // Pre-fix this would be ERROR (the bare catch swallowed the CE and wrote RefreshOutcome.Error). After the fix the CE is rethrown and
        // refreshOutcomeFlow stays Idle, so the surviving status must not be ERROR.
        assertNotEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.last().status)
    }

    @Test
    fun ERROR_from_failed_reload_survives_subsequent_Room_invalidation() = runTest {
        val giveaway = giveaway(id = 1)
        val unfilteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        every { giveawaysRepository.observeGiveaways() } returns unfilteredRoom
        everySuspend { giveawaysRepository.refreshGiveaways() } calls { throw Exception() }

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)
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

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)
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
        every { giveawaysRepository.observeGiveaways() } returns flow { throw Exception() }
        every { giveawaysRepository.observeGiveaways(any()) } returns filteredRoom

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)

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

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)

        unfilteredRoom.emit(listOf(unfilteredOne, unfilteredTwo))

        viewModel.loadGiveaway(para)
        filteredRoom.emit(listOf(filteredOne))

        unfilteredRoom.emit(listOf(unfilteredOne, unfilteredTwo))

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.last().status)
        assertEquals(1, emissions.last().giveaways.size)
        assertEquals(filteredOne, emissions.last().giveaways.first())
    }


    @Test
    fun rapid_reloadGiveaways_calls_each_trigger_refreshGiveaways() = runTest {
        val unfilteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        every { giveawaysRepository.observeGiveaways() } returns unfilteredRoom
        var refreshCount = 0
        everySuspend { giveawaysRepository.refreshGiveaways() } calls { refreshCount++ }

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)
        unfilteredRoom.emit(emptyList())
        observeStates(viewModel)

        // reloadGiveaways launches into viewModelScope each time; there is no cancellation gate, so three rapid invocations must each reach refreshGiveaways.
        // Confirms we don't accidentally introduce a flatMapLatest-style collapse on the reload path.
        viewModel.reloadGiveaways()
        viewModel.reloadGiveaways()
        viewModel.reloadGiveaways()
        runCurrent()

        assertEquals(3, refreshCount)
    }

    @Test
    fun loadGiveaway_then_a_refresh_failure_keeps_filtered_results_visible_while_emitting_ERROR() = runTest {
        val filteredResult = giveaway(id = 7, status = "Active")
        val filteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        every { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        every { giveawaysRepository.observeGiveaways(any()) } returns filteredRoom
        everySuspend { giveawaysRepository.refreshGiveaways() } calls { throw Exception() }

        val para = GiveawaySearchParameters(
            types = persistentListOf(GiveawayTypeSelection(GiveawayType.GAME, true)),
            platforms = persistentListOf(GiveawayPlatformSelection(GiveawayPlatform.PC, true)),
            sortBy = GiveawaySortBy.DATE
        )

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)
        viewModel.loadGiveaway(para)
        filteredRoom.emit(listOf(filteredResult))

        viewModel.reloadGiveaways()
        runCurrent()

        val emissions = observeStates(viewModel)
        // Even with the ERROR status flag, the most recently observed list must remain visible — a stale-data-vs-fresh-error pattern that the screen relies
        // on to show "couldn't refresh, here's what we last had".
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.last().status)
        assertEquals(1, emissions.last().giveaways.size)
        assertEquals(filteredResult, emissions.last().giveaways.first())
    }

    @Test
    fun only_live_giveaways_are_shown_and_expired_are_hidden() = runTest {
        val live = giveaway(id = 1, status = "Active", endDate = null)
        val expired = giveaway(id = 2, status = "Expired", endDate = null)
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf(live, expired))

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)

        val emissions = observeStates(viewModel)
        assertEquals(1, emissions.last().giveaways.size)
        assertEquals(live, emissions.last().giveaways.first())
    }

    @Test
    fun an_active_giveaway_past_its_end_date_is_hidden() = runTest {
        // now = year 2100, so the 2020 end date is in the past and the item is no longer "Live".
        now = 4_102_444_800_000L
        val pastEnd = giveaway(id = 1, status = "Active", endDate = "2020-01-01 00:00:00")
        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf(pastEnd))

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository, testDatetimeParsing, testClock)

        val emissions = observeStates(viewModel)
        assertTrue(emissions.last().giveaways.isEmpty())
    }

    private fun TestScope.observeStates(viewModel: GiveawaysViewModel) =
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
}
