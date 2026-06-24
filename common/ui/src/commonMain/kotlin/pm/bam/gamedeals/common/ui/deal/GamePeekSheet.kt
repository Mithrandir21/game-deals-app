package pm.bam.gamedeals.common.ui.deal

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.koinInject
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.a11y.politeLiveRegion
import pm.bam.gamedeals.common.ui.components.StoreIcon
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_ever_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_msg
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_retry
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_game_image
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_go_to_deal_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_loading_indicator
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_view_game_page_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_collection_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_collection_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_ignore_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_ignore_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_label
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_more_actions
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_best_price_at_store
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_no_deals_label
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_other_stores_label
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_store_row_description
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * The game-centric peek sheet (row-consolidation work): one quick-peek surface opened from every
 * game/deal row across Home and Deals. Shows the game's best current price + the other stores, with
 * waitlist / ignore / share actions and a "View game page" button into the full unified Game Page.
 * Driven by [GamePeekController].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamePeekSheet(
    data: GamePeekSheetData?,
    isWaitlisted: Boolean = false,
    isCollected: Boolean = false,
    isIgnored: Boolean = false,
    onDismiss: () -> Unit,
    onShare: (data: GamePeekSheetData.Data) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit = {},
    onToggleCollection: (gameId: String) -> Unit = {},
    onToggleIgnore: (gameId: String) -> Unit = {},
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onViewGamePage: (data: GamePeekSheetData.Data) -> Unit,
    onRetry: () -> Unit,
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (data != null) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = modalBottomSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            PeekContent(data, isWaitlisted, isCollected, isIgnored, onShare, onToggleWaitlist, onToggleCollection, onToggleIgnore, goToWeb, onViewGamePage, onRetry)
        }
    }
}

@Composable
private fun PeekContent(
    data: GamePeekSheetData,
    isWaitlisted: Boolean,
    isCollected: Boolean,
    isIgnored: Boolean,
    onShare: (data: GamePeekSheetData.Data) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit,
    onToggleCollection: (gameId: String) -> Unit,
    onToggleIgnore: (gameId: String) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onViewGamePage: (data: GamePeekSheetData.Data) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding(),
    ) {
        // Header: thumbnail + title + best-price subtitle, with waitlist / ignore / share actions.
        val asData = data as? GamePeekSheetData.Data
        val bestDeal = asData?.bestDeal
        val subtitle = when {
            bestDeal != null -> stringResource(Res.string.game_peek_best_price_at_store, bestDeal.deal.priceDenominated, bestDeal.store.storeName)
            asData?.upcoming == true -> stringResource(Res.string.game_peek_no_deals_label)
            else -> ""
        }
        // Waitlist / ignore act on a real ITAD id; disabled for the "upcoming" state that never resolved one.
        val canAct = asData != null && data.gameId.isNotEmpty()
        Row(
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = data.thumb,
                contentDescription = stringResource(Res.string.deal_details_game_image, data.gameName),
                contentScale = ContentScale.Crop,
                error = painterResource(Res.drawable.videogame_thumb),
                modifier = Modifier
                    .padding(GameDealsCustomTheme.spacing.small)
                    .width(80.dp)
                    .height(45.dp)
                    .clip(MaterialTheme.shapes.small),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = GameDealsCustomTheme.spacing.medium)) {
                Text(text = data.gameName, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Waitlist (bookmark) + Collection (library-check) are the two primary, distinct actions;
            // the less-used Share + Ignore fold into an overflow menu so four icons don't crowd the title.
            IconButton(enabled = canAct, onClick = { onToggleWaitlist(data.gameId) }) {
                AnimatedContent(targetState = isWaitlisted, label = "waitlist-icon") { waitlisted ->
                    Icon(
                        imageVector = if (waitlisted) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        tint = if (waitlisted) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        contentDescription = stringResource(if (waitlisted) Res.string.deal_favourite_remove_action else Res.string.deal_favourite_add_action),
                    )
                }
            }
            IconButton(enabled = canAct, onClick = { onToggleCollection(data.gameId) }) {
                AnimatedContent(targetState = isCollected, label = "collection-icon") { collected ->
                    Icon(
                        imageVector = if (collected) Icons.Filled.LibraryAddCheck else Icons.Outlined.LibraryAddCheck,
                        tint = if (collected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        contentDescription = stringResource(if (collected) Res.string.deal_collection_remove_action else Res.string.deal_collection_add_action),
                    )
                }
            }
            Box {
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.game_peek_more_actions))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.deal_share_label)) },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        enabled = bestDeal != null,
                        onClick = {
                            menuExpanded = false
                            asData?.let(onShare)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isIgnored) Res.string.deal_ignore_remove_action else Res.string.deal_ignore_add_action)) },
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
                            onToggleIgnore(data.gameId)
                        },
                    )
                }
            }
        }
        HorizontalDivider()
        when (data) {
            is GamePeekSheetData.Loading -> {
                val loadingCd = stringResource(Res.string.deal_details_loading_indicator)
                Box(modifier = Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.extraLarge)) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(120.dp)
                            .wrapContentSize(Alignment.Center)
                            .align(Alignment.Center)
                            .semantics { contentDescription = loadingCd },
                    )
                }
            }

            is GamePeekSheetData.Data -> PeekBody(data, goToWeb, onViewGamePage)
            is GamePeekSheetData.Error -> PeekError(onRetry)
        }
    }
}

@Composable
private fun PeekBody(
    data: GamePeekSheetData.Data,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onViewGamePage: (data: GamePeekSheetData.Data) -> Unit,
) {
    // Records the click-through to a store before the in-app browser opens. This is the single peek sheet
    // shown from Home/Deals/Discover/Bundle, so one capture point covers all those deal opens.
    val analytics: Analytics = koinInject()
    fun recordDealOpen(pair: StoreDealPair) = analytics.capture(
        AnalyticsEvents.DEAL_STORE_OPENED,
        mapOf(
            "game_id" to data.gameId,
            "store_id" to pair.store.storeID,
            "store_name" to pair.store.storeName,
            "discount_pct" to pair.deal.savings,
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        if (data.otherStores.isNotEmpty()) {
            Text(text = stringResource(Res.string.game_peek_other_stores_label), style = MaterialTheme.typography.titleSmall)
            data.otherStores.forEach { pair ->
                val rowCd = stringResource(Res.string.game_peek_store_row_description, pair.deal.priceDenominated, pair.store.storeName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { recordDealOpen(pair); goToWeb(pair.deal.url, data.gameName) }
                        .padding(vertical = GameDealsCustomTheme.spacing.extraSmall)
                        .semantics(mergeDescendants = true) { contentDescription = rowCd },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                ) {
                    StoreIcon(
                        storeName = pair.store.storeName,
                        iconUrl = pair.store.iconUrl,
                        iconSize = 28.dp,
                        color = LocalContentColor.current,
                        contentDescription = null,
                    )
                    Text(modifier = Modifier.weight(1f), text = pair.store.storeName)
                    Text(text = pair.deal.priceDenominated)
                }
            }
        }

        data.cheapestPriceEver?.let {
            // ITAD's prices endpoint doesn't return the low's date (it's mapped to ""), so show just the
            // price rather than a dangling "… on ".
            Text(
                text = stringResource(Res.string.deal_details_cheapest_ever_label) + it.priceDenominated,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Primary "Go to deal" + secondary "View game page" actions, side by side at the bottom of the sheet.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GameDealsCustomTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { onViewGamePage(data) },
            ) {
                Text(text = stringResource(Res.string.deal_details_view_game_page_label))
            }
            data.bestDeal?.let { best ->
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { recordDealOpen(best); goToWeb(best.deal.url, data.gameName) },
                ) {
                    Text(text = stringResource(Res.string.deal_details_go_to_deal_label))
                }
            }
        }
    }
}

@Composable
private fun PeekError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(horizontal = GameDealsCustomTheme.spacing.medium)
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Announce the error when it replaces the loading spinner in the already-open sheet.
        Text(
            text = stringResource(Res.string.deal_details_data_loading_error_msg),
            modifier = Modifier.politeLiveRegion(),
        )
        Button(
            modifier = Modifier.padding(vertical = GameDealsCustomTheme.spacing.large),
            onClick = onRetry,
        ) {
            Text(text = stringResource(Res.string.deal_details_data_loading_error_retry))
        }
    }
}

private val previewDeal = GameDetails.GameDeal(
    storeID = PreviewStore.storeID,
    dealID = "preview-deal-1",
    priceValue = 18.86,
    priceDenominated = "18,86 €",
    retailPriceValue = 58.99,
    retailPriceDenominated = "58,99 €",
    savings = 68,
    url = "https://example.com/deal",
)

private val previewPeekData = GamePeekSheetData.Data(
    gameId = "123",
    gameName = "No Man's Sky",
    thumb = "https://example.com/nms.jpg",
    bestDeal = StoreDealPair(store = PreviewStore, deal = previewDeal),
    otherStores = persistentListOf(
        StoreDealPair(store = PreviewStore.copy(storeID = 7, storeName = "GOG"), deal = previewDeal.copy(dealID = "d2", priceDenominated = "21,99 €")),
    ),
    cheapestPriceEver = GameDetails.GameCheapestPriceEver(priceValue = 14.99, priceDenominated = "14,99 €", date = "2023-11-24"),
    upcoming = false,
)

@Preview
@Composable
private fun GamePeekSheet_Data_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PeekContent(previewPeekData, false, false, false, {}, {}, {}, {}, { _, _ -> }, {}, {})
        }
    }
}

@Preview
@Composable
private fun GamePeekSheet_Upcoming_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PeekContent(
                previewPeekData.copy(bestDeal = null, otherStores = persistentListOf(), cheapestPriceEver = null, upcoming = true),
                false, false, false, {}, {}, {}, {}, { _, _ -> }, {}, {},
            )
        }
    }
}
