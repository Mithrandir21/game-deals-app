@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
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
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.PreviewRelease
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.BundleListRow
import pm.bam.gamedeals.common.ui.components.DealHeroTile
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.deal.GamePeekSheet
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_waitlist_sign_in_required
import pm.bam.gamedeals.common.ui.home.StatCard
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.models.Store
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
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_release_row_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_release_upcoming
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_stat_collected
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_stat_waitlisted
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_store_deal_row_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_store_deal_row_description_favourite
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_trending_label
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes

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
    val waitlistIds = viewModel.waitlistIds.collectAsStateWithLifecycle()
    val collectionIds = viewModel.collectionIds.collectAsStateWithLifecycle()
    val ignoredIds = viewModel.ignoredIds.collectAsStateWithLifecycle()
    val stores = viewModel.stores.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current
    val snackbarHostState = remember { SnackbarHostState() }
    val signInRequired = stringResource(CommonRes.string.deal_waitlist_sign_in_required)

    HomeScreenContent(
        data = data.value,
        waitlistIds = waitlistIds.value,
        collectionIds = collectionIds.value,
        ignoredIds = ignoredIds.value,
        stores = stores.value,
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
            HomeViewModel.HomeUiEvent.SignInRequired -> snackbarHostState.showSnackbar(signInRequired)
        }
    }
}

@Composable
private fun HomeScreenContent(
    data: HomeViewModel.HomeScreenData,
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
            // The app shell (epic #219) owns the top bar + bottom nav and provides outer padding;
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
                        data = data,
                        waitlistIds = waitlistIds,
                        collectionIds = collectionIds,
                        stores = stores,
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
    waitlistIds: ImmutableSet<String>,
    collectionIds: ImmutableSet<String>,
    stores: ImmutableMap<Int, Store>,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit,
    onPeekRelease: (title: String, thumb: String?) -> Unit,
    onViewWaitlist: () -> Unit,
    onViewCollection: () -> Unit,
    onViewBundles: () -> Unit,
    onViewBundle: (bundleId: Int) -> Unit,
) {
    val upcomingChip = stringResource(Res.string.home_screen_release_upcoming)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Track whether we've already emitted a section so each subsequent section is preceded by a
        // separator + spacing, while the first one stays flush to the top (no stray divider).
        var renderedSection = false

        // 1. Account stat cards (logged-in only) — tap through to the Waitlist / Collection lists.
        data.accountStats?.let { stats ->
            item(contentType = CONTENT_TYPE_STAT_ROW) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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

        // 2. Featured hero grid (top-discount deals), two tiles per row.
        if (data.featuredHero.isNotEmpty()) {
            if (renderedSection) sectionDivider()
            renderedSection = true
            item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(Res.string.home_screen_featured_label)) }

            val heroRows = data.featuredHero.chunked(2)
            items(
                count = heroRows.size,
                key = { index -> "hero-${heroRows[index].first().dealID}" },
                contentType = { CONTENT_TYPE_HERO_ROW },
            ) { index ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                ) {
                    heroRows[index].forEach { deal ->
                        val store = stores[deal.storeID]
                        DealHeroTile(
                            deal = deal,
                            storeName = store?.storeName,
                            storeIconUrl = store?.iconUrl,
                            contentDescription = stringResource(Res.string.home_screen_hero_deal_description, deal.title, deal.normalPriceDenominated, deal.salePriceDenominated),
                            onClick = { onPeekGame(deal.gameID, deal.title, deal.thumb) },
                            isWaitlisted = deal.gameID in waitlistIds,
                            isCollected = deal.gameID in collectionIds,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Pad the final row when there's an odd number of tiles so the last tile keeps its half-width.
                    if (heroRows[index].size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // 3. Trending deals.
        if (data.trending.isNotEmpty()) {
            if (renderedSection) sectionDivider()
            renderedSection = true
            item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(Res.string.home_screen_trending_label)) }
            items(
                count = data.trending.size,
                key = { index -> "trending-${data.trending[index].dealID}" },
                contentType = { CONTENT_TYPE_DEAL },
            ) { index ->
                val deal = data.trending[index]
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
                    onClick = { onPeekGame(deal.gameID, deal.title, deal.thumb) },
                    imageUrl = deal.thumb,
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
        }

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

        // 6. New Releases (IGDB) — title-only rows; the price column is a neutral "Upcoming" chip and the
        // tap resolves the title → game on open (peek with a no-deals state when nothing is sold yet).
        if (data.releases.isNotEmpty()) {
            if (renderedSection) sectionDivider()
            renderedSection = true
            item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(Res.string.home_screen_new_releases_label)) }
            items(
                count = data.releases.size,
                key = { index -> "release-${data.releases[index].title}" },
                contentType = { CONTENT_TYPE_RELEASE },
            ) { index ->
                val release = data.releases[index]
                DealListRow(
                    title = release.title,
                    contentDescription = stringResource(Res.string.home_screen_release_row_description, release.title),
                    onClick = { onPeekRelease(release.title, release.image) },
                    imageUrl = release.image,
                    neutralChip = upcomingChip,
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
            items(
                count = data.bundles.size,
                key = { index -> "bundle-${data.bundles[index].id}" },
                contentType = { CONTENT_TYPE_BUNDLE },
            ) { index ->
                HomeBundleRow(data.bundles[index]) { onViewBundle(data.bundles[index].id) }
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
        val game = games[index]
        val isWaitlisted = game.gameId in waitlistIds
        val isCollected = game.gameId in collectionIds
        val cd = game.priceDenominated?.let { stringResource(Res.string.home_screen_ranked_game_description, game.title, it) }
            ?: stringResource(Res.string.home_screen_ranked_game_description_no_price, game.title)
        // Same anatomy as the Trending rows: store label, struck regular price, discount + flag badges,
        // and the passive waitlist/collection badges — all enriched off the best current deal in the HomeViewModel.
        DealListRow(
            title = game.title,
            contentDescription = cd,
            onClick = { onPeekGame(game.gameId, game.title, game.boxart) },
            imageUrl = game.boxart,
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
}

/**
 * A light, ITAD-style section header (UI Improvements #252): the title styled as a heading with
 * no full-bleed primary block, and an optional inline "View all" affordance on the trailing edge
 * (replacing the old separate full-width [Button] row). The title keeps `heading()` semantics so
 * TalkBack still navigates by section; the action is a separate, independently actionable node.
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
                top = GameDealsCustomTheme.spacing.medium,
                bottom = GameDealsCustomTheme.spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
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
    accountStats = HomeViewModel.AccountStats(waitlistedCount = 12, collectedCount = 47),
    featuredHero = listOf(
        PreviewDeal,
        PreviewDeal.copy(dealID = "hero-2", title = "Hollow Knight", salePriceDenominated = "$7.49", normalPriceDenominated = "$14.99", gameID = "22222"),
        PreviewDeal.copy(dealID = "hero-3", title = "Stardew Valley", salePriceDenominated = "$8.99", normalPriceDenominated = "$14.99", gameID = "33333"),
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

@Preview
@Composable
private fun HomeScreenContent_Success_Preview() {
    GameDealsTheme {
        HomeScreenContent(
            data = previewSuccessData(),
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
private fun HomeScreenContent_Success_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        HomeScreenContent(
            data = previewSuccessData(),
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
private fun HomeScreenContent_Loading_Preview() {
    GameDealsTheme {
        HomeScreenContent(
            data = HomeViewModel.HomeScreenData(status = HomeScreenStatus.LOADING),
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
