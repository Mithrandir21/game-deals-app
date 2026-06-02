@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.common.ui.deal

import androidx.compose.animation.AnimatedContent
import kotlinx.collections.immutable.persistentListOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.PreviewDealCheaperStore
import pm.bam.gamedeals.common.ui.PreviewDealCheapestPrice
import pm.bam.gamedeals.common.ui.PreviewDealGameInfo
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_remove_action
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_content_description
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheaper_store_row_description
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheaper_store_thumbnail
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_ever_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_no
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_on_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_store_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_yes
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_msg
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_retry
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_game_image
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_header_description
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_go_to_deal_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_loading_indicator
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_metacritic_score_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_release_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_steam_reviews_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_steamworks_label_no
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_steamworks_label_yes
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_title_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_view_game_details_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_wiki_label
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealBottomSheet(
    data: DealBottomSheetData?,
    isFavourite: Boolean = false,
    onDismiss: () -> Unit,
    onShare: (data: DealBottomSheetData) -> Unit,
    onToggleFavourite: (data: DealBottomSheetData.DealDetailsData) -> Unit = {},
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
    onRetryDealDetails: () -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (data != null) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = modalBottomSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            DealContent(data, isFavourite, onShare, onToggleFavourite, goToWeb, goToGameDetails, goToGameDetailsByTitle, onRetryDealDetails)
        }
    }
}

@Composable
private fun DealContent(
    data: DealBottomSheetData,
    isFavourite: Boolean,
    onShare: (data: DealBottomSheetData) -> Unit,
    onToggleFavourite: (data: DealBottomSheetData.DealDetailsData) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
    retry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
    ) {
        val headerCd = stringResource(
            Res.string.deal_details_header_description,
            data.store.storeName,
            data.gameSalesPriceDenominated,
            data.gameName,
        )
        Row(
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = headerCd },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = data.store.images.logo,
                    contentDescription = null,
                    error = painterResource(Res.drawable.videogame_thumb),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(GameDealsCustomTheme.spacing.small)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GameDealsCustomTheme.spacing.small),
                        text = stringResource(Res.string.deal_details_title_label, data.store.storeName, data.gameSalesPriceDenominated),
                    )
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = GameDealsCustomTheme.spacing.small),
                        text = data.gameName
                    )
                }
            }
            IconButton(
                enabled = data is DealBottomSheetData.DealDetailsData,
                onClick = { (data as? DealBottomSheetData.DealDetailsData)?.let(onToggleFavourite) },
            ) {
                AnimatedContent(targetState = isFavourite, label = "favourite-icon") { fav ->
                    Icon(
                        imageVector = if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(
                            if (fav) Res.string.deal_favourite_remove_action
                            else Res.string.deal_favourite_add_action
                        ),
                    )
                }
            }
            IconButton(onClick = { onShare(data) }) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(Res.string.deal_share_content_description)
                )
            }
        }
        HorizontalDivider()
        when (data) {
            is DealBottomSheetData.DealDetailsLoading -> {
                val loadingCd = stringResource(Res.string.deal_details_loading_indicator)
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .align(Alignment.CenterHorizontally)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(160.dp)
                            .padding(GameDealsCustomTheme.spacing.extraLarge)
                            .wrapContentSize(Alignment.Center)
                            .aspectRatio(1f)
                            .semantics { contentDescription = loadingCd },
                    )
                }
            }

            is DealBottomSheetData.DealDetailsData -> GameDetails(data, goToWeb, goToGameDetails, goToGameDetailsByTitle)
            is DealBottomSheetData.DealDetailsError -> GameDetailsError(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                retry = retry
            )
        }
    }

}

@Composable
private fun GameDetails(
    data: DealBottomSheetData.DealDetailsData,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
) {
    Box(modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.small)) {
        Row {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(0.5f)
                    .padding(GameDealsCustomTheme.spacing.small)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.deal_details_cheapest_store_label))
                        when (data.cheaperStores.isEmpty()) {
                            true -> withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                append(stringResource(Res.string.deal_details_cheapest_yes))
                            }

                            false -> withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) {
                                append(stringResource(Res.string.deal_details_cheapest_no))
                            }
                        }
                    }
                )

                // List of Rows for each cheaper store
                data.cheaperStores.forEach {
                    val cheaperStoreRowCd = stringResource(
                        Res.string.deal_details_cheaper_store_row_description,
                        it.store.storeName,
                        it.cheaperStore.salePriceDenominated,
                    )
                    Row(
                        modifier = Modifier
                            .clickable(role = Role.Button) { goToWeb(it.cheaperStore.url, data.gameName) }
                            .semantics(mergeDescendants = true) { contentDescription = cheaperStoreRowCd },
                    ) {
                        AsyncImage(
                            model = it.store.images.logo,
                            contentDescription = stringResource(Res.string.deal_details_cheaper_store_thumbnail, data.store.storeName),
                            error = painterResource(Res.drawable.videogame_thumb),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(top = GameDealsCustomTheme.spacing.small)
                        )
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterVertically)
                                .padding(horizontal = GameDealsCustomTheme.spacing.small),
                            text = it.cheaperStore.salePriceDenominated
                        )
                    }
                }

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = GameDealsCustomTheme.spacing.small),
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.deal_details_cheapest_ever_label))
                        when (data.cheapestPrice == null) {
                            true -> withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                append(stringResource(Res.string.deal_details_cheapest_yes))
                            }

                            false -> withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) {
                                append(stringResource(Res.string.deal_details_cheapest_no))
                            }
                        }
                    }
                )

                data.cheapestPrice?.let {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(Res.string.deal_details_cheapest_on_label, it.priceDenominated, it.date)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(GameDealsCustomTheme.spacing.small)
            ) {
                AsyncImage(
                    model = data.gameInfo.thumb,
                    contentDescription = stringResource(Res.string.deal_details_game_image, data.gameName),
                    error = painterResource(Res.drawable.videogame_thumb),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(GameDealsCustomTheme.spacing.small)
                )
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    onClick = { goToWeb(data.dealUrl, data.gameName) }) {
                    Text(text = stringResource(Res.string.deal_details_go_to_deal_label))
                }
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    onClick = {
                        val steamId = data.gameInfo.steamAppID
                        if (steamId != null) goToGameDetails(steamId, data.gameName)
                        else goToGameDetailsByTitle(data.gameName)
                    },
                ) {
                    Text(text = stringResource(Res.string.deal_details_view_game_details_label))
                }
                data.gameInfo.metacriticScore?.let { Text(text = stringResource(Res.string.deal_details_metacritic_score_label, it)) }
                data.gameInfo.steamRatingPercent?.let { Text(text = stringResource(Res.string.deal_details_steam_reviews_label, it)) }
                data.gameInfo.releaseDate?.let { Text(text = stringResource(Res.string.deal_details_release_label, it)) }
                data.gameInfo.steamAppID?.let { Text(text = stringResource(Res.string.deal_details_wiki_label, it)) }
                data.gameInfo.steamworks?.let {
                    Text(
                        text = when (it) {
                            true -> stringResource(Res.string.deal_details_steamworks_label_yes)
                            false -> stringResource(Res.string.deal_details_steamworks_label_no)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameDetailsError(
    modifier: Modifier,
    retry: () -> Unit
) {
    Box(
        modifier = modifier
            .height(160.dp)
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.small)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentSize()
        ) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .wrapContentSize(),
                text = stringResource(Res.string.deal_details_data_loading_error_msg)
            )
            Button(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = GameDealsCustomTheme.spacing.large),
                onClick = { retry() }) {
                Text(text = stringResource(Res.string.deal_details_data_loading_error_retry))
            }
        }
    }
}

private val previewDealDetailsData = DealBottomSheetData.DealDetailsData(
    store = PreviewStore,
    gameId = 123,
    gameName = PreviewDealGameInfo.name,
    dealId = "preview-deal-1",
    gameSalesPriceDenominated = PreviewDealGameInfo.salePriceDenominated,
    gameInfo = PreviewDealGameInfo,
    cheaperStores = persistentListOf(),
    cheapestPrice = PreviewDealCheapestPrice,
)

@Preview
@Composable
private fun DealContent_Success_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            DealContent(
                data = previewDealDetailsData,
                isFavourite = false,
                onShare = {},
                onToggleFavourite = {},
                goToWeb = { _, _ -> },
                goToGameDetails = { _, _ -> },
                goToGameDetailsByTitle = {},
                retry = {},
            )
        }
    }
}

@Preview
@Composable
private fun DealContent_Success_Favourited_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            DealContent(
                data = previewDealDetailsData,
                isFavourite = true,
                onShare = {},
                onToggleFavourite = {},
                goToWeb = { _, _ -> },
                goToGameDetails = { _, _ -> },
                goToGameDetailsByTitle = {},
                retry = {},
            )
        }
    }
}

@Preview
@Composable
private fun DealContent_WithCheaperStores_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            DealContent(
                data = previewDealDetailsData.copy(
                    cheaperStores = persistentListOf(
                        StoreCheaperStorePair(
                            store = PreviewStore.copy(storeID = 11, storeName = "Humble Store"),
                            cheaperStore = PreviewDealCheaperStore,
                        ),
                        StoreCheaperStorePair(
                            store = PreviewStore.copy(storeID = 7, storeName = "GOG"),
                            cheaperStore = PreviewDealCheaperStore.copy(
                                dealID = "cheaper-2",
                                storeID = 7,
                                salePriceValue = 5.99,
                                salePriceDenominated = "$5.99",
                            ),
                        ),
                    ),
                    cheapestPrice = null,
                ),
                isFavourite = false,
                onShare = {},
                onToggleFavourite = {},
                goToWeb = { _, _ -> },
                goToGameDetails = { _, _ -> },
                goToGameDetailsByTitle = {},
                retry = {},
            )
        }
    }
}

@Preview
@Composable
private fun DealContent_Loading_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            DealContent(
                data = DealBottomSheetData.DealDetailsLoading(
                    store = PreviewStore,
                    gameId = 123,
                    gameName = PreviewDealGameInfo.name,
                    dealId = "preview-deal-1",
                    gameSalesPriceDenominated = PreviewDealGameInfo.salePriceDenominated,
                ),
                isFavourite = false,
                onShare = {},
                onToggleFavourite = {},
                goToWeb = { _, _ -> },
                goToGameDetails = { _, _ -> },
                goToGameDetailsByTitle = {},
                retry = {},
            )
        }
    }
}

@Preview
@Composable
private fun DealContent_Error_Preview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            DealContent(
                data = DealBottomSheetData.DealDetailsError(
                    store = PreviewStore,
                    gameId = 123,
                    gameName = PreviewDealGameInfo.name,
                    dealId = "preview-deal-1",
                    gameSalesPriceDenominated = PreviewDealGameInfo.salePriceDenominated,
                ),
                isFavourite = false,
                onShare = {},
                onToggleFavourite = {},
                goToWeb = { _, _ -> },
                goToGameDetails = { _, _ -> },
                goToGameDetailsByTitle = {},
                retry = {},
            )
        }
    }
}
