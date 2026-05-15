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

/**
 * Parameterless overload for Swift consumers. Kotlin/Native does not expose
 * Kotlin default arguments to Swift, so callers on the iOS side cannot invoke
 * `httpClient()` without supplying a closure unless this overload exists.
 * Delegates to the `expect` form with an empty config block.
 */
fun httpClient(): HttpClient = httpClient {}
