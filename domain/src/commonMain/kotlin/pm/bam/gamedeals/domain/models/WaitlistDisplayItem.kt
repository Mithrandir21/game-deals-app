package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A waitlist row enriched for the "buy-decision dashboard": the [WaitlistEntry] merged with the current best
 * deal (from a batched `/games/prices/v3` call). Price fields are null when the game has no current deal
 * (the UI shows "no current deal") or when the cached snapshot's region no longer matches the user's region.
 * [Serializable] so the whole list is persisted wholesale as a JSON blob (see WaitlistDisplayStore).
 */
@Immutable
@Serializable
data class WaitlistDisplayItem(
    val gameId: String,
    val title: String,
    val artwork: GameArtwork = GameArtwork(),
    val type: String? = null,
    val addedEpochMs: Long? = null,
    val bestPriceDenominated: String? = null,
    val bestRegularDenominated: String? = null,
    val bestShopName: String? = null,
    val discountPercent: Int? = null,
    /** Raw best price in the region's currency — for price-low→high sorting/comparison. */
    val bestPriceValue: Double? = null,
    /** All-time low value — for the "now at lowest ever" comparison. */
    val historicalLowValue: Double? = null,
    val hasVoucher: Boolean = false,
    val isNewHistoricalLow: Boolean = false,
    val isStoreLow: Boolean = false,
) {
    /** True when there is a current deal to show prices for. */
    val hasDeal: Boolean get() = bestPriceDenominated != null

    /**
     * True when the current best price is at (or below) the all-time low. ITAD has no "currently at
     * historical low" flag on the price endpoint, so we compare the value (the "N" new-low flag is only set
     * the instant a new low is reached — too narrow for "now at lowest").
     */
    val isAtHistoricalLow: Boolean
        get() = bestPriceValue != null && historicalLowValue != null && bestPriceValue <= historicalLowValue
}

/**
 * Persisted snapshot of the enriched waitlist. [regionCode] lets the display layer detect a stale region
 * (prices are region-specific) and suppress prices until a refresh completes.
 */
@Serializable
data class WaitlistDisplaySnapshot(
    val items: List<WaitlistDisplayItem> = emptyList(),
    val regionCode: String = "",
    val refreshedAtEpochMs: Long = 0L,
)
