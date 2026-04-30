package pm.bam.gamedeals.remote.cheapshark.api

import com.skydoves.sandwich.ApiResponse
import pm.bam.gamedeals.remote.cheapshark.models.RemoteStore
import retrofit2.http.GET
import retrofit2.http.Headers

interface StoresApi {

    @GET("/api/1.0/stores")
    @Headers("Accept: application/json")
    suspend fun getStores(): ApiResponse<List<RemoteStore>>

}