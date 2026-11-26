package pm.bam.gamedeals.remote.logic

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

actual fun httpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin, block)
