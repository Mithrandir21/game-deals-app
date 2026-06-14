package pm.bam.gamedeals.domain.repositories.notes

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.NotedGame
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.source.ItadAccountSource

/**
 * The user's per-game ITAD notes (epic #272, P4 #282/#283). No Room cache (schema: none) — the whole
 * gameId→note map is held in memory, lazily loaded once per session on first [observeNote] subscription.
 * [observeNote] is auth-gated: it emits `null` when logged out and clears the cached map on logout (so a
 * different account never sees the previous one's notes). Writes are **remote-first** ([setNote] /
 * [deleteNote] confirm the ITAD call before updating the in-memory map) and return [RepoUpdateResult] so
 * the UI can route a logged-out user to sign in. [getNotedGames] is the remote-as-truth reconcile for the
 * "My notes" screen — it enriches each id-only note with its game title + boxart.
 */
interface NotesRepository {
    fun observeNote(gameId: String): Flow<String?>
    suspend fun setNote(gameId: String, note: String): RepoUpdateResult
    suspend fun deleteNote(gameId: String): RepoUpdateResult
    suspend fun getNotedGames(): List<NotedGame>
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class NotesRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
    private val dealsSource: DealsSource,
) : NotesRepository {

    // gameId -> note text for the signed-in user. null = not yet loaded this session.
    private val notes = MutableStateFlow<Map<String, String>?>(null)

    override fun observeNote(gameId: String): Flow<String?> =
        authTokenStore.observeAuthState().flatMapLatest { auth ->
            when (auth) {
                is AuthState.LoggedOut -> {
                    notes.value = null // drop the previous session's notes (cross-account safety)
                    flowOf(null)
                }
                is AuthState.LoggedIn -> notes.onStart { ensureLoaded() }.map { it?.get(gameId) }
            }
        }

    override suspend fun setNote(gameId: String, note: String): RepoUpdateResult {
        if (!loggedIn()) return RepoUpdateResult.NOT_LOGGED_IN
        accountSource.setNote(gameId, note)
        notes.update { (it ?: emptyMap()) + (gameId to note) }
        return RepoUpdateResult.UPDATED
    }

    override suspend fun deleteNote(gameId: String): RepoUpdateResult {
        if (!loggedIn()) return RepoUpdateResult.NOT_LOGGED_IN
        accountSource.removeNote(gameId)
        notes.update { (it ?: emptyMap()) - gameId }
        return RepoUpdateResult.UPDATED
    }

    /**
     * Remote-as-truth list of every noted game for the "My notes" screen: re-fetches the notes (also
     * refreshing the in-memory map so [observeNote] stays in sync) and enriches each id-only note with its
     * title + boxart via a best-effort, parallel `/games/info` lookup. A failed lookup falls back to the
     * raw gameId as the title rather than dropping the note.
     */
    override suspend fun getNotedGames(): List<NotedGame> = coroutineScope {
        if (!loggedIn()) return@coroutineScope emptyList()
        val fetched = runCatching { accountSource.getNotes() }.getOrElse { emptyList() }
        notes.value = fetched.associate { it.gameId to it.note }
        fetched.map { note ->
            async {
                val game = runCatching { dealsSource.fetchGame(note.gameId) }.getOrNull()
                NotedGame(
                    gameId = note.gameId,
                    note = note.note,
                    title = game?.title?.takeIf { it.isNotBlank() } ?: note.gameId,
                    boxart = game?.thumb?.takeIf { it.isNotBlank() },
                )
            }
        }.awaitAll()
    }

    private suspend fun ensureLoaded() {
        if (notes.value != null) return
        notes.value = runCatching { accountSource.getNotes() }.getOrElse { emptyList() }
            .associate { it.gameId to it.note }
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
