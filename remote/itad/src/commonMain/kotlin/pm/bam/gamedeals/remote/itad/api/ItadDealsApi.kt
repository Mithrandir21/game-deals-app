package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsResponse

/**
 * ITAD deals list endpoint. The live `/deals/v2` wraps the games in an envelope
 * `{ nextOffset, hasMore, list }`, and each list item is a game with a single best `deal` (confirmed
 * against the live API during Phase 2b). `?shops=<id>` filters to that shop's deals.
 */
class ItadDealsApi(private val httpClient: HttpClient) {

    suspend fun getDeals(
        country: String? = null,
        offset: Int? = null,
        limit: Int? = null,
        sort: String? = null,
        shops: String? = null,
    ): ApiResponse<RemoteItadDealsResponse> = try {
        ApiResponse.Success(
            httpClient.get("/deals/v2") {
                parameter("country", country)
                parameter("offset", offset)
                parameter("limit", limit)
                parameter("sort", sort)
                parameter("shops", shops)
            }.body<RemoteItadDealsResponse>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
