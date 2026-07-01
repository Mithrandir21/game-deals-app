@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pm.bam.gamedeals.common.ui.components.StoreIcon
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_page_bundle_games_count
import pm.bam.gamedeals.feature.game.generated.resources.game_page_section_bundles
import pm.bam.gamedeals.feature.game.generated.resources.game_page_history_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_page_prices_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_page_regions_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_page_regions_error
import pm.bam.gamedeals.feature.game.generated.resources.game_page_regions_expander
import pm.bam.gamedeals.feature.game.generated.resources.game_page_regions_loading_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_list_item_savings_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_share_action
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_store_deal_row_description
import pm.bam.gamedeals.feature.game.ui.GamePageViewModel.GamePageData
import pm.bam.gamedeals.logging.analytics.Analytics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import androidx.compose.material3.Surface

// ----- Deal tab -------------------------------------------------------------------------------------------

/** All store deals, the price-history chart (which already marks the all-time low), regional prices (lazy, behind an expander) and bundles. */
@Composable
internal fun DealTab(
    data: GamePageData.Data,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onShareDeal: (GameDetails.GameInfo, Store, GameDetails.GameDeal) -> Unit,
    onBundleClick: (bundleId: Int) -> Unit,
    onRegionsSelected: () -> Unit,
    onRetrySection: (RetrySection) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.large),
    ) {
        if (data.bundles.isNotEmpty()) BundlesSection(data.bundles, onBundleClick)
        when (val deals = data.deals) {
            SectionState.Loading -> TabLoading()
            SectionState.Error -> TabError(onRetry = { onRetrySection(RetrySection.Deals) })
            is SectionState.Loaded -> {
                val gameDetails = deals.value
                if (gameDetails == null || data.dealDetails.isEmpty()) {
                    TabEmpty(stringResource(Res.string.game_page_prices_empty))
                } else {
                    data.dealDetails.forEach { pair ->
                        StoreGameDealRow(gameId = data.gameId, store = pair.store, gameInfo = gameDetails.info, deal = pair.deal, goToWeb = goToWeb, onShareDeal = onShareDeal)
                    }
                }
            }
        }
        when (val priceHistory = data.priceHistory) {
            SectionState.Loading -> TabLoading()
            SectionState.Error -> TabError(onRetry = { onRetrySection(RetrySection.PriceHistory) })
            is SectionState.Loaded ->
                if (priceHistory.value.points.isEmpty()) TabEmpty(stringResource(Res.string.game_page_history_empty))
                else PriceHistoryChart(priceHistory = priceHistory.value, modifier = Modifier.fillMaxWidth())
        }
        RegionalPricesExpander(state = data.regionalPricesState, gameTitle = data.title, goToWeb = goToWeb, onExpand = onRegionsSelected)
    }
}

/** Regional cross-country prices, collapsed behind an expander so the N per-country fetch only runs on demand. */
@Composable
private fun RegionalPricesExpander(
    state: GamePageViewModel.RegionalPricesState,
    gameTitle: String,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onExpand: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (expanded) onExpand() }
    Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = GameDealsCustomTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(Res.string.game_page_regions_expander), Modifier.weight(1f))
            Icon(imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (expanded) RegionsContent(state = state, gameTitle = gameTitle, goToWeb = goToWeb)
    }
}

@Composable
private fun RegionsContent(
    state: GamePageViewModel.RegionalPricesState,
    gameTitle: String,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    when (state) {
        GamePageViewModel.RegionalPricesState.Idle,
        GamePageViewModel.RegionalPricesState.Loading -> {
            val loadingCd = stringResource(Res.string.game_page_regions_loading_cd)
            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).semantics { contentDescription = loadingCd })
            }
        }
        GamePageViewModel.RegionalPricesState.Error -> Text(
            text = stringResource(Res.string.game_page_regions_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        is GamePageViewModel.RegionalPricesState.Loaded -> {
            if (state.items.isEmpty()) {
                Text(text = stringResource(Res.string.game_page_regions_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                state.items.forEachIndexed { index, region ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { goToWeb(region.url, gameTitle) }.padding(vertical = GameDealsCustomTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = region.country.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(text = region.priceDenominated, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreGameDealRow(
    gameId: String?,
    store: Store,
    gameInfo: GameDetails.GameInfo,
    deal: GameDetails.GameDeal,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    onShareDeal: (GameDetails.GameInfo, Store, GameDetails.GameDeal) -> Unit,
) {
    // Records the click-through to a store before the in-app browser opens.
    val analytics: Analytics = koinInject()
    val rowCd = stringResource(Res.string.game_screen_store_deal_row_description, store.storeName, deal.savings, deal.priceDenominated)
    Card(onClick = { openStoreDeal(analytics, gameId, store, deal, gameInfo.title, goToWeb) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = rowCd },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoreIcon(storeName = store.storeName, iconUrl = store.iconUrl, iconSize = GameDealsCustomTheme.spacing.large, contentDescription = null)
                Text(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.medium),
                    text = store.storeName,
                )
                Text(
                    modifier = Modifier.padding(GameDealsCustomTheme.spacing.medium),
                    text = stringResource(Res.string.game_screen_list_item_savings_label, deal.savings),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(GameDealsCustomTheme.spacing.extraSmall))
                        .padding(GameDealsCustomTheme.spacing.medium),
                    text = deal.priceDenominated,
                )
            }
            IconButton(onClick = { onShareDeal(gameInfo, store, deal) }) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = stringResource(Res.string.game_screen_share_action, store.storeName))
            }
        }
    }
}

@Composable
private fun BundlesSection(bundles: List<Bundle>, onBundleClick: (bundleId: Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(GameDealsCustomTheme.spacing.large), verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
            SectionHeader(stringResource(Res.string.game_page_section_bundles))
            bundles.forEachIndexed { index, bundle ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onBundleClick(bundle.id) }.padding(vertical = GameDealsCustomTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = bundle.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${bundle.storeName} · ${stringResource(Res.string.game_page_bundle_games_count, bundle.gameCount.toString())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    bundle.priceDenominated?.let { Text(text = it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}


@Preview
@Composable
private fun BundlesSectionPreview() {
    GameDealsTheme { Surface { Box(Modifier.padding(GameDealsCustomTheme.spacing.large)) { BundlesSection(PreviewBundles, onBundleClick = {}) } } }
}

@Preview
@Composable
private fun RegionsContentPreview() {
    GameDealsTheme {
        Surface {
            Column(Modifier.padding(GameDealsCustomTheme.spacing.large)) {
                RegionsContent(
                    state = GamePageViewModel.RegionalPricesState.Loaded(PreviewRegionalPrices),
                    gameTitle = "Hades",
                    goToWeb = { _, _ -> },
                )
            }
        }
    }
}

@Preview
@Composable
private fun StoreGameDealRowPreview() {
    GameDealsTheme {
        Surface {
            PreviewKoin {
                val pair = PreviewStoreDeals.first()
                Column(Modifier.padding(GameDealsCustomTheme.spacing.large)) {
                    StoreGameDealRow(
                        gameId = "g1",
                        store = pair.store,
                        gameInfo = PreviewGameDetails.info,
                        deal = pair.deal,
                        goToWeb = { _, _ -> },
                        onShareDeal = { _, _, _ -> },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun DealTabPreview() {
    GameDealsTheme {
        Surface {
            PreviewKoin {
                DealTab(
                    data = PreviewGamePageData,
                    goToWeb = { _, _ -> },
                    onShareDeal = { _, _, _ -> },
                    onBundleClick = {},
                    onRegionsSelected = {},
                    onRetrySection = {},
                )
            }
        }
    }
}
