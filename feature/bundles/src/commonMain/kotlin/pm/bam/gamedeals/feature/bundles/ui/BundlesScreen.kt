package pm.bam.gamedeals.feature.bundles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.feature.bundles.generated.resources.Res
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_row_description
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_row_expiry
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_row_from_price
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_row_game_count
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_empty
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_loading_indicator
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_navigation_back_button
import pm.bam.gamedeals.feature.bundles.generated.resources.bundles_screen_title
import pm.bam.gamedeals.feature.bundles.ui.BundlesViewModel.BundlesScreenData

@Composable
internal fun BundlesScreen(
    onBack: () -> Unit,
    onBundleClick: (bundleId: Int) -> Unit,
    viewModel: BundlesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BundlesScreenContent(
        state = state,
        onBack = onBack,
        onBundleClick = onBundleClick,
        onRetry = viewModel::load,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BundlesScreenContent(
    state: BundlesScreenData,
    onBack: () -> Unit,
    onBundleClick: (bundleId: Int) -> Unit,
    onRetry: () -> Unit,
) {
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
                            text = stringResource(Res.string.bundles_screen_title),
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
                BundlesScreenData.Loading -> {
                    val loadingCd = stringResource(Res.string.bundles_screen_loading_indicator)
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .semantics { contentDescription = loadingCd },
                    )
                }

                BundlesScreenData.Error -> CenteredMessage(
                    modifier = Modifier.padding(innerPadding),
                    message = stringResource(Res.string.bundles_screen_data_loading_error_msg),
                    actionLabel = stringResource(Res.string.bundles_screen_data_loading_error_retry),
                    onAction = onRetry,
                )

                is BundlesScreenData.Data ->
                    if (state.bundles.isEmpty()) {
                        CenteredMessage(
                            modifier = Modifier.padding(innerPadding),
                            message = stringResource(Res.string.bundles_screen_empty),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
                            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                        ) {
                            items(state.bundles, key = { it.id }) { bundle ->
                                BundleRow(bundle = bundle, onClick = { onBundleClick(bundle.id) })
                            }
                        }
                    }
            }
        }
    }
}

@Composable
internal fun BundleRow(
    bundle: Bundle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowCd = stringResource(Res.string.bundles_row_description, bundle.title, bundle.storeName)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = rowCd },
    ) {
        Column(modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium)) {
            Text(
                text = bundle.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.extraSmall),
                text = bundle.storeName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GameDealsCustomTheme.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.bundles_row_game_count, bundle.gameCount),
                    style = MaterialTheme.typography.labelLarge,
                )
                bundle.expiryEpochMs?.let { expiry ->
                    Text(
                        text = stringResource(Res.string.bundles_row_expiry, formatBundleExpiry(expiry)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                bundle.priceDenominated?.let { price ->
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(Res.string.bundles_row_from_price, price),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(GameDealsCustomTheme.spacing.large)
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private val previewBundles = persistentListOf(
    Bundle(
        id = 1,
        title = "Humble Choice (June 2026)",
        storeName = "Humble Bundle",
        url = "https://example.com/1",
        expiryEpochMs = 1_751_911_200_000L,
        gameCount = 8,
        priceDenominated = "$14.99",
        games = persistentListOf(),
    ),
    Bundle(
        id = 2,
        title = "Fanatical RimWorld with all Expansions Bundle",
        storeName = "Fanatical",
        url = "https://example.com/2",
        expiryEpochMs = 1_752_000_000_000L,
        gameCount = 4,
        priceDenominated = "$87.96",
        games = persistentListOf(),
    ),
)

@Preview
@Composable
private fun BundlesScreenPreview() {
    GameDealsTheme {
        BundlesScreenContent(
            state = BundlesScreenData.Data(previewBundles),
            onBack = {},
            onBundleClick = {},
            onRetry = {},
        )
    }
}
