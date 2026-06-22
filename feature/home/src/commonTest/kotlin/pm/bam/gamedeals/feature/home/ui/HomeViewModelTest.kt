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
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.deal.StoreDealPair
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.DEFAULT_COUNTRY
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.recommendations.RecommendationsRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.stats.StatsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.deal
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
    private val dealsRepository: DealsRepository = mock(MockMode.autoUnit) {
        everySuspend { getDeals(any()) } returns emptyList()
    }
    private val releasesRepository: ReleasesRepository = mock(MockMode.autoUnit) {
        every { observeReleases() } returns flowOf(emptyList())
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
    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit) {
        every { observeCollectionIds() } returns flowOf(persistentSetOf())
    }
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit) {
        every { observeSelectedCountry() } returns flowOf(DEFAULT_COUNTRY)
    }
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit) {
        every { observeIgnoredIds() } returns flowOf(persistentSetOf())
    }
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit) {
        everySuspend { getGamePrices(any()) } returns emptyList()
    }
    private val igdbRepository: IgdbRepository = mock(MockMode.autoUnit)
    private val recommendationsRepository: RecommendationsRepository = mock(MockMode.autoUnit) {
        everySuspend { getRecommendations(any()) } returns emptyList()
    }
    private val logger = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun createViewModel() = HomeViewModel(
        storesRepository = storesRepository,
        dealsRepository = dealsRepository,
        releasesRepository = releasesRepository,
        bundlesRepository = bundlesRepository,
        statsRepository = statsRepository,
        accountRepository = accountRepository,
        waitlistRepository = waitlistRepository,
        collectionRepository = collectionRepository,
        dealShareTextBuilder = dealShareTextBuilder,
        regionRepository = regionRepository,
        ignoredRepository = ignoredRepository,
        gamesRepository = gamesRepository,
        igdbRepository = igdbRepository,
        recommendationsRepository = recommendationsRepository,
        logger = logger,
    )

    @Test
    fun composes_sections_into_data_state() = runTest {
        // Hero + Trending are disjoint slices of one hottest-deals fetch (hero = top LIMIT_HERO,
        // trending = the rest), so the stub must return more than LIMIT_HERO deals for both to populate.
        everySuspend { dealsRepository.getDeals(any()) } returns (1..LIMIT_HERO + 3).map { deal("d$it", gameID = "g$it") }
        everySuspend { statsRepository.getMostWaitlisted(any()) } returns listOf(RankedGame("w1", "Waited"))

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(HomeScreenStatus.DATA, state.status)
        assertEquals(LIMIT_HERO, state.featuredHero.size) // top of the hot list
        assertEquals(3, state.trending.size) // the remainder
        assertTrue(state.featuredHero.none { hero -> hero in state.trending }) // disjoint slices
        assertEquals(1, state.mostWaitlisted.size)
    }

    @Test
    fun ranked_rows_are_enriched_with_best_price_and_discount() = runTest {
        everySuspend { statsRepository.getMostWaitlisted(any()) } returns listOf(RankedGame("w1", "Waited"))
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(
            BundleGamePrice(
                gameId = "w1",
                bestShopName = "Steam",
                bestPriceValue = 9.99,
                bestPriceDenominated = "$9.99",
                bestCutPercent = 50,
                historicalLowValue = null,
                historicalLowDenominated = null,
            )
        )

        val vm = createViewModel()
        advanceUntilIdle()

        val ranked = vm.uiState.value.mostWaitlisted.first()
        assertEquals("$9.99", ranked.priceDenominated)
        assertEquals(50, ranked.cutPercent)
    }

    @Test
    fun account_stats_reactively_reflect_logged_in_id_set_sizes() = runTest {
        // Stats are derived from the auth-gated, Room-backed id sets — not a one-shot fetch — so they show/hide
        // reactively on login/logout regardless of a region reload.
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedIn("bam"))
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf("a", "b"))
        every { collectionRepository.observeCollectionIds() } returns flowOf(persistentSetOf("c"))

        val vm = createViewModel()
        val emissions = vm.accountStats.observeEmissions(backgroundScope, testDispatcher)
        advanceUntilIdle()

        val stats = emissions.last()
        assertNotNull(stats)
        assertEquals(2, stats.waitlistedCount)
        assertEquals(1, stats.collectedCount)
    }

    @Test
    fun account_stats_are_null_when_logged_out() = runTest {
        // Defaults: observeAuthState = LoggedOut, id sets empty.
        val vm = createViewModel()
        val emissions = vm.accountStats.observeEmissions(backgroundScope, testDispatcher)
        advanceUntilIdle()

        assertNull(emissions.last())
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
    fun toggleWaitlist_delegates_to_repository() = runTest {
        everySuspend { waitlistRepository.toggleWaitlist("42") } returns RepoUpdateResult.UPDATED

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleWaitlist("42")
        advanceUntilIdle()

        verifySuspend(exactly(1)) { waitlistRepository.toggleWaitlist("42") }
    }

    @Test
    fun toggleWaitlist_when_logged_out_emits_SignInRequired() = runTest {
        everySuspend { waitlistRepository.toggleWaitlist("42") } returns RepoUpdateResult.NOT_LOGGED_IN

        val vm = createViewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)

        vm.toggleWaitlist("42")
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(HomeViewModel.HomeUiEvent.SignInRequired, events.first())
    }

    @Test
    fun onShareClicked_emits_ShareDeal_event() = runTest {
        every { dealShareTextBuilder.build(any(), any(), any(), any()) } returns "Built share text"

        val vm = createViewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)

        vm.onShareClicked(peekData(gameId = "42"))
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(HomeViewModel.HomeUiEvent.ShareDeal("Built share text"), events.first())
    }

    private fun peekData(gameId: String) = GamePeekSheetData.Data(
        gameId = gameId,
        gameName = "Halo",
        thumb = "thumb",
        bestDeal = StoreDealPair(
            store = store(storeName = "Steam"),
            deal = GameDetails.GameDeal(
                storeID = 1,
                dealID = "deal-1",
                priceValue = 9.99,
                priceDenominated = "$9.99",
                retailPriceValue = 19.99,
                retailPriceDenominated = "$19.99",
                savings = 50,
                url = "https://deal",
            ),
        ),
    )
}
