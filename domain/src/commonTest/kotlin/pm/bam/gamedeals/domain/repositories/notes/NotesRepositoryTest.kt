package pm.bam.gamedeals.domain.repositories.notes

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
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
import pm.bam.gamedeals.domain.models.NotedGame
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.testing.fixtures.game
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotesRepositoryTest {

    private val accountSource: ItadAccountSource = mock(MockMode.autoUnit)
    private val authTokenStore: AuthTokenStore = mock(MockMode.autoUnit)
    private val dealsSource: DealsSource = mock(MockMode.autoUnit)

    private fun repo() = NotesRepositoryImpl(accountSource, authTokenStore, dealsSource)

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
        val result = repo.setNote("g1", "Buy under $20")

        assertEquals(RepoUpdateResult.UPDATED, result)
        assertEquals("Buy under $20", repo.observeNote("g1").first())
        verifySuspend(exactly(1)) { accountSource.setNote("g1", "Buy under $20") }
    }

    @Test
    fun deleteNote_clears_the_observed_note_and_calls_remote() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotes() } returns listOf(ItadNote("g1", "note"))

        val repo = repo()
        assertEquals("note", repo.observeNote("g1").first()) // load
        val result = repo.deleteNote("g1")

        assertEquals(RepoUpdateResult.UPDATED, result)
        assertNull(repo.observeNote("g1").first())
        verifySuspend(exactly(1)) { accountSource.removeNote("g1") }
    }

    @Test
    fun writes_are_no_ops_when_logged_out() = runTest {
        loggedIn(false)

        val repo = repo()
        assertEquals(RepoUpdateResult.NOT_LOGGED_IN, repo.setNote("g1", "x"))
        assertEquals(RepoUpdateResult.NOT_LOGGED_IN, repo.deleteNote("g1"))

        verifySuspend(exactly(0)) { accountSource.setNote(any(), any()) }
        verifySuspend(exactly(0)) { accountSource.removeNote(any()) }
    }

    @Test
    fun getNotedGames_enriches_each_note_with_its_title_and_boxart() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotes() } returns listOf(ItadNote("g1", "Wait for a sale"))
        everySuspend { dealsSource.fetchGame("g1") } returns game(gameID = "g1", title = "Halo", thumb = "art")

        val result = repo().getNotedGames()

        assertEquals(listOf(NotedGame("g1", "Wait for a sale", "Halo", "art")), result)
    }

    @Test
    fun getNotedGames_falls_back_to_the_gameId_when_the_lookup_fails() = runTest {
        loggedIn(true)
        everySuspend { accountSource.getNotes() } returns listOf(ItadNote("g1", "note"))
        everySuspend { dealsSource.fetchGame("g1") } calls { throw Exception("info lookup down") }

        val result = repo().getNotedGames()

        assertEquals(listOf(NotedGame("g1", "note", "g1", null)), result)
    }

    @Test
    fun getNotedGames_is_empty_when_logged_out() = runTest {
        loggedIn(false)

        assertEquals(emptyList(), repo().getNotedGames())

        verifySuspend(exactly(0)) { accountSource.getNotes() }
    }
}
