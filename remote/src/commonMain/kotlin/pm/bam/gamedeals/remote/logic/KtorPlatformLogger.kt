package pm.bam.gamedeals.remote.logic

import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.LoggingConfig
import io.ktor.http.HttpHeaders

/**
 * Platform-specific Ktor `Logger` used by the network modules' `Logging` plugin
 * in DEBUG builds. Android routes to Logcat via `KtorLogcatLogger`; iOS routes
 * to `println`, which Xcode's debug console captures.
 */
expect val ktorPlatformLogger: Logger

/** Request headers that carry credentials: the ITAD API key, the IGDB client id and any bearer token. */
private val CREDENTIAL_HEADERS = listOf(HttpHeaders.Authorization, "ITAD-API-Key", "Client-ID")

/**
 * Masks credential headers in the debug `Logging` output, so the API keys and the user's sign-in token never reach
 * Logcat or the Xcode console (which anyone with adb or a shared bug report can read). Every client that installs
 * `Logging` calls this.
 */
fun LoggingConfig.hideCredentials() {
    sanitizeHeader { header -> CREDENTIAL_HEADERS.any { it.equals(header, ignoreCase = true) } }
}
