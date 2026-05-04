package pm.bam.gamedeals.remote.logic

import io.ktor.client.plugins.logging.Logger

actual val ktorPlatformLogger: Logger = object : Logger {
    override fun log(message: String) {
        println("[Ktor] $message")
    }
}
