package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.navigation.SignInPromptController
import pm.bam.gamedeals.common.ui.components.DealListRow
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealTotals
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideCard
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideSection
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideSectionKind
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.domain.utils.formatMoney
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_activation
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_attribution
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_back
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_checking_series
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_expires
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_fewer_popular
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hidden
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_more_series
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_no_sales
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_other_series
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_partial
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_popular
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_price_unavailable
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_price_unavailable_short
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_prices_checked
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_region_chip
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_row_description
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_row_description_no_price
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_series
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_show_less
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_sign_in
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_sign_in_action
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_stale
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_title
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_try_again
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_unavailable
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_updating
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_view_all
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_voucher
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_waitlist


private val MONTH_ABBREV = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Event, offer and price-check times read in the phone's own time zone, with the date so a weekday is never ambiguous. */
@OptIn(ExperimentalTime::class)
internal fun winterSaleTime(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    return "${time.day} ${MONTH_ABBREV[time.month.ordinal]} ${time.year}, ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

/** The day alone ("14 Dec"), in the phone's time zone: the countdown pill's date, where the full time would make it too long. */
@OptIn(ExperimentalTime::class)
internal fun winterSaleDay(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    return "${time.day} ${MONTH_ABBREV[time.month.ordinal]}"
}


/**
 * The guide's entry on Home: the sale's blue card in its compact form. During the sale it leads with the deal total
 * ([totals], from the day-long store counts); before the sale, and when the counts haven't loaded, with the countdown.
 */
@Composable
internal fun WinterSaleHomeCard(event: WinterSaleEventState.Available, totals: WinterSaleDealTotals? = null, onOpen: () -> Unit) {
    WinterSaleCard(
        event = event,
        totalDeals = totals?.totalDeals,
        storeCount = totals?.storeCount ?: 0,
        compact = true,
        onClick = onOpen,
        modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.medium),
    )
}

@Composable
internal fun WinterSaleGuideScreen(
    onBack: () -> Unit,
    onGame: (String) -> Unit,
    onOffer: (String) -> Unit,
    viewModel: WinterSaleGuideViewModel = koinViewModel(),
    peek: WinterSalePeekViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val waitlistIds by peek.waitlistIds.collectAsStateWithLifecycle()
    val collectionIds by peek.collectionIds.collectAsStateWithLifecycle()
    val stores by peek.stores.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    WinterSaleResumeEffect(viewModel::onResume, viewModel::onPause)

    WinterSaleGuideContent(
        state = state,
        onBack = onBack,
        onPeek = { card -> peek.peek(card.candidate.gameId, card.candidate.title, card.candidate.artwork.thumbnail) },
        onRefresh = viewModel::onPullToRefresh,
        onRetry = viewModel::onRetry,
        waitlistIds = waitlistIds,
        collectionIds = collectionIds,
        stores = stores,
        snackbarHostState = snackbarHostState,
    )
    WinterSalePeekHost(peek = peek, snackbarHostState = snackbarHostState, onGame = onGame, onOffer = onOffer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WinterSaleGuideContent(
    state: WinterSaleGuideState,
    onBack: () -> Unit,
    onPeek: (WinterSaleGuideCard) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    waitlistIds: Set<String> = emptySet(),
    collectionIds: Set<String> = emptySet(),
    stores: Map<Int, Store> = emptyMap(),
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
            if (event == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (state.eventChecked) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text(stringResource(Res.string.winter_sale_hidden))
                            // Only a failed check is worth retrying; a switched-off or ended guide stays hidden.
                            if ((state.event as? WinterSaleEventState.Hidden)?.reason == WinterSaleHiddenReason.Unavailable) {
                                TextButton(onClick = onRetry) { Text(stringResource(Res.string.winter_sale_try_again)) }
                            }
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }
                return@Scaffold
            }
            val content = state.content?.forAdultSetting(state.includeAdult)
            // Sections start collapsed to their best few games; "View all" expands one in place.
            var expanded by rememberSaveable { mutableStateOf("") }
            val bottomInset = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = bottomInset) {
                    item(key = "banner") { WinterSaleBanner(state, event, content?.checkedAtEpochMillis) }
                    if (state.loading) item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.large), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    if (content != null) {
                        if (content.sections.all { it.cards.isEmpty() } && !state.loading && !content.hasFailure &&
                            content.sections.none { it.checking || it.signInRequired }) {
                            item(key = "no-sales") { StatusRow(Icons.Outlined.Info, stringResource(Res.string.winter_sale_no_sales)) }
                        }
                        var rendered = false
                        for (section in content.sections) {
                            if (section.cards.isEmpty() && !section.unavailable && !section.incomplete && !section.signInRequired && !section.checking) continue
                            if (rendered) sectionDivider()
                            rendered = true
                            val kind = section.kind.name
                            val isExpanded = kind in expanded.split(',')
                            guideSection(
                                section = section,
                                expanded = isExpanded,
                                onToggle = {
                                    val open = expanded.split(',').filter { it.isNotEmpty() }.toMutableSet()
                                    if (!open.remove(kind)) open.add(kind)
                                    expanded = open.joinToString(",")
                                },
                                stalePrices = state.stalePrices,
                                waitlistIds = waitlistIds,
                                collectionIds = collectionIds,
                                stores = stores,
                                onPeek = onPeek,
                            )
                        }
                        item(key = "attribution") {
                            Text(
                                stringResource(Res.string.winter_sale_attribution),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = GameDealsCustomTheme.spacing.medium,
                                    vertical = GameDealsCustomTheme.spacing.large,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The sale's blue card (countdown, date and region), then any notices about stale or still-updating prices. */
@Composable
private fun WinterSaleBanner(state: WinterSaleGuideState, event: WinterSaleEventState.Available, checkedAt: Long?) {
    Column(
        modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        WinterSaleCard(
            event = event,
            totalDeals = null,
            storeCount = 0,
            extraChips = {
                state.country?.let { SaleChip(stringResource(Res.string.winter_sale_region_chip, it.name, it.currency), Icons.Filled.Public) }
            },
        ) {
            if (checkedAt != null && !state.stalePrices) {
                Text(stringResource(Res.string.winter_sale_prices_checked, winterSaleTime(checkedAt)), style = MaterialTheme.typography.labelSmall)
            }
        }
        val notices = buildList {
            if (event.usingCachedDocument) add(stringResource(Res.string.winter_sale_stale))
            if (state.stalePrices) add(stringResource(Res.string.winter_sale_price_unavailable))
            if (event.phase == WinterSalePhase.OnSale &&
                event.observedServerNowEpochMillis - event.event.saleStartEpochMillis < 6 * 60 * 60 * 1000L) {
                add(stringResource(Res.string.winter_sale_updating))
            }
        }
        if (notices.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)) {
                        notices.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

/** A section's state (sign in, still checking, failed) as one consistent row: icon, text and an optional action. */
@Composable
private fun StatusRow(
    icon: ImageVector?,
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    progress: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        when {
            progress -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (actionText != null && onAction != null) TextButton(onClick = onAction) { Text(actionText) }
    }
}

private const val COLLAPSED_ROWS = 5

private fun LazyListScope.guideSection(
    section: WinterSaleGuideSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    stalePrices: Boolean,
    waitlistIds: Set<String>,
    collectionIds: Set<String>,
    stores: Map<Int, Store>,
    onPeek: (WinterSaleGuideCard) -> Unit,
) {
    val kind = section.kind
    val canExpand = section.cards.size > COLLAPSED_ROWS
    item(key = "header:$kind") {
        SectionHeader(
            text = sectionTitle(kind),
            actionText = when {
                !canExpand -> null
                expanded -> stringResource(Res.string.winter_sale_show_less)
                else -> stringResource(Res.string.winter_sale_view_all, section.cards.size)
            },
            onActionClick = onToggle.takeIf { canExpand },
        )
    }
    if (section.signInRequired) item(key = "signin:$kind") {
        StatusRow(Icons.AutoMirrored.Filled.Login, stringResource(Res.string.winter_sale_sign_in),
            actionText = stringResource(Res.string.winter_sale_sign_in_action), onAction = { SignInPromptController.request() })
    }
    if (section.checking) item(key = "checking:$kind") { StatusRow(null, stringResource(Res.string.winter_sale_checking_series), progress = true) }
    if (section.unavailable) item(key = "unavailable:$kind") { StatusRow(Icons.Filled.ErrorOutline, stringResource(Res.string.winter_sale_unavailable)) }
    if (section.incomplete) item(key = "partial:$kind") { StatusRow(Icons.Filled.ErrorOutline, stringResource(Res.string.winter_sale_partial)) }
    if (section.moreSeriesFollowed) item(key = "more-series:$kind") { StatusRow(Icons.Outlined.Info, stringResource(Res.string.winter_sale_more_series)) }
    if (section.shortFeed && section.cards.isNotEmpty()) item(key = "short:$kind") { StatusRow(Icons.Outlined.Info, stringResource(Res.string.winter_sale_fewer_popular)) }

    val shown = if (expanded) section.cards else section.cards.take(COLLAPSED_ROWS)
    if (kind == WinterSaleGuideSectionKind.Series) {
        // Grouped under each game's first followed series; groups keep the order of their best deal.
        shown.groupBy { it.seriesNames.firstOrNull() }.forEach { (series, cards) ->
            item(key = "series:${series.orEmpty()}") { SeriesHeader(series) }
            cards.forEach { card -> guideRow(kind, series, card, stalePrices, waitlistIds, collectionIds, stores, onPeek) }
        }
    } else {
        shown.forEach { card -> guideRow(kind, null, card, stalePrices, waitlistIds, collectionIds, stores, onPeek) }
    }
}

private fun LazyListScope.guideRow(
    kind: WinterSaleGuideSectionKind,
    series: String?,
    card: WinterSaleGuideCard,
    stalePrices: Boolean,
    waitlistIds: Set<String>,
    collectionIds: Set<String>,
    stores: Map<Int, Store>,
    onPeek: (WinterSaleGuideCard) -> Unit,
) {
    item(key = "$kind:${card.candidate.gameId}", contentType = "winter-sale-row") {
        val game = card.candidate
        val offer = game.offer
        val salePrice = if (stalePrices) null else formatMoney(offer.price.amount, offer.price.currency)
        val supporting = rowDetails(kind, series, card)
        val description = if (salePrice == null) stringResource(Res.string.winter_sale_row_description_no_price, game.title)
            else stringResource(Res.string.winter_sale_row_description, game.title, salePrice, offer.cutPercent, offer.shopName)
        DealListRow(
            title = game.title,
            contentDescription = listOfNotNull(description, supporting).joinToString(". "),
            onClick = { onPeek(card) },
            imageUrl = game.artwork.thumbnail,
            salePrice = salePrice,
            regularPrice = offer.regular?.takeIf { !stalePrices }?.let { formatMoney(it.amount, it.currency) },
            neutralChip = if (stalePrices) stringResource(Res.string.winter_sale_price_unavailable_short) else null,
            discountPercent = if (stalePrices) 0 else offer.cutPercent,
            hasVoucher = !stalePrices && offer.voucher != null,
            isLowestEver = !stalePrices && offer.isAtHistoricalLow,
            storeName = offer.shopName,
            storeIconUrl = stores[offer.shopId]?.iconUrl?.takeIf { it.isNotBlank() },
            isWaitlisted = game.gameId in waitlistIds,
            isCollected = game.gameId in collectionIds,
            supportingText = supporting,
        )
    }
}

/** Launcher, voucher code, offer end and any reasons the section heading doesn't already say. */
@Composable
private fun rowDetails(kind: WinterSaleGuideSectionKind, series: String?, card: WinterSaleGuideCard): String? {
    val offer = card.candidate.offer
    val ownReason = when (kind) {
        WinterSaleGuideSectionKind.Waitlist -> "On your waitlist"
        WinterSaleGuideSectionKind.Popular -> "Popular on ITAD"
        WinterSaleGuideSectionKind.Series -> series?.let { "From $it" }
    }
    val parts = buildList {
        if (offer.activationLabels.isNotEmpty()) add(stringResource(Res.string.winter_sale_activation, offer.activationLabels.joinToString(", ")))
        offer.voucher?.let { add(stringResource(Res.string.winter_sale_voucher, it)) }
        offer.expiresAtEpochMillis?.let { add(stringResource(Res.string.winter_sale_expires, winterSaleTime(it))) }
        addAll(card.reasons.filter { it != ownReason })
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun SeriesHeader(series: String?) {
    Text(
        text = series ?: stringResource(Res.string.winter_sale_other_series),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(
                start = GameDealsCustomTheme.spacing.medium,
                end = GameDealsCustomTheme.spacing.medium,
                top = GameDealsCustomTheme.spacing.small,
                bottom = GameDealsCustomTheme.spacing.extraSmall,
            )
            .semantics { heading() },
    )
}

@Composable
private fun sectionTitle(kind: WinterSaleGuideSectionKind): String = when (kind) {
    WinterSaleGuideSectionKind.Waitlist -> stringResource(Res.string.winter_sale_waitlist)
    WinterSaleGuideSectionKind.Series -> stringResource(Res.string.winter_sale_series)
    WinterSaleGuideSectionKind.Popular -> stringResource(Res.string.winter_sale_popular)
}
