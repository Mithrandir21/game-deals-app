@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
import pm.bam.gamedeals.common.ui.PreviewGameDeal
import pm.bam.gamedeals.common.ui.PreviewGameDetails
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.cheapsharkDealRedirectUrl
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_cheapest_ever_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_cheapest_ever_on_date_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_cheapest_value_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_favourite_add_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_favourite_remove_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_game_image
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_list_item_savings_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_navigation_back_button
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_share_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_store_thumbnail
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_toolbar_title_loading
import pm.bam.gamedeals.feature.game.ui.GameViewModel.GameScreenData
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.store
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

private val AlwaysScrollable: () -> Boolean = { true }

@Composable
internal fun GameScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    viewModel: GameViewModel = koinViewModel()
) {
    val data = viewModel.uiState.collectAsStateWithLifecycle()
    val isFavourite = viewModel.isFavourite.collectAsStateWithLifecycle()
    val onRetry: () -> Unit = { viewModel.reloadGameDetails() }
    val platformActions = LocalPlatformActions.current

    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is GameViewModel.GameUiEvent.ShareDeal -> platformActions.share(event.text)
        }
    }

    // BoxWithConstraints is multiplatform — replaces the Android-only
    // currentWindowAdaptiveInfo() / WindowWidthSizeClass split. 600.dp matches
    // the Material3 Compact-vs-Medium boundary.
    BoxWithConstraints {
        GameScreenContent(
            isCompact = maxWidth < 600.dp,
            data = data.value,
            isFavourite = isFavourite.value,
            onBack = onBack,
            goToWeb = goToWeb,
            onShareDeal = { info, store, deal -> viewModel.onShareDealClicked(info, store, deal) },
            onToggleFavourite = { viewModel.toggleFavourite() },
            onRetry = onRetry
        )
    }
}

@Composable
private fun CompactGameDealsDetails(
    modifier: Modifier,
    data: GameScreenData.Data,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onShareDeal: (GameDetails.GameInfo, Store, GameDetails.GameDeal) -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        CompactGameDetail(data.gameDetails)

        Column(
            modifier = Modifier.padding(PaddingValues(start = GameDealsCustomTheme.spacing.large, end = GameDealsCustomTheme.spacing.large, bottom = GameDealsCustomTheme.spacing.large)),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium)
        ) {
            data.dealDetails.forEach {
                StoreGameDealRow(
                    store = it.store,
                    gameInfo = data.gameDetails.info,
                    deal = it.deal,
                    goToWeb = goToWeb,
                    onShareDeal = onShareDeal,
                )
            }
        }
    }
}

@Composable
private fun WideGameDealsDetails(
    modifier: Modifier,
    data: GameScreenData.Data,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onShareDeal: (GameDetails.GameInfo, Store, GameDetails.GameDeal) -> Unit,
) {
    Row(modifier = modifier) {
        WideGameDetail(data.gameDetails)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(GameDealsCustomTheme.spacing.large)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium)
        ) {
            data.dealDetails.forEach {
                StoreGameDealRow(
                    store = it.store,
                    gameInfo = data.gameDetails.info,
                    deal = it.deal,
                    goToWeb = goToWeb,
                    onShareDeal = onShareDeal,
                )
            }
        }
    }
}

@Composable
private fun CompactGameDetail(
    gameDetails: GameDetails
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GameDealsCustomTheme.spacing.large, top = GameDealsCustomTheme.spacing.large, bottom = GameDealsCustomTheme.spacing.large)
    ) {
        AsyncImage(
            model = gameDetails.info.thumb,
            contentDescription = stringResource(Res.string.game_screen_game_image, gameDetails.info.title),
            error = painterResource(CommonRes.drawable.videogame_thumb),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .height(200.dp)
                .aspectRatio(1f)
        )

        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.small)
        ) {
            Text(text = gameDetails.info.title)
            Text(
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.small),
                text = stringResource(Res.string.game_screen_cheapest_value_label, gameDetails.deals.minBy { it.priceValue }.priceDenominated)
            )
            Text(
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.medium),
                text = stringResource(Res.string.game_screen_cheapest_ever_label)
            )
            Text(
                text = stringResource(
                    Res.string.game_screen_cheapest_ever_on_date_label,
                    gameDetails.cheapestPriceEver.priceDenominated,
                    gameDetails.cheapestPriceEver.date
                )
            )
        }
    }
}

@Composable
private fun WideGameDetail(
    gameDetails: GameDetails
) {
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Min)
            .padding(start = GameDealsCustomTheme.spacing.large, top = GameDealsCustomTheme.spacing.large)
    ) {
        AsyncImage(
            model = gameDetails.info.thumb,
            contentDescription = stringResource(Res.string.game_screen_game_image, gameDetails.info.title),
            error = painterResource(CommonRes.drawable.videogame_thumb),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .height(200.dp)
                .aspectRatio(1f)
        )

        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.small)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = gameDetails.info.title
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GameDealsCustomTheme.spacing.small),
                text = stringResource(Res.string.game_screen_cheapest_value_label, gameDetails.deals.minBy { it.priceValue }.priceDenominated)
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GameDealsCustomTheme.spacing.medium),
                text = stringResource(Res.string.game_screen_cheapest_ever_label)
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(
                    Res.string.game_screen_cheapest_ever_on_date_label,
                    gameDetails.cheapestPriceEver.priceDenominated,
                    gameDetails.cheapestPriceEver.date
                )
            )
        }
    }
}

@Composable
private fun StoreGameDealRow(
    store: Store,
    gameInfo: GameDetails.GameInfo,
    deal: GameDetails.GameDeal,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onShareDeal: (GameDetails.GameInfo, Store, GameDetails.GameDeal) -> Unit,
) {
    Card(onClick = { goToWeb(cheapsharkDealRedirectUrl(deal.dealID), gameInfo.title) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GameDealsCustomTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = store.images.icon,
                contentDescription = stringResource(Res.string.game_screen_store_thumbnail, store.storeName),
                error = painterResource(CommonRes.drawable.store),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(GameDealsCustomTheme.spacing.large)
            )
            Text(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = GameDealsCustomTheme.spacing.medium),
                text = store.storeName,
            )
            Text(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                text = stringResource(Res.string.game_screen_list_item_savings_label, deal.savings),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
                    .padding(GameDealsCustomTheme.spacing.medium),
                text = deal.priceDenominated
            )
            IconButton(
                onClick = { onShareDeal(gameInfo, store, deal) }
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(Res.string.game_screen_share_action, store.storeName)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScreenContent(
    isCompact: Boolean,
    data: GameScreenData,
    isFavourite: Boolean,
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onShareDeal: (GameDetails.GameInfo, Store, GameDetails.GameDeal) -> Unit,
    onToggleFavourite: () -> Unit,
    onRetry: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topAppBarState, AlwaysScrollable)
    val currentOnRetry by rememberUpdatedState(onRetry)

    val errorMessage = stringResource(Res.string.game_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.game_screen_data_loading_error_retry)

    val title = when (data) {
        GameScreenData.Loading, GameScreenData.Error -> stringResource(Res.string.game_screen_toolbar_title_loading)
        is GameScreenData.Data -> data.gameDetails.info.title
    }

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
                            IconButton(
                                onClick = { onBack() }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.game_screen_navigation_back_button)
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                enabled = data is GameScreenData.Data,
                                onClick = onToggleFavourite,
                            ) {
                                AnimatedContent(targetState = isFavourite, label = "favourite-icon") { fav ->
                                    Icon(
                                        imageVector = if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = stringResource(
                                            if (fav) Res.string.game_screen_favourite_remove_action
                                            else Res.string.game_screen_favourite_add_action
                                        ),
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
            ) { innerPadding: PaddingValues ->
                when (data) {
                    GameScreenData.Loading -> CircularProgressIndicator(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                    )

                    GameScreenData.Error -> LaunchedEffect(snackbarHostState) {
                        val results = snackbarHostState.showSnackbar(
                            message = errorMessage,
                            actionLabel = errorRetry
                        )
                        if (results == SnackbarResult.ActionPerformed) {
                            currentOnRetry()
                        }
                    }

                    is GameScreenData.Data -> {
                        if (isCompact) {
                            CompactGameDealsDetails(Modifier.padding(innerPadding), data, goToWeb, onShareDeal)
                        } else {
                            WideGameDealsDetails(Modifier.padding(innerPadding), data, goToWeb, onShareDeal)
                        }
                    }

                }
            }
        }
}


private val previewGameDealDetails = persistentListOf(
    StoreDealPair(store = PreviewStore, deal = PreviewGameDeal),
    StoreDealPair(
        store = PreviewStore.copy(storeID = 11, storeName = "Humble Store"),
        deal = PreviewGameDeal.copy(
            storeID = 11,
            dealID = "deal-2",
            priceValue = 8.49,
            priceDenominated = "$8.49",
            savings = 78,
        ),
    ),
    StoreDealPair(
        store = PreviewStore.copy(storeID = 7, storeName = "GOG"),
        deal = PreviewGameDeal.copy(
            storeID = 7,
            dealID = "deal-3",
            priceValue = 11.99,
            priceDenominated = "$11.99",
            savings = 60,
        ),
    ),
)

@Preview
@Composable
private fun GameScreenContent_Compact_Success_Preview() {
    GameDealsTheme {
        GameScreenContent(
            isCompact = true,
            data = GameViewModel.GameScreenData.Data(
                gameDetails = PreviewGameDetails,
                dealDetails = previewGameDealDetails,
            ),
            isFavourite = false,
            onBack = {},
            goToWeb = { _, _ -> },
            onShareDeal = { _, _, _ -> },
            onToggleFavourite = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun GameScreenContent_Compact_Success_Favourited_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        GameScreenContent(
            isCompact = true,
            data = GameViewModel.GameScreenData.Data(
                gameDetails = PreviewGameDetails,
                dealDetails = previewGameDealDetails,
            ),
            isFavourite = true,
            onBack = {},
            goToWeb = { _, _ -> },
            onShareDeal = { _, _, _ -> },
            onToggleFavourite = {},
            onRetry = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun GameScreenContent_Wide_Success_Preview() {
    GameDealsTheme {
        GameScreenContent(
            isCompact = false,
            data = GameViewModel.GameScreenData.Data(
                gameDetails = PreviewGameDetails,
                dealDetails = previewGameDealDetails,
            ),
            isFavourite = false,
            onBack = {},
            goToWeb = { _, _ -> },
            onShareDeal = { _, _, _ -> },
            onToggleFavourite = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun GameScreenContent_Loading_Preview() {
    GameDealsTheme {
        GameScreenContent(
            isCompact = true,
            data = GameViewModel.GameScreenData.Loading,
            isFavourite = false,
            onBack = {},
            goToWeb = { _, _ -> },
            onShareDeal = { _, _, _ -> },
            onToggleFavourite = {},
            onRetry = {},
        )
    }
}

