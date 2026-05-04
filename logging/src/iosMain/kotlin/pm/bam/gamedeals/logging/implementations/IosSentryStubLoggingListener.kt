package pm.bam.gamedeals.logging.implementations

import platform.Foundation.NSLog
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.LoggingInterface

/**
 * Placeholder for the iOS Sentry path while sentry-kotlin-multiplatform's
 * Cocoa underpinning isn't yet wired into the Xcode project (Phase 7.7 will
 * add Sentry-Cocoa via SPM). Mirrors the role Android's `SentryLoggingListener`
 * plays — captures breadcrumbs for VERBOSE/DEBUG/INFO/WARN and exception
 * captures for ERROR/FATAL — but routes everything to NSLog with a `[Sentry
 * stub]` prefix so downstream developers can see what the real Sentry path
 * *would* have received without ever silently dropping signal.
 *
 * Logger registration on iOS pairs this stub alongside [IosConsoleLoggingListener];
 * the symmetry with Android's two-listener Logger means swapping in real Sentry
 * later is one constructor edit in `loggingIosModule`.
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
            LogLevel.WARN -> NSLog("%@", "[Sentry stub] breadcrumb [${level.name}] $effectiveTag: $message")
            LogLevel.ERROR,
            LogLevel.FATAL -> {
                if (throwable != null) {
                    NSLog("%@", "[Sentry stub] captureException [${level.name}] $effectiveTag: ${throwable.message}")
                    NSLog("%@", throwable.stackTraceToString())
                } else {
                    NSLog("%@", "[Sentry stub] captureMessage [${level.name}] $effectiveTag: $message")
                }
            }
        }
    }

    override fun onFatalThrowable(tag: String?, throwable: Throwable) {
        val effectiveTag = tag ?: getLoggerTag()
        NSLog("%@", "[Sentry stub] captureException [FATAL] $effectiveTag: ${throwable.message}")
        NSLog("%@", throwable.stackTraceToString())
    }
}
