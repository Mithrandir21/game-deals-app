package pm.bam.gamedeals.domain.repositories.waitlist

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pm.bam.gamedeals.domain.models.WaitlistEntry

/**
 * The user's ITAD waitlist (epic #219). Replaces the removed local Favourites (Phase 3); the heart is
 * login-gated and writes here. Mirrors the old `FavouritesRepository` shape so call sites swap cleanly.
 *
 * Phase 0 STUB: empty flows / no-op writes until the live ITAD account source is wired in Phase 2.3
 * (#228). Registered in DI now so later phases have a stable seam to inject.
 */
interface WaitlistRepository {
    fun observeWaitlistIds(): Flow<ImmutableSet<String>>
    fun observeIsWaitlisted(gameId: String): Flow<Boolean>
    suspend fun getWaitlist(): List<WaitlistEntry>
    suspend fun toggleWaitlist(gameId: String)
}

internal class WaitlistRepositoryImpl : WaitlistRepository {
    override fun observeWaitlistIds(): Flow<ImmutableSet<String>> = flowOf(persistentSetOf())
    override fun observeIsWaitlisted(gameId: String): Flow<Boolean> = flowOf(false)
    override suspend fun getWaitlist(): List<WaitlistEntry> = emptyList()
    override suspend fun toggleWaitlist(gameId: String) { /* no-op until Phase 2.3 (#228) */ }
}
