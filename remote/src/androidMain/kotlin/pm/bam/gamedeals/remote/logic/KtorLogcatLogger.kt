package pm.bam.gamedeals.remote.logic

import android.util.Log
import io.ktor.client.plugins.logging.Logger

/**
 * Routes Ktor's `Logging` plugin output to Android Logcat.
 *
 * Ktor's default `Logger.DEFAULT` is SLF4J-based; on Android with no SLF4J
 * binding it writes nowhere. This implementation hands every line to
 * `android.util.Log` so request/response output shows up in Logcat.
 */
object KtorLogcatLogger : Logger {
    private const val TAG = "Ktor"

    override fun log(message: String) {
        Log.d(TAG, message)
    }
}
