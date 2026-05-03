package pm.bam.gamedeals.remote.logic

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Platform-aware HttpClient factory. Each platform provides its own engine actual:
 * OkHttp on Android (so the existing okhttp logging interceptor / interceptor
 * configuration story is preserved), Darwin on iOS.
 *
 * Callers do `httpClient { install(ContentNegotiation) { ... }; defaultRequest { ... } }`
 * to get a fully-configured engine-bound client.
 */
expect fun httpClient(block: HttpClientConfig<*>.() -> Unit = {}): HttpClient
