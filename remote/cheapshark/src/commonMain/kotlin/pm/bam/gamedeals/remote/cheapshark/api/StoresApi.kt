package pm.bam.gamedeals.remote.cheapshark.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.cheapshark.models.RemoteStore

class StoresApi(private val httpClient: HttpClient) {

    suspend fun getStores(): ApiResponse<List<RemoteStore>> = try {
        ApiResponse.Success(httpClient.get("/api/1.0/stores").body<List<RemoteStore>>())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
