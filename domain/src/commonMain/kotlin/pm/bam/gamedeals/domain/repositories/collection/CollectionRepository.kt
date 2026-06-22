package pm.bam.gamedeals.domain.repositories.collection

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.db.cache.CollectionGameIdEntry
import pm.bam.gamedeals.domain.db.dao.CollectionDao
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.source.ItadAccountSource

/**
 * The user's ITAD collection (epic #219, Phase 2). Backed by [ItadAccountSource] over a Room-persisted
 * **gameId set** ([CollectionDao]) for reactive UI (ITAD caching strategy, Phase 7a, #268) — see
 * [WaitlistRepository][pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository] for the shape.
 * [getCollection] is the remote-as-truth reconcile; [toggleCollection] is **remote-first**; the observed
 * set is auth-gated and cleared on logout. All writes are login-gated (no-op when logged out).
 */
interface CollectionRepository {
    fun observeCollectionIds(): Flow<ImmutableSet<String>>
    fun observeIsCollected(gameId: String): Flow<Boolean>
    suspend fun getCollection(): List<CollectionEntry>
    suspend fun toggleCollection(gameId: String): RepoUpdateResult

    /** Wipes the locally-cached id set (no remote call) — used to clear the row on logout. */
    suspend fun clearLocal()
}

internal class CollectionRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
    private val collectionDao: CollectionDao,
) : CollectionRepository {

    override fun observeCollectionIds(): Flow<ImmutableSet<String>> =
        combine(authTokenStore.observeAuthState(), collectionDao.observeAll()) { authState, rows ->
            if (authState is AuthState.LoggedIn) rows.map { it.gameId }.toImmutableSet() else persistentSetOf()
        }

    override fun observeIsCollected(gameId: String): Flow<Boolean> =
        observeCollectionIds().map { gameId in it }

    override suspend fun getCollection(): List<CollectionEntry> {
        if (!loggedIn()) {
            collectionDao.clear()
            return emptyList()
        }
        val entries = accountSource.getCollection()
        collectionDao.replaceAll(entries.map { it.gameId })
        return entries
    }

    override suspend fun clearLocal() = collectionDao.clear()

    override suspend fun toggleCollection(gameId: String): RepoUpdateResult {
        if (!loggedIn()) return RepoUpdateResult.NOT_LOGGED_IN
        // Remote-first: confirm the ITAD write before mutating Room.
        if (collectionDao.contains(gameId)) {
            accountSource.removeFromCollection(gameId)
            collectionDao.delete(gameId)
        } else {
            accountSource.addToCollection(gameId)
            collectionDao.add(CollectionGameIdEntry(gameId))
        }
        return RepoUpdateResult.UPDATED
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
