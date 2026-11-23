package pm.bam.gamedeals.feature.home.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.CollectedGames
import pm.bam.gamedeals.domain.models.YearInDeals
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.yearindeals.YearInDealsRepository
import pm.bam.gamedeals.domain.repositories.yearindeals.YearInDealsSampleMode
import pm.bam.gamedeals.testing.MainDispatcherTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YearInDealsViewModelTest : MainDispatcherTest() {

    private class FakeYearRepository(
        var offered: Boolean = true,
        var result: () -> YearInDeals = { YearInDeals(2026, null, null, null, null) },
    ) : YearInDealsRepository {
        var loads = 0
        override fun isOffered() = offered
        override fun reportYear() = 2026
        override suspend fun getYearInDeals(): YearInDeals {
            loads++
            return result()
        }
    }

    private class FakeSampleMode : YearInDealsSampleMode {
        val on = MutableStateFlow(false)
        override fun observe(): Flow<Boolean> = on
        override suspend fun isOn() = on.value
        override suspend fun set(on: Boolean) { this.on.value = on }
    }

    private val authState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    private val accountRepository: AccountRepository = mock(MockMode.autoUnit) {
        every { observeAuthState() } returns authState
    }
    private val repository = FakeYearRepository()
    private val sampleMode = FakeSampleMode()

    private val withCollection = YearInDeals(2026, CollectedGames(23, persistentListOf()), "$412.77", null, null)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun the_home_entry_shows_only_to_signed_in_users_while_the_card_is_offered() = runTest {
        val vm = YearInDealsEntryViewModel(accountRepository, repository, sampleMode)
        backgroundScope.launch { vm.offeredYear.collect {} }
        advanceUntilIdle()
        assertNull(vm.offeredYear.value, "signed out")

        authState.value = AuthState.LoggedIn("bam")
        advanceUntilIdle()
        assertEquals(2026, vm.offeredYear.value)

        repository.offered = false
        authState.value = AuthState.LoggedIn("bam", needsReconnect = true)
        advanceUntilIdle()
        assertNull(vm.offeredYear.value, "out of season or flag off")
    }

    @Test
    fun the_sample_switch_shows_the_entry_any_time_signed_in_or_not() = runTest {
        repository.offered = false
        val vm = YearInDealsEntryViewModel(accountRepository, repository, sampleMode)
        backgroundScope.launch { vm.offeredYear.collect {} }
        sampleMode.on.value = true
        advanceUntilIdle()
        assertEquals(2026, vm.offeredYear.value)
    }

    @Test
    fun the_screen_shows_the_year_when_it_has_something_to_say() = runTest {
        repository.result = { withCollection }
        val vm = YearInDealsViewModel(repository)
        advanceUntilIdle()
        assertEquals(YearInDealsViewModel.State.Ready(withCollection), vm.state.value)
    }

    @Test
    fun an_empty_year_says_so_instead_of_showing_a_blank_card() = runTest {
        val vm = YearInDealsViewModel(repository)
        advanceUntilIdle()
        assertEquals(YearInDealsViewModel.State.Empty(2026), vm.state.value)
    }

    @Test
    fun a_failed_load_can_be_retried() = runTest {
        repository.result = { error("offline") }
        val vm = YearInDealsViewModel(repository)
        advanceUntilIdle()
        assertIs<YearInDealsViewModel.State.Failed>(vm.state.value)

        repository.result = { withCollection }
        vm.retry()
        advanceUntilIdle()
        assertIs<YearInDealsViewModel.State.Ready>(vm.state.value)
        assertEquals(2, repository.loads)
    }

    @Test
    fun the_share_text_names_the_year_and_links_to_the_app() {
        val vm = YearInDealsViewModel(repository)
        val text = vm.shareText(withCollection)
        assertTrue(text.startsWith("My 2026 in game deals: 23 games collected."), text)
        assertTrue(text.endsWith("https://gamedeals.bam.pm/app"), text)
        assertEquals("My 2026 in game deals. Made with Game Deals https://gamedeals.bam.pm/app", vm.shareText(YearInDeals(2026, null, null, null, null)))
    }
}
