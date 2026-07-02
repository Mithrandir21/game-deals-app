@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.PreviewRelease
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.BundleListRow
import pm.bam.gamedeals.common.ui.components.DealHeroTile
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.components.RecentlyViewedCarousel
import pm.bam.gamedeals.common.ui.deal.GamePeekSheet
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_remove_action
import pm.bam.gamedeals.common.navigation.SignInPromptController
import pm.bam.gamedeals.common.ui.adaptive.WidthSizeClass
import pm.bam.gamedeals.common.ui.adaptive.rememberWidthSizeClass
import pm.bam.gamedeals.common.ui.home.StatCard
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.models.RecentlyViewedGame
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_all_bundles_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_bundles_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_featured_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_hero_deal_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_loading_indicator
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_most_collected_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_most_waitlisted_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_new_releases_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_ranked_game_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_ranked_game_description_no_price
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_recommendation_row_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_recommended_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_release_row_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_release_upcoming
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_stat_collected
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_stat_waitlisted
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_store_deal_row_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_store_deal_row_description_favourite
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_trending_label
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus

// Stable contentType tokens for the Home LazyColumn — let Compose recycle scrolled-off items of the same
// type during a fling instead of re-composing from scratch (fix for fling jank on this mixed list).
private const val CONTENT_TYPE_SECTION_HEADER = "section_header"
private const val CONTENT_TYPE_SECTION_DIVIDER = "section_divider"
private const val CONTENT_TYPE_STAT_ROW = "stat_row"
private const val CONTENT_TYPE_HERO_ROW = "hero_row"
private const val CONTENT_TYPE_DEAL = "deal"
private const val CONTENT_TYPE_RANKED = "ranked"
private const val CONTENT_TYPE_RELEASE = "release"
private const val CONTENT_TYPE_BUNDLE = "bundle"
private const val CONTENT_TYPE_GRID_BUNDLE = "grid_bundle"
private const val CONTENT_TYPE_GRID_RELEASE = "grid_release"
private const val CONTENT_TYPE_SIDE_BY_SIDE = "side_by_side"
private const val CONTENT_TYPE_RECENTLY_VIEWED = "recently_viewed"

// Max number of rows shown per column when the Trending / Waitlisted / Collected lists are laid out
// side-by-side on wide screens: they render as non-lazy Columns (the outer feed is the only vertical
// scroller), so they must be capped rather than showing the full list inline.
private const val SIDE_BY_SIDE_CAP = 8

@Composable
internal fun HomeScreen(
    goToGame: (gameId: String) -> Unit,
    goToGameByTitle: (title: String) -> Unit,
    onViewWaitlist: () -> Unit,
    onViewCollection: () -> Unit,
    onViewBundles: () -> Unit,
    onViewBundle: (bundleId: Int) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val data = viewModel.uiState.collectAsStateWithLifecycle()
    val gamePeek = viewModel.gamePeek.collectAsStateWithLifecycle()
    val accountStats = viewModel.accountStats.collectAsStateWithLifecycle()
    val waitlistIds = viewModel.waitlistIds.collectAsStateWithLifecycle()
    val collectionIds = viewModel.collectionIds.collectAsStateWithLifecycle()
    val ignoredIds = viewModel.ignoredIds.collectAsStateWithLifecycle()
    val stores = viewModel.stores.collectAsStateWithLifecycle()
    val recentlyViewed = viewModel.recentlyViewed.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current
    val snackbarHostState = remember { SnackbarHostState() }
    val widthClass = rememberWidthSizeClass()

    HomeScreenContent(
        widthClass = widthClass,
        data = data.value,
        accountStats = accountStats.value,
        waitlistIds = waitlistIds.value,
        collectionIds = collectionIds.value,
        ignoredIds = ignoredIds.value,
        stores = stores.value,
        recentlyViewed = recentlyViewed.value,
        onRemoveRecentlyViewed = { gameId -> viewModel.onRemoveRecentlyViewed(gameId) },
        onClearRecentlyViewed = { viewModel.onClearRecentlyViewed() },
        snackbarHostState = snackbarHostState,
        gamePeek = gamePeek.value,
        onPeekGame = { gameId, gameName, thumb -> viewModel.peekGame(gameId, gameName, thumb) },
        onPeekRelease = { title, thumb -> viewModel.peekRelease(title, thumb) },
        onToggleWaitlist = { gameId -> viewModel.toggleWaitlist(gameId) },
        onToggleCollection = { gameId -> viewModel.toggleCollection(gameId) },
        onToggleIgnore = { gameId -> viewModel.toggleIgnore(gameId) },
        onViewWaitlist = onViewWaitlist,
        onViewCollection = onViewCollection,
        onViewBundles = onViewBundles,
        onViewBundle = onViewBundle,
        goToGame = goToGame,
        goToGameByTitle = goToGameByTitle,
        onDismissPeek = { viewModel.dismissPeek() },
        onShare = { peekData -> viewModel.onShareClicked(peekData) },
        onRetryPeek = {
            viewModel.gamePeek.value?.let { peek ->
                if (peek.gameId.isNotEmpty()) viewModel.peekGame(peek.gameId, peek.gameName, peek.thumb)
                else viewModel.peekRelease(peek.gameName, peek.thumb)
            }
        },
        goToWeb = goToWeb,
        onRetry = { viewModel.retry() },
    )

    // Collect one-shot UI events and dispatch them.
    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is HomeViewModel.HomeUiEvent.ShareDeal -> platformActions.share(event.text)
            HomeViewModel.HomeUiEvent.SignInRequired -> SignInPromptController.request()
        }
    }
}

@Composable
private fun HomeScreenContent(
    data: HomeViewModel.HomeScreenData,
    accountStats: HomeViewModel.AccountStats?,
    widthClass: WidthSizeClass = WidthSizeClass.COMPACT,
    waitlistIds: ImmutableSet<String>,
    collectionIds: ImmutableSet<String>,
    stores: ImmutableMap<Int, Store>,
    gamePeek: GamePeekSheetData?,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit,
    onPeekRelease: (title: String, thumb: String?) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit,
    onToggleCollection: (gameId: String) -> Unit,
    ignoredIds: ImmutableSet<String> = persistentSetOf(),
    onToggleIgnore: (gameId: String) -> Unit = {},
    recentlyViewed: ImmutableList<RecentlyViewedGame> = persistentListOf(),
    onRemoveRecentlyViewed: (gameId: String) -> Unit = {},
    onClearRecentlyViewed: () -> Unit = {},
    onViewWaitlist: () -> Unit,
    onViewCollection: () -> Unit,
    onViewBundles: () -> Unit,
    onViewBundle: (bundleId: Int) -> Unit,
    goToGame: (gameId: String) -> Unit,
    goToGameByTitle: (title: String) -> Unit,
    onDismissPeek: () -> Unit,
    onShare: (data: GamePeekSheetData.Data) -> Unit,
    onRetryPeek: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val currentOnRetry by rememberUpdatedState(onRetry)
    val errorMessage = stringResource(Res.string.home_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.home_screen_data_loading_error_retry)
    val loadingIndicator = stringResource(Res.string.home_screen_loading_indicator)
    val peekGameId = gamePeek?.gameId?.takeIf { it.isNotEmpty() }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            // The app shell owns the top bar + bottom nav and provides outer padding;
            // zero this Scaffold's insets so its content isn't double-inset under the shell.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding: PaddingValues ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (data.status == HomeScreenStatus.LOADING) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .semantics { contentDescription = loadingIndicator }
                    )
                } else {
                    HomeFeed(
                        widthClass = widthClass,
                        data = data,
                        accountStats = accountStats,
                        waitlistIds = waitlistIds,
                        collectionIds = collectionIds,
                        stores = stores,
                        recentlyViewed = recentlyViewed,
                        onRemoveRecentlyViewed = onRemoveRecentlyViewed,
                        onClearRecentlyViewed = onClearRecentlyViewed,
                        onPeekGame = onPeekGame,
                        onPeekRelease = onPeekRelease,
                        onViewWaitlist = onViewWaitlist,
                        onViewCollection = onViewCollection,
                        onViewBundles = onViewBundles,
                        onViewBundle = onViewBundle,
                    )
                }

                GamePeekSheet(
                    data = gamePeek,
                    isWaitlisted = peekGameId?.let { it in waitlistIds } == true,
                    isCollected = peekGameId?.let { it in collectionIds } == true,
                    isIgnored = peekGameId?.let { it in ignoredIds } == true,
                    onDismiss = onDismissPeek,
                    onShare = onShare,
                    onToggleWaitlist = onToggleWaitlist,
                    onToggleCollection = onToggleCollection,
                    onToggleIgnore = onToggleIgnore,
                    goToWeb = goToWeb,
                    onViewGamePage = { peekData ->
                        if (peekData.gameId.isNotEmpty()) goToGame(peekData.gameId)
                        else goToGameByTitle(peekData.gameName)
                    },
                    onRetry = onRetryPeek,
                )
            }
        }

        if (data.status == HomeScreenStatus.ERROR) {
            LaunchedEffect(snackbarHostState) {
                val result = snackbarHostState.showSnackbar(message = errorMessage, actionLabel = errorRetry)
                if (result == SnackbarResult.ActionPerformed) currentOnRetry()
            }
        }
    }
}

@Composable
private fun HomeFeed(
    data: HomeViewModel.HomeScreenData,
    accountStats: HomeViewModel.AccountStats?,
    waitlistIds: ImmutableSet<String>,
    collectionIds: ImmutableSet<String>,
    stores: ImmutableMap<Int, Store>,
    recentlyViewed: ImmutableList<RecentlyViewedGame>,
    onRemoveRecentlyViewed: (gameId: String) -> Unit,
    onClearRecentlyViewed: () -> Unit,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit,
    onPeekRelease: (title: String, thumb: String?) -> Unit,
    onViewWaitlist: () -> Unit,
    onViewCollection: () -> Unit,
    onViewBundles: () -> Unit,
    onViewBundle: (bundleId: Int) -> Unit,
    widthClass: WidthSizeClass = WidthSizeClass.COMPACT,
) {
    val upcomingChip = stringResource(Res.string.home_screen_release_upcoming)
    val analytics: Analytics = koinInject()

    // The app shell deliberately hands screens a zero bottom inset (the hide-on-scroll bottom bar would
    // otherwise leave a gap when it slides away — see AppShellScaffold), so the feed must reserve the
    // system navigation-bar inset itself as bottom contentPadding. Without it the last section (Bundles)
    // scrolls under the gesture pill / device nav bar.
    val bottomInset = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()

    // Column counts per section, widening with the available space. Compact = today's phone layout.
    val heroCols = when (widthClass) {
        WidthSizeClass.COMPACT -> 2
        WidthSizeClass.MEDIUM -> 3
        WidthSizeClass.EXPANDED -> 4
    }
    val bundleCols = when (widthClass) {
        WidthSizeClass.COMPACT -> 1
        WidthSizeClass.MEDIUM -> 2
        WidthSizeClass.EXPANDED -> 3
    }
    val otherCols = bundleCols // Recommended / New Releases share the bundle 1/2/3 gradation.

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = bottomInset) {
        // Track whether we've already emitted a section so each subsequent section is preceded by a
        // separator + spacing, while the first one stays flush to the top (no stray divider).
        var renderedSection = false

        // 1. Account stat cards (logged-in only) — tap through to the Waitlist / Collection lists.
        accountStats?.let { stats ->
            item(contentType = CONTENT_TYPE_STAT_ROW) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // On wide screens two weighted cards would stretch to enormous widths; cap the row so
                        // they stay a sensible size, left-aligned. Compact keeps the full-width phone layout.
                        .then(if (widthClass != WidthSizeClass.COMPACT) Modifier.widthIn(max = 480.dp) else Modifier)
                        .padding(
                            start = GameDealsCustomTheme.spacing.medium,
                            top = GameDealsCustomTheme.spacing.large,
                            end = GameDealsCustomTheme.spacing.medium,
                            bottom = GameDealsCustomTheme.spacing.medium
                        ),
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                ) {
                    StatCard(
                        label = stringResource(Res.string.home_screen_stat_waitlisted),
                        value = stats.waitlistedCount.toString(),
                        icon = Icons.Filled.Bookmark,
                        onClick = onViewWaitlist,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(Res.string.home_screen_stat_collected),
                        value = stats.collectedCount.toString(),
                        icon = Icons.Filled.LibraryAddCheck,
                        onClick = onViewCollection,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            renderedSection = true
        }

        // 2. Featured hero grid (top-discount deals): 2 tiles/row on phones, widening to 3/4 columns.
        if (data.featuredHero.isNotEmpty()) {
            if (renderedSection) sectionDivider()
            renderedSection = true
            item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(Res.string.home_screen_featured_label)) }
            gridSection(
                items = data.featuredHero,
                columns = heroCols,
                key = { it.dealID },
                contentType = CONTENT_TYPE_HERO_ROW,
            ) { deal, cellModifier ->
                val store = stores[deal.storeID]
                DealHeroTile(
                    deal = deal,
                    storeName = store?.storeName,
                    storeIconUrl = store?.iconUrl,
                    contentDescription = stringResource(Res.string.home_screen_hero_deal_description, deal.title, deal.normalPriceDenominated, deal.salePriceDenominated),
                    onClick = { onPeekGame(deal.gameID, deal.title, deal.artwork.thumbnail) },
                    isWaitlisted = deal.gameID in waitlistIds,
                    isCollected = deal.gameID in collectionIds,
                    modifier = cellModifier,
                )
            }
        }

        // 3. Trending / Most Waitlisted / Most Collected.
        // Compact: three stacked full-width lazy sections (phone layout). Medium: Trending stays
        // full-width, Waitlisted + Collected pair side-by-side. Expanded: all three side-by-side.
        // Side-by-side columns are non-lazy (the feed is the only vertical scroller) so they cap at
        // SIDE_BY_SIDE_CAP. Trending has no "View all" (there's no Trending destination).
        val foldTrending = widthClass == WidthSizeClass.EXPANDED

        if (!foldTrending && data.trending.isNotEmpty()) {
            if (renderedSection) sectionDivider()
            renderedSection = true
            item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(Res.string.home_screen_trending_label)) }
            items(
                count = data.trending.size,
                key = { index -> "trending-${data.trending[index].dealID}" },
                contentType = { CONTENT_TYPE_DEAL },
            ) { index ->
                TrendingDealRow(data.trending[index], stores, waitlistIds, collectionIds, onPeekGame)
            }
        }

        // 3b. Recently-viewed games (#211) — device-local history. Hidden when empty.
        if (recentlyViewed.isNotEmpty()) {
            if (renderedSection) sectionDivider()
            renderedSection = true
            item(contentType = CONTENT_TYPE_RECENTLY_VIEWED) {
                RecentlyViewedCarousel(
                    games = recentlyViewed,
                    onOpen = { game -> onPeekGame(game.gameId, game.title, game.boxart) },
                    onRemove = { game -> onRemoveRecentlyViewed(game.gameId) },
                    onClearAll = onClearRecentlyViewed,
                )
            }
        }

        when (widthClass) {
            WidthSizeClass.EXPANDED -> {
                // All three lists side-by-side in one row.
                if (data.trending.isNotEmpty() || data.mostWaitlisted.isNotEmpty() || data.mostCollected.isNotEmpty()) {
                    if (renderedSection) sectionDivider()
                    renderedSection = true
                    item(contentType = CONTENT_TYPE_SIDE_BY_SIDE) {
                        SideBySideRow {
                            if (data.trending.isNotEmpty()) {
                                DealColumn(
                                    title = stringResource(Res.string.home_screen_trending_label),
                                    deals = data.trending,
                                    stores = stores,
                                    waitlistIds = waitlistIds,
                                    collectionIds = collectionIds,
                                    onPeekGame = onPeekGame,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (data.mostWaitlisted.isNotEmpty()) {
                                RankedColumn(
                                    title = stringResource(Res.string.home_screen_most_waitlisted_label),
                                    games = data.mostWaitlisted,
                                    waitlistIds = waitlistIds,
                                    collectionIds = collectionIds,
                                    onPeekGame = onPeekGame,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (data.mostCollected.isNotEmpty()) {
                                RankedColumn(
                                    title = stringResource(Res.string.home_screen_most_collected_label),
                                    games = data.mostCollected,
                                    waitlistIds = waitlistIds,
                                    collectionIds = collectionIds,
                                    onPeekGame = onPeekGame,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            WidthSizeClass.MEDIUM -> {
                // Waitlisted + Collected paired side-by-side (Trending already emitted full-width above).
                if (data.mostWaitlisted.isNotEmpty() || data.mostCollected.isNotEmpty()) {
                    if (renderedSection) sectionDivider()
                    renderedSection = true
                    item(contentType = CONTENT_TYPE_SIDE_BY_SIDE) {
                        SideBySideRow {
                            if (data.mostWaitlisted.isNotEmpty()) {
                                RankedColumn(
                                    title = stringResource(Res.string.home_screen_most_waitlisted_label),
                                    games = data.mostWaitlisted,
                                    waitlistIds = waitlistIds,
                                    collectionIds = collectionIds,
                                    onPeekGame = onPeekGame,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (data.mostCollected.isNotEmpty()) {
                                RankedColumn(
                                    title = stringResource(Res.string.home_screen_most_collected_label),
                                    games = data.mostCollected,
                                    waitlistIds = waitlistIds,
                                    collectionIds = collectionIds,
                                    onPeekGame = onPeekGame,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            WidthSizeClass.COMPACT -> {
                // 4. Most Waitlisted.
                if (data.mostWaitlisted.isNotEmpty()) {
                    if (renderedSection) sectionDivider()
                    renderedSection = true
                    rankedSection(
                        titleRes = Res.string.home_screen_most_waitlisted_label,
                        games = data.mostWaitlisted,
                        keyPrefix = "waitlisted",
                        waitlistIds = waitlistIds,
                        collectionIds = collectionIds,
                        onPeekGame = onPeekGame,
                    )
                }

                // 5. Most Collected.
                if (data.mostCollected.isNotEmpty()) {
                    if (renderedSection) sectionDivider()
                    renderedSection = true
                    rankedSection(
                        titleRes = Res.string.home_screen_most_collected_label,
                        games = data.mostCollected,
                        keyPrefix = "collected",
                        waitlistIds = waitlistIds,
                        collectionIds = collectionIds,
                        onPeekGame = onPeekGame,
                    )
                }
            }
        }

        // 5.5. Recommended for you (#6) — IGDB games similar to the user's waitlist/collection. Title-only
        // rows like New Releases; the tap resolves the title → game on open (price shown on the game page).
        if (data.recommendations.isNotEmpty()) {
            if (renderedSection) sectionDivider()
            renderedSection = true
            item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(Res.string.home_screen_recommended_label)) }
            gridSection(
                items = data.recommendations,
                columns = otherCols,
                key = { it.id },
                contentType = CONTENT_TYPE_RELEASE,
            ) { rec, cellModifier ->
                val image = rec.coverImageId?.let { igdbImageUrl(it, IgdbImageSize.CoverBig) }
                DealListRow(
                    title = rec.name,
                    contentDescription = stringResource(Res.string.home_screen_recommendation_row_description, rec.name),
                    onClick = {
                        analytics.capture(
                            AnalyticsEvents.RECOMMENDATION_OPENED,
                            mapOf("game_id" to rec.id, "source" to "for_you"),
                        )
                        onPeekRelease(rec.name, image)
                    },
                    imageUrl = image,
                    modifier = cellModifier,
                )
            }
        }

        // 6. New Releases (IGDB) — title-only rows; the price column is a neutral "Upcoming" chip and the
        // tap resolves the title → game on open (peek with a no-deals state when nothing is sold yet).
        if (data.releases.isNotEmpty()) {
            if (renderedSection) sectionDivider()
            renderedSection = true
            item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(Res.string.home_screen_new_releases_label)) }
            gridSection(
                items = data.releases,
                columns = otherCols,
                key = { it.title },
                contentType = CONTENT_TYPE_RELEASE,
            ) { release, cellModifier ->
                DealListRow(
                    title = release.title,
                    contentDescription = stringResource(Res.string.home_screen_release_row_description, release.title),
                    onClick = { onPeekRelease(release.title, release.image) },
                    imageUrl = release.image,
                    neutralChip = upcomingChip,
                    modifier = cellModifier,
                )
            }
        }

        // 7. Bundles.
        if (data.bundles.isNotEmpty()) {
            if (renderedSection) sectionDivider()
            renderedSection = true
            item(contentType = CONTENT_TYPE_SECTION_HEADER) {
                SectionHeader(
                    text = stringResource(Res.string.home_screen_bundles_label),
                    actionText = stringResource(Res.string.home_screen_all_bundles_label),
                    onActionClick = onViewBundles,
                )
            }
            if (bundleCols == 1) {
                items(
                    count = data.bundles.size,
                    key = { index -> "bundle-${data.bundles[index].id}" },
                    contentType = { CONTENT_TYPE_BUNDLE },
                ) { index ->
                    HomeBundleRow(data.bundles[index]) { onViewBundle(data.bundles[index].id) }
                }
            } else {
                gridSection(
                    items = data.bundles,
                    columns = bundleCols,
                    key = { it.id },
                    contentType = CONTENT_TYPE_GRID_BUNDLE,
                ) { bundle, cellModifier ->
                    BundleListRow(bundle = bundle, onClick = { onViewBundle(bundle.id) }, modifier = cellModifier)
                }
            }
        }
    }
}

/**
 * A separator emitted between two consecutive Home sections: a hairline [HorizontalDivider] with
 * vertical breathing room so the distinct feed sections read as clearly separated blocks. Inset
 * horizontally to align with the section content rather than running fully edge-to-edge.
 */
private fun LazyListScope.sectionDivider() {
    item(contentType = CONTENT_TYPE_SECTION_DIVIDER) {
        HorizontalDivider(
            modifier = Modifier.padding(
                horizontal = GameDealsCustomTheme.spacing.medium,
                vertical = GameDealsCustomTheme.spacing.medium,
            ),
        )
    }
}

private fun LazyListScope.rankedSection(
    titleRes: StringResource,
    games: ImmutableList<RankedGame>,
    keyPrefix: String,
    waitlistIds: ImmutableSet<String>,
    collectionIds: ImmutableSet<String>,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit,
) {
    if (games.isEmpty()) return
    item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(titleRes)) }
    items(
        count = games.size,
        key = { index -> "$keyPrefix-${games[index].gameId}" },
        contentType = { CONTENT_TYPE_RANKED },
    ) { index ->
        RankedGameRow(games[index], waitlistIds, collectionIds, onPeekGame)
    }
}

/**
 * A row of [SIDE_BY_SIDE_CAP]-capped vertical lists shown next to each other on wide screens
 * (Trending / Waitlisted / Collected). The lists are plain [Column]s — not lazy — because the Home
 * feed's outer [LazyColumn] is the only vertical scroller; each caller sizes its column with
 * `Modifier.weight(1f)`.
 */
@Composable
private fun SideBySideRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        content()
    }
}

/** A single capped vertical list of Trending [Deal]s used inside [SideBySideRow]. */
@Composable
private fun DealColumn(
    title: String,
    deals: ImmutableList<Deal>,
    stores: ImmutableMap<Int, Store>,
    waitlistIds: ImmutableSet<String>,
    collectionIds: ImmutableSet<String>,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(text = title)
        deals.take(SIDE_BY_SIDE_CAP).forEach { deal ->
            TrendingDealRow(deal, stores, waitlistIds, collectionIds, onPeekGame)
        }
    }
}

/** A single capped vertical list of [RankedGame]s used inside [SideBySideRow]. */
@Composable
private fun RankedColumn(
    title: String,
    games: ImmutableList<RankedGame>,
    waitlistIds: ImmutableSet<String>,
    collectionIds: ImmutableSet<String>,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(text = title)
        games.take(SIDE_BY_SIDE_CAP).forEach { game ->
            RankedGameRow(game, waitlistIds, collectionIds, onPeekGame)
        }
    }
}

/** The Trending deal row, shared by the full-width lazy section and the side-by-side [DealColumn]. */
@Composable
private fun TrendingDealRow(
    deal: Deal,
    stores: ImmutableMap<Int, Store>,
    waitlistIds: ImmutableSet<String>,
    collectionIds: ImmutableSet<String>,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit,
) {
    val store = stores[deal.storeID]
    val isWaitlisted = deal.gameID in waitlistIds
    val isCollected = deal.gameID in collectionIds
    DealListRow(
        title = deal.title,
        contentDescription = stringResource(
            if (isWaitlisted) Res.string.home_screen_store_deal_row_description_favourite
            else Res.string.home_screen_store_deal_row_description,
            deal.title, deal.normalPriceDenominated, deal.salePriceDenominated,
        ),
        onClick = { onPeekGame(deal.gameID, deal.title, deal.artwork.thumbnail) },
        imageUrl = deal.artwork.thumbnail,
        salePrice = deal.salePriceDenominated,
        regularPrice = deal.normalPriceDenominated,
        discountPercent = deal.savings.roundToInt(),
        hasVoucher = deal.hasVoucher,
        isNewHistoricalLow = deal.isNewHistoricalLow,
        isStoreLow = deal.isStoreLow,
        storeName = store?.storeName,
        storeIconUrl = store?.iconUrl,
        isWaitlisted = isWaitlisted,
        isCollected = isCollected,
    )
}

/** The ranked-game row, shared by [rankedSection] (Compact) and the side-by-side [RankedColumn]. */
@Composable
private fun RankedGameRow(
    game: RankedGame,
    waitlistIds: ImmutableSet<String>,
    collectionIds: ImmutableSet<String>,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit,
) {
    val isWaitlisted = game.gameId in waitlistIds
    val isCollected = game.gameId in collectionIds
    val cd = game.priceDenominated?.let { stringResource(Res.string.home_screen_ranked_game_description, game.title, it) }
        ?: stringResource(Res.string.home_screen_ranked_game_description_no_price, game.title)
    // Same anatomy as the Trending rows: store label, struck regular price, discount + flag badges,
    // and the passive waitlist/collection badges — all enriched off the best current deal in the HomeViewModel.
    DealListRow(
        title = game.title,
        contentDescription = cd,
        onClick = { onPeekGame(game.gameId, game.title, game.artwork.thumbnail) },
        imageUrl = game.artwork.thumbnail,
        salePrice = game.priceDenominated,
        regularPrice = game.regularPriceDenominated,
        discountPercent = game.cutPercent ?: 0,
        hasVoucher = game.hasVoucher,
        isNewHistoricalLow = game.isNewHistoricalLow,
        isStoreLow = game.isStoreLow,
        storeName = game.storeName,
        isWaitlisted = isWaitlisted,
        isCollected = isCollected,
    )
}

/**
 * Emits a section as a grid of [columns] cells per row inside the Home [LazyColumn]. Each grid row is
 * one lazy item: a [Row] of `weight(1f)` cells with a short final row padded by [Spacer]s so the last
 * cells keep their fractional width. With `columns <= 1` it degrades to a plain one-item-per-row list
 * (each item gets `Modifier` unchanged), so callers can pass a per-tier column count uniformly.
 */
private fun <T> LazyListScope.gridSection(
    items: List<T>,
    columns: Int,
    key: (T) -> Any,
    contentType: String,
    itemContent: @Composable (item: T, cellModifier: Modifier) -> Unit,
) {
    if (columns <= 1) {
        items(
            count = items.size,
            key = { index -> "grid1-$contentType-${key(items[index])}" },
            contentType = { contentType },
        ) { index -> itemContent(items[index], Modifier) }
        return
    }
    val rows = items.chunked(columns)
    items(
        count = rows.size,
        key = { index -> "grid-$contentType-${key(rows[index].first())}" },
        contentType = { contentType },
    ) { index ->
        val row = rows[index]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            row.forEach { item -> itemContent(item, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * A light, ITAD-style section header (UI Improvements #252): a prominent `headlineSmall` title
 * anchored by a slim teal accent bar so the user is clearly aware which section they're scrolling
 * through, with no full-bleed primary block, and an optional inline "View all" affordance on the
 * trailing edge (replacing the old separate full-width [Button] row). The title keeps `heading()`
 * semantics so TalkBack still navigates by section; the action is a separate, independently
 * actionable node.
 */
@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = GameDealsCustomTheme.spacing.medium,
                end = GameDealsCustomTheme.spacing.small,
                top = GameDealsCustomTheme.spacing.large,
                bottom = GameDealsCustomTheme.spacing.medium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slim teal accent bar anchoring the section title — adds ITAD identity while keeping the
        // title itself high-contrast onSurface text.
        Box(
            modifier = Modifier
                .padding(end = GameDealsCustomTheme.spacing.medium)
                .height(22.dp)
                .width(4.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(text = actionText)
            }
        }
    }
}

@Composable
private fun HomeBundleRow(
    bundle: Bundle,
    onClick: () -> Unit,
) {
    // The Home bundle section reuses the same Card row as the Bundles tab (art strip + metadata), with
    // section padding so it isn't edge-to-edge in the Home LazyColumn.
    BundleListRow(
        bundle = bundle,
        onClick = onClick,
        modifier = Modifier.padding(
            horizontal = GameDealsCustomTheme.spacing.medium,
            vertical = GameDealsCustomTheme.spacing.small,
        ),
    )
}

private fun previewSuccessData(): HomeViewModel.HomeScreenData = HomeViewModel.HomeScreenData(
    status = HomeScreenStatus.DATA,
    featuredHero = listOf(
        PreviewDeal,
        PreviewDeal.copy(dealID = "hero-2", title = "Hollow Knight", salePriceDenominated = "$7.49", normalPriceDenominated = "$14.99", gameID = "22222"),
        PreviewDeal.copy(dealID = "hero-3", title = "Stardew Valley", salePriceDenominated = "$8.99", normalPriceDenominated = "$14.99", gameID = "33333"),
        PreviewDeal.copy(dealID = "hero-4", title = "Elden Ring", salePriceDenominated = "$41.99", normalPriceDenominated = "$59.99", gameID = "hero4"),
        PreviewDeal.copy(dealID = "hero-5", title = "Disco Elysium", salePriceDenominated = "$9.99", normalPriceDenominated = "$39.99", gameID = "hero5"),
        PreviewDeal.copy(dealID = "hero-6", title = "Hades", salePriceDenominated = "$12.49", normalPriceDenominated = "$24.99", gameID = "hero6"),
        PreviewDeal.copy(dealID = "hero-7", title = "Outer Wilds", salePriceDenominated = "$11.99", normalPriceDenominated = "$24.99", gameID = "hero7"),
        PreviewDeal.copy(dealID = "hero-8", title = "Dave the Diver", salePriceDenominated = "$14.99", normalPriceDenominated = "$19.99", gameID = "hero8"),
        PreviewDeal.copy(dealID = "hero-9", title = "Terraria", salePriceDenominated = "$4.99", normalPriceDenominated = "$9.99", gameID = "hero9"),
        PreviewDeal.copy(dealID = "hero-10", title = "Cuphead", salePriceDenominated = "$13.39", normalPriceDenominated = "$19.99", gameID = "hero10"),
        PreviewDeal.copy(dealID = "hero-11", title = "Slay the Spire", salePriceDenominated = "$5.99", normalPriceDenominated = "$24.99", gameID = "hero11"),
        PreviewDeal.copy(dealID = "hero-12", title = "Portal 2", salePriceDenominated = "$0.99", normalPriceDenominated = "$9.99", gameID = "hero12"),
    ).toImmutableList(),
    trending = listOf(
        PreviewDeal.copy(dealID = "trend-1", title = "Celeste", salePriceDenominated = "$4.99", normalPriceDenominated = "$19.99", gameID = "44444"),
    ).toImmutableList(),
    mostWaitlisted = listOf(
        RankedGame(gameId = "w1", title = "Baldur's Gate 3", priceDenominated = "$59.99", cutPercent = 0, storeName = "Steam"),
        RankedGame(
            gameId = "w2", title = "Cyberpunk 2077", priceDenominated = "$29.99", regularPriceDenominated = "$59.99",
            cutPercent = 50, storeName = "GOG", isNewHistoricalLow = true,
        ),
    ).toImmutableList(),
    mostCollected = listOf(
        RankedGame(
            gameId = "c1", title = "Skyrim Special Edition", priceDenominated = "$19.99", regularPriceDenominated = "$39.99",
            cutPercent = 50, storeName = "GreenManGaming", hasVoucher = true,
        ),
    ).toImmutableList(),
    releases = listOf(
        PreviewRelease,
        PreviewRelease.copy(title = "Silksong"),
    ).toImmutableList(),
)

// Shared success-state preview body. The width tier is forced via [widthClass] (not read from the
// window), so the Medium/Expanded previews exercise the wide layouts even though the CMP @Preview
// canvas defaults to phone width — resize the preview in the IDE to see the columns breathe.
@Composable
private fun HomeSuccessPreview(widthClass: WidthSizeClass, darkTheme: Boolean = false) {
    GameDealsTheme(darkTheme = darkTheme) {
        HomeScreenContent(
            data = previewSuccessData(),
            widthClass = widthClass,
            accountStats = HomeViewModel.AccountStats(waitlistedCount = 12, collectedCount = 47),
            waitlistIds = persistentSetOf("22222"),
            collectionIds = persistentSetOf("33333"),
            stores = persistentMapOf(PreviewStore.storeID to PreviewStore),
            gamePeek = null,
            onPeekGame = { _, _, _ -> },
            onPeekRelease = { _, _ -> },
            onToggleWaitlist = {},
            onToggleCollection = {},
            onViewWaitlist = {},
            onViewCollection = {},
            onViewBundles = {},
            onViewBundle = {},
            goToGame = {},
            goToGameByTitle = {},
            onDismissPeek = {},
            onShare = {},
            onRetryPeek = {},
            goToWeb = { _, _ -> },
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContent_Success_Preview() = HomeSuccessPreview(WidthSizeClass.COMPACT)

@Preview
@Composable
private fun HomeScreenContent_Success_Dark_Preview() = HomeSuccessPreview(WidthSizeClass.COMPACT, darkTheme = true)

@Preview
@Composable
private fun HomeScreenContent_Success_Medium_Preview() = HomeSuccessPreview(WidthSizeClass.MEDIUM)

@Preview
@Composable
private fun HomeScreenContent_Success_Expanded_Preview() = HomeSuccessPreview(WidthSizeClass.EXPANDED)

@Preview
@Composable
private fun HomeScreenContent_Loading_Preview() {
    GameDealsTheme {
        HomeScreenContent(
            data = HomeViewModel.HomeScreenData(status = HomeScreenStatus.LOADING),
            accountStats = null,
            waitlistIds = persistentSetOf(),
            collectionIds = persistentSetOf(),
            stores = persistentMapOf(),
            gamePeek = null,
            onPeekGame = { _, _, _ -> },
            onPeekRelease = { _, _ -> },
            onToggleWaitlist = {},
            onToggleCollection = {},
            onViewWaitlist = {},
            onViewCollection = {},
            onViewBundles = {},
            onViewBundle = {},
            goToGame = {},
            goToGameByTitle = {},
            onDismissPeek = {},
            onShare = {},
            onRetryPeek = {},
            goToWeb = { _, _ -> },
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContent_Error_Preview() {
    GameDealsTheme {
        HomeScreenContent(
            data = HomeViewModel.HomeScreenData(status = HomeScreenStatus.ERROR),
            accountStats = null,
            waitlistIds = persistentSetOf(),
            collectionIds = persistentSetOf(),
            stores = persistentMapOf(),
            gamePeek = null,
            onPeekGame = { _, _, _ -> },
            onPeekRelease = { _, _ -> },
            onToggleWaitlist = {},
            onToggleCollection = {},
            onViewWaitlist = {},
            onViewCollection = {},
            onViewBundles = {},
            onViewBundle = {},
            goToGame = {},
            goToGameByTitle = {},
            onDismissPeek = {},
            onShare = {},
            onRetryPeek = {},
            goToWeb = { _, _ -> },
            onRetry = {},
        )
    }
}
