package pm.bam.gamedeals.domain.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pm.bam.gamedeals.common.serializer.Serializer
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.db.dao.GiveawaysDao
import pm.bam.gamedeals.domain.db.dao.ReleasesDao
import pm.bam.gamedeals.domain.db.dao.StoresDao
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepositoryImpl
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepositoryImpl
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepositoryImpl
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.domain.source.GamerPowerSource
import pm.bam.gamedeals.domain.utils.GiveawayPlatformsConverter
import pm.bam.gamedeals.domain.utils.LocalDateSerializer
import pm.bam.gamedeals.domain.utils.LocalDatetimeConverter
import pm.bam.gamedeals.domain.utils.StoreImagesConverter
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.verbose
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module(includes = [InternalDomainModule::class])
@InstallIn(SingletonComponent::class)
class DomainModule {

    @Provides
    @Domain
    fun provideDomainSharedPreference(@ApplicationContext appContext: Context): SharedPreferences =
        appContext.getSharedPreferences("gamedeals_domain_storage", Context.MODE_PRIVATE)
}

@Module
@InstallIn(SingletonComponent::class)
internal class InternalDomainModule {

    @Provides
    @Domain
    fun provideStoreImagesConverter(serializer: Serializer) = StoreImagesConverter(serializer)

    @Provides
    @Domain
    fun provideGiveawayPlatformsConverter() = GiveawayPlatformsConverter()

    @Provides
    @Domain
    fun provideLocalDatetimeConverter() = LocalDatetimeConverter()

    @Provides
    fun provideLocalDateSerializer() = LocalDateSerializer()

    @Provides
    @Singleton
    fun provideDealsRepository(
        logger: Logger,
        dealsDao: DealsDao,
        db: DomainDatabase,
        cheapsharkSource: CheapsharkSource
    ): DealsRepository =
        DealsRepositoryImpl(logger, dealsDao, db, cheapsharkSource)

    @Provides
    @Singleton
    fun provideGamesRepository(
        gamesDao: GamesDao,
        cheapsharkSource: CheapsharkSource
    ): GamesRepository =
        GamesRepositoryImpl(gamesDao, cheapsharkSource)

    @Provides
    @Singleton
    fun provideStoresRepository(logger: Logger, storesDao: StoresDao, cheapsharkSource: CheapsharkSource): StoresRepository =
        StoresRepositoryImpl(logger, storesDao, cheapsharkSource)

    @Provides
    @Singleton
    fun provideReleasesRepository(logger: Logger, releasesDao: ReleasesDao, cheapsharkSource: CheapsharkSource): ReleasesRepository =
        ReleasesRepositoryImpl(logger, releasesDao, cheapsharkSource)

    @Provides
    @Singleton
    fun provideGiveawayRepository(logger: Logger, giveawaysDao: GiveawaysDao, gamerPowerSource: GamerPowerSource): GiveawaysRepository =
        GiveawaysRepositoryImpl(logger, giveawaysDao, gamerPowerSource)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        logger: Logger,
        @Domain storeImagesConverter: StoreImagesConverter,
        @Domain giveawayPlatformsConverter: GiveawayPlatformsConverter,
        @Domain localDatetimeConverter: LocalDatetimeConverter
    ): DomainDatabase =
        Room.databaseBuilder(context, DomainDatabase::class.java, "${DomainDatabase::class.java.simpleName}.db")
            .fallbackToDestructiveMigration()
            .addTypeConverter(storeImagesConverter)
            .addTypeConverter(giveawayPlatformsConverter)
            .addTypeConverter(localDatetimeConverter)
            .setQueryCallback({ sqlQuery, bindArgs ->
                verbose(logger) { "SQL Query: $sqlQuery SQL Args: $bindArgs" }
            }, Executors.newSingleThreadExecutor())
            .build()

    @Provides
    @Singleton
    fun provideDealsDao(db: DomainDatabase): DealsDao = db.getDealsDao()

    @Provides
    @Singleton
    fun provideGamesDao(db: DomainDatabase): GamesDao = db.getGamesDao()

    @Provides
    @Singleton
    fun provideStoresDao(db: DomainDatabase): StoresDao = db.getStoresDao()

    @Provides
    @Singleton
    fun provideReleasesDao(db: DomainDatabase): ReleasesDao = db.getReleasesDao()

    @Provides
    @Singleton
    fun provideGiveawaysDao(db: DomainDatabase): GiveawaysDao = db.getGiveawaysDao()
}
