package pm.bam.gamedeals.feature.deals.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.deal.DealBottomSheet
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.DealsSort
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.deals.generated.resources.Res
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_deal_row_description
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_deal_row_description_waitlisted
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_empty_label
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_load_more_error_msg
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_loading_error_msg
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_loading_error_retry
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_all_stores
import pm.bam.gamedeals.feature.deals.generated.resources.deals_sort_price_low_high
import pm.bam.gamedeals.feature.deals.generated.resources.deals_sort_recently_added
import pm.bam.gamedeals.feature.deals.generated.resources.deals_sort_top_discount
import pm.bam.gamedeals.feature.deals.ui.DealsViewModel.DealsScreenData
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_waitlist_sign_in_required

// Trigger a load-more when the user scrolls within this many rows of the end of the loaded page.
private const val LOAD_MORE_THRESHOLD = 5

@Composable
internal fun DealsScreen(
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
    viewModel: DealsViewModel = koinViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val data by viewModel.uiState.collectAsStateWithLifecycle()
    val waitlistIds by viewModel.waitlistIds.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val selectedShops by viewModel.selectedShops.collectAsStateWithLifecycle()
    val dealDetails by viewModel.dealDetails.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current
    val loadMoreError = stringResource(Res.string.deals_screen_load_more_error_msg)
    val signInRequired = stringResource(CommonRes.string.deal_waitlist_sign_in_required)

    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is DealsViewModel.DealsUiEvent.ShareDeal -> platformActions.share(event.text)
            DealsViewModel.DealsUiEvent.LoadMoreError -> snackbarHostState.showSnackbar(loadMoreError)
            DealsViewModel.DealsUiEvent.SignInRequired -> snackbarHostState.showSnackbar(signInRequired)
        }
    }

    DealsContent(
        data = data,
        waitlistIds = waitlistIds,
        stores = stores,
        selectedShops = selectedShops,
        dealDetails = dealDetails,
        snackbarHostState = snackbarHostState,
        onSelectSort = { viewModel.setSort(it) },
        onToggleShop = { viewModel.toggleShop(it) },
        onClearShops = { viewModel.clearShopFilter() },
        onLoadMore = { viewModel.loadNextPage() },
        onRetry = { viewModel.retry() },
        onLoadDealDetails = { dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated, dealUrl ->
            viewModel.loadDealDetails(dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated, dealUrl)
        },
        onDismissDealDetails = { viewModel.dismissDealDetails() },
        onShareDealDetails = { sheetData -> viewModel.onShareDealClicked(sheetData) },
        onToggleDealWaitlist = { sheetData -> viewModel.toggleWaitlistFromDeal(sheetData) },
        onToggleWaitlist = { gameId -> viewModel.toggleWaitlist(gameId) },
        goToWeb = goToWeb,
        goToGameDetails = goToGameDetails,
        goToGameDetailsByTitle = goToGameDetailsByTitle,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DealsContent(
    data: DealsScreenData,
    waitlistIds: ImmutableSet<String>,
    stores: ImmutableList<Store> = persistentListOf(),
    selectedShops: ImmutableSet<Int> = persistentSetOf(),
    dealDetails: DealBottomSheetData? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onSelectSort: (DealsSort) -> Unit,
    onToggleShop: (Int) -> Unit = {},
    onClearShops: () -> Unit = {},
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onLoadDealDetails: (dealId: String, dealStoreId: Int, dealGameId: String, dealTitle: String, dealPriceDenominated: String, dealUrl: String) -> Unit,
    onDismissDealDetails: () -> Unit,
    onShareDealDetails: (data: DealBottomSheetData) -> Unit,
    onToggleDealWaitlist: (data: DealBottomSheetData.DealDetailsData) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit = {},
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit = { _, _ -> },
    goToGameDetailsByTitle: (title: String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val errorMessage = stringResource(Res.string.deals_screen_loading_error_msg)
    val errorRetry = stringResource(Res.string.deals_screen_loading_error_retry)
    val storesById = remember(stores) { stores.associateBy(Store::storeID) }
    val addToWaitlistCd = stringResource(CommonRes.string.deal_favourite_add_action)
    val removeFromWaitlistCd = stringResource(CommonRes.string.deal_favourite_remove_action)

    // Load-more: fire once the user scrolls within LOAD_MORE_THRESHOLD rows of the end. The VM guards
    // against duplicate/exhausted calls (appending / endReached / non-Data state).
    val shouldLoadMore by remember(data.deals.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            data.deals.isNotEmpty() && lastVisible >= data.deals.size - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    if (data.status == DealsScreenData.Status.ERROR) {
        LaunchedEffect(snackbarHostState) {
            val result = snackbarHostState.showSnackbar(message = errorMessage, actionLabel = errorRetry)
            if (result == SnackbarResult.ActionPerformed) onRetry()
        }
    }

    Scaffold(
        // The app shell owns the top bar + bottom nav and already insets the NavHost; this inner
        // Scaffold only hosts the snackbar, so it contributes no insets of its own.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding: PaddingValues ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SortFilterRow(
                    selected = data.sort,
                    onSelectSort = onSelectSort,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (stores.isNotEmpty()) {
                    ShopFilterRow(
                        stores = stores,
                        selected = selectedShops,
                        onToggleShop = onToggleShop,
                        onClearShops = onClearShops,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        data.status == DealsScreenData.Status.LOADING -> CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                        )

                        data.status == DealsScreenData.Status.DATA && data.deals.isEmpty() -> Text(
                            text = stringResource(Res.string.deals_screen_empty_label),
                            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                        )

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(vertical = GameDealsCustomTheme.spacing.small),
                        ) {
                            items(items = data.deals, key = { it.dealID }) { deal ->
                                val store = storesById[deal.storeID]
                                val isWaitlisted = deal.gameID in waitlistIds
                                DealListRow(
                                    title = deal.title,
                                    contentDescription = stringResource(
                                        if (isWaitlisted) Res.string.deals_screen_deal_row_description_waitlisted
                                        else Res.string.deals_screen_deal_row_description,
                                        deal.title, deal.salePriceDenominated,
                                    ),
                                    onClick = { onLoadDealDetails(deal.dealID, deal.storeID, deal.gameID, deal.title, deal.salePriceDenominated, deal.url) },
                                    imageUrl = deal.thumb,
                                    salePrice = deal.salePriceDenominated,
                                    regularPrice = deal.normalPriceDenominated,
                                    discountPercent = deal.savings.roundToInt(),
                                    isLowestEver = deal.isLowestEver,
                                    storeName = store?.storeName,
                                    storeIconUrl = store?.iconUrl,
                                    isWaitlisted = isWaitlisted,
                                    onToggleWaitlist = { onToggleWaitlist(deal.gameID) },
                                    addToWaitlistContentDescription = addToWaitlistCd,
                                    removeFromWaitlistContentDescription = removeFromWaitlistCd,
                                )
                            }
                            if (data.appending) {
                                item(key = "deals-load-more-spinner") {
                                    CircularProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DealBottomSheet(
                data = dealDetails,
                isWaitlisted = dealDetails?.gameId?.let { it in waitlistIds } == true,
                goToWeb = goToWeb,
                goToGameDetails = goToGameDetails,
                goToGameDetailsByTitle = goToGameDetailsByTitle,
                onDismiss = { onDismissDealDetails() },
                onShare = { sheetData -> onShareDealDetails(sheetData) },
                onToggleWaitlist = { sheetData -> onToggleDealWaitlist(sheetData) },
                onRetryDealDetails = { dealDetails?.let { onLoadDealDetails(it.dealId, it.store.storeID, it.gameId, it.gameName, it.gameSalesPriceDenominated, it.dealUrl) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortFilterRow(
    selected: DealsSort,
    onSelectSort: (DealsSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        DealsSort.entries.forEach { sort ->
            FilterChip(
                selected = sort == selected,
                onClick = { onSelectSort(sort) },
                label = { Text(stringResource(sort.labelRes())) },
            )
        }
    }
}

private fun DealsSort.labelRes(): StringResource = when (this) {
    DealsSort.TopDiscount -> Res.string.deals_sort_top_discount
    DealsSort.RecentlyAdded -> Res.string.deals_sort_recently_added
    DealsSort.PriceLowToHigh -> Res.string.deals_sort_price_low_high
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopFilterRow(
    stores: ImmutableList<Store>,
    selected: ImmutableSet<Int>,
    onToggleShop: (Int) -> Unit,
    onClearShops: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        // Empty selection == all stores; the "All stores" chip clears the filter.
        FilterChip(
            selected = selected.isEmpty(),
            onClick = onClearShops,
            label = { Text(stringResource(Res.string.deals_filter_all_stores)) },
        )
        stores.forEach { store ->
            FilterChip(
                selected = store.storeID in selected,
                onClick = { onToggleShop(store.storeID) },
                label = { Text(store.storeName) },
            )
        }
    }
}

private val previewDeals = persistentListOf(
    PreviewDeal,
    PreviewDeal.copy(dealID = "deal-2", title = "Hollow Knight", salePriceDenominated = "$7.49", gameID = "222"),
    PreviewDeal.copy(dealID = "deal-3", title = "Stardew Valley", salePriceDenominated = "$8.99", gameID = "333"),
)

private val previewStores = persistentListOf(
    PreviewStore,
    PreviewStore.copy(storeID = 16, storeName = "Epic Games Store"),
    PreviewStore.copy(storeID = 35, storeName = "GOG"),
)

@Preview
@Composable
private fun DealsContent_Success_Preview() {
    GameDealsTheme {
        DealsContent(
            data = DealsScreenData(status = DealsScreenData.Status.DATA, deals = previewDeals),
            waitlistIds = persistentSetOf("222"),
            stores = previewStores,
            selectedShops = persistentSetOf(16),
            onSelectSort = {},
            onLoadMore = {},
            onRetry = {},
            onLoadDealDetails = { _, _, _, _, _, _ -> },
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealWaitlist = {},
            goToWeb = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun DealsContent_Loading_Preview() {
    GameDealsTheme {
        DealsContent(
            data = DealsScreenData(status = DealsScreenData.Status.LOADING),
            waitlistIds = persistentSetOf(),
            onSelectSort = {},
            onLoadMore = {},
            onRetry = {},
            onLoadDealDetails = { _, _, _, _, _, _ -> },
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealWaitlist = {},
            goToWeb = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun DealsContent_Empty_Preview() {
    GameDealsTheme {
        DealsContent(
            data = DealsScreenData(status = DealsScreenData.Status.DATA, deals = persistentListOf()),
            waitlistIds = persistentSetOf(),
            onSelectSort = {},
            onLoadMore = {},
            onRetry = {},
            onLoadDealDetails = { _, _, _, _, _, _ -> },
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealWaitlist = {},
            goToWeb = { _, _ -> },
        )
    }
}
