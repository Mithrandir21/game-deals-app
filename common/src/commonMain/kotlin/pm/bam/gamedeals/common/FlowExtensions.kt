package pm.bam.gamedeals.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

inline fun <T> Flow<T>.onError(crossinline action: suspend FlowCollector<T>.(cause: Throwable) -> Unit): Flow<T> =
    catch {
        action(it)
        emitAll(flow { throw it })
    }

inline fun <T> Flow<T>.catchAndContinue(defaultValue: T, crossinline action: suspend FlowCollector<T>.(cause: Throwable) -> Unit): Flow<T> =
    catch {
        action(it)
        emit(defaultValue)
    }

inline fun <T, R> Flow<List<T>>.mapEach(crossinline transformation: (T) -> R): Flow<List<R>> =
    map { list -> list.map { transformation(it) } }

inline fun <T> Flow<T>.onCompletionSuccess(crossinline successAction: suspend FlowCollector<T>.() -> Unit): Flow<T> =
    onCompletion { cause -> if (cause == null) successAction() }

inline fun <T> Flow<T>.onCompletionFailure(crossinline failureAction: suspend FlowCollector<T>.(cause: Throwable) -> Unit): Flow<T> =
    onCompletion { cause -> if (cause != null) failureAction(cause) }

fun <T> Flow<T>.delayOnStart(delayMillis: Long): Flow<T> = onStart { delay(delayMillis) }

// Time is measured via `delay` inside `coroutineScope` so the operator honours `TestDispatcher`'s
// virtual clock under `runTest` (vs. wall-clock `System.currentTimeMillis`).
fun <T, R> Flow<T>.mapDelayAtLeast(delayMillis: Long, transformFunction: suspend (value: T) -> R): Flow<R> =
    transform { value ->
        coroutineScope {
            val deferred = async { transformFunction(value) }
            val pad = launch { delay(delayMillis) }
            val result = deferred.await()
            pad.join()
            emit(result)
        }
    }

// Pad runs concurrently with [block] so virtual time under `runTest` is honoured.
suspend inline fun <T> withMinimumDuration(delayMillis: Long, crossinline block: suspend () -> T): T = coroutineScope {
    val pad = launch { delay(delayMillis) }
    val result = block()
    pad.join()
    result
}

@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T, R> Flow<T>.flatMapLatestDelayAtLeast(delayMillis: Long, crossinline transformFunction: suspend (value: T) -> R): Flow<R> =
    transformLatest { value ->
        coroutineScope {
            val deferred = async { transformFunction(value) }
            val pad = launch { delay(delayMillis) }
            val result = deferred.await()
            pad.join()
            emit(result)
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.latestDelayAtLeast(delayMillis: Long): Flow<T> =
    transformLatest { value ->
        delay(delayMillis)
        emit(value)
    }

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