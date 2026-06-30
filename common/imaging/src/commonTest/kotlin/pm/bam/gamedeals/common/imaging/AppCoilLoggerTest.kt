package pm.bam.gamedeals.common.imaging

import coil3.util.Logger as CoilLogger
import okio.IOException
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.LoggingInterface
import pm.bam.gamedeals.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the shared Coil->[Logger] adapter wiring (the bit both platforms install on their ImageLoader):
 * - the production-vs-debug `minLevel` policy (a regression here would pump per-load debug breadcrumbs
 *   into production Sentry — the exact noise this is meant to prevent), and
 * - the forwarding contract: Coil events are classified via [coilLogLevel], tagged `"CoilLogging"`, the
 *   throwable is passed through, and a null Coil message falls back to a default.
 */
class AppCoilLoggerTest {

    /** Records the most recent [Logger.log] call so the adapter's forwarding can be asserted. */
    private class RecordingLogger : Logger {
        var level: LogLevel? = null
        var tag: String? = null
        var throwable: Throwable? = null
        var message: String? = null

        override fun log(level: LogLevel, tag: String?, throwable: Throwable?, messageProvider: () -> String) {
            this.level = level
            this.tag = tag
            this.throwable = throwable
            this.message = messageProvider()
        }

        override fun fatalThrowable(throwable: Throwable, tag: String?) = Unit
        override fun addLoggerListener(loggingInterface: LoggingInterface) = Unit
        override fun removeLoggerListener(loggingInterface: LoggingInterface) = Unit
    }

    @Test
    fun production_starts_at_info_min_level() =
        assertEquals(CoilLogger.Level.Info, appCoilLogger(RecordingLogger(), debug = false).minLevel)

    @Test
    fun debug_starts_at_debug_min_level() =
        assertEquals(CoilLogger.Level.Debug, appCoilLogger(RecordingLogger(), debug = true).minLevel)

    @Test
    fun log_forwards_classified_level_tag_and_throwable() {
        val recorder = RecordingLogger()
        val boom = IllegalStateException("decode failed")

        appCoilLogger(recorder, debug = false).log("coilTag", CoilLogger.Level.Error, "boom", boom)

        assertEquals(LogLevel.ERROR, recorder.level)        // generic throwable at Error -> issue
        assertEquals("CoilLogging", recorder.tag)
        assertTrue(recorder.throwable === boom)
        assertEquals("boom", recorder.message)
    }

    @Test
    fun log_downgrades_transient_failure_to_warn() {
        val recorder = RecordingLogger()

        appCoilLogger(recorder, debug = false).log("coilTag", CoilLogger.Level.Error, "404", IOException("404"))

        assertEquals(LogLevel.WARN, recorder.level)         // IO failure -> breadcrumb, not issue
    }

    @Test
    fun log_defaults_null_message() {
        val recorder = RecordingLogger()

        appCoilLogger(recorder, debug = false).log("coilTag", CoilLogger.Level.Info, null, null)

        assertEquals(LogLevel.INFO, recorder.level)
        assertEquals("Coil Log Message", recorder.message)
    }
}
