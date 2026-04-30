package pm.bam.gamedeals.remote.cheapshark

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformation
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import retrofit2.Retrofit

/**
 * Test-friendly factory that constructs a real [CheapsharkSource] backed by Retrofit.
 *
 * Production code should obtain [CheapsharkSource] via Hilt; this factory exists so
 * consumers (e.g. repository tests in `:domain`) can stand the facade up against a
 * `MockWebServer` without exposing the internal Retrofit `*Api` interfaces.
 */
object CheapsharkSourceFactory {

    @OptIn(ExperimentalSerializationApi::class)
    fun create(
        baseUrl: String,
        logger: Logger,
        currencyTransformation: CurrencyTransformation,
        datetimeFormatter: DateTimeFormatter,
        json: Json = defaultJson,
        okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
        remoteExceptionTransformer: RemoteExceptionTransformer = RemoteExceptionTransformer { it }
    ): CheapsharkSource {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
            .client(okHttpClient)
            .build()

        return CheapsharkSourceImpl(
            logger = logger,
            dealsApi = retrofit.create(DealsApi::class.java),
            gamesApi = retrofit.create(GamesApi::class.java),
            releaseApi = retrofit.create(ReleaseApi::class.java),
            storesApi = retrofit.create(StoresApi::class.java),
            remoteExceptionTransformer = remoteExceptionTransformer,
            currencyTransformation = currencyTransformation,
            datetimeFormatter = datetimeFormatter
        )
    }

    private val defaultJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}
