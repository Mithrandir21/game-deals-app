package pm.bam.gamedeals.logging.implementations

import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.LoggingInterface

/**
 * Placeholder for the iOS Sentry path while sentry-kotlin-multiplatform's
 * Cocoa underpinning isn't yet wired into the Xcode project via SPM. Mirrors
 * Android's `SentryLoggingListener` — captures breadcrumbs for
 * VERBOSE/DEBUG/INFO/WARN and exception captures for ERROR/FATAL — but routes
 * everything to NSLog with a `[Sentry stub]` prefix so what the real Sentry
 * path would have received is visible instead of silently dropped.
 *
 * Paired with [IosConsoleLoggingListener] in `loggingIosModule`; swapping in
 * real Sentry later is one constructor edit.
 */
internal class IosSentryStubLoggingListener : LoggingInterface {

    override fun isEnabled(): Boolean = true

    override fun getLoggerTag(): String = "IosSentryStubLoggingListener"

    override fun onLog(level: LogLevel, message: String, tag: String?, throwable: Throwable?) {
        val effectiveTag = tag ?: getLoggerTag()
        when (level) {
            LogLevel.VERBOSE,
            LogLevel.DEBUG,
            LogLevel.INFO,
            LogLevel.WARN -> iosLog("[Sentry stub] breadcrumb [${level.name}] $effectiveTag: $message")
            LogLevel.ERROR,
            LogLevel.FATAL -> {
                if (throwable != null) {
                    iosLog("[Sentry stub] captureException [${level.name}] $effectiveTag: ${throwable.message}")
                    iosLog(throwable.stackTraceToString())
                } else {
                    iosLog("[Sentry stub] captureMessage [${level.name}] $effectiveTag: $message")
                }
            }
        }
    }

    override fun onFatalThrowable(tag: String?, throwable: Throwable) {
        val effectiveTag = tag ?: getLoggerTag()
        iosLog("[Sentry stub] captureException [FATAL] $effectiveTag: ${throwable.message}")
        iosLog(throwable.stackTraceToString())
    }
}
