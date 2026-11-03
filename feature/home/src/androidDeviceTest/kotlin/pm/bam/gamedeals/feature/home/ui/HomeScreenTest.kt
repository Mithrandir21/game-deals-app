package pm.bam.gamedeals.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.RecentlyViewedGame
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_all_bundles_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_featured_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_loading_indicator
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_stat_collected
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_stat_waitlisted
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_trending_label
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.AccountStats
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus

/**
 * Device UI coverage for [HomeScreen], expanded from the original loading/error smoke tests to also cover
 * the DATA feed: section headers render, the account stat cards and "View all bundles" action dispatch
 * navigation, and tapping a deal row opens the peek sheet — all against a mocked [HomeViewModel].
 */
class HomeScreenTest {

    // createAndroidComposeRule<ComponentActivity> (not createComposeRule) — HomeScreen has no top bar
    // (the app shell owns it), so with no focusable content createComposeRule trips
    // RootViewWithoutFocusException and the hierarchy is torn down. Same workaround as WebViewTest.
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel: HomeViewModel = mockk(relaxed = true)
    private val winterSaleEntry: WinterSaleEntryViewModel = mockk(relaxed = true)

    private lateinit var labels: Labels

    @Before
    fun setup() {
        every { winterSaleEntry.event } returns MutableStateFlow<WinterSaleEventState.Available?>(null)
        every { viewModel.gamePeek } returns MutableStateFlow(null)
        every { viewModel.accountStats } returns MutableStateFlow(null)
        every { viewModel.waitlistIds } returns MutableStateFlow(persistentSetOf())
        every { viewModel.collectionIds } returns MutableStateFlow(persistentSetOf())
        every { viewModel.ignoredIds } returns MutableStateFlow(persistentSetOf())
        every { viewModel.stores } returns MutableStateFlow(persistentMapOf())
        every { viewModel.recentlyViewed } returns MutableStateFlow(persistentListOf<RecentlyViewedGame>())
        every { viewModel.events } returns MutableSharedFlow<HomeViewModel.HomeUiEvent>().asSharedFlow()
    }

    private fun setupCompose(
        goToGame: (String) -> Unit = { _ -> },
        goToGameByTitle: (String) -> Unit = { _ -> },
        onViewWaitlist: () -> Unit = {},
        onViewCollection: () -> Unit = {},
        onViewBundles: () -> Unit = {},
        onViewBundle: (Int) -> Unit = {},
        goToWeb: (String, String) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            labels = Labels.load()
            HomeScreen(
                goToGame = goToGame,
                goToGameByTitle = goToGameByTitle,
                onViewWaitlist = onViewWaitlist,
                onViewCollection = onViewCollection,
                onViewBundles = onViewBundles,
                onViewBundle = onViewBundle,
                goToWeb = goToWeb,
                viewModel = viewModel,
                winterSaleEntry = winterSaleEntry,
            )
        }
    }

    @Test
    fun loadingState() {
        every { viewModel.uiState } returns MutableStateFlow(HomeScreenData(status = HomeScreenStatus.LOADING))

        setupCompose()

        composeTestRule.onNodeWithContentDescription(labels.loading).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.errorMsg).assertDoesNotExist()
    }

    @Test
    fun errorState() {
        every { viewModel.uiState } returns MutableStateFlow(HomeScreenData(status = HomeScreenStatus.ERROR))

        setupCompose()

        composeTestRule.onNodeWithContentDescription(labels.loading).assertDoesNotExist()
        composeTestRule.onNodeWithText(labels.errorMsg).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.retry).assertIsDisplayed()
    }

    @Test
    fun dataStateRendersSectionHeaders() {
        every { viewModel.uiState } returns MutableStateFlow(
            HomeScreenData(
                status = HomeScreenStatus.DATA,
                featuredHero = persistentListOf(PreviewDeal.copy(dealID = "hero-1", title = "Featured Game", gameID = "hero-g")),
                trending = persistentListOf(PreviewDeal.copy(dealID = "trend-1", title = TRENDING_TITLE, gameID = TRENDING_GAME_ID)),
            )
        )

        setupCompose()

        composeTestRule.onNodeWithText(labels.featured).assertIsDisplayed()
        composeTestRule.onNodeWithText(labels.trending).assertIsDisplayed()
    }

    @Test
    fun statCardsDispatchNavigation() {
        every { viewModel.accountStats } returns MutableStateFlow(AccountStats(waitlistedCount = 12, collectedCount = 47))
        every { viewModel.uiState } returns MutableStateFlow(HomeScreenData(status = HomeScreenStatus.DATA))
        val onViewWaitlist = mockk<() -> Unit>(relaxed = true)
        val onViewCollection = mockk<() -> Unit>(relaxed = true)

        setupCompose(onViewWaitlist = onViewWaitlist, onViewCollection = onViewCollection)

        composeTestRule.onNodeWithContentDescription("12 ${labels.statWaitlisted}").performClick()
        composeTestRule.onNodeWithContentDescription("47 ${labels.statCollected}").performClick()

        verify(exactly = 1) { onViewWaitlist() }
        verify(exactly = 1) { onViewCollection() }
    }

    @Test
    fun tappingTrendingDealOpensPeek() {
        every { viewModel.uiState } returns MutableStateFlow(
            HomeScreenData(
                status = HomeScreenStatus.DATA,
                trending = persistentListOf(PreviewDeal.copy(dealID = "trend-1", title = TRENDING_TITLE, gameID = TRENDING_GAME_ID)),
            )
        )

        setupCompose()

        composeTestRule.onNodeWithText(TRENDING_TITLE).performClick()

        verify(exactly = 1) { viewModel.peekGame(TRENDING_GAME_ID, TRENDING_TITLE, any()) }
    }

    @Test
    fun bundlesViewAllDispatchesNavigation() {
        every { viewModel.uiState } returns MutableStateFlow(
            HomeScreenData(
                status = HomeScreenStatus.DATA,
                bundles = persistentListOf(
                    Bundle(
                        id = 1, title = "Indie Bundle", storeName = "Humble Store", url = "https://example.com/b",
                        expiryEpochMs = null, gameCount = 1, priceDenominated = "$9.99", games = persistentListOf(),
                    ),
                ),
            )
        )
        val onViewBundles = mockk<() -> Unit>(relaxed = true)

        setupCompose(onViewBundles = onViewBundles)

        composeTestRule.onNodeWithText(labels.allBundles).performClick()

        verify(exactly = 1) { onViewBundles() }
    }

    private data class Labels(
        val loading: String,
        val errorMsg: String,
        val retry: String,
        val featured: String,
        val trending: String,
        val allBundles: String,
        val statWaitlisted: String,
        val statCollected: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                loading = stringResource(Res.string.home_screen_loading_indicator),
                errorMsg = stringResource(Res.string.home_screen_data_loading_error_msg),
                retry = stringResource(Res.string.home_screen_data_loading_error_retry),
                featured = stringResource(Res.string.home_screen_featured_label),
                trending = stringResource(Res.string.home_screen_trending_label),
                allBundles = stringResource(Res.string.home_screen_all_bundles_label),
                statWaitlisted = stringResource(Res.string.home_screen_stat_waitlisted),
                statCollected = stringResource(Res.string.home_screen_stat_collected),
            )
        }
    }

    companion object {
        private const val TRENDING_TITLE = "Trending Game"
        private const val TRENDING_GAME_ID = "trend-g"

        // HomeScreen resolves an Analytics via koinInject() for its non-loading content. Koin is kept up for
        // the whole class (not per-test) so a late resolution during Compose disposal never hits a closed
        // scope — a per-test stopKoin() in @After races the compose rule's teardown.
        @JvmStatic
        @BeforeClass
        fun startKoinForAnalytics() {
            startKoin { modules(module { single<Analytics> { mockk(relaxed = true) } }) }
        }

        @JvmStatic
        @AfterClass
        fun stopKoinForAnalytics() {
            stopKoin()
        }
    }
}
