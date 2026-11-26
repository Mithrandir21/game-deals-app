package pm.bam.gamedeals.remote.itad.auth.oauth

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * JVM-host coverage for the OAuth redirect hand-off: how a deep-link Uri is parsed into an
 * [AuthRedirectResult] and how the single-in-flight [CompletableDeferred] is resolved (and cleared).
 * `android.net.Uri` is stubbed with mockk.
 */
class AuthRedirectBusTest {

    // AuthRedirectBus is a process-global object; reset it around each test.
    @Before
    @After
    fun reset() = AuthRedirectBus.clear()

    @Test
    fun a_code_redirect_resolves_to_success_with_the_echoed_state() = runTest {
        val deferred = CompletableDeferred<AuthRedirectResult>()
        AuthRedirectBus.register(deferred)

        AuthRedirectBus.deliver(uri(code = "auth-code", state = "csrf-123"))

        assertEquals(AuthRedirectResult.Success("auth-code", "csrf-123"), deferred.await())
    }

    @Test
    fun a_code_redirect_without_state_resolves_to_success_with_null_state() = runTest {
        val deferred = CompletableDeferred<AuthRedirectResult>()
        AuthRedirectBus.register(deferred)

        AuthRedirectBus.deliver(uri(code = "auth-code", state = null))

        assertEquals(AuthRedirectResult.Success("auth-code", null), deferred.await())
    }

    @Test
    fun an_error_redirect_resolves_to_failed_and_takes_precedence_over_a_code() = runTest {
        val deferred = CompletableDeferred<AuthRedirectResult>()
        AuthRedirectBus.register(deferred)

        // error is checked before code, so a redirect carrying both is a failure.
        AuthRedirectBus.deliver(uri(code = "ignored", error = "access_denied"))

        assertEquals(AuthRedirectResult.Failed("access_denied"), deferred.await())
    }

    @Test
    fun a_redirect_missing_code_and_error_resolves_to_failed() = runTest {
        val deferred = CompletableDeferred<AuthRedirectResult>()
        AuthRedirectBus.register(deferred)

        AuthRedirectBus.deliver(uri())

        assertEquals(AuthRedirectResult.Failed("Redirect missing 'code'"), deferred.await())
    }

    @Test
    fun a_null_redirect_uri_resolves_to_failed() = runTest {
        val deferred = CompletableDeferred<AuthRedirectResult>()
        AuthRedirectBus.register(deferred)

        AuthRedirectBus.deliver(null)

        assertEquals(AuthRedirectResult.Failed("Null redirect URI"), deferred.await())
    }

    @Test
    fun deliver_with_no_pending_registration_is_a_noop() {
        // No register() call — deliver must not crash on the null pending.
        AuthRedirectBus.deliver(uri(code = "code"))
    }

    @Test
    fun delivering_twice_only_resolves_the_first_registration() = runTest {
        val first = CompletableDeferred<AuthRedirectResult>()
        AuthRedirectBus.register(first)
        AuthRedirectBus.deliver(uri(code = "first"))
        assertEquals(AuthRedirectResult.Success("first", null), first.await())

        // pending was cleared on delivery, so a second stray redirect is dropped.
        val second = uri(code = "second")
        AuthRedirectBus.deliver(second) // no pending → no-op, does not touch `first`
        assertEquals(AuthRedirectResult.Success("first", null), first.await())
    }

    @Test
    fun clear_prevents_a_pending_registration_from_being_resolved() {
        val deferred = CompletableDeferred<AuthRedirectResult>()
        AuthRedirectBus.register(deferred)

        AuthRedirectBus.clear()
        AuthRedirectBus.deliver(uri(code = "code"))

        assertFalse(deferred.isCompleted)
    }

    private fun uri(code: String? = null, error: String? = null, state: String? = null): Uri = mockk {
        every { getQueryParameter("code") } returns code
        every { getQueryParameter("error") } returns error
        every { getQueryParameter("state") } returns state
    }
}
