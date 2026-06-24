@file:OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.feature.giveaways.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.giveaway
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GiveawayDetailViewModelTest : MainDispatcherTest() {

    private val giveawaysRepository: GiveawaysRepository = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun loads_giveaway_by_id_with_no_expiry() = runTest {
        everySuspend { giveawaysRepository.getGiveaway(7) } returns giveaway(id = 7, endDate = null)

        val viewModel = newViewModel(7)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<GiveawayDetailViewModel.GiveawayDetailScreenData.Data>(state)
        assertEquals(7, state.giveaway.id)
        assertNull(state.endDateMillis)
    }

    @Test
    fun parses_end_date_for_the_countdown() = runTest {
        everySuspend { giveawaysRepository.getGiveaway(7) } returns giveaway(id = 7, endDate = "2026-07-01 18:00:00")

        val viewModel = newViewModel(7)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<GiveawayDetailViewModel.GiveawayDetailScreenData.Data>(state)
        val expected = LocalDateTime(2026, 7, 1, 18, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()
        assertEquals(expected, state.endDateMillis)
    }

    @Test
    fun error_when_giveaway_missing() = runTest {
        everySuspend { giveawaysRepository.getGiveaway(7) } returns null

        val viewModel = newViewModel(7)
        advanceUntilIdle()

        assertIs<GiveawayDetailViewModel.GiveawayDetailScreenData.Error>(viewModel.uiState.value)
    }

    @Test
    fun error_when_no_id_argument() = runTest {
        val viewModel = GiveawayDetailViewModel(SavedStateHandle(), TestingLoggingListener(), giveawaysRepository, testDatetimeParsing)
        advanceUntilIdle()

        assertIs<GiveawayDetailViewModel.GiveawayDetailScreenData.Error>(viewModel.uiState.value)
    }

    private fun newViewModel(giveawayId: Int) = GiveawayDetailViewModel(
        SavedStateHandle(mapOf("giveawayId" to giveawayId)),
        TestingLoggingListener(),
        giveawaysRepository,
        testDatetimeParsing,
    )
}
