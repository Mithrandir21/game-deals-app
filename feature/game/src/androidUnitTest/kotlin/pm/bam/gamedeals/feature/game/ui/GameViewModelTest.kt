package pm.bam.gamedeals.feature.game.ui

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.testing.MainCoroutineRule
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.fourth
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.utils.second
import pm.bam.gamedeals.testing.utils.third

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainCoroutineRule()

    private val gamesRepository: GamesRepository = mockk()
    private val storesRepository: StoresRepository = mockk()

    private fun createViewModel(gameId: Int): GameViewModel = GameViewModel(
        // The typed route stores its args under their property names in the SavedStateHandle,
        // matching what the Compose Navigation runtime hands to a destination's ViewModel.
        savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId)),
        logger = TestingLoggingListener(),
        gamesRepository = gamesRepository,
        storesRepository = storesRepository,
    )


    @Test
    fun `initially loading state`() = runTest {
        val gameId = 1
        // No mocks: the init flow stays in delayOnStart and does not emit before we assert.
        coEvery { gamesRepository.getGameDetails(gameId) } returns mockk(relaxed = true)

        val viewModel = createViewModel(gameId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, mainDispatcherRule.testDispatcher)

        assertEquals(1, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())
    }


    @Test
    fun `error state`() = runTest {
        val gameId = 1
        coEvery { gamesRepository.getGameDetails(gameId) } throws Exception()

        val viewModel = createViewModel(gameId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, mainDispatcherRule.testDispatcher)

        assertEquals(1, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())

        delay(1200) // Delay because Flow 'delayOnStart'

        assertEquals(2, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())
        assertEquals(GameViewModel.GameScreenData.Error, emissions.second())
    }


    @Test
    fun `game load`() = runTest {
        val gameId = 1
        val storeId = 2
        val store: Store = mockk()
        val gameDeals: GameDetails.GameDeal = mockk { every { storeID } returns storeId }
        val dealsList = persistentListOf(gameDeals)
        val gameDetails: GameDetails = mockk { every { deals } returns dealsList }

        coEvery { gamesRepository.getGameDetails(gameId) } returns gameDetails
        coEvery { storesRepository.getStore(storeId) } returns store

        val viewModel = createViewModel(gameId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, mainDispatcherRule.testDispatcher)

        assertEquals(1, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())

        delay(1200) // Delay because Flow 'delayOnStart'

        assertEquals(2, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())
        assertEquals(GameViewModel.GameScreenData.Data(gameDetails, persistentListOf(store to gameDeals)), emissions.second())
    }


    @Test
    fun `game reload`() = runTest {
        val gameId = 1
        val storeId = 2
        val store: Store = mockk()
        val gameDeals: GameDetails.GameDeal = mockk { every { storeID } returns storeId }
        val dealsList = persistentListOf(gameDeals)
        val gameDetails: GameDetails = mockk { every { deals } returns dealsList }

        coEvery { gamesRepository.getGameDetails(gameId) } returns gameDetails
        coEvery { storesRepository.getStore(storeId) } returns store

        val viewModel = createViewModel(gameId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, mainDispatcherRule.testDispatcher)

        assertEquals(1, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.first())

        delay(1200) // Initial load completes after delayOnStart.

        assertEquals(2, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Data(gameDetails, persistentListOf(store to gameDeals)), emissions.second())

        viewModel.reloadGameDetails()

        assertEquals(4, emissions.size)
        assertEquals(GameViewModel.GameScreenData.Loading, emissions.third())
        assertEquals(GameViewModel.GameScreenData.Data(gameDetails, persistentListOf(store to gameDeals)), emissions.fourth())
    }
}
