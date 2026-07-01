@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.DiscountBadge
import pm.bam.gamedeals.common.ui.components.PriceBlock
import pm.bam.gamedeals.common.ui.components.StoreIcon
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.DealQuality
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.domain.models.portrait
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_back_button
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_message
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_search_button
import pm.bam.gamedeals.feature.game.generated.resources.game_details_no_match_title
import pm.bam.gamedeals.feature.game.generated.resources.game_details_released_label
import pm.bam.gamedeals.feature.game.generated.resources.game_page_deal_quality_all_time_low_title
import pm.bam.gamedeals.feature.game.generated.resources.game_page_deal_quality_elevated_title
import pm.bam.gamedeals.feature.game.generated.resources.game_page_deal_quality_near_low_title
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_warning_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_page_buy_box_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_page_buy_box_open
import pm.bam.gamedeals.feature.game.generated.resources.game_page_buy_box_store
import pm.bam.gamedeals.feature.game.generated.resources.game_page_tab_about
import pm.bam.gamedeals.feature.game.generated.resources.game_page_tab_community
import pm.bam.gamedeals.feature.game.generated.resources.game_page_tab_deal
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_game_image
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_loading_indicator
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_navigation_back_button
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_add_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_edit_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_store_deal_row_description
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Which facet a tab's "Retry" re-fetches — threaded down to the tabs and dispatched to the VM at the top. */
internal enum class RetrySection { Deals, PriceHistory, GameMeta, Igdb }

/** Height of the condensed price bar that fades in as the hero collapses. */
private val STICKY_BAR_HEIGHT = 56.dp

/**
 * The unified Game Page (epic #291), redesigned into three intent-based tabs — **Deal**, **About** and
 * **Community** — under a collapsing hero whose "buy box" (best price + discount + store + all-time-low) is
 * the single source of price truth. As the hero scrolls away a condensed price bar fades in so the best
 * deal stays one tap away from any tab. Reuses the standalone [PriceHistoryChart] and [ScreenshotViewerDialog]
 * plus shared [PriceBlock]/[DiscountBadge] components. The three tab bodies live in the sibling
 * GamePage{Deal,About,Community}Tab files, with shared section helpers in GamePageCommon.
 */
@Composable
internal fun GamePageScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onSimilarGameClick: (igdbGameId: Long) -> Unit = {},
    onSearchDealsByTitle: (title: String) -> Unit = {},
    onBundleClick: (bundleId: Int) -> Unit = {},
    // Lets an embedding host (e.g. the Deals tablet detail pane) control the back arrow independently of
    // the personal actions (waitlist/collection/note), which always stay.
    showBackButton: Boolean = true,
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
        showBackButton = showBackButton,
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
    showBackButton: Boolean = true,
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
                        if (showBackButton) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.game_screen_navigation_back_button),
                                )
                            }
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
internal fun openStoreDeal(
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
