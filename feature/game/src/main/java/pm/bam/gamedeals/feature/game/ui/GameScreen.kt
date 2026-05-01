package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import pm.bam.gamedeals.common.ui.FoldableLandscape
import pm.bam.gamedeals.common.ui.FoldablePortrait
import pm.bam.gamedeals.common.ui.PhoneLandscape
import pm.bam.gamedeals.common.ui.PhonePortrait
import pm.bam.gamedeals.common.ui.PreviewGameDeal
import pm.bam.gamedeals.common.ui.PreviewGameDetails
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.TabletLandscape
import pm.bam.gamedeals.common.ui.TabletPortrait
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.game.R
import pm.bam.gamedeals.feature.game.ui.GameViewModel.GameScreenData


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun GameScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    viewModel: GameViewModel = hiltViewModel()
) {
    val data = viewModel.uiState.collectAsStateWithLifecycle()
    val windowInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo()

    val onRetry: () -> Unit = { viewModel.reloadGameDetails() }

    ScreenScaffold(
        windowWidth = windowInfo.windowSizeClass.widthSizeClass,
        data = data.value,
        onBack = onBack,
        goToWeb = goToWeb,
        onRetry = onRetry
    )
}

@Composable
private fun CompactGameDealsDetails(
    modifier: Modifier,
    data: GameScreenData.Data,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .testTag(GameDealsTag),
        horizontalAlignment = Alignment.Start
    ) {
        CompactGameDetail(data.gameDetails)

        Column(
            modifier = Modifier.padding(PaddingValues(start = GameDealsCustomTheme.spacing.large, end = GameDealsCustomTheme.spacing.large, bottom = GameDealsCustomTheme.spacing.large)),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium)
        ) {
            data.dealDetails.forEach {
                StoreGameDealRow(it.first, data.gameDetails.info, it.second, goToWeb = goToWeb)
            }
        }
    }
}

@Composable
private fun WideGameDealsDetails(
    modifier: Modifier,
    data: GameScreenData.Data,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    Row(modifier = modifier.testTag(GameDealsTag)) {
        WideGameDetail(data.gameDetails)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(GameDealsCustomTheme.spacing.large)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium)
        ) {
            data.dealDetails.forEach {
                StoreGameDealRow(it.first, data.gameDetails.info, it.second, goToWeb = goToWeb)
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
            .testTag(GameDetailsTag)
    ) {
        AsyncImage(
            model = gameDetails.info.thumb,
            contentDescription = stringResource(R.string.game_screen_game_image, gameDetails.info.title),
            error = painterResource(id = pm.bam.gamedeals.common.ui.R.drawable.videogame_thumb),
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
                modifier = Modifier.testTag(GameDetailsTitleTag),
                text = gameDetails.info.title)
            Text(
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.small),
                text = stringResource(R.string.game_screen_cheapest_value_label, gameDetails.deals.minBy { it.priceValue }.priceDenominated)
            )
            Text(
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.medium),
                text = stringResource(R.string.game_screen_cheapest_ever_label)
            )
            Text(
                text = stringResource(
                    R.string.game_screen_cheapest_ever_on_date_label,
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
            .testTag(GameDetailsTag)
    ) {
        AsyncImage(
            model = gameDetails.info.thumb,
            contentDescription = stringResource(R.string.game_screen_game_image, gameDetails.info.title),
            error = painterResource(id = pm.bam.gamedeals.common.ui.R.drawable.videogame_thumb),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GameDetailsTitleTag),
                text = gameDetails.info.title
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GameDealsCustomTheme.spacing.small),
                text = stringResource(R.string.game_screen_cheapest_value_label, gameDetails.deals.minBy { it.priceValue }.priceDenominated)
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GameDealsCustomTheme.spacing.medium),
                text = stringResource(R.string.game_screen_cheapest_ever_label)
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(
                    R.string.game_screen_cheapest_ever_on_date_label,
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
) {
    Card(onClick = { goToWeb("https://www.cheapshark.com/redirect?dealID=${deal.dealID}", gameInfo.title) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GameDealsCustomTheme.spacing.medium)
                .testTag(GameDealItemTag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = store.images.icon,
                contentDescription = stringResource(R.string.game_screen_store_thumbnail, store.storeName),
                error = painterResource(id = pm.bam.gamedeals.common.ui.R.drawable.store),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(GameDealsCustomTheme.spacing.large)
            )
            Text(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                    .testTag(GameDealItemStoreTitleLabelTag),
                text = store.storeName,
            )
            Text(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                text = stringResource(R.string.game_screen_list_item_savings_label, deal.savings),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
                    .padding(GameDealsCustomTheme.spacing.medium),
                text = deal.priceDenominated
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(
    windowWidth: WindowWidthSizeClass,
    data: GameScreenData,
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val currentOnRetry by rememberUpdatedState(onRetry)

    val title = when (data) {
        GameScreenData.Loading, GameScreenData.Error -> stringResource(R.string.game_screen_toolbar_title_loading)
        is GameScreenData.Data -> data.gameDetails.info.title
    }

    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        modifier = Modifier.testTag(TopAppBarTag),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        title = { Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(
                                modifier = Modifier.testTag(TopAppNavBarTag),
                                onClick = { onBack() }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.game_screen_navigation_back_button)
                                )
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
                            .testTag(LoadingDataTag)
                    )

                    GameScreenData.Error -> LaunchedEffect(snackbarHostState) {
                        val results = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.game_screen_data_loading_error_msg),
                            actionLabel = context.getString(R.string.game_screen_data_loading_error_retry)
                        )
                        if (results == SnackbarResult.ActionPerformed) {
                            currentOnRetry()
                        }
                    }

                    is GameScreenData.Data -> {
                        when (windowWidth) {
                            WindowWidthSizeClass.Compact -> CompactGameDealsDetails(Modifier.padding(innerPadding), data, goToWeb)
                            else -> WideGameDealsDetails(Modifier.padding(innerPadding), data, goToWeb)
                        }
                    }

                }
            }
        }
    }
}


@PhonePortrait
@Composable
private fun PreviewLoading() {
    ScreenScaffold(
        windowWidth = WindowWidthSizeClass.Compact,
        data = GameScreenData.Loading,
        onBack = {},
        goToWeb = { _, _ -> },
        onRetry = {}
    )
}

@PhonePortrait
@Composable
private fun PreviewError() {
    ScreenScaffold(
        windowWidth = WindowWidthSizeClass.Compact,
        data = GameScreenData.Error,
        onBack = {},
        goToWeb = { _, _ -> },
        onRetry = {}
    )
}

@PhonePortrait
@Composable
private fun PreviewCompact() {
    ScreenScaffold(
        windowWidth = WindowWidthSizeClass.Compact,
        data = GameScreenData.Data(
            gameDetails = PreviewGameDetails,
            dealDetails = List(19) { PreviewStore to PreviewGameDeal }
        ),
        onBack = {},
        goToWeb = { _, _ -> },
        onRetry = {}
    )
}

@PhoneLandscape
@TabletPortrait
@Composable
private fun PreviewMedium() {
    ScreenScaffold(
        windowWidth = WindowWidthSizeClass.Medium,
        data = GameScreenData.Data(
            gameDetails = PreviewGameDetails,
            dealDetails = List(19) { PreviewStore to PreviewGameDeal }
        ),
        onBack = {},
        goToWeb = { _, _ -> },
        onRetry = {}
    )
}

@FoldablePortrait
@FoldableLandscape
@TabletLandscape
@Composable
private fun PreviewExpanded() {
    ScreenScaffold(
        windowWidth = WindowWidthSizeClass.Expanded,
        data = GameScreenData.Data(
            gameDetails = PreviewGameDetails,
            dealDetails = List(19) { PreviewStore to PreviewGameDeal }
        ),
        onBack = {},
        goToWeb = { _, _ -> },
        onRetry = {}
    )
}


internal const val TopAppBarTag = "TopAppBarTag"
internal const val TopAppNavBarTag = "TopAppNavBarTag"
internal const val LoadingDataTag = "LoadingDataTag"

internal const val GameDetailsTag = "GameDetailsTag"
internal const val GameDetailsTitleTag = "GameDetailsTitleTag"

internal const val GameDealsTag = "GameDealsTag"
internal const val GameDealItemTag = "GameDealItemTag"
internal const val GameDealItemStoreTitleLabelTag = "GameDealItemStoreTitleLabelTag"

