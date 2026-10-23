package pm.bam.gamedeals.remote.exceptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_BAD_REQUEST
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_FORBIDDEN
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_METHOD_NOT_ALLOWED
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_NOT_FOUND
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_UNAUTHORIZED

/**
 * Covers the int-keyed mapping that backs both the Ktor-side
 * `ResponseException.toRemoteHttpException()` (via `response.status.value`) and
 * any other consumer that just has a status code.
 */
class RemoteHttpExceptionTest {

    @Test
    fun `BadRequest`() = assertTrue(CODE_BAD_REQUEST.toRemoteHttpException() is RemoteHttpException.BadRequest)

    @Test
    fun `Unauthorized`() = assertTrue(CODE_UNAUTHORIZED.toRemoteHttpException() is RemoteHttpException.Unauthorized)

    @Test
    fun `Forbidden`() = assertTrue(CODE_FORBIDDEN.toRemoteHttpException() is RemoteHttpException.Forbidden)

    @Test
    fun `NotFound`() = assertTrue(CODE_NOT_FOUND.toRemoteHttpException() is RemoteHttpException.NotFound)

    @Test
    fun `MethodNotAllowed`() = assertTrue(CODE_METHOD_NOT_ALLOWED.toRemoteHttpException() is RemoteHttpException.MethodNotAllowed)

    @Test
    fun `unrecognised code maps to generic HttpException`() {
        val result = 456789.toRemoteHttpException()
        assertTrue(result is RemoteHttpException.HttpException)
        assertEquals(456789, result.code)
    }

    @Test
    fun `message names the status and the cause is kept`() {
        // Sentry titles an issue by the message: without it GAME-DEALS-11 read as a bare "HttpException".
        val transport = IllegalStateException("Client request(GET /deals/v2) invalid: 429")

        val result = 429.toRemoteHttpException(cause = transport)

        assertEquals("HTTP 429", result.message)
        assertSame(transport, result.cause)
    }

    @Test
    fun `each mapping is a fresh instance`() {
        // A shared singleton would also share the stack trace of the first call that created it.
        assertNotSame(CODE_UNAUTHORIZED.toRemoteHttpException(), CODE_UNAUTHORIZED.toRemoteHttpException())
    }
}
