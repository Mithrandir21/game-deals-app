package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Game(
    @SerialName("gameID")
    val gameID: Int,
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
    @SerialName("thumb")
    val thumb: String
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
    @Serializable
    data class GameInfo(
        @SerialName("title")
        val title: String,
        @SerialName("steamAppID")
        val steamAppID: Int? = null,
        @SerialName("thumb")
        val thumb: String
    )

    @Serializable
    data class GameCheapestPriceEver(
        @SerialName("priceValue")
        val priceValue: Double,
        @SerialName("priceDenominated")
        val priceDenominated: String,
        @SerialName("date")
        val date: String
    )

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
        val savings: Int
    )
}
