package pm.bam.gamedeals.logging

import kotlinx.coroutines.CancellationException

/**
 * Cancellation-safe replacement for `runCatching { … }.onFailure { error(logger, it) }`.
 *
 * Plain [runCatching] catches **every** [Throwable], including [CancellationException]. In a coroutine
 * that means normal cancellation (a screen dismissed mid-load, a [kotlinx.coroutines.flow.Flow] re-collected,
 * a ViewModel cleared) is swallowed and re-logged at ERROR — which, under the project's Sentry severity
 * policy, turns routine teardown into spurious error issues.
 *
 * This helper rethrows [CancellationException] so structured concurrency still unwinds, logs any *real*
 * failure at ERROR via [error] (tagged with the caller's class name, exactly like a direct `error(logger, t)`),
 * and returns the outcome as a [Result] so call sites keep using `.getOrElse { default }` / `.isFailure`.
 *
 * Inline with a non-suspend `block` type so `block` may still call suspend functions when invoked from a
 * suspend context — the body is inlined into the (suspend) caller.
 */
inline fun <T> Any.runCatchingLogged(logger: Logger, tag: String? = null, block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        error(logger, t, tag)
        Result.failure(t)
    }
