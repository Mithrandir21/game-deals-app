package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * Current best price + all-time low for a single game inside a bundle (Bundles redesign). Built from one
 * batched ITAD `/games/prices/v3` call over all the bundle's game ids; keyed by [gameId] for lookup against
 * a [Bundle.Tier]'s games on the detail screen. Best-effort and not cached — a game with no current deal is
 * either absent from the result or carries null `best*` fields (the UI shows "No current deal"), and the
 * "overall value" math skips the missing entries.
 *
 * All price fields are split into a raw `*Value` (for summing/comparison, in the region's currency) and a
 * denominated display string. `bestCutPercent` is the discount on the best deal (e.g. 76 → "-76%").
 */
@Immutable
data class BundleGamePrice(
    val gameId: String,
    val bestShopName: String?,
    val bestPriceValue: Double?,
    val bestPriceDenominated: String?,
    val bestCutPercent: Int?,
    val historicalLowValue: Double?,
    val historicalLowDenominated: String?,
    /** ITAD currency code (e.g. "EUR") of this region's prices — lets the VM format summed totals. */
    val currency: String? = null,
)
