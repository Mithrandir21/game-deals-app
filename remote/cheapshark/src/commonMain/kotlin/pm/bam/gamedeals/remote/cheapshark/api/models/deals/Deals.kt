package pm.bam.gamedeals.remote.cheapshark.api.models.deals

import kotlinx.serialization.SerialName

data class RemoteDealsQuery(
    val storeID: Int? = null,
    val pageNumber: Int? = null,
    val pageSize: Int? = null,
    val sortBy: RemoteDealsSortBy? = RemoteDealsSortBy.DEALRATING,
    val desc: Int? = null,
    val lowerPrice: Int? = null,
    val upperPrice: Int? = null,
    val metacritic: Int? = null,
    val steamRating: Int? = null,
    val maxAge: Int? = null,
    val steamAppID: Int? = null,
    val title: String? = null,
    val exact: Int? = null,
    val aaa: Int? = null,
    val steamworks: Int? = null,
    val onSale: Int? = null
)

enum class RemoteDealsSortBy {

    @SerialName("DealRating")
    DEALRATING,

    @SerialName("Title")
    TITLE,

    @SerialName("Savings")
    SAVINGS,

    @SerialName("Price")
    PRICE,

    @SerialName("Metacritic")
    METACRITIC,

    @SerialName("Reviews")
    REVIEWS,

    @SerialName("Release")
    RELEASE,

    @SerialName("Store")
    STORE,

    @SerialName("Recent")
    RECENT

}