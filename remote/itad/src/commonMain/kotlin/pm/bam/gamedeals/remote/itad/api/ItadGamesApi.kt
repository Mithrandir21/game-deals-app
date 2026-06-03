package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.itad.models.RemoteItadGamePrices
import pm.bam.gamedeals.remote.itad.models.RemoteItadHistoryEntry
import pm.bam.gamedeals.remote.itad.models.RemoteItadLookupResponse
import pm.bam.gamedeals.remote.itad.models.RemoteItadSearchGame

/**
 * ITAD game endpoints: search/lookup (the Steam-appid → game-UUID bridge), current prices, and the
 * historical price log. `/games/prices/v3` is a POST whose body is a JSON array of game UUIDs.
 */
class ItadGamesApi(private val httpClient: HttpClient) {

    suspend fun searchGames(title: String, results: Int? = null): ApiResponse<List<RemoteItadSearchGame>> = try {
        ApiResponse.Success(
            httpClient.get("/games/search/v1") {
                parameter("title", title)
                parameter("results", results)
            }.body<List<RemoteItadSearchGame>>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    /** Basic game info (title + assets) by UUID — `/games/info/v2` returns the same shape as search. */
    suspend fun getInfo(id: String): ApiResponse<RemoteItadSearchGame> = try {
        ApiResponse.Success(
            httpClient.get("/games/info/v2") {
                parameter("id", id)
            }.body<RemoteItadSearchGame>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun lookupGame(title: String? = null, appid: Int? = null): ApiResponse<RemoteItadLookupResponse> = try {
        ApiResponse.Success(
            httpClient.get("/games/lookup/v1") {
                parameter("title", title)
                parameter("appid", appid)
            }.body<RemoteItadLookupResponse>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun getPrices(
        gameIds: List<String>,
        country: String? = null,
        shops: String? = null,
        capacity: Int? = null,
    ): ApiResponse<List<RemoteItadGamePrices>> = try {
        ApiResponse.Success(
            httpClient.post("/games/prices/v3") {
                parameter("country", country)
                parameter("shops", shops)
                parameter("capacity", capacity)
                contentType(ContentType.Application.Json)
                setBody(gameIds)
            }.body<List<RemoteItadGamePrices>>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun getHistory(
        gameId: String,
        country: String? = null,
        since: String? = null,
    ): ApiResponse<List<RemoteItadHistoryEntry>> = try {
        ApiResponse.Success(
            httpClient.get("/games/history/v2") {
                parameter("id", gameId)
                parameter("country", country)
                parameter("since", since)
            }.body<List<RemoteItadHistoryEntry>>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
