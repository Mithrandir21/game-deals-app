package pm.bam.gamedeals.remote.logic

import io.ktor.client.plugins.logging.Logger

/**
 * Platform-specific Ktor `Logger` used by the network modules' `Logging` plugin
 * in DEBUG builds. Android routes to Logcat via `KtorLogcatLogger`; iOS routes
 * to `println`, which Xcode's debug console captures.
 */
expect val ktorPlatformLogger: Logger
