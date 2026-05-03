package pm.bam.gamedeals.remote.exceptions

import io.ktor.client.plugins.ResponseException
import javax.inject.Inject

/**
 * Transformation functionality allowing for transformation between specific Remote module [Throwable]s and module-external [Throwable]s.
 *
 * Maps Ktor's `ResponseException` (4xx/5xx with `expectSuccess = true`) into the
 * sealed [RemoteHttpException] taxonomy. Other throwables pass through unchanged.
 */
internal class RemoteExceptionTransformerImpl @Inject constructor() : RemoteExceptionTransformer {

    override fun transformApiException(throwable: Throwable): Throwable =
        when (throwable) {
            is ResponseException -> throwable.toRemoteHttpException()
            else -> throwable
        }
}
