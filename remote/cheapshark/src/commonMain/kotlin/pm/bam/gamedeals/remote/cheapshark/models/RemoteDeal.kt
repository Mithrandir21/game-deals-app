package pm.bam.gamedeals.remote.cheapshark.models


import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RemoteDeal(
    @SerialName("internalName")
    val internalName: String,
    @SerialName("title")
    val title: String,
    @SerialName("metacriticLink")
    val metacriticLink: String? = null,
    @SerialName("dealID")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val dealID: String,
    @SerialName("storeID")
    val storeID: Int,
    @SerialName("gameID")
    val gameID: Int,
    @SerialName("salePrice")
    val salePrice: Double,
    @SerialName("normalPrice")
    val normalPrice: Double,
    @SerialName("isOnSale")
    val isOnSale: Int,
    @SerialName("savings")
    val savings: Double,
    @SerialName("metacriticScore")
    val metacriticScore: Int,
    @SerialName("steamRatingText")
    val steamRatingText: String? = null,
    @SerialName("steamRatingPercent")
    val steamRatingPercent: Int,
    @SerialName("steamRatingCount")
    val steamRatingCount: String,
    @SerialName("steamAppID")
    val steamAppID: Int? = null,
    @SerialName("releaseDate")
    val releaseDate: Int,
    @SerialName("lastChange")
    val lastChange: Int,
    @SerialName("dealRating")
    val dealRating: Double,
    @SerialName("thumb")
    val thumb: String
)

@Serializable
data class RemoteDealDetails(
    @SerialName("gameInfo")
    val gameInfo: RemoteGameInfo,
    @SerialName("cheaperStores")
    val cheaperStores: List<RemoteCheaperStore>,
    @SerialName("cheapestPrice")
    val cheapestPrice: RemoteCheapestPrice
) {

    @Serializable
    data class RemoteGameInfo(
        @SerialName("storeID")
        val storeID: Int,
        @SerialName("gameID")
        val gameID: Int,
        @SerialName("name")
        val name: String,
        @SerialName("steamAppID")
        val steamAppID: Int? = null,
        @SerialName("salePrice")
        val salePrice: Double,
        @SerialName("retailPrice")
        val retailPrice: Double,
        @SerialName("steamRatingText")
        val steamRatingText: String? = null,
        @SerialName("steamRatingPercent")
        val steamRatingPercent: Int,
        @SerialName("steamRatingCount")
        val steamRatingCount: String,
        @SerialName("metacriticScore")
        val metacriticScore: Int,
        @SerialName("metacriticLink")
        val metacriticLink: String? = null,
        @SerialName("releaseDate")
        val releaseDate: Long,
        @SerialName("publisher")
        val publisher: String,
        @SerialName("steamworks")
        val steamworks: Int? = null,
        @SerialName("thumb")
        val thumb: String
    )

    @Serializable
    data class RemoteCheaperStore(
        @SerialName("dealID")
        val dealID: String,
        @SerialName("storeID")
        val storeID: Int,
        @SerialName("salePrice")
        val salePrice: Double,
        @SerialName("retailPrice")
        val retailPrice: Double
    )

    @Serializable
    data class RemoteCheapestPrice(
        @SerialName("price")
        val price: Double? = null,
        @SerialName("date")
        val date: Long
    )
}