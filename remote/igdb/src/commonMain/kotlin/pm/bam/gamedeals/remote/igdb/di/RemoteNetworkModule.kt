package pm.bam.gamedeals.remote.igdb.di

import io.ktor.client.HttpClient
import org.koin.dsl.module
import pm.bam.gamedeals.common.di.SECURE_QUALIFIER
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.remote.igdb.auth.IgdbTokenProvider
import pm.bam.gamedeals.remote.igdb.auth.IgdbTokenStore
import pm.bam.gamedeals.remote.igdb.auth.StorageIgdbTokenStore
import pm.bam.gamedeals.remote.igdb.logic.TWITCH_TOKEN_BASE_URL
import pm.bam.gamedeals.remote.igdb.logic.igdbHttpClient
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient

val igdbNetworkModule = module {
    single<HttpClient>(IGDB_TOKEN_QUALIFIER) {
        gameDealsHttpClient(
            json = get(),
            buildUtil = get(),
            baseUrl = TWITCH_TOKEN_BASE_URL,
            // The client-credentials grant is idempotent, and every IGDB call is gated behind it — a
            // single timeout here blanks the IGDB surface for the whole session (Sentry KOTLIN-7).
            retryOnTransientFailures = true,
        )
    }

    // Twitch tokens live ~60 days, so persisting one turns the grant into a rare event rather than a
    // per-launch dependency — and lets a cold start with no connectivity still authorise IGDB.
    single<IgdbTokenStore> { StorageIgdbTokenStore(get(SECURE_QUALIFIER)) }

    single {
        IgdbTokenProvider(
            tokenClient = get(IGDB_TOKEN_QUALIFIER),
            credentials = get(),
            store = get(),
            clock = get(),
        )
    }

    single<HttpClient>(IGDB_QUALIFIER) {
        igdbHttpClient(
            json = get(),
            buildUtil = get(),
            credentials = get(),
            tokenProvider = get(),
        )
    }

    single { IgdbGamesApi(get(IGDB_QUALIFIER), json = get(), logger = get()) }
}
