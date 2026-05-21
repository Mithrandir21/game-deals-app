@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.PreviewFavourite
import pm.bam.gamedeals.common.ui.PreviewGiveaway
import pm.bam.gamedeals.common.ui.PreviewRelease
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.deal.DealBottomSheet
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.FavouriteGame
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_all_favourites_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_all_giveaways_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_all_store_deals_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_favourites_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_floating_favourites_icon
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_floating_search_error_icon
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_floating_search_icon
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_game_image
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_free_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_row_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_row_description_no_platforms
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_row_description_no_worth
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_row_description_no_worth_no_platforms
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaways_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_loading_indicator
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_loading_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_new_releases_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_store_banner
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_store_deal_row_description
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_store_deal_row_description_favourite
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenListData.DealData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenListData.StoreData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenListData.ViewAllData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus.ERROR
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus.LOADING
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus.SUCCESS
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes

@Composable
internal fun HomeScreen(
    onSearch: () -> Unit,
    goToGame: (gameId: Int) -> Unit,
    onViewStoreDeals: ((store: Store) -> Unit) = {},
    onViewGiveaways: () -> Unit,
    onViewFavourites: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val data = viewModel.uiState.collectAsStateWithLifecycle()
    val dealDetails = viewModel.dealDetails.collectAsStateWithLifecycle()
    val favouriteIds = viewModel.favouriteIds.collectAsStateWithLifecycle()
    val favourites = viewModel.favourites.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current

    val onReleaseTitle: (title: String) -> Unit = { title -> viewModel.onReleaseGame(title) }

    HomeScreenContent(
        onSearch = onSearch,
        onReleaseTitle = onReleaseTitle,
        data = data.value,
        favouriteIds = favouriteIds.value,
        favourites = favourites.value,
        dealDetails = dealDetails.value,
        onViewDealDetails = { dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated ->
            viewModel.loadDealDetails(
                dealId = dealId,
                dealStoreId = dealStoreId,
                dealGameId = dealGameId,
                dealTitle = dealTitle,
                dealPriceDenominated = dealPriceDenominated,
            )
        },
        onViewStoreDeals = onViewStoreDeals,
        onViewGiveaways = onViewGiveaways,
        onViewFavourites = onViewFavourites,
        goToFavouriteGame = goToGame,
        onDismissDealDetails = { viewModel.dismissDealDetails() },
        onShareDealDetails = { sheetData -> viewModel.onShareDealClicked(sheetData) },
        onToggleDealFavourite = { sheetData -> viewModel.toggleFavouriteFromDeal(sheetData) },
        goToWeb = goToWeb,
        onRetry = { viewModel.loadTopStoresDeals() }
    )

    // Collect one-shot UI events and dispatch them
    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is HomeViewModel.HomeUiEvent.NavigateToGame -> goToGame(event.gameId)
            is HomeViewModel.HomeUiEvent.ShareDeal -> platformActions.share(event.text)
        }
    }
}

@Composable
private fun StoreDealRow(
    deal: Deal,
    isFavourite: Boolean,
    onViewDealDetails: ((dealId: String, dealStoreId: Int, dealGameId: Int, dealTitle: String, dealPriceDenominated: String) -> Unit)
) {
    val rowCd = stringResource(
        if (isFavourite) Res.string.home_screen_store_deal_row_description_favourite
        else Res.string.home_screen_store_deal_row_description,
        deal.title,
        deal.normalPriceDenominated,
        deal.salePriceDenominated,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onViewDealDetails(deal.dealID, deal.storeID, deal.gameID, deal.title, deal.salePriceDenominated) }
            .padding(bottom = GameDealsCustomTheme.spacing.small)
            .semantics(mergeDescendants = true) { contentDescription = rowCd },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                .height(60.dp)
                .width(100.dp)
        ) {
            AsyncImage(
                model = deal.thumb,
                contentDescription = stringResource(Res.string.home_screen_game_image, deal.title),
                contentScale = ContentScale.Fit,
                error = painterResource(CommonRes.drawable.videogame_thumb),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
            )
            if (isFavourite) {
                FavouriteOverlay(modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
        Text(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = GameDealsCustomTheme.spacing.small),
            textAlign = TextAlign.Start,
            text = deal.title
        )
        Text(
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.medium),
            style = TextStyle(textDecoration = TextDecoration.LineThrough),
            text = deal.normalPriceDenominated
        )
        Text(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.extraSmall)
                .padding(GameDealsCustomTheme.spacing.medium),
            text = deal.salePriceDenominated,
        )
        Spacer(Modifier.padding(GameDealsCustomTheme.spacing.small))
    }
}

@Composable
private fun HomeScreenContent(
    onSearch: () -> Unit,
    onReleaseTitle: (title: String) -> Unit,
    data: HomeViewModel.HomeScreenData,
    favouriteIds: ImmutableSet<Int>,
    favourites: kotlinx.collections.immutable.ImmutableList<FavouriteGame>,
    dealDetails: DealBottomSheetData?,
    onViewDealDetails: (dealId: String, dealStoreId: Int, dealGameId: Int, dealTitle: String, dealPriceDenominated: String) -> Unit,
    onViewStoreDeals: (store: Store) -> Unit,
    onViewGiveaways: () -> Unit,
    onViewFavourites: () -> Unit,
    goToFavouriteGame: (gameId: Int) -> Unit,
    onDismissDealDetails: () -> Unit,
    onShareDealDetails: (data: DealBottomSheetData) -> Unit,
    onToggleDealFavourite: (data: DealBottomSheetData.DealDetailsData) -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onRetry: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnRetry by rememberUpdatedState(onRetry)

    val errorMessage = stringResource(Res.string.home_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.home_screen_data_loading_error_retry)
    val loadingCd = stringResource(Res.string.home_screen_loading_indicator)

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                        horizontalAlignment = Alignment.End,
                    ) {
                        SmallFloatingActionButton(onClick = onViewFavourites) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = stringResource(Res.string.home_screen_floating_favourites_icon),
                            )
                        }
                        FloatingActionButton(
                            onClick = {
                                when (data.state) {
                                    SUCCESS -> onSearch()
                                    ERROR -> onRetry()
                                    LOADING -> Unit
                                }
                            }
                        ) {
                            when (data.state) {
                                LOADING -> CircularProgressIndicator(
                                    Modifier.semantics { contentDescription = loadingCd }
                                )
                                ERROR -> Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = stringResource(Res.string.home_screen_floating_search_error_icon)
                                )
                                SUCCESS -> Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(Res.string.home_screen_floating_search_icon)
                                )
                            }
                        }
                    }
                }) { innerPadding: PaddingValues ->

                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    content = {
                        if (data.releases.isNotEmpty()) {
                            item { SectionHeader(stringResource(Res.string.home_screen_new_releases_label)) }

                            items(
                                count = data.releases.size,
                                key = { index -> "release-${data.releases[index].title}" }
                            ) { index ->
                                ReleaseRow(data.releases[index], onReleaseTitle)
                            }
                        }

                        if (favourites.isNotEmpty()) {
                            item { SectionHeader(stringResource(Res.string.home_screen_favourites_label)) }

                            items(
                                count = favourites.size,
                                key = { index -> "favourite-${favourites[index].gameID}" }
                            ) { index ->
                                FavouriteRow(favourites[index]) { goToFavouriteGame(favourites[index].gameID) }
                            }

                            item {
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentWidth()
                                        .padding(top = GameDealsCustomTheme.spacing.medium, bottom = GameDealsCustomTheme.spacing.large),
                                    onClick = { onViewFavourites() }) {
                                    Text(text = stringResource(Res.string.home_screen_all_favourites_label))
                                }
                            }
                        }

                        if (data.giveaways.isNotEmpty()) {
                            item { SectionHeader(stringResource(Res.string.home_screen_giveaways_label)) }

                            items(
                                count = data.giveaways.size,
                                key = { index -> "giveaway-${data.giveaways[index].id}" }
                            ) { index ->
                                GiveawayRow(data.giveaways[index]) { url -> goToWeb(url, data.giveaways[index].title) }
                            }

                            item {
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentWidth()
                                        .padding(top = GameDealsCustomTheme.spacing.medium, bottom = GameDealsCustomTheme.spacing.large),
                                    onClick = { onViewGiveaways() }) {
                                    Text(text = stringResource(Res.string.home_screen_all_giveaways_label))
                                }
                            }
                        }

                        if (data.items.isNotEmpty()) {
                            items(
                                count = data.items.size,
                                key = { index ->
                                    when (val itemData = data.items[index]) {
                                        is StoreData -> "store-${itemData.store.storeID}"
                                        is DealData -> "deal-${itemData.deal.dealID}"
                                        is ViewAllData -> "all-${itemData.store.storeID}"
                                    }
                                }
                            ) { index ->
                                when (val itemData = data.items[index]) {
                                    is StoreData -> StoreHeader(itemData.store)
                                    is DealData -> StoreDealRow(itemData.deal, itemData.deal.gameID in favouriteIds, onViewDealDetails)
                                    is ViewAllData -> Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentWidth()
                                            .padding(top = GameDealsCustomTheme.spacing.medium, bottom = GameDealsCustomTheme.spacing.large),
                                        onClick = { onViewStoreDeals(itemData.store) }) {
                                        Text(text = stringResource(Res.string.home_screen_all_store_deals_label, itemData.store.storeName))
                                    }
                                }
                            }
                        } else {
                            item { SectionHeader(stringResource(Res.string.home_screen_loading_label)) }
                        }
                    }
                )
                DealBottomSheet(
                    data = dealDetails,
                    isFavourite = dealDetails?.gameId?.let { it in favouriteIds } == true,
                    onDismiss = { onDismissDealDetails() },
                    onShare = { sheetData -> onShareDealDetails(sheetData) },
                    onToggleFavourite = { sheetData -> onToggleDealFavourite(sheetData) },
                    goToWeb = goToWeb,
                    onRetryDealDetails = {
                        dealDetails?.let {
                            onViewDealDetails(
                                it.dealId,
                                it.store.storeID,
                                it.gameId,
                                it.gameName,
                                it.gameSalesPriceDenominated
                            )
                        }
                    }
                )
            }
        }

        if (data.state == ERROR) {
            LaunchedEffect(snackbarHostState) {
                val results = snackbarHostState.showSnackbar(
                    message = errorMessage,
                    actionLabel = errorRetry
                )
                if (results == SnackbarResult.ActionPerformed) {
                    currentOnRetry()
                }
            }
        }
    }
}

@Composable
private fun StoreHeader(store: Store) {
    AsyncImage(
        model = store.images.banner,
        contentDescription = stringResource(Res.string.home_screen_store_banner, store.storeName),
        contentScale = ContentScale.Fit,
        error = painterResource(CommonRes.drawable.videogame_thumb),
        modifier = Modifier
            .height(80.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
            .background(color = MaterialTheme.colorScheme.primaryContainer)
            .padding(GameDealsCustomTheme.spacing.medium)
    )

    HorizontalDivider()
}

@Composable
private fun SectionHeader(text: String) {
    Box(
        modifier = Modifier
            .height(80.dp)
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.primary)
            .padding(GameDealsCustomTheme.spacing.medium)
            .semantics { heading() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}


@Composable
private fun ReleaseRow(
    release: Release,
    onReleaseTitle: (title: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onReleaseTitle(release.title) }
            .padding(bottom = GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = release.image,
            contentDescription = stringResource(Res.string.home_screen_game_image, release.title),
            contentScale = ContentScale.Fit,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                .height(60.dp)
                .width(100.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = GameDealsCustomTheme.spacing.small),
            textAlign = TextAlign.Start,
            text = release.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
private fun GiveawayRow(
    giveaway: Giveaway,
    onGiveawayTitle: (url: String) -> Unit,
) {
    val typeLabel = giveaway.type.displayLabel()
    val worth = giveaway.worthDenominated
    val platforms = giveaway.platforms
    val rowCd = when {
        worth != null && platforms.isNotEmpty() -> stringResource(
            Res.string.home_screen_giveaway_row_description,
            giveaway.title, worth, typeLabel,
            platforms.joinToString(", ") { it.platformValue },
        )
        worth != null -> stringResource(
            Res.string.home_screen_giveaway_row_description_no_platforms,
            giveaway.title, worth, typeLabel,
        )
        platforms.isNotEmpty() -> stringResource(
            Res.string.home_screen_giveaway_row_description_no_worth,
            giveaway.title, typeLabel,
            platforms.joinToString(", ") { it.platformValue },
        )
        else -> stringResource(
            Res.string.home_screen_giveaway_row_description_no_worth_no_platforms,
            giveaway.title, typeLabel,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onGiveawayTitle(giveaway.gamerpowerUrl) }
            .padding(vertical = GameDealsCustomTheme.spacing.medium)
            .semantics(mergeDescendants = true) { contentDescription = rowCd },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = giveaway.thumbnail,
            contentDescription = stringResource(Res.string.home_screen_game_image, giveaway.title),
            contentScale = ContentScale.Fit,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                .height(60.dp)
                .width(100.dp)
                .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = GameDealsCustomTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
        ) {
            Text(
                textAlign = TextAlign.Start,
                text = giveaway.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildAnnotatedString {
                    append(stringResource(Res.string.home_screen_giveaway_free_label))
                    giveaway.worthDenominated?.let {
                        append(" ")
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(it) }
                    }
                    append(" - ")
                    append(giveaway.type.displayLabel())
                    if (giveaway.platforms.isNotEmpty()) {
                        append(" · ")
                        append(giveaway.platforms.joinToString(", ") { it.platformValue })
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
private fun FavouriteRow(
    favourite: FavouriteGame,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onClick() }
            .padding(bottom = GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                .height(60.dp)
                .width(100.dp),
        ) {
            AsyncImage(
                model = favourite.thumb,
                contentDescription = stringResource(Res.string.home_screen_game_image, favourite.title),
                contentScale = ContentScale.Fit,
                error = painterResource(CommonRes.drawable.videogame_thumb),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall)),
            )
            FavouriteOverlay(modifier = Modifier.align(Alignment.BottomEnd))
        }
        Text(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = GameDealsCustomTheme.spacing.small),
            textAlign = TextAlign.Start,
            text = favourite.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FavouriteOverlay(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(GameDealsCustomTheme.spacing.extraSmall)
            .size(16.dp),
    )
}


private fun previewSuccessData(): HomeViewModel.HomeScreenData {
    val storeA = PreviewStore
    val storeB = PreviewStore.copy(storeID = 11, storeName = "Humble Store")

    val items = listOf(
        StoreData(storeA),
        DealData(PreviewDeal),
        DealData(
            PreviewDeal.copy(
                dealID = "preview-deal-2",
                title = "Hollow Knight",
                salePriceDenominated = "$7.49",
                normalPriceDenominated = "$14.99",
                gameID = 22222
            )
        ),
        ViewAllData(storeA),
        StoreData(storeB),
        DealData(
            PreviewDeal.copy(
                dealID = "preview-deal-3",
                storeID = 11,
                title = "Stardew Valley",
                salePriceDenominated = "$8.99",
                normalPriceDenominated = "$14.99",
                gameID = 33333
            )
        ),
        ViewAllData(storeB),
    )

    return HomeViewModel.HomeScreenData(
        state = SUCCESS,
        releases = listOf(
            PreviewRelease,
            PreviewRelease.copy(title = "Silksong"),
            PreviewRelease.copy(title = "Elden Ring: Nightreign"),
        ).toImmutableList(),
        giveaways = listOf(
            PreviewGiveaway,
            PreviewGiveaway.copy(id = 2, title = "Tomb Raider Trilogy", worthDenominated = "$49.99"),
        ).toImmutableList(),
        items = items.toImmutableList(),
    )
}

private val previewFavourites = persistentListOf(
    PreviewFavourite,
    PreviewFavourite.copy(gameID = 22222, title = "Hollow Knight"),
)

@Preview
@Composable
private fun HomeScreenContent_Success_Preview() {
    GameDealsTheme {
        HomeScreenContent(
            onSearch = {},
            onReleaseTitle = {},
            data = previewSuccessData(),
            favouriteIds = persistentSetOf(12345),
            favourites = previewFavourites,
            dealDetails = null,
            onViewDealDetails = { _, _, _, _, _ -> },
            onViewStoreDeals = {},
            onViewGiveaways = {},
            onViewFavourites = {},
            goToFavouriteGame = {},
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealFavourite = {},
            goToWeb = { _, _ -> },
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContent_Success_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        HomeScreenContent(
            onSearch = {},
            onReleaseTitle = {},
            data = previewSuccessData(),
            favouriteIds = persistentSetOf(12345),
            favourites = previewFavourites,
            dealDetails = null,
            onViewDealDetails = { _, _, _, _, _ -> },
            onViewStoreDeals = {},
            onViewGiveaways = {},
            onViewFavourites = {},
            goToFavouriteGame = {},
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealFavourite = {},
            goToWeb = { _, _ -> },
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContent_Loading_Preview() {
    GameDealsTheme {
        HomeScreenContent(
            onSearch = {},
            onReleaseTitle = {},
            data = HomeViewModel.HomeScreenData(state = LOADING),
            favouriteIds = persistentSetOf(),
            favourites = persistentListOf(),
            dealDetails = null,
            onViewDealDetails = { _, _, _, _, _ -> },
            onViewStoreDeals = {},
            onViewGiveaways = {},
            onViewFavourites = {},
            goToFavouriteGame = {},
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealFavourite = {},
            goToWeb = { _, _ -> },
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenContent_Error_Preview() {
    GameDealsTheme {
        HomeScreenContent(
            onSearch = {},
            onReleaseTitle = {},
            data = HomeViewModel.HomeScreenData(state = ERROR),
            favouriteIds = persistentSetOf(),
            favourites = persistentListOf(),
            dealDetails = null,
            onViewDealDetails = { _, _, _, _, _ -> },
            onViewStoreDeals = {},
            onViewGiveaways = {},
            onViewFavourites = {},
            goToFavouriteGame = {},
            onDismissDealDetails = {},
            onShareDealDetails = {},
            onToggleDealFavourite = {},
            goToWeb = { _, _ -> },
            onRetry = {},
        )
    }
}