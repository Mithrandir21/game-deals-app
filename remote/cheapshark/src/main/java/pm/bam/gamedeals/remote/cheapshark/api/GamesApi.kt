package pm.bam.gamedeals.remote.cheapshark.api

import com.skydoves.sandwich.ApiResponse
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGame
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGameDetails
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface GamesApi {

    @Suppress("kotlin:S107")
    @GET("/api/1.0/games")
    @Headers("Accept: application/json")
    suspend fun getGames(
        @Query("title") title: String? = null,
        @Query("steamAppID") steamAppID: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("exact") pageNumber: Int? = null
    ): ApiResponse<List<RemoteGame>>

    @GET("/api/1.0/games")
    @Headers("Accept: application/json")
    suspend fun getGame(@Query("id") id: String): ApiResponse<RemoteGameDetails>
}