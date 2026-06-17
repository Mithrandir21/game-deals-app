package pm.bam.gamedeals.feature.store.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.deal.GamePeekSheet
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.deal_waitlist_sign_in_required
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.store.generated.resources.Res
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_data_loading_error_msg
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_data_loading_error_retry
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_deal_row_description
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_deal_row_description_favourite
import pm.bam.gamedeals.feature.store.generated.resources.store_screen_navigation_back_icon
import pm.bam.gamedeals.feature.store.ui.StoreViewModel.StoreScreenData

private val AlwaysScrollable: () -> Boolean = { true }

@Composable
internal fun StoreScreen(
    onBack: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGame: (gameId: String) -> Unit,
    viewModel: StoreViewModel = koinViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deals: ImmutableList<Deal> by viewModel.deals.collectAsStateWithLifecycle()
    val favouriteIds by viewModel.waitlistIds.collectAsStateWithLifecycle()
    val collectionIds by viewModel.collectionIds.collectAsStateWithLifecycle()
    val ignoredIds by viewModel.ignoredIds.collectAsStateWithLifecycle()
    val gamePeek by viewModel.gamePeek.collectAsStateWithLifecycle()
    val errorMessage = stringResource(Res.string.store_screen_data_loading_error_msg)
    val errorRetry = stringResource(Res.string.store_screen_data_loading_error_retry)
    val signInRequired = stringResource(CommonRes.string.deal_waitlist_sign_in_required)
    val platformActions = LocalPlatformActions.current

    val store = (uiState as? StoreScreenData.Data)?.store

    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is StoreViewModel.StoreUiEvent.ShareDeal -> platformActions.share(event.text)
            StoreViewModel.StoreUiEvent.SignInRequired -> snackbarHostState.showSnackbar(signInRequired)
        }
    }

    StoreDeals(
        deals = deals,
        favouriteIds = favouriteIds,
        collectionIds = collectionIds,
        ignoredIds = ignoredIds,
        gamePeek = gamePeek,
        storeDetails = store,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onPeekGame = { gameId, gameName, thumb -> viewModel.peekGame(gameId, gameName, thumb) },
        onDismissPeek = { viewModel.dismissPeek() },
        onShare = { peekData -> viewModel.onShareClicked(peekData) },
        onToggleWaitlist = { gameId -> viewModel.toggleWaitlist(gameId) },
        onToggleCollection = { gameId -> viewModel.toggleCollection(gameId) },
        onToggleIgnore = { gameId -> viewModel.toggleIgnore(gameId) },
        onRetryPeek = { viewModel.retryPeek() },
        goToWeb = goToWeb,
        goToGame = goToGame,
    )

    when (uiState) {
        StoreScreenData.Loading -> CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        )

        StoreScreenData.Error -> LaunchedEffect(snackbarHostState) {
            val results = snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = errorRetry
            )
            if (results == SnackbarResult.ActionPerformed) {
                viewModel.retry()
            }
        }

        is StoreScreenData.Data -> Unit
    }
}

@Composable
private fun StoreDeals(
    deals: ImmutableList<Deal>,
    favouriteIds: ImmutableSet<String>,
    collectionIds: ImmutableSet<String> = persistentSetOf(),
    ignoredIds: ImmutableSet<String> = persistentSetOf(),
    gamePeek: GamePeekSheetData? = null,
    storeDetails: Store? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit,
    onPeekGame: (gameId: String, gameName: String, thumb: String?) -> Unit = { _, _, _ -> },
    onDismissPeek: () -> Unit = {},
    onShare: (data: GamePeekSheetData.Data) -> Unit = {},
    onToggleWaitlist: (gameId: String) -> Unit = {},
    onToggleCollection: (gameId: String) -> Unit = {},
    onToggleIgnore: (gameId: String) -> Unit = {},
    onRetryPeek: () -> Unit = {},
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGame: (gameId: String) -> Unit = {},
) {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = { StoreToolbar(onBack, storeDetails) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding: PaddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(vertical = GameDealsCustomTheme.spacing.small),
        ) {
            items(
                items = deals,
                key = { it.dealID }
            ) { deal ->
                val isFavourite = deal.gameID in favouriteIds
                val isCollected = deal.gameID in collectionIds
                DealListRow(
                    title = deal.title,
                    contentDescription = stringResource(
                        if (isFavourite) Res.string.store_screen_deal_row_description_favourite
                        else Res.string.store_screen_deal_row_description,
                        deal.title, deal.salePriceDenominated,
                    ),
                    onClick = { onPeekGame(deal.gameID, deal.title, deal.thumb) },
                    imageUrl = deal.thumb,
                    salePrice = deal.salePriceDenominated,
                    regularPrice = deal.normalPriceDenominated,
                    discountPercent = deal.savings.roundToInt(),
                    hasVoucher = deal.hasVoucher,
                    isNewHistoricalLow = deal.isNewHistoricalLow,
                    isStoreLow = deal.isStoreLow,
                    // store label omitted: this screen is already scoped to a single store
                    isWaitlisted = isFavourite,
                    isCollected = isCollected,
                )
            }
        }

        val peekGameId = gamePeek?.gameId?.takeIf { it.isNotEmpty() }
        GamePeekSheet(
            data = gamePeek,
            isWaitlisted = peekGameId?.let { it in favouriteIds } == true,
            isCollected = peekGameId?.let { it in collectionIds } == true,
            isIgnored = peekGameId?.let { it in ignoredIds } == true,
            onDismiss = onDismissPeek,
            onShare = onShare,
            onToggleWaitlist = onToggleWaitlist,
            onToggleCollection = onToggleCollection,
            onToggleIgnore = onToggleIgnore,
            goToWeb = goToWeb,
            onViewGamePage = { peekData -> goToGame(peekData.gameId) },
            onRetry = onRetryPeek,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreToolbar(
    onBack: () -> Unit,
    storeDetails: Store? = null
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior: TopAppBarScrollBehavior =
        TopAppBarDefaults.pinnedScrollBehavior(topAppBarState, AlwaysScrollable)

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = storeDetails?.images?.banner,
                    contentDescription = null,
                    error = painterResource(CommonRes.drawable.videogame_thumb),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(60.dp)
                        .width(100.dp)
                        .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
                )
                Text(
                    modifier = Modifier
                        .padding(horizontal = GameDealsCustomTheme.spacing.medium)
                        .semantics { heading() },
                    text = storeDetails?.storeName ?: "",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = { onBack() }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.store_screen_navigation_back_icon)
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}


private val previewDealsList = persistentListOf(
    PreviewDeal,
    PreviewDeal.copy(
        dealID = "deal-2",
        title = "Hollow Knight",
        salePriceDenominated = "$7.49",
        normalPriceDenominated = "$14.99",
        gameID = "222",
    ),
    PreviewDeal.copy(
        dealID = "deal-3",
        title = "Stardew Valley",
        salePriceDenominated = "$8.99",
        normalPriceDenominated = "$14.99",
        gameID = "333",
    ),
)

@Preview
@Composable
private fun StoreDeals_Success_Preview() {
    GameDealsTheme {
        StoreDeals(
            deals = previewDealsList,
            favouriteIds = persistentSetOf("222"), // marks Hollow Knight as favourited
            storeDetails = PreviewStore,
            onBack = {},
            goToWeb = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun StoreDeals_Success_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        StoreDeals(
            deals = previewDealsList,
            favouriteIds = persistentSetOf("222"),
            storeDetails = PreviewStore,
            onBack = {},
            goToWeb = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun StoreDeals_Empty_Preview() {
    GameDealsTheme {
        StoreDeals(
            deals = persistentListOf(),
            favouriteIds = persistentSetOf(),
            storeDetails = PreviewStore,
            onBack = {},
            goToWeb = { _, _ -> },
        )
    }
}
