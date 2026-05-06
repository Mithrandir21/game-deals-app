package pm.bam.gamedeals.remote.logic

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onException
import com.skydoves.sandwich.onSuccess
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


/**
 * Maps any [ApiResponse.Failure.Exception] to another [ApiResponse.Failure.Exception]
 * containing a [Throwable] produced by the [transformer].
 *
 * Every API failure (HTTP-status or transport-level) arrives as
 * `Failure.Exception` because the API classes wrap calls in try/catch and
 * Ktor's `expectSuccess = true` turns 4xx/5xx into `ResponseException`s.
 *
 * @param transformer A transformer that receives [Throwable] and returns [Throwable].
 *
 * @return A [T] type of the [ApiResponse].
 */
@OptIn(ExperimentalContracts::class)
fun <T> ApiResponse<T>.mapAnyFailure(transformer: Throwable.() -> Throwable): ApiResponse<T> {
    contract { callsInPlace(transformer, InvocationKind.AT_MOST_ONCE) }

    return when (this) {
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
