package pm.bam.gamedeals.remote.logic

import io.ktor.client.plugins.logging.Logger
import pm.bam.gamedeals.logging.implementations.iosLog

actual val ktorPlatformLogger: Logger = object : Logger {
    override fun log(message: String) {
        iosLog("[Ktor] $message")
    }
}
