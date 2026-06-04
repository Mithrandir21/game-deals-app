package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.itad.models.RemoteItadUser

/**
 * ITAD user-profile endpoint (epic #219, Phase 2). OAuth-scoped (`user_info`) — the [httpClient] must be
 * the bearer client (`ITAD_AUTH_QUALIFIER`).
 */
class ItadUserApi(private val httpClient: HttpClient) {

    suspend fun getInfo(): ApiResponse<RemoteItadUser> = try {
        ApiResponse.Success(httpClient.get("/user/info/v2").body<RemoteItadUser>())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
