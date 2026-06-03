package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Entity(tableName = "Deal")
@Immutable
@Serializable
data class Deal(
    @PrimaryKey
    @SerialName("dealID")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val dealID: String,
    @SerialName("internalName")
    val internalName: String? = null,
    @SerialName("title")
    val title: String,
    @SerialName("metacriticLink")
    val metacriticLink: String? = null,
    @SerialName("storeID")
    val storeID: Int,
    @SerialName("gameID")
    val gameID: String,
    @SerialName("salePriceValue")
    val salePriceValue: Double,
    @SerialName("salePriceDenominated")
    val salePriceDenominated: String,
    @SerialName("normalPriceValue")
    val normalPriceValue: Double,
    @SerialName("normalPriceDenominated")
    val normalPriceDenominated: String,
    @SerialName("isOnSale")
    val isOnSale: Boolean,
    @SerialName("savings")
    val savings: Double,
    @SerialName("metacriticScore")
    val metacriticScore: Int? = null,
    @SerialName("steamRatingText")
    val steamRatingText: String? = null,
    @SerialName("steamRatingPercent")
    val steamRatingPercent: Int? = null,
    @SerialName("steamRatingCount")
    val steamRatingCount: String? = null,
    @SerialName("steamAppID")
    val steamAppID: Int? = null,
    @SerialName("releaseDate")
    val releaseDate: Int? = null,
    @SerialName("lastChange")
    val lastChange: Int? = null,
    @SerialName("dealRating")
    val dealRating: Double? = null,
    @SerialName("thumb")
    val thumb: String,

    /**
     * Source-neutral deep link to this deal at its store, read by the UI and share text.
     *
     * The deal-source migration (epic #205) promoted this from a Phase-0 computed property to a
     * stored, source-filled column in Phase 2a; the source mapper fills it (CheapShark with its
     * redirect URL, ITAD with the direct affiliate URL).
     */
    @SerialName("url")
    val url: String = "",

    /**
     * Epoch-millisecond expiry stamp written when the entity is persisted by the repository.
     *
     * The repository stamps this via the injected `Clock` plus the resource's TTL when adding
     * fetched entities to the DAO; defaults to `0L` (already-expired) so any unstamped entity
     * is considered stale by the cache.
     */
    @SerialName("expires")
    val expires: Long = 0L
)

@Immutable
@Serializable
data class DealDetails(
    @SerialName("gameInfo")
    val gameInfo: GameInfo,
    @SerialName("cheaperStores")
    val cheaperStores: ImmutableList<CheaperStore>,
    @SerialName("cheapestPrice")
    val cheapestPrice: CheapestPrice? = null
) {

    @Immutable
    @Serializable
    data class GameInfo(
        @SerialName("storeID")
        val storeID: Int,
        @SerialName("gameID")
        val gameID: String,
        @SerialName("name")
        val name: String,
        @SerialName("steamAppID")
        val steamAppID: Int? = null,
        @SerialName("salePriceValue")
        val salePriceValue: Double,
        @SerialName("salePriceDenominated")
        val salePriceDenominated: String,
        @SerialName("retailPriceValue")
        val retailPriceValue: Double,
        @SerialName("retailPriceDenominated")
        val retailPriceDenominated: String,
        @SerialName("steamRatingText")
        val steamRatingText: String? = null,
        @SerialName("steamRatingPercent")
        val steamRatingPercent: Int? = null,
        @SerialName("steamRatingCount")
        val steamRatingCount: String? = null,
        @SerialName("metacriticScore")
        val metacriticScore: Int? = null,
        @SerialName("metacriticLink")
        val metacriticLink: String? = null,
        @SerialName("releaseDate")
        val releaseDate: String? = null,
        @SerialName("publisher")
        val publisher: String? = null,
        @SerialName("steamworks")
        val steamworks: Boolean? = null,
        @SerialName("thumb")
        val thumb: String
    )

    @Immutable
    @Serializable
    data class CheaperStore(
        @SerialName("dealID")
        val dealID: String,
        @SerialName("storeID")
        val storeID: Int,
        @SerialName("salePriceValue")
        val salePriceValue: Double,
        @SerialName("salePriceDenominated")
        val salePriceDenominated: String,
        @SerialName("retailPrice")
        val retailPriceValue: Double,
        @SerialName("retailPriceDenominated")
        val retailPriceDenominated: String,
        @SerialName("url")
        val url: String = ""
    )

    @Immutable
    @Serializable
    data class CheapestPrice(
        @SerialName("priceValue")
        val priceValue: Double,
        @SerialName("priceDenominated")
        val priceDenominated: String,
        @SerialName("date")
        val date: String
    )
}

