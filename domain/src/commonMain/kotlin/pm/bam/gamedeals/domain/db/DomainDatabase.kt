package pm.bam.gamedeals.domain.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import pm.bam.gamedeals.domain.db.cache.BundlesCacheEntry
import pm.bam.gamedeals.domain.db.cache.DealDetailsCacheEntry
import pm.bam.gamedeals.domain.db.cache.GameDetailsCacheEntry
import pm.bam.gamedeals.domain.db.cache.PriceHistoryCacheEntry
import pm.bam.gamedeals.domain.db.cache.StatsRankingsCacheEntry
import pm.bam.gamedeals.domain.db.dao.BundlesCacheDao
import pm.bam.gamedeals.domain.db.dao.DealDetailsCacheDao
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.db.dao.GameDetailsCacheDao
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.db.dao.GiveawaysDao
import pm.bam.gamedeals.domain.db.dao.PriceHistoryCacheDao
import pm.bam.gamedeals.domain.db.dao.ReleasesDao
import pm.bam.gamedeals.domain.db.dao.StatsRankingsCacheDao
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.utils.GiveawayPlatformsConverter
import pm.bam.gamedeals.domain.utils.LocalDatetimeConverter
import pm.bam.gamedeals.domain.utils.StoreImagesConverter

internal const val DOMAIN_DB_VERSION = 16

@Database(
    version = DOMAIN_DB_VERSION,
    entities = [
        Deal::class, Game::class, Store::class, Release::class, Giveaway::class,
        DealDetailsCacheEntry::class, GameDetailsCacheEntry::class, PriceHistoryCacheEntry::class,
        BundlesCacheEntry::class, StatsRankingsCacheEntry::class,
    ],
    exportSchema = true,
)
@TypeConverters(StoreImagesConverter::class, GiveawayPlatformsConverter::class, LocalDatetimeConverter::class)
@ConstructedBy(DomainDatabaseConstructor::class)
abstract class DomainDatabase : RoomDatabase() {

    internal abstract fun getDealsDao(): DealsDao

    internal abstract fun getGamesDao(): GamesDao

    internal abstract fun getStoresDao(): StoresDao

    internal abstract fun getReleasesDao(): ReleasesDao

    internal abstract fun getGiveawaysDao(): GiveawaysDao

    internal abstract fun getDealDetailsCacheDao(): DealDetailsCacheDao

    internal abstract fun getGameDetailsCacheDao(): GameDetailsCacheDao

    internal abstract fun getPriceHistoryCacheDao(): PriceHistoryCacheDao

    internal abstract fun getBundlesCacheDao(): BundlesCacheDao

    internal abstract fun getStatsRankingsCacheDao(): StatsRankingsCacheDao

}

// Room generates the per-platform `actual` implementations from the `@Database` declaration.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object DomainDatabaseConstructor : RoomDatabaseConstructor<DomainDatabase> {
    override fun initialize(): DomainDatabase
}
