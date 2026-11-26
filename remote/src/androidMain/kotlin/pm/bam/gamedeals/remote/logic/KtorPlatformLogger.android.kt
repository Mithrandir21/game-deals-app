package pm.bam.gamedeals.remote.logic

import io.ktor.client.plugins.logging.Logger

actual val ktorPlatformLogger: Logger = KtorLogcatLogger
