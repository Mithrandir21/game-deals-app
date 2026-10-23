package pm.bam.gamedeals.remote.exceptions

import io.ktor.client.plugins.ResponseException
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.*
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_BAD_REQUEST
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_FORBIDDEN
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_METHOD_NOT_ALLOWED
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_NOT_FOUND
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_UNAUTHORIZED

/*
 * Internal Note: This file represents HTTP exceptions in a transport-agnostic way so that
 * other modules don't have to know whether the underlying client is Ktor or anything else.
 */

/**
 * Sealed class that will represent specific HTTP exception that are results of API calls.
 *
 * The message carries the status ("HTTP 429") and [cause] the transport's own exception (URL and response
 * text), so a Sentry issue says which failure it was. These are classes, not singletons: a shared exception
 * instance would also share the stack trace of whichever call created it first.
 */
sealed class RemoteHttpException(val code: Int, cause: Throwable?) : RuntimeException("HTTP $code", cause) {

    class BadRequest(cause: Throwable? = null) : RemoteHttpException(CODE_BAD_REQUEST, cause)
    class Unauthorized(cause: Throwable? = null) : RemoteHttpException(CODE_UNAUTHORIZED, cause)
    class Forbidden(cause: Throwable? = null) : RemoteHttpException(CODE_FORBIDDEN, cause)
    class NotFound(cause: Throwable? = null) : RemoteHttpException(CODE_NOT_FOUND, cause)
    class MethodNotAllowed(cause: Throwable? = null) : RemoteHttpException(CODE_METHOD_NOT_ALLOWED, cause)
    class HttpException(code: Int, cause: Throwable? = null) : RemoteHttpException(code, cause)

    companion object {
        internal const val CODE_BAD_REQUEST = 400
        internal const val CODE_UNAUTHORIZED = 401
        internal const val CODE_FORBIDDEN = 403
        internal const val CODE_NOT_FOUND = 404
        internal const val CODE_METHOD_NOT_ALLOWED = 405
    }
}


internal fun ResponseException.toRemoteHttpException(): RemoteHttpException =
    response.status.value.toRemoteHttpException(cause = this)


internal fun Int.toRemoteHttpException(cause: Throwable? = null): RemoteHttpException =
    when (this) {
        CODE_BAD_REQUEST -> BadRequest(cause)
        CODE_UNAUTHORIZED -> Unauthorized(cause)
        CODE_FORBIDDEN -> Forbidden(cause)
        CODE_NOT_FOUND -> NotFound(cause)
        CODE_METHOD_NOT_ALLOWED -> MethodNotAllowed(cause)
        else -> HttpException(this, cause)
    }
