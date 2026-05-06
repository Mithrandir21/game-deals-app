package pm.bam.gamedeals.logging.implementations

import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.LoggingInterface

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
