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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.PreviewDealCheaperStore
import pm.bam.gamedeals.common.ui.PreviewDealCheapestPrice
import pm.bam.gamedeals.common.ui.PreviewDealDetails
import pm.bam.gamedeals.common.ui.PreviewDealGameInfo
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.R
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Store


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealBottomSheet(
    data: DealBottomSheetData?,
    onDismiss: () -> Unit,
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
            DealContent(data, goToWeb, onRetryDealDetails)
        }
    }
}

@Composable
private fun DealContent(
    data: DealBottomSheetData,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    retry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
    ) {
        Row(modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.small)) {
            AsyncImage(
                model = data.store.images.logo,
                contentDescription = stringResource(R.string.deal_details_store_thumbnail, data.store.storeName),
                error = painterResource(id = R.drawable.videogame_thumb),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp)
                    .padding(GameDealsCustomTheme.spacing.small)
                    .align(Alignment.CenterVertically)
            )
            Column {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GameDealsCustomTheme.spacing.small)
                        .testTag(StoreDataGameDataTag),
                    text = stringResource(id = R.string.deal_details_title_label, data.store.storeName, data.gameSalesPriceDenominated),
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GameDealsCustomTheme.spacing.small)
                        .testTag(StoreDataGameNameTag),
                    text = data.gameName
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
                        append(stringResource(id = R.string.deal_details_cheapest_store_label))
                        when (data.cheaperStores.isEmpty()) {
                            true -> withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                append(stringResource(id = R.string.deal_details_cheapest_yes))
                            }

                            false -> withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) {
                                append(stringResource(id = R.string.deal_details_cheapest_no))
                            }
                        }
                    }
                )

                // List of Rows for each cheaper store
                data.cheaperStores.forEach {
                    Row(
                        modifier = Modifier
                            .clickable { goToWeb("$DEAL_URL${it.second.dealID}", data.gameName) }
                            .testTag(DealCheaperStoreRowTag.plus(it.first.storeID)),
                    ) {
                        AsyncImage(
                            model = it.first.images.logo,
                            contentDescription = stringResource(R.string.deal_details_cheaper_store_thumbnail, data.store.storeName),
                            error = painterResource(id = R.drawable.videogame_thumb),
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
                        append(stringResource(id = R.string.deal_details_cheapest_ever_label))
                        when (data.cheapestPrice == null) {
                            true -> withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                append(stringResource(id = R.string.deal_details_cheapest_yes))
                            }

                            false -> withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) {
                                append(stringResource(id = R.string.deal_details_cheapest_no))
                            }
                        }
                    }
                )

                data.cheapestPrice?.let {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CheapestPriceTag),
                        text = stringResource(id = R.string.deal_details_cheapest_on_label, it.priceDenominated, it.date)
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
                    contentDescription = stringResource(R.string.deal_details_game_image, data.gameName),
                    error = painterResource(id = R.drawable.videogame_thumb),
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
                    onClick = { goToWeb("$DEAL_URL${data.dealId}", data.gameName) }) {
                    Text(text = stringResource(id = R.string.deal_details_go_to_deal_label))
                }
                data.gameInfo.metacriticScore?.let { Text(text = stringResource(id = R.string.deal_details_metacritic_score_label, it)) }
                data.gameInfo.steamRatingPercent?.let { Text(text = stringResource(id = R.string.deal_details_steam_reviews_label, it)) }
                data.gameInfo.releaseDate?.let { Text(text = stringResource(id = R.string.deal_details_release_label, it)) }
                data.gameInfo.steamAppID?.let { Text(text = stringResource(id = R.string.deal_details_wiki_label, it)) }
                data.gameInfo.steamworks?.let {
                    Text(
                        text = when (it) {
                            true -> stringResource(id = R.string.deal_details_steamworks_label_yes)
                            false -> stringResource(id = R.string.deal_details_steamworks_label_no)
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
                text = stringResource(id = R.string.deal_details_data_loading_error_msg)
            )
            Button(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = GameDealsCustomTheme.spacing.large)
                    .testTag(DataErrorBtnTag),
                onClick = { retry() }) {
                Text(text = stringResource(id = R.string.deal_details_data_loading_error_retry))
            }
        }
    }
}

@Immutable
sealed class DealBottomSheetData(
    open val store: Store,
    open val gameName: String,
    open val dealId: String,
    open val gameSalesPriceDenominated: String,
) {
    @Immutable
    data class DealDetailsData(
        override val store: Store,
        override val gameName: String,
        override val dealId: String,
        override val gameSalesPriceDenominated: String,
        val gameInfo: DealDetails.GameInfo,
        val cheaperStores: List<Pair<Store, DealDetails.CheaperStore>>,
        val cheapestPrice: DealDetails.CheapestPrice?,
    ) : DealBottomSheetData(store, gameName, dealId, gameSalesPriceDenominated)

    @Immutable
    data class DealDetailsLoading(
        override val store: Store,
        override val gameName: String,
        override val dealId: String,
        override val gameSalesPriceDenominated: String
    ) : DealBottomSheetData(store, gameName, dealId, gameSalesPriceDenominated)

    @Immutable
    data class DealDetailsError(
        override val store: Store,
        override val gameName: String,
        override val dealId: String,
        override val gameSalesPriceDenominated: String
    ) : DealBottomSheetData(store, gameName, dealId, gameSalesPriceDenominated)
}

@Preview
@Composable
private fun DealBottomLoadingPreview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DealContent(
                data = DealBottomSheetData.DealDetailsLoading(
                    PreviewStore,
                    PreviewDealDetails.gameInfo.name,
                    PreviewDeal.dealID,
                    PreviewDealDetails.gameInfo.salePriceDenominated
                ),
                goToWeb = { _, _ -> },
                retry = { }
            )
        }
    }
}

@Preview
@Composable
private fun DealBottomDataPreview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DealContent(
                data = DealBottomSheetData.DealDetailsData(
                    PreviewStore,
                    PreviewDealDetails.gameInfo.name,
                    PreviewDeal.dealID,
                    PreviewDealDetails.gameInfo.salePriceDenominated,
                    PreviewDealGameInfo,
                    listOf(PreviewStore to PreviewDealCheaperStore, PreviewStore to PreviewDealCheaperStore),
                    PreviewDealCheapestPrice
                ),
                goToWeb = { _, _ -> },
                retry = { }
            )
        }
    }
}

@Preview
@Composable
private fun DealBottomErrorPreview() {
    GameDealsTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DealContent(
                data = DealBottomSheetData.DealDetailsError(
                    PreviewStore,
                    PreviewDealDetails.gameInfo.name,
                    PreviewDeal.dealID,
                    PreviewDealDetails.gameInfo.salePriceDenominated
                ),
                goToWeb = { _, _ -> },
                retry = { }
            )
        }
    }
}

const val DEAL_URL = "https://www.cheapshark.com/redirect?dealID="

internal const val CheapestPriceTag = "CheapestPrice"
internal const val DataLoadingTag = "DataLoading"
internal const val DataErrorMsgTag = "DataErrorMsg"
internal const val DataErrorBtnTag = "DataErrorBtn"
internal const val GoToDealBtnTag = "GoToDealBtn"
internal const val DealCheaperStoreRowTag = "DealCheaperStoreRow"
internal const val DealCheapestTag = "DealCheapest"
internal const val StoreDataGameDataTag = "StoreDataGameData"
internal const val StoreDataGameNameTag = "StoreDataGameName"
