package pm.bam.gamedeals.domain.repositories.franchise

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.builtins.ListSerializer
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.FollowedFranchise

/**
 * The franchises/series the user follows (#7), persisted via [Storage] (same store as
 * [PriceWatchRepository][pm.bam.gamedeals.domain.repositories.pricewatch.PriceWatchRepository]). Exposed
 * reactively so the game page's follow toggle reflects the current state immediately.
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
) : FollowedFranchiseRepository {

    private val followed = MutableStateFlow<List<FollowedFranchise>?>(null)

    override fun observeFollowed(): Flow<List<FollowedFranchise>> =
        followed
            .onStart { if (followed.value == null) followed.value = load() }
            .filterNotNull()

    override fun observeFollowedIds(): Flow<Set<Long>> =
        observeFollowed().map { list -> list.map { it.franchiseId }.toSet() }

    override suspend fun getFollowed(): List<FollowedFranchise> {
        if (followed.value == null) followed.value = load()
        return followed.value.orEmpty()
    }

    override suspend fun toggle(franchiseId: Long, name: String) {
        val current = getFollowed()
        val next = if (current.any { it.franchiseId == franchiseId }) {
            current.filterNot { it.franchiseId == franchiseId }
        } else {
            current + FollowedFranchise(franchiseId, name, clock.nowMillis())
        }
        persist(next)
    }

    override suspend fun remove(franchiseId: Long) =
        persist(getFollowed().filterNot { it.franchiseId == franchiseId })

    private suspend fun persist(list: List<FollowedFranchise>) {
        storage.save(FOLLOWED_FRANCHISES_KEY, list, ListSerializer(FollowedFranchise.serializer()))
        followed.value = list
    }

    private suspend fun load(): List<FollowedFranchise> =
        runCatching { storage.getNullable(FOLLOWED_FRANCHISES_KEY, ListSerializer(FollowedFranchise.serializer())) }.getOrNull() ?: emptyList()
}
