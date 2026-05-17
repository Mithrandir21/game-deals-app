package pm.bam.gamedeals.remote.exceptions

import org.junit.Assert.assertEquals
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
    fun `BadRequest`() = assertEquals(RemoteHttpException.BadRequest, CODE_BAD_REQUEST.toRemoteHttpException())

    @Test
    fun `Unauthorized`() = assertEquals(RemoteHttpException.Unauthorized, CODE_UNAUTHORIZED.toRemoteHttpException())

    @Test
    fun `Forbidden`() = assertEquals(RemoteHttpException.Forbidden, CODE_FORBIDDEN.toRemoteHttpException())

    @Test
    fun `NotFound`() = assertEquals(RemoteHttpException.NotFound, CODE_NOT_FOUND.toRemoteHttpException())

    @Test
    fun `MethodNotAllowed`() = assertEquals(RemoteHttpException.MethodNotAllowed, CODE_METHOD_NOT_ALLOWED.toRemoteHttpException())

    @Test
    fun `unrecognised code maps to generic HttpException`() =
        assertEquals(RemoteHttpException.HttpException(456789), 456789.toRemoteHttpException())
}
