package pm.bam.gamedeals.feature.discover.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.TagDiscoveryResult
import pm.bam.gamedeals.feature.discover.generated.resources.Res
import pm.bam.gamedeals.feature.discover.generated.resources.discover_navigation_back
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_empty
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_error
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_load_more_error
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_no_price
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_open_steam
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_retry
import pm.bam.gamedeals.feature.discover.generated.resources.discover_results_title
import pm.bam.gamedeals.feature.discover.ui.DiscoverResultsViewModel.ResultsScreenData
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

// Fire a load-more once the user scrolls within this many rows of the end of the loaded page.
private const val LOAD_MORE_THRESHOLD = 5

@Composable
internal fun DiscoverResultsScreen(
    onBack: () -> Unit,
    goToGame: (gameId: String) -> Unit,
    goToWeb: (url: String) -> Unit,
    viewModel: DiscoverResultsViewModel = koinViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadMoreError = stringResource(Res.string.discover_results_load_more_error)

    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            DiscoverResultsViewModel.DiscoverResultsUiEvent.LoadMoreError -> snackbarHostState.showSnackbar(loadMoreError)
        }
    }

    DiscoverResultsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onLoadMore = { viewModel.loadNextPage() },
        onRetry = { viewModel.retry() },
        onResultClick = { result ->
            when (val pricing = result.pricing) {
                is TagDiscoveryResult.Pricing.Priced -> goToGame(pricing.gameId)
                is TagDiscoveryResult.Pricing.SteamLinkOut -> goToWeb(pricing.steamUrl)
                TagDiscoveryResult.Pricing.Unpriced -> Unit
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverResultsContent(
    state: ResultsScreenData,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onResultClick: (TagDiscoveryResult) -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(state.results.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.results.isNotEmpty() && lastVisible >= state.results.size - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(stringResource(Res.string.discover_results_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.discover_navigation_back),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding: PaddingValues ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                when (state.status) {
                    ResultsScreenData.Status.LOADING -> CircularProgressIndicator(
                        Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                    )

                    ResultsScreenData.Status.EMPTY -> Text(
                        text = stringResource(Res.string.discover_results_empty),
                        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                    )

                    ResultsScreenData.Status.ERROR -> Column(
                        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(Res.string.discover_results_error))
                        TextButton(onClick = onRetry) { Text(stringResource(Res.string.discover_results_retry)) }
                    }

                    ResultsScreenData.Status.DATA -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(GameDealsCustomTheme.spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                    ) {
                        items(items = state.results, key = { it.igdbId }) { result ->
                            DiscoverResultRow(result = result, onClick = { onResultClick(result) })
                        }
                        if (state.appending) {
                            item(key = "discover-load-more-spinner") {
                                CircularProgressIndicator(Modifier.fillMaxWidth().wrapContentSize(Alignment.Center))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverResultRow(
    result: TagDiscoveryResult,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = result.coverImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .height(80.dp)
                .width(60.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            PricingLabel(result.pricing)
        }
    }
}

@Composable
private fun PricingLabel(pricing: TagDiscoveryResult.Pricing) {
    when (pricing) {
        is TagDiscoveryResult.Pricing.Priced -> {
            val price = pricing.price
            val denominated = price?.bestPriceDenominated
            if (denominated != null) {
                val cut = price?.bestCutPercent
                Text(
                    text = if (cut != null && cut > 0) "$denominated · -$cut%" else denominated,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = stringResource(Res.string.discover_results_no_price),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is TagDiscoveryResult.Pricing.SteamLinkOut -> Text(
            text = stringResource(Res.string.discover_results_open_steam),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        TagDiscoveryResult.Pricing.Unpriced -> Unit
    }
}

@Preview
@Composable
private fun DiscoverResultsContent_Data_Preview() {
    GameDealsTheme {
        DiscoverResultsContent(
            state = ResultsScreenData(
                status = ResultsScreenData.Status.DATA,
                results = persistentListOf(
                    TagDiscoveryResult(
                        igdbId = 1L, title = "Hades", coverImageUrl = null, steamAppId = 1145360,
                        pricing = TagDiscoveryResult.Pricing.Priced(
                            "itad-1",
                            BundleGamePrice(
                                gameId = "itad-1", bestShopName = "Steam", bestPriceValue = 12.49,
                                bestPriceDenominated = "$12.49", bestCutPercent = 50,
                                historicalLowValue = 9.99, historicalLowDenominated = "$9.99",
                            ),
                        ),
                    ),
                    TagDiscoveryResult(
                        igdbId = 2L, title = "Some Console Game", coverImageUrl = null, steamAppId = null,
                        pricing = TagDiscoveryResult.Pricing.Unpriced,
                    ),
                    TagDiscoveryResult(
                        igdbId = 3L, title = "Steam-only Game", coverImageUrl = null, steamAppId = 42,
                        pricing = TagDiscoveryResult.Pricing.SteamLinkOut("https://store.steampowered.com/app/42"),
                    ),
                ),
            ),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onResultClick = {},
        )
    }
}
