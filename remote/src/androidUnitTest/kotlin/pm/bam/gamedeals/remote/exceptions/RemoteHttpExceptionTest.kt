package pm.bam.gamedeals.remote.exceptions

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_BAD_REQUEST
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_FORBIDDEN
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_METHOD_NOT_ALLOWED
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_NOT_FOUND
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException.Companion.CODE_UNAUTHORIZED
import retrofit2.HttpException

class RemoteHttpExceptionTest {

    @Test
    fun `test BadRequest Exception transformation`() = test(CODE_BAD_REQUEST, RemoteHttpException.BadRequest)

    @Test
    fun `test Unauthorized Exception transformation`() = test(CODE_UNAUTHORIZED, RemoteHttpException.Unauthorized)

    @Test
    fun `test Forbidden Exception transformation`() = test(CODE_FORBIDDEN, RemoteHttpException.Forbidden)

    @Test
    fun `test NotFound Exception transformation`() = test(CODE_NOT_FOUND, RemoteHttpException.NotFound)

    @Test
    fun `test MethodNotAllowed Exception transformation`() = test(CODE_METHOD_NOT_ALLOWED, RemoteHttpException.MethodNotAllowed)

    @Test
    fun `test general HttpException transformation`() = test(456789, RemoteHttpException.HttpException(456789))


    private fun <T> test(code: Int, expected: T) {
        val exception: HttpException = mockk()

        every { exception.code() } returns code

        val result = exception.toRemoteHttpException()

        assertEquals(expected, result)
    }
}