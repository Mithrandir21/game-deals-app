package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsRequest
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsResponse

/**
 * ITAD deals list endpoint. The live `/deals/v2` wraps the games in an envelope
 * `{ nextOffset, hasMore, list }`, and each list item is a game with a single best `deal` (confirmed
 * against the live API during Phase 2b).
 *
 * Issued as a **POST** with a JSON body ([RemoteItadDealsRequest]): only the POST body honours ITAD's
 * rich `filter` object (and `shops`/`sort`/`mature`/paging) server-side — the GET query string ignores
 * the structured filters. The response shape is unchanged from the GET variant.
 */
class ItadDealsApi(private val httpClient: HttpClient) {

    suspend fun getDeals(request: RemoteItadDealsRequest): ApiResponse<RemoteItadDealsResponse> = try {
        ApiResponse.Success(
            httpClient.post("/deals/v2") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<RemoteItadDealsResponse>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
