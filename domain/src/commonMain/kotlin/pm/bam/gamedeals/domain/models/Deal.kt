@file:UseSerializers(ImmutableListSerializer::class)

package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import pm.bam.gamedeals.domain.utils.ImmutableListSerializer

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
    val expires: Long = 0L,

    /**
     * `true` when this deal's price is at the game's all-time historical low (UI Improvements board,
     * Phase E, #255). Filled by the source mapper from ITAD's deal `flag` (`"N"` new low / `"H"` at
     * historical low); both the deals and prices endpoints carry it.
     *
     * Retained as a stored column for backward compatibility, but the deal surfaces no longer render
     * it directly — historical-low status is now shown via the new-low badge ([isNewHistoricalLow]).
     *
     * Persisted (so it survives the Room round-trip on the cached Home/Store surfaces) with a SQL
     * `DEFAULT 0` — that default also backs the v8→v9 `ADD COLUMN` migration and matches older cached
     * rows, which stay `false` until their next TTL refetch.
     */
    @SerialName("isLowestEver")
    @ColumnInfo(defaultValue = "0")
    val isLowestEver: Boolean = false,

    /**
     * `true` when ITAD's deal `flag == "N"` — the price *just* hit a new all-time low. Drives the
     * orange "N" new-low badge on the deal tiles/rows (deal-badge work). Persisted with SQL
     * `DEFAULT 0`, backing the v9→v10 `ADD COLUMN` migration; older cached rows stay `false` until
     * their next TTL refetch.
     */
    @SerialName("isNewHistoricalLow")
    @ColumnInfo(defaultValue = "0")
    val isNewHistoricalLow: Boolean = false,

    /**
     * `true` when ITAD's deal `flag == "S"` — the lowest price this specific store has ever offered.
     * Drives the "S" store-low badge. Persisted with SQL `DEFAULT 0` (see [isNewHistoricalLow]).
     */
    @SerialName("isStoreLow")
    @ColumnInfo(defaultValue = "0")
    val isStoreLow: Boolean = false,

    /**
     * `true` when the deal requires a voucher/coupon code at the store. Drives the "with voucher"
     * scissors badge. Persisted with SQL `DEFAULT 0` (see [isNewHistoricalLow]).
     */
    @SerialName("hasVoucher")
    @ColumnInfo(defaultValue = "0")
    val hasVoucher: Boolean = false,

    /**
     * The storefront region ([Country.code]) this deal's price/currency was fetched for — the cache's
     * region dimension (ITAD caching strategy, D5 / Phase 2, #263). The repository stamps it (from
     * [RegionRepository][pm.bam.gamedeals.domain.repositories.region.RegionRepository]) on write and
     * filters reads by it, so a region switch reads the new region's rows instead of clearing the
     * cache (#212). Persisted with SQL `DEFAULT 'US'` ([DEFAULT_COUNTRY]) — that default backs the
     * v11→v12 `ADD COLUMN` migration, which backfills existing rows to the default region.
     */
    @SerialName("country")
    @ColumnInfo(defaultValue = "US")
    val country: String = DEFAULT_COUNTRY.code
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

