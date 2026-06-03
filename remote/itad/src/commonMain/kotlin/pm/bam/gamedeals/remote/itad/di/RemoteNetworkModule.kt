package pm.bam.gamedeals.remote.itad.di

import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import pm.bam.gamedeals.remote.itad.api.ItadBundlesApi
import pm.bam.gamedeals.remote.itad.api.ItadDealsApi
import pm.bam.gamedeals.remote.itad.api.ItadGamesApi
import pm.bam.gamedeals.remote.itad.api.ItadShopsApi
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.remote.itad.logic.itadHttpClient

val ITAD_QUALIFIER = named("itad")

val itadNetworkModule = module {
    single<HttpClient>(ITAD_QUALIFIER) {
        itadHttpClient(
            json = get(),
            buildUtil = get(),
            apiKey = get<ItadCredentials>().apiKey,
        )
    }

    single { ItadShopsApi(get(ITAD_QUALIFIER)) }
    single { ItadDealsApi(get(ITAD_QUALIFIER)) }
    single { ItadGamesApi(get(ITAD_QUALIFIER)) }
    single { ItadBundlesApi(get(ITAD_QUALIFIER)) }
}
