package pm.bam.gamedeals.feature.bundles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.DiscountBadge
import pm.bam.gamedeals.common.ui.components.NewHistoricalLowBadge
import pm.bam.gamedeals.common.ui.components.StoreLowBadge
import pm.bam.gamedeals.common.ui.components.VoucherBadge
import pm.bam.gamedeals.common.ui.deal.GamePeekSheet
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.feature.bundles.generated.resources.Res
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_expiry
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_game_image
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_get_bundle
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_historical_low
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_included_games
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_no_deal
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_price_at
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_published
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_tier
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_tier_price
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_value_bundle_price
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_value_current
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_value_low
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_value_partial
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_value_savings
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_value_title
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_row_game_count
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_loading_indicator
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_navigation_back_button
import pm.bam.gamedeals.feature.bundles.ui.BundleDetailViewModel.BundleDetailScreenData
import pm.bam.gamedeals.feature.bundles.ui.BundleDetailViewModel.BundleValueSummary
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_waitlist_sign_in_required
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

@Composable
internal fun BundleDetailScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, title: String) -> Unit,
    onGameClick: (gameId: String) -> Unit,
    viewModel: BundleDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gamePeek by viewModel.gamePeek.collectAsStateWithLifecycle()
    val waitlistIds by viewModel.waitlistIds.collectAsStateWithLifecycle()
    val collectionIds by viewModel.collectionIds.collectAsStateWithLifecycle()
    val ignoredIds by viewModel.ignoredIds.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current
    val snackbarHostState = remember { SnackbarHostState() }
    val signInRequired = stringResource(CommonRes.string.deal_waitlist_sign_in_required)

    BundleDetailScreenContent(
        state = state,
        gamePeek = gamePeek,
        waitlistIds = waitlistIds,
        collectionIds = collectionIds,
        ignoredIds = ignoredIds,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        goToWeb = goToWeb,
        onGameClick = onGameClick,
        onPeekGame = { gameId, gameName, thumb -> viewModel.peekGame(gameId, gameName, thumb) },
        onToggleWaitlist = { gameId -> viewModel.toggleWaitlist(gameId) },
        onToggleCollection = { gameId -> viewModel.toggleCollection(gameId) },
        onToggleIgnore = { gameId -> viewModel.toggleIgnore(gameId) },
        onDismissPeek = { viewModel.dismissPeek() },
        onShare = { peekData -> viewModel.onShareClicked(peekData) },
        onRetryPeek = {
            viewModel.gamePeek.value?.let { peek ->
                if (peek.gameId.isNotEmpty()) viewModel.peekGame(peek.gameId, peek.gameName, peek.thumb)
            }
        },
        onRetry = viewModel::load,
    )

    // Collect one-shot UI events and dispatch them (share sheet / sign-in snackbar).
    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is BundleDetailViewModel.BundleDetailUiEvent.ShareDeal -> platformActions.share(event.text)
            BundleDetailViewModel.BundleDetailUiEvent.SignInRequired -> snackbarHostState.showSnackbar(signInRequired)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BundleDetailScreenContent(
    state: BundleDetailScreenData,
    gamePeek: GamePeekSheetData?,
    waitlistIds: ImmutableSet<String>,
    collectionIds: ImmutableSet<String>,
    ignoredIds: ImmutableSet<String>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    goToWeb: (url: String, title: String) -> Unit,
    onGameClick: (gameId: String) -> Unit,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit,
    onToggleCollection: (gameId: String) -> Unit,
    onToggleIgnore: (gameId: String) -> Unit,
    onDismissPeek: () -> Unit,
    onShare: (data: GamePeekSheetData.Data) -> Unit,
    onRetryPeek: () -> Unit,
    onRetry: () -> Unit,
) {
    val title = (state as? BundleDetailScreenData.Data)?.bundle?.title.orEmpty()
    val peekGameId = gamePeek?.gameId?.takeIf { it.isNotEmpty() }
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            modifier = Modifier.semantics { heading() },
                            text = title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.bundles_screen_navigation_back_button),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding: PaddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (state) {
                    BundleDetailScreenData.Loading -> {
                        val loadingCd = stringResource(Res.string.bundles_screen_loading_indicator)
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .wrapContentSize(Alignment.Center)
                                .semantics { contentDescription = loadingCd },
                        )
                    }

                    BundleDetailScreenData.Error -> Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(GameDealsCustomTheme.spacing.large)
                            .wrapContentSize(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                    ) {
                        Text(stringResource(Res.string.bundles_screen_data_loading_error_msg))
                        Button(onClick = onRetry) { Text(stringResource(Res.string.bundles_screen_data_loading_error_retry)) }
                    }

                    is BundleDetailScreenData.Data -> BundleDetailBody(
                        data = state,
                        goToWeb = goToWeb,
                        onPeekGame = onPeekGame,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                // The shared game-centric peek sheet — opened by tapping a bundle game row, same as Home/Deals.
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
                    onViewGamePage = { peekData -> onGameClick(peekData.gameId) },
                    onRetry = onRetryPeek,
                )
            }
        }
    }
}

@Composable
private fun BundleDetailBody(
    data: BundleDetailScreenData.Data,
    goToWeb: (url: String, title: String) -> Unit,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bundle = data.bundle
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)) {
                Text(text = bundle.storeName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(Res.string.bundles_row_game_count, bundle.gameCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Button(
                onClick = { goToWeb(bundle.url, bundle.title) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.bundle_detail_get_bundle))
            }
        }

        bundle.expiryEpochMs?.let { expiry ->
            item { BundleCountdown(expiryEpochMs = expiry, modifier = Modifier.fillMaxWidth()) }
        }

        bundle.details?.let { details ->
            item {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Surfaced near the top so the savings are visible before scrolling through the tiers.
        data.valueSummary?.let { summary ->
            item { OverallValueCard(summary) }
        }

        if (bundle.tiers.isNotEmpty()) {
            bundle.tiers.forEachIndexed { index, tier ->
                item(key = "tier_$index") { TierHeader(tierNumber = index + 1, priceDenominated = tier.priceDenominated) }
                items(tier.games, key = { "tier_${index}_${it.id}" }) { game ->
                    BundleGameRow(game = game, price = data.prices[game.id], onClick = { onPeekGame(game.id, game.title, game.boxart) })
                }
            }
        } else if (bundle.games.isNotEmpty()) {
            item {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(Res.string.bundle_detail_included_games),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(bundle.games, key = { it.id }) { game ->
                BundleGameRow(game = game, price = data.prices[game.id], onClick = { onPeekGame(game.id, game.title, game.boxart) })
            }
        }

        item { BundleFooter(bundle) }
    }
}

@Composable
private fun TierHeader(tierNumber: Int, priceDenominated: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(Res.string.bundle_detail_tier, tierNumber),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.weight(1f))
        priceDenominated?.let {
            Text(
                text = stringResource(Res.string.bundle_detail_tier_price, it),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun BundleGameRow(
    game: Bundle.BundleGame,
    price: BundleGamePrice?,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GameDealsCustomTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = game.boxart,
                contentDescription = stringResource(Res.string.bundle_detail_game_image, game.title),
                error = painterResource(CommonRes.drawable.videogame_thumb),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = GameDealsCustomTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                BundleGamePriceLines(price)
            }
        }
    }
}

@Composable
private fun BundleGamePriceLines(price: BundleGamePrice?) {
    val bestPrice = price?.bestPriceDenominated
    if (bestPrice == null) {
        Text(
            text = stringResource(Res.string.bundle_detail_no_deal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // Price + shop, with the regular (pre-discount) price struck through beside it when discounted.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        Text(
            text = price.bestShopName?.let { stringResource(Res.string.bundle_detail_price_at, bestPrice, it) } ?: bestPrice,
            style = MaterialTheme.typography.bodyMedium,
        )
        price.bestRegularDenominated?.takeIf { it != bestPrice }?.let { regular ->
            Text(
                text = regular,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
            )
        }
    }
    // ITAD flag cluster, same anatomy as the deal rows: [voucher] [-XX%] [N] [S]. Hidden when none apply.
    val cut = price.bestCutPercent ?: 0
    if (price.bestHasVoucher || cut > 0 || price.bestIsNewHistoricalLow || price.bestIsStoreLow) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
        ) {
            if (price.bestHasVoucher) VoucherBadge()
            DiscountBadge(discountPercent = cut)
            if (price.bestIsNewHistoricalLow) NewHistoricalLowBadge()
            if (price.bestIsStoreLow) StoreLowBadge()
        }
    }
    price.historicalLowDenominated?.let { low ->
        Text(
            text = stringResource(Res.string.bundle_detail_historical_low, low),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverallValueCard(summary: BundleValueSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
        ) {
            Text(
                modifier = Modifier.semantics { heading() },
                text = stringResource(Res.string.bundle_detail_value_title),
                style = MaterialTheme.typography.titleMedium,
            )
            summary.currentValueDenominated?.let { ValueLine(stringResource(Res.string.bundle_detail_value_current), it) }
            summary.historicalLowDenominated?.let { ValueLine(stringResource(Res.string.bundle_detail_value_low), it) }
            summary.bundlePriceDenominated?.let { ValueLine(stringResource(Res.string.bundle_detail_value_bundle_price), it) }
            summary.savingsPercent?.let { savings ->
                Text(
                    text = stringResource(Res.string.bundle_detail_value_savings, savings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (summary.pricedGames < summary.totalGames) {
                Text(
                    text = stringResource(Res.string.bundle_detail_value_partial, summary.pricedGames, summary.totalGames),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ValueLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BundleFooter(bundle: Bundle) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)) {
        HorizontalDivider(modifier = Modifier.padding(vertical = GameDealsCustomTheme.spacing.small))
        bundle.publishEpochMs?.let { published ->
            Text(
                text = stringResource(Res.string.bundle_detail_published, formatBundlePublished(published)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        bundle.expiryEpochMs?.let { expiry ->
            Text(
                text = stringResource(Res.string.bundle_detail_expiry, formatBundleExpiry(expiry)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun BundleDetailScreenPreview() {
    val games = persistentListOf(
        Bundle.BundleGame("a", "Descenders", ""),
        Bundle.BundleGame("b", "MudRunner", ""),
        Bundle.BundleGame("c", "DRIFT CE", ""),
    )
    val prices: ImmutableMap<String, BundleGamePrice> = listOf(
        BundleGamePrice(
            gameId = "a",
            bestShopName = "Steam",
            bestPriceValue = 4.59,
            bestPriceDenominated = "€4.59",
            bestCutPercent = 80,
            bestRegularDenominated = "€22.99",
            bestIsNewHistoricalLow = true,
            historicalLowValue = 4.59,
            historicalLowDenominated = "€4.59",
            currency = "EUR",
        ),
        BundleGamePrice(
            gameId = "c",
            bestShopName = "GamesPlanet",
            bestPriceValue = 4.49,
            bestPriceDenominated = "€4.49",
            bestCutPercent = 76,
            bestRegularDenominated = "€18.99",
            bestHasVoucher = true,
            historicalLowValue = 3.99,
            historicalLowDenominated = "€3.99",
            currency = "EUR",
        ),
    ).associateBy { it.gameId }.toImmutableMap()
    GameDealsTheme {
        BundleDetailScreenContent(
            state = BundleDetailScreenData.Data(
                bundle = Bundle(
                    id = 1,
                    title = "Redline Racing Bundle",
                    storeName = "Humble Store",
                    url = "https://example.com/1",
                    expiryEpochMs = 1_999_999_999_000L,
                    gameCount = 3,
                    priceDenominated = "€5.38",
                    games = games,
                    publishEpochMs = 1_749_400_000_000L,
                    isMature = false,
                    details = "Some proceeds go to support charity. Keys expire — please redeem before the date shown.",
                    priceValue = 5.38,
                    tiers = persistentListOf(
                        Bundle.Tier(priceDenominated = "€5.38", priceValue = 5.38, games = games),
                    ),
                ),
                prices = prices,
                valueSummary = BundleValueSummary(
                    currentValueDenominated = "€132.01",
                    historicalLowDenominated = "€29.55",
                    bundlePriceDenominated = "€5.38",
                    savingsPercent = 92,
                    pricedGames = 2,
                    totalGames = 3,
                ),
            ),
            gamePeek = null,
            waitlistIds = persistentSetOf(),
            collectionIds = persistentSetOf(),
            ignoredIds = persistentSetOf(),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            goToWeb = { _, _ -> },
            onGameClick = {},
            onPeekGame = { _, _, _ -> },
            onToggleWaitlist = {},
            onToggleCollection = {},
            onToggleIgnore = {},
            onDismissPeek = {},
            onShare = {},
            onRetryPeek = {},
            onRetry = {},
        )
    }
}
