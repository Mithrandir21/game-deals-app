package pm.bam.gamedeals.domain.repositories.collection

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.db.cache.CollectionGameIdEntry
import pm.bam.gamedeals.domain.db.dao.CollectionDao
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

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

    /**
     * The enriched collection snapshot (title/art/type/added — no price; the games are owned). Emits the
     * persisted cache first, then each [refreshCollectionDisplay] result. Null until a snapshot exists.
     */
    fun observeCollectionDisplay(): Flow<List<CollectionEntry>?>

    /** Reconciles the collection from remote (also refreshing the id set) and persists the display snapshot. */
    suspend fun refreshCollectionDisplay()

    /** Wipes the locally-cached id set + display snapshot (no remote call) — used to clear the row on logout. */
    suspend fun clearLocal()
}

internal class CollectionRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
    private val collectionDao: CollectionDao,
    private val analytics: Analytics,
    private val displayStore: CollectionDisplayStore,
) : CollectionRepository {

    private val displayFlow = MutableStateFlow<List<CollectionEntry>?>(null)

    override fun observeCollectionIds(): Flow<ImmutableSet<String>> =
        combine(authTokenStore.observeAuthState(), collectionDao.observeAll()) { authState, rows ->
            if (authState is AuthState.LoggedIn) rows.map { it.gameId }.toImmutableSet() else persistentSetOf()
        }

    override fun observeIsCollected(gameId: String): Flow<Boolean> =
        observeCollectionIds().map { gameId in it }

    override fun observeCollectionDisplay(): Flow<List<CollectionEntry>?> =
        displayFlow.onStart { if (displayFlow.value == null) displayFlow.value = displayStore.get() }

    override suspend fun getCollection(): List<CollectionEntry> {
        if (!loggedIn()) {
            collectionDao.clear()
            return emptyList()
        }
        val entries = accountSource.getCollection()
        collectionDao.replaceAll(entries.map { it.gameId })
        return entries
    }

    override suspend fun refreshCollectionDisplay() {
        val entries = getCollection()
        if (entries.isEmpty()) {
            displayStore.clear()
            displayFlow.value = emptyList()
            return
        }
        displayStore.replace(entries)
        displayFlow.value = entries
    }

    override suspend fun clearLocal() {
        collectionDao.clear()
        displayStore.clear()
        displayFlow.value = null
    }

    override suspend fun toggleCollection(gameId: String): RepoUpdateResult {
        if (!loggedIn()) return RepoUpdateResult.NOT_LOGGED_IN
        // Remote-first: confirm the ITAD write before mutating Room.
        if (collectionDao.contains(gameId)) {
            accountSource.removeFromCollection(gameId)
            collectionDao.delete(gameId)
            analytics.capture(AnalyticsEvents.COLLECTION_REMOVED, mapOf("game_id" to gameId))
        } else {
            accountSource.addToCollection(gameId)
            collectionDao.add(CollectionGameIdEntry(gameId))
            analytics.capture(AnalyticsEvents.COLLECTION_ADDED, mapOf("game_id" to gameId))
        }
        return RepoUpdateResult.UPDATED
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
