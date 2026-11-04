package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.navigation.SignInPromptController
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideCard
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideSection
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideSectionKind
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.domain.utils.formatMoney
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_activation
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_activation_unknown
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_attribution
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_checked
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_checking_series
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_discount
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_ends
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_expires
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_fewer_popular
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_from_store
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_historical_low
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hidden
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_last_link
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_more_series
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_no_sales
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_offer_link
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_partial
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_popular
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_price_unavailable
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_region
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_series
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_sign_in
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_stale
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_starts
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_title
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_unavailable
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_updating
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_voucher
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_was
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_waitlist

private val MONTH_ABBREV = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Event, offer and price-check times read in the phone's own time zone, with the date so a weekday is never ambiguous. */
@OptIn(ExperimentalTime::class)
internal fun winterSaleTime(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    return "${time.day} ${MONTH_ABBREV[time.month.ordinal]} ${time.year}, ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

@Composable
internal fun WinterSaleHomeCard(event: WinterSaleEventState.Available, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clickable(onClick = onOpen)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.event.title, style = MaterialTheme.typography.titleLarge)
            Text(if (event.phase == WinterSalePhase.BeforeSale) event.event.beforeSaleCopy else event.event.duringSaleCopy)
            Text(
                if (event.phase == WinterSalePhase.BeforeSale) stringResource(Res.string.winter_sale_starts, winterSaleTime(event.event.saleStartEpochMillis))
                else stringResource(Res.string.winter_sale_ends, winterSaleTime(event.event.endEpochMillis)),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
internal fun WinterSaleGuideScreen(
    onBack: () -> Unit,
    onGame: (String) -> Unit,
    onOffer: (String) -> Unit,
    viewModel: WinterSaleGuideViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WinterSaleResumeEffect(viewModel::onResume, viewModel::onPause)
    WinterSaleGuideContent(state, onBack, onGame, onOffer, viewModel::onPullToRefresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WinterSaleGuideContent(
    state: WinterSaleGuideState,
    onBack: () -> Unit,
    onGame: (String) -> Unit,
    onOffer: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(Res.string.winter_sale_title)) }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.winter_sale_title))
                }
            })
        }) { padding ->
            val event = state.event as? WinterSaleEventState.Available
            if (event == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (state.eventChecked) {
                        Text(stringResource(Res.string.winter_sale_hidden), modifier = Modifier.padding(24.dp))
                    } else {
                        CircularProgressIndicator()
                    }
                }
                return@Scaffold
            }
            val content = state.content?.forAdultSetting(state.includeAdult)
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (event.phase == WinterSalePhase.BeforeSale) event.event.beforeSaleCopy else event.event.duringSaleCopy,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                if (event.phase == WinterSalePhase.BeforeSale) {
                                    stringResource(Res.string.winter_sale_starts, winterSaleTime(event.event.saleStartEpochMillis))
                                } else {
                                    stringResource(Res.string.winter_sale_ends, winterSaleTime(event.event.endEpochMillis))
                                },
                            )
                            state.country?.let { Text(stringResource(Res.string.winter_sale_region, it.name, it.currency)) }
                            if (event.usingCachedDocument) Text(stringResource(Res.string.winter_sale_stale))
                            if (state.stalePrices) Text(stringResource(Res.string.winter_sale_price_unavailable))
                            if (event.phase == WinterSalePhase.OnSale &&
                                event.observedServerNowEpochMillis - event.event.saleStartEpochMillis < 6 * 60 * 60 * 1000L) {
                                Text(stringResource(Res.string.winter_sale_updating))
                            }
                        }
                    }
                    if (state.loading) item { CircularProgressIndicator() }
                    if (content != null) {
                        if (content.sections.all { it.cards.isEmpty() } && !state.loading && !content.hasFailure &&
                            content.sections.none { it.checking || it.signInRequired }) {
                            item { Text(stringResource(Res.string.winter_sale_no_sales)) }
                        }
                        for (section in content.sections) {
                            if (section.cards.isEmpty() && !section.unavailable && !section.incomplete && !section.signInRequired && !section.checking) continue
                            item { Text(sectionTitle(section.kind), style = MaterialTheme.typography.titleLarge) }
                            if (section.signInRequired) item {
                                TextButton(onClick = { SignInPromptController.request() }) { Text(stringResource(Res.string.winter_sale_sign_in)) }
                            }
                            if (section.checking) item { Text(stringResource(Res.string.winter_sale_checking_series)) }
                            if (section.unavailable) item { Text(stringResource(Res.string.winter_sale_unavailable)) }
                            if (section.incomplete) item { Text(stringResource(Res.string.winter_sale_partial)) }
                            if (section.moreSeriesFollowed) item { Text(stringResource(Res.string.winter_sale_more_series)) }
                            if (section.shortFeed && section.cards.isNotEmpty()) item { Text(stringResource(Res.string.winter_sale_fewer_popular)) }
                            items(section.cards, key = { "${section.kind}:${it.candidate.gameId}" }) { card ->
                                WinterSaleDealCard(card, content.checkedAtEpochMillis, state.stalePrices, onGame, onOffer)
                            }
                        }
                        item { Text(stringResource(Res.string.winter_sale_attribution), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun sectionTitle(kind: WinterSaleGuideSectionKind): String = when (kind) {
    WinterSaleGuideSectionKind.Waitlist -> stringResource(Res.string.winter_sale_waitlist)
    WinterSaleGuideSectionKind.Series -> stringResource(Res.string.winter_sale_series)
    WinterSaleGuideSectionKind.Popular -> stringResource(Res.string.winter_sale_popular)
}

@Composable
private fun WinterSaleDealCard(card: WinterSaleGuideCard, fallbackCheckedAt: Long, stale: Boolean, onGame: (String) -> Unit, onOffer: (String) -> Unit) {
    val game = card.candidate
    val offer = game.offer
    Card(Modifier.fillMaxWidth().clickable { onGame(game.gameId) }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(game.title, style = MaterialTheme.typography.titleMedium)
            Text(card.reasons.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
            if (stale) {
                Text(stringResource(Res.string.winter_sale_price_unavailable))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(formatMoney(offer.price.amount, offer.price.currency), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.winter_sale_discount, offer.cutPercent))
                }
                offer.regular?.let { Text(stringResource(Res.string.winter_sale_was, formatMoney(it.amount, it.currency))) }
                if (offer.isAtHistoricalLow) Text(stringResource(Res.string.winter_sale_historical_low))
                Text(stringResource(Res.string.winter_sale_from_store, offer.shopName))
                Text(if (offer.activationLabels.isEmpty()) stringResource(Res.string.winter_sale_activation_unknown)
                    else stringResource(Res.string.winter_sale_activation, offer.activationLabels.joinToString(", ")))
                offer.voucher?.let { Text(stringResource(Res.string.winter_sale_voucher, it)) }
                offer.expiresAtEpochMillis?.let { Text(stringResource(Res.string.winter_sale_expires, winterSaleTime(it))) }
            }
            Text(stringResource(Res.string.winter_sale_checked, winterSaleTime(offer.checkedAtEpochMillis ?: fallbackCheckedAt)),
                style = MaterialTheme.typography.labelSmall)
            if (offer.url.isNotBlank()) TextButton(onClick = { onOffer(offer.url) }) {
                Text(stringResource(if (stale) Res.string.winter_sale_last_link else Res.string.winter_sale_offer_link))
            }
        }
    }
}
