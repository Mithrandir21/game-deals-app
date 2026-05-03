package pm.bam.gamedeals.remote.cheapshark.models


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteStore(
    // Cheapshark returns this as a JSON string ("1"), not a number. The previous
    // Retrofit setup may have silently tolerated the type mismatch; under Ktor's
    // ContentNegotiation + the same Json the payload throws SerializationException.
    // Keep it as String here and convert to Int in the domain mapper.
    @SerialName("storeID")
    val storeID: String,
    @SerialName("storeName")
    val storeName: String,
    @SerialName("isActive")
    val isActive: Int,
    @SerialName("images")
    val images: RemoteStoreImages
) {
    @Serializable
    data class RemoteStoreImages(
        @SerialName("banner")
        val banner: String,
        @SerialName("logo")
        val logo: String,
        @SerialName("icon")
        val icon: String
    )
}