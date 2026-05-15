@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.deal.DealBottomSheet
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.store.generated.resources.Res
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_deal_row_description
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_favourite_indicator
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_game_image
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_navigation_back_icon
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_store_banner
import pm.bam.gamedeals.feature.store.ui.StoreViewModel.StoreScreenData

private val AlwaysScrollable: () -> Boolean = { true }

@Composable
internal fun StoreScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    viewModel: StoreViewModel = koinViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deals: ImmutableList<Deal> by viewModel.deals.collectAsStateWithLifecycle()
    val favouriteIds by viewModel.favouriteIds.collectAsStateWithLifecycle()
    val dealDetails by viewModel.dealDetails.collectAsStateWithLifecycle()
    val errorMessage = stringResource(Res.string.store_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.store_screen_data_loading_error_retry)
    val platformActions = LocalPlatformActions.current

    val store = (uiState as? StoreScreenData.Data)?.store

    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is StoreViewModel.StoreUiEvent.ShareDeal -> platformActions.share(event.text)
        }
    }

    StoreDeals(
        deals = deals,
        favouriteIds = favouriteIds,
        dealDetails = dealDetails,
        storeDetails = store,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onLoadDealDetails = { dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated ->
            viewModel.loadDealDetails(
                dealId = dealId,
                dealStoreId = dealStoreId,
                dealGameId = dealGameId,
                dealTitle = dealTitle,
                dealPriceDenominated = dealPriceDenominated,
            )
        },
        onDismissDealDetails = { viewModel.dismissDealDetails() },
        onShareDealDetails = { sheetData -> viewModel.onShareDealClicked(sheetData) },
        onToggleDealFavourite = { sheetData -> viewModel.toggleFavouriteFromDeal(sheetData) },
        goToWeb = goToWeb
    )

    when (uiState) {
        StoreScreenData.Loading -> CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        )

        StoreScreenData.Error -> LaunchedEffect(snackbarHostState) {
            val results = snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = errorRetry
            )
            if (results == SnackbarResult.ActionPerformed) {
                viewModel.retry()
            }
        }

        is StoreScreenData.Data -> Unit
    }
}

@Composable
private fun DealRow(
    deal: Deal,
    isFavourite: Boolean,
    onViewDealDetails: ((deal: Deal) -> Unit)
) {
    val dealRowCd = stringResource(Res.string.store_screen_deal_row_description, deal.title, deal.salePriceDenominated)
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onViewDealDetails(deal) }
                .padding(
                    start = GameDealsCustomTheme.spacing.small,
                    top = GameDealsCustomTheme.spacing.extraSmall,
                    end = GameDealsCustomTheme.spacing.medium,
                    bottom = GameDealsCustomTheme.spacing.extraSmall
                )
                .semantics { contentDescription = dealRowCd },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(60.dp)
                    .width(100.dp),
            ) {
                AsyncImage(
                    model = deal.thumb,
                    contentDescription = stringResource(Res.string.store_screen_game_image, deal.title),
                    error = painterResource(CommonRes.drawable.videogame_thumb),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
                )
                if (isFavourite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = stringResource(Res.string.store_screen_favourite_indicator),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(GameDealsCustomTheme.spacing.extraSmall)
                            .size(16.dp),
                    )
                }
            }
            Text(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = GameDealsCustomTheme.spacing.medium),
                textAlign = TextAlign.Start,
                text = deal.title
            )
            Text(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
                    .padding(GameDealsCustomTheme.spacing.medium),
                text = deal.salePriceDenominated,
            )
        }
    }
}

@Composable
private fun StoreDeals(
    deals: ImmutableList<Deal>,
    favouriteIds: Set<Int>,
    dealDetails: DealBottomSheetData? = null,
    storeDetails: Store? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit,
    onLoadDealDetails: (dealId: String, dealStoreId: Int, dealGameId: Int, dealTitle: String, dealPriceDenominated: String) -> Unit,
    onDismissDealDetails: () -> Unit,
    onShareDealDetails: (data: DealBottomSheetData) -> Unit,
    onToggleDealFavourite: (data: DealBottomSheetData.DealDetailsData) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = { StoreToolbar(onBack, storeDetails) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding: PaddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            items(
                items = deals,
                key = { it.dealID }
            ) { deal ->
                DealRow(deal, deal.gameID in favouriteIds) {
                    onLoadDealDetails(deal.dealID, deal.storeID, deal.gameID, deal.title, deal.salePriceDenominated)
                }
            }
        }

        DealBottomSheet(
            data = dealDetails,
            isFavourite = dealDetails?.gameId?.let { it in favouriteIds } == true,
            goToWeb = goToWeb,
            onDismiss = { onDismissDealDetails() },
            onShare = { sheetData -> onShareDealDetails(sheetData) },
            onToggleFavourite = { sheetData -> onToggleDealFavourite(sheetData) },
            onRetryDealDetails = { dealDetails?.let { onLoadDealDetails(it.dealId, it.store.storeID, it.gameId, it.gameName, it.gameSalesPriceDenominated) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreToolbar(
    onBack: () -> Unit,
    storeDetails: Store? = null
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior: TopAppBarScrollBehavior =
        TopAppBarDefaults.pinnedScrollBehavior(topAppBarState, AlwaysScrollable)

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = storeDetails?.images?.banner,
                    contentDescription = stringResource(Res.string.store_screen_store_banner, storeDetails?.storeName ?: ""),
                    error = painterResource(CommonRes.drawable.videogame_thumb),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(60.dp)
                        .width(100.dp)
                        .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
                )
                Text(
                    modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.medium),
                    text = storeDetails?.storeName ?: "",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = { onBack() }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.store_screen_navigation_back_icon)
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}


private val previewDealsList = persistentListOf(
    PreviewDeal,
    PreviewDeal.copy(
        dealID = "deal-2",
        title = "Hollow Knight",
        salePriceDenominated = "$7.49",
        normalPriceDenominated = "$14.99",
        gameID = 222,
    ),
    PreviewDeal.copy(
        dealID = "deal-3",
        title = "Stardew Valley",
        salePriceDenominated = "$8.99",
        normalPriceDenominated = "$14.99",
        gameID = 333,
    ),
)

@Preview
@Composable
private fun StoreDeals_Success_Preview() {
    GameDealsTheme {
        StoreDeals(
            deals = previewDealsList,
            favouriteIds = setOf(222), // marks Hollow Knight as favourited
            storeDetails = PreviewStore,
            onBack = {},
            onLoadDealDetails = { _, _, _, _, _ -> },
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealFavourite = {},
            goToWeb = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun StoreDeals_Success_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        StoreDeals(
            deals = previewDealsList,
            favouriteIds = setOf(222),
            storeDetails = PreviewStore,
            onBack = {},
            onLoadDealDetails = { _, _, _, _, _ -> },
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealFavourite = {},
            goToWeb = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun StoreDeals_Empty_Preview() {
    GameDealsTheme {
        StoreDeals(
            deals = persistentListOf(),
            favouriteIds = emptySet(),
            storeDetails = PreviewStore,
            onBack = {},
            onLoadDealDetails = { _, _, _, _, _ -> },
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealFavourite = {},
            goToWeb = { _, _ -> },
        )
    }
}
