@file:UseSerializers(ImmutableListSerializer::class)

package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import pm.bam.gamedeals.domain.utils.ImmutableListSerializer

@Entity(tableName = "Game")
@Immutable
@Serializable
data class Game(
    @PrimaryKey
    @SerialName("gameID")
    val gameID: String,
    @SerialName("steamAppID")
    val steamAppID: Int? = null,
    @SerialName("cheapestValue")
    val cheapestValue: Double,
    @SerialName("cheapestDenominated")
    val cheapestDenominated: String,
    @SerialName("cheapestDealID")
    val cheapestDealID: String,
    @SerialName("title")
    val title: String,
    @SerialName("internalName")
    val internalName: String,
    @Embedded(prefix = "art_")
    @SerialName("artwork")
    val artwork: GameArtwork = GameArtwork(),

    /**
     * Epoch-millisecond expiry stamp written when the entity is persisted by the repository.
     *
     * The repository stamps this via the injected `Clock` plus the resource's TTL when adding
     * fetched entities to the DAO (ITAD caching strategy, Phase 1 — TTL-gate). Persisted with SQL
     * `DEFAULT 0` (already-expired), which backs the v10→v11 `ADD COLUMN` migration: older cached
     * rows are treated as stale and refetch once on next access.
     */
    @SerialName("expires")
    @ColumnInfo(defaultValue = "0")
    val expires: Long = 0L
)

@Immutable
@Serializable
data class GameDetails(
    @SerialName("info")
    val info: GameInfo,
    @SerialName("cheapestPriceEver")
    val cheapestPriceEver: GameCheapestPriceEver,
    @SerialName("deals")
    val deals: ImmutableList<GameDeal>
) {
    @Immutable
    @Serializable
    data class GameInfo(
        @SerialName("title")
        val title: String,
        @SerialName("steamAppID")
        val steamAppID: Int? = null,
        @SerialName("artwork")
        val artwork: GameArtwork = GameArtwork()
    )

    @Immutable
    @Serializable
    data class GameCheapestPriceEver(
        @SerialName("priceValue")
        val priceValue: Double,
        @SerialName("priceDenominated")
        val priceDenominated: String,
        @SerialName("date")
        val date: String
    )

    @Immutable
    @Serializable
    data class GameDeal(
        @SerialName("storeID")
        val storeID: Int,
        @SerialName("dealID")
        val dealID: String,
        @SerialName("priceValue")
        val priceValue: Double,
        @SerialName("priceDenominated")
        val priceDenominated: String,
        @SerialName("retailPriceValue")
        val retailPriceValue: Double,
        @SerialName("retailPriceDenominated")
        val retailPriceDenominated: String,
        @SerialName("savings")
        val savings: Int,
        @SerialName("url")
        val url: String = ""
    )
}
