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
import pm.bam.gamedeals.domain.repositories.cache.CacheMaintenance
import pm.bam.gamedeals.domain.repositories.cache.CacheMaintenanceImpl
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepositoryImpl
import pm.bam.gamedeals.domain.repositories.discovery.TagDiscoveryRepository
import pm.bam.gamedeals.domain.repositories.discovery.TagDiscoveryRepositoryImpl
import pm.bam.gamedeals.domain.repositories.franchise.FollowedDealSeenStore
import pm.bam.gamedeals.domain.repositories.franchise.FollowedDealSeenStoreImpl
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseChecker
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseCheckerImpl
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseRepository
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseRepositoryImpl
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepositoryImpl
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepositoryImpl
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepositoryImpl
import pm.bam.gamedeals.domain.repositories.notes.NotesRepository
import pm.bam.gamedeals.domain.repositories.notes.NotesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.recommendations.RecommendationsRepository
import pm.bam.gamedeals.domain.repositories.recommendations.RecommendationsRepositoryImpl
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettingsImpl
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSync
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSyncImpl
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepositoryImpl
import pm.bam.gamedeals.domain.repositories.notifications.SurfacedNotificationStore
import pm.bam.gamedeals.domain.repositories.notifications.SurfacedNotificationStoreImpl
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepositoryImpl
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepositoryImpl
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepositoryImpl
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
    single { get<DomainDatabase>().getDealDetailsCacheDao() }
    single { get<DomainDatabase>().getGameDetailsCacheDao() }
    single { get<DomainDatabase>().getPriceHistoryCacheDao() }
    single { get<DomainDatabase>().getBundlesCacheDao() }
    single { get<DomainDatabase>().getStatsRankingsCacheDao() }
    single { get<DomainDatabase>().getGameIdMappingDao() }
    single { get<DomainDatabase>().getWaitlistDao() }
    single { get<DomainDatabase>().getCollectionDao() }
    single { get<DomainDatabase>().getIgnoredDao() }
    single { get<DomainDatabase>().getIgdbTagDao() }

    single<DealsRepository> { DealsRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<StoresRepository> { StoresRepositoryImpl(get(), get(), get(), get()) }
    single<GamesRepository> { GamesRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<GiveawaysRepository> { GiveawaysRepositoryImpl(get(), get(), get(), get()) }
    single<ReleasesRepository> { ReleasesRepositoryImpl(get(), get(), get(), get()) }
    single<IgdbRepository> { IgdbRepositoryImpl(get()) }
    // Tag discovery: composes IGDB tag search + the ITAD pricing bridge, with a Room-cached
    // picker vocabulary (logger, igdbRepository, gamesRepository, igdbTagDao, clock).
    single<TagDiscoveryRepository> { TagDiscoveryRepositoryImpl(get(), get(), get(), get(), get()) }
    single<RegionRepository> { RegionRepositoryImpl(get(SETTINGS_QUALIFIER)) }
    single<SettingsRepository> { SettingsRepositoryImpl(get(SETTINGS_QUALIFIER)) }
    single<BundlesRepository> { BundlesRepositoryImpl(get(), get(), get(), get(), get()) }

    // ITAD account. AuthTokenStore is Storage-backed (like RegionRepository); Waitlist/Collection are
    // backed by the live ITAD account source, Stats by the live ITAD stats source.
    single<AuthTokenStore> { AuthTokenStoreImpl(get(SETTINGS_QUALIFIER)) }
    single<AccountRepository> { AccountRepositoryImpl(get(), get()) }
    single<WaitlistRepository> { WaitlistRepositoryImpl(get(), get(), get()) }
    single<CollectionRepository> { CollectionRepositoryImpl(get(), get(), get()) }
    single<NotificationsRepository> { NotificationsRepositoryImpl(get(), get()) }
    // Background (OS-tray) notification delivery. Scheduler is platform-bound
    // (domainAndroidModule / domainIosModule); presenter is host-bound (:app / :iosApp).
    single<SurfacedNotificationStore> { SurfacedNotificationStoreImpl(get(SETTINGS_QUALIFIER)) }
    single<NotificationSettings> { NotificationSettingsImpl(get(SETTINGS_QUALIFIER)) }
    single<NotificationSync> { NotificationSyncImpl(get(), get(), get()) }
    // Followed franchises/series (#7) — client-side, Storage-backed.
    single<FollowedFranchiseRepository> { FollowedFranchiseRepositoryImpl(get(SETTINGS_QUALIFIER), get()) }
    // Followed-franchise deal alerts: the client-side checker compares each followed franchise's games to
    // live ITAD prices in the same background poll as the ITAD sync, deduped via the seen store. The alert
    // title is built here (domain has no string resources) — concise English copy, consistent with the
    // price-watch precedent and the ITAD waitlist channel.
    single<FollowedDealSeenStore> { FollowedDealSeenStoreImpl(get(SETTINGS_QUALIFIER)) }
    single<FollowedFranchiseChecker> {
        FollowedFranchiseCheckerImpl(get(), get(), get(), get()) { gameTitle, franchiseName, cutPercent, priceDenominated ->
            "$gameTitle is $cutPercent% off in $franchiseName — now $priceDenominated"
        }
    }
    // "For You" recommendations (#6): IGDB similarity seeded from the user's waitlist + collection.
    single<RecommendationsRepository> { RecommendationsRepositoryImpl(get(), get(), get(), get()) }
    single<IgnoredRepository> { IgnoredRepositoryImpl(get(), get(), get()) }
    single<NotesRepository> { NotesRepositoryImpl(get(), get(), get()) }

    // Startup cache maintenance: cacheSchemaVersion guard + eviction sweep over the ITAD caches.
    single<CacheMaintenance> {
        CacheMaintenanceImpl(get(SETTINGS_QUALIFIER), get(), get(), get(), get(), get(), get(), get(), get())
    }
    single<StatsRepository> { StatsRepositoryImpl(get(), get(), get(), get(), get()) }
}
