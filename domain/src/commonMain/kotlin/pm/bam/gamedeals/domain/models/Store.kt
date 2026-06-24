package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "Store")
@Immutable
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
     * Source-neutral store icon URL, intended for UI that should not depend on the provider's
     * image layout. The deal-source migration (epic #205) promoted this from a Phase-0 computed
     * property to a stored, source-filled column in Phase 2a; the source mapper fills it
     * (CheapShark from its [images] logo, ITAD blank until logos land in a later phase).
     */
    @SerialName("iconUrl")
    val iconUrl: String = "",

    /**
     * Epoch-millisecond expiry stamp written when the entity is persisted by the repository.
     *
     * The repository stamps this via the injected `Clock` plus the resource's TTL when adding
     * fetched entities to the DAO; defaults to `0L` (already-expired) so any unstamped entity
     * is considered stale by the cache.
     */
    @SerialName("expires")
    val expires: Long = 0L
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
