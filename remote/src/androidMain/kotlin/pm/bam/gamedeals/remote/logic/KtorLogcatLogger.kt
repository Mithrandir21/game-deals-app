package pm.bam.gamedeals.remote.logic

import android.util.Log
import io.ktor.client.plugins.logging.Logger

/**
 * Routes Ktor's `Logging` plugin output to Android Logcat.
 *
 * Replaces the OkHttp `HttpLoggingInterceptor` from the Retrofit era. Ktor's
 * default `Logger.DEFAULT` is SLF4J-based; on Android with no SLF4J binding it
 * writes nowhere, which during the Phase 3 swap manifested as silently missing
 * network logs in Logcat. This implementation hands every line to
 * `android.util.Log` so request/response bodies show up alongside any other
 * Logcat output the dev was watching.
 */
object KtorLogcatLogger : Logger {
    private const val TAG = "Ktor"

    override fun log(message: String) {
        Log.d(TAG, message)
    }
}
