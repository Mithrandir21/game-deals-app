package pm.bam.gamedeals.remote.itad.auth.oauth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** How the Android sign-in await resolves when the user comes back to the app with or without a redirect. */
class AwaitRedirectOrAbandonTest {

    private val success = AuthRedirectResult.Success("code", "state")

    @Test
    fun a_redirect_resolves_the_attempt() = runTest {
        val redirect = CompletableDeferred<AuthRedirectResult>()
        val result = async { awaitRedirectOrAbandon(redirect, CompletableDeferred(), graceMs = 1_000) }

        redirect.complete(success)

        assertEquals(success, result.await())
    }

    @Test
    fun returning_to_the_app_without_a_redirect_cancels_after_the_grace_period() = runTest {
        val returned = CompletableDeferred<Unit>()
        val result = async { awaitRedirectOrAbandon(CompletableDeferred(), returned, graceMs = 1_000) }

        returned.complete(Unit)
        advanceTimeBy(999)
        runCurrent()
        assertFalse(result.isCompleted)
        advanceTimeBy(2)

        assertEquals(AuthRedirectResult.Cancelled, result.await())
    }

    @Test
    fun a_redirect_arriving_within_the_grace_period_still_wins() = runTest {
        val redirect = CompletableDeferred<AuthRedirectResult>()
        val returned = CompletableDeferred<Unit>()
        val result = async { awaitRedirectOrAbandon(redirect, returned, graceMs = 1_000) }

        returned.complete(Unit)
        advanceTimeBy(500)
        redirect.complete(success)

        assertEquals(success, result.await())
    }

    @Test
    fun while_the_user_stays_in_the_browser_the_attempt_stays_pending() = runTest {
        val result = async { awaitRedirectOrAbandon(CompletableDeferred(), CompletableDeferred(), graceMs = 1_000) }

        advanceTimeBy(10 * 60 * 1_000)
        runCurrent()

        assertFalse(result.isCompleted)
        result.cancel()
    }
}
