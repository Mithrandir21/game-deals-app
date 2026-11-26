package pm.bam.gamedeals.logging

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the platform-independent half of [isExpectedNetworkFailure]: the cause-chain walk and the
 * simple-name arm. The type-based arm ([isPlatformNetworkFailure]) is inherently per-platform and is
 * covered on the JVM by `SentryLoggingListenerTest`.
 *
 * The name arm matters most on iOS, where it is the *only* thing classifying a Ktor timeout — Kotlin/Native
 * does no renaming, so the names below are load-bearing there rather than the redundant belt-and-braces
 * they are on Android. Naming the doubles after the real Ktor types is the point of the exercise: the
 * production code matches on `simpleName` and nothing else.
 */
class NetworkFailureClassifierTest {

    private class HttpRequestTimeoutException : Throwable("Request timeout")
    private class ConnectTimeoutException : Throwable("Connect timeout")
    private class SocketTimeoutException : Throwable("Socket timeout")
    private class DarwinHttpRequestException(code: Int) :
        Throwable("Exception in http request: Error Domain=NSURLErrorDomain Code=$code")

    @Test
    fun a_null_throwable_is_not_a_network_failure() {
        assertFalse(isExpectedNetworkFailure(null))
    }

    @Test
    fun each_known_ktor_timeout_name_is_recognised() {
        assertTrue(isExpectedNetworkFailure(HttpRequestTimeoutException()))
        assertTrue(isExpectedNetworkFailure(ConnectTimeoutException()))
        assertTrue(isExpectedNetworkFailure(SocketTimeoutException()))
    }

    @Test
    fun a_darwin_transport_failure_is_recognised() {
        // iOS offline (GAME-DEALS-1G) and TLS handshake failure (GAME-DEALS-1F): the Darwin engine wraps
        // every URLSession transport error in this one type, and nothing else classifies it on iOS.
        assertTrue(isExpectedNetworkFailure(DarwinHttpRequestException(code = -1009)))
        assertTrue(isExpectedNetworkFailure(DarwinHttpRequestException(code = -1200)))
    }

    @Test
    fun a_darwin_transport_failure_wrapped_by_a_repository_is_recognised() {
        assertTrue(isExpectedNetworkFailure(IllegalStateException("load failed", DarwinHttpRequestException(code = -1009))))
    }

    @Test
    fun an_ordinary_defect_is_not_a_network_failure() {
        assertFalse(isExpectedNetworkFailure(IllegalStateException("query returned no rows")))
    }

    @Test
    fun a_timeout_buried_in_the_cause_chain_is_still_recognised() {
        // Repositories wrap transport failures on the way up; the classifier has to see through that.
        val wrapped = IllegalStateException("load failed", RuntimeException("mapping failed", ConnectTimeoutException()))

        assertTrue(isExpectedNetworkFailure(wrapped))
    }

    @Test
    fun a_timeout_at_the_last_inspected_link_is_still_recognised() {
        assertTrue(isExpectedNetworkFailure(chainOf(depth = 9, tail = SocketTimeoutException())))
    }

    @Test
    fun a_timeout_past_the_depth_cap_is_given_up_on_rather_than_walked_forever() {
        // Documents the deliberate ceiling: a chain this deep is pathological, and bounding the walk
        // matters more than classifying it. Failing "not a network failure" is the safe direction —
        // a reported non-issue, never a swallowed one.
        assertFalse(isExpectedNetworkFailure(chainOf(depth = 10, tail = SocketTimeoutException())))
    }

    /** [tail] wrapped in [depth] layers of ordinary exceptions. */
    private fun chainOf(depth: Int, tail: Throwable): Throwable =
        (1..depth).fold(tail) { cause, level -> RuntimeException("layer $level", cause) }
}
