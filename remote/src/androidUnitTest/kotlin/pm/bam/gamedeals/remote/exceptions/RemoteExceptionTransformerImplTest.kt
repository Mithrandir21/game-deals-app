package pm.bam.gamedeals.remote.exceptions

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RemoteExceptionTransformerImplTest {

    @Test
    fun `Ktor ResponseException with 400 maps to BadRequest`() = runTest {
        val responseException = captureResponseException(HttpStatusCode.BadRequest)

        val result = RemoteExceptionTransformerImpl().transformApiException(responseException)

        assertEquals(RemoteHttpException.BadRequest, result)
    }

    @Test
    fun `Ktor ResponseException with 404 maps to NotFound`() = runTest {
        val responseException = captureResponseException(HttpStatusCode.NotFound)

        val result = RemoteExceptionTransformerImpl().transformApiException(responseException)

        assertEquals(RemoteHttpException.NotFound, result)
    }

    @Test
    fun `non-Ktor exceptions pass through unchanged`() {
        val original = RuntimeException("boom")

        val result = RemoteExceptionTransformerImpl().transformApiException(original)

        assertSame(original, result)
    }

    /**
     * Building a real `ResponseException` is non-trivial — it wraps an `HttpResponse`
     * with non-public constructors. We get one cheaply by letting Ktor produce one
     * naturally: a MockEngine that responds with an error status while the client has
     * `expectSuccess = true`.
     */
    private suspend fun captureResponseException(status: HttpStatusCode): ResponseException {
        val mockEngine = MockEngine { _ -> respondError(status) }
        val client = HttpClient(mockEngine) { expectSuccess = true }
        return try {
            client.get("/whatever")
            error("expected ResponseException for $status")
        } catch (e: ResponseException) {
            e
        } finally {
            client.close()
        }
    }
}
