@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.PreviewGiveaway
import pm.bam.gamedeals.common.ui.PreviewRelease
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.DealHeroTile
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.deal.DealBottomSheet
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_waitlist_sign_in_required
import pm.bam.gamedeals.common.ui.generated.resources.open_in_new
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb
import pm.bam.gamedeals.common.ui.home.StatCard
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_all_bundles_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_all_giveaways_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_bundle_row_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_bundles_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_featured_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_game_image
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_free_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_opens_externally
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_row_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_row_description_no_platforms
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_row_description_no_worth
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_row_description_no_worth_no_platforms
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaways_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_hero_deal_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_loading_indicator
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_most_collected_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_most_waitlisted_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_new_releases_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_ranked_game_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_ranked_game_description_no_price
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
private const val CONTENT_TYPE_STAT_ROW = "stat_row"
private const val CONTENT_TYPE_HERO_ROW = "hero_row"
private const val CONTENT_TYPE_DEAL = "deal"
private const val CONTENT_TYPE_RANKED = "ranked"
private const val CONTENT_TYPE_RELEASE = "release"
private const val CONTENT_TYPE_GIVEAWAY = "giveaway"
private const val CONTENT_TYPE_BUNDLE = "bundle"

@Composable
internal fun HomeScreen(
    goToGame: (gameId: String) -> Unit,
    onViewGiveaways: () -> Unit,
    onViewBundles: () -> Unit,
    onViewBundle: (bundleId: Int) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val data = viewModel.uiState.collectAsStateWithLifecycle()
    val dealDetails = viewModel.dealDetails.collectAsStateWithLifecycle()
    val waitlistIds = viewModel.waitlistIds.collectAsStateWithLifecycle()
    val stores = viewModel.stores.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current
    val snackbarHostState = remember { SnackbarHostState() }
    val signInRequired = stringResource(CommonRes.string.deal_waitlist_sign_in_required)
    val releaseUnavailable = stringResource(Res.string.home_screen_data_loading_error_msg)

    HomeScreenContent(
        onReleaseTitle = { title -> viewModel.onReleaseGame(title) },
        data = data.value,
        waitlistIds = waitlistIds.value,
        stores = stores.value,
        snackbarHostState = snackbarHostState,
        dealDetails = dealDetails.value,
        onViewDealDetails = { dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated, dealUrl ->
            viewModel.loadDealDetails(dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated, dealUrl)
        },
        onToggleWaitlist = { gameId -> viewModel.toggleWaitlist(gameId) },
        goToGame = goToGame,
        onViewGiveaways = onViewGiveaways,
        onViewBundles = onViewBundles,
        onViewBundle = onViewBundle,
        onDismissDealDetails = { viewModel.dismissDealDetails() },
        onShareDealDetails = { sheetData -> viewModel.onShareDealClicked(sheetData) },
        onToggleDealWaitlist = { sheetData -> viewModel.toggleWaitlistFromDeal(sheetData) },
        goToWeb = goToWeb,
        goToGameDetails = goToGameDetails,
        goToGameDetailsByTitle = goToGameDetailsByTitle,
        onRetry = { viewModel.retry() },
    )

    // Collect one-shot UI events and dispatch them.
    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is HomeViewModel.HomeUiEvent.ShareDeal -> platformActions.share(event.text)
            HomeViewModel.HomeUiEvent.SignInRequired -> snackbarHostState.showSnackbar(signInRequired)
            HomeViewModel.HomeUiEvent.ReleaseUnavailable -> snackbarHostState.showSnackbar(releaseUnavailable)
        }
    }
}

@Composable
private fun HomeScreenContent(
    onReleaseTitle: (title: String) -> Unit,
    data: HomeViewModel.HomeScreenData,
    waitlistIds: ImmutableSet<String>,
    stores: ImmutableMap<Int, Store>,
    dealDetails: DealBottomSheetData?,
    onViewDealDetails: (dealId: String, dealStoreId: Int, dealGameId: String, dealTitle: String, dealPriceDenominated: String, dealUrl: String) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit,
    goToGame: (gameId: String) -> Unit,
    onViewGiveaways: () -> Unit,
    onViewBundles: () -> Unit,
    onViewBundle: (bundleId: Int) -> Unit,
    onDismissDealDetails: () -> Unit,
    onShareDealDetails: (data: DealBottomSheetData) -> Unit,
    onToggleDealWaitlist: (data: DealBottomSheetData.DealDetailsData) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val currentOnRetry by rememberUpdatedState(onRetry)
    val errorMessage = stringResource(Res.string.home_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.home_screen_data_loading_error_retry)
    val loadingIndicator = stringResource(Res.string.home_screen_loading_indicator)

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
                        stores = stores,
                        onReleaseTitle = onReleaseTitle,
                        onViewDealDetails = onViewDealDetails,
                        onToggleWaitlist = onToggleWaitlist,
                        goToGame = goToGame,
                        onViewGiveaways = onViewGiveaways,
                        onViewBundles = onViewBundles,
                        onViewBundle = onViewBundle,
                        goToWeb = goToWeb,
                    )
                }

                DealBottomSheet(
                    data = dealDetails,
                    isWaitlisted = dealDetails?.gameId?.let { it in waitlistIds } == true,
                    onDismiss = { onDismissDealDetails() },
                    onShare = { sheetData -> onShareDealDetails(sheetData) },
                    onToggleWaitlist = { sheetData -> onToggleDealWaitlist(sheetData) },
                    goToWeb = goToWeb,
                    goToGameDetails = goToGameDetails,
                    goToGameDetailsByTitle = goToGameDetailsByTitle,
                    onRetryDealDetails = {
                        dealDetails?.let { onViewDealDetails(it.dealId, it.store.storeID, it.gameId, it.gameName, it.gameSalesPriceDenominated, it.dealUrl) }
                    },
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
    stores: ImmutableMap<Int, Store>,
    onReleaseTitle: (title: String) -> Unit,
    onViewDealDetails: (dealId: String, dealStoreId: Int, dealGameId: String, dealTitle: String, dealPriceDenominated: String, dealUrl: String) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit,
    goToGame: (gameId: String) -> Unit,
    onViewGiveaways: () -> Unit,
    onViewBundles: () -> Unit,
    onViewBundle: (bundleId: Int) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    val addToWaitlistCd = stringResource(CommonRes.string.deal_favourite_add_action)
    val removeFromWaitlistCd = stringResource(CommonRes.string.deal_favourite_remove_action)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 1. Account stat cards (logged-in only).
        data.accountStats?.let { stats ->
            item(contentType = CONTENT_TYPE_STAT_ROW) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(GameDealsCustomTheme.spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                ) {
                    StatCard(
                        label = stringResource(Res.string.home_screen_stat_waitlisted),
                        value = stats.waitlistedCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(Res.string.home_screen_stat_collected),
                        value = stats.collectedCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 2. Featured hero grid (top-discount deals), two tiles per row.
        if (data.featuredHero.isNotEmpty()) {
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
                            onClick = { onViewDealDetails(deal.dealID, deal.storeID, deal.gameID, deal.title, deal.salePriceDenominated, deal.url) },
                            isWaitlisted = deal.gameID in waitlistIds,
                            onToggleWaitlist = { onToggleWaitlist(deal.gameID) },
                            addToWaitlistContentDescription = addToWaitlistCd,
                            removeFromWaitlistContentDescription = removeFromWaitlistCd,
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
            item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(Res.string.home_screen_trending_label)) }
            items(
                count = data.trending.size,
                key = { index -> "trending-${data.trending[index].dealID}" },
                contentType = { CONTENT_TYPE_DEAL },
            ) { index ->
                val deal = data.trending[index]
                val store = stores[deal.storeID]
                val isWaitlisted = deal.gameID in waitlistIds
                DealListRow(
                    title = deal.title,
                    contentDescription = stringResource(
                        if (isWaitlisted) Res.string.home_screen_store_deal_row_description_favourite
                        else Res.string.home_screen_store_deal_row_description,
                        deal.title, deal.normalPriceDenominated, deal.salePriceDenominated,
                    ),
                    onClick = { onViewDealDetails(deal.dealID, deal.storeID, deal.gameID, deal.title, deal.salePriceDenominated, deal.url) },
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
                    onToggleWaitlist = { onToggleWaitlist(deal.gameID) },
                    addToWaitlistContentDescription = addToWaitlistCd,
                    removeFromWaitlistContentDescription = removeFromWaitlistCd,
                )
            }
        }

        // 4. Most Waitlisted.
        rankedSection(
            titleRes = Res.string.home_screen_most_waitlisted_label,
            games = data.mostWaitlisted,
            keyPrefix = "waitlisted",
            goToGame = goToGame,
        )

        // 5. Most Collected.
        rankedSection(
            titleRes = Res.string.home_screen_most_collected_label,
            games = data.mostCollected,
            keyPrefix = "collected",
            goToGame = goToGame,
        )

        // 6. New Releases (IGDB).
        if (data.releases.isNotEmpty()) {
            item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(Res.string.home_screen_new_releases_label)) }
            items(
                count = data.releases.size,
                key = { index -> "release-${data.releases[index].title}" },
                contentType = { CONTENT_TYPE_RELEASE },
            ) { index ->
                ReleaseRow(data.releases[index], onReleaseTitle)
            }
        }

        // 7. Bundles.
        if (data.bundles.isNotEmpty()) {
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

        // 8. Giveaways.
        if (data.giveaways.isNotEmpty()) {
            item(contentType = CONTENT_TYPE_SECTION_HEADER) {
                SectionHeader(
                    text = stringResource(Res.string.home_screen_giveaways_label),
                    actionText = stringResource(Res.string.home_screen_all_giveaways_label),
                    onActionClick = onViewGiveaways,
                )
            }
            items(
                count = data.giveaways.size,
                key = { index -> "giveaway-${data.giveaways[index].id}" },
                contentType = { CONTENT_TYPE_GIVEAWAY },
            ) { index ->
                GiveawayRow(data.giveaways[index]) { url -> goToWeb(url, data.giveaways[index].title) }
            }
        }
    }
}

private fun LazyListScope.rankedSection(
    titleRes: StringResource,
    games: ImmutableList<RankedGame>,
    keyPrefix: String,
    goToGame: (gameId: String) -> Unit,
) {
    if (games.isEmpty()) return
    item(contentType = CONTENT_TYPE_SECTION_HEADER) { SectionHeader(stringResource(titleRes)) }
    items(
        count = games.size,
        key = { index -> "$keyPrefix-${games[index].gameId}" },
        contentType = { CONTENT_TYPE_RANKED },
    ) { index ->
        val game = games[index]
        val cd = game.priceDenominated?.let { stringResource(Res.string.home_screen_ranked_game_description, game.title, it) }
            ?: stringResource(Res.string.home_screen_ranked_game_description_no_price, game.title)
        DealListRow(
            title = game.title,
            contentDescription = cd,
            onClick = { goToGame(game.gameId) },
            imageUrl = game.boxart,
            salePrice = game.priceDenominated,
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
private fun ReleaseRow(
    release: Release,
    onReleaseTitle: (title: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onReleaseTitle(release.title) }
            .padding(bottom = GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = release.image,
            contentDescription = stringResource(Res.string.home_screen_game_image, release.title),
            contentScale = ContentScale.Fit,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                .height(60.dp)
                .width(100.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = GameDealsCustomTheme.spacing.small),
            textAlign = TextAlign.Start,
            text = release.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeBundleRow(
    bundle: Bundle,
    onClick: () -> Unit,
) {
    val rowCd = stringResource(Res.string.home_screen_bundle_row_description, bundle.title, bundle.storeName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small)
            .semantics(mergeDescendants = true) { contentDescription = rowCd },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bundle.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = bundle.storeName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        bundle.priceDenominated?.let { price ->
            Text(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.extraSmall)
                    .padding(GameDealsCustomTheme.spacing.medium),
                text = price,
            )
        }
    }
}

@Composable
private fun GiveawayRow(
    giveaway: Giveaway,
    onGiveawayTitle: (url: String) -> Unit,
) {
    val typeLabel = giveaway.type.displayLabel()
    val freeLabel = stringResource(Res.string.home_screen_giveaway_free_label)
    val worth = giveaway.worthDenominated
    val platformsLabel = remember(giveaway.platforms) {
        giveaway.platforms.joinToString(", ") { it.platformValue }
    }
    val rowCd = when {
        worth != null && platformsLabel.isNotEmpty() -> stringResource(
            Res.string.home_screen_giveaway_row_description,
            giveaway.title, worth, typeLabel, platformsLabel,
        )
        worth != null -> stringResource(
            Res.string.home_screen_giveaway_row_description_no_platforms,
            giveaway.title, worth, typeLabel,
        )
        platformsLabel.isNotEmpty() -> stringResource(
            Res.string.home_screen_giveaway_row_description_no_worth,
            giveaway.title, typeLabel, platformsLabel,
        )
        else -> stringResource(
            Res.string.home_screen_giveaway_row_description_no_worth_no_platforms,
            giveaway.title, typeLabel,
        )
    }
    val opensExternallyCd = stringResource(Res.string.home_screen_giveaway_opens_externally)
    val giveawaySubtitle = remember(worth, typeLabel, platformsLabel, freeLabel) {
        buildAnnotatedString {
            append(freeLabel)
            worth?.let {
                append(" ")
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(it) }
            }
            append(" - ")
            append(typeLabel)
            if (platformsLabel.isNotEmpty()) {
                append(" · ")
                append(platformsLabel)
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onGiveawayTitle(giveaway.gamerpowerUrl) }
            .padding(vertical = GameDealsCustomTheme.spacing.medium)
            .semantics(mergeDescendants = true) { contentDescription = "$rowCd, $opensExternallyCd" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = giveaway.thumbnail,
            contentDescription = stringResource(Res.string.home_screen_game_image, giveaway.title),
            contentScale = ContentScale.Fit,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                .height(60.dp)
                .width(100.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = GameDealsCustomTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
        ) {
            Text(
                textAlign = TextAlign.Start,
                text = giveaway.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = giveawaySubtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(CommonRes.drawable.open_in_new),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                .size(20.dp),
        )
    }
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
        RankedGame(gameId = "w1", title = "Baldur's Gate 3", priceDenominated = "$59.99"),
        RankedGame(gameId = "w2", title = "Cyberpunk 2077", priceDenominated = "$29.99"),
    ).toImmutableList(),
    mostCollected = listOf(
        RankedGame(gameId = "c1", title = "Skyrim Special Edition", priceDenominated = "$19.99"),
    ).toImmutableList(),
    releases = listOf(
        PreviewRelease,
        PreviewRelease.copy(title = "Silksong"),
    ).toImmutableList(),
    giveaways = listOf(
        PreviewGiveaway,
        PreviewGiveaway.copy(id = 2, title = "Tomb Raider Trilogy", worthDenominated = "$49.99"),
    ).toImmutableList(),
)

@Preview
@Composable
private fun HomeScreenContent_Success_Preview() {
    GameDealsTheme {
        HomeScreenContent(
            onReleaseTitle = {},
            data = previewSuccessData(),
            waitlistIds = persistentSetOf("22222"),
            stores = persistentMapOf(PreviewStore.storeID to PreviewStore),
            dealDetails = null,
            onViewDealDetails = { _, _, _, _, _, _ -> },
            onToggleWaitlist = {},
            goToGame = {},
            onViewGiveaways = {},
            onViewBundles = {},
            onViewBundle = {},
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealWaitlist = {},
            goToWeb = { _, _ -> },
            goToGameDetails = { _, _ -> },
            goToGameDetailsByTitle = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContent_Success_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        HomeScreenContent(
            onReleaseTitle = {},
            data = previewSuccessData(),
            waitlistIds = persistentSetOf("22222"),
            stores = persistentMapOf(PreviewStore.storeID to PreviewStore),
            dealDetails = null,
            onViewDealDetails = { _, _, _, _, _, _ -> },
            onToggleWaitlist = {},
            goToGame = {},
            onViewGiveaways = {},
            onViewBundles = {},
            onViewBundle = {},
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealWaitlist = {},
            goToWeb = { _, _ -> },
            goToGameDetails = { _, _ -> },
            goToGameDetailsByTitle = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContent_Loading_Preview() {
    GameDealsTheme {
        HomeScreenContent(
            onReleaseTitle = {},
            data = HomeViewModel.HomeScreenData(status = HomeScreenStatus.LOADING),
            waitlistIds = persistentSetOf(),
            stores = persistentMapOf(),
            dealDetails = null,
            onViewDealDetails = { _, _, _, _, _, _ -> },
            onToggleWaitlist = {},
            goToGame = {},
            onViewGiveaways = {},
            onViewBundles = {},
            onViewBundle = {},
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealWaitlist = {},
            goToWeb = { _, _ -> },
            goToGameDetails = { _, _ -> },
            goToGameDetailsByTitle = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContent_Error_Preview() {
    GameDealsTheme {
        HomeScreenContent(
            onReleaseTitle = {},
            data = HomeViewModel.HomeScreenData(status = HomeScreenStatus.ERROR),
            waitlistIds = persistentSetOf(),
            stores = persistentMapOf(),
            dealDetails = null,
            onViewDealDetails = { _, _, _, _, _, _ -> },
            onToggleWaitlist = {},
            goToGame = {},
            onViewGiveaways = {},
            onViewBundles = {},
            onViewBundle = {},
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealWaitlist = {},
            goToWeb = { _, _ -> },
            goToGameDetails = { _, _ -> },
            goToGameDetailsByTitle = {},
            onRetry = {},
        )
    }
}
