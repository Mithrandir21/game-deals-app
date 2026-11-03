package pm.bam.gamedeals.remote.di

import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventSource
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformerImpl
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.RemoteBuildUtilImpl
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient
import pm.bam.gamedeals.remote.wintersale.WinterSaleEventHttpSource

private val winterSaleEventClient = named("winter-sale-event")

const val WINTER_SALE_EVENT_BASE_URL = "https://gamedeals.bam.pm"

fun remoteModule(buildType: RemoteBuildType, winterSaleEventBaseUrl: String = WINTER_SALE_EVENT_BASE_URL) = module {
    single<RemoteBuildUtil> { RemoteBuildUtilImpl(buildType) }
    single<RemoteExceptionTransformer> { RemoteExceptionTransformerImpl() }
    single<HttpClient>(winterSaleEventClient) {
        gameDealsHttpClient(json = get(), buildUtil = get(), baseUrl = winterSaleEventBaseUrl)
    }
    single<WinterSaleEventSource> { WinterSaleEventHttpSource(get(winterSaleEventClient)) }
}
