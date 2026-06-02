@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.favourites.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.favouriteGame
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FavouritesViewModelTest : MainDispatcherTest() {

    private val favouritesRepository: FavouritesRepository = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun empty_list_emits_SUCCESS() = runTest {
        every { favouritesRepository.observeFavourites() } returns flowOf(emptyList())
        val viewModel = FavouritesViewModel(TestingLoggingListener(), favouritesRepository)

        val emissions = observeStates(viewModel)
        assertEquals(FavouritesViewModel.FavouritesScreenStatus.SUCCESS, emissions.last().status)
        assertEquals(0, emissions.last().favourites.size)
    }

    @Test
    fun populated_list_emits_SUCCESS_with_items() = runTest {
        val fav = favouriteGame(gameID = "1")
        every { favouritesRepository.observeFavourites() } returns flowOf(listOf(fav))
        val viewModel = FavouritesViewModel(TestingLoggingListener(), favouritesRepository)

        val emissions = observeStates(viewModel)
        assertEquals(FavouritesViewModel.FavouritesScreenStatus.SUCCESS, emissions.last().status)
        assertEquals(1, emissions.last().favourites.size)
        assertEquals(fav, emissions.last().favourites.first())
    }

    @Test
    fun repository_throws_emits_ERROR() = runTest {
        every { favouritesRepository.observeFavourites() } returns flow { throw Exception() }
        val viewModel = FavouritesViewModel(TestingLoggingListener(), favouritesRepository)

        val emissions = observeStates(viewModel)
        assertEquals(FavouritesViewModel.FavouritesScreenStatus.ERROR, emissions.last().status)
    }

    @Test
    fun initial_emission_is_LOADING_when_no_repository_value_yet() = runTest {
        // Hot SharedFlow that never emits → the onStart-emitted default LOADING is what the subscriber sees first. Documents the LOADING entry-point of the
        // stateIn.Eagerly chain.
        val source = MutableSharedFlow<List<pm.bam.gamedeals.domain.models.FavouriteGame>>(replay = 0)
        every { favouritesRepository.observeFavourites() } returns source
        val viewModel = FavouritesViewModel(TestingLoggingListener(), favouritesRepository)

        val emissions = observeStates(viewModel)
        runCurrent()

        assertEquals(FavouritesViewModel.FavouritesScreenStatus.LOADING, emissions.first().status)
    }

    @Test
    fun list_growth_emissions_update_uiState() = runTest {
        val source = MutableSharedFlow<List<pm.bam.gamedeals.domain.models.FavouriteGame>>(replay = 1)
        every { favouritesRepository.observeFavourites() } returns source
        val viewModel = FavouritesViewModel(TestingLoggingListener(), favouritesRepository)

        val emissions = observeStates(viewModel)

        source.emit(emptyList())
        runCurrent()
        assertEquals(0, emissions.last().favourites.size)

        source.emit(listOf(favouriteGame(gameID = "1"), favouriteGame(gameID = "2")))
        runCurrent()
        assertEquals(2, emissions.last().favourites.size)

        source.emit(listOf(favouriteGame(gameID = "1")))
        runCurrent()
        assertEquals(1, emissions.last().favourites.size)
        assertEquals(FavouritesViewModel.FavouritesScreenStatus.SUCCESS, emissions.last().status)
    }


    private fun TestScope.observeStates(viewModel: FavouritesViewModel) =
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
}
