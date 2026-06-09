@file:OptIn(ExperimentalSerializationApi::class)
@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.search.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.components.WaitlistHeartButton
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.search.generated.resources.Res
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filter_exact_match_label
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filter_price_range_label
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filter_steam_range_label
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filters_exact_match_switch_description
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filters_icon
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filters_price_range_slider_description
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_filters_rating_range_slider_description
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_list_empty_state_label
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_list_item_deal_count
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_list_item_row_description
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_list_item_row_description_favourite
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_list_no_results_state_label
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_loading_indicator
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_search_field_label
import pm.bam.gamedeals.feature.search.generated.resources.search_screen_search_icon
import kotlin.math.roundToInt
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_waitlist_sign_in_required

@Composable
internal fun SearchScreen(
    onSearchedGame: ((gameId: String) -> Unit) = {},
    searchViewModel: SearchViewModel = koinViewModel()
) {
    val data = searchViewModel.resultState.collectAsStateWithLifecycle()
    val favouriteIds = searchViewModel.waitlistIds.collectAsStateWithLifecycle()
    val stores = searchViewModel.stores.collectAsStateWithLifecycle()

    val initialTitle = searchViewModel.initialQuery

    var showFilters by rememberSaveable { mutableStateOf(false) }
    var existingParameters by rememberSaveable(stateSaver = parametersSaver) {
        mutableStateOf(SearchParameters(title = initialTitle))
    }

    val onSearch: () -> Unit = {
        searchViewModel.searchGames(
            title = existingParameters.title,
            lowerPrice = existingParameters.lowerPrice,
            upperPrice = existingParameters.upperPrice,
            steamMinimum = existingParameters.steamMinRating,
            exactMatch = existingParameters.exact
        )
    }

    SearchScreenContent(
        showFilters = showFilters,
        onShowFiltersChanged = { newShowFilters ->
            showFilters = newShowFilters
            if (!newShowFilters) {
                onSearch()
            }
        },
        existingSearchParameters = existingParameters,
        initialTitle = initialTitle,
        searchData = data.value,
        favouriteIds = favouriteIds.value,
        stores = stores.value,
        events = searchViewModel.events,
        onSearchTitleChanged = {
            existingParameters = existingParameters.copy(title = it)
            onSearch()
        },
        onSearchedGame = onSearchedGame,
        onToggleWaitlist = { gameId -> searchViewModel.toggleWaitlist(gameId) },
        onPriceChanged = { from, to -> existingParameters = existingParameters.copy(lowerPrice = from, upperPrice = to) },
        onSteamMinChanged = { min -> existingParameters = existingParameters.copy(steamMinRating = min) },
        onExactMatch = { exactMatch -> existingParameters = existingParameters.copy(exact = exactMatch) },
        onRetry = { onSearch() }
    )
}


@Composable
private fun SearchScreenContent(
    showFilters: Boolean,
    onShowFiltersChanged: (showFilters: Boolean) -> Unit,
    existingSearchParameters: SearchParameters,
    initialTitle: String? = null,
    searchData: SearchViewModel.SearchData,
    favouriteIds: ImmutableSet<String>,
    stores: ImmutableMap<Int, Store> = persistentMapOf(),
    events: Flow<SearchViewModel.SearchUiEvent> = emptyFlow(),
    onSearchTitleChanged: (text: String) -> Unit,
    onSearchedGame: (gameId: String) -> Unit = {},
    onToggleWaitlist: (gameId: String) -> Unit = {},
    onPriceChanged: (from: Int?, to: Int?) -> Unit,
    onSteamMinChanged: (min: Int) -> Unit,
    onExactMatch: (exactMatch: Boolean) -> Unit,
    onRetry: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnRetry by rememberUpdatedState(onRetry)

    val errorMessage = stringResource(Res.string.search_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.search_screen_data_loading_error_retry)
    val signInRequired = stringResource(CommonRes.string.deal_waitlist_sign_in_required)

    // One-shot waitlist sign-in prompt, mirroring Home/Deals/Store.
    SingleEventEffect(events) { event ->
        when (event) {
            SearchViewModel.SearchUiEvent.SignInRequired -> snackbarHostState.showSnackbar(signInRequired)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                SearchField(
                    initialTitle = initialTitle,
                    onSearchTitleChanged = { onSearchTitleChanged(it) },
                    onShowFilters = { onShowFiltersChanged(!showFilters) },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        )
        { innerPadding: PaddingValues ->
            when (searchData) {
                SearchViewModel.SearchData.Error -> Unit

                SearchViewModel.SearchData.Empty -> Text(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center),
                    text = stringResource(Res.string.search_screen_list_empty_state_label)
                )

                SearchViewModel.SearchData.Loading -> {
                    val loadingCd = stringResource(Res.string.search_screen_loading_indicator)
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .semantics { contentDescription = loadingCd }
                    )
                }

                SearchViewModel.SearchData.NoResults -> Text(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center),
                    text = stringResource(Res.string.search_screen_list_no_results_state_label)
                )

                is SearchViewModel.SearchData.SearchResults -> {
                    LazyColumn(
                        modifier = Modifier.padding(innerPadding),
                        contentPadding = PaddingValues(vertical = GameDealsCustomTheme.spacing.small),
                        content = {
                            items(
                                key = { index -> searchData.searchResults[index].gameID },
                                count = searchData.searchResults.size
                            ) {
                                val group = searchData.searchResults[it]
                                SearchResultListItem(
                                    deal = group.cheapestDeal,
                                    dealCount = group.totalDealCount,
                                    isFavourite = group.gameID in favouriteIds,
                                    store = stores[group.cheapestDeal.storeID],
                                    onGame = { onSearchedGame(group.gameID) },
                                    onToggleWaitlist = { onToggleWaitlist(group.gameID) },
                                )
                            }
                        }
                    )
                }
            }

            SearchFilters(
                showFilters = showFilters,
                onDismiss = { onShowFiltersChanged(false) },
                existingSearchParameters = existingSearchParameters,
                onPriceChanged = onPriceChanged,
                onSteamMinChanged = onSteamMinChanged,
                onExactMatch = onExactMatch
            )
        }
    }

    if (searchData is SearchViewModel.SearchData.Error) {
        LaunchedEffect(snackbarHostState) {
            val results = snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = errorRetry
            )
            if (results == SnackbarResult.ActionPerformed) {
                currentOnRetry()
            }
        }
    }
}


@Composable
private fun SearchResultListItem(
    deal: Deal,
    dealCount: Int,
    isFavourite: Boolean,
    store: Store?,
    onGame: () -> Unit,
    onToggleWaitlist: () -> Unit,
) {
    val showBadge = dealCount > 1
    // The row's spoken description names the title + cheapest price only; the separate "N deals"
    // badge node carries the count, so we deliberately keep it out of the row CD to avoid TalkBack
    // announcing the count twice (UI Improvements #257).
    val rowCd = if (isFavourite) {
        stringResource(Res.string.search_screen_list_item_row_description_favourite, deal.title, deal.salePriceDenominated)
    } else {
        stringResource(Res.string.search_screen_list_item_row_description, deal.title, deal.salePriceDenominated)
    }
    val addToWaitlistCd = stringResource(CommonRes.string.deal_favourite_add_action)
    val removeFromWaitlistCd = stringResource(CommonRes.string.deal_favourite_remove_action)

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
            isLowestEver = deal.isLowestEver,
            storeName = store?.storeName,
            storeIconUrl = store?.iconUrl,
        )
        if (showBadge) {
            DealCountBadge(count = dealCount)
        }
        WaitlistHeartButton(
            isWaitlisted = isFavourite,
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
            text = stringResource(Res.string.search_screen_list_item_deal_count, count),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = GameDealsCustomTheme.spacing.small,
                vertical = GameDealsCustomTheme.spacing.extraSmall,
            ),
        )
    }
}


@Composable
private fun SearchField(
    initialTitle: String? = null,
    onSearchTitleChanged: (text: String) -> Unit,
    onShowFilters: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    val keyboardController = LocalSoftwareKeyboardController.current
    var title by rememberSaveable { mutableStateOf(initialTitle.orEmpty()) }
    val searchAction: () -> Unit = {
        onSearchTitleChanged(title)
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                    searchAction()
                    true
                } else {
                    false
                }
            },
        value = title,
        onValueChange = { title = it },
        singleLine = true,
        maxLines = 1,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(Res.string.search_screen_search_icon)
            )
        },
        trailingIcon = {
            IconButton(onClick = { onShowFilters() }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(Res.string.search_screen_filters_icon),
                )
            }
        },
        label = { Text(text = stringResource(Res.string.search_screen_search_field_label)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions { searchAction() }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilters(
    showFilters: Boolean,
    onDismiss: () -> Unit,
    existingSearchParameters: SearchParameters,
    onPriceChanged: (from: Int?, to: Int?) -> Unit,
    onSteamMinChanged: (min: Int) -> Unit,
    onExactMatch: (exactMatch: Boolean) -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = modalBottomSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Filters(existingSearchParameters, onPriceChanged, onSteamMinChanged, onExactMatch)
        }
    }
}

@Composable
private fun Filters(
    existingSearchParameters: SearchParameters,
    onPriceChanged: (from: Int?, to: Int?) -> Unit,
    onSteamMinChanged: (min: Int) -> Unit,
    onExactMatch: (exactMatch: Boolean) -> Unit
) {
    val priceLowest = SearchFilterMinPrice
    val priceHighest = SearchFilterMaxPrice
    val priceRange = priceLowest..priceHighest

    val existingLowest = existingSearchParameters.lowerPrice.takeIf { it != null }?.toFloat() ?: priceLowest
    val existingHighest = existingSearchParameters.upperPrice.takeIf { it != null }?.toFloat() ?: priceHighest
    val existingPriceRange = existingLowest..existingHighest

    val steamRange = SearchFilterMinRate..SearchFilterMaxRate
    val existingMin = existingSearchParameters.steamMinRating?.toFloat() ?: SearchFilterMinRate

    var priceSliderValue by rememberSaveable(existingPriceRange, stateSaver = floatRangeSaver) { mutableStateOf(existingPriceRange) }
    var steamSliderValue by rememberSaveable(existingMin) { mutableFloatStateOf(existingMin) }
    var exactMatch by rememberSaveable(existingSearchParameters.exact) { mutableStateOf(existingSearchParameters.exact ?: false) }

    val priceSliderCd = stringResource(Res.string.search_screen_filters_price_range_slider_description)
    val ratingSliderCd = stringResource(Res.string.search_screen_filters_rating_range_slider_description)
    val exactMatchSwitchCd = stringResource(Res.string.search_screen_filters_exact_match_switch_description)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(GameDealsCustomTheme.spacing.large)
            .navigationBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(Res.string.search_screen_filter_price_range_label), Modifier.weight(1f))
            Text(text = rangeString(priceSliderValue.start, priceSliderValue.endInclusive, priceHighest))
        }
        val priceSliderState = rangeString(priceSliderValue.start, priceSliderValue.endInclusive, priceHighest)
        RangeSlider(
            modifier = Modifier.semantics {
                contentDescription = priceSliderCd
                stateDescription = priceSliderState
            },
            value = priceSliderValue,
            steps = SearchFilterPriceSteps,
            onValueChange = { range -> priceSliderValue = range },
            valueRange = priceRange,
            onValueChangeFinished = {
                when (priceSliderValue.start == priceSliderValue.endInclusive) {
                    true -> onPriceChanged(null, null)
                    false -> onPriceChanged(priceSliderValue.start.roundToInt(), priceSliderValue.endInclusive.roundToInt())
                }
            },
        )
        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GameDealsCustomTheme.spacing.large)
        ) {
            Text(text = stringResource(Res.string.search_screen_filter_steam_range_label), modifier = Modifier.weight(1f))
            Text(text = valueString(steamSliderValue, SearchFilterMaxRate))
        }
        val ratingSliderState = valueString(steamSliderValue, SearchFilterMaxRate)
        Slider(
            modifier = Modifier.semantics {
                contentDescription = ratingSliderCd
                stateDescription = ratingSliderState
            },
            value = steamSliderValue,
            steps = SearchFilterRateSteps,
            onValueChange = { range -> steamSliderValue = range },
            valueRange = steamRange,
            onValueChangeFinished = { onSteamMinChanged(steamSliderValue.roundToInt()) },
        )
        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(top = GameDealsCustomTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(Res.string.search_screen_filter_exact_match_label), Modifier.weight(1f))
            Switch(
                modifier = Modifier.semantics { contentDescription = exactMatchSwitchCd },
                checked = exactMatch,
                onCheckedChange = {
                    exactMatch = it
                    onExactMatch(it)
                }
            )
        }
    }
}


@Suppress("SameParameterValue")
internal fun rangeString(startValue: Float, endInclusiveValue: Float, highestValue: Float): String =
    "${startValue.roundToInt()} - ${endInclusiveValue.roundToInt()}"
        .plus(if (endInclusiveValue == highestValue) "+" else "")

@Suppress("SameParameterValue")
internal fun valueString(value: Float, highestValue: Float): String =
    "${value.toInt()}".plus(if (value == highestValue) "+" else "")


/** Saving mechanism for [SearchParameters] into [rememberSaveable]. */
private val parametersSaver = run {
    mapSaver(
        save = { it.asMap() },
        restore = { SearchParameters.from(it) }
    )
}

/** Saving mechanism for [ClosedFloatingPointRange<Float>] into [rememberSaveable]. */
private val floatRangeSaver = listSaver<ClosedFloatingPointRange<Float>, Any>(
    save = { listOf(it.start, it.endInclusive) },
    restore = { (it[0] as Float)..(it[1] as Float) }
)


internal const val SearchFilterMinPrice = 0f
internal const val SearchFilterMaxPrice = 50f
internal const val SearchFilterPriceSteps = SearchFilterMaxPrice.toInt()

internal const val SearchFilterMinRate = 40f
internal const val SearchFilterMaxRate = 95f
internal const val SearchFilterRateSteps = 10


private val previewSearchResults = persistentListOf(
    GroupedSearchResult(gameID = PreviewDeal.gameID, cheapestDeal = PreviewDeal, totalDealCount = 5),
    GroupedSearchResult(
        gameID = "222",
        cheapestDeal = PreviewDeal.copy(dealID = "deal-2", title = "Hollow Knight", salePriceDenominated = "$7.49", gameID = "222"),
        totalDealCount = 1,
    ),
    GroupedSearchResult(
        gameID = "333",
        cheapestDeal = PreviewDeal.copy(dealID = "deal-3", title = "Stardew Valley", salePriceDenominated = "$8.99", gameID = "333"),
        totalDealCount = 3,
    ),
)

@Preview
@Composable
private fun SearchScreenContent_Results_Preview() {
    GameDealsTheme {
        SearchScreenContent(
            showFilters = false,
            onShowFiltersChanged = {},
            existingSearchParameters = SearchParameters(),
            searchData = SearchViewModel.SearchData.SearchResults(previewSearchResults),
            favouriteIds = persistentSetOf("222"),
            stores = persistentMapOf(PreviewStore.storeID to PreviewStore),
            onSearchTitleChanged = {},
            onSearchedGame = {},
            onPriceChanged = { _, _ -> },
            onSteamMinChanged = {},
            onExactMatch = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun SearchScreenContent_Results_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        SearchScreenContent(
            showFilters = false,
            onShowFiltersChanged = {},
            existingSearchParameters = SearchParameters(),
            searchData = SearchViewModel.SearchData.SearchResults(previewSearchResults),
            favouriteIds = persistentSetOf("222"),
            stores = persistentMapOf(PreviewStore.storeID to PreviewStore),
            onSearchTitleChanged = {},
            onSearchedGame = {},
            onPriceChanged = { _, _ -> },
            onSteamMinChanged = {},
            onExactMatch = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun SearchScreenContent_Empty_Preview() {
    GameDealsTheme {
        SearchScreenContent(
            showFilters = false,
            onShowFiltersChanged = {},
            existingSearchParameters = SearchParameters(),
            searchData = SearchViewModel.SearchData.Empty,
            favouriteIds = persistentSetOf(),
            onSearchTitleChanged = {},
            onSearchedGame = {},
            onPriceChanged = { _, _ -> },
            onSteamMinChanged = {},
            onExactMatch = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun SearchScreenContent_NoResults_Preview() {
    GameDealsTheme {
        SearchScreenContent(
            showFilters = false,
            onShowFiltersChanged = {},
            existingSearchParameters = SearchParameters(),
            searchData = SearchViewModel.SearchData.NoResults,
            favouriteIds = persistentSetOf(),
            onSearchTitleChanged = {},
            onSearchedGame = {},
            onPriceChanged = { _, _ -> },
            onSteamMinChanged = {},
            onExactMatch = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun SearchFilters_Preview() {
    // Preview the Filters body directly; ModalBottomSheet does not render reliably in static previews.
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Filters(
                existingSearchParameters = SearchParameters(
                    lowerPrice = 5,
                    upperPrice = 30,
                    steamMinRating = 80,
                    exact = false,
                ),
                onPriceChanged = { _, _ -> },
                onSteamMinChanged = {},
                onExactMatch = {},
            )
        }
    }
}
