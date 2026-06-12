package pm.bam.gamedeals.domain.repositories.notes

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.source.ItadAccountSource

/**
 * The user's per-game ITAD notes (epic #272, P4 #282). No Room cache (schema: none) — the whole
 * gameId→note map is held in memory, lazily loaded once per session on first [observeNote] subscription.
 * [observeNote] is auth-gated: it emits `null` when logged out and clears the cached map on logout (so a
 * different account never sees the previous one's notes). Writes are **remote-first** ([setNote] /
 * [deleteNote] confirm the ITAD call before updating the in-memory map).
 */
interface NotesRepository {
    fun observeNote(gameId: String): Flow<String?>
    suspend fun setNote(gameId: String, note: String)
    suspend fun deleteNote(gameId: String)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class NotesRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
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

    override suspend fun setNote(gameId: String, note: String) {
        if (!loggedIn()) return
        accountSource.setNote(gameId, note)
        notes.update { (it ?: emptyMap()) + (gameId to note) }
    }

    override suspend fun deleteNote(gameId: String) {
        if (!loggedIn()) return
        accountSource.removeNote(gameId)
        notes.update { (it ?: emptyMap()) - gameId }
    }

    private suspend fun ensureLoaded() {
        if (notes.value != null) return
        notes.value = runCatching { accountSource.getNotes() }.getOrElse { emptyList() }
            .associate { it.gameId to it.note }
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
