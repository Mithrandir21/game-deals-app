package pm.bam.gamedeals.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.error
import pm.bam.gamedeals.logging.info
import pm.bam.gamedeals.logging.warn

/*
 * As opposed to pure Kotlin/Java FLow functionality provide elsewhere, this class will provide functionality
 * that uses Common provided classes, such as AndroidX, Logger, custom exceptions and other non-pure Kotlin/Java Flow functionality.
 */

inline fun <reified T> Flow<T>.logFlow(logger: Logger, tag: String? = null, logErrorAsWarning: Boolean = false): Flow<T> =
    onEach { debug(logger, tag = tag) { "Collected ${it.toString()}" } }
        .onError { if (logErrorAsWarning) warn(logger, it, tag = tag) { it.message ?: "Error" } }
        .onError { if (!logErrorAsWarning) error(logger, it, tag = tag) { it.message ?: "Error" } }
        .onStart { debug(logger, tag = tag) { "Started collecting ${T::class.simpleName}" } }
        .onCompletionSuccess { debug(logger, tag = tag) { "Completed collecting ${T::class.simpleName}" } }
        .onCompletionFailure { cause -> warn(logger, throwable = cause, tag = tag) { "Failed collecting ${T::class.simpleName}: ${cause.message}" } }


/**
 * Retries collection of the given flow when [Flow] throws a [Throwable].
 *
 * @param attempts The number of attempts to retry before giving up.
 * @param delayMillis The delay in milliseconds to wait before retrying.
 * @param onRetry The lambda to be called when a retry is attempted. The lambda receives the cause of the exception and the attempt number.
 * @param onNotRetry The lambda to be called when no more retries are attempted. The lambda receives the cause of the exception and the attempt number.
 * @param predicate An additional predicate to determine if the flow should be retried, where returned TRUE means retry, and FALSE means do not retry.
 *
 * @return A flow that retries collection of the given flow when [Flow] emits a null value.
 *
 */
inline fun <reified T> Flow<T>.retryOnException(
    logger: Logger,
    attempts: Int = 3,
    delayMillis: Long = 1000,
    noinline onRetry: (suspend (cause: Throwable, attempt: Long) -> Unit)? = null,
    noinline onNotRetry: (suspend (cause: Throwable, attempt: Long) -> Unit)? = null,
    noinline predicate: (suspend (cause: Throwable, attempt: Long) -> Boolean)? = null
): Flow<T> =
    this.onError { info(logger, it) { "${T::class.simpleName} data not present" } }
        .retryWhenDelay(
            delayMillis = delayMillis,
            onRetry = { cause, attempt ->
                debug(logger, cause) { "${T::class.simpleName} data not observed (attempt: ${attempt + 1}), retrying in $delayMillis ms" }
                onRetry?.invoke(cause, attempt)
            },
            onNotRetry = { cause, attempt ->
                warn(logger, cause) { "${T::class.simpleName} data not observed after $attempt attempts" }
                onNotRetry?.invoke(cause, attempt)
            },
            predicate = { cause, attempt -> attempt < attempts && (predicate?.invoke(cause, attempt) != false) }
        )
