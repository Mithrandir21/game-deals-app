package pm.bam.gamedeals.logging.implementations

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.LoggingInterface
import pm.bam.gamedeals.logging.isExpectedNetworkFailure

internal class SentryLoggingListener : LoggingInterface {

    override fun isEnabled(): Boolean = Sentry.isEnabled()

    override fun getLoggerTag(): String = SentryLoggingListener::class.simpleName.orEmpty()

    override fun onLog(level: LogLevel, message: String, tag: String?, throwable: Throwable?) {
        when (level) {
            // Developer chatter stays local: it would crowd the 100-slot trail and can carry payload detail.
            LogLevel.VERBOSE,
            LogLevel.DEBUG -> Unit
            LogLevel.INFO,
            LogLevel.WARN -> Sentry.addBreadcrumb(breadcrumb(level.toSentryLevel(), message, tag, throwable))
            // A connectivity failure isn't a defect — the app already degraded as designed and the user
            // simply had no network. Record it as a breadcrumb so it still shows up in the trail of any
            // real issue, but don't manufacture an issue of its own (see [isExpectedNetworkFailure]).
            LogLevel.ERROR if isExpectedNetworkFailure(throwable) ->
                Sentry.addBreadcrumb(breadcrumb(SentryLevel.WARNING, message, tag, throwable))
            LogLevel.ERROR,
            LogLevel.FATAL -> {
                val sentryLevel = level.toSentryLevel()
                if (throwable != null) {
                    Sentry.captureException(throwable) { it.level = sentryLevel }
                } else {
                    Sentry.captureMessage(message) { it.level = sentryLevel }
                }
            }
        }
    }

    override fun onFatalThrowable(tag: String?, throwable: Throwable) {
        Sentry.captureException(throwable) { it.level = SentryLevel.FATAL }
    }

    /**
     * A breadcrumb carrying whatever the throwable can tell us.
     *
     * The downgrade path above is the reason this exists: a connectivity failure never becomes an issue,
     * so the breadcrumb is the *only* record of it, and a bare log message ("deals fetch failed") says
     * nothing about which failure it was. `UnknownHostException` (offline) and `SocketTimeoutException`
     * (stalled) want very different responses, and the trail is where that gets read.
     *
     * `simpleName` survives R8 for the platform IO types, which come from `android.jar` and so are never
     * renamed; Ktor's own types are app-bundled and do get obfuscated in a release build, which is why
     * `reason` is recorded alongside rather than instead.
     */
    private fun breadcrumb(level: SentryLevel, message: String, tag: String?, throwable: Throwable?) =
        Breadcrumb().apply {
            this.level = level
            this.message = message
            this.category = tag
            throwable?.let {
                setData("exception", it::class.simpleName ?: "Throwable")
                setData("reason", it.message ?: "")
            }
        }

    private fun LogLevel.toSentryLevel(): SentryLevel = when (this) {
        LogLevel.VERBOSE, LogLevel.DEBUG -> SentryLevel.DEBUG
        LogLevel.INFO -> SentryLevel.INFO
        LogLevel.WARN -> SentryLevel.WARNING
        LogLevel.ERROR -> SentryLevel.ERROR
        LogLevel.FATAL -> SentryLevel.FATAL
    }
}
