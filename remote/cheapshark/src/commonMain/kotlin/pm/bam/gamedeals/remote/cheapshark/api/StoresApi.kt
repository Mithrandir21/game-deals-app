package pm.bam.gamedeals.remote.cheapshark.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.cheapshark.models.RemoteStore

class StoresApi(private val httpClient: HttpClient) {

    suspend fun getStores(): ApiResponse<List<RemoteStore>> = try {
        // TEMP DEBUG (phase-3 silent-failure investigation): bracket the suspend call
        // so we can tell whether the call hangs (no "returned" log), is cancelled
        // ("cancelled" log), or throws ("caught X" log). Remove once cause is found.
        println("StoresApi.getStores: about to suspend")
        val result = httpClient.get("/api/1.0/stores").body<List<RemoteStore>>()
        println("StoresApi.getStores: returned ${result.size} items")
        ApiResponse.Success(result)
    } catch (e: CancellationException) {
        println("StoresApi.getStores: cancelled")
        throw e
    } catch (t: Throwable) {
        println("StoresApi.getStores: caught ${t::class.simpleName}: ${t.message}")
        ApiResponse.exception(t)
    }
}
