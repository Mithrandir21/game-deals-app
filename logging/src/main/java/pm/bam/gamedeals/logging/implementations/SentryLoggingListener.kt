package pm.bam.gamedeals.logging.implementations

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.LoggingInterface
import javax.inject.Inject

internal class SentryLoggingListener @Inject constructor() : LoggingInterface {

    override fun isEnabled(): Boolean = Sentry.isEnabled()

    override fun getLoggerTag(): String = SentryLoggingListener::class.java.simpleName

    override fun onLog(level: LogLevel, message: String, tag: String?, throwable: Throwable?) {
        when (level) {
            LogLevel.VERBOSE,
            LogLevel.DEBUG,
            LogLevel.INFO,
            LogLevel.WARN -> Sentry.addBreadcrumb(
                Breadcrumb().apply {
                    this.level = level.toSentryLevel()
                    this.message = message
                    this.category = tag
                }
            )
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

    private fun LogLevel.toSentryLevel(): SentryLevel = when (this) {
        LogLevel.VERBOSE, LogLevel.DEBUG -> SentryLevel.DEBUG
        LogLevel.INFO -> SentryLevel.INFO
        LogLevel.WARN -> SentryLevel.WARNING
        LogLevel.ERROR -> SentryLevel.ERROR
        LogLevel.FATAL -> SentryLevel.FATAL
    }
}
