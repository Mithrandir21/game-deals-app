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
        nsLog("[${level.name}] $effectiveTag: $message")
        if (throwable != null) {
            nsLog(throwable.stackTraceToString())
        }
    }

    override fun onFatalThrowable(tag: String?, throwable: Throwable) {
        val effectiveTag = tag ?: getLoggerTag()
        nsLog("[FATAL] $effectiveTag: ${throwable.message}")
        nsLog(throwable.stackTraceToString())
    }

    /**
     * Kotlin/Native's `NSLog(format, vararg args)` binding doesn't bridge Kotlin
     * `String` to Objective-C `NSString *` cleanly through C variadic args, so
     * `NSLog("%@", line)` lands a garbage pointer at the variadic slot and
     * crashes with EXC_BAD_ACCESS. The safe pattern: call NSLog with a single
     * argument (the message itself becomes the format string) and pre-escape
     * `%` characters so application content can't be reinterpreted as format
     * specifiers.
     */
    private fun nsLog(line: String) {
        NSLog(line.replace("%", "%%"))
    }
}
