package pm.bam.gamedeals.remote.itad.di

import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import pm.bam.gamedeals.remote.itad.api.ItadBundlesApi
import pm.bam.gamedeals.remote.itad.api.ItadCollectionApi
import pm.bam.gamedeals.remote.itad.api.ItadDealsApi
import pm.bam.gamedeals.remote.itad.api.ItadGamesApi
import pm.bam.gamedeals.remote.itad.api.ItadIgnoredApi
import pm.bam.gamedeals.remote.itad.api.ItadNotesApi
import pm.bam.gamedeals.remote.itad.api.ItadNotificationsApi
import pm.bam.gamedeals.remote.itad.api.ItadShopsApi
import pm.bam.gamedeals.remote.itad.api.ItadStatsApi
import pm.bam.gamedeals.remote.itad.api.ItadUserApi
import pm.bam.gamedeals.remote.itad.api.ItadWaitlistApi
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.remote.itad.auth.oauth.ItadOAuthClient
import pm.bam.gamedeals.remote.itad.auth.oauth.ItadTokenProvider
import pm.bam.gamedeals.remote.itad.logic.itadAuthHttpClient
import pm.bam.gamedeals.remote.itad.logic.itadHttpClient
import pm.bam.gamedeals.remote.itad.logic.itadOAuthHttpClient

val ITAD_QUALIFIER = named("itad")

/** API-key-less client for the OAuth token endpoint (epic #219, Phase 2). */
val ITAD_OAUTH_QUALIFIER = named("itad-oauth")

/** Bearer (user-token) client for the ITAD user endpoints (epic #219, Phase 2). */
val ITAD_AUTH_QUALIFIER = named("itad-auth")

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
    single { ItadStatsApi(get(ITAD_QUALIFIER)) }

    // --- ITAD OAuth ---
    single<HttpClient>(ITAD_OAUTH_QUALIFIER) { itadOAuthHttpClient(json = get(), buildUtil = get()) }
    single { ItadOAuthClient(httpClient = get(ITAD_OAUTH_QUALIFIER), credentials = get()) }
    single { ItadTokenProvider(authTokenStore = get(), oauthClient = get(), clock = get(), logger = get()) }
    single<HttpClient>(ITAD_AUTH_QUALIFIER) { itadAuthHttpClient(json = get(), buildUtil = get(), tokenProvider = get()) }

    // User endpoints use the bearer client.
    single { ItadUserApi(get(ITAD_AUTH_QUALIFIER)) }
    single { ItadWaitlistApi(get(ITAD_AUTH_QUALIFIER)) }
    single { ItadCollectionApi(get(ITAD_AUTH_QUALIFIER)) }
    single { ItadNotificationsApi(get(ITAD_AUTH_QUALIFIER)) }
    single { ItadIgnoredApi(get(ITAD_AUTH_QUALIFIER)) }
    single { ItadNotesApi(get(ITAD_AUTH_QUALIFIER)) }
}
