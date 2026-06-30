package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import pm.bam.gamedeals.common.navigation.SignInPromptController
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.deal.GamePeekSheet
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_collection_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_collection_sort_recently_added
import pm.bam.gamedeals.feature.account.generated.resources.account_collection_sort_title
import pm.bam.gamedeals.feature.account.generated.resources.account_list_loading
import pm.bam.gamedeals.feature.account.generated.resources.account_list_refresh_failed
import pm.bam.gamedeals.feature.account.generated.resources.account_list_sort
import pm.bam.gamedeals.feature.account.generated.resources.account_navigation_back
import pm.bam.gamedeals.feature.account.generated.resources.account_section_collection
import pm.bam.gamedeals.feature.account.generated.resources.account_section_waitlist
import pm.bam.gamedeals.feature.account.generated.resources.account_type_dlc
import pm.bam.gamedeals.feature.account.generated.resources.account_type_game
import pm.bam.gamedeals.feature.account.generated.resources.account_type_package
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_empty
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_lowest_ever
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_no_deal
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_sort_biggest_discount
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_sort_now_lowest
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_sort_price_low_high
import pm.bam.gamedeals.feature.account.generated.resources.account_waitlist_sort_recently_added
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * A single game row in a library list (#274). Retained for the Ignored sub-screen, which shares this minimal
 * shape; Waitlist/Collection now use their own enriched row models ([WaitlistRowUi]/[CollectionRowUi]).
 */
@Immutable
internal data class GameListItem(
    val gameId: String,
    val title: String,
    val boxart: String?,
)

@Immutable
internal data class GameListState(
    val loading: Boolean = false,
    val items: ImmutableList<GameListItem> = persistentListOf(),
)

@Composable
internal fun WaitlistListScreen(
    onBack: () -> Unit,
    goToGame: (gameId: String) -> Unit,
    goToWeb: (url: String) -> Unit,
    viewModel: WaitlistListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sortOptions = listOf(
        WaitlistSort.RECENTLY_ADDED to stringResource(Res.string.account_waitlist_sort_recently_added),
        WaitlistSort.BIGGEST_DISCOUNT to stringResource(Res.string.account_waitlist_sort_biggest_discount),
        WaitlistSort.PRICE_LOW_HIGH to stringResource(Res.string.account_waitlist_sort_price_low_high),
        WaitlistSort.NOW_AT_LOWEST to stringResource(Res.string.account_waitlist_sort_now_lowest),
    )
    LibraryPeekHost(peek = viewModel.peek, goToGame = goToGame, goToWeb = goToWeb) {
        LibraryScaffold(
            title = stringResource(Res.string.account_section_waitlist),
            loading = state.loading,
            isEmpty = !state.loading && state.rows.isEmpty(),
            emptyText = stringResource(Res.string.account_waitlist_empty),
            refreshFailed = state.refreshFailed,
            onBack = onBack,
            actions = { SortMenu(sortOptions, state.sort, viewModel::setSort) },
        ) {
            itemsIndexed(state.rows, key = { _, row -> row.gameId }) { index, row ->
                WaitlistRow(row) { viewModel.peek.peek(row.gameId, row.title, row.imageUrl) }
                if (index < state.rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun CollectionListScreen(
    onBack: () -> Unit,
    goToGame: (gameId: String) -> Unit,
    goToWeb: (url: String) -> Unit,
    viewModel: CollectionListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sortOptions = listOf(
        CollectionSort.RECENTLY_ADDED to stringResource(Res.string.account_collection_sort_recently_added),
        CollectionSort.TITLE_AZ to stringResource(Res.string.account_collection_sort_title),
    )
    LibraryPeekHost(peek = viewModel.peek, goToGame = goToGame, goToWeb = goToWeb) {
        LibraryScaffold(
            title = stringResource(Res.string.account_section_collection),
            loading = state.loading,
            isEmpty = !state.loading && state.rows.isEmpty(),
            emptyText = stringResource(Res.string.account_collection_empty),
            refreshFailed = state.refreshFailed,
            onBack = onBack,
            actions = { SortMenu(sortOptions, state.sort, viewModel::setSort) },
        ) {
            itemsIndexed(state.rows, key = { _, row -> row.gameId }) { index, row ->
                CollectionRow(row) { viewModel.peek.peek(row.gameId, row.title, row.imageUrl) }
                if (index < state.rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

/**
 * Hosts the shared game-centric peek sheet over a library list: collects the peek state + the
 * waitlist/collection/ignore id sets, wires the sheet's toggles/share/retry through the [peek] delegate, and
 * routes the sheet's "View game page" / store opens to [goToGame] / [goToWeb]. Same UX as Home/Deals.
 */
@Composable
private fun LibraryPeekHost(
    peek: GamePeekDelegate,
    goToGame: (gameId: String) -> Unit,
    goToWeb: (url: String) -> Unit,
    content: @Composable () -> Unit,
) {
    val peekData by peek.data.collectAsStateWithLifecycle()
    val waitlistIds by peek.waitlistIds.collectAsStateWithLifecycle()
    val collectionIds by peek.collectionIds.collectAsStateWithLifecycle()
    val ignoredIds by peek.ignoredIds.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current

    SingleEventEffect(peek.events) { event ->
        when (event) {
            GamePeekEvent.SignInRequired -> SignInPromptController.request()
            is GamePeekEvent.ShareDeal -> platformActions.share(event.text)
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        val peekGameId = peekData?.gameId?.takeIf { it.isNotEmpty() }
        GamePeekSheet(
            data = peekData,
            isWaitlisted = peekGameId?.let { it in waitlistIds } == true,
            isCollected = peekGameId?.let { it in collectionIds } == true,
            isIgnored = peekGameId?.let { it in ignoredIds } == true,
            onDismiss = { peek.dismiss() },
            onShare = { peek.share(it) },
            onToggleWaitlist = { peek.toggleWaitlist(it) },
            onToggleCollection = { peek.toggleCollection(it) },
            onToggleIgnore = { peek.toggleIgnore(it) },
            goToWeb = { url, _ -> goToWeb(url) },
            onViewGamePage = { goToGame(it.gameId) },
            onRetry = { peek.retry() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScaffold(
    title: String,
    loading: Boolean,
    isEmpty: Boolean,
    emptyText: String,
    refreshFailed: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.account_navigation_back),
                            )
                        }
                    },
                    actions = actions,
                )
            },
        ) { innerPadding: PaddingValues ->
            when {
                loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    val loadingLabel = stringResource(Res.string.account_list_loading)
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loadingLabel })
                }

                isEmpty -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(text = emptyText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.politeLiveRegion())
                }

                else -> Column(Modifier.fillMaxSize().padding(innerPadding)) {
                    if (refreshFailed) RefreshFailedBanner()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
                        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun RefreshFailedBanner() {
    val message = stringResource(Res.string.account_list_refresh_failed)
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .politeLiveRegion()
                .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small),
        )
    }
}

@Composable
private fun <T> SortMenu(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(Res.string.account_list_sort),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    trailingIcon = {
                        if (value == selected) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}

@Composable
private fun WaitlistRow(row: WaitlistRowUi, onClick: () -> Unit) {
    val ago = addedAgoLabel(row.addedEpochMs, AgoPrefix.WAITLISTED)
    val lowestEver = stringResource(Res.string.account_waitlist_lowest_ever)
    val noDeal = stringResource(Res.string.account_waitlist_no_deal)
    Column(Modifier.fillMaxWidth()) {
        DealListRow(
            title = row.title,
            contentDescription = waitlistContentDescription(row, ago, lowestEver, noDeal),
            onClick = onClick,
            imageUrl = row.imageUrl,
            salePrice = row.salePrice,
            regularPrice = row.regularPrice,
            neutralChip = noDeal.takeIf { row.salePrice == null },
            discountPercent = row.discountPercent,
            hasVoucher = row.hasVoucher,
            isNewHistoricalLow = row.isNewHistoricalLow,
            isStoreLow = row.isStoreLow,
            storeName = row.storeName,
            storeIconUrl = row.storeIconUrl,
            isWaitlisted = true,
        )
        if (row.isAtHistoricalLow || ago != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = GameDealsCustomTheme.spacing.medium, bottom = GameDealsCustomTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                if (row.isAtHistoricalLow) LowestEverChip(lowestEver)
                ago?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LowestEverChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = GameDealsCustomTheme.spacing.small,
                vertical = GameDealsCustomTheme.spacing.extraSmall,
            ),
        )
    }
}

/** Flat, non-composable TalkBack string from already-resolved pieces. */
private fun waitlistContentDescription(row: WaitlistRowUi, ago: String?, lowestEver: String, noDeal: String): String {
    val parts = mutableListOf(row.title)
    if (row.salePrice != null) {
        parts += row.salePrice
        if (row.discountPercent > 0) parts += "-${row.discountPercent}%"
    } else {
        parts += noDeal
    }
    if (row.isAtHistoricalLow) parts += lowestEver
    ago?.let { parts += it }
    return parts.joinToString(", ")
}

@Composable
private fun CollectionRow(row: CollectionRowUi, onClick: () -> Unit) {
    val ago = addedAgoLabel(row.addedEpochMs, AgoPrefix.ADDED)
    val type = typeLabel(row.type)
    val secondary = listOfNotNull(ago, type).joinToString(" · ").ifBlank { null }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            // Merge the title + secondary line into one TalkBack node; the image is decorative.
            .semantics(mergeDescendants = true) {}
            .padding(vertical = GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = row.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .height(60.dp)
                .width(100.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = GameDealsCustomTheme.spacing.medium),
        ) {
            Text(text = row.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            secondary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun typeLabel(type: String?): String? = when (type?.lowercase()) {
    "game" -> stringResource(Res.string.account_type_game)
    "dlc" -> stringResource(Res.string.account_type_dlc)
    "package" -> stringResource(Res.string.account_type_package)
    null, "" -> null
    else -> type.replaceFirstChar { it.uppercase() }
}

private val previewWaitlistRows = persistentListOf(
    WaitlistRowUi(
        gameId = "g1", title = "Surviving Mars", imageUrl = null, addedEpochMs = 0L,
        salePrice = "€9.99", regularPrice = "€39.99", discountPercent = 75, storeName = "Steam",
        storeIconUrl = null, hasVoucher = false, isNewHistoricalLow = false, isStoreLow = false,
        isAtHistoricalLow = true, bestPriceValue = 9.99,
    ),
    WaitlistRowUi(
        gameId = "g2", title = "Take On Mars", imageUrl = null, addedEpochMs = 0L,
        salePrice = null, regularPrice = null, discountPercent = 0, storeName = null,
        storeIconUrl = null, hasVoucher = false, isNewHistoricalLow = false, isStoreLow = false,
        isAtHistoricalLow = false, bestPriceValue = null,
    ),
)

private val previewCollectionRows = persistentListOf(
    CollectionRowUi(gameId = "g1", title = "Hades", imageUrl = null, addedEpochMs = 0L, type = "game"),
    CollectionRowUi(gameId = "g2", title = "Stardew Valley", imageUrl = null, addedEpochMs = 0L, type = "dlc"),
)

@Preview
@Composable
private fun WaitlistScaffoldPreview() {
    GameDealsTheme {
        LibraryScaffold(
            title = "Waitlist",
            loading = false,
            isEmpty = false,
            emptyText = "Your waitlist is empty.",
            refreshFailed = false,
            onBack = {},
            actions = {},
        ) {
            itemsIndexed(previewWaitlistRows, key = { _, row -> row.gameId }) { index, row ->
                WaitlistRow(row) {}
                if (index < previewWaitlistRows.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Preview
@Composable
private fun CollectionScaffoldPreview() {
    GameDealsTheme {
        LibraryScaffold(
            title = "Collection",
            loading = false,
            isEmpty = false,
            emptyText = "Your collection is empty.",
            refreshFailed = true,
            onBack = {},
            actions = {},
        ) {
            itemsIndexed(previewCollectionRows, key = { _, row -> row.gameId }) { index, row ->
                CollectionRow(row) {}
                if (index < previewCollectionRows.lastIndex) HorizontalDivider()
            }
        }
    }
}
