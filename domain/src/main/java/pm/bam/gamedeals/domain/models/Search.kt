package pm.bam.gamedeals.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.properties.Properties

@ExperimentalSerializationApi
@Serializable
data class SearchParameters(
    val storeID: Int? = null,
    val pageNumber: Int? = null,
    val pageSize: Int? = null,
    val sortBy: DealsSortBy? = DealsSortBy.DEALRATING,
    val desc: Int? = null,
    val lowerPrice: Int? = null,
    val upperPrice: Int? = null,
    val metacritic: Int? = null,
    val steamMinRating: Int? = null,
    val maxAge: Int? = null,
    val steamAppID: Int? = null,
    val title: String? = null,
    val exact: Boolean? = null,
    val aaa: Boolean? = null,
    val steamworks: Boolean? = null,
    val onSale: Boolean? = null
) {

    /**
     * Encodes properties from the this [SearchParameters] to a map.
     * `null` values are omitted from the output.
     *
     * @see SearchParameters.from
     */
    fun asMap() = Properties.encodeToMap(serializer(), this)

    companion object {
        /**
         * Decodes properties from the given [map] to a value of type [SearchParameters].
         * [SearchParameters] may contain properties of nullable types; they will be filled by non-null values from the [map], if present.
         */
        fun from(map: Map<String, Any?>): SearchParameters = Properties.decodeFromMap(serializer(),
            // Removes any map Key/Value pairs where the Value is NULL.
            map.mapNotNull { (key, value) -> value?.let { key to it } }
                .toMap())
    }
}

enum class DealsSortBy {

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
