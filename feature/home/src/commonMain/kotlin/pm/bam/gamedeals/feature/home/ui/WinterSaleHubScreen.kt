package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.navigation.SignInPromptController
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.DealBadgeCluster
import pm.bam.gamedeals.common.ui.components.GameStateBadges
import pm.bam.gamedeals.common.ui.components.PriceBlock
import pm.bam.gamedeals.common.ui.components.StoreLabel
import pm.bam.gamedeals.common.ui.components.dealBadgeContentSuffix
import pm.bam.gamedeals.common.ui.components.discountColors
import pm.bam.gamedeals.common.ui.components.StoreIcon
import pm.bam.gamedeals.common.ui.components.gameStateContentSuffix
import pm.bam.gamedeals.common.ui.deal.GamePeekSheet
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsElevation
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.portrait
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.wintersale.WINTER_SALE_BIG_CUT_PERCENT
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDeal
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealFlag
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHub
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubStore
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShelf
import pm.bam.gamedeals.domain.utils.formatMoney
import pm.bam.gamedeals.domain.utils.groupThousands
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.library_action_failed
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_attribution
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_back
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_by_store
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_cuts
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_cuts_body
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_empty
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_ends_days
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_ends_hours
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_ends_soon
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_failed
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_for_you
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_for_you_body
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_lows
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_lows_body
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_partial
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_see_all
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_see_all_description
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_store_deals
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_store_description
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hub_store_description_no_count
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_row_description
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_stale
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_title
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_try_again
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_updating

/**
 * The guide's entry. While the sale is on it opens on the hub (stores, all-time lows, big cuts, and a "For you"
 * tile for the personal list); before and after the sale it shows the guide's own pages as before.
 */
@Composable
internal fun WinterSaleEntryScreen(
    onBack: () -> Unit,
    onGame: (String) -> Unit,
    onOffer: (String) -> Unit,
    onForYou: () -> Unit,
    onShelf: (shelfKey: String, saleEndEpochMillis: Long) -> Unit,
    hub: WinterSaleHubViewModel = koinViewModel(),
    peek: WinterSalePeekViewModel = koinViewModel(),
) {
    val state by hub.state.collectAsStateWithLifecycle()
    WinterSaleResumeEffect(hub::onResume, hub::onPause)
    when {
        !state.eventChecked -> WinterSaleHubContent(state = state, onBack = onBack)
        state.onSale -> {
            val snackbarHostState = remember { SnackbarHostState() }
            val stores by peek.stores.collectAsStateWithLifecycle()
            val waitlistIds by peek.waitlistIds.collectAsStateWithLifecycle()
            val collectionIds by peek.collectionIds.collectAsStateWithLifecycle()
            val saleEnd = state.saleEndOnPhoneEpochMillis ?: 0L
            WinterSaleHubContent(
                state = state,
                onBack = onBack,
                onForYou = {
                    hub.onShelfOpened(FOR_YOU_SHELF)
                    onForYou()
                },
                onShelf = { shelf ->
                    hub.onShelfOpened(shelf.key)
                    onShelf(shelf.key, saleEnd)
                },
                onPeek = { deal, shelf ->
                    hub.onGamePeeked(shelf.key)
                    peek.peek(deal.gameId, deal.title, deal.artwork.thumbnail)
                },
                onRefresh = hub::onPullToRefresh,
                onRetry = hub::onRetry,
                stores = stores,
                waitlistIds = waitlistIds,
                collectionIds = collectionIds,
                snackbarHostState = snackbarHostState,
            )
            WinterSalePeekHost(peek = peek, snackbarHostState = snackbarHostState, onGame = onGame, onOffer = onOffer)
        }
        else -> WinterSaleGuideScreen(onBack = onBack, onGame = onGame, onOffer = onOffer, peek = peek)
    }
}

/** The shared game peek sheet and its events (share, sign-in prompt, failed library change), for every sale page. */
@Composable
internal fun WinterSalePeekHost(
    peek: WinterSalePeekViewModel,
    snackbarHostState: SnackbarHostState,
    onGame: (String) -> Unit,
    onOffer: (String) -> Unit,
) {
    val gamePeek by peek.gamePeek.collectAsStateWithLifecycle()
    val waitlistIds by peek.waitlistIds.collectAsStateWithLifecycle()
    val collectionIds by peek.collectionIds.collectAsStateWithLifecycle()
    val ignoredIds by peek.ignoredIds.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current
    val actionFailed = stringResource(CommonRes.string.library_action_failed)

    val peekGameId = gamePeek?.gameId
    GamePeekSheet(
        snackbarHostState = snackbarHostState,
        data = gamePeek,
        isWaitlisted = peekGameId != null && peekGameId in waitlistIds,
        isCollected = peekGameId != null && peekGameId in collectionIds,
        isIgnored = peekGameId != null && peekGameId in ignoredIds,
        onDismiss = peek::dismiss,
        onShare = peek::share,
        onToggleWaitlist = peek::toggleWaitlist,
        onToggleCollection = peek::toggleCollection,
        onToggleIgnore = peek::toggleIgnore,
        goToWeb = { url, _ -> onOffer(url) },
        onViewGamePage = { data -> if (data.gameId.isNotEmpty()) onGame(data.gameId) },
        onRetry = peek::retry,
    )

    SingleEventEffect(peek.events) { event ->
        when (event) {
            is WinterSalePeekViewModel.Event.Share -> platformActions.share(event.text)
            WinterSalePeekViewModel.Event.SignInRequired -> SignInPromptController.request()
            WinterSalePeekViewModel.Event.ActionFailed -> snackbarHostState.showSnackbar(actionFailed)
        }
    }
}

internal const val FOR_YOU_SHELF = "for_you"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WinterSaleHubContent(
    state: WinterSaleHubState,
    onBack: () -> Unit,
    onForYou: () -> Unit = {},
    onShelf: (WinterSaleShelf) -> Unit = {},
    onPeek: (WinterSaleDeal, WinterSaleShelf) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onRetry: () -> Unit = {},
    stores: Map<Int, Store> = emptyMap(),
    waitlistIds: Set<String> = emptySet(),
    collectionIds: Set<String> = emptySet(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(Res.string.winter_sale_title)) }, navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.winter_sale_back))
                    }
                })
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            val event = state.event as? WinterSaleEventState.Available
            val hub = state.hub
            // One spinner until the shelves are in, like the app's other pages: shown earlier, the blue card would open on
            // the countdown and switch to the deal total a moment later.
            if (!state.eventChecked || event == null || (hub == null && state.loading)) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            val bottomInset = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = bottomInset) {
                    item(key = "header") { HubHeader(event, hub) }
                    item(key = "for-you") { ForYouTile(onForYou) }
                    if (hub == null && state.failed) item(key = "failed") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(Res.string.winter_sale_hub_failed), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onRetry) { Text(stringResource(Res.string.winter_sale_try_again)) }
                        }
                    }
                    if (hub != null) hubShelves(hub, onShelf, onPeek, stores, waitlistIds, collectionIds)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.hubShelves(
    hub: WinterSaleHub,
    onShelf: (WinterSaleShelf) -> Unit,
    onPeek: (WinterSaleDeal, WinterSaleShelf) -> Unit,
    stores: Map<Int, Store>,
    waitlistIds: Set<String>,
    collectionIds: Set<String>,
) {
    if (hub.partial && !hub.isEmpty) item(key = "partial") { HubNotice(stringResource(Res.string.winter_sale_hub_partial)) }
    if (hub.isEmpty) item(key = "empty") { HubNotice(stringResource(Res.string.winter_sale_hub_empty)) }
    if (hub.steam != null || hub.otherStores.isNotEmpty()) item(key = "by-store") {
        HubHeading(stringResource(Res.string.winter_sale_hub_by_store), modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.large))
    }
    hub.steam?.let { steam ->
        item(key = "store:${steam.shopId}") { LeadStoreCard(steam, stores[steam.shopId]?.iconUrl, onShelf, onPeek, waitlistIds, collectionIds) }
    }
    if (hub.otherStores.isNotEmpty()) item(key = "stores") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(hub.otherStores, key = { it.shopId }) { store -> StoreTile(store, stores[store.shopId]?.iconUrl, onShelf) }
        }
    }
    if (hub.allTimeLows.isNotEmpty()) item(key = "lows") {
        DealShelf(
            title = stringResource(Res.string.winter_sale_hub_lows),
            subtitle = stringResource(Res.string.winter_sale_hub_lows_body),
            shelf = WinterSaleShelf.AllTimeLows,
            deals = hub.allTimeLows,
            stores = stores,
            onShelf = onShelf,
            onPeek = onPeek,
            waitlistIds = waitlistIds,
            collectionIds = collectionIds,
        )
    }
    if (hub.bigCuts.isNotEmpty()) item(key = "cuts") {
        DealShelf(
            title = stringResource(Res.string.winter_sale_hub_cuts, WINTER_SALE_BIG_CUT_PERCENT),
            subtitle = stringResource(Res.string.winter_sale_hub_cuts_body),
            shelf = WinterSaleShelf.BigCuts,
            deals = hub.bigCuts,
            stores = stores,
            onShelf = onShelf,
            onPeek = onPeek,
            waitlistIds = waitlistIds,
            collectionIds = collectionIds,
        )
    }
    item(key = "attribution") {
        Text(
            stringResource(Res.string.winter_sale_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.large),
        )
    }
}

/** "Ends in 9 days", or hours under two days ("Ends in 47 hours", not "1 day"), from the server time of the last event check. */
internal sealed interface SaleCountdown {
    data class Days(val days: Int) : SaleCountdown
    data class Hours(val hours: Int) : SaleCountdown
    data object Soon : SaleCountdown
}

internal fun saleCountdown(remainingMillis: Long): SaleCountdown {
    val hours = (remainingMillis / HOUR_MS).toInt()
    return when {
        hours >= 48 -> SaleCountdown.Days(hours / 24)
        hours >= 1 -> SaleCountdown.Hours(hours)
        else -> SaleCountdown.Soon
    }
}

/** 64,127 reads as "64,000+": the number moves all day, and the round figure is what makes the point. */
internal fun roundedDealTotal(total: Int): String =
    if (total >= 1_000) "${groupThousands((total / 1_000 * 1_000).toLong())}+" else total.toString()

@Composable
internal fun countdownText(countdown: SaleCountdown): String = when (countdown) {
    is SaleCountdown.Days -> pluralStringResource(Res.plurals.winter_sale_hub_ends_days, countdown.days, countdown.days)
    is SaleCountdown.Hours -> pluralStringResource(Res.plurals.winter_sale_hub_ends_hours, countdown.hours, countdown.hours)
    SaleCountdown.Soon -> stringResource(Res.string.winter_sale_hub_ends_soon)
}

private const val HOUR_MS = 60 * 60 * 1000L

@Composable
private fun HubHeader(event: WinterSaleEventState.Available, hub: WinterSaleHub?) {
    WinterSaleCard(
        event = event,
        totalDeals = hub?.totals?.totalDeals,
        storeCount = hub?.totals?.storeCount ?: hub?.storeCount ?: 0,
        modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small),
    ) {
        if (event.usingCachedDocument) Text(stringResource(Res.string.winter_sale_stale), style = MaterialTheme.typography.bodySmall)
        if (event.observedServerNowEpochMillis - event.event.saleStartEpochMillis < 6 * HOUR_MS) {
            Text(stringResource(Res.string.winter_sale_updating), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ForYouTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val palette = winterSalePalette()
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Surface(color = palette.ice, shape = RoundedCornerShape(50), modifier = Modifier.size(40.dp)) {}
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = palette.onIce, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.winter_sale_hub_for_you), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(Res.string.winter_sale_hub_for_you_body), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HubHeading(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(horizontal = GameDealsCustomTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HubNotice(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun storeCountText(store: WinterSaleHubStore): String? =
    store.dealCount?.let { stringResource(Res.string.winter_sale_hub_store_deals, groupThousands(it.toLong())) }

@Composable
private fun storeDescription(store: WinterSaleHubStore): String =
    store.dealCount?.let { stringResource(Res.string.winter_sale_hub_store_description, store.name, groupThousands(it.toLong())) }
        ?: stringResource(Res.string.winter_sale_hub_store_description_no_count, store.name)

@Composable
private fun LeadStoreCard(
    store: WinterSaleHubStore,
    iconUrl: String?,
    onShelf: (WinterSaleShelf) -> Unit,
    onPeek: (WinterSaleDeal, WinterSaleShelf) -> Unit,
    waitlistIds: Set<String>,
    collectionIds: Set<String>,
) {
    val shelf = WinterSaleShelf.Store(store.shopId)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large).padding(top = 12.dp),
    ) {
        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val description = storeDescription(store)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onShelf(shelf) }
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StoreIcon(storeName = store.name, iconUrl = iconUrl, iconSize = 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(store.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    storeCountText(store)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(Res.string.winter_sale_hub_see_all), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(store.deals, key = { it.gameId }) { deal ->
                    SaleGameCard(
                        deal,
                        storeIconUrl = null,
                        showStore = false,
                        waitlisted = deal.gameId in waitlistIds,
                        collected = deal.gameId in collectionIds,
                    ) {
                        onPeek(deal, shelf)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreTile(store: WinterSaleHubStore, iconUrl: String?, onShelf: (WinterSaleShelf) -> Unit) {
    val description = storeDescription(store)
    Surface(
        onClick = { onShelf(WinterSaleShelf.Store(store.shopId)) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(156.dp).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoreIcon(storeName = store.name, iconUrl = iconUrl, iconSize = 20.dp)
                Text(store.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            storeCountText(store)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                store.deals.take(3).forEach { deal ->
                    Box(Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(5.dp))) {
                        Cover(deal, Modifier.fillMaxSize())
                        if (deal.isBigCut) {
                            val colors = discountColors(deal.cutPercent)
                            Text(
                                "-${deal.cutPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.content,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(colors.container)
                                    .padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DealShelf(
    title: String,
    subtitle: String,
    shelf: WinterSaleShelf,
    deals: List<WinterSaleDeal>,
    stores: Map<Int, Store>,
    onShelf: (WinterSaleShelf) -> Unit,
    onPeek: (WinterSaleDeal, WinterSaleShelf) -> Unit,
    waitlistIds: Set<String>,
    collectionIds: Set<String>,
) {
    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HubHeading(title, Modifier.weight(1f), subtitle)
            val seeAll = stringResource(Res.string.winter_sale_hub_see_all_description, title)
            TextButton(
                onClick = { onShelf(shelf) },
                modifier = Modifier.padding(end = GameDealsCustomTheme.spacing.medium).semantics { contentDescription = seeAll },
            ) {
                Text(stringResource(Res.string.winter_sale_hub_see_all))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = GameDealsCustomTheme.spacing.large), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(deals, key = { it.gameId }) { deal ->
                SaleGameCard(
                    deal,
                    storeIconUrl = stores[deal.shopId]?.iconUrl?.takeIf { it.isNotBlank() },
                    showStore = true,
                    waitlisted = deal.gameId in waitlistIds,
                    collected = deal.gameId in collectionIds,
                ) {
                    onPeek(deal, shelf)
                }
            }
        }
    }
}

@Composable
internal fun Cover(deal: WinterSaleDeal, modifier: Modifier = Modifier) {
    AsyncImage(
        model = deal.artwork.portrait,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        error = painterResource(CommonRes.drawable.videogame_thumb),
        modifier = modifier,
    )
}

/** The spoken summary of a sale deal: title, price, cut and store. Cards and rows add the badges and library state. */
@Composable
internal fun saleDealBaseDescription(deal: WinterSaleDeal): String =
    stringResource(Res.string.winter_sale_row_description, deal.title, formatMoney(deal.price.amount, deal.price.currency), deal.cutPercent, deal.shopName)

/** The spoken summary of a sale card: [saleDealBaseDescription], then its badges as Home's tiles say them, then library state. */
@Composable
internal fun saleDealDescription(deal: WinterSaleDeal, waitlisted: Boolean, collected: Boolean): String =
    saleDealBaseDescription(deal) +
        dealBadgeContentSuffix(
            isNewHistoricalLow = deal.isNewLow,
            isLowestEver = deal.isLowestEver,
            isStoreLow = deal.isStoreLow,
            hasVoucher = deal.voucher != null,
        ) +
        gameStateContentSuffix(isWaitlisted = waitlisted, isCollected = collected)

/** ITAD's one flag as the shared badges read it ("N" is also a lowest-ever price). */
internal val WinterSaleDeal.isNewLow: Boolean get() = flag == WinterSaleDealFlag.NewLow
internal val WinterSaleDeal.isLowestEver: Boolean get() = flag == WinterSaleDealFlag.NewLow || flag == WinterSaleDealFlag.AllTimeLow
internal val WinterSaleDeal.isStoreLow: Boolean get() = flag == WinterSaleDealFlag.StoreLow

/** The regular price, only when it is above the sale price. */
internal fun WinterSaleDeal.regularPriceText(): String? =
    regular?.takeIf { it.amount > price.amount }?.let { formatMoney(it.amount, it.currency) }

/**
 * A sale shelf card in Home's look: a raised card like Home's Featured tiles, the cover with the discount and
 * low-price letter on its corner, two lines kept for the title so prices line up along the shelf, the store
 * with its icon (left out on a store's own shelf), and the green price.
 */
@Composable
private fun SaleGameCard(
    deal: WinterSaleDeal,
    storeIconUrl: String?,
    showStore: Boolean,
    waitlisted: Boolean,
    collected: Boolean,
    onClick: () -> Unit,
) {
    val description = saleDealDescription(deal, waitlisted, collected)
    Card(
        modifier = Modifier.width(CARD_WIDTH),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = GameDealsElevation.card),
    ) {
        Column(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
        ) {
            Box(Modifier.width(CARD_WIDTH).height(CARD_COVER_HEIGHT)) {
                Cover(deal, Modifier.fillMaxSize())
                DealBadgeCluster(
                    discountPercent = deal.cutPercent,
                    hasVoucher = deal.voucher != null,
                    isNewHistoricalLow = deal.isNewLow,
                    isLowestEver = deal.isLowestEver,
                    isStoreLow = deal.isStoreLow,
                    onArt = true,
                    modifier = Modifier.align(Alignment.TopStart).padding(GameDealsCustomTheme.spacing.small),
                )
                GameStateBadges(
                    isWaitlisted = waitlisted,
                    isCollected = collected,
                    modifier = Modifier.align(Alignment.TopEnd).padding(GameDealsCustomTheme.spacing.small),
                )
            }
            Column(
                modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
            ) {
                Text(
                    deal.title,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showStore) StoreLabel(storeName = deal.shopName, iconUrl = storeIconUrl)
                PriceBlock(
                    salePrice = formatMoney(deal.price.amount, deal.price.currency),
                    regularPrice = deal.regularPriceText(),
                    horizontalAlignment = Alignment.Start,
                )
            }
        }
    }
}

private val CARD_WIDTH = 120.dp
private val CARD_COVER_HEIGHT = 180.dp
