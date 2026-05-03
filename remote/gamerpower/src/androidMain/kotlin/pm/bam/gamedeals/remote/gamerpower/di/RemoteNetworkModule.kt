package pm.bam.gamedeals.remote.gamerpower.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.httpClient
import javax.inject.Qualifier
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
class RemoteNetworkModule {

    @Provides
    @Singleton
    @GamerPower
    fun provideHttpClient(remoteBuildUtil: RemoteBuildUtil, json: Json): HttpClient =
        httpClient {
            expectSuccess = true

            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
            }

            when (remoteBuildUtil.buildType()) {
                RemoteBuildType.DEBUG -> install(Logging) { level = LogLevel.BODY }
                RemoteBuildType.RELEASE -> Unit
            }

            defaultRequest { url("https://www.gamerpower.com") }
        }

    @Provides
    @Singleton
    internal fun provideGamesApi(@GamerPower httpClient: HttpClient): GamesApi = GamesApi(httpClient)
}

/** A [Qualifier] used specifically for dependencies associated specifically with this GamerPower module as opposed to any other Module. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GamerPower
