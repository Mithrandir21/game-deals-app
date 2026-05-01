package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import pm.bam.gamedeals.common.ui.PhonePortrait
import pm.bam.gamedeals.common.ui.PreviewGiveaway
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.feature.giveaways.R

@Composable
internal fun GiveawaysScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    viewModel: GiveawaysViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    var showFilters by rememberSaveable { mutableStateOf(false) }
    var existingParameters by rememberSaveable(stateSaver = parametersSaver) { mutableStateOf(GiveawaySearchParameters()) }


    ScreenScaffold(
        data = uiState.value,
        onBack = onBack,
        onReload = { viewModel.reloadGiveaways() },
        goToWeb = goToWeb,
        existingParameters = existingParameters,
        showFilters = showFilters,
        onShowFiltersChanged = { newShowFilters ->
            showFilters = newShowFilters
            if (!newShowFilters) {
                viewModel.loadGiveaway(existingParameters)
            }
        },
        onPlatformSelection = { platform, selection ->
            existingParameters = existingParameters.copy(
                platforms = existingParameters.platforms.toMutableList().map { if (it.first == platform) platform to selection else it })
        },
        onTypeSelection = { type, selection ->
            existingParameters = existingParameters.copy(
                types = existingParameters.types.toMutableList().map { if (it.first == type) type to selection else it })
        },
        onSortBySelection = { sortBy ->
            existingParameters = existingParameters.copy(sortBy = sortBy)
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(
    data: GiveawaysViewModel.GiveawaysScreenData,
    onBack: () -> Unit,
    onReload: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    existingParameters: GiveawaySearchParameters,
    showFilters: Boolean,
    onShowFiltersChanged: (showFilters: Boolean) -> Unit,
    onPlatformSelection: (platform: GiveawayPlatform, selection: Boolean) -> Unit,
    onTypeSelection: (type: GiveawayType, selection: Boolean) -> Unit,
    onSortBySelection: (sortBy: GiveawaySortBy) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnReload by rememberUpdatedState(onReload)

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
                        title = { Text(text = "Giveaways", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(
                                modifier = Modifier.testTag(TopAppNavBarTag),
                                onClick = { onBack() }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.giveaway_screen_navigation_back_button)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { onShowFiltersChanged(!showFilters) }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    stringResource(R.string.giveaway_screen_filters_icon),
                                    modifier = Modifier.testTag(GiveawayFiltersIconTag)
                                )
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
            ) { innerPadding: PaddingValues ->
                when (data.status) {
                    GiveawaysViewModel.GiveawaysScreenStatus.LOADING -> CircularProgressIndicator(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .testTag(LoadingDataTag)
                    )

                    GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS -> LazyColumn(
                        state = scrollState,
                        modifier = Modifier.padding(innerPadding),
                        content = {
                            items(
                                count = data.giveaways.size,
                                key = { index -> data.giveaways[index].id }
                            ) {
                                GiveawayListItem(data.giveaways[it]) { goToWeb(data.giveaways[it].gamerpowerUrl, data.giveaways[it].title) }
                            }
                        }
                    )

                    GiveawaysViewModel.GiveawaysScreenStatus.ERROR -> LaunchedEffect(snackbarHostState) {
                        val results = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.giveaway_screen_data_loading_error_msg),
                            actionLabel = context.getString(R.string.giveaway_screen_data_loading_error_retry)
                        )
                        if (results == SnackbarResult.ActionPerformed) {
                            currentOnReload()
                        }
                    }
                }

                GiveawayFilters(
                    existingParameters = existingParameters,
                    showFilters = showFilters,
                    onDismiss = { onShowFiltersChanged(false) },
                    onPlatformSelection = onPlatformSelection,
                    onTypeSelection = onTypeSelection,
                    onSortBySelection = onSortBySelection
                )
            }
        }
    }
}

@Composable
private fun GiveawayListItem(
    giveaway: Giveaway,
    onGiveaway: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clickable { onGiveaway() }
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small)
            .testTag(GiveawayListItemTag.plus(giveaway.id)),
        headlineContent = { Text(giveaway.title) },
        supportingContent = {
            giveaway.worthDenominated?.let {
                Text(text = buildAnnotatedString {
                    withStyle(style = MaterialTheme.typography.bodyLarge.toSpanStyle()) {
                        append(stringResource(id = R.string.giveaway_screen_list_item_free_label))
                    }
                    append(" ")
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(stringResource(id = R.string.giveaway_screen_list_item_worth_label, it))
                    }
                })
            } ?: Text(stringResource(id = R.string.giveaway_screen_list_item_free_label))
        },
        leadingContent = {
            AsyncImage(
                model = giveaway.thumbnail,
                contentDescription = stringResource(R.string.giveaway_screen_game_image, giveaway.title),
                error = painterResource(id = pm.bam.gamedeals.common.ui.R.drawable.videogame_thumb),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(60.dp)
                    .width(100.dp)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
            )
        }
    )
    HorizontalDivider(color = Color.Black)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiveawayFilters(
    existingParameters: GiveawaySearchParameters,
    showFilters: Boolean,
    onDismiss: () -> Unit,
    onPlatformSelection: (platform: GiveawayPlatform, selection: Boolean) -> Unit,
    onTypeSelection: (type: GiveawayType, selection: Boolean) -> Unit,
    onSortBySelection: (sortBy: GiveawaySortBy) -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showFilters) {
        ModalBottomSheet(
            modifier = Modifier.testTag(GiveawayFiltersTag),
            onDismissRequest = { onDismiss() },
            sheetState = modalBottomSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Filters(existingParameters, onPlatformSelection, onTypeSelection, onSortBySelection)
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(
    existingParameters: GiveawaySearchParameters,
    onPlatformSelection: (platform: GiveawayPlatform, selection: Boolean) -> Unit,
    onTypeSelection: (type: GiveawayType, selection: Boolean) -> Unit,
    onSortBySelection: (sortBy: GiveawaySortBy) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(GameDealsCustomTheme.spacing.large)
            .navigationBarsPadding()
    ) {
        Text(
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.medium),
            text = stringResource(R.string.giveaway_screen_filters_platform_label)
        )
        FlowRow(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)
        ) {
            existingParameters.platforms.forEach { (platform, selected) ->
                FilterChip(selected = selected, onClick = { onPlatformSelection(platform, !selected) }, label = {
                    Text(
                        text = platform.platformValue,
                        modifier = Modifier.padding(GameDealsCustomTheme.spacing.extraSmall),
                        style = MaterialTheme.typography.bodyMedium
                    )
                })
            }
        }

        HorizontalDivider()

        Text(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
            text = stringResource(R.string.giveaway_screen_filters_type_label)
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)
        ) {
            existingParameters.types.forEach { (type, selected) ->
                FilterChip(selected = selected, onClick = { onTypeSelection(type, !selected) }, label = {
                    Text(
                        text = type.name,
                        modifier = Modifier.padding(GameDealsCustomTheme.spacing.extraSmall),
                        style = MaterialTheme.typography.bodyMedium
                    )
                })
            }
        }

        HorizontalDivider()

        Text(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
            text = stringResource(R.string.giveaway_screen_filters_sort_by_label)
        )
        GiveawaySortByOptions(existingParameters, onSortBySelection)
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GiveawaySortByOptions(
    existingParameters: GiveawaySearchParameters,
    onSortBySelection: (sortBy: GiveawaySortBy) -> Unit
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)
    ) {
        GiveawaySortBy.entries
            .map {
                when (it) {
                    existingParameters.sortBy -> it to true
                    else -> it to false
                }
            }
            .forEach { (sortBy, selected) ->
                FilterChip(
                    label = {
                        Text(
                            modifier = Modifier
                                .padding(GameDealsCustomTheme.spacing.extraSmall)
                                .testTag(GiveawayFiltersSortTag.plus(sortBy.name)),
                            text = sortBy.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    selected = selected,
                    onClick = { onSortBySelection(sortBy) })
            }
    }
}


/** Saving mechanism for [GiveawaySearchParameters] into [rememberSaveable]. */
private val parametersSaver = run {
    mapSaver(
        save = { it.asMap() },
        restore = { GiveawaySearchParameters.from(it) }
    )
}


@Preview
@Composable
private fun SortOptionsPreview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GiveawaySortByOptions(
                existingParameters = GiveawaySearchParameters(),
                onSortBySelection = { }
            )
        }
    }
}


@Preview
@Composable
private fun FiltersPreview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Filters(
                existingParameters = GiveawaySearchParameters(),
                onPlatformSelection = { _, _ -> },
                onTypeSelection = { _, _ -> },
                onSortBySelection = { }
            )
        }
    }
}

@PhonePortrait
@Composable
private fun PreviewLoading() {
    ScreenScaffold(
        data = GiveawaysViewModel.GiveawaysScreenData(status = GiveawaysViewModel.GiveawaysScreenStatus.LOADING),
        onBack = {},
        goToWeb = { _, _ -> },
        onReload = {},
        existingParameters = GiveawaySearchParameters(),
        showFilters = false,
        onShowFiltersChanged = {},
        onPlatformSelection = { _, _ -> },
        onTypeSelection = { _, _ -> },
        onSortBySelection = {}
    )
}

@PhonePortrait
@Composable
private fun PreviewData() {
    ScreenScaffold(
        data = GiveawaysViewModel.GiveawaysScreenData(
            status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
            giveaways = persistentListOf(
                PreviewGiveaway.copy(id = 1),
                PreviewGiveaway.copy(id = 2).copy(worthDenominated = null),
                PreviewGiveaway.copy(id = 3),
                PreviewGiveaway.copy(id = 4).copy(worthDenominated = null),
                PreviewGiveaway.copy(id = 5).copy(worthDenominated = null),
            )
        ),
        onBack = {},
        goToWeb = { _, _ -> },
        onReload = {},
        existingParameters = GiveawaySearchParameters(),
        showFilters = false,
        onShowFiltersChanged = {},
        onPlatformSelection = { _, _ -> },
        onTypeSelection = { _, _ -> },
        onSortBySelection = {}
    )
}


internal const val TopAppBarTag = "TopAppBarTag"
internal const val TopAppNavBarTag = "TopAppNavBarTag"
internal const val LoadingDataTag = "LoadingDataTag"

internal const val GiveawayListItemTag = "GiveawayListItemTag"

internal const val GiveawayFiltersTag = "GiveawayFiltersTag"
internal const val GiveawayFiltersIconTag = "GiveawayFiltersIconTag"
internal const val GiveawayFiltersSortTag = "GiveawayFiltersSortTag"