@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.game.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GameDetailsViewModelTest : MainDispatcherTest() {

    private val igdbRepository: IgdbRepository = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel(steamAppId: Int?): GameDetailsViewModel = GameDetailsViewModel(
        savedStateHandle = if (steamAppId == null) SavedStateHandle() else SavedStateHandle(mapOf("steamAppId" to steamAppId)),
        logger = TestingLoggingListener(),
        igdbRepository = igdbRepository,
    )

    @Test
    fun initially_emits_Loading_while_fetch_in_flight() = runTest {
        val steamId = 1240440
        // Mock that never resolves so the flow stays in Loading state.
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } calls {
            delay(Long.MAX_VALUE)
            null
        }

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Loading, emissions.last())
    }

    @Test
    fun successful_fetch_emits_Data() = runTest {
        val steamId = 1240440
        val igdb = igdbDetails()
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns igdb

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Data(igdb), emissions.last())
    }

    @Test
    fun repository_returns_null_emits_Error() = runTest {
        val steamId = 1240440
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } returns null

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
    }

    @Test
    fun exception_during_fetch_emits_Error() = runTest {
        val steamId = 1240440
        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } calls { throw Exception("IGDB down") }

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
    }

    @Test
    fun missing_steamAppId_emits_Error_without_calling_repository() = runTest {
        val viewModel = createViewModel(steamAppId = null)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()

        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())
        verifySuspend(exactly(0)) { igdbRepository.fetchGameDetailsBySteamId(any()) }
    }

    @Test
    fun reload_re_fetches_and_emits_fresh_Data() = runTest {
        val steamId = 1240440
        val first = igdbDetails(summary = "first")
        val second = igdbDetails(summary = "second")

        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } sequentially {
            returns(first)
            returns(second)
        }

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Data(first), emissions.last())

        viewModel.reload()
        runCurrent()
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Data(second), emissions.last())
    }

    @Test
    fun reload_after_initial_failure_flips_Error_back_to_Data() = runTest {
        val steamId = 1240440
        val igdb = igdbDetails()

        everySuspend { igdbRepository.fetchGameDetailsBySteamId(steamId) } sequentially {
            calls { throw Exception("IGDB down") }
            returns(igdb)
        }

        val viewModel = createViewModel(steamId)
        val emissions = viewModel.uiState.observeEmissions(this.backgroundScope, testDispatcher)
        runCurrent()
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Error, emissions.last())

        viewModel.reload()
        runCurrent()
        assertEquals(GameDetailsViewModel.GameDetailsScreenData.Data(igdb), emissions.last())
    }

    private fun igdbDetails(
        id: Long = 1L,
        name: String = "Halo Infinite",
        summary: String? = "Master Chief stuff",
    ): IgdbGame = IgdbGame(id = id, name = name, summary = summary)
}
