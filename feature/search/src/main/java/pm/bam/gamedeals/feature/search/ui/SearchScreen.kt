@file:OptIn(ExperimentalSerializationApi::class)

package pm.bam.gamedeals.feature.search.ui

import android.view.KeyEvent
import androidx.annotation.OpenForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.common.ui.PhoneLandscape
import pm.bam.gamedeals.common.ui.PhonePortrait
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.feature.search.R
import kotlin.math.roundToInt

@Composable
internal fun SearchScreen(
    onSearchedGame: ((gameId: Int) -> Unit) = {},
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val data = searchViewModel.resultState.collectAsStateWithLifecycle()

    var showFilters by rememberSaveable { mutableStateOf(false) }
    var existingParameters by rememberSaveable(stateSaver = parametersSaver) { mutableStateOf(SearchParameters()) }

    val onSearch: () -> Unit = {
        searchViewModel.searchGames(
            title = existingParameters.title,
            lowerPrice = existingParameters.lowerPrice,
            upperPrice = existingParameters.upperPrice,
            steamMinimum = existingParameters.steamMinRating,
            exactMatch = existingParameters.exact
        )
    }

    Screen(
        showFilters = showFilters,
        onShowFiltersChanged = { newShowFilters ->
            showFilters = newShowFilters
            if (!newShowFilters) {
                onSearch()
            }
        },
        existingSearchParameters = existingParameters,
        searchData = data.value,
        onSearchTitleChanged = {
            existingParameters = existingParameters.copy(title = it)
            onSearch()
        },
        onSearchedGame = onSearchedGame,
        onPriceChanged = { from, to -> existingParameters = existingParameters.copy(lowerPrice = from, upperPrice = to) },
        onSteamMinChanged = { min -> existingParameters = existingParameters.copy(steamMinRating = min) },
        onExactMatch = { exactMatch -> existingParameters = existingParameters.copy(exact = exactMatch) },
        onRetry = { onSearch() }
    )
}


@OptIn(ExperimentalSerializationApi::class)
@Composable
private fun Screen(
    showFilters: Boolean,
    onShowFiltersChanged: (showFilters: Boolean) -> Unit,
    existingSearchParameters: SearchParameters,
    searchData: SearchViewModel.SearchData,
    onSearchTitleChanged: (text: String) -> Unit,
    onSearchedGame: (gameId: Int) -> Unit = {},
    onPriceChanged: (from: Int?, to: Int?) -> Unit,
    onSteamMinChanged: (min: Int) -> Unit,
    onExactMatch: (exactMatch: Boolean) -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnRetry by rememberUpdatedState(onRetry)

    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = { SearchField(onSearchTitleChanged = { onSearchTitleChanged(it) }, onShowFilters = { onShowFiltersChanged(!showFilters) }) },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
            )
            { innerPadding: PaddingValues ->
                when (searchData) {
                    SearchViewModel.SearchData.Error -> Unit

                    SearchViewModel.SearchData.Empty -> Text(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center),
                        text = stringResource(R.string.search_screen_list_empty_state_label)
                    )

                    SearchViewModel.SearchData.Loading -> CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .testTag(SearchLoadingTag)
                    )

                    SearchViewModel.SearchData.NoResults -> Text(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .testTag(SearchEmptyTag),
                        text = stringResource(R.string.search_screen_list_no_results_state_label)
                    )

                    is SearchViewModel.SearchData.SearchResults -> {
                        LazyColumn(
                            modifier = Modifier.padding(innerPadding),
                            content = {
                                items(
                                    key = { index -> searchData.searchResults[index].dealID },
                                    count = searchData.searchResults.size
                                ) {
                                    SearchResultListItem(searchData.searchResults[it]) { onSearchedGame(searchData.searchResults[it].gameID) }
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
    }

    if(searchData is SearchViewModel.SearchData.Error) {
        LaunchedEffect(snackbarHostState) {
            val results = snackbarHostState.showSnackbar(
                message = context.getString(R.string.search_screen_data_loading_error_msg),
                actionLabel = context.getString(R.string.search_screen_data_loading_error_retry)
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
    onGame: () -> Unit
) {
    ListItem(
        headlineContent = { Text(deal.title) },
        supportingContent = { Text(stringResource(R.string.search_screen_list_item_label, deal.salePriceDenominated)) },
        leadingContent = {
            AsyncImage(
                model = deal.thumb,
                contentDescription = stringResource(R.string.search_screen_game_image, deal.title),
                error = painterResource(id = pm.bam.gamedeals.common.ui.R.drawable.videogame_thumb),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(60.dp)
                    .width(100.dp)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
            )
        },
        modifier = Modifier
            .clickable { onGame() }
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small)
            .testTag(SearchResultsListItemTag)
    )
    HorizontalDivider(color = Color.Black)
}


@Composable
private fun SearchField(
    onSearchTitleChanged: (text: String) -> Unit,
    onShowFilters: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    val keyboardController = LocalSoftwareKeyboardController.current
    var title by rememberSaveable { mutableStateOf("") }
    val searchAction: () -> Unit? = {
        onSearchTitleChanged(title)
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .onKeyEvent {
                return@onKeyEvent when (it.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_ENTER -> {
                        searchAction()
                        true
                    }

                    else -> false
                }
            }
            .testTag(SearchFieldTag),
        value = title,
        onValueChange = { title = it },
        maxLines = 1,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search_screen_search_icon)
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Settings,
                stringResource(R.string.search_screen_filters_icon),
                modifier = Modifier
                    .clickable { onShowFilters() }
                    .testTag(SearchFiltersIconTag)
            )
        },
        label = { Text(text = stringResource(R.string.search_screen_search_field_label)) },
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

@OptIn(ExperimentalSerializationApi::class)
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

    var priceSliderValue by rememberSaveable(stateSaver = floatRangeSaver) { mutableStateOf(existingPriceRange) }
    var steamSliderValue by rememberSaveable { mutableFloatStateOf(existingMin) }
    var exactMatch by rememberSaveable { mutableStateOf(existingSearchParameters.exact ?: false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(GameDealsCustomTheme.spacing.large)
            .navigationBarsPadding()
            .testTag(SearchFiltersTag)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.search_screen_filter_price_range_label), Modifier.weight(1f))
            Text(modifier = Modifier.testTag(SearchFiltersPriceRangeLabelTag), text = rangeString(priceSliderValue.start, priceSliderValue.endInclusive, priceHighest))
        }
        RangeSlider(
            modifier = Modifier.testTag(SearchFiltersPriceRangeTag),
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
            Text(text = stringResource(R.string.search_screen_filter_steam_range_label), modifier = Modifier.weight(1f))
            Text(modifier = Modifier.testTag(SearchFiltersRatingRangeLabelTag), text = valueString(steamSliderValue, SearchFilterMaxRate))
        }
        Slider(
            modifier = Modifier.testTag(SearchFiltersRatingRangeTag),
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
            Text(text = stringResource(R.string.search_screen_filter_exact_match_label), Modifier.weight(1f))
            Switch(
                modifier = Modifier.testTag(SearchFiltersExactMatchSwitchTag),
                checked = exactMatch,
                onCheckedChange = {
                    exactMatch = it
                    onExactMatch(it)
                }
            )
        }
    }
}


@OpenForTesting
@Suppress("SameParameterValue")
internal fun rangeString(startValue: Float, endInclusiveValue: Float, highestValue: Float): String =
    "${startValue.roundToInt()} - ${endInclusiveValue.roundToInt()}"
        .plus(if (endInclusiveValue == highestValue) "+" else "")

@OpenForTesting
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


@Preview
@Composable
private fun FiltersPreview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Filters(
                existingSearchParameters = SearchParameters(),
                onPriceChanged = { _, _ -> },
                onSteamMinChanged = {},
                onExactMatch = {}
            )
        }
    }
}

@PhonePortrait
@PhoneLandscape
@Composable
private fun SearchScreenPreview() {
    Screen(
        showFilters = false,
        onShowFiltersChanged = {},
        existingSearchParameters = SearchParameters(),
        searchData = SearchViewModel.SearchData.SearchResults(
            searchResults = List(15) { PreviewDeal.copy(dealID = "$it") }.toImmutableList()
        ),
        onSearchTitleChanged = {},
        onSearchedGame = {},
        onPriceChanged = { _, _ -> },
        onSteamMinChanged = {},
        onExactMatch = {},
        onRetry = {}
    )
}

@PhonePortrait
@Composable
private fun SearchScreenNoResultsPreview() {
    Screen(
        showFilters = false,
        onShowFiltersChanged = {},
        existingSearchParameters = SearchParameters(),
        searchData = SearchViewModel.SearchData.NoResults,
        onSearchTitleChanged = {},
        onSearchedGame = {},
        onPriceChanged = { _, _ -> },
        onSteamMinChanged = {},
        onExactMatch = {},
        onRetry = {}
    )
}

internal const val SearchFilterMinPrice = 0f
internal const val SearchFilterMaxPrice = 50f
internal const val SearchFilterPriceSteps = SearchFilterMaxPrice.toInt()

internal const val SearchFilterMinRate = 40f
internal const val SearchFilterMaxRate = 95f
internal const val SearchFilterRateSteps = 10


internal const val SearchFieldTag = "SearchFieldTag"
internal const val SearchFiltersIconTag = "SearchFiltersIconTag"
internal const val SearchLoadingTag = "SearchLoadingTag"
internal const val SearchEmptyTag = "SearchEmptyTag"
internal const val SearchResultsListItemTag = "SearchResultsListItemTag"

internal const val SearchFiltersTag = "SearchFiltersTag"

internal const val SearchFiltersPriceRangeTag = "SearchFiltersPriceRangeTag"
internal const val SearchFiltersPriceRangeLabelTag = "SearchFiltersPriceRangeLabelTag"

internal const val SearchFiltersRatingRangeTag = "SearchFiltersRatingRangeTag"
internal const val SearchFiltersRatingRangeLabelTag = "SearchFiltersRatingRangeLabelTag"

internal const val SearchFiltersExactMatchSwitchTag = "SearchFiltersExactMatchSwitchTag"
