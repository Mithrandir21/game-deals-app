package pm.bam.gamedeals.common.imaging

import coil3.util.Logger as CoilLogger
import okio.IOException
import pm.bam.gamedeals.logging.LogLevel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the Sentry-noise contract for image loading on both platforms: only genuine decode/bitmap bugs
 * from Coil reach Sentry as issues ([LogLevel.ERROR]). Transient failures the app recovers from are
 * downgraded so they never create an issue — cancellations (navigated away mid-load) to a breadcrumb-only
 * DEBUG, and HTTP/connectivity failures (404 artwork, offline, timeout) to a breadcrumb-only WARN.
 * Non-error Coil levels pass through unchanged.
 *
 * `coil3.network.HttpException` shares the WARN arm with [IOException]; it isn't exercised directly here
 * because it requires a full `NetworkResponse` to construct, but `okio.IOException` covers the same arm
 * (and is itself the umbrella for Ktor connectivity failures on the JVM).
 */
class CoilLoggingTest {

    @Test
    fun error_with_cancellation_is_downgraded_to_debug() =
        assertEquals(LogLevel.DEBUG, coilLogLevel(CoilLogger.Level.Error, CancellationException()))

    @Test
    fun error_with_io_failure_is_downgraded_to_warn() =
        assertEquals(LogLevel.WARN, coilLogLevel(CoilLogger.Level.Error, IOException("socket closed")))

    @Test
    fun error_without_throwable_is_downgraded_to_warn() =
        assertEquals(LogLevel.WARN, coilLogLevel(CoilLogger.Level.Error, null))

    @Test
    fun error_with_generic_throwable_stays_error() =
        assertEquals(LogLevel.ERROR, coilLogLevel(CoilLogger.Level.Error, IllegalStateException("decode failed")))

    @Test
    fun non_error_levels_pass_through_unchanged() {
        assertEquals(LogLevel.WARN, coilLogLevel(CoilLogger.Level.Warn, IllegalStateException("ignored")))
        assertEquals(LogLevel.INFO, coilLogLevel(CoilLogger.Level.Info, null))
        assertEquals(LogLevel.DEBUG, coilLogLevel(CoilLogger.Level.Debug, null))
        assertEquals(LogLevel.VERBOSE, coilLogLevel(CoilLogger.Level.Verbose, null))
    }
}
