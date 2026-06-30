package pm.bam.gamedeals.logging

/** Interface used to log messages and possible [Throwable] throughout the App. */
interface Logger {

    /**
     * Method used to log a specific message and possible [Throwable].
     *
     * Note that, depending on implementation, zero or more [LoggingInterface]s might be called to pass on the message and possible [Throwable].
     *
     * @param level The level of logging that applies to the provided message and possible [Throwable].
     * @param tag The tag to use in logging.
     * @param throwable A specific [Throwable] associated with this log event. This could be a fatal exception to some journey, or simply informative.
     * @param messageProvider The lambda function that will provide the message to be logged.
     */
    fun log(level: LogLevel = LogLevel.VERBOSE, tag: String? = null, throwable: Throwable? = null, messageProvider: () -> String)

    /**
     * A fatal [Throwable] has been thrown by the App. This happens when something catastrophic happens, something that cannot be recovered from.
     *
     * @param throwable The [Throwable] that has been thrown by the fatal event.
     * @param tag The tag to use in logging.
     */
    fun fatalThrowable(throwable: Throwable, tag: String? = null)

    /**
     * Add the provided [loggingInterface] to the [Set] of loggers that will receive log events.
     *
     * No exception will be thrown if the provided [loggingInterface] is already found in the existing set.
     *
     * **Thread-safety:** Listener registration is **not** thread-safe. Callers must register and
     * unregister listeners during DI bootstrap, *before* any consumer calls [log] or
     * [fatalThrowable]. Concurrent registration with active logging is a race against the
     * underlying [MutableSet]. If a future requirement needs runtime registration, swap the
     * backing set for a thread-safe (e.g. copy-on-write) variant first.
     */
    fun addLoggerListener(loggingInterface: LoggingInterface)

    /**
     * Remove the provided [loggingInterface] from the [Set] of loggers that will receive log events.
     *
     * No exception will be thrown if the provided [loggingInterface] is not found in the existing set.
     *
     * **Thread-safety:** See [addLoggerListener]; the same DI-bootstrap-only contract applies.
     */
    fun removeLoggerListener(loggingInterface: LoggingInterface)
}


fun Any.verbose(logger: Logger, throwable: Throwable? = null, tag: String? = null, messageProvider: () -> String) =
    logger.log(LogLevel.VERBOSE, tag ?: this::class.simpleName, throwable, messageProvider)

fun Any.debug(logger: Logger, throwable: Throwable? = null, tag: String? = null, messageProvider: () -> String) =
    logger.log(LogLevel.DEBUG, tag ?: this::class.simpleName, throwable, messageProvider)

fun Any.info(logger: Logger, throwable: Throwable? = null, tag: String? = null, messageProvider: () -> String) =
    logger.log(LogLevel.INFO, tag ?: this::class.simpleName, throwable, messageProvider)

fun Any.warn(logger: Logger, throwable: Throwable? = null, tag: String? = null, messageProvider: () -> String) =
    logger.log(LogLevel.WARN, tag ?: this::class.simpleName, throwable, messageProvider)

fun Any.error(logger: Logger, throwable: Throwable? = null, tag: String? = null, messageProvider: () -> String) =
    logger.log(LogLevel.ERROR, tag ?: this::class.simpleName, throwable, messageProvider)

fun Any.error(logger: Logger, throwable: Throwable, tag: String? = null) =
    logger.log(LogLevel.ERROR, tag ?: this::class.simpleName, throwable) { throwable.message ?: throwable::class.simpleName ?: "Error" }

fun Any.fatal(logger: Logger, throwable: Throwable? = null, tag: String? = null, messageProvider: () -> String) =
    logger.log(LogLevel.FATAL, tag ?: this::class.simpleName, throwable, messageProvider)

fun Any.fatal(logger: Logger, throwable: Throwable, tag: String? = null) =
    logger.fatalThrowable(throwable, tag ?: this::class.simpleName)