package pm.bam.gamedeals.domain.repositories.notes

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.ItadNote
import pm.bam.gamedeals.domain.source.ItadAccountSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotesRepositoryTest {

    private val accountSource: ItadAccountSource = mock(MockMode.autoUnit)
    private val authTokenStore: AuthTokenStore = mock(MockMode.autoUnit)

    private fun repo() = NotesRepositoryImpl(accountSource, authTokenStore)

    private fun loggedIn(loggedIn: Boolean) {
        every { authTokenStore.observeAuthState() } returns
            flowOf(if (loggedIn) AuthState.LoggedIn("user") else AuthState.LoggedOut)
        everySuspend { authTokenStore.getAccessToken() } returns if (loggedIn) "token" else null
    }

    @Test
    fun observeNote_is_null_when_logged_out() = runTest {
        loggedIn(false)
        assertNull(repo().observeNote("g1").first())
    }

    @Test
    fun observeNote_returns_the_loaded_note() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotes() } returns listOf(ItadNote("g1", "Wait for a sale"))

        val repo = repo()
        assertEquals("Wait for a sale", repo.observeNote("g1").first())
        assertNull(repo.observeNote("g2").first())
    }

    @Test
    fun setNote_updates_the_observed_note_and_calls_remote() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotes() } returns emptyList()

        val repo = repo()
        repo.setNote("g1", "Buy under $20")

        assertEquals("Buy under $20", repo.observeNote("g1").first())
        verifySuspend(exactly(1)) { accountSource.setNote("g1", "Buy under $20") }
    }

    @Test
    fun deleteNote_clears_the_observed_note_and_calls_remote() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotes() } returns listOf(ItadNote("g1", "note"))

        val repo = repo()
        assertEquals("note", repo.observeNote("g1").first()) // load
        repo.deleteNote("g1")

        assertNull(repo.observeNote("g1").first())
        verifySuspend(exactly(1)) { accountSource.removeNote("g1") }
    }

    @Test
    fun writes_are_no_ops_when_logged_out() = runTest {
        loggedIn(false)

        val repo = repo()
        repo.setNote("g1", "x")
        repo.deleteNote("g1")

        verifySuspend(exactly(0)) { accountSource.setNote(any(), any()) }
        verifySuspend(exactly(0)) { accountSource.removeNote(any()) }
    }
}
