package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.itad.models.RemoteItadSearchGame

/**
 * ITAD collection endpoints (epic #219, Phase 2). OAuth-scoped (`coll_read`/`coll_write`) — the
 * [httpClient] must be the bearer client (`ITAD_AUTH_QUALIFIER`).
 *
 * `GET` returns an array of `obj.game` ([RemoteItadSearchGame]); `PUT`/`DELETE` take a JSON array of
 * game-id strings and return `204 No Content` (verified against the ITAD OpenAPI).
 */
class ItadCollectionApi(private val httpClient: HttpClient) {

    suspend fun getCollection(): ApiResponse<List<RemoteItadSearchGame>> = try {
        ApiResponse.Success(httpClient.get("/collection/games/v1").body<List<RemoteItadSearchGame>>())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun addGames(gameIds: List<String>): ApiResponse<Unit> = try {
        httpClient.put("/collection/games/v1") {
            contentType(ContentType.Application.Json)
            setBody(gameIds)
        }
        ApiResponse.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun removeGames(gameIds: List<String>): ApiResponse<Unit> = try {
        httpClient.delete("/collection/games/v1") {
            contentType(ContentType.Application.Json)
            setBody(gameIds)
        }
        ApiResponse.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
