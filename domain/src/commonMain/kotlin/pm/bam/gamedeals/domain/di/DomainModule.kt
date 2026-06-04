package pm.bam.gamedeals.domain.di

import androidx.room.RoomDatabase
import org.koin.dsl.module
import pm.bam.gamedeals.common.di.SETTINGS_QUALIFIER
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.auth.AuthTokenStoreImpl
import pm.bam.gamedeals.domain.db.DOMAIN_MIGRATIONS
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.account.AccountRepositoryImpl
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepositoryImpl
import pm.bam.gamedeals.domain.repositories.stats.StatsRepository
import pm.bam.gamedeals.domain.repositories.stats.StatsRepositoryImpl
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepositoryImpl
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepositoryImpl
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepository
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepositoryImpl
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepositoryImpl
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepositoryImpl
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
            .addMigrations(*DOMAIN_MIGRATIONS)
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)
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
    single<IgdbRepository> { IgdbRepositoryImpl(get()) }
    single<RegionRepository> { RegionRepositoryImpl(get(SETTINGS_QUALIFIER)) }
    single<BundlesRepository> { BundlesRepositoryImpl(get()) }

    // ITAD account (epic #219). AuthTokenStore is Storage-backed (like RegionRepository). Waitlist/
    // Collection are backed by the live ITAD account source (Phase 2.3); Stats is still a stub until #235.
    single<AuthTokenStore> { AuthTokenStoreImpl(get(SETTINGS_QUALIFIER)) }
    single<AccountRepository> { AccountRepositoryImpl(get(), get()) }
    single<WaitlistRepository> { WaitlistRepositoryImpl(get(), get()) }
    single<CollectionRepository> { CollectionRepositoryImpl(get(), get()) }
    single<StatsRepository> { StatsRepositoryImpl() }
}
