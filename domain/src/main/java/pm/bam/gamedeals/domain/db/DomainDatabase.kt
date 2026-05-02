package pm.bam.gamedeals.domain.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.db.dao.GiveawaysDao
import pm.bam.gamedeals.domain.db.dao.PagingDao
import pm.bam.gamedeals.domain.db.dao.ReleasesDao
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.db.entities.DealEntity
import pm.bam.gamedeals.domain.db.entities.DealPageEntity
import pm.bam.gamedeals.domain.db.entities.GameEntity
import pm.bam.gamedeals.domain.db.entities.GiveawayEntity
import pm.bam.gamedeals.domain.db.entities.ReleaseEntity
import pm.bam.gamedeals.domain.db.entities.StoreEntity
import pm.bam.gamedeals.domain.utils.GiveawayPlatformsConverter
import pm.bam.gamedeals.domain.utils.LocalDatetimeConverter
import pm.bam.gamedeals.domain.utils.StoreImagesConverter

@Database(
    version = 3,
    entities = [
        DealEntity::class,
        DealPageEntity::class,
        GameEntity::class,
        StoreEntity::class,
        ReleaseEntity::class,
        GiveawayEntity::class,
    ],
    exportSchema = false,
)
@TypeConverters(StoreImagesConverter::class, GiveawayPlatformsConverter::class, LocalDatetimeConverter::class)
abstract class DomainDatabase : RoomDatabase() {

    internal abstract fun getDealsDao(): DealsDao

    internal abstract fun getGamesDao(): GamesDao

    internal abstract fun getStoresDao(): StoresDao

    internal abstract fun getPagingDao(): PagingDao

    internal abstract fun getReleasesDao(): ReleasesDao

    internal abstract fun getGiveawaysDao(): GiveawaysDao

}