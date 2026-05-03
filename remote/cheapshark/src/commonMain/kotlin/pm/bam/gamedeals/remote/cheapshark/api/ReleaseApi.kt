package pm.bam.gamedeals.remote.cheapshark.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.cheapshark.models.RemoteRelease

class ReleaseApi(private val httpClient: HttpClient) {

    suspend fun getReleases(): ApiResponse<List<RemoteRelease>> = try {
        ApiResponse.Success(httpClient.get("/api/other/releases").body<List<RemoteRelease>>())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
