package pm.bam.gamedeals.domain.repositories.franchise

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.FollowedFranchise
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Storage-backed behaviour for the followed-franchise repository: toggling, persistence, seeding and analytics. */
class FollowedFranchiseRepositoryImplTest {

    private val clock = Clock { NOW_MS }
    private val analytics = RecordingAnalytics()

    private fun repo(storage: Storage) = FollowedFranchiseRepositoryImpl(storage, clock, analytics)

    private fun event(name: String, id: Long): Pair<String, Map<String, Any>> = name to mapOf("franchise_id" to id)

    @Test
    fun defaults_to_empty_when_nothing_stored() = runTest {
        assertEquals(emptyList(), repo(FakeFranchiseStorage()).getFollowed())
    }

    @Test
    fun toggle_follows_a_new_franchise_with_the_clock_timestamp() = runTest {
        val repo = repo(FakeFranchiseStorage())

        repo.toggle(franchiseId = 1L, name = "Halo")

        assertEquals(listOf(FollowedFranchise(1L, "Halo", NOW_MS)), repo.getFollowed())
    }

    @Test
    fun toggle_captures_a_follow_analytics_event() = runTest {
        repo(FakeFranchiseStorage()).toggle(franchiseId = 1L, name = "Halo")

        assertEquals(listOf(event(AnalyticsEvents.FRANCHISE_FOLLOWED, 1L)), analytics.captured)
    }

    @Test
    fun toggle_a_second_time_unfollows_and_captures_the_unfollow_event() = runTest {
        val repo = repo(FakeFranchiseStorage())

        repo.toggle(franchiseId = 1L, name = "Halo")
        repo.toggle(franchiseId = 1L, name = "Halo")

        assertEquals(emptyList(), repo.getFollowed())
        assertEquals(
            listOf(
                event(AnalyticsEvents.FRANCHISE_FOLLOWED, 1L),
                event(AnalyticsEvents.FRANCHISE_UNFOLLOWED, 1L),
            ),
            analytics.captured,
        )
    }

    @Test
    fun toggling_distinct_franchises_keeps_both_in_insertion_order() = runTest {
        val repo = repo(FakeFranchiseStorage())

        repo.toggle(franchiseId = 1L, name = "Halo")
        repo.toggle(franchiseId = 2L, name = "Doom")

        assertEquals(
            listOf(FollowedFranchise(1L, "Halo", NOW_MS), FollowedFranchise(2L, "Doom", NOW_MS)),
            repo.getFollowed(),
        )
    }

    @Test
    fun remove_drops_the_matching_franchise() = runTest {
        val repo = repo(FakeFranchiseStorage())
        repo.toggle(franchiseId = 1L, name = "Halo")
        repo.toggle(franchiseId = 2L, name = "Doom")

        repo.remove(franchiseId = 1L)

        assertEquals(listOf(FollowedFranchise(2L, "Doom", NOW_MS)), repo.getFollowed())
    }

    @Test
    fun remove_of_an_unfollowed_id_leaves_the_list_but_still_reports_unfollowed() = runTest {
        // The list is unchanged, but remove() unconditionally emits FRANCHISE_UNFOLLOWED — pin this quirk so a
        // future change to gate the event on an actual removal is a deliberate decision, not an accident.
        val repo = repo(FakeFranchiseStorage())
        repo.toggle(franchiseId = 1L, name = "Halo")
        analytics.captured.clear()

        repo.remove(franchiseId = 99L)

        assertEquals(listOf(FollowedFranchise(1L, "Halo", NOW_MS)), repo.getFollowed())
        assertEquals(listOf(event(AnalyticsEvents.FRANCHISE_UNFOLLOWED, 99L)), analytics.captured)
    }

    @Test
    fun observeFollowed_seeds_from_storage_on_first_collection() = runTest {
        val stored = listOf(FollowedFranchise(1L, "Halo", NOW_MS))
        val repo = repo(FakeFranchiseStorage(mapOf(FOLLOWED_FRANCHISES_KEY to stored)))

        assertEquals(stored, repo.observeFollowed().first())
    }

    @Test
    fun observeFollowed_reflects_a_toggle() = runTest {
        val repo = repo(FakeFranchiseStorage())

        repo.toggle(franchiseId = 1L, name = "Halo")

        assertEquals(listOf(FollowedFranchise(1L, "Halo", NOW_MS)), repo.observeFollowed().first())
    }

    @Test
    fun observeFollowedIds_projects_the_id_set() = runTest {
        val repo = repo(FakeFranchiseStorage())
        repo.toggle(franchiseId = 1L, name = "Halo")
        repo.toggle(franchiseId = 2L, name = "Doom")

        assertEquals(setOf(1L, 2L), repo.observeFollowedIds().first())
    }

    @Test
    fun a_follow_is_persisted_to_storage() = runTest {
        val storage = FakeFranchiseStorage()

        repo(storage).toggle(franchiseId = 1L, name = "Halo")

        // A fresh repository over the same storage reads the persisted follow (survives process restart).
        assertEquals(listOf(FollowedFranchise(1L, "Halo", NOW_MS)), repo(storage).getFollowed())
    }

    @Test
    fun a_corrupt_or_failing_read_falls_back_to_empty() = runTest {
        // load() wraps the read in runCatching; a deserialization failure must not crash the follow toggle.
        val repo = repo(FakeFranchiseStorage(throwOnGet = true))

        assertEquals(emptyList(), repo.getFollowed())
    }

    @Test
    fun storage_is_read_only_once_across_repeated_reads() = runTest {
        // ensureLoaded seeds the in-memory StateFlow once; later reads must not hit storage again.
        val storage = FakeFranchiseStorage()

        val repo = repo(storage)
        repo.getFollowed()
        repo.getFollowed()
        repo.observeFollowed().first()

        assertEquals(1, storage.getCount)
    }

    @Test
    fun toggling_off_the_only_follow_persists_the_empty_list() = runTest {
        val storage = FakeFranchiseStorage()
        val repo = repo(storage)

        repo.toggle(franchiseId = 1L, name = "Halo")
        repo.toggle(franchiseId = 1L, name = "Halo")

        assertFalse(repo(storage).getFollowed().any { it.franchiseId == 1L })
        assertTrue(repo(storage).getFollowed().isEmpty())
    }

    private companion object {
        const val NOW_MS = 1_700_000_000_000L
    }
}

private class RecordingAnalytics : Analytics {
    val captured = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun screen(name: String, properties: Map<String, Any>) = Unit
    override fun capture(event: String, properties: Map<String, Any>) { captured += event to properties }
    override fun identify(distinctId: String) = Unit
    override fun reset() = Unit
    override fun setConsent(granted: Boolean) = Unit
}

/** In-memory [Storage] holding values directly (per the project convention in NotificationStoresTest). Shared by [FranchiseStoresTest]. */
internal class FakeFranchiseStorage(
    stored: Map<String, Any> = emptyMap(),
    private val throwOnGet: Boolean = false,
) : Storage {
    private val saved = stored.toMutableMap()
    var getCount = 0
        private set

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? {
        getCount++
        if (throwOnGet) throw RuntimeException("corrupt storage")
        return (saved[storageKey] as T?) ?: defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T {
        getCount++
        if (throwOnGet) throw RuntimeException("corrupt storage")
        return (saved[storageKey] as T?) ?: defaultValue ?: error("no value for $storageKey")
    }

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
        saved[storageKey] = data
        return true
    }

    override suspend fun containsKey(storageKey: String): Boolean = saved.containsKey(storageKey)
    override suspend fun remove(storageKey: String): Boolean = saved.remove(storageKey) != null
}
