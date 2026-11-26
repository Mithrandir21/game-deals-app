package pm.bam.gamedeals.logging

/**
 * Kotlin/Native has no shared IO exception hierarchy to match on the way the JVM does, and pulling in
 * `kotlinx.io` here would put a transport dependency in the logging layer. The common Ktor names
 * (timeouts plus the Darwin engine's `DarwinHttpRequestException`, which carries every URLSession
 * transport failure) classify iOS connectivity failures instead; anything else stays at ERROR, which is
 * the conservative direction (a reported non-issue, not a swallowed one).
 */
internal actual fun Throwable.isPlatformNetworkFailure(): Boolean = false
