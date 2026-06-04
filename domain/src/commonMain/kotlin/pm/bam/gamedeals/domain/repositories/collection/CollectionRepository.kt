package pm.bam.gamedeals.domain.repositories.collection

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pm.bam.gamedeals.domain.models.CollectionEntry

/**
 * The user's ITAD collection (epic #219, Phase 2).
 *
 * Phase 0 STUB: empty flows / no-op writes until the live ITAD account source is wired in Phase 2.3
 * (#228). Registered in DI now so later phases have a stable seam to inject.
 */
interface CollectionRepository {
    fun observeCollectionIds(): Flow<ImmutableSet<String>>
    fun observeIsCollected(gameId: String): Flow<Boolean>
    suspend fun getCollection(): List<CollectionEntry>
    suspend fun toggleCollection(gameId: String)
}

internal class CollectionRepositoryImpl : CollectionRepository {
    override fun observeCollectionIds(): Flow<ImmutableSet<String>> = flowOf(persistentSetOf())
    override fun observeIsCollected(gameId: String): Flow<Boolean> = flowOf(false)
    override suspend fun getCollection(): List<CollectionEntry> = emptyList()
    override suspend fun toggleCollection(gameId: String) { /* no-op until Phase 2.3 (#228) */ }
}
