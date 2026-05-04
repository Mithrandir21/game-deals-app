package pm.bam.gamedeals.logging.implementations

import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.LoggingInterface

/**
 * iOS counterpart to the Android `SimpleLoggingListener` — routes log lines to
 * stdout via `println`, which Xcode's debug console captures. Stays in iosMain
 * because Sentry-Cocoa isn't yet wired into the Xcode project (Phase 7 polish).
 */
internal class IosConsoleLoggingListener : LoggingInterface {

    override fun isEnabled(): Boolean = true

    override fun getLoggerTag(): String = "IosConsoleLoggingListener"

    override fun onLog(level: LogLevel, message: String, tag: String?, throwable: Throwable?) {
        val effectiveTag = tag ?: getLoggerTag()
        val prefix = "[${level.name}] $effectiveTag:"
        if (throwable != null) {
            println("$prefix $message")
            println(throwable.stackTraceToString())
        } else {
            println("$prefix $message")
        }
    }

    override fun onFatalThrowable(tag: String?, throwable: Throwable) {
        val effectiveTag = tag ?: getLoggerTag()
        println("[FATAL] $effectiveTag: ${throwable.message}")
        println(throwable.stackTraceToString())
    }
}
