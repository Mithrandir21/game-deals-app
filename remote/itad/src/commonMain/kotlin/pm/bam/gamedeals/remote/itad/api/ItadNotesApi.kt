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
import pm.bam.gamedeals.remote.itad.models.RemoteItadNote

/**
 * ITAD user-notes endpoints (epic #272, P4.1 #282). OAuth-scoped (`notes_read`/`notes_write`) — the
 * [httpClient] must be the bearer client (`ITAD_AUTH_QUALIFIER`).
 *
 * `GET /user/notes/v1` returns all of the user's notes. `PUT` adds/edits notes (body: an array of
 * `{gid, note}`; atomic + idempotent) and `DELETE` removes them (body: an array of game ids); both
 * return `200` (verified against the ITAD OpenAPI). The single-note helpers send one-element arrays.
 */
class ItadNotesApi(private val httpClient: HttpClient) {

    suspend fun getNotes(): ApiResponse<List<RemoteItadNote>> = try {
        ApiResponse.Success(httpClient.get("/user/notes/v1").body<List<RemoteItadNote>>())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun putNote(gameId: String, note: String): ApiResponse<Unit> = try {
        httpClient.put("/user/notes/v1") {
            contentType(ContentType.Application.Json)
            setBody(listOf(RemoteItadNote(gid = gameId, note = note)))
        }
        ApiResponse.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun deleteNote(gameId: String): ApiResponse<Unit> = try {
        httpClient.delete("/user/notes/v1") {
            contentType(ContentType.Application.Json)
            setBody(listOf(gameId))
        }
        ApiResponse.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
