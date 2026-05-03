package pm.bam.gamedeals.remote.cheapshark.models


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteGame(
    @SerialName("gameID")
    val gameID: Int,
    @SerialName("steamAppID")
    val steamAppID: Int? = null,
    @SerialName("cheapest")
    val cheapest: Double,
    @SerialName("cheapestDealID")
    val cheapestDealID: String,
    @SerialName("external")
    val `external`: String,
    @SerialName("internalName")
    val internalName: String,
    @SerialName("thumb")
    val thumb: String
)

@Serializable
data class RemoteGameDetails(
    @SerialName("info")
    val info: RemoteGameInfo,
    @SerialName("cheapestPriceEver")
    val cheapestPriceEver: RemoteGameCheapestPriceEver,
    @SerialName("deals")
    val deals: List<RemoteGameDeal>
) {
    @Serializable
    data class RemoteGameInfo(
        @SerialName("title")
        val title: String,
        @SerialName("steamAppID")
        val steamAppID: Int? = null,
        @SerialName("thumb")
        val thumb: String
    )

    @Serializable
    data class RemoteGameCheapestPriceEver(
        @SerialName("price")
        val price: Double,
        @SerialName("date")
        val date: Long
    )

    @Serializable
    data class RemoteGameDeal(
        @SerialName("storeID")
        val storeID: Int,
        @SerialName("dealID")
        val dealID: String,
        @SerialName("price")
        val price: Double,
        @SerialName("retailPrice")
        val retailPrice: Double,
        @SerialName("savings")
        val savings: Double
    )
}