package pm.bam.gamedeals.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest


fun <T : Any> T.toFlow(): Flow<T> = flow { emit(this@toFlow) }

/** Catches and re-throws any [Throwable] that happen upstream after performing [action] on the caught [Throwable]. */
inline fun <T> Flow<T>.onError(crossinline action: suspend FlowCollector<T>.(cause: Throwable) -> Unit): Flow<T> =
    catch {
        action(it)
        emitAll(flow { throw it })
    }

/**
 * This catch prevents the Flow from being cancelled if an error occurs upstream.
 *
 * Catches and performs some action on the [Throwable] that happen upstream before emitting the [defaultValue] and continuing the flow.
 */
inline fun <T> Flow<T>.catchAndContinue(defaultValue: T, crossinline action: suspend FlowCollector<T>.(cause: Throwable) -> Unit): Flow<T> =
    catch {
        action(it)
        emit(defaultValue)
    }

/** Maps each item in the [Flow] using the provided [transformation] function. */
inline fun <T, R> Flow<List<T>>.mapEach(crossinline transformation: (T) -> R): Flow<List<R>> =
    map { list -> list.map { transformation(it) } }


/**
 * Performs provided [successAction] if the [Flow] completes successfully, when no [Throwable] is thrown [Flow] upstream or downstream.
 *
 * @param successAction The action to perform when the [Flow] completes successfully.
 * @see onCompletion
 */
inline fun <T> Flow<T>.onCompletionSuccess(crossinline successAction: suspend FlowCollector<T>.() -> Unit): Flow<T> =
    onCompletion { cause -> if (cause == null) successAction() }


/**
 * Performs provided [failureAction] if the [Flow] completes successfully, when a [Throwable] is thrown [Flow] upstream or downstream.
 *
 * @param failureAction The action to perform when the [Flow] completes with a [Throwable].
 * @see onCompletion
 */
inline fun <T> Flow<T>.onCompletionFailure(crossinline failureAction: suspend FlowCollector<T>.(cause: Throwable) -> Unit): Flow<T> =
    onCompletion { cause -> if (cause != null) failureAction(cause) }

/**
 * Returns a flow that delays the given [delayMillis] **before** this flow starts to be collected.
 */
fun <T> Flow<T>.delayOnStart(delayMillis: Long): Flow<T> = onStart { delay(delayMillis) }

/**
 * Returns a flow containing the results of applying the given [transform] function to each value of the original flow
 * only after given [delayMillis] has passed, either because the transformation took more or equal amount of time as the [delayMillis],
 * or because the suspend function was delayed for the remaining time.
 */
fun <T, R> Flow<T>.mapDelayAtLeast(delayMillis: Long, transformFunction: suspend (value: T) -> R): Flow<R> =
    transform { value ->
        val timeBeforeTransformation = System.currentTimeMillis()
        val transformedTransformation = transformFunction(value)
        val timeAfterTransformation = System.currentTimeMillis()

        val transformationDuration = timeAfterTransformation - timeBeforeTransformation

        val transformationShorterThanDelay = delayMillis > transformationDuration

        if (transformationShorterThanDelay) {
            val remainingDelay = delayMillis - transformationDuration
            delay(remainingDelay)
        }

        return@transform emit(transformedTransformation)
    }


/**
 * Returns a flow containing the results of applying the given [transformLatest] function to each value of the original flow
 * only after given [delayMillis] has passed, either because the transformation took more or equal amount of time as the [delayMillis],
 * or because the suspend function was delayed for the remaining time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T, R> Flow<T>.flatMapLatestDelayAtLeast(delayMillis: Long, crossinline transformFunction: suspend (value: T) -> R): Flow<R> =
    transformLatest { value ->

        val timeBeforeTransformation = System.currentTimeMillis()
        val transformedTransformation = transformFunction(value)
        val timeAfterTransformation = System.currentTimeMillis()

        val transformationDuration = timeAfterTransformation - timeBeforeTransformation

        val transformationShorterThanDelay = delayMillis > transformationDuration

        if (transformationShorterThanDelay) {
            val remainingDelay = delayMillis - transformationDuration
            delay(remainingDelay)
        }

        return@transformLatest emit(transformedTransformation)
    }


/**
 * Returns a flow containing the results of applying the given [transformLatest] function to each value of the original flow
 * only after given [delayMillis] has passed, either because the transformation took more or equal amount of time as the [delayMillis],
 * or because the suspend function was delayed for the remaining time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.latestDelayAtLeast(delayMillis: Long): Flow<T> =
    transformLatest { value ->

        val timeBeforeTransformation = System.currentTimeMillis()
        val timeAfterTransformation = System.currentTimeMillis()

        val transformationDuration = timeAfterTransformation - timeBeforeTransformation

        val transformationShorterThanDelay = delayMillis > transformationDuration

        if (transformationShorterThanDelay) {
            val remainingDelay = delayMillis - transformationDuration
            delay(remainingDelay)
        }

        return@transformLatest emit(value)
    }

/**
 * Retries collection of the given flow when an exception occurs in the upstream flow and the
 * [predicate] returns true ***after*** delaying for [delayMillis] milliseconds. The predicate also
 * receives an `attempt` number as parameter, starting from zero on the initial call. This operator is
 * *transparent* to exceptions that occur in downstream flow and does not retry on exceptions that are thrown to cancel the flow.
 *
 * On each retry, the [onRetry] lambda is called with the cause of the exception and the attempt number.
 * If the [predicate] returns false, the [onNotRetry] lambda is called with the cause of the exception and the attempt number.
 *
 * @param delayMillis The delay in milliseconds to wait before retrying.
 * @param predicate The predicate to determine if the flow should be retried. The predicate receives the cause of the exception and the attempt number.
 * @return A flow that retries collection of the given flow when an exception occurs in the upstream flow and the [predicate] returns true.
 *
 * @see retryWhen
 */
fun <T> Flow<T>.retryWhenDelay(
    delayMillis: Long,
    onRetry: (suspend (cause: Throwable, attempt: Long) -> Unit)? = null,
    onNotRetry: (suspend (cause: Throwable, attempt: Long) -> Unit)? = null,
    predicate: suspend FlowCollector<T>.(cause: Throwable, attempt: Long) -> Boolean
): Flow<T> =
    retryWhen { cause, attempt ->
        val shouldRetry = predicate(cause, attempt)
        if (shouldRetry) {
            onRetry?.invoke(cause, attempt)
            delay(delayMillis)
            return@retryWhen true
        } else {
            onNotRetry?.invoke(cause, attempt)
            return@retryWhen false
        }
    }