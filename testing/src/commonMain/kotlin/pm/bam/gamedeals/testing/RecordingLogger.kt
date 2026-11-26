package pm.bam.gamedeals.testing

import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggingInterface

/** A [Logger] that keeps every entry, so a test can check what would reach Sentry. */
class RecordingLogger : Logger {
    data class Entry(val level: LogLevel, val message: String, val throwable: Throwable?)

    val entries = mutableListOf<Entry>()

    fun at(level: LogLevel): List<Entry> = entries.filter { it.level == level }

    override fun log(level: LogLevel, tag: String?, throwable: Throwable?, messageProvider: () -> String) {
        entries += Entry(level, messageProvider(), throwable)
    }

    override fun fatalThrowable(throwable: Throwable, tag: String?) {
        entries += Entry(LogLevel.FATAL, throwable.message.orEmpty(), throwable)
    }

    override fun addLoggerListener(loggingInterface: LoggingInterface) = Unit

    override fun removeLoggerListener(loggingInterface: LoggingInterface) = Unit
}
