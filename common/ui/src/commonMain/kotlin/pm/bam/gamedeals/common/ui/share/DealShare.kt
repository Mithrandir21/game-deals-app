package pm.bam.gamedeals.common.ui.share

import androidx.compose.runtime.Immutable
import pm.bam.gamedeals.common.links.ShareLinks
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags

/**
 * What sharing a deal hands to [DealSharer]: the text for the share sheet and, when deal links are on, the image
 * card to preview and share with it.
 */
@Immutable
data class DealShare(
    val text: String,
    val card: DealShareCard? = null,
)

/** Everything the shared deal image shows ([DealShareCardView]). */
@Immutable
data class DealShareCard(
    val title: String,
    val coverUrl: String?,
    val priceDenominated: String,
    val regularDenominated: String,
    val cutPercent: Int,
    val storeName: String,
    val isNewHistoricalLow: Boolean,
    val isLowestEver: Boolean,
    val isStoreLow: Boolean,
    /** When the sale ends, or null when the store gives no end time. */
    val endsAtEpochMs: Long?,
    /** The game's gamedeals.bam.pm link, carried in the share text next to the card. */
    val link: String,
)

/**
 * Builds what a deal share carries. Behind an interface so ViewModel unit tests can mock it and assert the
 * arguments without coupling to the exact wording.
 */
interface DealShareBuilder {
    fun build(
        gameId: String,
        gameTitle: String,
        coverUrl: String?,
        storeName: String,
        deal: GameDetails.GameDeal,
    ): DealShare
}

/**
 * With [FeatureFlag.DealLinks] on, the text links to the game's gamedeals.bam.pm page (the app if installed,
 * otherwise a preview page with every store) and an image card comes with it. With it off, or for an id that
 * can't make a link, the text carries the store's own deal link as before and there is no card.
 */
internal class DefaultDealShareBuilder(
    private val featureFlags: FeatureFlags,
) : DealShareBuilder {

    override fun build(
        gameId: String,
        gameTitle: String,
        coverUrl: String?,
        storeName: String,
        deal: GameDetails.GameDeal,
    ): DealShare {
        val link = ShareLinks.game(gameId)?.takeIf { featureFlags.isEnabled(FeatureFlag.DealLinks) }
            ?: return DealShare(text = "$gameTitle — ${deal.priceDenominated} at $storeName: ${deal.url}")
        return DealShare(
            text = "$gameTitle — ${deal.priceDenominated} at $storeName: $link",
            card = DealShareCard(
                title = gameTitle,
                coverUrl = coverUrl?.takeIf { it.isNotBlank() },
                priceDenominated = deal.priceDenominated,
                regularDenominated = deal.retailPriceDenominated,
                cutPercent = deal.savings,
                storeName = storeName,
                isNewHistoricalLow = deal.isNewHistoricalLow,
                isLowestEver = deal.isLowestEver,
                isStoreLow = deal.isStoreLow,
                endsAtEpochMs = deal.expiryEpochMs,
                link = link,
            ),
        )
    }
}
