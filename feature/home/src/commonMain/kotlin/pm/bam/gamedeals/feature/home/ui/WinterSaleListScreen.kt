package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.utils.formatMoney
import pm.bam.gamedeals.domain.repositories.wintersale.WINTER_SALE_BIG_CUT_PERCENT
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDeal
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShelf
import pm.bam.gamedeals.domain.utils.groupThousands
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_attribution
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_back
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_cuts
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_lows
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_list_banner
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_list_cuts_sub
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_list_empty
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_list_end
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_list_failed
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_list_lows_sub
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_list_more_failed
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_list_store_fallback
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_list_store_sub
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_list_store_sub_no_count
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_try_again

@Composable
internal fun WinterSaleListScreen(
    onBack: () -> Unit,
    onGame: (String) -> Unit,
    onOffer: (String) -> Unit,
    viewModel: WinterSaleListViewModel = koinViewModel(),
    peek: WinterSalePeekViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stores by peek.stores.collectAsStateWithLifecycle()
    val waitlistIds by peek.waitlistIds.collectAsStateWithLifecycle()
    val collectionIds by peek.collectionIds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    WinterSaleListContent(
        state = state,
        onBack = onBack,
        onPeek = { deal ->
            viewModel.onGamePeeked()
            peek.peek(deal.gameId, deal.title, deal.artwork.thumbnail)
        },
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        stores = stores,
        waitlistIds = waitlistIds,
        collectionIds = collectionIds,
        snackbarHostState = snackbarHostState,
    )
    WinterSalePeekHost(peek = peek, snackbarHostState = snackbarHostState, onGame = onGame, onOffer = onOffer)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
internal fun WinterSaleListContent(
    state: WinterSaleListState,
    onBack: () -> Unit,
    onPeek: (WinterSaleDeal) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    stores: Map<Int, Store> = emptyMap(),
    waitlistIds: Set<String> = emptySet(),
    collectionIds: Set<String> = emptySet(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val title = when (val shelf = state.shelf) {
        is WinterSaleShelf.Store -> state.storeName ?: stringResource(Res.string.winter_sale_list_store_fallback)
        WinterSaleShelf.AllTimeLows -> stringResource(Res.string.winter_sale_hub_lows)
        WinterSaleShelf.BigCuts -> stringResource(Res.string.winter_sale_hub_cuts, WINTER_SALE_BIG_CUT_PERCENT)
        null -> stringResource(Res.string.winter_sale_list_banner)
    }
    val subtitle = when (state.shelf) {
        is WinterSaleShelf.Store -> state.storeDeals?.let { stringResource(Res.string.winter_sale_list_store_sub, groupThousands(it.toLong())) }
            ?: stringResource(Res.string.winter_sale_list_store_sub_no_count)
        WinterSaleShelf.AllTimeLows -> stringResource(Res.string.winter_sale_list_lows_sub)
        WinterSaleShelf.BigCuts -> stringResource(Res.string.winter_sale_list_cuts_sub)
        null -> null
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (subtitle != null) {
                                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.winter_sale_back))
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            val listState = rememberLazyListState()
            val currentLoadMore by rememberUpdatedState(onLoadMore)
            val nearEnd by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
                }
            }
            // Asks again after each page lands, in case the new rows still leave the end in view.
            LaunchedEffect(state.deals.size, nearEnd) { if (nearEnd) currentLoadMore() }

            val bottomInset = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = bottomInset) {
                state.saleEndEpochMillis?.takeIf { it > 0 }?.let { end ->
                    item(key = "banner") { SaleStrip(saleCountdown(end - nowEpochMillis())) }
                }
                when {
                    state.loading -> item(key = "loading") { CenteredSpinner() }
                    state.failed -> item(key = "failed") {
                        RetryRow(stringResource(Res.string.winter_sale_list_failed), onRetry)
                    }
                    state.deals.isEmpty() -> item(key = "empty") { FooterText(stringResource(Res.string.winter_sale_list_empty)) }
                }
                items(state.deals, key = { it.gameId }, contentType = { "sale-row" }) { deal ->
                    // Home's deal row, so the sale's lists look like every other list in the app.
                    DealListRow(
                        title = deal.title,
                        contentDescription = saleDealBaseDescription(deal),
                        onClick = { onPeek(deal) },
                        imageUrl = deal.artwork.thumbnail,
                        salePrice = formatMoney(deal.price.amount, deal.price.currency),
                        regularPrice = deal.regularPriceText(),
                        discountPercent = deal.cutPercent,
                        hasVoucher = deal.voucher != null,
                        isNewHistoricalLow = deal.isNewLow,
                        isStoreLow = deal.isStoreLow,
                        isLowestEver = deal.isLowestEver,
                        storeName = deal.shopName,
                        storeIconUrl = stores[deal.shopId]?.iconUrl?.takeIf { it.isNotBlank() },
                        isWaitlisted = deal.gameId in waitlistIds,
                        isCollected = deal.gameId in collectionIds,
                    )
                }
                if (state.deals.isNotEmpty()) item(key = "footer") {
                    when {
                        state.loadingMore -> CenteredSpinner()
                        state.moreFailed -> RetryRow(stringResource(Res.string.winter_sale_list_more_failed), onRetry)
                        state.endReached -> FooterText(stringResource(Res.string.winter_sale_list_end))
                    }
                }
                item(key = "attribution") {
                    Text(
                        stringResource(Res.string.winter_sale_attribution),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
                    )
                }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 6

@Composable
private fun SaleStrip(countdown: SaleCountdown) {
    val palette = winterSalePalette()
    Surface(
        color = palette.hero,
        contentColor = palette.onHero,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.winter_sale_list_banner), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = palette.heroAccent, modifier = Modifier.weight(1f))
            Text(countdownText(countdown), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.large), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp))
    }
}

@Composable
private fun RetryRow(text: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.large), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.winter_sale_try_again)) }
    }
}

@Composable
private fun FooterText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.large),
    )
}
