package pm.bam.gamedeals.remote.cheapshark.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGame
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGameDetails

class GamesApi(private val httpClient: HttpClient) {

    suspend fun getGames(
        title: String? = null,
        steamAppID: Int? = null,
        limit: Int? = null,
        pageNumber: Int? = null
    ): ApiResponse<List<RemoteGame>> = try {
        ApiResponse.Success(
            httpClient.get("/api/1.0/games") {
                parameter("title", title)
                parameter("steamAppID", steamAppID)
                parameter("limit", limit)
                parameter("exact", pageNumber)
            }.body<List<RemoteGame>>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun getGame(id: String): ApiResponse<RemoteGameDetails> = try {
        ApiResponse.Success(
            httpClient.get("/api/1.0/games") { parameter("id", id) }
                .body<RemoteGameDetails>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
