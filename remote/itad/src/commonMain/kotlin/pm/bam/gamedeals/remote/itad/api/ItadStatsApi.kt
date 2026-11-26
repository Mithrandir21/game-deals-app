package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.itad.models.RemoteItadRankedGame

/**
 * ITAD global ranking stats (epic #219, Phase 5). Each endpoint returns a bare JSON array of ranked
 * games ordered by `position`. API-key only (uses the key client). `offset = 0` is sent explicitly so
 * the ranking starts at position 1.
 */
class ItadStatsApi(private val httpClient: HttpClient) {

    suspend fun getMostWaitlisted(limit: Int? = null, offset: Int = 0): ApiResponse<List<RemoteItadRankedGame>> =
        getRanking("/stats/most-waitlisted/v1", limit, offset)

    suspend fun getMostCollected(limit: Int? = null, offset: Int = 0): ApiResponse<List<RemoteItadRankedGame>> =
        getRanking("/stats/most-collected/v1", limit, offset)

    suspend fun getMostPopular(limit: Int? = null, offset: Int = 0): ApiResponse<List<RemoteItadRankedGame>> =
        getRanking("/stats/most-popular/v1", limit, offset)

    private suspend fun getRanking(path: String, limit: Int?, offset: Int): ApiResponse<List<RemoteItadRankedGame>> = try {
        ApiResponse.Success(
            httpClient.get(path) {
                parameter("limit", limit)
                parameter("offset", offset)
            }.body<List<RemoteItadRankedGame>>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
