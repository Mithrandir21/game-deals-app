@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.components.AgeRatingsRow
import pm.bam.gamedeals.common.ui.components.DiscountBadge
import pm.bam.gamedeals.common.ui.components.PriceBlock
import pm.bam.gamedeals.common.ui.components.StoreIcon
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.DealQuality
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.domain.models.portrait
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_details_cover_image_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_critic_rating_label
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_back_button
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_message
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_search_button
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_title
import pm.bam.gamedeals.feature.game.generated.resources.game_details_released_label
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screenshot_image_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_description
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_links
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_similar
import pm.bam.gamedeals.feature.game.generated.resources.game_page_deal_quality_above_detail
import pm.bam.gamedeals.feature.game.generated.resources.game_page_deal_quality_all_time_low_title
import pm.bam.gamedeals.feature.game.generated.resources.game_page_deal_quality_at_low_detail
import pm.bam.gamedeals.feature.game.generated.resources.game_page_deal_quality_elevated_title
import pm.bam.gamedeals.feature.game.generated.resources.game_page_deal_quality_near_low_title
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_game_modes
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_platforms
import pm.bam.gamedeals.feature.game.generated.resources.game_page_series_follow
import pm.bam.gamedeals.feature.game.generated.resources.game_page_series_following
import pm.bam.gamedeals.feature.game.generated.resources.game_page_series_section
import pm.bam.gamedeals.feature.game.generated.resources.game_page_trailer_thumbnail_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_page_trailer_title_fallback
import pm.bam.gamedeals.feature.game.generated.resources.game_details_section_storyline
import pm.bam.gamedeals.feature.game.generated.resources.game_details_similar_game_row_description
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_close
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_current_tile_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_error
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_explanation
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_loading_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_retry
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_title
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_warning_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_user_rating_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_bundle_games_count
import pm.bam.gamedeals.feature.game.generated.resources.game_page_buy_box_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_page_buy_box_open
import pm.bam.gamedeals.feature.game.generated.resources.game_page_buy_box_store
import pm.bam.gamedeals.feature.game.generated.resources.game_page_details_age_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_details_developer_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_details_genres_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_details_publisher_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_hltb_completely
import pm.bam.gamedeals.feature.game.generated.resources.game_page_hltb_hastily
import pm.bam.gamedeals.feature.game.generated.resources.game_page_hltb_hours
import pm.bam.gamedeals.feature.game.generated.resources.game_page_hltb_normally
import pm.bam.gamedeals.feature.game.generated.resources.game_page_players_peak
import pm.bam.gamedeals.feature.game.generated.resources.game_page_players_recent
import pm.bam.gamedeals.feature.game.generated.resources.game_page_review_count
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_bundles
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_details
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_dlcs
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_hltb
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_media
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_players
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_reviews
import pm.bam.gamedeals.feature.game.generated.resources.game_page_history_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_page_overview_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_page_prices_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_page_regions_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_page_regions_error
import pm.bam.gamedeals.feature.game.generated.resources.game_page_regions_expander
import pm.bam.gamedeals.feature.game.generated.resources.game_page_regions_loading_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_error
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_loading_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_page_stats_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_page_tab_about
import pm.bam.gamedeals.feature.game.generated.resources.game_page_tab_community
import pm.bam.gamedeals.feature.game.generated.resources.game_page_tab_deal
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_game_image
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_list_item_savings_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_loading_indicator
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_navigation_back_button
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_add_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_dialog_cancel
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_dialog_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_dialog_placeholder
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_dialog_save
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_dialog_title
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_edit_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_share_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_store_deal_row_description
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_summary_read_more
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_summary_show_less
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_collection_add_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_collection_remove_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_favourite_add_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_favourite_remove_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_ignore_add_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_ignore_remove_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_more_actions
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_toolbar_title_loading
import pm.bam.gamedeals.feature.game.ui.GamePageViewModel.GamePageData
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.navigation.SignInPromptController
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

// IGDB video ids are YouTube ids; we open the watch page externally and show YouTube's thumbnail tile.
private fun youTubeWatchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
private fun youTubeThumbnailUrl(videoId: String): String = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

private const val COVER_ASPECT_RATIO = 0.75f
private const val SCREENSHOT_ASPECT_RATIO = 16f / 9f
private val TRAILER_TILE_WIDTH = 320.dp // 180.dp tall × 16:9
private const val COLLAPSED_LINES = 5

/** Height of the condensed price bar that fades in as the hero collapses. */
private val STICKY_BAR_HEIGHT = 56.dp

/** Which facet a tab's "Retry" re-fetches — threaded down to the tabs and dispatched to the VM at the top. */
private enum class RetrySection { Deals, PriceHistory, GameMeta, Igdb }

/**
 * The unified Game Page (epic #291), redesigned into three intent-based tabs — **Deal**, **About** and
 * **Community** — under a collapsing hero whose "buy box" (best price + discount + store + all-time-low) is
 * the single source of price truth. As the hero scrolls away a condensed price bar fades in so the best
 * deal stays one tap away from any tab. Reuses the standalone [PriceHistoryChart] and [ScreenshotViewerDialog]
 * plus the shared [AgeRatingsRow]/[PriceBlock]/[DiscountBadge]; the remaining sections are private here.
 */
@Composable
internal fun GamePageScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onSimilarGameClick: (igdbGameId: Long) -> Unit = {},
    onSearchDealsByTitle: (title: String) -> Unit = {},
    onBundleClick: (bundleId: Int) -> Unit = {},
    viewModel: GamePageViewModel = koinViewModel(),
) {
    val data = viewModel.uiState.collectAsStateWithLifecycle()
    val isFavourite = viewModel.isWaitlisted.collectAsStateWithLifecycle()
    val isCollected = viewModel.isCollected.collectAsStateWithLifecycle()
    val isIgnored = viewModel.isIgnored.collectAsStateWithLifecycle()
    val note = viewModel.note.collectAsStateWithLifecycle()
    val followedFranchiseIds = viewModel.followedFranchiseIds.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current
    val snackbarHostState = remember { SnackbarHostState() }

    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is GamePageViewModel.GameUiEvent.ShareDeal -> platformActions.share(event.text)
            GamePageViewModel.GameUiEvent.SignInRequired -> SignInPromptController.request()
        }
    }

    GamePageContent(
        data = data.value,
        isFavourite = isFavourite.value,
        isCollected = isCollected.value,
        isIgnored = isIgnored.value,
        note = note.value,
        followedFranchiseIds = followedFranchiseIds.value,
        onBack = onBack,
        goToWeb = goToWeb,
        onSimilarGameClick = onSimilarGameClick,
        onSearchDealsByTitle = onSearchDealsByTitle,
        onShareDeal = { info, store, deal -> viewModel.onShareDealClicked(info, store, deal) },
        onToggleFollowFranchise = viewModel::toggleFollowFranchise,
        onToggleFavourite = viewModel::toggleWaitlist,
        onToggleCollection = viewModel::toggleCollection,
        onToggleIgnore = viewModel::toggleIgnore,
        onSaveNote = viewModel::setNote,
        onDeleteNote = viewModel::deleteNote,
        onRetry = viewModel::reload,
        onWarningTap = viewModel::onWarningTap,
        onPickerDismiss = viewModel::onPickerDismiss,
        onCandidatePicked = viewModel::onCandidatePicked,
        onBundleClick = onBundleClick,
        onRegionsSelected = viewModel::onRegionsSelected,
        onRetrySection = { section ->
            when (section) {
                RetrySection.Deals -> viewModel.retryDeals()
                RetrySection.PriceHistory -> viewModel.retryPriceHistory()
                RetrySection.GameMeta -> viewModel.retryGameMeta()
                RetrySection.Igdb -> viewModel.retryIgdb()
            }
        },
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamePageContent(
    data: GamePageData,
    isFavourite: Boolean,
    isCollected: Boolean,
    isIgnored: Boolean,
    note: String?,
    followedFranchiseIds: Set<Long>,
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onSimilarGameClick: (igdbGameId: Long) -> Unit,
    onSearchDealsByTitle: (title: String) -> Unit,
    onShareDeal: (GameDetails.GameInfo, Store, GameDetails.GameDeal) -> Unit,
    onToggleFollowFranchise: (franchiseId: Long, name: String) -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleCollection: () -> Unit,
    onToggleIgnore: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDeleteNote: () -> Unit,
    onRetry: () -> Unit,
    onWarningTap: () -> Unit,
    onPickerDismiss: () -> Unit,
    onCandidatePicked: (igdbGameId: Long) -> Unit,
    onBundleClick: (bundleId: Int) -> Unit,
    onRegionsSelected: () -> Unit,
    onRetrySection: (RetrySection) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val currentOnRetry by rememberUpdatedState(onRetry)
    val errorMessage = stringResource(Res.string.game_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.game_screen_data_loading_error_retry)

    // "My note" is a personal action, not page content — it lives in the top bar and opens this dialog.
    var editingNote by remember { mutableStateOf(false) }

    val title = when (data) {
        is GamePageData.Data -> data.title
        is GamePageData.NoMatch -> data.title
        else -> stringResource(Res.string.game_screen_toolbar_title_loading)
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
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.game_screen_navigation_back_button),
                            )
                        }
                    },
                    actions = {
                        val resolvedByTitle = (data as? GamePageData.Data)?.resolvedByTitle == true
                        val canAct = data is GamePageData.Data
                        if (resolvedByTitle) {
                            IconButton(onClick = onWarningTap) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = stringResource(Res.string.game_details_title_match_warning_cd),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        // Waitlist (bookmark) + Collection (library-check) + Note are the primary actions;
                        // Ignore folds into the overflow menu.
                        IconButton(enabled = canAct, onClick = onToggleFavourite) {
                            Icon(
                                imageVector = if (isFavourite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                tint = if (isFavourite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                contentDescription = stringResource(
                                    if (isFavourite) Res.string.game_screen_favourite_remove_action else Res.string.game_screen_favourite_add_action
                                ),
                            )
                        }
                        IconButton(enabled = canAct, onClick = onToggleCollection) {
                            Icon(
                                imageVector = if (isCollected) Icons.Filled.LibraryAddCheck else Icons.Outlined.LibraryAddCheck,
                                tint = if (isCollected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                contentDescription = stringResource(
                                    if (isCollected) Res.string.game_screen_collection_remove_action else Res.string.game_screen_collection_add_action
                                ),
                            )
                        }
                        IconButton(enabled = canAct, onClick = { editingNote = true }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                tint = if (note != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                contentDescription = stringResource(
                                    if (note != null) Res.string.game_screen_note_edit_action else Res.string.game_screen_note_add_action
                                ),
                            )
                        }
                        Box {
                            var menuExpanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(Res.string.game_screen_more_actions),
                                )
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (isIgnored) Res.string.game_screen_ignore_remove_action else Res.string.game_screen_ignore_add_action
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.VisibilityOff,
                                            tint = if (isIgnored) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                            contentDescription = null,
                                        )
                                    },
                                    enabled = canAct,
                                    onClick = {
                                        menuExpanded = false
                                        onToggleIgnore()
                                    },
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
            val loadingCd = stringResource(Res.string.game_screen_loading_indicator)
            when (data) {
                GamePageData.Loading -> CircularProgressIndicator(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                        .semantics { contentDescription = loadingCd },
                )

                GamePageData.Error -> LaunchedEffect(snackbarHostState) {
                    val result = snackbarHostState.showSnackbar(message = errorMessage, actionLabel = errorRetry)
                    if (result == SnackbarResult.ActionPerformed) currentOnRetry()
                }

                is GamePageData.NoMatch -> NoMatchSection(
                    modifier = Modifier.padding(innerPadding),
                    title = data.title,
                    onSearch = { onSearchDealsByTitle(data.title) },
                    onBack = onBack,
                )

                is GamePageData.Data -> GamePageBody(
                    modifier = Modifier.padding(innerPadding),
                    data = data,
                    followedFranchiseIds = followedFranchiseIds,
                    goToWeb = goToWeb,
                    onSimilarGameClick = onSimilarGameClick,
                    onShareDeal = onShareDeal,
                    onToggleFollowFranchise = onToggleFollowFranchise,
                    onBundleClick = onBundleClick,
                    onRegionsSelected = onRegionsSelected,
                    onRetrySection = onRetrySection,
                )
            }
        }
    }

    if (data is GamePageData.Data && editingNote) {
        NoteEditDialog(initial = note.orEmpty(), onDismiss = { editingNote = false }, onConfirm = { text ->
            editingNote = false
            val trimmed = text.trim()
            if (trimmed.isEmpty()) onDeleteNote() else onSaveNote(trimmed)
        })
    }

    if (data is GamePageData.Data && data.showPicker) {
        CandidatePickerSheet(data = data, onDismiss = onPickerDismiss, onCandidatePicked = onCandidatePicked, onRetry = onWarningTap)
    }
}

/**
 * The collapsing-hero + 3-tab body. The hero (cover, title, ratings, buy box) is rendered once in a
 * translated header overlay above a pinned [TabRow]; the three tab bodies are pages of a [HorizontalPager],
 * each its own [LazyColumn] so they scroll — and retain scroll position — independently. As the user scrolls
 * a tab, a [NestedScrollConnection] collapses the hero first (translating the header up); a condensed
 * [StickyPriceBar] crossfades in beneath the tabs so the best price is always reachable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamePageBody(
    modifier: Modifier,
    data: GamePageData.Data,
    followedFranchiseIds: Set<Long>,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onSimilarGameClick: (igdbGameId: Long) -> Unit,
    onShareDeal: (GameDetails.GameInfo, Store, GameDetails.GameDeal) -> Unit,
    onToggleFollowFranchise: (franchiseId: Long, name: String) -> Unit,
    onBundleClick: (bundleId: Int) -> Unit,
    onRegionsSelected: () -> Unit,
    onRetrySection: (RetrySection) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 3 }
    val listStates = List(3) { rememberLazyListState() }
    val tabs = listOf(
        stringResource(Res.string.game_page_tab_deal),
        stringResource(Res.string.game_page_tab_about),
        stringResource(Res.string.game_page_tab_community),
    )

    // Collapsing-hero geometry. Heights are measured from content (independent of the scroll offset, so no
    // feedback loop); only the offset drives the collapse.
    val heroPx = remember { mutableFloatStateOf(0f) }
    val tabPx = remember { mutableFloatStateOf(0f) }
    val heroOffset = remember { mutableFloatStateOf(0f) }
    val stickyPx = if (data.buyBox != null) with(density) { STICKY_BAR_HEIGHT.toPx() } else 0f
    val collapseFraction = if (heroPx.floatValue > 0f) (-heroOffset.floatValue / heroPx.floatValue).coerceIn(0f, 1f) else 0f

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta >= 0f) return Offset.Zero // scrolling down → handled in onPostScroll (expand only at top)
                val max = heroPx.floatValue
                val newOffset = (heroOffset.floatValue + delta).coerceIn(-max, 0f)
                val consumed = newOffset - heroOffset.floatValue
                heroOffset.floatValue = newOffset
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta <= 0f) return Offset.Zero
                val max = heroPx.floatValue
                val newOffset = (heroOffset.floatValue + delta).coerceIn(-max, 0f)
                val used = newOffset - heroOffset.floatValue
                heroOffset.floatValue = newOffset
                return Offset(0f, used)
            }
        }
    }

    // The pinned header bottom (where tab content must start): visible hero + tab row + the growing strip.
    val contentTopPx = (heroOffset.floatValue + heroPx.floatValue + tabPx.floatValue + stickyPx * collapseFraction).coerceAtLeast(0f)
    val contentTopDp = with(density) { contentTopPx.toDp() }

    Box(modifier = modifier.fillMaxSize().nestedScroll(connection)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            LazyColumn(
                state = listStates[page],
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentTopDp + GameDealsCustomTheme.spacing.large,
                    bottom = GameDealsCustomTheme.spacing.large,
                ),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.large),
            ) {
                item(key = "tab-$page") {
                    when (page) {
                        0 -> DealTab(
                            data = data,
                            goToWeb = goToWeb,
                            onShareDeal = onShareDeal,
                            onBundleClick = onBundleClick,
                            onRegionsSelected = onRegionsSelected,
                            onRetrySection = onRetrySection,
                        )
                        1 -> AboutTab(
                            data = data,
                            followedFranchiseIds = followedFranchiseIds,
                            goToWeb = goToWeb,
                            onSimilarGameClick = onSimilarGameClick,
                            onToggleFollowFranchise = onToggleFollowFranchise,
                            onRetryIgdb = { onRetrySection(RetrySection.Igdb) },
                        )
                        else -> CommunityTab(data = data, onRetry = { onRetrySection(RetrySection.GameMeta) })
                    }
                }
            }
        }

        // Header overlay: drawn over the pager, translated up by the collapse offset. Opaque so content
        // scrolling underneath the hero band stays hidden.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, heroOffset.floatValue.roundToInt()) }
                .background(MaterialTheme.colorScheme.background),
        ) {
            HeroContent(
                data = data,
                goToWeb = goToWeb,
                onRatingsClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                modifier = Modifier.onSizeChanged { heroPx.floatValue = it.height.toFloat() },
            )
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.onSizeChanged { tabPx.floatValue = it.height.toFloat() },
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(label) },
                    )
                }
            }
            // Condensed price bar — reserves height and fades in only as the hero collapses, so it doesn't
            // leave a blank band when the full hero (and its buy box) is visible.
            data.buyBox?.let { buyBox ->
                Box(modifier = Modifier.height(STICKY_BAR_HEIGHT * collapseFraction).alpha(collapseFraction)) {
                    StickyPriceBar(buyBox = buyBox, gameId = data.gameId, gameTitle = data.title, goToWeb = goToWeb)
                }
            }
        }
    }
}

// ----- Hero + buy box -------------------------------------------------------------------------------------

@Composable
private fun HeroContent(
    data: GamePageData.Data,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onRatingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val igdb = data.igdbGameOrNull
    val coverModel: Any? = igdb?.coverImageId?.let { igdbImageUrl(it, IgdbImageSize.CoverBig) } ?: data.gameDetailsOrNull?.info?.artwork?.portrait
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(COVER_ASPECT_RATIO)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(GameDealsCustomTheme.spacing.small)),
            ) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = stringResource(Res.string.game_screen_game_image, data.title),
                    error = painterResource(CommonRes.drawable.videogame_thumb),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(GameDealsCustomTheme.spacing.small)),
                )
            }
            Column(
                modifier = Modifier.padding(start = GameDealsCustomTheme.spacing.medium).weight(1f),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                Text(text = data.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                igdb?.firstReleaseDate?.let { instant ->
                    Text(text = stringResource(Res.string.game_details_released_label, formatReleaseDate(instant)), style = MaterialTheme.typography.bodyMedium)
                }
                // Compact ratings here; the full breakdown (with players/reviews) lives in the Community tab.
                if (igdb != null) RatingsRow(igdb, onClick = onRatingsClick)
            }
        }
        data.buyBox?.let { HeroBuyBox(buyBox = it, gameId = data.gameId, gameTitle = data.title, goToWeb = goToWeb) }
    }
}

/**
 * The hero "buy box": the single source of price truth. Shows the cheapest current deal (price + struck
 * regular price), its discount and store, and the [DealQuality] buy-signal. The whole card opens that deal
 * (same analytics + in-app browser path as a store row), merged into one spoken phrase for TalkBack.
 */
@Composable
private fun HeroBuyBox(
    buyBox: BuyBoxState,
    gameId: String?,
    gameTitle: String,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    val analytics: Analytics = koinInject()
    val deal = buyBox.pair.deal
    val store = buyBox.pair.store
    val rowCd = stringResource(Res.string.game_screen_store_deal_row_description, store.storeName, deal.savings, deal.priceDenominated)
    val qualityTitle = buyBox.quality?.let { dealQualityTitle(it) }
    val mergedCd = stringResource(Res.string.game_page_buy_box_cd, deal.priceDenominated, qualityTitle ?: rowCd)
    Card(
        onClick = { openStoreDeal(analytics, gameId, store, deal, gameTitle, goToWeb) },
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = mergedCd },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)) {
                PriceBlock(
                    salePrice = deal.priceDenominated,
                    regularPrice = deal.retailPriceDenominated,
                    salePriceStyle = MaterialTheme.typography.headlineSmall,
                    horizontalAlignment = Alignment.Start,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)) {
                    StoreIcon(storeName = store.storeName, iconUrl = store.iconUrl, iconSize = GameDealsCustomTheme.spacing.large, contentDescription = null)
                    Text(text = stringResource(Res.string.game_page_buy_box_store, store.storeName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                buyBox.quality?.let { BuyBoxQuality(it) }
            }
            DiscountBadge(discountPercent = deal.savings)
        }
    }
}

/** The colour-graded all-time-low buy signal line inside the buy box (replaces the old standalone callout). */
@Composable
private fun BuyBoxQuality(quality: DealQuality) {
    val color = when (quality.tier) {
        DealQuality.Tier.AllTimeLow -> MaterialTheme.colorScheme.tertiary
        DealQuality.Tier.NearLow -> MaterialTheme.colorScheme.primary
        DealQuality.Tier.Elevated -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = dealQualityTitle(quality), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color)
}

@Composable
private fun dealQualityTitle(quality: DealQuality): String = stringResource(
    when (quality.tier) {
        DealQuality.Tier.AllTimeLow -> Res.string.game_page_deal_quality_all_time_low_title
        DealQuality.Tier.NearLow -> Res.string.game_page_deal_quality_near_low_title
        DealQuality.Tier.Elevated -> Res.string.game_page_deal_quality_elevated_title
    }
)

/** The condensed price bar that crossfades in beneath the tabs once the hero collapses. */
@Composable
private fun StickyPriceBar(
    buyBox: BuyBoxState,
    gameId: String?,
    gameTitle: String,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    val analytics: Analytics = koinInject()
    val deal = buyBox.pair.deal
    val store = buyBox.pair.store
    val rowCd = stringResource(Res.string.game_screen_store_deal_row_description, store.storeName, deal.savings, deal.priceDenominated)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(role = Role.Button) { openStoreDeal(analytics, gameId, store, deal, gameTitle, goToWeb) }
                .clearAndSetSemantics { contentDescription = rowCd }
                .padding(horizontal = GameDealsCustomTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            StoreIcon(storeName = store.storeName, iconUrl = store.iconUrl, iconSize = GameDealsCustomTheme.spacing.large, contentDescription = null)
            PriceBlock(
                salePrice = deal.priceDenominated,
                regularPrice = deal.retailPriceDenominated,
                salePriceStyle = MaterialTheme.typography.titleMedium,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
            )
            DiscountBadge(discountPercent = deal.savings)
            Text(text = stringResource(Res.string.game_page_buy_box_open), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Records the click-through to a store (analytics) then opens the deal in the in-app browser. */
private fun openStoreDeal(
    analytics: Analytics,
    gameId: String?,
    store: Store,
    deal: GameDetails.GameDeal,
    gameTitle: String,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    analytics.capture(
        AnalyticsEvents.DEAL_STORE_OPENED,
        mapOf(
            "game_id" to (gameId ?: ""),
            "store_id" to store.storeID,
            "store_name" to store.storeName,
            "discount_pct" to deal.savings,
        ),
    )
    goToWeb(deal.url, gameTitle)
}

// ----- Deal tab -------------------------------------------------------------------------------------------

/** All store deals, the price-history chart (which already marks the all-time low), regional prices (lazy, behind an expander) and bundles. */
@Composable
private fun DealTab(
    data: GamePageData.Data,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onShareDeal: (GameDetails.GameInfo, Store, GameDetails.GameDeal) -> Unit,
    onBundleClick: (bundleId: Int) -> Unit,
    onRegionsSelected: () -> Unit,
    onRetrySection: (RetrySection) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        when (val deals = data.deals) {
            SectionState.Loading -> TabLoading()
            SectionState.Error -> TabError(onRetry = { onRetrySection(RetrySection.Deals) })
            is SectionState.Loaded -> {
                val gameDetails = deals.value
                if (gameDetails == null || data.dealDetails.isEmpty()) {
                    TabEmpty(stringResource(Res.string.game_page_prices_empty))
                } else {
                    data.dealDetails.forEach { pair ->
                        StoreGameDealRow(gameId = data.gameId, store = pair.store, gameInfo = gameDetails.info, deal = pair.deal, goToWeb = goToWeb, onShareDeal = onShareDeal)
                    }
                }
            }
        }
        when (val priceHistory = data.priceHistory) {
            SectionState.Loading -> TabLoading()
            SectionState.Error -> TabError(onRetry = { onRetrySection(RetrySection.PriceHistory) })
            is SectionState.Loaded ->
                if (priceHistory.value.points.isEmpty()) TabEmpty(stringResource(Res.string.game_page_history_empty))
                else PriceHistoryChart(priceHistory = priceHistory.value, modifier = Modifier.fillMaxWidth())
        }
        RegionalPricesExpander(state = data.regionalPricesState, gameTitle = data.title, goToWeb = goToWeb, onExpand = onRegionsSelected)
        if (data.bundles.isNotEmpty()) BundlesSection(data.bundles, onBundleClick)
    }
}

/** Regional cross-country prices, collapsed behind an expander so the N per-country fetch only runs on demand. */
@Composable
private fun RegionalPricesExpander(
    state: GamePageViewModel.RegionalPricesState,
    gameTitle: String,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onExpand: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (expanded) onExpand() }
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = GameDealsCustomTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(Res.string.game_page_regions_expander), Modifier.weight(1f))
            Icon(imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (expanded) RegionsContent(state = state, gameTitle = gameTitle, goToWeb = goToWeb)
    }
}

@Composable
private fun RegionsContent(
    state: GamePageViewModel.RegionalPricesState,
    gameTitle: String,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    when (state) {
        GamePageViewModel.RegionalPricesState.Idle,
        GamePageViewModel.RegionalPricesState.Loading -> {
            val loadingCd = stringResource(Res.string.game_page_regions_loading_cd)
            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).semantics { contentDescription = loadingCd })
            }
        }
        GamePageViewModel.RegionalPricesState.Error -> Text(
            text = stringResource(Res.string.game_page_regions_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        is GamePageViewModel.RegionalPricesState.Loaded -> {
            if (state.items.isEmpty()) {
                Text(text = stringResource(Res.string.game_page_regions_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                state.items.forEachIndexed { index, region ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { goToWeb(region.url, gameTitle) }.padding(vertical = GameDealsCustomTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = region.country.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(text = region.priceDenominated, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ----- About tab ------------------------------------------------------------------------------------------

/**
 * The game's IGDB content, clustered into scannable groups: description, a merged media gallery
 * (trailers + screenshots), a compact details table (platforms/modes/genres/HLTB/companies/age), "more
 * games" (DLC + similar + series) and a links footer. IGDB drives the empty/error message; links are a
 * best-effort extra.
 */
@Composable
private fun AboutTab(
    data: GamePageData.Data,
    followedFranchiseIds: Set<Long>,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onSimilarGameClick: (igdbGameId: Long) -> Unit,
    onToggleFollowFranchise: (franchiseId: Long, name: String) -> Unit,
    onRetryIgdb: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.large)) {
        when (val igdb = data.igdb) {
            SectionState.Loading -> TabLoading()
            SectionState.Error -> TabError(onRetry = onRetryIgdb)
            is SectionState.Loaded -> {
                val game = igdb.value
                if (game != null) {
                    if (!game.summary.isNullOrBlank() || !game.storyline.isNullOrBlank()) DescriptionSection(game)
                    if (game.videos.isNotEmpty() || game.screenshotImageIds.isNotEmpty()) MediaGallery(game, goToWeb)
                    DetailsTable(game)
                    val dlcs = game.dlcs + game.expansions
                    if (dlcs.isNotEmpty()) DlcsSection(dlcs, onSimilarGameClick)
                    if (game.similarGames.isNotEmpty()) SimilarGamesSection(game.similarGames, onSimilarGameClick)
                    game.franchises.forEach { franchise ->
                        SeriesSection(
                            franchise = franchise,
                            isFollowed = franchise.id in followedFranchiseIds,
                            onToggleFollow = { onToggleFollowFranchise(franchise.id, franchise.name) },
                            onGameClick = onSimilarGameClick,
                        )
                    }
                } else if (data.websites.isEmpty()) {
                    TabEmpty(stringResource(Res.string.game_page_overview_empty))
                }
            }
        }
        if (data.websites.isNotEmpty()) LinksSection(data.websites)
    }
}

/** The compact label/value "Details" card — folds the old platform/mode/genre/HLTB/company chip-rows + age badges into one scannable block. */
@Composable
private fun DetailsTable(game: IgdbGame) {
    val separator = ", "
    val platforms = game.platforms.takeIf { it.isNotEmpty() }?.joinToString(separator)
    val modes = game.gameModes.takeIf { it.isNotEmpty() }?.joinToString(separator)
    val genres = (game.genres + game.themes).takeIf { it.isNotEmpty() }?.joinToString(separator)
    val developers = game.involvedCompanies.filter { it.role == IgdbGame.IgdbCompanyRole.Role.Developer }.map { it.companyName }.distinct().takeIf { it.isNotEmpty() }?.joinToString(separator)
    val publishers = game.involvedCompanies.filter { it.role == IgdbGame.IgdbCompanyRole.Role.Publisher }.map { it.companyName }.distinct().takeIf { it.isNotEmpty() }?.joinToString(separator)
    val hltb = game.timeToBeat?.let { ttb ->
        listOfNotNull(ttb.hastily, ttb.normally, ttb.completely).map { stringResource(Res.string.game_page_hltb_hours, hoursFromSeconds(it)) }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
    val hasAge = game.ageRatings.isNotEmpty()
    if (platforms == null && modes == null && genres == null && developers == null && publishers == null && hltb == null && !hasAge) return
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large)) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            SectionHeader(stringResource(Res.string.game_page_section_details))
            platforms?.let { DetailRow(stringResource(Res.string.game_page_section_platforms), it) }
            modes?.let { DetailRow(stringResource(Res.string.game_page_section_game_modes), it) }
            genres?.let { DetailRow(stringResource(Res.string.game_page_details_genres_label), it) }
            hltb?.let { DetailRow(stringResource(Res.string.game_page_section_hltb), it) }
            developers?.let { DetailRow(stringResource(Res.string.game_page_details_developer_label), it) }
            publishers?.let { DetailRow(stringResource(Res.string.game_page_details_publisher_label), it) }
            if (hasAge) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(Res.string.game_page_details_age_label), modifier = Modifier.width(112.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AgeRatingsRow(ratings = game.ageRatings, modifier = Modifier.weight(1f), badgeHeight = 32.dp)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(text = label, modifier = Modifier.width(112.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Trailers + screenshots merged into one horizontal gallery: video tiles play externally, image tiles open the viewer. */
@Composable
private fun MediaGallery(game: IgdbGame, goToWeb: (url: String, gameTitle: String) -> Unit) {
    var openIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(stringResource(Res.string.game_page_section_media), Modifier.padding(horizontal = GameDealsCustomTheme.spacing.large))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            itemsIndexed(game.videos) { index, video ->
                val title = video.name ?: stringResource(Res.string.game_page_trailer_title_fallback, index + 1)
                val description = stringResource(Res.string.game_page_trailer_thumbnail_cd, index + 1, game.videos.size, game.name, title)
                Column(
                    modifier = Modifier
                        .width(TRAILER_TILE_WIDTH)
                        .clickable(role = Role.Button) { goToWeb(youTubeWatchUrl(video.videoId), game.name) }
                        .semantics(mergeDescendants = true) { contentDescription = description },
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                            .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.small))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = youTubeThumbnailUrl(video.videoId),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .padding(GameDealsCustomTheme.spacing.extraSmall),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            itemsIndexed(game.screenshotImageIds) { index, imageId ->
                AsyncImage(
                    model = igdbImageUrl(imageId, IgdbImageSize.ScreenshotMed),
                    contentDescription = stringResource(Res.string.game_details_screenshot_image_cd, game.name, index + 1),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(180.dp)
                        .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                        .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.small))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(role = Role.Button) { openIndex = index },
                )
            }
        }
    }
    openIndex?.let { startPage ->
        ScreenshotViewerDialog(
            screenshotImageIds = game.screenshotImageIds,
            gameName = game.name,
            initialPage = startPage,
            onDismiss = { openIndex = null },
        )
    }
}

// ----- Community tab --------------------------------------------------------------------------------------

/** The social-proof tab: critic/user scores (full, with counts), current player counts and per-source review bars. */
@Composable
private fun CommunityTab(data: GamePageData.Data, onRetry: () -> Unit) {
    val igdb = data.igdbGameOrNull
    val hasRatings = igdb != null && (igdb.rating != null || igdb.aggregatedRating != null)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        if (igdb != null) RatingsRow(igdb)
        when (val meta = data.gameMeta) {
            SectionState.Loading -> TabLoading()
            SectionState.Error -> TabError(onRetry = onRetry)
            is SectionState.Loaded -> {
                val value = meta.value
                val hasPlayers = value?.players?.let { it.recent != null || it.peak != null } == true
                val hasReviews = value?.reviews?.isNotEmpty() == true
                if (!hasPlayers && !hasReviews && !hasRatings) {
                    TabEmpty(stringResource(Res.string.game_page_stats_empty))
                } else {
                    value?.players?.let { PlayersBlock(it) }
                    value?.reviews?.takeIf { it.isNotEmpty() }?.let { ReviewsBlock(it) }
                }
            }
        }
    }
}

/**
 * A franchise/series the game belongs to (#7): the series name with a Follow/Following toggle, and its
 * other member games as tappable tiles (reusing the similar-games row).
 */
@Composable
private fun SeriesSection(
    franchise: IgdbGame.IgdbFranchise,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    onGameClick: (igdbGameId: Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(Res.string.game_page_series_section, franchise.name), Modifier.weight(1f))
            FilterChip(
                selected = isFollowed,
                onClick = onToggleFollow,
                label = {
                    Text(stringResource(if (isFollowed) Res.string.game_page_series_following else Res.string.game_page_series_follow))
                },
                leadingIcon = if (isFollowed) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
        if (franchise.games.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                items(franchise.games, key = { it.id }) { game -> IgdbGameTile(game, onGameClick, Modifier.width(112.dp)) }
            }
        }
    }
}

@Composable
private fun PlayersBlock(players: GameMeta.Players) {
    val recent = players.recent
    val peak = players.peak
    if (recent == null && peak == null) return
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(stringResource(Res.string.game_page_section_players))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
            // Display-only stat chips: clear the (no-op) button semantics so TalkBack reads them as text.
            recent?.let {
                val label = stringResource(Res.string.game_page_players_recent, formatCount(it))
                AssistChip(onClick = {}, modifier = Modifier.clearAndSetSemantics { contentDescription = label }, label = { Text(label) })
            }
            peak?.let {
                val label = stringResource(Res.string.game_page_players_peak, formatCount(it))
                AssistChip(onClick = {}, modifier = Modifier.clearAndSetSemantics { contentDescription = label }, label = { Text(label) })
            }
        }
    }
}

/** Storefront/critic review scores (Steam %, Metacritic /100, …) from ITAD `reviews`, one card each. */
@Composable
private fun ReviewsBlock(reviews: List<GameMeta.Review>) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(stringResource(Res.string.game_page_section_reviews))
        reviews.forEach { ReviewCard(it) }
    }
}

/** A positive/negative split-bar review card: 👍 score% … negative% 👎, a proportional bar, source + count. */
@Composable
private fun ReviewCard(review: GameMeta.Review) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            val positive = review.score?.coerceIn(0, 100)
            if (positive != null) {
                val negative = 100 - positive
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = ReviewPositiveColor, modifier = Modifier.size(16.dp))
                    Text(
                        text = "$positive%",
                        modifier = Modifier.padding(start = GameDealsCustomTheme.spacing.extraSmall),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ReviewPositiveColor,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "$negative%",
                        modifier = Modifier.padding(end = GameDealsCustomTheme.spacing.extraSmall),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Icon(Icons.Filled.ThumbDown, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
                ) {
                    if (positive > 0) Box(Modifier.weight(positive.toFloat()).fillMaxHeight().background(ReviewPositiveColor))
                    if (negative > 0) Box(Modifier.weight(negative.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.error))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = review.source, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                review.count?.let {
                    Text(
                        text = stringResource(Res.string.game_page_review_count, formatCount(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Green used for the positive share of review bars/labels; reads on both light and dark surfaces. */
private val ReviewPositiveColor = Color(0xFF43A047)

@Composable
private fun RatingsRow(game: IgdbGame, onClick: (() -> Unit)? = null) {
    val user = game.rating?.toInt()
    val critic = game.aggregatedRating?.toInt()
    if (user == null && critic == null) return
    val rowModifier = if (onClick != null) Modifier.clickable(role = Role.Button) { onClick() } else Modifier
    Row(modifier = rowModifier, horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium)) {
        if (user != null) RatingPill(stringResource(Res.string.game_details_user_rating_label), user, game.ratingCount)
        if (critic != null) RatingPill(stringResource(Res.string.game_details_critic_rating_label), critic, game.aggregatedRatingCount)
    }
}

@Composable
private fun RatingPill(label: String, value: Int, count: Long?) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(GameDealsCustomTheme.spacing.small))
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = "$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count != null) Text(text = "($count)", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StoreGameDealRow(
    gameId: String?,
    store: Store,
    gameInfo: GameDetails.GameInfo,
    deal: GameDetails.GameDeal,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onShareDeal: (GameDetails.GameInfo, Store, GameDetails.GameDeal) -> Unit,
) {
    // Records the click-through to a store before the in-app browser opens.
    val analytics: Analytics = koinInject()
    val rowCd = stringResource(Res.string.game_screen_store_deal_row_description, store.storeName, deal.savings, deal.priceDenominated)
    Card(onClick = { openStoreDeal(analytics, gameId, store, deal, gameInfo.title, goToWeb) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = rowCd },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoreIcon(storeName = store.storeName, iconUrl = store.iconUrl, iconSize = GameDealsCustomTheme.spacing.large, contentDescription = null)
                Text(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.medium),
                    text = store.storeName,
                )
                Text(
                    modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                    text = stringResource(Res.string.game_screen_list_item_savings_label, deal.savings),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
                        .padding(GameDealsCustomTheme.spacing.medium),
                    text = deal.priceDenominated,
                )
            }
            IconButton(onClick = { onShareDeal(gameInfo, store, deal) }) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = stringResource(Res.string.game_screen_share_action, store.storeName))
            }
        }
    }
}

@Composable
private fun DescriptionSection(game: IgdbGame) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large)) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            SectionHeader(stringResource(Res.string.game_details_section_description))
            game.summary?.let { CollapsibleParagraph(it) }
            game.storyline?.let {
                Spacer(modifier = Modifier.height(GameDealsCustomTheme.spacing.small))
                SectionHeader(stringResource(Res.string.game_details_section_storyline))
                CollapsibleParagraph(it)
            }
        }
    }
}

@Composable
private fun CollapsibleParagraph(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var hasOverflow by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout -> if (!expanded) hasOverflow = layout.hasVisualOverflow },
    )
    if (hasOverflow || expanded) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.CenterEnd)) {
            Text(text = stringResource(if (expanded) Res.string.game_screen_summary_show_less else Res.string.game_screen_summary_read_more))
        }
    }
}

@Composable
private fun SimilarGamesSection(games: List<IgdbGame.IgdbSimilarGame>, onSimilarGameClick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(stringResource(Res.string.game_details_section_similar), Modifier.padding(horizontal = GameDealsCustomTheme.spacing.large))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            items(games, key = { it.id }) { similar -> IgdbGameTile(similar, onSimilarGameClick, Modifier.width(112.dp)) }
        }
    }
}

@Composable
private fun IgdbGameTile(game: IgdbGame.IgdbSimilarGame, onClick: (Long) -> Unit, modifier: Modifier = Modifier, isCurrent: Boolean = false) {
    val rowCd = if (isCurrent) stringResource(Res.string.game_details_title_match_picker_current_tile_cd, game.name)
    else stringResource(Res.string.game_details_similar_game_row_description, game.name)
    val borderModifier = if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(GameDealsCustomTheme.spacing.small)) else Modifier
    Column(
        modifier = modifier.clickable(role = Role.Button) { onClick(game.id) }.semantics(mergeDescendants = true) { contentDescription = rowCd },
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(GameDealsCustomTheme.spacing.small))
                .then(borderModifier),
        ) {
            game.coverImageId?.let { imageId ->
                AsyncImage(
                    model = igdbImageUrl(imageId, IgdbImageSize.CoverBig),
                    contentDescription = stringResource(Res.string.game_details_cover_image_cd, game.name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(text = game.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinksSection(websites: List<WebsiteUiModel>) {
    val uriHandler = LocalUriHandler.current
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large)) {
        Column(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            SectionHeader(stringResource(Res.string.game_details_section_links))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
                websites.forEach { site ->
                    AssistChip(
                        onClick = { uriHandler.openUri(site.url) },
                        label = { Text(site.category.name) },
                        leadingIcon = site.faviconUrl?.let { faviconUrl ->
                            {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(faviconUrl)
                                        .memoryCacheKey(site.faviconCacheKey)
                                        .diskCacheKey(site.faviconCacheKey)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(AssistChipDefaults.IconSize)
                                        .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEditDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.game_screen_note_dialog_title)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.game_screen_note_dialog_label)) },
                placeholder = { Text(stringResource(Res.string.game_screen_note_dialog_placeholder)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(Res.string.game_screen_note_dialog_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.game_screen_note_dialog_cancel)) } },
    )
}

@Composable
private fun NoMatchSection(modifier: Modifier, title: String, onSearch: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(GameDealsCustomTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium)) {
                Text(modifier = Modifier.semantics { heading() }, text = stringResource(Res.string.game_details_no_match_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = stringResource(Res.string.game_details_no_match_message, title), style = MaterialTheme.typography.bodyMedium)
            }
        }
        FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = onSearch) { Text(stringResource(Res.string.game_details_no_match_search_button)) }
        TextButton(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text(stringResource(Res.string.game_details_no_match_back_button)) }
    }
}

@Composable
private fun BundlesSection(bundles: List<Bundle>, onBundleClick: (bundleId: Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(GameDealsCustomTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
            SectionHeader(stringResource(Res.string.game_page_section_bundles))
            bundles.forEachIndexed { index, bundle ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onBundleClick(bundle.id) }.padding(vertical = GameDealsCustomTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = bundle.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${bundle.storeName} · ${stringResource(Res.string.game_page_bundle_games_count, bundle.gameCount.toString())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    bundle.priceDenominated?.let { Text(text = it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun DlcsSection(dlcs: List<IgdbGame.IgdbSimilarGame>, onDlcClick: (igdbGameId: Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        SectionHeader(stringResource(Res.string.game_page_section_dlcs), Modifier.padding(horizontal = GameDealsCustomTheme.spacing.large))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            items(dlcs, key = { it.id }) { dlc -> IgdbGameTile(dlc, onDlcClick, Modifier.width(112.dp)) }
        }
    }
}

/** Seconds → nearest whole hour, as a string (HowLongToBeat values are coarse, so whole hours read fine). */
private fun hoursFromSeconds(seconds: Long): String = ((seconds + 1800) / 3600).toString()

/** KMP-safe thousands grouping for player/review counts (no java.text on common). */
private fun formatCount(value: Int): String {
    val digits = value.toString()
    val sb = StringBuilder()
    for (i in digits.indices) {
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(digits[i])
    }
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidatePickerSheet(
    data: GamePageData.Data,
    onDismiss: () -> Unit,
    onCandidatePicked: (igdbGameId: Long) -> Unit,
    onRetry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            Text(text = stringResource(Res.string.game_details_title_match_picker_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = stringResource(Res.string.game_details_title_match_picker_explanation), style = MaterialTheme.typography.bodyMedium)
            when (val state = data.candidatesState) {
                GamePageViewModel.CandidatesState.Idle,
                GamePageViewModel.CandidatesState.Loading -> {
                    val loadingCd = stringResource(Res.string.game_details_title_match_picker_loading_cd)
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).semantics { contentDescription = loadingCd })
                    }
                }
                is GamePageViewModel.CandidatesState.Loaded -> {
                    if (state.items.isEmpty()) {
                        Text(text = stringResource(Res.string.game_details_title_match_picker_empty))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                            modifier = Modifier.fillMaxWidth().height(420.dp),
                        ) {
                            gridItems(state.items, key = { it.id }) { candidate ->
                                IgdbGameTile(game = candidate, onClick = onCandidatePicked, isCurrent = candidate.id == data.igdbGameOrNull?.id)
                            }
                        }
                    }
                }
                GamePageViewModel.CandidatesState.Error -> {
                    Text(text = stringResource(Res.string.game_details_title_match_picker_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.game_details_title_match_picker_retry)) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.game_details_title_match_picker_close)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(modifier = modifier.semantics { heading() }, text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/** Short "nothing here" line for an empty tab/section — matches the Regions tab's empty state. */
@Composable
private fun TabEmpty(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.politeLiveRegion(),
    )
}

/** "Couldn't load" line + a Retry button for a tab/section whose fetch failed (re-fetches that facet only). */
@Composable
private fun TabError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        Text(
            text = stringResource(Res.string.game_page_section_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.politeLiveRegion(),
        )
        TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(Res.string.game_screen_data_loading_error_retry))
        }
    }
}

/** Spinner shown while a tab/section is re-fetching after a Retry (initial load resolves before the tab renders). */
@Composable
private fun TabLoading(modifier: Modifier = Modifier) {
    val loadingCd = stringResource(Res.string.game_page_section_loading_cd)
    Box(modifier = modifier.fillMaxWidth().height(120.dp)) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).semantics { contentDescription = loadingCd })
    }
}

private val MONTH_ABBREV = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun formatReleaseDate(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.UTC).date
    return "${MONTH_ABBREV[date.month.ordinal]} ${date.dayOfMonth}, ${date.year}"
}

@Composable
private fun GamePagePreview(data: GamePageData) {
    GameDealsTheme {
        GamePageContent(
            data = data,
            isFavourite = false,
            isCollected = false,
            isIgnored = false,
            note = null,
            followedFranchiseIds = emptySet(),
            onBack = {},
            goToWeb = { _, _ -> },
            onSimilarGameClick = {},
            onSearchDealsByTitle = {},
            onShareDeal = { _, _, _ -> },
            onToggleFollowFranchise = { _, _ -> },
            onToggleFavourite = {},
            onToggleCollection = {},
            onToggleIgnore = {},
            onSaveNote = {},
            onDeleteNote = {},
            onRetry = {},
            onWarningTap = {},
            onPickerDismiss = {},
            onCandidatePicked = {},
            onBundleClick = {},
            onRegionsSelected = {},
            onRetrySection = {},
        )
    }
}

@Preview
@Composable
private fun GamePage_Loading_Preview() {
    GamePagePreview(GamePageData.Loading)
}

@Preview
@Composable
private fun GamePage_NoMatch_Preview() {
    GamePagePreview(GamePageData.NoMatch(title = "Some Obscure Game"))
}

@Preview
@Composable
private fun GamePage_Data_Preview() {
    GamePagePreview(GamePageData.Data(title = "Hades"))
}
