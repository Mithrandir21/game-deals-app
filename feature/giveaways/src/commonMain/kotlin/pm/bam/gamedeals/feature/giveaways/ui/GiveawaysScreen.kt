@file:Suppress("DEPRECATION")

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.PreviewGiveaway
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayPlatformSelection
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.models.GiveawayTypeSelection
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_icon
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_loading_indicator
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_toolbar_title
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_platform_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_sort_by_ascending_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_sort_by_descending_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_sort_by_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_type_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_game_image
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_free_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_worth_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_navigation_back_button
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

@Composable
internal fun GiveawaysScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    viewModel: GiveawaysViewModel = koinViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    var showFilters by rememberSaveable { mutableStateOf(false) }
    var existingParameters by rememberSaveable(stateSaver = parametersSaver) { mutableStateOf(GiveawaySearchParameters()) }


    GiveawaysScreenContent(
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
                platforms = existingParameters.platforms
                    .map { if (it.platform == platform) GiveawayPlatformSelection(platform, selection) else it }
                    .toImmutableList())
        },
        onTypeSelection = { type, selection ->
            existingParameters = existingParameters.copy(
                types = existingParameters.types
                    .map { if (it.type == type) GiveawayTypeSelection(type, selection) else it }
                    .toImmutableList())
        },
        onSortBySelection = { sortBy ->
            existingParameters = existingParameters.copy(sortBy = sortBy)
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiveawaysScreenContent(
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
    val scrollState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnReload by rememberUpdatedState(onReload)

    val errorMessage = stringResource(Res.string.giveaway_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.giveaway_screen_data_loading_error_retry)

    val loadingCd = stringResource(Res.string.giveaway_screen_loading_indicator)

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
                                text = stringResource(Res.string.giveaway_screen_toolbar_title),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { onBack() }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.giveaway_screen_navigation_back_button)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { onShowFiltersChanged(!showFilters) }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    stringResource(Res.string.giveaway_screen_filters_icon)
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
                            .semantics { contentDescription = loadingCd }
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
                            message = errorMessage,
                            actionLabel = errorRetry
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

@Composable
private fun GiveawayListItem(
    giveaway: Giveaway,
    onGiveaway: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clickable { onGiveaway() }
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small),
        headlineContent = { Text(giveaway.title) },
        supportingContent = {
            giveaway.worthDenominated?.let {
                Text(text = buildAnnotatedString {
                    withStyle(style = MaterialTheme.typography.bodyLarge.toSpanStyle()) {
                        append(stringResource(Res.string.giveaway_screen_list_item_free_label))
                    }
                    append(" ")
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(stringResource(Res.string.giveaway_screen_list_item_worth_label, it))
                    }
                })
            } ?: Text(stringResource(Res.string.giveaway_screen_list_item_free_label))
        },
        leadingContent = {
            AsyncImage(
                model = giveaway.thumbnail,
                contentDescription = stringResource(Res.string.giveaway_screen_game_image, giveaway.title),
                error = painterResource(CommonRes.drawable.videogame_thumb),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(60.dp)
                    .width(100.dp)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
            )
        }
    )
    HorizontalDivider()
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
            text = stringResource(Res.string.giveaway_screen_filters_platform_label)
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
            text = stringResource(Res.string.giveaway_screen_filters_type_label)
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)
        ) {
            existingParameters.types.forEach { (type, selected) ->
                FilterChip(selected = selected, onClick = { onTypeSelection(type, !selected) }, label = {
                    Text(
                        text = type.displayLabel(),
                        modifier = Modifier.padding(GameDealsCustomTheme.spacing.extraSmall),
                        style = MaterialTheme.typography.bodyMedium
                    )
                })
            }
        }

        HorizontalDivider()

        Text(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
            text = stringResource(Res.string.giveaway_screen_filters_sort_by_label)
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
                            modifier = Modifier.padding(GameDealsCustomTheme.spacing.extraSmall),
                            text = sortBy.displayLabel(),
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


private val previewGiveawaysList = persistentListOf(
    PreviewGiveaway,
    PreviewGiveaway.copy(
        id = 456,
        title = "Tomb Raider Trilogy",
        worthDenominated = "$49.99",
        worth = 49.99,
        platforms = persistentListOf(GiveawayPlatform.PC, GiveawayPlatform.STEAM),
    ),
    PreviewGiveaway.copy(
        id = 789,
        title = "Crysis Remastered (DLC)",
        worthDenominated = null,
        worth = null,
        type = GiveawayType.DLC,
        platforms = persistentListOf(GiveawayPlatform.EPIC),
    ),
)

@Preview
@Composable
private fun GiveawaysScreen_Success_Preview() {
    GameDealsTheme {
        GiveawaysScreenContent(
            data = GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = previewGiveawaysList,
            ),
            onBack = {},
            onReload = {},
            goToWeb = { _, _ -> },
            existingParameters = GiveawaySearchParameters(),
            showFilters = false,
            onShowFiltersChanged = {},
            onPlatformSelection = { _, _ -> },
            onTypeSelection = { _, _ -> },
            onSortBySelection = {},
        )
    }
}

@Preview
@Composable
private fun GiveawaysScreen_Success_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        GiveawaysScreenContent(
            data = GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = previewGiveawaysList,
            ),
            onBack = {},
            onReload = {},
            goToWeb = { _, _ -> },
            existingParameters = GiveawaySearchParameters(),
            showFilters = false,
            onShowFiltersChanged = {},
            onPlatformSelection = { _, _ -> },
            onTypeSelection = { _, _ -> },
            onSortBySelection = {},
        )
    }
}

@Preview
@Composable
private fun GiveawaysScreen_Loading_Preview() {
    GameDealsTheme {
        GiveawaysScreenContent(
            data = GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.LOADING,
            ),
            onBack = {},
            onReload = {},
            goToWeb = { _, _ -> },
            existingParameters = GiveawaySearchParameters(),
            showFilters = false,
            onShowFiltersChanged = {},
            onPlatformSelection = { _, _ -> },
            onTypeSelection = { _, _ -> },
            onSortBySelection = {},
        )
    }
}

@Preview
@Composable
private fun GiveawayFilters_Preview() {
    // Preview the Filters body directly. ModalBottomSheet does not render
    // reliably in static previews, so we skip GiveawayFilters() and call
    // its content composable instead.
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Filters(
                existingParameters = GiveawaySearchParameters().copy(
                    platforms = GiveawaySearchParameters().platforms
                        .map { GiveawayPlatformSelection(it.platform, it.platform == GiveawayPlatform.PC || it.platform == GiveawayPlatform.STEAM) }
                        .toImmutableList(),
                ),
                onPlatformSelection = { _, _ -> },
                onTypeSelection = { _, _ -> },
                onSortBySelection = {},
            )
        }
    }
}