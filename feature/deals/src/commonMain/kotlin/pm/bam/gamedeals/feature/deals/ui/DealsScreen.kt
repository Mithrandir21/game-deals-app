package pm.bam.gamedeals.feature.deals.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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
import pm.bam.gamedeals.common.navigation.SearchRequestBus
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.components.WaitlistHeartButton
import pm.bam.gamedeals.common.ui.deal.DealBottomSheet
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealFlag
import pm.bam.gamedeals.domain.models.DealsFilter
import pm.bam.gamedeals.domain.models.DealsSort
import pm.bam.gamedeals.domain.models.ProductType
import pm.bam.gamedeals.domain.models.ReleaseWindow
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.deals.generated.resources.Res
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_all_stores
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_button
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_button_count
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_sort
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_stores
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_sheet_title
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_reset
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_chip_any
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_discount
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_discount_tier
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_price
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_price_free
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_price_under
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_type
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_type_game
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_type_dlc
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_type_bundle
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_drm
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_drm_free_label
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_drm_free_switch_description
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_flag
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_flag_new_low
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_flag_historical_low
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_flag_shop_low
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_reviews
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_reviews_tier
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_section_release
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_release_new
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_release_this_year
import pm.bam.gamedeals.feature.deals.generated.resources.deals_filter_release_older
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_deal_row_description
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_deal_row_description_waitlisted
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_empty_label
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_load_more_error_msg
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_loading_error_msg
import pm.bam.gamedeals.feature.deals.generated.resources.deals_screen_loading_error_retry
import pm.bam.gamedeals.feature.deals.generated.resources.deals_search_close
import pm.bam.gamedeals.feature.deals.generated.resources.deals_search_field_label
import pm.bam.gamedeals.feature.deals.generated.resources.deals_search_icon
import pm.bam.gamedeals.feature.deals.generated.resources.deals_search_loading_indicator
import pm.bam.gamedeals.feature.deals.generated.resources.deals_search_no_results_label
import pm.bam.gamedeals.feature.deals.generated.resources.deals_search_result_count
import pm.bam.gamedeals.feature.deals.generated.resources.deals_search_result_row_description
import pm.bam.gamedeals.feature.deals.generated.resources.deals_search_result_row_description_waitlisted
import pm.bam.gamedeals.feature.deals.generated.resources.deals_sort_expiring_soon
import pm.bam.gamedeals.feature.deals.generated.resources.deals_sort_price_low_high
import pm.bam.gamedeals.feature.deals.generated.resources.deals_sort_recently_added
import pm.bam.gamedeals.feature.deals.generated.resources.deals_sort_top_discount
import pm.bam.gamedeals.feature.deals.generated.resources.deals_sort_trending
import pm.bam.gamedeals.feature.deals.ui.DealsViewModel.DealsScreenData
import pm.bam.gamedeals.feature.deals.ui.DealsViewModel.SearchResultsState
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_waitlist_sign_in_required

// Trigger a load-more when the user scrolls within this many rows of the end of the loaded page.
private const val LOAD_MORE_THRESHOLD = 5

// Preset thresholds for the single-select filter chip rows (see DealsFilterSheet).
private val CUT_TIERS = listOf(25, 50, 75, 90)
private val PRICE_TIERS = listOf(5, 10, 20, 50)
private val STEAM_TIERS = listOf(70, 80, 90)

@Composable
internal fun DealsScreen(
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGame: (gameId: String) -> Unit,
    viewModel: DealsViewModel = koinViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val data by viewModel.uiState.collectAsStateWithLifecycle()
    val waitlistIds by viewModel.waitlistIds.collectAsStateWithLifecycle()
    val ignoredIds by viewModel.ignoredIds.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val selectedShops by viewModel.selectedShops.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val dealDetails by viewModel.dealDetails.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current
    val loadMoreError = stringResource(Res.string.deals_screen_load_more_error_msg)
    val signInRequired = stringResource(CommonRes.string.deal_waitlist_sign_in_required)

    var searchRevealed by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    // Reveal (and optionally prefill) the search field when requested from elsewhere — the app-shell
    // search icon, or the Game Page's "search by title" deep-link (consumed once via the bus).
    LaunchedEffect(Unit) {
        SearchRequestBus.requests.collect { title ->
            searchRevealed = true
            if (!title.isNullOrBlank()) viewModel.setSearchQuery(title)
        }
    }

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
        ignoredIds = ignoredIds,
        stores = stores,
        selectedShops = selectedShops,
        filter = filter,
        searchRevealed = searchRevealed,
        searchQuery = searchQuery,
        searchResults = searchResults,
        showFilters = showFilters,
        dealDetails = dealDetails,
        snackbarHostState = snackbarHostState,
        onSelectSort = { viewModel.setSort(it) },
        onToggleShop = { viewModel.toggleShop(it) },
        onClearShops = { viewModel.clearShopFilter() },
        onSetMinCut = { viewModel.setMinCut(it) },
        onSetMaxPrice = { viewModel.setMaxPrice(it) },
        onToggleType = { viewModel.toggleType(it) },
        onSetDrmFree = { viewModel.setDrmFree(it) },
        onSetFlag = { viewModel.setFlag(it) },
        onSetMinSteam = { viewModel.setMinSteam(it) },
        onSetRelease = { viewModel.setRelease(it) },
        onClearFilters = { viewModel.clearFilters() },
        onShowFiltersChange = { showFilters = it },
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onCloseSearch = {
            searchRevealed = false
            viewModel.clearSearch()
        },
        onLoadMore = { viewModel.loadNextPage() },
        onRetry = { viewModel.retry() },
        onLoadDealDetails = { dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated, dealUrl ->
            viewModel.loadDealDetails(dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated, dealUrl)
        },
        onDismissDealDetails = { viewModel.dismissDealDetails() },
        onShareDealDetails = { sheetData -> viewModel.onShareDealClicked(sheetData) },
        onToggleDealWaitlist = { sheetData -> viewModel.toggleWaitlistFromDeal(sheetData) },
        onToggleWaitlist = { gameId -> viewModel.toggleWaitlist(gameId) },
        onToggleDealIgnore = { sheetData -> viewModel.toggleIgnoreFromDeal(sheetData) },
        goToWeb = goToWeb,
        goToGame = goToGame,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DealsContent(
    data: DealsScreenData,
    waitlistIds: ImmutableSet<String>,
    ignoredIds: ImmutableSet<String> = persistentSetOf(),
    stores: ImmutableList<Store> = persistentListOf(),
    selectedShops: ImmutableSet<Int> = persistentSetOf(),
    filter: DealsFilter = DealsFilter(),
    searchRevealed: Boolean = false,
    searchQuery: String = "",
    searchResults: SearchResultsState = SearchResultsState.Idle,
    showFilters: Boolean = false,
    dealDetails: DealBottomSheetData? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onSelectSort: (DealsSort) -> Unit,
    onToggleShop: (Int) -> Unit = {},
    onClearShops: () -> Unit = {},
    onSetMinCut: (Int?) -> Unit = {},
    onSetMaxPrice: (Double?) -> Unit = {},
    onToggleType: (ProductType) -> Unit = {},
    onSetDrmFree: (Boolean) -> Unit = {},
    onSetFlag: (DealFlag?) -> Unit = {},
    onSetMinSteam: (Int?) -> Unit = {},
    onSetRelease: (ReleaseWindow?) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onShowFiltersChange: (Boolean) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onLoadDealDetails: (dealId: String, dealStoreId: Int, dealGameId: String, dealTitle: String, dealPriceDenominated: String, dealUrl: String) -> Unit,
    onDismissDealDetails: () -> Unit,
    onShareDealDetails: (data: DealBottomSheetData) -> Unit,
    onToggleDealWaitlist: (data: DealBottomSheetData.DealDetailsData) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit = {},
    onToggleDealIgnore: (data: DealBottomSheetData.DealDetailsData) -> Unit = {},
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGame: (gameId: String) -> Unit = {},
) {
    val searching = searchQuery.isNotBlank()
    // Hide ignored games from the deals list (#280). Paging still tracks the full fetched list (offset),
    // so only the rendered list is filtered; the id set is Room-backed (correct on cold start + offline).
    val visibleDeals = remember(data.deals, ignoredIds) { data.deals.filter { it.gameID !in ignoredIds } }
    val listState = rememberLazyListState()
    val errorMessage = stringResource(Res.string.deals_screen_loading_error_msg)
    val errorRetry = stringResource(Res.string.deals_screen_loading_error_retry)
    val storesById = remember(stores) { stores.associateBy(Store::storeID) }
    val addToWaitlistCd = stringResource(CommonRes.string.deal_favourite_add_action)
    val removeFromWaitlistCd = stringResource(CommonRes.string.deal_favourite_remove_action)

    // Load-more: fire once the user scrolls within LOAD_MORE_THRESHOLD rows of the end. The VM guards
    // against duplicate/exhausted calls (appending / endReached / non-Data state). Browse mode only.
    val shouldLoadMore by remember(visibleDeals.size, data.deals.size, searching) {
        derivedStateOf {
            if (searching) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Also fetch more when the whole loaded page is filtered out (all ignored); the VM guards
            // against duplicate/exhausted calls (appending / endReached).
            data.deals.isNotEmpty() && (visibleDeals.isEmpty() || lastVisible >= visibleDeals.size - LOAD_MORE_THRESHOLD)
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    if (data.status == DealsScreenData.Status.ERROR && !searching) {
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
                // Toolbar: the title-search field when revealed, otherwise a Filter button opening the sheet.
                if (searchRevealed) {
                    DealsSearchField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        onClose = onCloseSearch,
                    )
                } else {
                    FilterBar(
                        activeCount = selectedShops.size + filter.activeCount,
                        onClick = { onShowFiltersChange(true) },
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        searching -> SearchResultsBody(
                            state = searchResults,
                            ignoredIds = ignoredIds,
                            waitlistIds = waitlistIds,
                            storesById = storesById,
                            addToWaitlistCd = addToWaitlistCd,
                            removeFromWaitlistCd = removeFromWaitlistCd,
                            errorMessage = errorMessage,
                            onGame = goToGame,
                            onToggleWaitlist = onToggleWaitlist,
                        )

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
                            items(items = visibleDeals, key = { it.dealID }) { deal ->
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
                                    hasVoucher = deal.hasVoucher,
                                    isNewHistoricalLow = deal.isNewHistoricalLow,
                                    isStoreLow = deal.isStoreLow,
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
                isIgnored = dealDetails?.gameId?.let { it in ignoredIds } == true,
                goToWeb = goToWeb,
                goToGame = goToGame,
                onDismiss = { onDismissDealDetails() },
                onShare = { sheetData -> onShareDealDetails(sheetData) },
                onToggleWaitlist = { sheetData -> onToggleDealWaitlist(sheetData) },
                onToggleIgnore = { sheetData -> onToggleDealIgnore(sheetData) },
                onRetryDealDetails = { dealDetails?.let { onLoadDealDetails(it.dealId, it.store.storeID, it.gameId, it.gameName, it.gameSalesPriceDenominated, it.dealUrl) } },
            )

            DealsFilterSheet(
                show = showFilters,
                onDismiss = { onShowFiltersChange(false) },
                selectedSort = data.sort,
                onSelectSort = onSelectSort,
                stores = stores,
                selectedShops = selectedShops,
                onToggleShop = onToggleShop,
                onClearShops = onClearShops,
                filter = filter,
                // The currency symbol/affix for price-bucket labels is read from a loaded deal's
                // pre-formatted price (ITAD denominates per region); null until the first page loads.
                currencySample = visibleDeals.firstOrNull()?.salePriceDenominated,
                onSetMinCut = onSetMinCut,
                onSetMaxPrice = onSetMaxPrice,
                onToggleType = onToggleType,
                onSetDrmFree = onSetDrmFree,
                onSetFlag = onSetFlag,
                onSetMinSteam = onSetMinSteam,
                onSetRelease = onSetRelease,
                onClearFilters = onClearFilters,
            )
        }
    }
}

@Composable
private fun SearchResultsBody(
    state: SearchResultsState,
    ignoredIds: ImmutableSet<String>,
    waitlistIds: ImmutableSet<String>,
    storesById: Map<Int, Store>,
    addToWaitlistCd: String,
    removeFromWaitlistCd: String,
    errorMessage: String,
    onGame: (gameId: String) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit,
) {
    when (state) {
        SearchResultsState.Idle -> Unit

        SearchResultsState.Loading -> {
            val loadingCd = stringResource(Res.string.deals_search_loading_indicator)
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                    .semantics { contentDescription = loadingCd }
            )
        }

        SearchResultsState.NoResults -> Text(
            text = stringResource(Res.string.deals_search_no_results_label),
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
        )

        SearchResultsState.Error -> Text(
            text = errorMessage,
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
        )

        is SearchResultsState.Results -> {
            // Hide ignored games from the results (#280); the id set is Room-backed (cold-start/offline correct).
            val visible = remember(state.results, ignoredIds) { state.results.filter { it.gameID !in ignoredIds } }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = GameDealsCustomTheme.spacing.small),
            ) {
                items(items = visible, key = { it.gameID }) { group ->
                    SearchResultListItem(
                        deal = group.cheapestDeal,
                        dealCount = group.totalDealCount,
                        isWaitlisted = group.gameID in waitlistIds,
                        store = storesById[group.cheapestDeal.storeID],
                        addToWaitlistCd = addToWaitlistCd,
                        removeFromWaitlistCd = removeFromWaitlistCd,
                        onGame = { onGame(group.gameID) },
                        onToggleWaitlist = { onToggleWaitlist(group.gameID) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultListItem(
    deal: Deal,
    dealCount: Int,
    isWaitlisted: Boolean,
    store: Store?,
    addToWaitlistCd: String,
    removeFromWaitlistCd: String,
    onGame: () -> Unit,
    onToggleWaitlist: () -> Unit,
) {
    val showBadge = dealCount > 1
    // The row's spoken description names the title + cheapest price only; the separate "N deals" badge
    // node carries the count, so we keep it out of the row CD to avoid TalkBack announcing it twice (#257).
    val rowCd = if (isWaitlisted) {
        stringResource(Res.string.deals_search_result_row_description_waitlisted, deal.title, deal.salePriceDenominated)
    } else {
        stringResource(Res.string.deals_search_result_row_description, deal.title, deal.salePriceDenominated)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DealListRow(
            modifier = Modifier.weight(1f),
            title = deal.title,
            contentDescription = rowCd,
            onClick = onGame,
            imageUrl = deal.thumb,
            salePrice = deal.salePriceDenominated,
            regularPrice = deal.normalPriceDenominated,
            discountPercent = deal.savings.roundToInt(),
            hasVoucher = deal.hasVoucher,
            isNewHistoricalLow = deal.isNewHistoricalLow,
            isStoreLow = deal.isStoreLow,
            storeName = store?.storeName,
            storeIconUrl = store?.iconUrl,
        )
        if (showBadge) {
            DealCountBadge(count = dealCount)
        }
        WaitlistHeartButton(
            isWaitlisted = isWaitlisted,
            onToggle = onToggleWaitlist,
            addToWaitlistContentDescription = addToWaitlistCd,
            removeFromWaitlistContentDescription = removeFromWaitlistCd,
        )
    }
}

@Composable
private fun DealCountBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(GameDealsCustomTheme.spacing.small),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = stringResource(Res.string.deals_search_result_count, count),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = GameDealsCustomTheme.spacing.small,
                vertical = GameDealsCustomTheme.spacing.extraSmall,
            ),
        )
    }
}

@Composable
private fun FilterBar(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onClick) {
            Icon(Icons.Filled.FilterList, contentDescription = null)
            Text(
                modifier = Modifier.padding(start = GameDealsCustomTheme.spacing.small),
                text = if (activeCount > 0) stringResource(Res.string.deals_filter_button_count, activeCount)
                else stringResource(Res.string.deals_filter_button),
            )
        }
    }
}

@Composable
private fun DealsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Focus the field as soon as it is revealed so the user can type immediately.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TextField(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small)
            .focusRequester(focusRequester),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        maxLines = 1,
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.deals_search_icon))
        },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.deals_search_close))
            }
        },
        label = { Text(stringResource(Res.string.deals_search_field_label)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions {
            keyboardController?.hide()
            focusManager.clearFocus()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DealsFilterSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    selectedSort: DealsSort,
    onSelectSort: (DealsSort) -> Unit,
    stores: ImmutableList<Store>,
    selectedShops: ImmutableSet<Int>,
    onToggleShop: (Int) -> Unit,
    onClearShops: () -> Unit,
    filter: DealsFilter,
    currencySample: String?,
    onSetMinCut: (Int?) -> Unit,
    onSetMaxPrice: (Double?) -> Unit,
    onToggleType: (ProductType) -> Unit,
    onSetDrmFree: (Boolean) -> Unit,
    onSetFlag: (DealFlag?) -> Unit,
    onSetMinSteam: (Int?) -> Unit,
    onSetRelease: (ReleaseWindow?) -> Unit,
    onClearFilters: () -> Unit,
) {
    if (!show) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val drmFreeCd = stringResource(Res.string.deals_filter_drm_free_switch_description)
    val anyLabel = stringResource(Res.string.deals_filter_chip_any)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GameDealsCustomTheme.spacing.large)
                .padding(bottom = GameDealsCustomTheme.spacing.large)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            // Title + "Reset all" (clears the server-side filters only; sort/shops/mature are untouched).
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.deals_filter_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (!filter.isEmpty()) {
                    TextButton(onClick = onClearFilters) {
                        Text(stringResource(Res.string.deals_filter_reset))
                    }
                }
            }

            // Sort (single-select)
            FilterSectionTitle(stringResource(Res.string.deals_filter_section_sort))
            FilterChipRow {
                DealsSort.entries.forEach { sort ->
                    FilterChip(
                        selected = sort == selectedSort,
                        onClick = { onSelectSort(sort) },
                        label = { Text(stringResource(sort.labelRes())) },
                    )
                }
            }

            HorizontalDivider()

            // Stores (multi-select; empty == all)
            FilterSectionTitle(stringResource(Res.string.deals_filter_section_stores))
            FilterChipRow {
                FilterChip(
                    selected = selectedShops.isEmpty(),
                    onClick = onClearShops,
                    label = { Text(stringResource(Res.string.deals_filter_all_stores)) },
                )
                stores.forEach { store ->
                    FilterChip(
                        selected = store.storeID in selectedShops,
                        onClick = { onToggleShop(store.storeID) },
                        label = { Text(store.storeName) },
                    )
                }
            }

            HorizontalDivider()

            // Minimum discount (single-select; "Any" clears)
            FilterSectionTitle(stringResource(Res.string.deals_filter_section_discount))
            FilterChipRow {
                FilterChip(selected = filter.minCutPercent == null, onClick = { onSetMinCut(null) }, label = { Text(anyLabel) })
                CUT_TIERS.forEach { tier ->
                    FilterChip(
                        selected = filter.minCutPercent == tier,
                        onClick = { onSetMinCut(tier) },
                        label = { Text(stringResource(Res.string.deals_filter_discount_tier, tier)) },
                    )
                }
            }

            HorizontalDivider()

            // Maximum price (single-select; "Any" clears, "Free" == max 0)
            FilterSectionTitle(stringResource(Res.string.deals_filter_section_price))
            FilterChipRow {
                FilterChip(selected = filter.maxPrice == null, onClick = { onSetMaxPrice(null) }, label = { Text(anyLabel) })
                FilterChip(
                    selected = filter.maxPrice == 0.0,
                    onClick = { onSetMaxPrice(0.0) },
                    label = { Text(stringResource(Res.string.deals_filter_price_free)) },
                )
                PRICE_TIERS.forEach { tier ->
                    FilterChip(
                        selected = filter.maxPrice == tier.toDouble(),
                        onClick = { onSetMaxPrice(tier.toDouble()) },
                        label = { Text(stringResource(Res.string.deals_filter_price_under, priceLabel(tier, currencySample))) },
                    )
                }
            }

            HorizontalDivider()

            // Type (multi-select; empty == all)
            FilterSectionTitle(stringResource(Res.string.deals_filter_section_type))
            FilterChipRow {
                ProductType.entries.forEach { type ->
                    FilterChip(
                        selected = type in filter.types,
                        onClick = { onToggleType(type) },
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }

            HorizontalDivider()

            // Deal type / flag (single-select)
            FilterSectionTitle(stringResource(Res.string.deals_filter_section_flag))
            FilterChipRow {
                FilterChip(selected = filter.flag == null, onClick = { onSetFlag(null) }, label = { Text(anyLabel) })
                DealFlag.entries.forEach { flag ->
                    FilterChip(
                        selected = filter.flag == flag,
                        onClick = { onSetFlag(flag) },
                        label = { Text(stringResource(flag.labelRes())) },
                    )
                }
            }

            HorizontalDivider()

            // Minimum Steam rating (single-select)
            FilterSectionTitle(stringResource(Res.string.deals_filter_section_reviews))
            FilterChipRow {
                FilterChip(selected = filter.minSteamPercent == null, onClick = { onSetMinSteam(null) }, label = { Text(anyLabel) })
                STEAM_TIERS.forEach { tier ->
                    FilterChip(
                        selected = filter.minSteamPercent == tier,
                        onClick = { onSetMinSteam(tier) },
                        label = { Text(stringResource(Res.string.deals_filter_reviews_tier, tier)) },
                    )
                }
            }

            HorizontalDivider()

            // Release recency (single-select)
            FilterSectionTitle(stringResource(Res.string.deals_filter_section_release))
            FilterChipRow {
                FilterChip(selected = filter.release == null, onClick = { onSetRelease(null) }, label = { Text(anyLabel) })
                ReleaseWindow.entries.forEach { window ->
                    FilterChip(
                        selected = filter.release == window,
                        onClick = { onSetRelease(window) },
                        label = { Text(stringResource(window.labelRes())) },
                    )
                }
            }

            HorizontalDivider()

            // DRM-free toggle
            FilterSectionTitle(stringResource(Res.string.deals_filter_section_drm))
            FilterToggleRow(
                label = stringResource(Res.string.deals_filter_drm_free_label),
                description = drmFreeCd,
                checked = filter.drmFree,
                onCheckedChange = onSetDrmFree,
            )
        }
    }
}

@Composable
private fun FilterSectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        content = content,
    )
}

@Composable
private fun FilterToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(
            modifier = Modifier.semantics { contentDescription = description },
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

// Formats a price-bucket threshold with the region currency affix derived from a sample denominated
// price ("$7.49" -> "$5"; "7.49 PLN" -> "5 PLN"); falls back to the bare number before any deal loads.
private fun priceLabel(threshold: Int, sample: String?): String {
    if (sample.isNullOrBlank()) return threshold.toString()
    val prefix = sample.takeWhile { !it.isDigit() }
    val suffix = sample.takeLastWhile { !it.isDigit() }
    return "$prefix$threshold$suffix"
}

private fun DealsSort.labelRes(): StringResource = when (this) {
    DealsSort.Trending -> Res.string.deals_sort_trending
    DealsSort.TopDiscount -> Res.string.deals_sort_top_discount
    DealsSort.RecentlyAdded -> Res.string.deals_sort_recently_added
    DealsSort.PriceLowToHigh -> Res.string.deals_sort_price_low_high
    DealsSort.ExpiringSoon -> Res.string.deals_sort_expiring_soon
}

private fun ProductType.labelRes(): StringResource = when (this) {
    ProductType.Game -> Res.string.deals_filter_type_game
    ProductType.Dlc -> Res.string.deals_filter_type_dlc
    ProductType.Bundle -> Res.string.deals_filter_type_bundle
}

private fun DealFlag.labelRes(): StringResource = when (this) {
    DealFlag.NewLow -> Res.string.deals_filter_flag_new_low
    DealFlag.HistoricalLow -> Res.string.deals_filter_flag_historical_low
    DealFlag.ShopLow -> Res.string.deals_filter_flag_shop_low
}

private fun ReleaseWindow.labelRes(): StringResource = when (this) {
    ReleaseWindow.NewLast90 -> Res.string.deals_filter_release_new
    ReleaseWindow.ThisYear -> Res.string.deals_filter_release_this_year
    ReleaseWindow.TwoPlusYears -> Res.string.deals_filter_release_older
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
