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
