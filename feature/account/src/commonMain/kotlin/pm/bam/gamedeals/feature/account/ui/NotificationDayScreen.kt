package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.components.DiscountBadge
import pm.bam.gamedeals.common.ui.components.NewHistoricalLowBadge
import pm.bam.gamedeals.common.ui.components.StoreLowBadge
import pm.bam.gamedeals.common.ui.components.VoucherBadge
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.models.NotificationShopDeal
import pm.bam.gamedeals.domain.models.hero
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_best_deal
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_deal_expired
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_detail_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_detail_image
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_detail_title
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_historical_low
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_open_game
import pm.bam.gamedeals.feature.account.ui.NotificationDayViewModel.NotificationDayEvent
import pm.bam.gamedeals.feature.account.ui.NotificationDayViewModel.NotificationDayScreenData
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

@Composable
internal fun NotificationDayScreen(
    onBack: () -> Unit,
    onGameClick: (gameId: String) -> Unit,
    viewModel: NotificationDayViewModel = koinViewModel(),
) {
    val data by viewModel.uiState.collectAsStateWithLifecycle()

    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is NotificationDayEvent.OpenGame -> onGameClick(event.gameId)
        }
    }

    NotificationDayScreenContent(
        data = data,
        onBack = onBack,
        onGameViewed = viewModel::onGameViewed,
        onOpenGame = viewModel::onOpenGame,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationDayScreenContent(
    data: NotificationDayScreenData,
    onBack: () -> Unit,
    onGameViewed: (gameId: String) -> Unit,
    onOpenGame: (gameId: String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(stringResource(Res.string.account_notification_detail_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.account_navigation_back),
                            )
                        }
                    },
                )
            },
        ) { innerPadding: PaddingValues ->
            when {
                data.loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                data.games.isEmpty() -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.account_notification_detail_empty),
                        modifier = Modifier.politeLiveRegion(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(GameDealsCustomTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                ) {
                    items(data.games, key = { it.gameId }) { game ->
                        // Viewing a game's card is the per-game read action — fires once the card composes.
                        LaunchedEffect(game.gameId) { onGameViewed(game.gameId) }
                        GameDealCard(game = game, onOpenGame = { onOpenGame(game.gameId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDealCard(
    game: NotificationDealGame,
    onOpenGame: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            AsyncImage(
                model = game.artwork.hero,
                contentDescription = stringResource(Res.string.account_notification_detail_image, game.title),
                error = painterResource(CommonRes.drawable.videogame_thumb),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    // Steam-header ratio (≈2.14:1) — the variant GameArtwork.hero resolves to.
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
            )

            Text(
                text = game.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            game.historicalLowDenominated?.let { low ->
                Text(
                    text = "★ " + stringResource(Res.string.account_notification_historical_low, low),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (game.isExpired) {
                Text(
                    text = stringResource(Res.string.account_notification_deal_expired),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // All shop deals, with the best (lowest) price highlighted as the notified deal.
                val best = game.bestDeal
                game.deals.forEach { deal -> ShopDealRow(deal = deal, highlighted = deal == best) }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenGame) {
                    Text(stringResource(Res.string.account_notification_open_game))
                }
            }
        }
    }
}

@Composable
private fun ShopDealRow(deal: NotificationShopDeal, highlighted: Boolean) {
    val rowModifier = if (highlighted) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(GameDealsCustomTheme.spacing.small)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.small)
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deal.shopName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            )
            if (highlighted) {
                Text(
                    text = stringResource(Res.string.account_notification_best_deal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        if (deal.isNewHistoricalLow) NewHistoricalLowBadge()
        if (deal.isStoreLow) StoreLowBadge()
        if (deal.hasVoucher) VoucherBadge()
        DiscountBadge(discountPercent = deal.cutPercent)
        Text(
            text = deal.salePriceDenominated,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val previewDayGames = persistentListOf(
    NotificationDealGame(
        gameId = "g1",
        title = "Cyberpunk 2077",
        historicalLowDenominated = "€17.99",
        deals = listOf(
            NotificationShopDeal("GOG", 17.09, "€17.09", "€59.99", cutPercent = 72, url = "", isNewHistoricalLow = true),
            NotificationShopDeal("Steam", 29.99, "€29.99", "€59.99", cutPercent = 50, url = "", isStoreLow = true),
            NotificationShopDeal("Fanatical", 32.99, "€32.99", "€59.99", cutPercent = 45, url = "", hasVoucher = true),
        ),
    ),
    NotificationDealGame(
        gameId = "g2",
        title = "Hades",
        historicalLowDenominated = "€12.49",
        deals = emptyList(), // expired → "deal expired" note
    ),
)

@Preview
@Composable
private fun NotificationDayScreenPreview() {
    GameDealsTheme {
        NotificationDayScreenContent(
            data = NotificationDayScreenData(loading = false, games = previewDayGames),
            onBack = {},
            onGameViewed = {},
            onOpenGame = {},
        )
    }
}

@Preview
@Composable
private fun NotificationDayScreenEmptyPreview() {
    GameDealsTheme {
        NotificationDayScreenContent(
            data = NotificationDayScreenData(loading = false, games = persistentListOf()),
            onBack = {},
            onGameViewed = {},
            onOpenGame = {},
        )
    }
}
