package pm.bam.gamedeals.logging.implementations

import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.LoggingInterface

/**
 * iOS counterpart to the Android `SimpleLoggingListener` — routes log lines via
 * `NSLog`, which writes to both the Apple System Log (visible in Console.app
 * with subsystem filtering) and stderr (visible in Xcode's debug console).
 * `println` would only hit stdout, missing the system log integration.
 */
internal class IosConsoleLoggingListener : LoggingInterface {

    override fun isEnabled(): Boolean = true

    override fun getLoggerTag(): String = "IosConsoleLoggingListener"

    override fun onLog(level: LogLevel, message: String, tag: String?, throwable: Throwable?) {
        val effectiveTag = tag ?: getLoggerTag()
        iosLog("[${level.name}] $effectiveTag: $message")
        if (throwable != null) {
            iosLog(throwable.stackTraceToString())
        }
    }

    override fun onFatalThrowable(tag: String?, throwable: Throwable) {
        val effectiveTag = tag ?: getLoggerTag()
        iosLog("[FATAL] $effectiveTag: ${throwable.message}")
        iosLog(throwable.stackTraceToString())
    }
}
