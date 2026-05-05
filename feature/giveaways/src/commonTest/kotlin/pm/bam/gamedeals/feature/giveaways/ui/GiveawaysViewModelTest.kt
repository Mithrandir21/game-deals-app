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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GiveawaysViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val giveawaysRepository: GiveawaysRepository = mock(MockMode.autoUnit)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initially_loading() = runTest {
        every { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        val emissions = observeStates(viewModel)
        assertEquals(1, emissions.size)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.first().status)
    }

    @Test
    fun initially_error() = runTest {
        every { giveawaysRepository.observeGiveaways() } throws Exception()
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        val emissions = observeStates(viewModel)
        assertEquals(1, emissions.size)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.first().status)
    }

    @Test
    fun reload_giveaways() = runTest {
        every { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        viewModel.reloadGiveaways()

        val emissions = observeStates(viewModel)
        assertEquals(1, emissions.size)

        // Loading is emitted twice, but observed only once because StateFlow emits only distinct values
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.LOADING, emissions.first().status)
    }

    @Test
    fun reload_giveaways_emits_LOADING_before_refresh_completes() = runTest {
        every { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        val refreshGate = CompletableDeferred<Unit>()
        everySuspend { giveawaysRepository.refreshGiveaways() } calls { refreshGate.await() }

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        viewModel.reloadGiveaways()

        val emissions = observeStates(viewModel)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.LOADING, emissions.last().status)

        refreshGate.complete(Unit)
    }

    @Test
    fun load_giveaways_with_search_parameters() = runTest {
        val resultOne = giveaway(id = 1)
        val resultTwo = giveaway(id = 2)
        val resultThree = giveaway(id = 3)

        val para = GiveawaySearchParameters(
            types = persistentListOf(GiveawayType.GAME to true, GiveawayType.BETA to true),
            platforms = persistentListOf(GiveawayPlatform.PC to true, GiveawayPlatform.NINTENDO_SWITCH to true),
            sortBy = GiveawaySortBy.DATE
        )

        every { giveawaysRepository.observeGiveaways() } returns flowOf(listOf(resultOne, resultTwo, resultThree))
        every { giveawaysRepository.observeGiveaways(any()) } returns flowOf(listOf(resultThree, resultOne))
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        viewModel.loadGiveaway(para)

        val emissions = observeStates(viewModel)
        assertEquals(1, emissions.size)
        assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.first().status)
        assertEquals(2, emissions.first().giveaways.size)
        assertEquals(resultThree, emissions.first().giveaways.first())
        assertEquals(resultOne, emissions.first().giveaways.second())
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
            types = persistentListOf(GiveawayType.GAME to true),
            platforms = persistentListOf(GiveawayPlatform.PC to true),
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
            types = persistentListOf(GiveawayType.GAME to true),
            platforms = persistentListOf(GiveawayPlatform.PC to true),
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

private val MIN_DATETIME = LocalDateTime(1970, 1, 1, 0, 0)

private fun giveaway(
    id: Int = 1,
    title: String = "Test Giveaway",
    worthDenominated: String? = "$0",
    worth: Double? = 0.0,
    thumbnail: String = "thumb.png",
    image: String = "image.png",
    description: String = "desc",
    instructions: String = "instructions",
    openGiveawayUrl: String = "https://example.com/open",
    publishedDate: LocalDateTime = MIN_DATETIME,
    type: GiveawayType = GiveawayType.GAME,
    platforms: List<GiveawayPlatform> = listOf(GiveawayPlatform.PC),
    endDate: String? = null,
    users: Int = 0,
    status: String = "Active",
    gamerpowerUrl: String = "https://example.com",
    openGiveaway: String = "https://example.com/giveaway",
) = Giveaway(
    id, title, worthDenominated, worth, thumbnail, image, description, instructions,
    openGiveawayUrl, publishedDate, type, platforms, endDate, users, status, gamerpowerUrl, openGiveaway,
)
