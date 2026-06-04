package pm.bam.gamedeals.domain.repositories.collection

import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.repositories.waitlist.FakeAccountSource
import pm.bam.gamedeals.domain.repositories.waitlist.FakeAuthTokenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionRepositoryTest {

    @Test
    fun logged_out_getCollection_returns_empty() = runTest {
        val repo = CollectionRepositoryImpl(
            FakeAccountSource(collection = listOf(CollectionEntry("a", "A"))),
            FakeAuthTokenStore(access = null),
        )
        assertEquals(emptyList(), repo.getCollection())
        assertEquals(persistentSetOf(), repo.observeCollectionIds().first())
    }

    @Test
    fun logged_in_getCollection_populates_the_id_cache() = runTest {
        val repo = CollectionRepositoryImpl(
            FakeAccountSource(collection = listOf(CollectionEntry("a", "A"), CollectionEntry("b", "B"))),
            FakeAuthTokenStore(access = "token"),
        )
        assertEquals(2, repo.getCollection().size)
        assertEquals(setOf("a", "b"), repo.observeCollectionIds().first().toSet())
    }

    @Test
    fun logged_in_toggle_adds_when_absent() = runTest {
        val source = FakeAccountSource()
        val repo = CollectionRepositoryImpl(source, FakeAuthTokenStore(access = "token"))

        repo.toggleCollection("a")

        assertEquals(listOf("a"), source.added)
        assertTrue(repo.observeIsCollected("a").first())
    }
}
