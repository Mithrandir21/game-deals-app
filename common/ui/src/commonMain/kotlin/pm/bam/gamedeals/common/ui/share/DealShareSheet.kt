package pm.bam.gamedeals.common.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.components.DealBadgeCluster
import pm.bam.gamedeals.common.ui.components.PriceBlock
import pm.bam.gamedeals.common.ui.components.StoreLabel
import pm.bam.gamedeals.common.ui.components.formatBundleShortDate
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_button
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_ends
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_failed
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_footer
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_footer_site
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_link_only
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_lowest_ever
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_new_low
import pm.bam.gamedeals.common.ui.generated.resources.deal_share_sheet_title
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.platform.PlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme

/**
 * Shares deals for one screen. A [DealShare] without a card goes straight to the share sheet as text; one with a
 * card opens [DealShareSheet] first, so the person sees the image before sending it.
 *
 * ```
 * val dealSharer = rememberDealSharer()
 * DealShareSheet(dealSharer)
 * SingleEventEffect(viewModel.events) { event -> if (event is ShareDeal) dealSharer.share(event.share) }
 * ```
 */
@Stable
class DealSharer internal constructor(private val actions: PlatformActions) {

    internal var pending: DealShare? by mutableStateOf(null)

    fun share(share: DealShare) {
        if (share.card == null) actions.share(share.text) else pending = share
    }
}

@Composable
fun rememberDealSharer(): DealSharer {
    val actions = LocalPlatformActions.current
    return remember(actions) { DealSharer(actions) }
}

/** The card preview with Share and Share-the-link-only buttons; shows while [sharer] has a card to share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealShareSheet(sharer: DealSharer) {
    val share = sharer.pending ?: return
    val card = share.card ?: return
    val actions = LocalPlatformActions.current
    val capture = rememberShareCardCapture()
    val scope = rememberCoroutineScope()
    var failed by remember(share) { mutableStateOf(false) }
    val sheetColor = BottomSheetDefaults.ContainerColor

    // Open fully, so the Share button shows without dragging; the column scrolls on short screens.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { sharer.pending = null }, sheetState = sheetState, containerColor = sheetColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GameDealsCustomTheme.spacing.large)
                .padding(bottom = GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.deal_share_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            DealShareCardView(
                card = card,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().captureContent(capture, sheetColor),
            )
            Button(
                onClick = {
                    scope.launch {
                        val png = capture.capturePng()
                        if (png == null) {
                            failed = true
                        } else {
                            actions.shareImage(png, share.text)
                            sharer.pending = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.deal_share_button))
            }
            TextButton(onClick = {
                actions.share(share.text)
                sharer.pending = null
            }) {
                Text(stringResource(Res.string.deal_share_link_only))
            }
            if (failed) {
                Text(
                    text = stringResource(Res.string.deal_share_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * The shared deal image: the game's wide art with the discount and low-price badges, as on the app's deal tiles,
 * then the title, price, store, "lowest price ever" in words (the N and H letters mean nothing to someone without
 * the app), the end date and the app's name.
 */
@Composable
fun DealShareCardView(card: DealShareCard, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = card.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                error = painterResource(Res.drawable.videogame_thumb),
                fallback = painterResource(Res.drawable.videogame_thumb),
                modifier = Modifier.fillMaxSize(),
            )
            DealBadgeCluster(
                discountPercent = card.cutPercent,
                isNewHistoricalLow = card.isNewHistoricalLow,
                isLowestEver = card.isLowestEver,
                isStoreLow = card.isStoreLow,
                onArt = true,
                modifier = Modifier.align(Alignment.TopStart).padding(GameDealsCustomTheme.spacing.medium),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            PriceBlock(
                salePrice = card.priceDenominated,
                regularPrice = card.regularDenominated.takeIf { card.cutPercent > 0 },
                salePriceStyle = MaterialTheme.typography.headlineSmall,
                horizontalAlignment = Alignment.Start,
            )
            StoreLabel(storeName = card.storeName)
            val lowLine = when {
                card.isNewHistoricalLow -> stringResource(Res.string.deal_share_new_low)
                card.isLowestEver -> stringResource(Res.string.deal_share_lowest_ever)
                else -> null
            }
            if (lowLine != null) {
                Text(
                    text = lowLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            card.endsAtEpochMs?.let { endsAt ->
                Text(
                    text = stringResource(Res.string.deal_share_ends, formatBundleShortDate(endsAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.medium)) {
                Text(
                    text = stringResource(Res.string.deal_share_footer),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.deal_share_footer_site),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun DealShareCardView_Preview() {
    GameDealsTheme {
        DealShareCardView(
            card = DealShareCard(
                title = "Hades",
                coverUrl = null,
                priceDenominated = "$8.99",
                regularDenominated = "$24.99",
                cutPercent = 64,
                storeName = "Steam",
                isNewHistoricalLow = false,
                isLowestEver = true,
                isStoreLow = false,
                endsAtEpochMs = 1_796_000_000_000,
                link = "https://gamedeals.bam.pm/g/018d937f-33f0-7200-80fc-87f769196c84",
            ),
        )
    }
}
