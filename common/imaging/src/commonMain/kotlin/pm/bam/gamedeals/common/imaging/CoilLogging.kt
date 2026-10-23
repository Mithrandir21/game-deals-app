package pm.bam.gamedeals.common.imaging

import coil3.network.HttpException
import coil3.request.NullRequestDataException
import coil3.util.Logger as CoilLogger
import okio.IOException
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Shared Coil-to-[Logger] bridge used by both Android and iOS, so image-load diagnostics reach Sentry
 * through the same noise-filtered policy on both platforms.
 *
 * @param logger the app logging seam to forward Coil events to.
 * @param debug when true (debug builds) Coil logs from [CoilLogger.Level.Debug] up — verbose console
 *   output for development; when false (production) it starts at [CoilLogger.Level.Warn]. Coil reports every
 *   successful or cancelled load at Info, and as Sentry breadcrumbs those filled the whole 100-slot trail,
 *   pushing out the failure that led to the issue (GAME-DEALS-11 carried nothing but image loads).
 */
fun appCoilLogger(logger: Logger, debug: Boolean): CoilLogger = object : CoilLogger {
    override var minLevel: CoilLogger.Level = if (debug) CoilLogger.Level.Debug else CoilLogger.Level.Warn
    override fun log(tag: String, level: CoilLogger.Level, message: String?, throwable: Throwable?) {
        logger.log(coilLogLevel(level, throwable), "CoilLogging", throwable = throwable) { message ?: "Coil Log Message" }
    }
}

/**
 * Maps a Coil log event to an app [LogLevel], downgrading transient image-load failures so they
 * don't reach Sentry as issues. Only genuine decode/bitmap bugs stay at [LogLevel.ERROR]:
 * - cancellations (request abandoned, e.g. navigated away) -> DEBUG (breadcrumb only)
 * - null request data (the item simply has no artwork URL) -> DEBUG (breadcrumb only)
 * - HTTP (404 etc.) and IO/connectivity failures -> WARN (breadcrumb only)
 * - non-error levels pass through unchanged.
 *
 * [NullRequestDataException] is Coil's signal that `AsyncImage(model = null)` was composed — a routine
 * data condition for records with no boxart/banner, not a defect. Coil already renders the request's
 * `fallback`/`error` painter for it, so the user sees the intended placeholder; left at ERROR it would
 * only manufacture Sentry noise. The type is Coil-internal (thrown solely by `RealImageLoader` when
 * `request.data == NullRequestData`), so this arm cannot swallow null-argument failures from elsewhere.
 *
 * `okio.IOException` is Coil's multiplatform IO type — on the JVM it's a typealias to
 * `java.io.IOException`, so it still catches Ktor connectivity failures (UnknownHost/timeout) on Android.
 */
internal fun coilLogLevel(level: CoilLogger.Level, throwable: Throwable?): LogLevel =
    if (level != CoilLogger.Level.Error) level.toAppLogLevel()
    else when (throwable) {
        is CancellationException -> LogLevel.DEBUG
        is NullRequestDataException -> LogLevel.DEBUG
        is HttpException, is IOException -> LogLevel.WARN
        null -> LogLevel.WARN
        else -> LogLevel.ERROR
    }

private fun CoilLogger.Level.toAppLogLevel(): LogLevel = when (this) {
    CoilLogger.Level.Verbose -> LogLevel.VERBOSE
    CoilLogger.Level.Debug -> LogLevel.DEBUG
    CoilLogger.Level.Info -> LogLevel.INFO
    CoilLogger.Level.Warn -> LogLevel.WARN
    CoilLogger.Level.Error -> LogLevel.ERROR
}
