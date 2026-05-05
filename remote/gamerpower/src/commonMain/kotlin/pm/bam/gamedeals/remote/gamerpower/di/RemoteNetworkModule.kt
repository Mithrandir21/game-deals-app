package pm.bam.gamedeals.remote.gamerpower.di

import io.ktor.client.HttpClient
import org.koin.dsl.module
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient

val gamerpowerNetworkModule = module {
    single<HttpClient>(GAMERPOWER_QUALIFIER) {
        gameDealsHttpClient(
            json = get(),
            buildUtil = get(),
            baseUrl = "https://www.gamerpower.com",
        )
    }

    single { GamesApi(get(GAMERPOWER_QUALIFIER)) }
}
