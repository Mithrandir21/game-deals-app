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
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.cheapshark.di.CheapShark
import pm.bam.gamedeals.integration.support.FixtureMockDispatcher
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class TestCheapSharkNetworkModule {

    @Provides
    @Singleton
    @CheapShark
    fun provideMockWebServer(dispatcher: FixtureMockDispatcher): MockWebServer =
        MockWebServer().apply {
            this.dispatcher = dispatcher
            start()
        }

    @Provides
    @Singleton
    @CheapShark
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    @CheapShark
    @ExperimentalSerializationApi
    fun provideRetrofit(
        @CheapShark okHttpClient: OkHttpClient,
        @CheapShark mockWebServer: MockWebServer,
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
    fun provideDealsApi(@CheapShark retrofit: Retrofit): DealsApi = retrofit.create(DealsApi::class.java)

    @Provides
    @Singleton
    fun provideGamesApi(@CheapShark retrofit: Retrofit): GamesApi = retrofit.create(GamesApi::class.java)

    @Provides
    @Singleton
    fun provideStoresApi(@CheapShark retrofit: Retrofit): StoresApi = retrofit.create(StoresApi::class.java)

    @Provides
    @Singleton
    fun provideReleaseApi(@CheapShark retrofit: Retrofit): ReleaseApi = retrofit.create(ReleaseApi::class.java)
}
