package pm.bam.gamedeals.remote.logic

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onException
import com.skydoves.sandwich.onSuccess
import com.skydoves.sandwich.retrofit.statusCode
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.toRemoteHttpException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


/**
 * Maps [Any] type of the [ApiResponse.Failure.Error] or [ApiResponse.Failure.Exception] to
 * [ApiResponse.Failure.Exception] containing either a [Throwable] produced by the [transformer].
 *
 * @param transformer A transformer that receives [Throwable] and returns [Throwable].
 *
 * @return A [T] type of the [ApiResponse].
 */
@OptIn(ExperimentalContracts::class)
fun <T> ApiResponse<T>.mapAnyFailure(transformer: Throwable.() -> Throwable): ApiResponse<T> {
    contract { callsInPlace(transformer, InvocationKind.AT_MOST_ONCE) }

    return when (this) {
        is ApiResponse.Failure.Error -> ApiResponse.exception(ex = statusCode.code.toRemoteHttpException())
        is ApiResponse.Failure.Exception -> ApiResponse.exception(ex = transformer.invoke(throwable))
        else -> this
    }
}


/** Logs, using the provided [logger], for [onError], [onException] and [onSuccess]. */
fun <T> ApiResponse<T>.log(logger: Logger, logLevel: LogLevel = LogLevel.DEBUG, tag: String? = null): ApiResponse<T> {
    this.onError { logger.log(logLevel, tag) { "Error: $this" } }
    this.onException { logger.log(logLevel, tag, this.throwable) { "Exception message: ${this.message}" } }
    this.onSuccess { logger.log(logLevel, tag) { "Success: ${this.data}" } }

    return this
}