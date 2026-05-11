@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.game.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
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

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel(gameId: Int): GameViewModel = GameViewModel(
        savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId)),
        logger = TestingLoggingListener(),
        gamesRepository = gamesRepository,
        storesRepository = storesRepository,
        dealShareTextBuilder = dealShareTextBuilder,
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

        delay(1200) // Delay because Flow 'delayOnStart'

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

        delay(1200)

        assertEquals(2, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())
        assertEquals(GameViewModel.GameScreenData.Data(details, persistentListOf(store to gameDeal)), emissions.second())
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

        delay(1200)

        assertEquals(2, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Data(details, persistentListOf(store to gameDeal)), emissions.second())

        viewModel.reloadGameDetails()

        assertEquals(4, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.third())
        assertEquals(GameViewModel.GameScreenData.Data(details, persistentListOf(store to gameDeal)), emissions.fourth())
    }
}
