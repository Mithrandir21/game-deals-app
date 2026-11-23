package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.navigation.SignInPromptController
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.signals.LocalRowSignals
import pm.bam.gamedeals.common.ui.signals.ProvideGameSignals
import pm.bam.gamedeals.common.ui.signals.isRare
import pm.bam.gamedeals.common.ui.signals.reviewText
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_list_loading
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_no_deal
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_also_on_list
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_both_want
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_both_want_none
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_empty
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_failed
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_intro
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_on_list
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_retry
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_sign_in
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_sign_in_body
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_sign_in_title
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_title
import pm.bam.gamedeals.feature.account.generated.resources.shared_wishlist_unavailable

/** A friend's shared wishlist link: "You both want" first when signed in, then the rest, each cheapest first. */
@Composable
internal fun SharedWishlistScreen(
    onBack: () -> Unit,
    goToGame: (gameId: String) -> Unit,
    goToWeb: (url: String) -> Unit,
    viewModel: SharedWishlistViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryPeekHost(peek = viewModel.peek, goToGame = goToGame, goToWeb = goToWeb) {
        ProvideGameSignals((state.bothWant + state.others).map { it.gameId }) {
        SharedWishlistContent(
            state = state,
            onBack = onBack,
            onRetry = viewModel::retry,
            onSignIn = { SignInPromptController.request() },
            onRowClick = { row -> viewModel.peek.peek(row.gameId, row.title, row.imageUrl) },
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedWishlistContent(
    state: SharedWishlistUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onRowClick: (SharedWishlistRowUi) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(stringResource(Res.string.shared_wishlist_title)) },
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
        ) { innerPadding ->
            val centered = Modifier.fillMaxSize().padding(innerPadding).padding(GameDealsCustomTheme.spacing.large)
            when {
                state.loading -> Box(centered, contentAlignment = Alignment.Center) {
                    val loadingLabel = stringResource(Res.string.account_list_loading)
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loadingLabel })
                }

                state.failed -> Column(
                    modifier = centered,
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.shared_wishlist_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.politeLiveRegion(),
                    )
                    Button(onClick = onRetry) { Text(stringResource(Res.string.shared_wishlist_retry)) }
                }

                state.isEmpty -> Box(centered, contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.shared_wishlist_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.politeLiveRegion(),
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                ) {
                    item(key = "intro") {
                        Text(
                            text = stringResource(Res.string.shared_wishlist_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!state.signedIn) {
                        item(key = "sign-in") { SignInCard(onSignIn) }
                    } else {
                        sectionHeader("both-header", Res.string.shared_wishlist_both_want)
                        if (state.bothWant.isEmpty()) {
                            item(key = "both-none") {
                                Text(
                                    text = stringResource(Res.string.shared_wishlist_both_want_none),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            rows(state.bothWant, onRowClick)
                        }
                    }
                    if (state.others.isNotEmpty()) {
                        val othersTitle = if (state.signedIn && state.bothWant.isNotEmpty()) {
                            Res.string.shared_wishlist_also_on_list
                        } else {
                            Res.string.shared_wishlist_on_list
                        }
                        sectionHeader("others-header", othersTitle)
                        rows(state.others, onRowClick)
                    }
                    if (state.unavailableCount > 0) {
                        item(key = "unavailable") {
                            Text(
                                text = stringResource(Res.string.shared_wishlist_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.sectionHeader(key: String, title: StringResource) {
    item(key = key) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(top = GameDealsCustomTheme.spacing.medium)
                .semantics { heading() },
        )
    }
}

private fun LazyListScope.rows(rows: ImmutableList<SharedWishlistRowUi>, onRowClick: (SharedWishlistRowUi) -> Unit) {
    itemsIndexed(rows, key = { _, row -> row.gameId }) { index, row ->
        SharedWishlistRow(row) { onRowClick(row) }
        if (index < rows.lastIndex) HorizontalDivider()
    }
}

@Composable
private fun SharedWishlistRow(row: SharedWishlistRowUi, onClick: () -> Unit) {
    val noDeal = stringResource(Res.string.account_waitlist_no_deal)
    val description = buildList {
        add(row.title)
        if (row.salePrice != null) {
            add(row.salePrice)
            if (row.discountPercent > 0) add("-${row.discountPercent}%")
        } else {
            add(noDeal)
        }
    }.joinToString(", ")
    DealListRow(
        title = row.title,
        contentDescription = description,
        onClick = onClick,
        imageUrl = row.imageUrl,
        salePrice = row.salePrice,
        regularPrice = row.regularPrice,
        neutralChip = noDeal.takeIf { row.salePrice == null },
        discountPercent = row.discountPercent,
        hasVoucher = row.hasVoucher,
        isNewHistoricalLow = row.isNewHistoricalLow,
        isStoreLow = row.isStoreLow,
        isLowestEver = row.isLowestEver,
        storeName = row.storeName,
        storeIconUrl = row.storeIconUrl,
        isWaitlisted = row.isWaitlisted,
        isRare = row.salePrice != null && LocalRowSignals.current.isRare(row.gameId, row.discountPercent),
        reviewText = LocalRowSignals.current.reviewText(row.gameId),
    )
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            Text(
                text = stringResource(Res.string.shared_wishlist_sign_in_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(Res.string.shared_wishlist_sign_in_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onSignIn) { Text(stringResource(Res.string.shared_wishlist_sign_in)) }
        }
    }
}

private fun previewRow(id: String, title: String, price: String?, value: Double?, waitlisted: Boolean) = SharedWishlistRowUi(
    gameId = id, title = title, imageUrl = null, salePrice = price, regularPrice = price?.let { "$24.99" },
    discountPercent = if (price != null) 60 else 0, storeName = price?.let { "Steam" }, storeIconUrl = null,
    hasVoucher = false, isNewHistoricalLow = false, isStoreLow = false, isLowestEver = value == 4.99,
    isWaitlisted = waitlisted, bestPriceValue = value,
)

@Preview
@Composable
private fun SharedWishlistSignedInPreview() {
    GameDealsTheme {
        SharedWishlistContent(
            state = SharedWishlistUiState(
                loading = false,
                signedIn = true,
                bothWant = persistentListOf(previewRow("1", "Hades", "$4.99", 4.99, true)),
                others = persistentListOf(
                    previewRow("2", "Celeste", "$9.99", 9.99, false),
                    previewRow("3", "Outer Wilds", null, null, false),
                ),
            ),
            onBack = {}, onRetry = {}, onSignIn = {}, onRowClick = {},
        )
    }
}

@Preview
@Composable
private fun SharedWishlistSignedOutPreview() {
    GameDealsTheme {
        SharedWishlistContent(
            state = SharedWishlistUiState(
                loading = false,
                signedIn = false,
                others = persistentListOf(previewRow("2", "Celeste", "$9.99", 9.99, false)),
                unavailableCount = 1,
            ),
            onBack = {}, onRetry = {}, onSignIn = {}, onRowClick = {},
        )
    }
}
