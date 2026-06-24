@file:UseSerializers(ImmutableListSerializer::class)

package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import pm.bam.gamedeals.domain.utils.ImmutableListSerializer

/**
 * A game's historical low-price time series, plotted on the game screen (epic #205, Phase 3 — #208).
 *
 * Source-neutral: ITAD fills this from `/games/history/v2`; CheapShark cannot provide a full series and
 * returns an empty one. [points] are sorted oldest → newest.
 *
 * Serialized to a region-keyed blob in `PriceHistoryCache` (ITAD caching strategy, Phase 4, #265) — the
 * `@file:UseSerializers` makes the [ImmutableList] field round-trip through kotlinx-serialization. The
 * series is append-only, so the cache refreshes incrementally via the `since` parameter rather than
 * refetching the whole log.
 */
@Immutable
@Serializable
data class PriceHistory(
    @SerialName("gameID")
    val gameID: String,
    @SerialName("points")
    val points: ImmutableList<PricePoint>
) {
    @Immutable
    @Serializable
    data class PricePoint(
        @SerialName("timestampEpochMs")
        val timestampEpochMs: Long,
        @SerialName("priceValue")
        val priceValue: Double,
        @SerialName("priceDenominated")
        val priceDenominated: String,
        /**
         * Discount applied at this point, as a whole percentage (e.g. `75` = −75%); `0` when the
         * price was the regular one. New in the chart-enrichment work — defaulted so older cached
         * blobs (serialized before this field existed) still deserialize.
         */
        @SerialName("cutPercent")
        val cutPercent: Int = 0,
        /**
         * The regular (non-sale) price at this point, when ITAD reported one. Drives the chart's MSRP
         * reference line (latest non-null value wins). Defaulted for cache back-compat.
         */
        @SerialName("regularValue")
        val regularValue: Double? = null,
        /** The shop offering this price (e.g. "Steam"), shown in the chart's scrub tooltip. */
        @SerialName("shopName")
        val shopName: String? = null,
    )
}
