package pm.bam.gamedeals.remote.exceptions

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException

class RemoteExceptionTransformerImplTest {

    @Test
    fun `test HttpException transformation`() {
        val code = 400

        val exception: HttpException = mockk()

        val expected = RemoteHttpException.BadRequest

        every { exception.code() } returns code

        val impl = RemoteExceptionTransformerImpl()

        val result = impl.transformApiException(exception)

        assertEquals(expected, result)
    }

    @Test
    fun `test General Exception transformation`() {
        val exception: RuntimeException = mockk()

        val impl = RemoteExceptionTransformerImpl()

        val result = impl.transformApiException(exception)

        assertEquals(exception, result)
    }
}