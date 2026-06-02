package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsGame

/**
 * ITAD deals list endpoint. Each returned game nests its per-shop `deals[]`.
 *
 * NOTE: modelled as a bare JSON array per the published docs. If the live `/deals/v2` wraps the
 * results in an envelope (e.g. `{ "list": [...], "nextOffset": ..., "hasMore": ... }`), introduce a
 * `RemoteItadDealsResponse` wrapper here. Confirm during the Phase 1 live smoke (see plan).
 */
class ItadDealsApi(private val httpClient: HttpClient) {

    suspend fun getDeals(
        country: String? = null,
        offset: Int? = null,
        limit: Int? = null,
        sort: String? = null,
        shops: String? = null,
    ): ApiResponse<List<RemoteItadDealsGame>> = try {
        ApiResponse.Success(
            httpClient.get("/deals/v2") {
                parameter("country", country)
                parameter("offset", offset)
                parameter("limit", limit)
                parameter("sort", sort)
                parameter("shops", shops)
            }.body<List<RemoteItadDealsGame>>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
