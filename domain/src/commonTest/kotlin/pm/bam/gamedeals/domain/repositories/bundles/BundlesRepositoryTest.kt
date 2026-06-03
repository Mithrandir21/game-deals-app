package pm.bam.gamedeals.domain.repositories.bundles

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.source.DealsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BundlesRepositoryTest {

    private val dealsSource: DealsSource = mock(MockMode.autoUnit)
    private val repository = BundlesRepositoryImpl(dealsSource)

    @Test
    fun get_bundles_delegates_to_source() = runTest {
        val bundles = listOf(bundle(1), bundle(2))
        everySuspend { dealsSource.fetchBundles() } returns bundles

        assertEquals(bundles, repository.getBundles())
        verifySuspend(exactly(1)) { dealsSource.fetchBundles() }
    }

    @Test
    fun get_bundle_returns_the_matching_id() = runTest {
        everySuspend { dealsSource.fetchBundles() } returns listOf(bundle(1), bundle(2))

        assertEquals(2, repository.getBundle(2)?.id)
    }

    @Test
    fun get_bundle_returns_null_when_absent() = runTest {
        everySuspend { dealsSource.fetchBundles() } returns listOf(bundle(1))

        assertNull(repository.getBundle(99))
    }

    private fun bundle(id: Int) = Bundle(
        id = id,
        title = "Bundle $id",
        storeName = "Store",
        url = "https://example.com/$id",
        expiryEpochMs = null,
        gameCount = 0,
        priceDenominated = null,
        games = persistentListOf(),
    )
}
