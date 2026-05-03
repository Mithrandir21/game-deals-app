package pm.bam.gamedeals.remote.exceptions

import io.ktor.client.plugins.ResponseException
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.*
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_BAD_REQUEST
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_FORBIDDEN
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_METHOD_NOT_ALLOWED
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_NOT_FOUND
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_UNAUTHORIZED
import retrofit2.HttpException

/*
 * Internal Note: This class is specifically created so that HttpExceptions produced by Retrofit can be propagated to other modules
 * without needing those modules to include the Retrofit library just to be able to receive Retrofit specific exceptions.
 *
 * Phase 3 transitional: handles both Retrofit `HttpException` and Ktor `ResponseException` while
 * `:remote:cheapshark` and `:remote:gamerpower` migrate from Retrofit to Ktor. The Retrofit
 * extension goes away when phase 3.3 finishes.
 */

/**
 * Sealed class that will represent specific HTTP exception that are results of API calls.
 */
sealed class RemoteHttpException(open val code: Int) : RuntimeException() {

    data object BadRequest : RemoteHttpException(400)
    data object Unauthorized : RemoteHttpException(401)
    data object Forbidden : RemoteHttpException(403)
    data object NotFound : RemoteHttpException(404)
    data object MethodNotAllowed : RemoteHttpException(405)
    data class HttpException(override val code: Int) : RemoteHttpException(code)

    companion object {
        internal const val CODE_BAD_REQUEST = 400
        internal const val CODE_UNAUTHORIZED = 401
        internal const val CODE_FORBIDDEN = 403
        internal const val CODE_NOT_FOUND = 404
        internal const val CODE_METHOD_NOT_ALLOWED = 405
    }
}


internal fun HttpException.toRemoteHttpException(): RemoteHttpException = code().toRemoteHttpException()


internal fun ResponseException.toRemoteHttpException(): RemoteHttpException =
    response.status.value.toRemoteHttpException()


internal fun Int.toRemoteHttpException(): RemoteHttpException =
    when (this) {
        CODE_BAD_REQUEST -> BadRequest
        CODE_UNAUTHORIZED -> Unauthorized
        CODE_FORBIDDEN -> Forbidden
        CODE_NOT_FOUND -> NotFound
        CODE_METHOD_NOT_ALLOWED -> MethodNotAllowed
        else -> HttpException(this)
    }
