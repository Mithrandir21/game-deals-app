package pm.bam.gamedeals.domain.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.utils.GiveawayPlatformsConverter
import pm.bam.gamedeals.domain.utils.LocalDateSerializer
import pm.bam.gamedeals.domain.utils.LocalDatetimeConverter
import pm.bam.gamedeals.domain.utils.StoreImagesConverter
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.verbose
import java.util.concurrent.Executors

val domainModule = module {
    single { StoreImagesConverter(get()) }
    single { GiveawayPlatformsConverter() }
    single { LocalDatetimeConverter() }
    single { LocalDateSerializer() }

    single<DomainDatabase> {
        val logger = get<Logger>()
        Room.databaseBuilder(
            androidContext(),
            DomainDatabase::class.java,
            "${DomainDatabase::class.java.simpleName}.db"
        )
            .fallbackToDestructiveMigration()
            .addTypeConverter(get<StoreImagesConverter>())
            .addTypeConverter(get<GiveawayPlatformsConverter>())
            .addTypeConverter(get<LocalDatetimeConverter>())
            .setQueryCallback({ sqlQuery, bindArgs ->
                verbose(logger) { "SQL Query: $sqlQuery SQL Args: $bindArgs" }
            }, Executors.newSingleThreadExecutor())
            .build()
    }

    single { get<DomainDatabase>().getDealsDao() }
    single { get<DomainDatabase>().getGamesDao() }
    single { get<DomainDatabase>().getStoresDao() }
    single { get<DomainDatabase>().getReleasesDao() }
    single { get<DomainDatabase>().getGiveawaysDao() }

    single { DealsRepository(get(), get(), get(), get(), get()) }
    single { StoresRepository(get(), get(), get(), get()) }
    single { GamesRepository(get(), get()) }
    single { GiveawaysRepository(get(), get(), get()) }
    single { ReleasesRepository(get(), get(), get()) }
}
