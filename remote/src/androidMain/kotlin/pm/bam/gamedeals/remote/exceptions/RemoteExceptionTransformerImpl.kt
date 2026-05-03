package pm.bam.gamedeals.remote.exceptions

import io.ktor.client.plugins.ResponseException
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Transformation functionality allowing for transformation between specific Remote module [Throwable]s and module-external [Throwable]s.
 *
 * Phase 3 transitional: dispatches both Retrofit and Ktor exceptions to the same sealed
 * [RemoteHttpException] taxonomy. The Retrofit branch disappears when phase 3.3 finishes
 * and `:remote:cheapshark` / `:remote:gamerpower` no longer produce Retrofit `HttpException`.
 */
internal class RemoteExceptionTransformerImpl @Inject constructor() : RemoteExceptionTransformer {

    override fun transformApiException(throwable: Throwable): Throwable =
        when (throwable) {
            is HttpException -> throwable.toRemoteHttpException()
            is ResponseException -> throwable.toRemoteHttpException()
            else -> throwable
        }
}