package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A game's historical low-price time series, plotted on the game screen (epic #205, Phase 3 — #208).
 *
 * Source-neutral: ITAD fills this from `/games/history/v2`; CheapShark cannot provide a full series and
 * returns an empty one. Not a Room entity — it is fetched on demand alongside the game details, never
 * cached. [points] are sorted oldest → newest.
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
