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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.deal.DealBottomSheet
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
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
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_favourite_indicator
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_favourites_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_floating_favourites_icon
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_floating_search_icon
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_game_image
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaways_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_loading_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_new_releases_label
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_store_banner
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenListData.DealData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenListData.StoreData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenListData.ViewAllData
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus.ERROR
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus.LOADING
import pm.bam.gamedeals.feature.home.ui.HomeViewModel.HomeScreenStatus.SUCCESS
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

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

    Screen(
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDealDetails(deal.dealID, deal.storeID, deal.gameID, deal.title, deal.salePriceDenominated) }
            .padding(bottom = GameDealsCustomTheme.spacing.small)
            .testTag(HomeScreenDealRowTag.plus(deal.dealID)),
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
private fun Screen(
    onSearch: () -> Unit,
    onReleaseTitle: (title: String) -> Unit,
    data: HomeViewModel.HomeScreenData,
    favouriteIds: Set<Int>,
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
                        SmallFloatingActionButton(
                            modifier = Modifier.testTag(HomeScreenFavouritesFabTag),
                            onClick = onViewFavourites,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = stringResource(Res.string.home_screen_floating_favourites_icon),
                            )
                        }
                        FloatingActionButton(onClick = {}) {
                            when (data.state) {
                                LOADING -> CircularProgressIndicator(Modifier.testTag(HomeScreenLoadingTag))
                                ERROR -> Icon(Icons.Default.Warning, contentDescription = stringResource(Res.string.home_screen_floating_search_icon))
                                SUCCESS -> Icon(
                                    modifier = Modifier.clickable { onSearch() },
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
                                        .padding(top = GameDealsCustomTheme.spacing.medium, bottom = GameDealsCustomTheme.spacing.large)
                                        .testTag(HomeScreenViewAllFavouritesButtonTag),
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
                                        .padding(top = GameDealsCustomTheme.spacing.medium, bottom = GameDealsCustomTheme.spacing.large)
                                        .testTag(HomeScreenViewAllGiveawaysButtonTag),
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
                                            .padding(top = GameDealsCustomTheme.spacing.medium, bottom = GameDealsCustomTheme.spacing.large)
                                            .testTag(HomeScreenViewAllButtonTag.plus(itemData.store.storeID)),
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
            .testTag(HomeScreenStoreBannerTag.plus(store.storeID))
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
            .padding(GameDealsCustomTheme.spacing.medium),
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
            .clickable { onReleaseTitle(release.title) }
            .padding(bottom = GameDealsCustomTheme.spacing.small)
            .testTag(HomeScreenReleaseRowTag.plus(release.title)),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onGiveawayTitle(giveaway.gamerpowerUrl) }
            .padding(bottom = GameDealsCustomTheme.spacing.small)
            .testTag(HomeScreenGiveawayRowTag.plus(giveaway.id)),
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
        Text(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = GameDealsCustomTheme.spacing.small),
            textAlign = TextAlign.Start,
            text = giveaway.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
            .clickable { onClick() }
            .padding(bottom = GameDealsCustomTheme.spacing.small)
            .testTag(HomeScreenFavouriteRowTag.plus(favourite.gameID)),
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
        contentDescription = stringResource(Res.string.home_screen_favourite_indicator),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(GameDealsCustomTheme.spacing.extraSmall)
            .size(16.dp),
    )
}


internal const val HomeScreenReleaseRowTag = "HomeScreenReleaseRowTag"

internal const val HomeScreenGiveawayRowTag = "HomeScreenGiveawayRowTag"
internal const val HomeScreenViewAllGiveawaysButtonTag = "HomeScreenViewAllGiveawaysButtonTag"

internal const val HomeScreenFavouriteRowTag = "HomeScreenFavouriteRowTag"
internal const val HomeScreenViewAllFavouritesButtonTag = "HomeScreenViewAllFavouritesButtonTag"
internal const val HomeScreenFavouritesFabTag = "HomeScreenFavouritesFabTag"

internal const val HomeScreenStoreBannerTag = "HomeScreenStoreBannerTag"
internal const val HomeScreenDealRowTag = "HomeScreenDealRowTag"
internal const val HomeScreenViewAllButtonTag = "HomeScreenViewAllButtonTag"
internal const val HomeScreenLoadingTag = "HomeScreenLoadingTag"
