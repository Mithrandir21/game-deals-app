package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pm.bam.gamedeals.domain.utils.millisInHour

@Immutable
@Entity(tableName = "Store")
@Serializable
data class Store(
    @PrimaryKey
    @SerialName("storeID")
    val storeID: Int,
    @SerialName("storeName")
    val storeName: String,
    @SerialName("isActive")
    val isActive: Boolean,
    @SerialName("images")
    val images: StoreImages,

    /**
     * An expiration date has been artificially to determine when
     * the Store should be considered as expired, set as now + (something time).
     *
     * @see millisInHour
     */
    @SerialName("expires")
    val expires: Long = System.currentTimeMillis().plus(millisInHour * 8)
) {
    @Immutable
    @Serializable
    data class StoreImages(
        @SerialName("banner")
        val banner: String,
        @SerialName("logo")
        val logo: String,
        @SerialName("icon")
        val icon: String
    )
}
