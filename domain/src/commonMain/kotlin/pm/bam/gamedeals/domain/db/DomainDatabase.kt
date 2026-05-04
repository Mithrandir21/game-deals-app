package pm.bam.gamedeals.domain.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.db.dao.GiveawaysDao
import pm.bam.gamedeals.domain.db.dao.ReleasesDao
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.utils.GiveawayPlatformsConverter
import pm.bam.gamedeals.domain.utils.LocalDatetimeConverter
import pm.bam.gamedeals.domain.utils.StoreImagesConverter

// Bumped 3 → 4 when the `DealPage` entity (Paging-3 RemoteMediator cursor table)
// was dropped along with the rest of the paging surface. fallbackToDestructive-
// Migration handles the schema delta; cached deal data is regenerated on next
// fetch.
@Database(version = 4, entities = [Deal::class, Game::class, Store::class, Release::class, Giveaway::class], exportSchema = false)
@TypeConverters(StoreImagesConverter::class, GiveawayPlatformsConverter::class, LocalDatetimeConverter::class)
@ConstructedBy(DomainDatabaseConstructor::class)
abstract class DomainDatabase : RoomDatabase() {

    internal abstract fun getDealsDao(): DealsDao

    internal abstract fun getGamesDao(): GamesDao

    internal abstract fun getStoresDao(): StoresDao

    internal abstract fun getReleasesDao(): ReleasesDao

    internal abstract fun getGiveawaysDao(): GiveawaysDao

}

// Room generates the per-platform `actual` implementations from the `@Database` declaration.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object DomainDatabaseConstructor : RoomDatabaseConstructor<DomainDatabase> {
    override fun initialize(): DomainDatabase
}
