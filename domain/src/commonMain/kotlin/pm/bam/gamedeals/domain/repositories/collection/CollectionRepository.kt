package pm.bam.gamedeals.domain.repositories.collection

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.source.ItadAccountSource

/**
 * The user's ITAD collection (epic #219, Phase 2). Backed by [ItadAccountSource] with a local id cache
 * for reactive UI; [getCollection] refreshes the cache and [toggleCollection] applies an optimistic
 * local update around the remote add/remove. All writes are login-gated (no-op when logged out).
 */
interface CollectionRepository {
    fun observeCollectionIds(): Flow<ImmutableSet<String>>
    fun observeIsCollected(gameId: String): Flow<Boolean>
    suspend fun getCollection(): List<CollectionEntry>
    suspend fun toggleCollection(gameId: String)
}

internal class CollectionRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
) : CollectionRepository {

    private val ids = MutableStateFlow<ImmutableSet<String>>(persistentSetOf())

    override fun observeCollectionIds(): Flow<ImmutableSet<String>> = ids
    override fun observeIsCollected(gameId: String): Flow<Boolean> = ids.map { gameId in it }

    override suspend fun getCollection(): List<CollectionEntry> {
        if (!loggedIn()) {
            ids.value = persistentSetOf()
            return emptyList()
        }
        val entries = accountSource.getCollection()
        ids.value = entries.map { it.gameId }.toImmutableSet()
        return entries
    }

    override suspend fun toggleCollection(gameId: String) {
        if (!loggedIn()) return
        if (gameId in ids.value) {
            accountSource.removeFromCollection(gameId)
            ids.value = (ids.value - gameId).toImmutableSet()
        } else {
            accountSource.addToCollection(gameId)
            ids.value = (ids.value + gameId).toImmutableSet()
        }
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
