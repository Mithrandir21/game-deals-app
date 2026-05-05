package pm.bam.gamedeals.testing

import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggingInterface

class TestingLoggingListener : Logger {

    override fun log(level: LogLevel, tag: String?, throwable: Throwable?, messageProvider: () -> String) {
        println("$level - $tag - ${messageProvider.invoke()}")
        throwable?.let { println("Throwable: ${it.printStackTrace()}") }
    }

    override fun fatalThrowable(throwable: Throwable, tag: String?) =
        println("${tag ?: this::class.simpleName} - Throwable: ${throwable.printStackTrace()}")

    override fun addLoggerListener(loggingInterface: LoggingInterface) = Unit

    override fun removeLoggerListener(loggingInterface: LoggingInterface) = Unit
}