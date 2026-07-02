@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ContextualFlowRow
import androidx.compose.foundation.layout.ContextualFlowRowOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.PreviewGiveaway
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.adaptive.WidthSizeClass
import pm.bam.gamedeals.common.ui.adaptive.rememberWidthSizeClass
import pm.bam.gamedeals.common.ui.components.StoreLabel
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
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_empty_live
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filter_button
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filter_button_count
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_loading_indicator
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_platform_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_sort_by_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_type_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_game_image
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_free_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_go_to_giveaway
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_opens_detail
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_row_description
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_row_description_worth
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_title_free_on
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_worth_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_no_expiry
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_platforms_overflow
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

// Fixed height of the GiveawayCard platform-badge / countdown row. Comfortably fits the 16dp
// StoreIcon chip and the labelMedium countdown text, and keeps every card the exact same height.
private val GiveawayBadgeRowHeight = 24.dp

@Composable
internal fun GiveawaysScreen(
    goToWeb: (url: String, gameTitle: String) -> Unit,
    viewModel: GiveawaysViewModel = koinViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    var showFilters by rememberSaveable { mutableStateOf(false) }
    var existingParameters by rememberSaveable(stateSaver = parametersSaver) { mutableStateOf(GiveawaySearchParameters()) }


    GiveawaysScreenContent(
        data = uiState.value,
        widthClass = rememberWidthSizeClass(),
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
    widthClass: WidthSizeClass = WidthSizeClass.COMPACT,
    onReload: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    existingParameters: GiveawaySearchParameters,
    showFilters: Boolean,
    onShowFiltersChanged: (showFilters: Boolean) -> Unit,
    onPlatformSelection: (platform: GiveawayPlatform, selection: Boolean) -> Unit,
    onTypeSelection: (type: GiveawayType, selection: Boolean) -> Unit,
    onSortBySelection: (sortBy: GiveawaySortBy) -> Unit
) {
    val scrollState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Tapping a card opens a peek sheet for that giveaway (no navigation). We keep the id (Saveable)
    // and resolve the full object from the live list; if it expired out of the list the sheet closes.
    var selectedGiveawayId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedGiveaway = selectedGiveawayId?.let { id -> data.giveaways.firstOrNull { it.id == id } }

    // Column count widens with the available space (Home hero-tile gradation): 2-up on phones,
    // 3 on medium (portrait tablets), 4 on expanded (large/wide windows).
    val columns = when (widthClass) {
        WidthSizeClass.COMPACT -> 2
        WidthSizeClass.MEDIUM -> 3
        WidthSizeClass.EXPANDED -> 4
    }
    val currentOnReload by rememberUpdatedState(onReload)

    val errorMessage = stringResource(Res.string.giveaway_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.giveaway_screen_data_loading_error_retry)

    val loadingCd = stringResource(Res.string.giveaway_screen_loading_indicator)

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            // The app shell owns the top bar + bottom nav and insets the NavHost; this inner
            // Scaffold only hosts the snackbar, so it contributes no insets of its own.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding: PaddingValues ->
            Column(modifier = Modifier.padding(innerPadding)) {
                // Filter affordance mirrors the Deals tab (the shared shell top bar is content-agnostic),
                // keeping the same look and feel across screens.
                FilterBar(
                    activeCount = existingParameters.activeCount,
                    onClick = { onShowFiltersChanged(true) },
                )

                when (data.status) {
                    GiveawaysViewModel.GiveawaysScreenStatus.LOADING -> CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .semantics { contentDescription = loadingCd }
                    )

                    GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS -> if (data.giveaways.isEmpty()) {
                        CenteredMessage(message = stringResource(Res.string.giveaway_screen_empty_live))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = scrollState,
                            contentPadding = PaddingValues(
                                horizontal = GameDealsCustomTheme.spacing.medium,
                                vertical = GameDealsCustomTheme.spacing.small,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                        ) {
                            items(
                                items = data.giveaways,
                                key = { giveaway -> giveaway.id },
                            ) { giveaway ->
                                GiveawayCard(
                                    giveaway = giveaway,
                                    endDateMillis = data.endDateMillis[giveaway.id],
                                    onOpenDetail = { selectedGiveawayId = giveaway.id },
                                    onGoToGiveaway = { goToWeb(giveaway.openGiveawayUrl, giveaway.title) },
                                    // Fill the grid line's height so cards in a row line up at the bottom.
                                    modifier = Modifier.fillMaxHeight(),
                                )
                            }
                        }
                    }

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
            }

            GiveawayFilters(
                existingParameters = existingParameters,
                showFilters = showFilters,
                onDismiss = { onShowFiltersChanged(false) },
                onPlatformSelection = onPlatformSelection,
                onTypeSelection = onTypeSelection,
                onSortBySelection = onSortBySelection
            )

            GiveawayPeekSheet(
                giveaway = selectedGiveaway,
                endDateMillis = selectedGiveaway?.let { data.endDateMillis[it.id] },
                onDismiss = { selectedGiveawayId = null },
                goToWeb = goToWeb,
            )
        }
    }
}

/** Filter affordance mirroring the Deals tab: an outlined "Filter" button with an active-count badge. */
@Composable
private fun FilterBar(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(GameDealsCustomTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onClick) {
            Icon(Icons.Filled.FilterList, contentDescription = null)
            Text(
                modifier = Modifier.padding(start = GameDealsCustomTheme.spacing.small),
                text = if (activeCount > 0) stringResource(Res.string.giveaway_screen_filter_button_count, activeCount)
                else stringResource(Res.string.giveaway_screen_filter_button),
            )
        }
    }
}

/** Centered placeholder shown when the live giveaways (or the active filters) yield no results. */
@Composable
private fun CenteredMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(GameDealsCustomTheme.spacing.large)
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.politeLiveRegion(),
        )
    }
}

/**
 * An ITAD-styled giveaway card: game art + "<title> - FREE on <platform>", the original worth
 * struck through, the platforms as store badges, a live countdown (or "No expiry"), and a prominent
 * "Go to giveaway" claim button. Tapping the card body opens the in-app detail; the button is the
 * fast path straight to the claim URL.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GiveawayCard(
    giveaway: Giveaway,
    endDateMillis: Long?,
    onOpenDetail: () -> Unit,
    onGoToGiveaway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val platformsText = giveaway.platforms.joinToString { it.platformValue }
    val titleText = if (platformsText.isNotBlank()) {
        stringResource(Res.string.giveaway_screen_list_item_title_free_on, giveaway.title, platformsText)
    } else {
        giveaway.title
    }

    val rowCd = giveaway.worthDenominated?.let {
        stringResource(Res.string.giveaway_screen_list_item_row_description_worth, giveaway.title, it)
    } ?: stringResource(Res.string.giveaway_screen_list_item_row_description, giveaway.title)
    val opensDetailCd = stringResource(Res.string.giveaway_screen_list_item_opens_detail)

    Card(
        onClick = onOpenDetail,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$rowCd, $opensDetailCd" },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            // Full-width hero art uses the card's whole width; the platform badges and countdown
            // run along the row below it, and the title and worth follow at full width. The ratio
            // matches GamerPower's source image (460×215 ≈ 2.14:1, a Steam header) so Crop no longer
            // slices the sides — that's also its max resolution, so it can't be made sharper.
            AsyncImage(
                model = giveaway.image,
                contentDescription = stringResource(Res.string.giveaway_screen_game_image, giveaway.title),
                error = painterResource(CommonRes.drawable.videogame_thumb),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(460f / 215f)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
            )
            Row(
                // Fixed height so this row is identical whether the card has platform chips (16dp
                // StoreIcon), just countdown text, or no platforms at all — the last thing that made
                // card heights wobble. Content is centred within it.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GiveawayBadgeRowHeight),
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cap to one line so every card's badge row is the same height. Platforms that don't
                // fit collapse into a trailing "+N" indicator so the user knows there are more (they're
                // also all named in the "… FREE on <platforms>" title above).
                ContextualFlowRow(
                    itemCount = giveaway.platforms.size,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                    maxLines = 1,
                    overflow = ContextualFlowRowOverflow.expandIndicator {
                        Text(
                            text = stringResource(
                                Res.string.giveaway_screen_platforms_overflow,
                                totalItemCount - shownItemCount,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) { index ->
                    StoreLabel(storeName = giveaway.platforms[index].platformValue)
                }
                endDateMillis?.let {
                    GiveawayCountdown(expiryEpochMs = it, style = MaterialTheme.typography.labelMedium)
                } ?: Text(
                    text = stringResource(Res.string.giveaway_screen_no_expiry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                // Always reserve two lines so a 1-line and a 2-line title take the same vertical space.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            giveaway.worthDenominated?.let {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.bodyMedium.toSpanStyle()) {
                            append(stringResource(Res.string.giveaway_screen_list_item_free_label))
                        }
                        append(" ")
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(stringResource(Res.string.giveaway_screen_list_item_worth_label, it))
                        }
                    },
                    // Match the plain "Free" branch's metrics so worth-present and worth-absent
                    // cards are the exact same height (otherwise this falls back to the larger
                    // default LocalTextStyle line height).
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } ?: Text(
                text = stringResource(Res.string.giveaway_screen_list_item_free_label),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )

            // Push the claim button to the bottom so cards in the same grid row line up.
            Spacer(modifier = Modifier.weight(1f))

            Button(onClick = onGoToGiveaway, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.giveaway_screen_list_item_go_to_giveaway))
            }
        }
    }
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

// A fixed far-future expiry so the preview's countdown renders a stable value (PreviewGiveaway is "N/A" → no chip).
private val previewEndDateMillis = persistentMapOf(456 to 4_102_444_800_000L)

@Preview
@Composable
private fun GiveawaysScreen_Success_Preview() {
    GameDealsTheme {
        GiveawaysScreenContent(
            data = GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = previewGiveawaysList,
                endDateMillis = previewEndDateMillis,
            ),
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
                endDateMillis = previewEndDateMillis,
            ),
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

// The width tier is forced via [widthClass] (not read from the window), so these previews exercise the
// wide layouts even though the CMP @Preview canvas defaults to phone width — resize in the IDE to see
// the columns breathe.
@Preview
@Composable
private fun GiveawaysScreen_Success_Medium_Preview() {
    GameDealsTheme {
        GiveawaysScreenContent(
            data = GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = previewGiveawaysList,
                endDateMillis = previewEndDateMillis,
            ),
            widthClass = WidthSizeClass.MEDIUM,
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
private fun GiveawaysScreen_Success_Expanded_Preview() {
    GameDealsTheme {
        GiveawaysScreenContent(
            data = GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = previewGiveawaysList,
                endDateMillis = previewEndDateMillis,
            ),
            widthClass = WidthSizeClass.EXPANDED,
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
private fun GiveawaysScreen_Empty_Preview() {
    GameDealsTheme {
        GiveawaysScreenContent(
            data = GiveawaysViewModel.GiveawaysScreenData(
                status = GiveawaysViewModel.GiveawaysScreenStatus.SUCCESS,
                giveaways = persistentListOf(),
            ),
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
    // Preview the Filters body directly. ModalBottomSheet does not render reliably in static previews, so we skip GiveawayFilters() and call its content
    // composable instead.
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
