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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.components.StoreIcon
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_ever_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_on_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_msg
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_retry
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_game_image
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_go_to_deal_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_loading_indicator
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_view_game_page_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_ignore_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_ignore_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_content_description
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_best_price_at_store
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_no_deals_label
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_other_stores_label
import pm.bam.gamedeals.common.ui.generated.resources.game_peek_store_row_description
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * The game-centric peek sheet (row-consolidation work): one quick-peek surface opened from every
 * game/deal row across Home and Deals. Shows the game's best current price + the other stores, with
 * waitlist / ignore / share actions and a "View game page" button into the full unified Game Page.
 * Replaces the deal-centric `DealBottomSheet`. Driven by [GamePeekController].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamePeekSheet(
    data: GamePeekSheetData?,
    isWaitlisted: Boolean = false,
    isIgnored: Boolean = false,
    onDismiss: () -> Unit,
    onShare: (data: GamePeekSheetData.Data) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit = {},
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
            PeekContent(data, isWaitlisted, isIgnored, onShare, onToggleWaitlist, onToggleIgnore, goToWeb, onViewGamePage, onRetry)
        }
    }
}

@Composable
private fun PeekContent(
    data: GamePeekSheetData,
    isWaitlisted: Boolean,
    isIgnored: Boolean,
    onShare: (data: GamePeekSheetData.Data) -> Unit,
    onToggleWaitlist: (gameId: String) -> Unit,
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
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.small),
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
            Column(modifier = Modifier.weight(1f).padding(horizontal = GameDealsCustomTheme.spacing.small)) {
                Text(text = data.gameName, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(enabled = canAct, onClick = { onToggleWaitlist(data.gameId) }) {
                AnimatedContent(targetState = isWaitlisted, label = "favourite-icon") { fav ->
                    Icon(
                        imageVector = if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(if (fav) Res.string.deal_favourite_remove_action else Res.string.deal_favourite_add_action),
                    )
                }
            }
            IconButton(enabled = canAct, onClick = { onToggleIgnore(data.gameId) }) {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    tint = if (isIgnored) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = stringResource(if (isIgnored) Res.string.deal_ignore_remove_action else Res.string.deal_ignore_add_action),
                )
            }
            IconButton(enabled = bestDeal != null, onClick = { asData?.let(onShare) }) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = stringResource(Res.string.deal_share_content_description))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        data.bestDeal?.let { best ->
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { goToWeb(best.deal.url, data.gameName) },
            ) {
                Text(text = stringResource(Res.string.deal_details_go_to_deal_label))
            }
        }

        if (data.otherStores.isNotEmpty()) {
            Text(text = stringResource(Res.string.game_peek_other_stores_label), style = MaterialTheme.typography.titleSmall)
            data.otherStores.forEach { pair ->
                val rowCd = stringResource(Res.string.game_peek_store_row_description, pair.deal.priceDenominated, pair.store.storeName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { goToWeb(pair.deal.url, data.gameName) }
                        .semantics(mergeDescendants = true) { contentDescription = rowCd },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
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
            Text(
                text = stringResource(Res.string.deal_details_cheapest_ever_label) + stringResource(Res.string.deal_details_cheapest_on_label, it.priceDenominated, it.date),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onViewGamePage(data) },
        ) {
            Text(text = stringResource(Res.string.deal_details_view_game_page_label))
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
        Text(text = stringResource(Res.string.deal_details_data_loading_error_msg))
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
            PeekContent(previewPeekData, false, false, {}, {}, {}, { _, _ -> }, {}, {})
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
                false, false, {}, {}, {}, { _, _ -> }, {}, {},
            )
        }
    }
}
