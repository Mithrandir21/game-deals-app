package pm.bam.gamedeals.domain.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import pm.bam.gamedeals.domain.models.Store

@Entity(tableName = "Store")
internal data class StoreEntity(
    @PrimaryKey
    val storeID: Int,
    val storeName: String,
    val isActive: Boolean,
    val images: Store.StoreImages,
    val expires: Long = 0L,
)
