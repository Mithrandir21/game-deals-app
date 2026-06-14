@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.home.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.DEFAULT_COUNTRY
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.stats.StatsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.deal
import pm.bam.gamedeals.testing.fixtures.gameInfo
import pm.bam.gamedeals.testing.fixtures.store
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeViewModelTest : MainDispatcherTest() {

    private val storesRepository: StoresRepository = mock(MockMode.autoUnit) {
        every { observeStores() } returns flowOf(emptyList())
    }
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val dealsRepository: DealsRepository = mock(MockMode.autoUnit) {
        everySuspend { getDeals(any()) } returns emptyList()
    }
    private val releasesRepository: ReleasesRepository = mock(MockMode.autoUnit) {
        every { observeReleases() } returns flowOf(emptyList())
    }
    private val giveawaysRepository: GiveawaysRepository = mock(MockMode.autoUnit) {
        every { observeGiveaways() } returns flowOf(emptyList())
    }
    private val bundlesRepository: BundlesRepository = mock(MockMode.autoUnit) {
        everySuspend { getBundles() } returns emptyList()
    }
    private val statsRepository: StatsRepository = mock(MockMode.autoUnit) {
        everySuspend { getMostWaitlisted(any()) } returns emptyList()
        everySuspend { getMostCollected(any()) } returns emptyList()
    }
    private val accountRepository: AccountRepository = mock(MockMode.autoUnit) {
        every { observeAuthState() } returns flowOf(AuthState.LoggedOut)
    }
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        every { observeWaitlistIds() } returns flowOf(persistentSetOf())
    }
    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit)
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit) {
        every { observeSelectedCountry() } returns flowOf(DEFAULT_COUNTRY)
    }
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit) {
        every { observeIgnoredIds() } returns flowOf(persistentSetOf())
    }
    private val logger = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel() = HomeViewModel(
        storesRepository = storesRepository,
        dealsRepository = dealsRepository,
        gamesRepository = gamesRepository,
        releasesRepository = releasesRepository,
        giveawaysRepository = giveawaysRepository,
        bundlesRepository = bundlesRepository,
        statsRepository = statsRepository,
        accountRepository = accountRepository,
        waitlistRepository = waitlistRepository,
        collectionRepository = collectionRepository,
        dealShareTextBuilder = dealShareTextBuilder,
        regionRepository = regionRepository,
        ignoredRepository = ignoredRepository,
        logger = logger,
    )

    @Test
    fun composes_sections_into_data_state() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))
        everySuspend { statsRepository.getMostWaitlisted(any()) } returns listOf(RankedGame("w1", "Waited"))

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(HomeScreenStatus.DATA, state.status)
        assertTrue(state.featuredHero.isNotEmpty())
        assertTrue(state.trending.isNotEmpty())
        assertEquals(1, state.mostWaitlisted.size)
        assertNull(state.accountStats) // logged out
    }

    @Test
    fun account_stats_present_when_logged_in() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedIn("bam"))
        everySuspend { waitlistRepository.getWaitlist() } returns listOf(WaitlistEntry("a", "A"), WaitlistEntry("b", "B"))
        everySuspend { collectionRepository.getCollection() } returns listOf(CollectionEntry("c", "C"))

        val vm = createViewModel()
        advanceUntilIdle()

        val stats = vm.uiState.value.accountStats
        assertNotNull(stats)
        assertEquals(2, stats.waitlistedCount)
        assertEquals(1, stats.collectedCount)
    }

    @Test
    fun a_failed_section_is_hidden_but_the_feed_survives() = runTest {
        everySuspend { dealsRepository.getDeals(any()) } returns listOf(deal("d1"))
        everySuspend { statsRepository.getMostWaitlisted(any()) } throws RuntimeException("stats down")

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(HomeScreenStatus.DATA, state.status)
        assertTrue(state.featuredHero.isNotEmpty()) // other sections still loaded
        assertTrue(state.mostWaitlisted.isEmpty()) // failed section hidden
    }

    @Test
    fun empty_feed_surfaces_error_for_retry() = runTest {
        // all section stubs already return empty / logged out → nothing to show
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(HomeScreenStatus.ERROR, vm.uiState.value.status)
    }

    @Test
    fun toggleWaitlistFromDeal_delegates_to_repository() = runTest {
        everySuspend { waitlistRepository.toggleWaitlist("42") } returns RepoUpdateResult.UPDATED

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleWaitlistFromDeal(dealDetailsData(gameId = "42"))
        advanceUntilIdle()

        verifySuspend(exactly(1)) { waitlistRepository.toggleWaitlist("42") }
    }

    @Test
    fun toggleWaitlistFromDeal_when_logged_out_emits_SignInRequired() = runTest {
        everySuspend { waitlistRepository.toggleWaitlist("42") } returns RepoUpdateResult.NOT_LOGGED_IN

        val vm = createViewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)

        vm.toggleWaitlistFromDeal(dealDetailsData(gameId = "42"))
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(HomeViewModel.HomeUiEvent.SignInRequired, events.first())
    }

    @Test
    fun onShareDealClicked_emits_ShareDeal_event() = runTest {
        every { dealShareTextBuilder.build(any(), any(), any(), any()) } returns "Built share text"

        val vm = createViewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)

        vm.onShareDealClicked(
            DealBottomSheetData.DealDetailsLoading(
                store = store(storeName = "Steam"),
                gameId = "42",
                gameName = "Halo",
                dealId = "deal-1",
                dealUrl = "https://deal",
                gameSalesPriceDenominated = "$9.99",
            )
        )
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(HomeViewModel.HomeUiEvent.ShareDeal("Built share text"), events.first())
    }

    @Test
    fun onReleaseGame_emits_ReleaseUnavailable_when_no_deal_resolves() = runTest {
        everySuspend { gamesRepository.getReleaseDeal(any()) } returns null

        val vm = createViewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)

        vm.onReleaseGame("Some Release")
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(HomeViewModel.HomeUiEvent.ReleaseUnavailable, events.first())
    }

    private fun dealDetailsData(gameId: String) = DealBottomSheetData.DealDetailsData(
        store = store(),
        gameId = gameId,
        gameName = "Halo",
        dealId = "deal-1",
        gameSalesPriceDenominated = "$9.99",
        gameInfo = gameInfo(gameID = gameId, thumb = "thumb"),
        cheaperStores = persistentListOf(),
        cheapestPrice = null,
    )
}
