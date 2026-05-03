package pm.bam.gamedeals.remote.cheapshark.models


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteStore(
    @SerialName("storeID")
    val storeID: Int,
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