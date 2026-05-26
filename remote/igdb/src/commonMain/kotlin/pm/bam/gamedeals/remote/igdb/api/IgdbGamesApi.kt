package pm.bam.gamedeals.remote.igdb.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.igdb.models.RemoteIgdbGame

/**
 * IGDB `/v4/games` endpoint. IGDB uses Apicalypse — a plain-text query language sent as the
 * request body. `Content-Type: text/plain` keeps Ktor's ContentNegotiation from trying to
 * JSON-serialize the raw `String` body. The `Client-ID` header is set on the HttpClient's
 * `defaultRequest`; the `Authorization: Bearer …` header is injected by Ktor's Auth plugin.
 */
class IgdbGamesApi(private val httpClient: HttpClient) {

    suspend fun sampleGames(): ApiResponse<List<RemoteIgdbGame>> = try {
        ApiResponse.Success(
            httpClient.post("/v4/games") {
                contentType(ContentType.Text.Plain)
                setBody(SAMPLE_QUERY)
            }.body()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    private companion object {
        const val SAMPLE_QUERY = "fields id,name,summary; limit 5;"
    }
}
