package pm.bam.gamedeals.domain.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Deal")
internal data class DealEntity(
    @PrimaryKey
    val dealID: String,
    val internalName: String,
    val title: String,
    val metacriticLink: String? = null,
    val storeID: Int,
    val gameID: Int,
    val salePriceValue: Double,
    val salePriceDenominated: String,
    val normalPriceValue: Double,
    val normalPriceDenominated: String,
    val isOnSale: Boolean,
    val savings: Double,
    val metacriticScore: Int,
    val steamRatingText: String? = null,
    val steamRatingPercent: Int,
    val steamRatingCount: String,
    val steamAppID: Int? = null,
    val releaseDate: Int,
    val lastChange: Int,
    val dealRating: Double,
    val thumb: String,
    val expires: Long = 0L,
)

@Entity(tableName = "DealPage")
internal data class DealPageEntity(
    @PrimaryKey
    @ColumnInfo(name = "storeID", collate = ColumnInfo.NOCASE)
    val storeID: Int,
    val page: Int,
)
