package pm.bam.gamedeals.feature.giveaways.ui

import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.testing.MainCoroutineRule
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second

@OptIn(ExperimentalCoroutinesApi::class)
class GiveawaysViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val giveawaysRepository: GiveawaysRepository = mockk()


    @Before
    fun setup() {
    }

    @Test
    fun `initially loading`() = runTest {
        coEvery { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        val emissions = observeStates(viewModel)
        Assert.assertEquals(1, emissions.size)
        Assert.assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.first().status)
    }

    @Test
    fun `initially error`() = runTest {
        coEvery { giveawaysRepository.observeGiveaways() } throws Exception()
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        val emissions = observeStates(viewModel)
        Assert.assertEquals(1, emissions.size)
        Assert.assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.ERROR, emissions.first().status)
    }

    @Test
    fun `reload Giveaways`() = runTest {
        coEvery { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        coEvery { giveawaysRepository.refreshGiveaways() } just runs
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        viewModel.reloadGiveaways()

        val emissions = observeStates(viewModel)
        Assert.assertEquals(1, emissions.size)

        // Loading is emitted twice, but observed only once because StateFlow emits only distinct values
        Assert.assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.LOADING, emissions.first().status)
    }

    @Test
    fun `reload Giveaways emits LOADING before refresh completes`() = runTest {
        coEvery { giveawaysRepository.observeGiveaways() } returns flowOf(emptyList())
        val refreshGate = CompletableDeferred<Unit>()
        coEvery { giveawaysRepository.refreshGiveaways() } coAnswers { refreshGate.await() }

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        viewModel.reloadGiveaways()

        val emissions = observeStates(viewModel)
        Assert.assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.LOADING, emissions.last().status)

        refreshGate.complete(Unit)
    }

    @Test
    fun `load Giveaways with search parameters`() = runTest {
        val resultOne = mockk<Giveaway>()
        val resultTwo = mockk<Giveaway>()
        val resultThree = mockk<Giveaway>()


        val para = GiveawaySearchParameters(
            types = persistentListOf(GiveawayType.GAME to true, GiveawayType.BETA to true),
            platforms = persistentListOf(GiveawayPlatform.PC to true, GiveawayPlatform.NINTENDO_SWITCH to true),
            sortBy = GiveawaySortBy.DATE
        )

        coEvery { giveawaysRepository.observeGiveaways() } returns flowOf(listOf(resultOne, resultTwo, resultThree))
        coEvery { giveawaysRepository.observeGiveaways(any()) } returns flowOf(listOf(resultThree, resultOne))
        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)
        viewModel.loadGiveaway(para)

        val emissions = observeStates(viewModel)
        Assert.assertEquals(1, emissions.size)
        Assert.assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.first().status)
        Assert.assertEquals(2, emissions.first().giveaways.size)
        Assert.assertEquals(resultThree, emissions.first().giveaways.first())
        Assert.assertEquals(resultOne, emissions.first().giveaways.second())
    }

    @Test
    fun `load Giveaways cancels prior unfiltered collector when filter applied`() = runTest {
        val unfilteredOne = mockk<Giveaway>()
        val unfilteredTwo = mockk<Giveaway>()
        val filteredOne = mockk<Giveaway>()

        val para = GiveawaySearchParameters(
            types = persistentListOf(GiveawayType.GAME to true),
            platforms = persistentListOf(GiveawayPlatform.PC to true),
            sortBy = GiveawaySortBy.DATE
        )

        // Model the hot Room Flows: each invalidation re-emits, the flow never completes.
        val unfilteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        val filteredRoom = MutableSharedFlow<List<Giveaway>>(replay = 1)
        coEvery { giveawaysRepository.observeGiveaways() } returns unfilteredRoom
        coEvery { giveawaysRepository.observeGiveaways(any()) } returns filteredRoom

        val viewModel = GiveawaysViewModel(TestingLoggingListener(), giveawaysRepository)

        // Initial unfiltered emission seeds the screen with two giveaways.
        unfilteredRoom.emit(listOf(unfilteredOne, unfilteredTwo))

        // User applies a filter; the active source must switch to the filtered Room flow.
        viewModel.loadGiveaway(para)
        filteredRoom.emit(listOf(filteredOne))

        // Simulate a Room invalidation re-emitting on the unfiltered flow (e.g. after refreshGiveaways).
        // With parallel collectors this would clobber the filtered result; with flatMapLatest the
        // unfiltered collector is cancelled and this emission is ignored.
        unfilteredRoom.emit(listOf(unfilteredOne, unfilteredTwo))

        val emissions = observeStates(viewModel)
        Assert.assertEquals(GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS, emissions.last().status)
        Assert.assertEquals(1, emissions.last().giveaways.size)
        Assert.assertEquals(filteredOne, emissions.last().giveaways.first())
    }


    private fun TestScope.observeStates(viewModel: GiveawaysViewModel) =
        viewModel.uiState.observeEmissions(this.backgroundScope, mainCoroutineRule.testDispatcher)
}