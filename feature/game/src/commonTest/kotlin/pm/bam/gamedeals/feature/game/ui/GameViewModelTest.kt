@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.game.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.gameDeal
import pm.bam.gamedeals.testing.fixtures.gameDetails
import pm.bam.gamedeals.testing.fixtures.store
import pm.bam.gamedeals.testing.utils.fourth
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second
import pm.bam.gamedeals.testing.utils.third
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GameViewModelTest : MainDispatcherTest() {

    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val storesRepository: StoresRepository = mock(MockMode.autoUnit)
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)
    private val favouritesRepository: FavouritesRepository = mock(MockMode.autoUnit) {
        every { observeIsFavourite(any()) } returns flowOf(false)
    }

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel(gameId: Int?): GameViewModel = GameViewModel(
        savedStateHandle = if (gameId == null) SavedStateHandle() else SavedStateHandle(mapOf("gameId" to gameId)),
        logger = TestingLoggingListener(),
        gamesRepository = gamesRepository,
        storesRepository = storesRepository,
        dealShareTextBuilder = dealShareTextBuilder,
        favouritesRepository = favouritesRepository,
    )

    @Test
    fun initially_loading_state() = runTest {
        val gameId = 1
        // No mocks: the init flow stays in delayOnStart and does not emit before we assert.
        everySuspend { gamesRepository.getGameDetails(gameId) } returns gameDetails()

        val viewModel = createViewModel(gameId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(1, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())
    }

    @Test
    fun error_state() = runTest {
        val gameId = 1
        everySuspend { gamesRepository.getGameDetails(gameId) } throws Exception()

        val viewModel = createViewModel(gameId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(1, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())

        testScheduler.advanceTimeBy(1200)
        runCurrent()

        assertEquals(2, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())
        assertEquals(GameViewModel.GameScreenData.Error, emissions.second())
    }

    @Test
    fun game_load() = runTest {
        val gameId = 1
        val storeId = 2
        val store = store(storeID = storeId)
        val gameDeal = gameDeal(storeID = storeId)
        val details = gameDetails(deals = persistentListOf(gameDeal))

        everySuspend { gamesRepository.getGameDetails(gameId) } returns details
        everySuspend { storesRepository.getStore(storeId) } returns store

        val viewModel = createViewModel(gameId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(1, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())

        testScheduler.advanceTimeBy(1200)
        runCurrent()

        assertEquals(2, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())
        assertEquals(GameViewModel.GameScreenData.Data(details, persistentListOf(StoreDealPair(store = store, deal = gameDeal))), emissions.second())
    }

    @Test
    fun game_reload() = runTest {
        val gameId = 1
        val storeId = 2
        val store = store(storeID = storeId)
        val gameDeal = gameDeal(storeID = storeId)
        val details = gameDetails(deals = persistentListOf(gameDeal))

        everySuspend { gamesRepository.getGameDetails(gameId) } returns details
        everySuspend { storesRepository.getStore(storeId) } returns store

        val viewModel = createViewModel(gameId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(1, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())

        testScheduler.advanceTimeBy(1200)
        runCurrent()

        assertEquals(2, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Data(details, persistentListOf(StoreDealPair(store = store, deal = gameDeal))), emissions.second())

        viewModel.reloadGameDetails()

        assertEquals(4, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.third())
        assertEquals(GameViewModel.GameScreenData.Data(details, persistentListOf(StoreDealPair(store = store, deal = gameDeal))), emissions.fourth())
    }

    @Test
    fun missing_gameId_emits_Error_after_initial_delay() = runTest {
        val viewModel = createViewModel(gameId = null)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())

        testScheduler.advanceTimeBy(1200)
        runCurrent()

        assertEquals(GameViewModel.GameScreenData.Error, emissions.last())
    }

    @Test
    fun isFavourite_initial_value_is_false() = runTest {
        val viewModel = createViewModel(gameId = 1)
        val emissions = viewModel.isFavourite.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(false, emissions.first())
    }

    @Test
    fun isFavourite_emits_true_when_repository_reports_favourited() = runTest {
        every { favouritesRepository.observeIsFavourite(1) } returns flowOf(true)

        val viewModel = createViewModel(gameId = 1)
        val emissions = viewModel.isFavourite.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(true, emissions.last())
    }

    @Test
    fun isFavourite_emits_false_when_gameId_is_null_without_querying_repository() = runTest {
        val viewModel = createViewModel(gameId = null)
        val emissions = viewModel.isFavourite.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(false, emissions.last())
        verify(exactly(0)) { favouritesRepository.observeIsFavourite(any()) }
    }

    @Test
    fun toggleFavourite_while_uiState_is_Loading_does_not_call_repository() = runTest {
        // No getGameDetails stub fired yet, so uiState stays Loading.
        val viewModel = createViewModel(gameId = 1)
        runCurrent()

        viewModel.toggleFavourite()
        runCurrent()

        verifySuspend(exactly(0)) { favouritesRepository.toggleFavourite(any(), any(), any()) }
    }

    @Test
    fun toggleFavourite_while_uiState_is_Error_does_not_call_repository() = runTest {
        everySuspend { gamesRepository.getGameDetails(1) } throws Exception()

        val viewModel = createViewModel(gameId = 1)
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        testScheduler.advanceTimeBy(1200)
        runCurrent()

        viewModel.toggleFavourite()
        runCurrent()

        verifySuspend(exactly(0)) { favouritesRepository.toggleFavourite(any(), any(), any()) }
    }

    @Test
    fun toggleFavourite_with_Data_state_forwards_gameId_title_and_thumb_to_repository() = runTest {
        val gameId = 1
        val storeId = 2
        val info = GameDetails.GameInfo(title = "Halo", steamAppID = null, thumb = "thumb-halo")
        val details = gameDetails(info = info, deals = persistentListOf(gameDeal(storeID = storeId)))
        everySuspend { gamesRepository.getGameDetails(gameId) } returns details
        everySuspend { storesRepository.getStore(storeId) } returns store(storeID = storeId)
        everySuspend { favouritesRepository.toggleFavourite(any(), any(), any()) } returns true

        val viewModel = createViewModel(gameId)
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        testScheduler.advanceTimeBy(1200)
        runCurrent()

        viewModel.toggleFavourite()
        runCurrent()

        verifySuspend(exactly(1)) {
            favouritesRepository.toggleFavourite(gameId = gameId, title = "Halo", thumb = "thumb-halo")
        }
    }

    @Test
    fun onShareDealClicked_emits_ShareDeal_event_with_built_text() = runTest {
        every { dealShareTextBuilder.build(any(), any(), any(), any()) } returns "Built share text"

        val viewModel = createViewModel(gameId = 1)
        val events = viewModel.events.observeEmissions(this.backgroundScope, testDispatcher)

        val info = GameDetails.GameInfo(title = "Halo", steamAppID = null, thumb = "thumb")
        val storeValue = store(storeName = "Steam")
        val deal = gameDeal(dealID = "deal-1", priceDenominated = "$9.99")

        viewModel.onShareDealClicked(info, storeValue, deal)
        runCurrent()

        assertEquals(1, events.size)
        assertEquals(GameViewModel.GameUiEvent.ShareDeal("Built share text"), events.first())

        verify(exactly(1)) {
            dealShareTextBuilder.build(
                gameTitle = "Halo",
                salePriceDenominated = "$9.99",
                storeName = "Steam",
                dealId = "deal-1",
            )
        }
    }

    @Test
    fun reload_after_initial_failure_flips_Error_back_to_Data() = runTest {
        val gameId = 1
        val storeId = 2
        val store = store(storeID = storeId)
        val gameDeal = gameDeal(storeID = storeId)
        val details = gameDetails(deals = persistentListOf(gameDeal))

        everySuspend { gamesRepository.getGameDetails(gameId) } sequentially {
            throws(Exception())
            returns(details)
        }
        everySuspend { storesRepository.getStore(storeId) } returns store

        val viewModel = createViewModel(gameId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        testScheduler.advanceTimeBy(1200)
        runCurrent()
        assertEquals(GameViewModel.GameScreenData.Error, emissions.last())

        viewModel.reloadGameDetails()
        runCurrent()

        assertEquals(GameViewModel.GameScreenData.Data(details, persistentListOf(StoreDealPair(store = store, deal = gameDeal))), emissions.last())
    }

    @Test
    fun rapid_reload_calls_do_not_crash_or_lose_final_Data_state() = runTest {
        val gameId = 1
        val storeId = 2
        val store = store(storeID = storeId)
        val gameDeal = gameDeal(storeID = storeId)
        val details = gameDetails(deals = persistentListOf(gameDeal))

        everySuspend { gamesRepository.getGameDetails(gameId) } returns details
        everySuspend { storesRepository.getStore(storeId) } returns store

        val viewModel = createViewModel(gameId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        testScheduler.advanceTimeBy(1200)
        runCurrent()

        // The reloadTrigger is a DROP_OLDEST SharedFlow with extraBufferCapacity=1; rapid emits collapse rather than queue. flatMapLatest cancels any in-flight
        // inner flow before relaunching it. The combined effect is that the burst settles back into Data without emitting an intermediate Error or losing the
        // final state.
        viewModel.reloadGameDetails()
        viewModel.reloadGameDetails()
        viewModel.reloadGameDetails()
        runCurrent()

        assertEquals(GameViewModel.GameScreenData.Data(details, persistentListOf(StoreDealPair(store = store, deal = gameDeal))), emissions.last())
    }
}
