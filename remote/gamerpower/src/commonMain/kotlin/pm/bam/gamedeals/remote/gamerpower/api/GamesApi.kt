package pm.bam.gamedeals.remote.gamerpower.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.gamerpower.models.RemoteGiveaway
import pm.bam.gamedeals.remote.logic.bodyAsListSkippingInvalid

/**
 * GamerPower API. Calls return a sandwich [ApiResponse], which the source impl
 * funnels through `mapAnyFailure { remoteExceptionTransformer.transformApiException(...) }`
 * before unwrapping. The HttpClient that backs this API is configured with
 * `expectSuccess = true` (see RemoteNetworkModule) so 4xx/5xx throws Ktor's
 * `ResponseException`; the transformer then maps it to the `RemoteHttpException` taxonomy.
 *
 * The try/catch wrapper is intentional: sandwich's `responseOf` helper isn't
 * suspend-aware in 2.x, so we wrap manually. `CancellationException` is rethrown
 * to preserve coroutine cancellation semantics.
 *
 * The giveaways array is decoded **element by element** ([bodyAsListSkippingInvalid]): GamerPower is a
 * community feed, so one malformed row (or a giveaway type added after this build) drops that row rather
 * than failing the whole response. [json]/[logger] default for tests; DI injects the configured singletons.
 */
class GamesApi(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val logger: Logger? = null,
) {

    suspend fun getAllGames(): ApiResponse<List<RemoteGiveaway>> = try {
        ApiResponse.Success(
            httpClient.get("/api/giveaways").bodyAsListSkippingInvalid<RemoteGiveaway>(json, logger, TAG),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    private companion object {
        private const val TAG = "GamesApi"
    }
}
