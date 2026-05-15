@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.favourites.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.FavouriteGame
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
        val fav = favouriteGame(gameID = 1)
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
    fun retry_resubscribes_to_observeFavourites() = runTest {
        // Use MutableSharedFlow so the source stays open and we can verify re-subscription
        // delivers fresh emissions (L-2026-05-02-05: flowOf would complete after one emit
        // and mask the second-subscription behaviour).
        val source = MutableSharedFlow<List<FavouriteGame>>(replay = 1)
        every { favouritesRepository.observeFavourites() } returns source

        val viewModel = FavouritesViewModel(TestingLoggingListener(), favouritesRepository)

        source.emit(emptyList())
        runCurrent()

        viewModel.retry()
        runCurrent()

        // Initial subscription + one re-subscription from retry().
        verify(exactly(2)) { favouritesRepository.observeFavourites() }
    }

    @Test
    fun retry_after_failed_load_flips_ERROR_back_to_SUCCESS_when_repository_recovers() = runTest {
        val fav = favouriteGame(gameID = 1)
        var callCount = 0
        every { favouritesRepository.observeFavourites() } calls {
            callCount++
            if (callCount == 1) flow<List<FavouriteGame>> { throw Exception() }
            else flowOf(listOf(fav))
        }

        val viewModel = FavouritesViewModel(TestingLoggingListener(), favouritesRepository)
        val emissions = observeStates(viewModel)
        runCurrent()

        // First load surfaced as ERROR.
        assertEquals(FavouritesViewModel.FavouritesScreenStatus.ERROR, emissions.last().status)

        viewModel.retry()
        runCurrent()

        assertEquals(FavouritesViewModel.FavouritesScreenStatus.SUCCESS, emissions.last().status)
        assertEquals(1, emissions.last().favourites.size)
    }


    private fun TestScope.observeStates(viewModel: FavouritesViewModel) =
        viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
}
