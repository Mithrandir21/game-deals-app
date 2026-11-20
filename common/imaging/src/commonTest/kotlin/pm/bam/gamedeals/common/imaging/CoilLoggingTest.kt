package pm.bam.gamedeals.common.imaging

import coil3.request.NullRequestDataException
import coil3.util.Logger as CoilLogger
import okio.IOException
import pm.bam.gamedeals.logging.LogLevel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the Sentry-noise contract for image loading on both platforms: only genuine decode/bitmap bugs
 * from Coil reach Sentry as issues ([LogLevel.ERROR]). Transient failures the app recovers from are
 * downgraded so they never create an issue — cancellations (navigated away mid-load) and null request
 * data (the item has no artwork URL) to a breadcrumb-only DEBUG, and HTTP/connectivity failures
 * (404 artwork, offline, timeout) to a breadcrumb-only WARN. Non-error Coil levels pass through unchanged.
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
    fun error_with_null_request_data_is_downgraded_to_debug() =
        assertEquals(LogLevel.DEBUG, coilLogLevel(CoilLogger.Level.Error, NullRequestDataException()))

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
    fun error_with_generic_illegal_argument_stays_error() =
        assertEquals(LogLevel.ERROR, coilLogLevel(CoilLogger.Level.Error, IllegalArgumentException("bad size")))

    @Test
    fun android_bitmap_factory_rejecting_the_bytes_is_downgraded_to_warn() {
        // GAME-DEALS-W: the exact message BitmapFactoryDecoder throws.
        val undecodable = IllegalStateException(
            "BitmapFactory returned a null bitmap. Often this means BitmapFactory could not decode the image data " +
                "read from the image source (e.g. network, disk, or memory) as it's not encoded as a valid image format.",
        )
        assertEquals(LogLevel.WARN, coilLogLevel(CoilLogger.Level.Error, undecodable))
    }

    @Test
    fun ios_skia_rejecting_the_bytes_is_downgraded_to_warn() =
        // GAME-DEALS-1H: what Skiko's Image.makeFromEncoded throws under SkiaImageDecoder.
        assertEquals(
            LogLevel.WARN,
            coilLogLevel(CoilLogger.Level.Error, IllegalArgumentException("Failed to Image::makeFromEncoded")),
        )

    @Test
    fun a_decoder_message_on_the_wrong_exception_type_stays_error() =
        assertEquals(
            LogLevel.ERROR,
            coilLogLevel(CoilLogger.Level.Error, RuntimeException("Failed to Image::makeFromEncoded")),
        )

    @Test
    fun non_error_levels_pass_through_unchanged() {
        assertEquals(LogLevel.WARN, coilLogLevel(CoilLogger.Level.Warn, IllegalStateException("ignored")))
        assertEquals(LogLevel.INFO, coilLogLevel(CoilLogger.Level.Info, null))
        assertEquals(LogLevel.DEBUG, coilLogLevel(CoilLogger.Level.Debug, null))
        assertEquals(LogLevel.VERBOSE, coilLogLevel(CoilLogger.Level.Verbose, null))
    }
}
