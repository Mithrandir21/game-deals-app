package pm.bam.gamedeals.remote.gamerpower.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.gamerpower.models.RemoteGiveaway

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
 */
class GamesApi(private val httpClient: HttpClient) {

    suspend fun getAllGames(): ApiResponse<List<RemoteGiveaway>> = try {
        ApiResponse.Success(httpClient.get("/api/giveaways").body<List<RemoteGiveaway>>())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
