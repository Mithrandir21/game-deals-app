package pm.bam.gamedeals.feature.bundles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.feature.bundles.generated.resources.Res
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_game_image
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_get_bundle
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_included_games
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_row_expiry
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_row_from_price
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_row_game_count
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_loading_indicator
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_navigation_back_button
import pm.bam.gamedeals.feature.bundles.ui.BundleDetailViewModel.BundleDetailScreenData
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

@Composable
internal fun BundleDetailScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, title: String) -> Unit,
    viewModel: BundleDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BundleDetailScreenContent(
        state = state,
        onBack = onBack,
        goToWeb = goToWeb,
        onRetry = viewModel::load,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BundleDetailScreenContent(
    state: BundleDetailScreenData,
    onBack: () -> Unit,
    goToWeb: (url: String, title: String) -> Unit,
    onRetry: () -> Unit,
) {
    val title = (state as? BundleDetailScreenData.Data)?.bundle?.title.orEmpty()
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
        ) { innerPadding: PaddingValues ->
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
                    bundle = state.bundle,
                    goToWeb = goToWeb,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun BundleDetailBody(
    bundle: Bundle,
    goToWeb: (url: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                bundle.expiryEpochMs?.let { expiry ->
                    Text(
                        text = stringResource(Res.string.bundles_row_expiry, formatBundleExpiry(expiry)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                bundle.priceDenominated?.let { price ->
                    Text(
                        text = stringResource(Res.string.bundles_row_from_price, price),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
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

        if (bundle.games.isNotEmpty()) {
            item {
                Text(
                    modifier = Modifier
                        .padding(top = GameDealsCustomTheme.spacing.small)
                        .semantics { heading() },
                    text = stringResource(Res.string.bundle_detail_included_games),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(bundle.games, key = { it.id }) { game ->
                BundleGameRow(game)
            }
        }
    }
}

@Composable
private fun BundleGameRow(game: Bundle.BundleGame) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = game.boxart,
            contentDescription = stringResource(Res.string.bundle_detail_game_image, game.title),
            error = painterResource(CommonRes.drawable.videogame_thumb),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = 64.dp, height = 48.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = GameDealsCustomTheme.spacing.medium),
            text = game.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun BundleDetailScreenPreview() {
    GameDealsTheme {
        BundleDetailScreenContent(
            state = BundleDetailScreenData.Data(
                Bundle(
                    id = 1,
                    title = "Humble Choice (June 2026)",
                    storeName = "Humble Bundle",
                    url = "https://example.com/1",
                    expiryEpochMs = 1_751_911_200_000L,
                    gameCount = 3,
                    priceDenominated = "$14.99",
                    games = persistentListOf(
                        Bundle.BundleGame("a", "Construction Simulator", ""),
                        Bundle.BundleGame("b", "Another Great Game", ""),
                        Bundle.BundleGame("c", "Third Title", ""),
                    ),
                ),
            ),
            onBack = {},
            goToWeb = { _, _ -> },
            onRetry = {},
        )
    }
}
