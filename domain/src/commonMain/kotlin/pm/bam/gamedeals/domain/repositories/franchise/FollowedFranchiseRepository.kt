package pm.bam.gamedeals.domain.repositories.franchise

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.FollowedFranchise
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/**
 * The franchises/series the user follows (#7), persisted via [Storage]. Exposed reactively so the game
 * page's follow toggle reflects the current state immediately.
 */
interface FollowedFranchiseRepository {
    fun observeFollowed(): Flow<List<FollowedFranchise>>
    /** Emits the set of followed franchise ids (for cheap "is this followed?" checks in the UI). */
    fun observeFollowedIds(): Flow<Set<Long>>
    suspend fun getFollowed(): List<FollowedFranchise>
    suspend fun toggle(franchiseId: Long, name: String)
    suspend fun remove(franchiseId: Long)
}

internal const val FOLLOWED_FRANCHISES_KEY = "followed_franchises"

internal class FollowedFranchiseRepositoryImpl(
    private val storage: Storage,
    private val clock: Clock,
    private val analytics: Analytics,
) : FollowedFranchiseRepository {

    private val followed = MutableStateFlow<List<FollowedFranchise>?>(null)

    // Serializes the whole-list read-modify-write — without it two near-simultaneous toggles read the same
    // baseline and the last persist() wins, silently dropping a follow/unfollow (lost update).
    private val mutex = Mutex()

    override fun observeFollowed(): Flow<List<FollowedFranchise>> =
        followed
            .onStart { mutex.withLock { ensureLoaded() } }
            .filterNotNull()

    override fun observeFollowedIds(): Flow<Set<Long>> =
        observeFollowed().map { list -> list.map { it.franchiseId }.toSet() }

    override suspend fun getFollowed(): List<FollowedFranchise> = mutex.withLock { ensureLoaded() }

    override suspend fun toggle(franchiseId: Long, name: String) = mutex.withLock {
        val current = ensureLoaded()
        val wasFollowing = current.any { it.franchiseId == franchiseId }
        val next = if (wasFollowing) {
            current.filterNot { it.franchiseId == franchiseId }
        } else {
            current + FollowedFranchise(franchiseId, name, clock.nowMillis())
        }
        persist(next)
        val event = if (wasFollowing) AnalyticsEvents.FRANCHISE_UNFOLLOWED else AnalyticsEvents.FRANCHISE_FOLLOWED
        analytics.capture(event, mapOf("franchise_id" to franchiseId))
    }

    override suspend fun remove(franchiseId: Long) = mutex.withLock {
        persist(ensureLoaded().filterNot { it.franchiseId == franchiseId })
        analytics.capture(AnalyticsEvents.FRANCHISE_UNFOLLOWED, mapOf("franchise_id" to franchiseId))
    }

    /** Seeds [followed] from [Storage] on first access. Callers must hold [mutex]; [Mutex] is not reentrant. */
    private suspend fun ensureLoaded(): List<FollowedFranchise> {
        if (followed.value == null) followed.value = load()
        return followed.value.orEmpty()
    }

    private suspend fun persist(list: List<FollowedFranchise>) {
        storage.save(FOLLOWED_FRANCHISES_KEY, list, ListSerializer(FollowedFranchise.serializer()))
        followed.value = list
    }

    private suspend fun load(): List<FollowedFranchise> =
        runCatching { storage.getNullable(FOLLOWED_FRANCHISES_KEY, ListSerializer(FollowedFranchise.serializer())) }.getOrNull() ?: emptyList()
}
