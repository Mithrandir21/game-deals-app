package pm.bam.gamedeals.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.remote.gamerpower.di.GamerPower
import pm.bam.gamedeals.integration.support.FixtureMockDispatcher
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class TestGamerPowerNetworkModule {

    @Provides
    @Singleton
    @GamerPower
    fun provideMockWebServer(dispatcher: FixtureMockDispatcher): MockWebServer =
        MockWebServer().apply {
            this.dispatcher = dispatcher
            start()
        }

    @Provides
    @Singleton
    @GamerPower
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    @GamerPower
    @ExperimentalSerializationApi
    fun provideRetrofit(
        @GamerPower okHttpClient: OkHttpClient,
        @GamerPower mockWebServer: MockWebServer,
        json: Json
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(mockWebServer.url("/").toString())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .client(okHttpClient)
            .build()

    @Provides
    @Singleton
    fun provideGamesApi(@GamerPower retrofit: Retrofit): GamesApi = retrofit.create(GamesApi::class.java)
}
