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
        val priceDenominated: String
    )
}
