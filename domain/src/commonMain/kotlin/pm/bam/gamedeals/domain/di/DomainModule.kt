package pm.bam.gamedeals.domain.di

import androidx.room.RoomDatabase
import org.koin.dsl.module
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepositoryImpl
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepository
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepositoryImpl
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepositoryImpl
import pm.bam.gamedeals.domain.utils.GiveawayPlatformsConverter
import pm.bam.gamedeals.domain.utils.LocalDateSerializer
import pm.bam.gamedeals.domain.utils.LocalDatetimeConverter
import pm.bam.gamedeals.domain.utils.StoreImagesConverter

val domainModule = module {
    single { StoreImagesConverter(get()) }
    single { GiveawayPlatformsConverter() }
    single { LocalDatetimeConverter() }
    single { LocalDateSerializer() }

    single<DomainDatabase> {
        get<RoomDatabase.Builder<DomainDatabase>>()
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addTypeConverter(get<StoreImagesConverter>())
            .addTypeConverter(get<GiveawayPlatformsConverter>())
            .addTypeConverter(get<LocalDatetimeConverter>())
            .build()
    }

    single { get<DomainDatabase>().getDealsDao() }
    single { get<DomainDatabase>().getGamesDao() }
    single { get<DomainDatabase>().getStoresDao() }
    single { get<DomainDatabase>().getReleasesDao() }
    single { get<DomainDatabase>().getGiveawaysDao() }
    single { get<DomainDatabase>().getFavouritesDao() }

    single<DealsRepository> { DealsRepositoryImpl(get(), get(), get(), get(), get()) }
    single<StoresRepository> { StoresRepositoryImpl(get(), get(), get(), get()) }
    single<GamesRepository> { GamesRepositoryImpl(get(), get()) }
    single<GiveawaysRepository> { GiveawaysRepositoryImpl(get(), get(), get()) }
    single<ReleasesRepository> { ReleasesRepositoryImpl(get(), get(), get()) }
    single<FavouritesRepository> { FavouritesRepositoryImpl(get(), get()) }
}
