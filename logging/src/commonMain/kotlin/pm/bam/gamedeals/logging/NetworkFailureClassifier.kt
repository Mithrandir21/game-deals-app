package pm.bam.gamedeals.logging

/**
 * Whether [throwable] (or anything in its cause chain) is a *connectivity* failure rather than a defect
 * — no network, DNS miss, dropped socket, upstream timeout.
 *
 * These reach the logger through the ordinary `error(logger, t)` path, which [implementations.SentryLoggingListener]
 * captures as an issue. That turned every offline user into Sentry traffic (KOTLIN-M/K/P/G/H: all
 * `handled`, all cases where the app degraded exactly as designed and the user simply had no signal),
 * and the volume scales with the install base rather than with anything wrong in the app. Downgrading
 * them to breadcrumbs keeps the diagnostic trail on genuine crashes without the standing noise.
 *
 * This deliberately does **not** suppress the log — only its Sentry severity. Logcat/console output is
 * unchanged, and a connectivity failure still appears as a breadcrumb on any issue that follows it.
 *
 * Platform IO hierarchies are matched by type via [isPlatformNetworkFailure]. Ktor's timeout types are
 * matched by simple *name* so `:logging` needn't depend on Ktor (it sits *below* `:remote` in the module
 * graph) — but see [KTOR_NETWORK_FAILURE_NAMES] for why that arm carries less weight than it looks like
 * it does on Android.
 */
fun isExpectedNetworkFailure(throwable: Throwable?): Boolean =
    throwable.anyInCauseChain { it.isPlatformNetworkFailure() || it::class.simpleName in KTOR_NETWORK_FAILURE_NAMES }

/** Whether [predicate] holds for this throwable or any of its causes, up to [MAX_CAUSE_DEPTH] deep. */
internal inline fun Throwable?.anyInCauseChain(predicate: (Throwable) -> Boolean): Boolean {
    var cause = this
    var depth = 0
    while (cause != null && depth < MAX_CAUSE_DEPTH) {
        if (predicate(cause)) return true
        val next = cause.cause
        if (next === cause) return false // self-referential chain; bail rather than spin
        cause = next
        depth++
    }
    return false
}

/**
 * Whether this throwable is a connectivity failure in the platform's own IO hierarchy — `java.io.IOException`
 * and its subclasses (`UnknownHostException`, `SocketException`, `SSLException`, …) on Android.
 */
internal expect fun Throwable.isPlatformNetworkFailure(): Boolean

/**
 * Ktor's transport timeouts, matched by simple name to avoid a `:remote`-ward dependency from the
 * logging layer.
 *
 * IMPORTANT — this arm only actually fires on iOS. R8 renames these classes in an Android release build,
 * so `simpleName` never matches there. It doesn't matter today because every name listed here extends
 * `java.io.IOException` (`HttpRequestTimeoutException` and `ConnectTimeoutException` directly,
 * `SocketTimeoutException` via `InterruptedIOException`), so [isPlatformNetworkFailure] already catches
 * all three by type on Android and this set is redundant there. Kotlin/Native does no such renaming,
 * which is what makes the set load-bearing on iOS.
 *
 * The trap: adding a Ktor type that is *not* an `IOException` would silently do nothing in an Android
 * release build while appearing to work in debug and in unit tests. Anything added here must either
 * extend `IOException` or be handled explicitly in the platform actuals.
 */
private val KTOR_NETWORK_FAILURE_NAMES = setOf(
    "HttpRequestTimeoutException",
    "ConnectTimeoutException",
    "SocketTimeoutException",
)

/** Guards against a pathologically long (or cyclic) cause chain. */
internal const val MAX_CAUSE_DEPTH = 10
