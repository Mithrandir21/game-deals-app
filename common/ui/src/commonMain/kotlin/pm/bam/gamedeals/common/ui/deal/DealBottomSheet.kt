package pm.bam.gamedeals.common.ui.deal

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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.domain.models.cheapsharkDealRedirectUrl
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_content_description
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheaper_store_thumbnail
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_ever_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_no
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_on_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_store_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_cheapest_yes
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_msg
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_data_loading_error_retry
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_game_image
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_go_to_deal_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_metacritic_score_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_release_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_steam_reviews_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_steamworks_label_no
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_steamworks_label_yes
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_store_thumbnail
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_title_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_wiki_label
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealBottomSheet(
    data: DealBottomSheetData?,
    onDismiss: () -> Unit,
    onShare: (data: DealBottomSheetData) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onRetryDealDetails: () -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (data != null) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = modalBottomSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            DealContent(data, onShare, goToWeb, onRetryDealDetails)
        }
    }
}

@Composable
private fun DealContent(
    data: DealBottomSheetData,
    onShare: (data: DealBottomSheetData) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    retry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = data.store.images.logo,
                contentDescription = stringResource(Res.string.deal_details_store_thumbnail, data.store.storeName),
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
                        .padding(horizontal = GameDealsCustomTheme.spacing.small)
                        .testTag(StoreDataGameDataTag),
                    text = stringResource(Res.string.deal_details_title_label, data.store.storeName, data.gameSalesPriceDenominated),
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GameDealsCustomTheme.spacing.small)
                        .testTag(StoreDataGameNameTag),
                    text = data.gameName
                )
            }
            IconButton(
                modifier = Modifier.testTag(ShareDealBtnTag),
                onClick = { onShare(data) }
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(Res.string.deal_share_content_description)
                )
            }
        }
        HorizontalDivider()
        when (data) {
            is DealBottomSheetData.DealDetailsLoading -> {
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
                            .testTag(DataLoadingTag),
                    )
                }
            }

            is DealBottomSheetData.DealDetailsData -> GameDetails(data, goToWeb)
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
    goToWeb: (url: String, gameTitle: String) -> Unit
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DealCheapestTag),
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
                    Row(
                        modifier = Modifier
                            .clickable { goToWeb(cheapsharkDealRedirectUrl(it.second.dealID), data.gameName) }
                            .testTag(DealCheaperStoreRowTag.plus(it.first.storeID)),
                    ) {
                        AsyncImage(
                            model = it.first.images.logo,
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
                            text = it.second.salePriceDenominated
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CheapestPriceTag),
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
                        .align(Alignment.CenterHorizontally)
                        .testTag(GoToDealBtnTag),
                    onClick = { goToWeb(cheapsharkDealRedirectUrl(data.dealId), data.gameName) }) {
                    Text(text = stringResource(Res.string.deal_details_go_to_deal_label))
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
                    .wrapContentSize()
                    .testTag(DataErrorMsgTag),
                text = stringResource(Res.string.deal_details_data_loading_error_msg)
            )
            Button(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = GameDealsCustomTheme.spacing.large)
                    .testTag(DataErrorBtnTag),
                onClick = { retry() }) {
                Text(text = stringResource(Res.string.deal_details_data_loading_error_retry))
            }
        }
    }
}

internal const val CheapestPriceTag = "CheapestPrice"
internal const val DataLoadingTag = "DataLoading"
internal const val DataErrorMsgTag = "DataErrorMsg"
internal const val DataErrorBtnTag = "DataErrorBtn"
internal const val GoToDealBtnTag = "GoToDealBtn"
internal const val ShareDealBtnTag = "ShareDealBtn"
internal const val DealCheaperStoreRowTag = "DealCheaperStoreRow"
internal const val DealCheapestTag = "DealCheapest"
internal const val StoreDataGameDataTag = "StoreDataGameData"
internal const val StoreDataGameNameTag = "StoreDataGameName"
