package pm.bam.gamedeals.remote.cheapshark.di

import io.ktor.client.HttpClient
import org.koin.dsl.module
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient

val cheapsharkNetworkModule = module {
    single<HttpClient>(CHEAPSHARK_QUALIFIER) {
        gameDealsHttpClient(
            json = get(),
            buildUtil = get(),
            baseUrl = "https://www.cheapshark.com",
        )
    }

    single { DealsApi(get(CHEAPSHARK_QUALIFIER)) }
    single { GamesApi(get(CHEAPSHARK_QUALIFIER)) }
    single { StoresApi(get(CHEAPSHARK_QUALIFIER)) }
    single { ReleaseApi(get(CHEAPSHARK_QUALIFIER)) }
}
