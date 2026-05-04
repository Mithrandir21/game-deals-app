package pm.bam.gamedeals.logging.implementations

import platform.Foundation.NSLog
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.LoggingInterface

/**
 * iOS counterpart to the Android `SimpleLoggingListener` — routes log lines via
 * `NSLog`, which writes to both the Apple System Log (visible in Console.app
 * with subsystem filtering) and stderr (visible in Xcode's debug console).
 * `println` would only hit stdout, missing the system log integration. Sentry-
 * Cocoa is still deferred until SPM wiring (Phase 7.7).
 */
internal class IosConsoleLoggingListener : LoggingInterface {

    override fun isEnabled(): Boolean = true

    override fun getLoggerTag(): String = "IosConsoleLoggingListener"

    override fun onLog(level: LogLevel, message: String, tag: String?, throwable: Throwable?) {
        val effectiveTag = tag ?: getLoggerTag()
        val line = "[${level.name}] $effectiveTag: $message"
        // NSLog's first arg is a printf format string. Pre-formatted text must be
        // routed through `%@` to avoid the system interpreting `%` characters in
        // application messages.
        NSLog("%@", line)
        if (throwable != null) {
            NSLog("%@", throwable.stackTraceToString())
        }
    }

    override fun onFatalThrowable(tag: String?, throwable: Throwable) {
        val effectiveTag = tag ?: getLoggerTag()
        NSLog("%@", "[FATAL] $effectiveTag: ${throwable.message}")
        NSLog("%@", throwable.stackTraceToString())
    }
}
