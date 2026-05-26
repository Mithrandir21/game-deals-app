package pm.bam.gamedeals.remote.igdb.di

import io.ktor.client.HttpClient
import org.koin.dsl.module
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.remote.igdb.auth.IgdbTokenProvider
import pm.bam.gamedeals.remote.igdb.logic.TWITCH_TOKEN_BASE_URL
import pm.bam.gamedeals.remote.igdb.logic.igdbHttpClient
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient

val igdbNetworkModule = module {
    single<HttpClient>(IGDB_TOKEN_QUALIFIER) {
        gameDealsHttpClient(
            json = get(),
            buildUtil = get(),
            baseUrl = TWITCH_TOKEN_BASE_URL,
        )
    }

    single { IgdbTokenProvider(tokenClient = get(IGDB_TOKEN_QUALIFIER), credentials = get()) }

    single<HttpClient>(IGDB_QUALIFIER) {
        igdbHttpClient(
            json = get(),
            buildUtil = get(),
            credentials = get(),
            tokenProvider = get(),
        )
    }

    single { IgdbGamesApi(get(IGDB_QUALIFIER)) }
}
